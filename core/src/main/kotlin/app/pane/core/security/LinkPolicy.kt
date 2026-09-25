package app.pane.core.security

import java.net.URLDecoder

/** What to do with a navigation to a non-web URL requested by a page. */
enum class LinkDecision {
    /** A normal web load; let the engine handle it. */
    LoadInBrowser,

    /** Ask the user before handing the URL to another app. */
    AskToOpenExternally,

    /** Never load or hand off (e.g. `file:` or `content:` from web content). */
    Block,
}

object LinkPolicy {
    private val engineSchemes = setOf("http", "https", "about", "data", "blob", "javascript", "moz-extension", "resource", "view-source", "ws", "wss")
    private val alwaysBlocked = setOf("file", "content", "chrome", "jar", "vbscript")

    /**
     * Decides how to route [url]. Web content can never reach the local filesystem or content
     * providers, and never launches another app without an explicit tap from the user.
     */
    fun decide(url: String): LinkDecision {
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme.isEmpty()) return LinkDecision.Block
        return when (scheme) {
            in alwaysBlocked -> LinkDecision.Block
            in engineSchemes -> LinkDecision.LoadInBrowser
            else -> LinkDecision.AskToOpenExternally
        }
    }
}

/**
 * Minimal parser for Chrome-style `intent://` URLs, enough to pick a safe fallback.
 * The Android layer still does the real `Intent.parseUri` with `URI_INTENT_SCHEME`.
 */
data class IntentUri(
    val scheme: String?,
    val packageName: String?,
    val fallbackUrl: String?,
    val action: String?,
) {
    companion object {
        fun parse(url: String): IntentUri? {
            if (!url.startsWith("intent:", ignoreCase = true)) return null
            val marker = url.indexOf("#Intent;")
            if (marker < 0) return IntentUri(null, null, null, null)
            val params = url.substring(marker + "#Intent;".length).substringBefore(";end").split(';')
            var scheme: String? = null
            var pkg: String? = null
            var fallback: String? = null
            var action: String? = null
            for (p in params) {
                val key = p.substringBefore('=')
                val value = p.substringAfter('=', "")
                when (key) {
                    "scheme" -> scheme = value
                    "package" -> pkg = value
                    "action" -> action = value
                    "S.browser_fallback_url" -> fallback = try {
                        URLDecoder.decode(value, "UTF-8")
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
            }
            // A fallback may only ever send the user to a regular web page.
            val safeFallback = fallback?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
            return IntentUri(scheme, pkg, safeFallback, action)
        }
    }
}
