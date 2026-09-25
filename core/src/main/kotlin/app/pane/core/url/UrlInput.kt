package app.pane.core.url

import java.net.IDN
import java.net.URI

/** What the text typed into the address bar should do. */
sealed interface InputAction {
    /** Navigate the tab to [url]. */
    data class Navigate(val url: String) : InputAction

    /** Run [query] through the current search engine. */
    data class Search(val query: String) : InputAction

    /** Hand [url] to another app (mailto:, tel:, intent:, …) after asking the user. */
    data class External(val url: String) : InputAction
}

/**
 * Turns address bar input into a navigation, a search or an external hand-off.
 *
 * Deliberately conservative: `javascript:`, `data:`, `file:` and `content:` URLs typed or pasted into
 * the bar are never executed, which removes a whole class of paste-this-to-win-a-prize attacks.
 */
object UrlInput {
    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val ipv4Regex = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")
    private val hostLabelRegex = Regex("^[a-z0-9]([a-z0-9\\-_]{0,61}[a-z0-9])?$")

    /** Schemes Pane renders itself. */
    val webSchemes = setOf("http", "https")
    private val internalSchemes = setOf("about", "view-source", "moz-extension", "resource")

    /** Schemes that are opened by other apps. */
    val externalSchemes = setOf("mailto", "tel", "sms", "smsto", "mms", "geo", "intent", "market", "whatsapp", "tg", "spotify", "zoommtg", "maps")

    /** Schemes that are never loaded from the address bar. */
    val blockedSchemes = setOf("javascript", "vbscript", "data", "file", "content", "chrome", "jar", "blob")

    fun classify(raw: String): InputAction? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        schemeRegex.matchEntire(input)?.let { match ->
            val scheme = match.groupValues[1].lowercase()
            val rest = match.groupValues[2]
            when {
                scheme in webSchemes -> return webUrlWithScheme(input, rest)
                scheme in internalSchemes -> return InputAction.Navigate(input)
                scheme in blockedSchemes -> return InputAction.Search(input)
                scheme in externalSchemes && !input.contains(' ') -> return InputAction.External(input)
                // `localhost:8080`, `example.com:443/path` – the "scheme" is actually a host with a port.
                looksLikeHostWithPort(scheme, rest) -> Unit
                else -> return InputAction.Search(input)
            }
        }

        if (input.any { it.isWhitespace() }) return InputAction.Search(input)
        if (input.contains('@') && !input.contains('/')) return InputAction.Search(input)

        val hostEnd = input.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) input.length else it }
        val hostPort = input.substring(0, hostEnd)
        if (!isPlausibleHostPort(hostPort)) return InputAction.Search(input)
        return InputAction.Navigate(normalizeWebUrl("https://$input"))
    }

    /** `https://example.com/a b` navigates with the space encoded; whitespace in the host is a search. */
    private fun webUrlWithScheme(input: String, rest: String): InputAction {
        if (!rest.startsWith("//") || rest.length <= 2) return InputAction.Search(input)
        val afterSlashes = rest.substring(2)
        val authority = afterSlashes.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || authority.any { it.isWhitespace() }) return InputAction.Search(input)
        return InputAction.Navigate(normalizeWebUrl(input.replace(Regex("\\s"), "%20")))
    }

    private fun looksLikeHostWithPort(scheme: String, rest: String): Boolean {
        val port = rest.takeWhile { it.isDigit() }
        if (port.isEmpty() || port.length > 5) return false
        val after = rest.substring(port.length)
        return (after.isEmpty() || after[0] == '/' || after[0] == '?' || after[0] == '#') &&
            (scheme == "localhost" || scheme.contains('.'))
    }

    /** True for `localhost`, IPv4, `[ipv6]`, and dotted names ending in a known TLD, each with an optional port. */
    fun isPlausibleHostPort(hostPort: String): Boolean {
        if (hostPort.isEmpty()) return false
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return false
            val inside = hostPort.substring(1, close)
            val tail = hostPort.substring(close + 1)
            return inside.contains(':') && inside.all { it.isLetterOrDigit() || it == ':' || it == '.' } &&
                (tail.isEmpty() || isPort(tail.removePrefix(":")) && tail.startsWith(":"))
        }
        val colon = hostPort.lastIndexOf(':')
        val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
        if (colon >= 0 && !isPort(hostPort.substring(colon + 1))) return false
        val lower = host.lowercase().trimEnd('.')
        if (lower == "localhost") return true
        if (ipv4Regex.matches(lower)) return true
        val ascii = try {
            IDN.toASCII(lower, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val labels = ascii.split('.')
        if (labels.size < 2) return false
        if (labels.any { !hostLabelRegex.matches(it) }) return false
        val tld = labels.last()
        if (tld.all { it.isDigit() }) return false
        return Tlds.isKnown(tld)
    }

    private fun isPort(s: String): Boolean = s.isNotEmpty() && s.length <= 5 && s.all { it.isDigit() } && s.toInt() in 1..65535

    /** Lower-cases scheme and host and converts IDN hosts to punycode, leaving the rest untouched. */
    fun normalizeWebUrl(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val scheme = url.substring(0, schemeEnd).lowercase()
        val afterScheme = url.substring(schemeEnd + 3)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        var rest = afterScheme.substring(authorityEnd)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at + 1) else ""
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        val normalizedHostPort = if (hostPort.startsWith("[")) {
            hostPort.lowercase()
        } else {
            val colon = hostPort.lastIndexOf(':')
            val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
            val port = if (colon >= 0) hostPort.substring(colon) else ""
            val asciiHost = try {
                IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
            } catch (_: IllegalArgumentException) {
                host.lowercase()
            }
            asciiHost + port
        }
        if (rest.isEmpty()) rest = "/"
        return "$scheme://$userInfo$normalizedHostPort$rest"
    }

    /** Returns the host of [url] or null when it cannot be parsed. */
    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (_: Exception) {
        val start = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return null
        url.substring(start).takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }.substringAfterLast('@').lowercase().ifEmpty { null }
    }
}
