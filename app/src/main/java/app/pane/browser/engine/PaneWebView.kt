package app.pane.browser.engine

import android.content.Context
import android.view.MotionEvent
import android.view.WindowInsets
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/**
 * One tab's page. A plain [WebView] that reports where it has scrolled and where it was last
 * touched (for the long-press menu). It is built on the activity it is shown in: WebView ties its
 * autofill, pop-ups and pickers to the context it is created with, for good, so a page lives only as
 * long as its window.
 */
class PaneWebView(window: Context) : WebView(window) {
    var tabId: String = ""

    init {
        // The platform's stretch (Android 12+) at the page's edges, like every other scroller in the app.
        overScrollMode = OVER_SCROLL_ALWAYS
    }

    /**
     * The page script lifts bottom ui above the floating bar, which already covers the navigation inset,
     * so that inset is not passed on as the page's `env(safe-area-inset-bottom)`: a page that pads for it
     * would count it twice.
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val bars = compat.getInsets(WindowInsetsCompat.Type.systemBars())
        val page = WindowInsetsCompat.Builder(compat)
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(bars.left, bars.top, bars.right, 0))
            .build()
        super.onApplyWindowInsets(page.toWindowInsets() ?: insets)
        return insets
    }

    /** Called with the vertical scroll position whenever it changes. */
    var onScroll: ((Int) -> Unit)? = null

    /**
     * The host of the page being loaded, set from the request thread (hence volatile) so the ad
     * blocker can tell a site's own requests from third parties' without touching the view.
     */
    @Volatile
    var siteHost: String? = null

    /** Where a finger last went down, in screen pixels. */
    var touchX = 0
    var touchY = 0

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScroll?.invoke(t)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchX = event.rawX.toInt()
            touchY = event.rawY.toInt()
        }
        return super.onTouchEvent(event)
    }

    /** Frees the page's memory for good. Safe to call twice. */
    fun release() {
        onScroll = null
        runCatching {
            stopLoading()
            webChromeClient = null
            setDownloadListener(null)
            setOnLongClickListener(null)
            (parent as? android.view.ViewGroup)?.removeView(this)
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
    }
}
