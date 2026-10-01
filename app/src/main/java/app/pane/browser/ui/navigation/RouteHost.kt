package app.pane.browser.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.pane.browser.ui.theme.LocalEntranceMemory
import app.pane.browser.ui.theme.EntranceMemory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Presents [Navigator] screens over the browser: the new screen slides in from the right on a
 * critically damped spring while whatever is beneath drifts left, settles back slightly smaller
 * and dims. The system back gesture scrubs the pop interactively and can be cancelled midway.
 * With reduced motion the screens cross-fade in place instead.
 *
 * Every frame of the transition is read inside `graphicsLayer` lambdas, so nothing recomposes
 * while it runs. [underlay], if given, is kept at 1 while a screen sits over the browser (and
 * eases between 0 and 1 as it arrives or leaves) so the caller can push the browser back too.
 */
@Composable
fun RouteHost(navigator: Navigator, underlay: MutableFloatState? = null, content: @Composable (Route) -> Unit) {
    var displayed by remember { mutableStateOf(emptyList<Route>()) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val holder = rememberSaveableStateHolder()
    // One memory of finished entrance animations per screen on the stack.
    val memories = remember { HashMap<Route, EntranceMemory>() }
    val reduce = LocalReduceMotion.current
    val reduceNow by rememberUpdatedState(reduce)

    fun pushSpec(): AnimationSpec<Float> = if (reduceNow) Motion.fade(200) else Motion.push()

    LaunchedEffect(navigator) {
        snapshotFlow { navigator.stack.toList() }.collect { target ->
            val current = displayed
            when {
                target.size > current.size && target.take(current.size) == current -> {
                    displayed = target
                    progress.takeOver { snapTo(1f) }
                    progress.takeOver { animateTo(0f, pushSpec()) }
                }
                target.size < current.size && current.take(target.size) == target -> {
                    // Pop one or many: slide the top away, reveal the new top.
                    displayed = target + current.last()
                    progress.takeOver { animateTo(1f, pushSpec()) }
                    current.drop(target.size).forEach {
                        holder.removeState(it.toString())
                        memories.remove(it)
                    }
                    displayed = target
                    progress.takeOver { snapTo(0f) }
                }
                else -> {
                    displayed = target
                    progress.takeOver { snapTo(0f) }
                }
            }
        }
    }

    if (underlay != null) {
        LaunchedEffect(underlay) {
            snapshotFlow {
                when (displayed.size) {
                    0 -> 0f
                    1 -> 1f - progress.value
                    else -> 1f
                }
            }.collect { underlay.floatValue = it }
        }
    }

    PredictiveBackHandler(enabled = navigator.stack.isNotEmpty()) { events: Flow<BackEventCompat> ->
        try {
            events.collect { progress.takeOver { snapTo(it.progress * 0.85f) } }
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, Motion.smooth()) }
            throw e
        }
    }

    if (displayed.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val n = displayed.size
        // Dim the browser beneath the first screen.
        if (n == 1) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - progress.value) * 0.18f }.background(Color.Black))
        }
        displayed.forEachIndexed { index, route ->
            if (index < n - 2) return@forEachIndexed
            val isTop = index == n - 1
            key(route) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress.value
                            when {
                                reduce -> alpha = if (isTop) 1f - p else 1f
                                isTop -> translationX = p * width
                                else -> {
                                    // The screen beneath drifts left and steps back a little.
                                    translationX = -0.28f * width * (1f - p)
                                    val s = 1f - 0.06f * (1f - p)
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                        },
                ) {
                    holder.SaveableStateProvider(route.toString()) {
                        CompositionLocalProvider(LocalEntranceMemory provides memories.getOrPut(route) { EntranceMemory() }) { content(route) }
                    }
                    if (!isTop) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - progress.value) * 0.12f }.background(Color.Black))
                    }
                }
                if (isTop && !reduce) {
                    // Soft edge shadow on the leading side of the moving screen.
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(14.dp)
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                val p = progress.value
                                translationX = p * width - 14.dp.toPx()
                                alpha = 1f - p
                            }
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)))),
                    )
                }
            }
        }
    }
}

/**
 * Runs [block] on [this], which a back gesture can take over at any moment. The takeover cancels the
 * block, not the coroutine that called it, so the caller carries on: if it were cancelled too, the
 * screen stack would stop following the navigator for good.
 */
private suspend fun Animatable<Float, AnimationVector1D>.takeOver(block: suspend Animatable<Float, AnimationVector1D>.() -> Unit) {
    try {
        block()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}
