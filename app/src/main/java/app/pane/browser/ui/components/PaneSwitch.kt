package app.pane.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import kotlin.math.roundToInt

/**
 * The switch, flat: off is a hairline-outlined track with a small ink thumb, on is a solid ink track
 * with a paper thumb. You can tap it or drag the thumb; the thumb stretches while held, follows the
 * finger, and springs to whichever side it's nearer when let go. 52×32, so it is easy to hit.
 */
@Composable
fun PaneSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val changeNow by rememberUpdatedState(onCheckedChange)
    val reduceMotion = LocalReduceMotion.current

    val trackW = 52.dp
    val trackH = 32.dp
    val pad = 5.dp
    val restW = 22.dp
    val heldW = 30.dp
    val padPx = with(density) { pad.toPx() }
    val trackPx = with(density) { trackW.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }

    // While dragging, the thumb's offset in px; NaN otherwise, when the spring below owns it.
    var drag by remember { mutableFloatStateOf(Float.NaN) }
    val settled = animateFloatAsState(if (checked) 1f else 0f, if (reduceMotion) Motion.fade(0) else Motion.bouncy(), label = "switch")
    val width = animateFloatAsState(with(density) { (if (pressed || !drag.isNaN()) heldW else restW).toPx() }, if (reduceMotion) Motion.fade(0) else Motion.snappy(), label = "thumbW")
    val alpha = animateFloatAsState(if (enabled) 1f else 0.4f, Motion.fade(), label = "alpha")

    Box(
        modifier = modifier
            .width(trackW)
            .height(trackH)
            .drawBehind {
                val fraction = position(drag, settled.value, trackPx, padPx, width.value)
                val a = alpha.value
                // Solid ink fills the track as the thumb travels across it...
                val fill = colors.accent
                drawRoundRect(fill.copy(alpha = fill.alpha * fraction * a), cornerRadius = CornerRadius(size.height / 2f))
                // ...and the outline turns from a hairline into that same ink, so it vanishes into it.
                val line = lerp(colors.secondaryLabel, colors.accent, fraction)
                drawRoundRect(
                    color = line.copy(alpha = line.alpha * a),
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius((size.height - strokePx) / 2f),
                    style = Stroke(strokePx),
                )
            }
            .pointerInput(enabled, checked, trackPx, padPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { drag = settled.value * (trackPx - width.value - padPx * 2) },
                    onDragEnd = {
                        val travel = trackPx - width.value - padPx * 2
                        val on = drag > travel / 2f
                        drag = Float.NaN
                        if (on != checked) {
                            haptics.toggle(on)
                            changeNow(on)
                        }
                    },
                    onDragCancel = { drag = Float.NaN },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        val travel = trackPx - width.value - padPx * 2
                        drag = (drag + delta).coerceIn(0f, travel)
                    },
                )
            }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = null,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layout { measurable, _ ->
                    val w = width.value.roundToInt()
                    val h = (trackH - pad * 2).roundToPx()
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                    layout(w, h) {
                        val travel = trackPx - width.value - padPx * 2
                        val fraction = position(drag, settled.value, trackPx, padPx, width.value)
                        placeable.place((padPx + fraction * travel).roundToInt(), 0)
                    }
                }
                .drawBehind {
                    val fraction = position(drag, settled.value, trackPx, padPx, width.value)
                    // A small ink dot when off; it grows and turns to paper as the track fills.
                    val scale = 0.68f + 0.32f * fraction
                    val inset = size.height * (1f - scale) / 2f
                    val color = lerp(colors.label, colors.onAccent, fraction)
                    drawRoundRect(
                        color = color.copy(alpha = color.alpha * alpha.value),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius((size.height - inset * 2f) / 2f),
                    )
                },
        )
    }
}

/** The thumb's position as 0..1 along its travel, from the finger while dragging or the spring otherwise. */
private fun position(drag: Float, settled: Float, trackPx: Float, padPx: Float, thumbPx: Float): Float {
    val travel = (trackPx - thumbPx - padPx * 2).coerceAtLeast(1f)
    return if (drag.isNaN()) settled.coerceIn(0f, 1f) else (drag / travel).coerceIn(0f, 1f)
}
