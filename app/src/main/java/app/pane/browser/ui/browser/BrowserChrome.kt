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

    /** Where the address pill sits, in root coordinates; the address editor's field grows out of it. */
    var pillRect by mutableStateOf(Rect.Zero)

    /** Where the open menu button sits, in root coordinates; the menu sheet grows out of it. */
    var menuRect by mutableStateOf(Rect.Zero)

    /** Where the web content is drawn, in root coordinates; the anchor for zoom transitions. */
    var pageRect by mutableStateOf(Rect.Zero)

    /** Snapshot drawn over the page while a gesture or transition is in flight. */
    var overlay by mutableStateOf<PageOverlay?>(null)

    /** Draws the page on screen into a bitmap at full resolution; set by the screen. */
    var snapshotSource: (suspend () -> Bitmap?)? = null

    /** Whether the page has scrolled away from its top; the status area then dissolves into it. */
    var scrolled by mutableStateOf(false)

    /** Horizontal tab-swipe progress from the address bar, -1…1 (negative = towards the next tab). */
    var tabSwipe by mutableFloatStateOf(0f)

    /** Back-gesture progress 0…1 and the edge it started from. */
    val back = Animatable(0f)
    var backFromRight by mutableStateOf(false)

    private var settleJob: Job? = null

    val anyOverlay: Boolean get() = editing || showTabs || showMenu || findInPage || siteInfo

    fun expand() = head(0f)

    /** Sends the bar towards [target] on one spring; a new target takes over its motion, speed and all, without a seam. */
    private fun head(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch { collapse.animateTo(target, Motion.smooth()) }
    }

    /**
     * Follows page scrolling: the bar melts away as content moves up and returns as it moves down. The
     * scroll moves where the bar is heading and the bar follows on a spring, so a fling can't snap it away
     * in a frame or two.
     */
    fun onScroll(deltaY: Int, range: Float) {
        if (anyOverlay || range <= 0f) return
        val target = (collapse.target + deltaY / range).coerceIn(0f, 1f)
        if (target != collapse.target) head(target)
    }

    /** The page was let go and has stopped: finish whichever way the bar was heading. */
    fun settle() {
        if (anyOverlay) return
        val target = if (collapse.target > 0.45f) 1f else 0f
        if (collapse.target != target) head(target)
    }

    /** Grabs the visible page; null if nothing is drawn. */
    suspend fun capture(): Bitmap? = snapshotSource?.invoke()

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

/** How melted the bar is, and where it is heading. */
@Stable
class Collapse {
    private val motion = Animatable(0f)
    val value: Float get() = motion.value

    var target = 0f
        private set

    /** Keeps the speed it already has, so one motion hands off to the next. */
    suspend fun animateTo(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
        this.target = target.coerceIn(0f, 1f)
        motion.animateTo(this.target, spec)
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
