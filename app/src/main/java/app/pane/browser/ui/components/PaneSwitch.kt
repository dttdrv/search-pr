package app.pane.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
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
 * The switch: an ink track and a thumb you can tap or drag. The thumb stretches while held, follows
 * the finger, and springs to whichever side it's nearer when let go. 56×34, so it is easy to hit.
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

    val trackW = 56.dp
    val trackH = 34.dp
    val pad = 3.dp
    val restW = 28.dp
    val heldW = 36.dp
    val padPx = with(density) { pad.toPx() }
    val trackPx = with(density) { trackW.toPx() }

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
                val track = lerp(colors.fill, colors.accent, fraction)
                drawRoundRect(track.copy(alpha = track.alpha * alpha.value), cornerRadius = CornerRadius(size.height / 2f))
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
                .shadow(3.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .drawBehind {
                    drawRoundRect(
                        if (checked) colors.onAccent else Color.White,
                        cornerRadius = CornerRadius(size.height / 2f),
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
