package app.pane.browser.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A solid surface that floats over the page: the address pill, round buttons, menus, sheets. One
 * flat fill, a hairline and (for things that hover over content) a short soft shadow. Nothing is
 * blurred or redrawn from what is behind it, so it costs no more than a rectangle. Apply it before
 * padding so the fill spans the full bounds.
 */
@Composable
fun Modifier.floating(shape: Shape, shadow: Dp = 8.dp): Modifier {
    val colors = PaneTheme.colors
    return this
        .then(
            if (shadow > 0.dp) {
                Modifier.shadow(
                    elevation = shadow,
                    shape = shape,
                    ambientColor = colors.shadow.copy(alpha = colors.shadow.alpha * 0.5f),
                    spotColor = colors.shadow,
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(colors.floating)
        .border(0.75.dp, colors.hairline, shape)
}

/**
 * Where scrolling content meets an edge: it fades into the background instead of being cut off.
 * A plain two-colour gradient, drawn once, with no blur.
 */
@Composable
fun EdgeFade(top: Boolean, height: Dp, modifier: Modifier = Modifier, color: Color? = null) {
    val fill = color ?: PaneTheme.colors.background
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val solid = fill
                val clear = fill.copy(alpha = 0f)
                drawRect(Brush.verticalGradient(if (top) listOf(solid, clear) else listOf(clear, solid)))
            },
    )
}
