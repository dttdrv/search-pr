package app.pane.core.adblock

import app.pane.core.url.Tlds
import kotlinx.serialization.Serializable

/** One parsed line of a filter list. Lines that can't be honoured by a WebView parse to nothing at all. */
sealed interface ParsedFilter

/**
 * A request rule. [pattern] has its anchors and the options already taken off; for a [regex] it is the
 * source between the slashes. [party]: 0 any, 1 third-party only, 2 first-party only.
 */
data class NetworkFilter(
    val pattern: String,
    val exception: Boolean,
    val regex: Boolean,
    val anchorHost: Boolean,
    val anchorStart: Boolean,
    val anchorEnd: Boolean,
    val types: Int,
    val party: Int,
    val important: Boolean,
    val badfilter: Boolean,
    val matchCase: Boolean,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
    val denyAllow: List<String>,
    val extra: NetworkExtra? = null,
    val strictParty: Int = 0,
) : ParsedFilter

@Serializable
data class NetworkExtra(
    val redirect: String? = null,
    val redirectRule: Boolean = false,
    val priority: Int = 0,
    val removeParam: String? = null,
    val methods: List<String> = emptyList(),
    val excludedMethods: List<String> = emptyList(),
)

@Serializable
data class ScriptletFilter(
    val args: List<String>,
    val exception: Boolean,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
) : ParsedFilter

/** An element-hiding rule, one per selector (a comma list is split so one bad part can't sink the rest). */
class CosmeticFilter(
    val selector: String,
    val exception: Boolean,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
) : ParsedFilter

/**
 * Reads Adblock Plus / uBlock Origin static filter syntax, hosts-file lines and bare domains.
 *
 * Deliberately conservative: an option that changes what a rule means and that a WebView can't do
 * (`csp`, `replace`, response `header`, `popup`, `to=` ...) drops
 * the whole rule, because loosening it would block things it was never meant to. HTML filters
 * (`##^`), procedural cosmetics (`:has-text()`, `:xpath()` ...) and style
 * injection (`#$#`) are skipped for the same reason.
 */
object FilterParser {
    fun parseOne(line: String): ParsedFilter? {
        var first: ParsedFilter? = null
        parse(line) { if (first == null) first = it }
        return first
    }

    fun parse(raw: String, sink: (ParsedFilter) -> Unit) {
        val line = raw.trim()
        if (line.isEmpty()) return
        val c0 = line[0]
        if (c0 == '!') return
        if (c0 == '[' && (line.startsWith("[Adblock", true) || line.startsWith("[uBlock", true) || line.startsWith("[AdGuard", true))) return
        val sep = findCosmeticSeparator(line)
        if (sep >= 0) {
            parseCosmetic(line, sep, sink)
            return
        }
        if (c0 == '#') return // a hosts-file comment
        if (parseHostsLine(line, sink)) return
        if (looksLikeBareDomain(line)) {
            sink(hostRule(line.lowercase()))
            return
        }
        parseNetwork(line)?.let(sink)
    }

    // ---- hosts files and bare domains

