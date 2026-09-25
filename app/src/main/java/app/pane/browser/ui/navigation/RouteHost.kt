package app.pane.browser.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Presents [Navigator] screens over the browser with UINavigationController motion: the new
 * screen slides in from the right while the one beneath drifts left and dims. The system back
 * gesture scrubs the pop interactively and can be cancelled midway.
 */
@Composable
fun RouteHost(navigator: Navigator, content: @Composable (Route) -> Unit) {
    var displayed by remember { mutableStateOf(emptyList<Route>()) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val holder = rememberSaveableStateHolder()

    LaunchedEffect(navigator) {
        snapshotFlow { navigator.stack.toList() }.collect { target ->
            val current = displayed
            when {
                target.size > current.size && target.take(current.size) == current -> {
                    displayed = target
                    progress.snapTo(1f)
                    progress.animateTo(0f, Motion.push())
                }
                target.size < current.size && current.take(target.size) == target -> {
                    // Pop one or many: slide the top away, reveal the new top.
                    displayed = target + current.last()
                    progress.animateTo(1f, Motion.push())
                    current.drop(target.size).forEach { holder.removeState(it.toString()) }
                    displayed = target
                    progress.snapTo(0f)
                }
                else -> {
                    displayed = target
                    progress.snapTo(0f)
                }
            }
        }
    }

    PredictiveBackHandler(enabled = navigator.stack.isNotEmpty()) { events: Flow<BackEventCompat> ->
        try {
            events.collect { progress.snapTo(it.progress * 0.85f) }
            navigator.pop()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, Motion.bouncy()) }
            throw e
        }
    }

    if (displayed.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val p = progress.value
        val n = displayed.size
        // Dim the browser beneath the first screen.
        if (n == 1) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - p) * 0.18f }.background(Color.Black))
        }
        displayed.forEachIndexed { index, route ->
            if (index < n - 2) return@forEachIndexed
            val isTop = index == n - 1
            key(route) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (isTop) p * width else -0.28f * width * (1f - p)
                        },
                ) {
                    holder.SaveableStateProvider(route.toString()) { content(route) }
                    if (!isTop) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - p) * 0.12f }.background(Color.Black))
                    }
                }
                if (isTop && p > 0f) {
                    // Soft edge shadow on the leading side of the moving screen.
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(14.dp)
                            .align(Alignment.CenterStart)
                            .graphicsLayer { translationX = p * width - 14.dp.toPx(); alpha = 1f - p }
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)))),
                    )
                }
            }
        }
    }
}
