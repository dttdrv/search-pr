package app.pane.browser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import app.pane.browser.ui.theme.Frost
import app.pane.browser.ui.theme.frostTint

/** How far the veil runs before it is gone, as a share of the status area's own height. */
internal const val VeilReach = 1.5f

/** How much of a plain colour the veil keeps behind the clock and battery, at the top and where the area ends. */
private const val Cover = 0.9f
private const val CoverAtEdge = 0.6f

/**
 * The page runs under the status area. Over it the page is blurred and lightly tinted (dark over a dark
 * page), whole behind the clock and battery and fading out a little below them, the way the Play Store's
 * lists pass under its status bar. Where the device has no working blur, [color] (what the page shows
 * there) is faded over it instead. [dark] and [color] are read while drawing, so they change without
 * recomposing.
 */
@Composable
fun StatusVeil(statusTop: Dp, frost: Frost, dark: () -> Boolean, color: () -> Color) {
    val blurred = frost.crown
    val edge = 1f / VeilReach
    Box(
        Modifier.fillMaxWidth().height(statusTop * VeilReach)
            .then(if (blurred != null) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
            .drawBehind {
                if (blurred == null) {
                    val c = color()
                    drawRect(Brush.verticalGradient(0f to c.copy(alpha = Cover), edge to c.copy(alpha = CoverAtEdge), 1f to c.copy(alpha = 0f)))
                    return@drawBehind
                }
                drawLayer(blurred)
                drawRect(frostTint(dark()))
                // keep all of it over the status area, then let it go
                drawRect(Brush.verticalGradient(0f to Color.Black, edge to Color.Black, 1f to Color.Transparent), topLeft = Offset.Zero, blendMode = BlendMode.DstIn)
            },
    )
}