    private val ignoredHosts = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )

    private fun hostRule(host: String) = NetworkFilter(
        pattern = "$host^", exception = false, regex = false, anchorHost = true, anchorStart = false,
        anchorEnd = false, types = ResourceType.ALL_REQUESTS, party = 0, important = false,
        badfilter = false, matchCase = false, includeDomains = emptyList(), excludeDomains = emptyList(),
        denyAllow = emptyList(),
    )

    private fun parseHostsLine(line: String, sink: (ParsedFilter) -> Unit): Boolean {
        val firstSpace = line.indexOfFirst { it == ' ' || it == '\t' }
        if (firstSpace <= 0) return false
        val ip = line.substring(0, firstSpace)
        val isIp = ip.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' } &&
            (ip.contains('.') || ip.contains(':')) && !ip.contains('/') && !ip.contains('*')
        if (!isIp) return false
        val hash = line.indexOf('#')
        val body = if (hash >= 0) line.substring(firstSpace, hash) else line.substring(firstSpace)
        for (token in body.split(' ', '\t')) {
            val h = token.trim().lowercase()
            if (h.isEmpty() || h in ignoredHosts || !validHost(h)) continue
            sink(hostRule(h))
        }
        return true
    }

    private fun validHost(h: String): Boolean {
        if (h.length > 253 || !h.contains('.')) return false
        for (c in h) if (!(c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-' || c == '_')) return false
        return !h.startsWith('.') && !h.endsWith('.')
    }

    private fun looksLikeBareDomain(line: String): Boolean {
        if (!line.contains('.')) return false
        val l = line.lowercase()
        if (!validHost(l)) return false
        return Tlds.isKnown(l.substringAfterLast('.'))
    }

    // ---- cosmetic rules

    /** The index of the `#` that starts a cosmetic separator (`##`, `#@#`, `#?#`, `#$#` ...), or -1. */
    private fun findCosmeticSeparator(line: String): Int {
        var i = line.indexOf('#')
        while (i >= 0) {
            if (separatorLength(line, i) > 0 && domainPartOk(line, i)) return i
            i = line.indexOf('#', i + 1)
        }
        return -1
    }

    private fun separatorLength(line: String, i: Int): Int {
        val n = line.length
        if (i + 1 >= n) return 0
        return when (line[i + 1]) {
            '#' -> 2
            '@' -> when {
                i + 2 < n && line[i + 2] == '#' -> 3
                i + 3 < n && (line[i + 2] == '$' || line[i + 2] == '?' || line[i + 2] == '%') && line[i + 3] == '#' -> 4
                else -> 0
            }
            '?', '$', '%' -> if (i + 2 < n && line[i + 2] == '#') 3 else 0
            else -> 0
        }
    }

    private fun domainPartOk(line: String, end: Int): Boolean {
        for (k in 0 until end) {
            val c = line[k]
            if (!(c.isLetterOrDigit() || c == '.' || c == ',' || c == '~' || c == '-' || c == '_' || c == '*' || c == '>')) return false
        }
        return true
    }

    private fun parseCosmetic(line: String, sep: Int, sink: (ParsedFilter) -> Unit) {
        val len = separatorLength(line, sep)
        val marker = line.substring(sep, sep + len)
        val exception = when (marker) {
            "##" -> false
            "#@#" -> true
            else -> return
        }
        val body = line.substring(sep + len).trim()
        if (body.isEmpty() || body[0] == '^' || body.startsWith("script:")) return
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        if (sep > 0) {
            for (entry in line.substring(0, sep).split(',')) {
                val e = entry.trim().lowercase()
                if (e.isEmpty()) continue
                if (e.startsWith("~")) exclude.add(e.substring(1)) else include.add(e)
            }
        }
        if (body.startsWith("+js(") && body.endsWith(')')) {
            if (exception && sep > 0 && include.isEmpty()) return
            val args = scriptletArgs(body.substring(4, body.length - 1))
            if (args.isEmpty()) {
                if (exception) sink(ScriptletFilter(args, true, include, emptyList()))
            } else if (exception || include.isNotEmpty()) {
                val resource = FilterResources.scriptlet(args[0]) ?: return
                sink(ScriptletFilter(listOf(resource.name) + args.drop(1), exception, include, if (exception) emptyList() else exclude))
            }
            return
        }
        if ((include + exclude).any { '>' in it }) return
        for (part in splitSelectorList(body)) {
            val s = part.trim()
            if (isNativeSelector(s)) sink(CosmeticFilter(s, exception, include, exclude))
        }
    }

    internal fun scriptletArgs(s: String): List<String> {
        if (s.isBlank()) return emptyList()
        val out = ArrayList<String>()
        var start = 0
        fun endOf(from: Int, delimiter: Char): Int {
            var i = from
            while (i < s.length) {
                if (s[i] == '\\') { i += 2; continue }
                if (s[i] == delimiter) return i
                i++
            }
            return s.length
        }
        while (start <= s.length) {
            while (start < s.length && s[start].isWhitespace()) start++
            val quote = s.getOrNull(start)?.takeIf { it == '\'' || it == '"' || it == '`' }
            var end = if (quote != null) endOf(start + 1, quote) else s.length
            var next = end + 1
            while (next < s.length && s[next].isWhitespace()) next++
            val quoted = quote != null && end < s.length && (next == s.length || s.getOrNull(next) == ',')
            if (!quoted) { end = endOf(start, ','); next = end }
            val value = if (quoted) s.substring(start + 1, end) else s.substring(start, end).trimEnd()
            val delimiter = if (quoted) quote!! else ','
            val normalized = StringBuilder()
            var i = 0
            while (i < value.length) {
                if (value[i] != '\\') { normalized.append(value[i++]); continue }
                val from = i
                while (i < value.length && value[i] == '\\') i++
                val count = i - from
                repeat(count - if (count % 2 == 1 && value.getOrNull(i) == delimiter) 1 else 0) { normalized.append('\\') }
            }
            out.add(normalized.toString())
            if (next >= s.length) break
            start = next + 1
        }
        return out
    }

    /** Splits `a, b` at top-level commas (not inside brackets, parentheses or quotes). */
    internal fun splitSelectorList(s: String): List<String> {
        if (s.indexOf(',') < 0) return listOf(s)
        val out = ArrayList<String>()
        var depth = 0
        var quote = 0.toChar()
        var start = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> i++
                quote != 0.toChar() -> if (c == quote) quote = 0.toChar()
                c == '"' || c == '\'' -> quote = c
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> depth--
                c == ',' && depth == 0 -> { out.add(s.substring(start, i)); start = i + 1 }
            }
            i++
        }
        out.add(s.substring(start))
        return out
    }

    private val nativePseudo = setOf(
        "not", "has", "is", "where", "nth-child", "nth-last-child", "nth-of-type", "nth-last-of-type",
        "first-child", "last-child", "only-child", "first-of-type", "last-of-type", "only-of-type", "empty",
        "root", "checked", "disabled", "enabled", "link", "visited", "hover", "focus", "focus-within",
        "focus-visible", "active", "target", "lang", "dir", "before", "after", "first-line", "first-letter",
        "placeholder", "any-link", "read-only", "read-write", "required", "optional", "defined", "scope",
    )

    /** Whether a browser can evaluate [s] by itself: no procedural operators, nothing that could escape a rule. */
    internal fun isNativeSelector(s: String): Boolean {
        if (s.isEmpty() || s.length > 512) return false
        val c0 = s[0]
        if (c0 == '@' || c0 == '!' || c0 == '^' || c0 == '}' || c0 == '{') return false
        if (c0 == '#' && (s.length == 1 || !(s[1].isLetterOrDigit() || s[1] == '_' || s[1] == '-' || s[1] == '\\'))) return false
        var depth = 0
        var bracket = 0
        var quote = 0.toChar()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') { i += 2; continue }
            if (quote != 0.toChar()) {
                if (c == quote) quote = 0.toChar()
                i++
                continue
            }
            when (c) {
                '"', '\'' -> quote = c
                '{', '}', '<', '\n', '\r', ';' -> if (bracket == 0 || c == '{' || c == '}') return false
                '[' -> bracket++
                ']' -> bracket--
                '(' -> depth++
                ')' -> depth--
                ':' -> if (bracket == 0) {
                    var j = i + 1
                    if (j < s.length && s[j] == ':') j++
                    val start = j
                    while (j < s.length && (s[j].isLetter() || s[j] == '-')) j++
                    if (s.substring(start, j).lowercase() !in nativePseudo) return false
                }
            }
            i++
        }
        return quote == 0.toChar() && depth == 0 && bracket == 0
    }

    // ---- request rules

    private fun parseNetwork(line0: String): NetworkFilter? {
        var s = line0
        var exception = false
        if (s.startsWith("@@")) {
            exception = true
            s = s.substring(2)
        }
        if (s.isEmpty()) return null
        var optionText: String? = null
        val wholeRegex = s.length > 2 && s[0] == '/' && s[s.length - 1] == '/'
        if (!wholeRegex) {
            var dollar = s.indexOf('$')
            while (dollar >= 0) {
                val name = s.substring(dollar + 1).substringBefore(',').substringBefore('=').removePrefix("~").lowercase()
                if (ResourceType.fromOption(name) != 0 || name in setOf("domain", "from", "denyallow", "third-party", "3p", "first-party", "1p", "strict3p", "strict1p", "important", "badfilter", "match-case", "redirect", "redirect-rule", "removeparam", "queryprune", "method", "empty", "mp4")) break
                val next = s.indexOf('$', dollar + 1)
                if (next < 0) break
                dollar = next
            }
            if (dollar >= 0) {
                optionText = s.substring(dollar + 1)
                s = s.substring(0, dollar)
            }
        }

        var pos = 0
        var neg = 0
        var party = 0
        var strictParty = 0
        var important = false
        var badfilter = false
        var matchCase = false
        val include = ArrayList<String>()
        val exclude = ArrayList<String>()
        val deny = ArrayList<String>()
        var redirect: String? = null
        var redirectRule = false
        var priority = 0
        var removeParam: String? = null
        val methods = ArrayList<String>()
        val excludedMethods = ArrayList<String>()
        if (optionText != null) {
            for (raw in scriptletArgs(optionText)) {
                val opt = raw.trim()
                if (opt.isEmpty()) continue
                val negated = opt.startsWith("~")
                val body = if (negated) opt.substring(1) else opt
                val eq = body.indexOf('=')
                val name = (if (eq >= 0) body.substring(0, eq) else body).lowercase()
                val value = if (eq >= 0) body.substring(eq + 1) else ""
                if (negated && name in setOf("redirect", "redirect-rule", "removeparam", "queryprune", "method", "important", "badfilter", "match-case", "domain", "from", "denyallow", "empty", "mp4")) return null
                when (name) {
                    "third-party", "3p" -> party = if (negated) 2 else 1
                    "first-party", "1p" -> party = if (negated) 1 else 2
                    "strict3p" -> strictParty = if (negated) 2 else 1
                    "strict1p" -> strictParty = if (negated) 1 else 2
                    "important" -> important = true
                    "badfilter" -> badfilter = true
                    "match-case" -> matchCase = true
                    "domain", "from" -> if (!readDomains(value, include, exclude)) return null
                    "denyallow" -> for (d in value.split('|')) {
                        val h = d.trim().lowercase()
                        if (h.isEmpty() || h.startsWith("/") || h.startsWith("~") || h.contains('*')) return null
                        deny.add(h)
                    }
                    "empty" -> redirect = "empty"
                    "mp4" -> redirect = "noop-1s.mp4"
                    "redirect", "redirect-rule" -> {
                        redirectRule = name == "redirect-rule"
                        val colon = value.lastIndexOf(':')
                        val p = if (colon >= 0) value.substring(colon + 1).toIntOrNull() else null
                        val token = if (p != null) value.substring(0, colon) else value
                        priority = p ?: 0
                        redirect = when {
                            token == "none" -> token
                            token.isEmpty() && exception && redirectRule -> ""
                            else -> FilterResources.redirect(token)?.name ?: return null
                        }
                    }
                    "removeparam", "queryprune" -> {
                        val p = value.removePrefix("~")
                        if (p.startsWith('/')) {
                            val end = p.lastIndexOf('/')
                            if (end <= 0 || p.substring(end + 1).any { it != 'i' }) return null
                            try { java.util.regex.Pattern.compile(p.substring(1, end)) }
                            catch (_: java.util.regex.PatternSyntaxException) { return null }
                        }
                        removeParam = value
                    }
                    "method" -> for (m in value.lowercase().split('|')) {
                        val bare = m.removePrefix("~")
                        if (bare !in setOf("connect", "delete", "get", "head", "options", "patch", "post", "put")) return null
                        if (m.startsWith('~')) excludedMethods.add(bare) else methods.add(bare)
                    }
                    else -> {
                        val bit = ResourceType.fromOption(name)
                        if (bit == 0) return null
                        if (negated) neg = neg or bit else pos = pos or bit
                    }
                }
            }
        }
        val defaults = ResourceType.ALL_REQUESTS or if (removeParam != null) ResourceType.DOCUMENT else 0
        val types = (if (pos != 0) pos else defaults) and neg.inv()
        if (exception && redirect != null) redirectRule = true
        if (!exception && types and ResourceType.ALL_REQUESTS == 0 && removeParam == null) return null
        if (types == 0) return null
        if (redirect != null && removeParam != null) return null
        if (deny.isNotEmpty() && include.isEmpty()) return null
        val extra = NetworkExtra(redirect, redirectRule, priority, removeParam, methods.distinct().sorted(), excludedMethods.distinct().sorted()).takeUnless { it == NetworkExtra() }

        // The pattern.
        if (wholeRegex || (s.length > 2 && s[0] == '/' && s[s.length - 1] == '/')) {
            val src = s.substring(1, s.length - 1)
            if (src.isEmpty() || src.length > 400) return null
            return NetworkFilter(src, exception, true, false, false, false, types, party, important, badfilter, matchCase, include, exclude, deny, extra, strictParty)
        }
        var anchorHost = false
        var anchorStart = false
        var anchorEnd = false
        if (s.startsWith("||")) {
            anchorHost = true
            s = s.substring(2)
        } else if (s.startsWith("|")) {
            anchorStart = true
            s = s.substring(1)
        }
        if (s.endsWith("|")) {
            anchorEnd = true
            s = s.substring(0, s.length - 1)
        }
        // Wildcards at the ends say nothing; a leading one after `||` makes the anchor moot.
        if (s.startsWith("*")) {
            anchorHost = false
            anchorStart = false
        }
        if (s.endsWith("*")) anchorEnd = false
        s = collapseStars(s)
        if (s.length > 600) return null
        for (c in s) if (c.code > 126 || c.code <= 32) return null
        if (!matchCase) s = s.lowercase()
        if (s.isEmpty() && !anchorHost && !anchorStart && !anchorEnd) {
            // `*$script,domain=x` style rules apply to everything; with no option at all they would block the web.
            if (optionText == null || optionText.isBlank()) return null
            val constrained = types != ResourceType.ALL_REQUESTS || party != 0 || include.isNotEmpty() || deny.isNotEmpty() || extra != null || strictParty != 0
            if (!constrained) return null
        }
        if (s.isEmpty() && (anchorHost || anchorStart)) return null
        return NetworkFilter(s, exception, false, anchorHost, anchorStart, anchorEnd, types, party, important, badfilter, matchCase, include, exclude, deny, extra, strictParty)
    }

    private fun collapseStars(s: String): String {
        var a = 0
        var b = s.length
        while (a < b && s[a] == '*') a++
        while (b > a && s[b - 1] == '*') b--
        val t = s.substring(a, b)
        return if (t.contains("**")) t.replace(Regex("\\*{2,}"), "*") else t
    }

    private fun readDomains(value: String, include: MutableList<String>, exclude: MutableList<String>): Boolean {
        for (d in value.split('|')) {
            val e = d.trim().lowercase()
            if (e.isEmpty()) continue
            if (e.startsWith("/") || e.startsWith("~/")) return false
            if (e.startsWith("~")) exclude.add(e.substring(1)) else include.add(e)
        }
        return true
    }
}
