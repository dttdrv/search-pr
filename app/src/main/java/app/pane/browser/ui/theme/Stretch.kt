package app.pane.browser.ui.theme

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * The platform stretch on a whole surface, even when its content fits and nothing can scroll.
 * Compose only feeds overscroll to a scrollable that says it can move; this one moves nothing and
 * keeps [ScrollableState]'s default answer that it always can. A scroller inside takes the drag
 * only while it [canScroll], and then stretches on its own.
 */
fun Modifier.stretch(): Modifier = composed {
    val effect = rememberOverscrollEffect()
    overscroll(effect).scrollable(rememberScrollableState { 0f }, Orientation.Vertical, overscrollEffect = effect)
}

/** Whether there is anywhere to scroll; a scroller without it leaves its drags to [stretch]. */
val ScrollableState.canScroll: Boolean get() = canScrollForward || canScrollBackward
