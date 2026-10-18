package app.pane.browser.engine

import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * what a press on a page looks like (assets/press.js): the platform's own ripple on whatever was pressed, after
 * the platform's tap timeout, in place of the engine's flat tap highlight.
 */
object PressFeedback {
    private var script: String? = null

    fun attach(page: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val js = script ?: page.context.assets.open("press.js").bufferedReader().use { it.readText() }.also { script = it }
        runCatching { WebViewCompat.addDocumentStartJavaScript(page, "($js)(${ViewConfiguration.getTapTimeout()})", setOf("*")) }
    }
}
