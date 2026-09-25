package app.pane.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.rememberHaptics

/**
 * iOS-style press feedback: the element shrinks slightly and springs back, no ripple.
 * Used for cards, tiles and prominent buttons.
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    haptic: Boolean = false,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.snappy(), label = "pressScale")
    val haptics = rememberHaptics()
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick?.let { { haptics.longPress(); it() } },
            onClick = { if (haptic) haptics.tap(); onClick() },
        )
}

/** iOS toolbar-button feedback: dims while pressed. */
fun Modifier.pressDim(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        when {
            !enabled -> 0.3f
            pressed -> 0.35f
            else -> 1f
        },
        Motion.snappy(),
        label = "pressDim",
    )
    this
        .graphicsLayer { this.alpha = alpha }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}
