package app.pane.browser.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A choice between a few options that you can tap or swipe. A hairline-outlined pill with no fill:
 * the ink thumb rides under your finger, ticks over each option as it passes, and springs into the
 * nearest one when let go — with the speed of the flick, so a quick swipe carries on to the next
 * option. Plain, high-contrast labels: the chosen one reads paper-on-ink, the others ink on
 * nothing.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    if (options.isEmpty()) return
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val count = options.size.coerceAtLeast(1)
    val selection = selectedIndex.coerceIn(0, count - 1)
    val selectNow by rememberUpdatedState(onSelect)
    // The 1dp outline plus a 3dp gap, so the thumb never touches the line.
    val inset = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(PaneShapes.pill)
            .border(1.dp, colors.secondaryLabel, PaneShapes.pill)
            .padding(inset),
    ) {
        val segment = maxWidth / count
        val segmentPx = with(density) { segment.toPx() }
        val maxPos = segmentPx * (count - 1)

        // The thumb's position in pixels. Plain snapshot state, written directly while dragging and
        // by a spring when it settles, so following a finger never launches anything.
        var pos by remember { mutableFloatStateOf(selection * segmentPx) }
        var dragging by remember { mutableStateOf(false) }
        var settle by remember { mutableStateOf<Job?>(null) }
        val nearest by remember(segmentPx, count) { derivedStateOf { if (segmentPx <= 0f) 0 else (pos / segmentPx).roundToInt().coerceIn(0, count - 1) } }

        fun springTo(target: Float, velocity: Float = 0f) {
            settle?.cancel()
            settle = scope.launch {
                animate(pos, target, initialVelocity = velocity, animationSpec = if (reduceMotion) Motion.fade(0) else Motion.bouncy()) { v, _ -> pos = v }
            }
        }

        // A change from outside (or a tap) moves the thumb; a drag in progress owns it.
        LaunchedEffect(selection, segmentPx) {
            if (!dragging) springTo(selection * segmentPx)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .selectableGroup()
                .pointerInput(count, segmentPx, selection) {
                    if (segmentPx <= 0f) return@pointerInput
                    var last = selection
                    var velocity = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settle?.cancel()
                            dragging = true
                            last = nearest
                            velocity = 0f
                        },
                        onDragEnd = {
                            dragging = false
                            // A flick carries on to the next option; a slow drag lands on the nearest.
                            val bias = (velocity / 4000f).coerceIn(-0.5f, 0.5f)
                            val target = ((pos / segmentPx) + bias).roundToInt().coerceIn(0, count - 1)
                            springTo(target * segmentPx, velocity)
                            if (target != selection) selectNow(target)
                        },
                        onDragCancel = {
                            dragging = false
                            springTo(selection * segmentPx)
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            velocity = delta * 60f
                            pos = (pos + delta).coerceIn(0f, maxPos)
                            if (nearest != last) {
                                last = nearest
                                haptics.tick()
                            }
                        },
                    )
                },
        ) {
            // The ink thumb.
            Box(
                Modifier
                    .offset { IntOffset(pos.roundToInt(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // It swells a little while held, like something you've picked up.
                        val s = if (dragging) 1.04f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(PaneShapes.pill)
                    .background(colors.accent),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, label ->
                    val chosen = index == nearest
                    Box(
                        Modifier
                            .width(segment)
                            .fillMaxHeight()
                            .selectable(
                                selected = index == selection,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (index != selection) {
                                        haptics.tick()
                                        selectNow(index)
                                    }
                                    springTo(index * segmentPx)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = PaneTheme.type.callout.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (chosen) colors.onAccent else colors.label,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
