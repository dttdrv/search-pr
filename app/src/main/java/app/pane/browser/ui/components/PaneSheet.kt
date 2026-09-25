package app.pane.browser.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A modal sheet that springs up from the bottom, follows the finger (including from nested
 * scrolling content at its top), dismisses on a flick, and shrinks away with the predictive back
 * gesture.
 */
@Composable
fun PaneSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.92f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaneTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    // Starts far off-screen so nothing flashes before the first measurement.
    val offset = remember { Animatable(OFFSCREEN) }
    var shown by remember { mutableStateOf(false) }
    var measuredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(visible, measuredOnce) {
        if (visible) {
            shown = true
            if (!measuredOnce) return@LaunchedEffect
            offset.animateTo(0f, Motion.bouncy())
        } else if (shown) {
            offset.animateTo(sheetHeight.coerceAtLeast(1f), Motion.smooth())
            shown = false
            measuredOnce = false
        }
    }

    if (!shown) return

    fun settle(velocity: Float) {
        scope.launch {
            if (offset.value > sheetHeight * 0.3f || velocity > 1400f) {
                onDismiss()
            } else {
                offset.animateTo(0f, Motion.bouncy(), initialVelocity = velocity)
            }
        }
    }

    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offset.value > 0f) {
                    val consumed = maxOf(available.y, -offset.value)
                    scope.launch { offset.snapTo(offset.value + consumed) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    scope.launch { offset.snapTo(offset.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    PredictiveBackHandler(enabled = visible) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { event -> offset.snapTo(event.progress * sheetHeight * 0.25f) }
            onDismiss()
        } catch (e: CancellationException) {
            scope.launch { offset.animateTo(0f, Motion.bouncy()) }
            throw e
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxHeight = maxHeight * maxHeightFraction
        val progress = if (sheetHeight > 0f) (1f - offset.value / sheetHeight).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .onSizeChanged {
                    val h = it.height.toFloat()
                    if (!measuredOnce && h > 0f) {
                        sheetHeight = h
                        scope.launch {
                            offset.snapTo(h)
                            measuredOnce = true
                        }
                    } else {
                        sheetHeight = h
                    }
                }
                .offset { IntOffset(0, offset.value.coerceAtLeast(0f).roundToInt()) }
                .shadow(24.dp, PaneShapes.sheet, ambientColor = colors.shadow, spotColor = colors.shadow)
                .clip(PaneShapes.sheet)
                .background(if (colors.isDark) colors.surface else colors.groupedBackground)
                .nestedScroll(nested)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            // Grabber; also the drag handle for sheets whose content doesn't scroll.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.tertiaryLabel),
                )
            }
            content()
        }
    }
}

private const val OFFSCREEN = 100_000f

/** Title row for sheets: centred headline with an optional trailing "Done". */
@Composable
fun SheetHeader(title: String, onDone: (() -> Unit)? = null, doneLabel: String = "Done") {
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
        androidx.compose.material3.Text(
            title,
            style = PaneTheme.type.headline,
            color = PaneTheme.colors.label,
            modifier = Modifier.align(Alignment.Center),
        )
        if (onDone != null) {
            TextButton(doneLabel, onClick = onDone, bold = true, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
