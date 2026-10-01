package app.pane.browser.ui.components

import app.pane.browser.ui.icons.PaneIcons
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import app.pane.browser.ui.theme.FloatingElevation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A modal sheet that slides up from the bottom, follows a drag down from anywhere on it (the handle,
 * its rows, the scrim around it) or from scrolling content back at its top, dismisses on a flick,
 * stretches like every other surface when pulled up, and shrinks away with the predictive back
 * gesture.
 */
@Composable
fun PaneSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.92f,
    origin: Rect = Rect.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaneTheme.colors
    val scope = rememberCoroutineScope()
    val dismissNow by rememberUpdatedState(onDismiss)
    val reduceMotion = LocalReduceMotion.current
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    // From where the card rests to just below the screen.
    var travel by remember { mutableFloatStateOf(0f) }
    // Starts far off-screen so nothing flashes before the first measurement.
    val offset = remember { Animatable(OFFSCREEN) }
    // 0 = the sheet is still its source (a button), 1 = at rest; a sheet with no source stays at 1
    val reveal = remember { Animatable(1f) }
    var rest by remember { mutableStateOf(Rect.Zero) }
    val morphing by rememberUpdatedState(origin != Rect.Zero && !reduceMotion)
    var shown by remember { mutableStateOf(false) }
    var measuredOnce by remember { mutableStateOf(false) }

    LaunchedEffect(visible, measuredOnce) {
        if (visible) {
            shown = true
            if (!measuredOnce) return@LaunchedEffect
            if (morphing) reveal.animateTo(1f, Motion.smooth()) else offset.animateTo(0f, if (reduceMotion) Motion.fade(0) else Motion.smooth())
        } else if (shown) {
            // lowered by a drag it carries on down; otherwise it folds back into its button
            if (morphing && offset.value == 0f) reveal.animateTo(0f, Motion.smooth()) else offset.animateTo(travel, if (reduceMotion) Motion.fade(0) else Motion.smooth())
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
                offset.animateTo(0f, Motion.smooth(), initialVelocity = velocity)
            }
        }
    }

    // Every drag on the sheet passes through here. Up first lifts a lowered card and what is left
    // stretches it; down lowers the card once any scrolling content is back at its top.
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
            scope.launch { offset.animateTo(0f, Motion.smooth()) }
            throw e
        }
    }

    val stretch = rememberOverscrollEffect()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .nestedScroll(nested)
            // Moves nothing (see Modifier.stretch): it hands a drag anywhere on the sheet to [nested] and the card's stretch.
            .scrollable(rememberScrollableState { 0f }, Orientation.Vertical, enabled = visible, overscrollEffect = stretch),
    ) {
        val maxHeight = maxHeight * maxHeightFraction
        val bottom = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (travel > 0f) (1f - offset.value / travel).coerceIn(0f, 1f) * reveal.value else 0f }
                .background(colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.tappableElement)
                .imePadding()
                .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.gutter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                // Outside the slide below, so this is where the card rests.
                .onPlaced {
                    sheetHeight = it.size.height.toFloat()
                    travel = bottom - it.positionInParent().y
                    scope.launch {
                        if (!measuredOnce) {
                            if (morphing) {
                                offset.snapTo(0f)
                                reveal.snapTo(0f)
                            } else {
                                offset.snapTo(travel)
                                reveal.snapTo(1f)
                            }
                            measuredOnce = true
                        }
                    }
                }
                .onGloballyPositioned { rest = it.boundsInRoot() }
                .graphicsLayer {
                    translationY = offset.value
                    shadowElevation = FloatingElevation.toPx()
                    shape = sheetShape(origin.translate(-rest.topLeft), reveal.value)
                    clip = reveal.value >= 1f
                }
                .drawWithContent {
                    val outline = sheetShape(origin.translate(-rest.topLeft), reveal.value).createOutline(size, layoutDirection, this)
                    drawOutline(outline, colors.background)
                    if (reveal.value >= 1f) drawContent() else clipPath(Path().apply { addOutline(outline) }) { this@drawWithContent.drawContent() }
                }
                .pointerInput(Unit) { detectTapGestures { } }
                // Inside the card, like a list in its frame: the rows stretch, the card keeps its shape.
                .overscroll(stretch)
                .padding(bottom = Spacing.gutter),
        ) {
            // what the sheet holds comes in once it has grown most of the way out of its button
            Column(Modifier.graphicsLayer { alpha = ((reveal.value - ContentFrom) / (1f - ContentFrom)).coerceIn(0f, 1f) }) {
                // A short, quiet handle, inside the top margin.
                Box(Modifier.fillMaxWidth().height(Spacing.gutter), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(3.dp)
                            .clip(PaneShapes.pill)
                            .background(colors.faint),
                    )
                }
                content()
            }
        }
    }
}

/** The radius of a sheet's corners, which every corner inside it derives from. */
private val SheetRadius = 32.dp

/**
 * Corners of a card set in a sheet by [Spacing.gutter]: concentric with the sheet's, so the gap
 * between the two curves is as wide round the corner as along the sides.
 */
val SheetCardRadius = (SheetRadius - Spacing.gutter).coerceAtLeast(0.dp)

/** A sheet's corners: a flat floating card. */
private val SheetShape = ContinuousRoundedShape(SheetRadius)

private const val ContentFrom = 0.45f

/** The sheet [p] of the way out of [from], its button's bounds in the sheet's own space: a rounded box between the two. */
private class Grown(val from: Rect, val p: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(
        RoundRect(
            left = lerp(from.left, 0f, p),
            top = lerp(from.top, 0f, p),
            right = lerp(from.right, size.width, p),
            bottom = lerp(from.bottom, size.height, p),
            cornerRadius = CornerRadius(lerp(from.height / 2f, with(density) { SheetRadius.toPx() }, p)),
        ),
    )
}

private fun sheetShape(from: Rect, p: Float): Shape = if (p >= 1f) SheetShape else Grown(from, p)

private const val OFFSCREEN = 100_000f

/** Title row for sheets: one line of `title3`, with an optional text button ("Done") at the end. */
@Composable
fun SheetHeader(title: String, onDone: (() -> Unit)? = null, doneLabel: String = "Done") {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 24.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            title,
            style = PaneTheme.type.title3,
            color = PaneTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onDone != null) {
            ChromeButton(PaneIcons.Close, doneLabel, onClick = onDone)
        }
    }
}
