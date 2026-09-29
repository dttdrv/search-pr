package app.pane.browser.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lets an element arrive instead of appearing: it fades up from a few dp below and settles on a
 * spring, each [index] a beat after the one before it (capped, so long lists don't crawl). Plays
 * once per [key]; with reduced motion on it just fades.
 */
fun Modifier.entrance(index: Int = 0, key: Any? = Unit, rise: Float = 14f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(9) * 38).toLong())
        if (reduce) progress.animateTo(1f, Motion.fade(160)) else progress.animateTo(1f, Motion.spring(0.5f, 0.82f))
    }
    val risePx = with(density) { rise.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        if (!reduce) {
            translationY = (1f - p) * risePx
            val s = 0.97f + 0.03f * p
            scaleX = s
            scaleY = s
        }
    }
}

/** Like [entrance] for something that should drop in from above (banners, toasts). */
fun Modifier.dropIn(key: Any? = Unit): Modifier = composed {
    val density = LocalDensity.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { launch { progress.animateTo(1f, Motion.bouncy()) } }
    val dropPx = with(density) { 24.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = -(1f - p) * dropPx
    }
}
