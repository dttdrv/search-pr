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
import kotlinx.coroutines.flow.MutableStateFlow
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
    val collapse = Collapse()

    var editing by mutableStateOf(false)
    var showTabs by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var findInPage by mutableStateOf(false)
    var siteInfo by mutableStateOf(false)

    /** Which tabs the tab overview lists: the private ones, or the normal ones. */
    var showPrivateTabs by mutableStateOf(false)

    /** Where the menu button sits; the menu grows out of it. */
    var menuRect by mutableStateOf(Rect.Zero)

    /** Where the address pill sits, in root coordinates; the address editor's field grows out of it. */
    var pillRect by mutableStateOf(Rect.Zero)

    /** Where the web content is drawn, in root coordinates; the anchor for zoom transitions. */
    var pageRect by mutableStateOf(Rect.Zero)

    /** Snapshot drawn over the page while a gesture or transition is in flight. */
    var overlay by mutableStateOf<PageOverlay?>(null)

    var geckoView by mutableStateOf<GeckoView?>(null)

    /** Whether the page has scrolled away from its top; the status area then dissolves into it. */
    var scrolled by mutableStateOf(false)

    /** The colours along the page's edges for the tab on screen; null until the page has painted. */
    var edges by mutableStateOf<PageEdges?>(null)
        private set
    private val edgeCache = HashMap<String, PageEdges>()

    /** Switching tabs shows that tab's last known colours straight away instead of the old page's. */
    fun restoreEdges(tabId: String?) {
        edges = tabId?.let(edgeCache::get)
    }

    fun forgetEdges(tabId: String) {
        edgeCache.remove(tabId)
    }

    /** Reads the page's edge colours from what Gecko has drawn. */
    suspend fun sampleEdges(tabId: String, bottomBandPx: Int, isCurrent: () -> Boolean) {
        val bitmap = capture(220) ?: return
        if (isCurrent()) applyEdges(tabId, bitmap, bottomBandPx)
    }

    fun applyEdges(tabId: String, bitmap: Bitmap, bottomBandPx: Int) {
        val sampled = PageColors.sample(bitmap, bottomBandPx) ?: return
        edgeCache[tabId] = sampled
        edges = sampled
    }

    /** Horizontal tab-swipe progress from the address bar, -1…1 (negative = towards the next tab). */
    var tabSwipe by mutableFloatStateOf(0f)

    /** Back-gesture progress 0…1 and the edge it started from. */
    val back = Animatable(0f)
    var backFromRight by mutableStateOf(false)

    private var settleJob: Job? = null

    val anyOverlay: Boolean get() = editing || showTabs || showMenu || findInPage || siteInfo

    fun expand() {
        settleJob?.cancel()
        settleJob = scope.launch { collapse.animateTo(0f, Motion.snappy()) }
    }

    /**
     * Follows page scrolling: the bar melts away as content moves up and returns as it moves down.
     * Called for every scroll event, so it only writes a float; nothing is launched here.
     */
    fun onScroll(deltaY: Int, range: Float) {
        if (anyOverlay) return
        settleJob?.cancel()
        if (range > 0f) collapse.snap(collapse.value + deltaY / range)
    }

    /** Scrolling has paused: finish whichever way the bar was heading. */
    fun settle() {
        if (anyOverlay) return
        val target = if (collapse.value > 0.45f) 1f else 0f
        if (collapse.value == target) return
        settleJob?.cancel()
        settleJob = scope.launch { collapse.animateTo(target, Motion.snappy()) }
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

    companion object {
        /**
         * Whether the tab overview is up on the private tabs. The browser screen keeps it in sync so
         * the activity blocks screenshots then too, not only while a private tab is selected.
         */
        val privateTabsShowing = MutableStateFlow(false)

        /** Whether the page on screen is a locked private tab; its prompts wait until it's unlocked. */
        val pageLocked = MutableStateFlow(false)
    }
}

/**
 * How melted the bar is. A plain float in snapshot state (not an Animatable) so scroll events can
 * write it directly, without a coroutine per event; [animateTo] springs it home when scrolling stops.
 */
@Stable
class Collapse {
    var value by mutableFloatStateOf(0f)
        private set

    fun snap(v: Float) {
        value = v.coerceIn(0f, 1f)
    }

    suspend fun animateTo(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        androidx.compose.animation.core.animate(value, target, animationSpec = spec) { v, _ -> value = v }
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
