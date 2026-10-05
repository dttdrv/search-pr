package app.pane.browser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import app.pane.browser.ui.theme.Frost
import app.pane.browser.ui.theme.frostTint

/** How far the veil runs before it is gone, as a share of the status area's own height. */
internal const val VeilReach = 1.5f

/** How much the veil covers at the very top; it thins from there to nothing, with no edge to see. */
private const val Cover = 0.75f

/**
 * What is drawn over the page in the status area.
 *
 * Where the page lets its content pass under the status area, a veil: the page itself, blurred and lightly
 * tinted (dark over a dark page), or the colour it shows there where the device has no working blur,
 * strongest at the very top and thinning to nothing a little below the icons, the way the Play Store's lists
 * pass under its status bar.
 *
 * Where the page holds something across its top ([held]), nothing passes under, so the status area carries on
 * what is at its edge instead: the row of the page just below it, drawn up over it (every column in its own
 * colour), or that row's colour where the page isn't kept as a layer.
 *
 * [dark] and [color] are read while drawing, so they change without recomposing.
 */
@Composable
fun StatusVeil(statusTop: Dp, frost: Frost, held: Boolean, dark: () -> Boolean, color: () -> Color) {
    val page = frost.page
    val blurred = frost.crown
    Box(
        Modifier.fillMaxWidth().height(statusTop * VeilReach)
            .then(if (blurred != null && !held) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
            .drawBehind {
                val area = Size(size.width, size.height / VeilReach)
                when {
                    held && page != null -> clipRect(bottom = area.height) {
                        // the one row under the status area, stretched up to fill it
                        scale(1f, area.height, pivot = Offset.Zero) { translate(top = -area.height) { drawLayer(page) } }
                    }
                    held -> drawRect(color(), size = area)
                    blurred != null -> {
                        drawLayer(blurred)
                        drawRect(frostTint(dark()))
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = Cover), Color.Transparent)), blendMode = BlendMode.DstIn)
                    }
                    else -> color().let { drawRect(Brush.verticalGradient(listOf(it.copy(alpha = Cover), it.copy(alpha = 0f)))) }
                }
            },
    )
}
