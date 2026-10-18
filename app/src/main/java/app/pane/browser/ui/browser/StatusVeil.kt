package app.pane.browser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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

/** How much the veil covers at the very top; it thins from there to nothing, with no edge to see. */
private const val Cover = 0.75f

/**
 * What is drawn over the page in the status area: the page itself, blurred and tinted in the app's tone
 * ([dark]), or a fade in the app's [ground] where the device has no working blur, strongest at the very top
 * and thinning to nothing a little below the icons, the way the Play Store's lists pass under its status bar.
 * It never takes a colour from the site.
 */
@Composable
fun StatusVeil(statusTop: Dp, frost: Frost, dark: Boolean, ground: Color) {
    val blurred = frost.crown
    Box(
        Modifier.fillMaxWidth().height(statusTop * VeilReach)
            .then(if (blurred != null) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
            .drawBehind {
                if (blurred != null) {
                    drawLayer(blurred)
                    drawRect(frostTint(dark))
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = Cover), Color.Transparent)), blendMode = BlendMode.DstIn)
                } else {
                    drawRect(Brush.verticalGradient(listOf(ground.copy(alpha = Cover), ground.copy(alpha = 0f))))
                }
            },
    )
}
