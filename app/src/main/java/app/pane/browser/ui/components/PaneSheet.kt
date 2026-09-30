package app.pane.browser.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.GlassStrength
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.glass
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
    val dismissNow by rememberUpdatedState(onDismiss)
    val reduceMotion = LocalReduceMotion.current
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    // Starts far off-screen so nothing flashes before the first measurement.
    val offset = remember { Animatable(OFFSCREEN) }
    var shown by remember { mutableStateOf(false) }
    var measuredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(visible, measuredOnce) {
        if (visible) {
            shown = true
            if (!measuredOnce) return@LaunchedEffect
            offset.animateTo(0f, if (reduceMotion) Motion.fade(0) else Motion.bouncy())
        } else if (shown) {
            offset.animateTo(sheetHeight.coerceAtLeast(1f) + with(density) { 160.dp.toPx() }, if (reduceMotion) Motion.fade(0) else Motion.smooth())
            shown = false
            measuredOnce = false
        }
    }

    if (!shown) return

    fun settle(velocity: Float) {
        scope.launch {
            if (offset.value > sheetHeight * 0.3f || velocity > 1400f) {
                dismissNow()
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
            dismissNow()
        } catch (e: CancellationException) {
            scope.launch { offset.animateTo(0f, Motion.bouncy()) }
            throw e
        }
    }

    val travelExtra = with(density) { 160.dp.toPx() }
    val origin = LocalSheetOrigin.current
    var cardBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxHeight = maxHeight * maxHeightFraction
        val travel = sheetHeight + travelExtra
        val progress = if (sheetHeight > 0f) (1f - offset.value / travel).coerceIn(0f, 1f) else 0f
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .onSizeChanged {
                    val h = it.height.toFloat()
                    if (!measuredOnce && h > 0f) {
                        sheetHeight = h
                        scope.launch {
                            offset.snapTo(h + travelExtra)
                            measuredOnce = true
                        }
                    } else {
                        sheetHeight = h
                    }
                }
                .onGloballyPositioned { cardBounds = it.boundsInRoot() }
                .graphicsLayer {
                    if (origin != null && cardBounds.width > 1f) {
                        // Grows out of the control that opened it, and shrinks back into it.
                        val p = 1f - offset.value / travel
                        val s = 0.3f + 0.7f * p.coerceIn(0f, 1.12f)
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            ((origin.x - cardBounds.left) / cardBounds.width),
                            ((origin.y - cardBounds.top) / cardBounds.height),
                        )
                        alpha = (p * 1.5f).coerceIn(0f, 1f)
                        // A drag on the card still moves it.
                        translationY = (offset.value.coerceAtLeast(0f) - (1f - p.coerceIn(0f, 1f)) * travel).coerceAtLeast(0f)
                    } else {
                        translationY = offset.value.coerceAtLeast(0f)
                        // A whisper of scale as it lands, so the card feels like it settles into place.
                        val s = 0.96f + 0.04f * progress
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                }
                .glass(PaneShapes.floating, GlassStrength.Thick)
                .pointerInput(Unit) { detectTapGestures { } }
                .nestedScroll(nested),
        ) {
            // Grabber; also the drag handle for sheets whose content doesn't scroll.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
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
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.tertiaryLabel),
                )
            }
            content()
        }
    }
}

/** Where a sheet should grow from (root coordinates), e.g. the menu button; null slides it up from the bottom. */
val LocalSheetOrigin = androidx.compose.runtime.staticCompositionLocalOf<Offset?> { null }

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
