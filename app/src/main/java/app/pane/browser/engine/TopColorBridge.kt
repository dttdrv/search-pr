package app.pane.browser.engine

import android.graphics.Color
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * what the page (assets/bar-inset.js) says about its top the moment it changes. [held]: it holds something
 * across the top (a header, the start of a page that doesn't scroll), so nothing passes under the status area
 * and what is at its edge should carry on over it. [color]: a first word on the colour passing under the
 * status area, ahead of the screen being read; null where no box can say.
 */
data class PageTop(val held: Boolean, val color: Int?)

object TopColorBridge {
    /** the object the page script posts to; attached before the page loads. */
    private const val JS_OBJECT = "__paneTop"

    /** calls [onTop] with what the main frame says. */
    fun attach(page: WebView, onTop: (PageTop) -> Unit) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching {
            WebViewCompat.addWebMessageListener(page, JS_OBJECT, setOf("*")) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) parse(message.data)?.let(onTop)
            }
        }
    }

    /** `1` or `0` for held, a tab, then a computed css colour or nothing. */
    internal fun parse(message: String?): PageTop? {
        val parts = message?.split('\t', limit = 2)?.takeIf { it.size == 2 && it[0] in setOf("0", "1") } ?: return null
        return PageTop(parts[0] == "1", color(parts[1]))
    }

    /** a computed css colour, `rgb(r, g, b)`; anything else (translucent, empty) is no colour. */
    internal fun color(css: String): Int? {
        val parts = Regex("""^rgb\((\d+), (\d+), (\d+)\)$""").find(css)?.groupValues ?: return null
        return Color.rgb(parts[1].toInt().coerceAtMost(255), parts[2].toInt().coerceAtMost(255), parts[3].toInt().coerceAtMost(255))
    }
}
