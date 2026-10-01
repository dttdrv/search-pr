package app.pane.browser.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What has already arrived on one screen. A screen keeps its memory for as long as it is on the
 * navigation stack, so coming back to it (the back gesture from a screen above) shows it settled
 * instead of playing every entrance again. Leaving it for good drops the memory.
 */
class EntranceMemory {
    internal val played = HashSet<Int>()
}

/** The memory of the screen being composed; null where entrances should play every time (sheets, dialogs). */
val LocalEntranceMemory = compositionLocalOf<EntranceMemory?> { null }

/**
 * Lets an element arrive instead of appearing: it fades up from a few dp below without overshoot,
 * each [index] a short beat after the one before it (capped, so long lists don't crawl). Plays
 * once per [key] and, on a screen with an [EntranceMemory], once for as long as the screen lives;
 * with reduced motion on it just fades.
 */
fun Modifier.entrance(index: Int = 0, key: Any? = Unit, rise: Float = 8f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val memory = LocalEntranceMemory.current
    // Where this call sits in the composition, so the same element is recognised after a re-entry.
    val id = currentCompositeKeyHash * 31 + index * 7 + (key?.hashCode() ?: 0)
    val seen = remember(key, memory) { memory?.played?.contains(id) == true }
    val progress = remember(key, memory) { Animatable(if (seen) 1f else 0f) }
    LaunchedEffect(key, memory) {
        if (seen) return@LaunchedEffect
        memory?.played?.add(id)
        delay((index.coerceAtMost(6) * 20).toLong())
        progress.animateTo(1f, if (reduce) Motion.fade(160) else Motion.smooth())
    }
    val risePx = with(density) { rise.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        if (!reduce) translationY = (1f - p) * risePx
    }
}

/** Like [entrance] for something that should drop in from above (banners, toasts). */
fun Modifier.dropIn(key: Any? = Unit): Modifier = composed {
    val density = LocalDensity.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { launch { progress.animateTo(1f, Motion.smooth()) } }
    val dropPx = with(density) { 24.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = -(1f - p) * dropPx
    }
}
