package app.pane.core.url

import java.net.IDN

/** How URLs are shown in the chrome. Like Safari, the bar shows only the registrable host. */
object UrlDisplay {
    private val strippedPrefixes = listOf("www.", "m.", "mobile.")

    /** `https://www.example.co.uk/path?q=1` → `example.co.uk`. Falls back to the raw URL. */
    fun toolbarText(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        if (url.startsWith("about:") || url.startsWith("moz-extension:")) return url
        val host = UrlInput.hostOf(url) ?: return url
        var display = unicodeHost(host)
        for (prefix in strippedPrefixes) {
            if (display.startsWith(prefix) && display.count { it == '.' } >= 2) {
                display = display.removePrefix(prefix)
                break
            }
        }
        return display
    }

    /**
     * Converts punycode to Unicode for display, except when the host mixes scripts in a way that
     * suggests a homograph attack, in which case the punycode form is kept.
     */
    fun unicodeHost(host: String): String {
        if (!host.split('.').any { it.startsWith("xn--") }) return host
        val unicode = try {
            IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return host
        }
        return if (unicode.split('.').all { isSingleScript(it) }) unicode else host
    }

    private fun isSingleScript(label: String): Boolean {
        val scripts = label.codePoints().toArray()
            .filter { Character.isLetter(it) }
            .map { Character.UnicodeScript.of(it) }
            .filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
            .toSet()
        if (scripts.size <= 1) return true
        // Japanese legitimately mixes Han with kana; Latin plus one other script is the classic spoof.
        val cjk = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
        return cjk.containsAll(scripts)
    }

    /** Full URL for the edit field. Kept verbatim so that submitting it unchanged reloads the same page. */
    fun editableText(url: String): String = if (url == "about:blank") "" else url

    fun isSecure(url: String): Boolean = url.startsWith("https://") || url.startsWith("about:") || url.startsWith("moz-extension://")
}
