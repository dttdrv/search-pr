package app.pane.browser.engine

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * the colour a page shows under the status area (assets/bar-inset.js posts it when it changes): its held
 * header's, or its own background's. the app only takes it to pick status icons that read on it.
 */
object PageTone {
    /** the object the page script posts to; attached before the page loads. */
    private const val JS_OBJECT = "__paneTone"

    fun attach(page: WebView, onTone: (Int) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching {
            WebViewCompat.addWebMessageListener(page, JS_OBJECT, setOf("*")) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) parse(message.data)?.let(onTone)
            }
        }
    }

    /** `(r << 16) | (g << 8) | b` in decimal, made opaque; anything else is no tone. */
    internal fun parse(message: String?): Int? = message?.toIntOrNull()?.takeIf { it in 0..0xFFFFFF }?.or(0xFF shl 24)
}
