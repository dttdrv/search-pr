package app.pane.browser.engine

import android.graphics.Color
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * what the page shows under the status area, told by the page itself (assets/bar-inset.js) the moment it
 * changes: the colour of the box there, or nothing when a picture or a gradient decides it and only the
 * screen can say.
 */
object TopColorBridge {
    /** the object the page script posts to; attached before the page loads. */
    private const val JS_OBJECT = "__paneTop"

    /** calls [onColor] with the main frame's colour as argb, or null when the page can't name one. */
    fun attach(page: WebView, onColor: (Int?) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching {
            WebViewCompat.addWebMessageListener(page, JS_OBJECT, setOf("*")) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) onColor(parse(message.data))
            }
        }
    }

    /** a computed css colour, `rgb(r, g, b)`; anything else (translucent, empty) is no colour. */
    internal fun parse(css: String?): Int? {
        val parts = Regex("""^rgb\((\d+), (\d+), (\d+)\)$""").find(css.orEmpty())?.groupValues ?: return null
        return Color.rgb(parts[1].toInt().coerceAtMost(255), parts[2].toInt().coerceAtMost(255), parts[3].toInt().coerceAtMost(255))
    }
}
