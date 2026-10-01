package app.pane.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The one shadow Pane draws: Material's standard elevation for a menu over content. */
val FloatingElevation: Dp = MenuDefaults.ShadowElevation

/**
 * A flat solid surface: one fill, no outline, clipped to [shape]. On Pane's own ground it is told
 * apart by tone alone (pass 0.dp); chrome over the web page (the pill, its round buttons, menus,
 * sheets) keeps one platform elevation shadow, so a white pill still shows on a white page. Apply it
 * before padding so the fill spans the full bounds.
 */
@Composable
fun Modifier.floating(shape: Shape, shadow: Dp = FloatingElevation, fill: Color? = null): Modifier =
    this.shadow(shadow, shape, clip = true).background(fill ?: PaneTheme.colors.floating)

/**
 * The page under the floating bar: [frostSource] records it once and keeps its bottom band blurred,
 * one blur shared by every [frosted] surface over it. The layers are null where blur isn't reliable
 * (RenderEffect needs Android 12, whose RenderNodes don't repaint when drawn in two places), and the
 * surfaces are then a plain tint.
 */
@Stable
class Frost internal constructor(internal val page: GraphicsLayer?, internal val band: GraphicsLayer?) {
    /** The band's top-left in the root. */
    internal var origin by mutableStateOf(Offset.Zero)
}

/** The [Frost] that chrome floats over; none outside the bar. */
val LocalFrost = staticCompositionLocalOf<Frost?> { null }

@Composable
fun rememberFrost(): Frost {
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val page = if (blurs) rememberGraphicsLayer() else null
    val band = if (blurs) rememberGraphicsLayer() else null
    // nothing.tech's header blurs by 45px, a css standard deviation; an android radius r has sigma r / √3 + 0.5.
    val radius = with(LocalDensity.current) { (45.dp.toPx() - 0.5f) * sqrt(3f) }
    return remember(page, band, radius) {
        // the page renders offscreen once and is reused, so its WebView draws once a frame: drawn twice,
        // chromium rasters tiles only for the viewport of whichever draw came last.
        page?.compositingStrategy = CompositingStrategy.Offscreen
        // DEBUG band?.renderEffect = BlurEffect(radius, radius)
        band?.colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(FrostSaturation) })
        Frost(page, band)
    }
}

// fitted to the owner's reference, an ios banner over grass: this saturation, then this much white,
// turn the grass (131,149,63) into the banner's (175,191,113).
private const val FrostSaturation = 1.4f
private const val FrostVeil = 0.37f

/** Draws this through [frost]'s page layer and keeps the bottom [band] of it blurred. */
@Composable
fun Modifier.frostSource(frost: Frost, band: Dp): Modifier {
    val page = frost.page
    val blurred = frost.band
    if (page == null || blurred == null) return this
    val height = with(LocalDensity.current) { band.roundToPx() }
    return onGloballyPositioned { frost.origin = it.positionInRoot() + Offset(0f, (it.size.height - height).toFloat()) }
        .drawWithContent {
            page.record { this@drawWithContent.drawContent() }
            drawLayer(page)
            val top = height - size.height
            blurred.record(IntSize(size.width.roundToInt(), height)) { drawRect(Color.Red); translate(top = top) { drawLayer(page) } } // DEBUG red
        }
}

/**
 * Chrome over the page, frosted: the page blurred and its colour deepened behind a veil of white
 * (black when the bar is dark, which follows the page behind it), with no shadow and no outline.
 * Outside the bar it is the solid [floating] surface.
 */
@Composable
fun Modifier.frosted(shape: Shape): Modifier {
    val frost = LocalFrost.current ?: return floating(shape)
    val veil = (if (PaneTheme.colors.isDark) Color.Black else Color.White).copy(alpha = FrostVeil)
    var at by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { at = it.positionInRoot() }
        .clip(shape)
        .drawBehind { frost.band?.let { translate(frost.origin.x - at.x, frost.origin.y - at.y) { drawLayer(it) } } }
        .background(veil)
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
