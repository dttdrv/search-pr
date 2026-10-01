package app.pane.browser.engine

import android.content.Context
import android.webkit.WebSettings

/**
 * What the browser calls itself. Android's WebView announces itself as an embedded view (`; wv`,
 * `Version/4.0`), which makes some sites, sign-in with Google among them, refuse to work; a browser
 * should look like the browser it is.
 */
object UserAgents {
    @Volatile private var cachedMobile: String? = null

    fun mobile(context: Context): String = cachedMobile ?: build(context).also { cachedMobile = it }

    fun desktop(context: Context): String {
        val version = Regex("Chrome/([\\d.]+)").find(mobile(context))?.groupValues?.get(1) ?: "124.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    private fun build(context: Context): String =
        WebSettings.getDefaultUserAgent(context)
            .replace("; wv)", ")")
            .replace(" Version/4.0", "")
}
