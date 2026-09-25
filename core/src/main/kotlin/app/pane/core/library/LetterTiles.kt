package app.pane.core.library

import app.pane.core.url.UrlInput

/**
 * Stand-in site icons: the first letter of the site on a colour derived from its host. Pane
 * never fetches favicons in the background, so these keep lists scannable without extra requests.
 */
object LetterTiles {
    private val strippedPrefixes = listOf("www.", "m.", "mobile.")

    /** The host a tile is keyed on, without `www.`-style prefixes so `www.x.com` and `x.com` match. */
    fun hostKey(url: String): String {
        val host = UrlInput.hostOf(url) ?: return url
        val prefix = strippedPrefixes.firstOrNull { host.startsWith(it) && host.count { c -> c == '.' } >= 2 }
        return if (prefix != null) host.removePrefix(prefix) else host
    }

    /** Upper-case first letter or digit of the host, then of [title]; null when neither has one. */
    fun letter(url: String, title: String? = null): String? {
        val fromHost = if (UrlInput.hostOf(url) != null) hostKey(url).firstOrNull { it.isLetterOrDigit() } else null
        val c = fromHost ?: title?.firstOrNull { it.isLetterOrDigit() } ?: return null
        return c.uppercase()
    }

    /** A stable palette slot for [key]; the same site always gets the same colour. */
    fun colorIndex(key: String, paletteSize: Int): Int {
        require(paletteSize > 0) { "paletteSize must be positive" }
        return Math.floorMod(key.hashCode(), paletteSize)
    }
}
