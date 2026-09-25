package app.pane.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics

/** The iOS switch: 51×31 track, springy thumb that stretches while pressed. */
@Composable
fun PaneSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) colors.positive else colors.fill, Motion.fade(200), label = "track")
    val thumbWidth by animateDpAsState(if (pressed) 34.dp else 27.dp, Motion.snappy(), label = "thumbW")
    val offset by animateDpAsState(
        if (checked) 51.dp - 2.dp - thumbWidth else 2.dp,
        Motion.spring(0.35f, 0.78f),
        label = "thumbX",
    )
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    Box(
        modifier = modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(50))
            .background(track.copy(alpha = track.alpha * alpha))
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
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(thumbWidth)
                .height(27.dp)
                .shadow(3.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
    }
}
