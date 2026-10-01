package app.pane.core.download

import java.net.URI

/** the `Referer` a download sends, as a browser does by default: the page's origin, and none when a secure page links to a plain one. */
object Referer {
    fun origin(page: String?, target: String): String? {
        val from = runCatching { URI(page ?: return null) }.getOrNull() ?: return null
        val scheme = from.scheme?.lowercase()
        val host = from.host
        if ((scheme != "http" && scheme != "https") || host == null) return null
        if (scheme == "https" && !target.startsWith("https:", ignoreCase = true)) return null
        return "$scheme://$host" + (if (from.port >= 0) ":${from.port}" else "") + "/"
    }
}
