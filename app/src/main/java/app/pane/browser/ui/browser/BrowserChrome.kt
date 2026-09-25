package app.pane.browser.ui.browser

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import app.pane.browser.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoView
import kotlin.coroutines.resume

/**
 * Transient state of the browser chrome: how collapsed the toolbar is, which overlay is up, and
 * the page snapshots used to animate between pages and tabs.
 */
@Stable
class BrowserChrome(private val scope: CoroutineScope) {
    /** 0 = toolbar fully expanded, 1 = collapsed to the slim host label. */
    val collapse = Animatable(0f)

    var editing by mutableStateOf(false)
    var showTabs by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var findInPage by mutableStateOf(false)
    var siteInfo by mutableStateOf(false)

    /** Where the web content is drawn, in root coordinates; the anchor for zoom transitions. */
    var pageRect by mutableStateOf(Rect.Zero)

    /** Snapshot drawn over the page while a gesture or transition is in flight. */
    var overlay by mutableStateOf<PageOverlay?>(null)

    var geckoView: GeckoView? = null

    /** Horizontal tab-swipe progress from the address bar, -1…1 (negative = towards the next tab). */
    var tabSwipe by mutableFloatStateOf(0f)

    /** Back-gesture progress 0…1 and the edge it started from. */
    val back = Animatable(0f)
    var backFromRight by mutableStateOf(false)

    private var settleJob: Job? = null

    val anyOverlay: Boolean get() = editing || showTabs || showMenu || findInPage || siteInfo

    fun expand() {
        settleJob?.cancel()
        scope.launch { collapse.animateTo(0f, Motion.snappy()) }
    }

    /** Follows page scrolling like Safari: hide as content moves up, return when it moves down. */
    fun onScroll(deltaY: Int, range: Float) {
        if (anyOverlay) return
        settleJob?.cancel()
        scope.launch { collapse.snapTo((collapse.value + deltaY / range).coerceIn(0f, 1f)) }
        settleJob = scope.launch {
            delay(140)
            collapse.animateTo(if (collapse.value > 0.45f) 1f else 0f, Motion.snappy())
        }
    }

    /** Grabs the visible page. Returns null if Gecko has nothing drawn or takes too long. */
    suspend fun capture(timeoutMs: Long = 250): Bitmap? {
        val view = geckoView ?: return null
        if (view.session == null) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                try {
                    view.capturePixels().accept({ bmp -> if (cont.isActive) cont.resume(bmp) }, { _ -> if (cont.isActive) cont.resume(null) })
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}

/** A bitmap layered over the page area, positioned by the owning transition. */
data class PageOverlay(
    val current: Bitmap?,
    val behind: Bitmap?,
    val behindTitle: String? = null,
    val kind: Kind,
) {
    enum class Kind { Back, TabSwipe, Cover }
}
