package app.pane.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
 * What chrome floats over: [frostSource] records it once and keeps its bottom band blurred, one blur
 * shared by every [frosted] surface over it. The layers are null where blur isn't reliable
 * (RenderEffect needs Android 12, whose RenderNodes don't reliably repaint when drawn in two places),
 * and the surfaces are then solid.
 */
@Stable
class Frost internal constructor(internal val page: GraphicsLayer?, internal val band: GraphicsLayer?) {
    /** The source's top-left in the root. */
    internal var origin by mutableStateOf(Offset.Zero)

    /** A sheet is up: it covers more than the band, so the whole source is blurred while it is. */
    internal var whole by mutableStateOf(false)
}

/** The [Frost] that chrome floats over; none outside the bar. */
val LocalFrost = staticCompositionLocalOf<Frost?> { null }

@Composable
fun rememberFrost(): Frost {
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val page = if (blurs) rememberGraphicsLayer() else null
    val band = if (blurs) rememberGraphicsLayer() else null
    // nothing.tech's header blurs by 45px, a css standard deviation; an android blur radius r has
    // sigma r / √3 + 0.5.
    val radius = with(LocalDensity.current) { (45.dp.toPx() - 0.5f) * sqrt(3f) }
    return remember(page, band, radius) {
        // the page renders offscreen once and is reused, so its WebView draws once a frame: drawn twice,
        // chromium rasters tiles only for the viewport of whichever draw came last.
        page?.compositingStrategy = CompositingStrategy.Offscreen
        band?.renderEffect = BlurEffect(radius, radius)
        band?.colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(FrostSaturation) })
        Frost(page, band)
    }
}

// fitted to the owner's reference, an ios banner over grass: this saturation, then this much white,
// turn the grass (131,149,63) into the banner's (175,191,113).
private const val FrostSaturation = 1.4f
private const val FrostVeil = 0.37f

// then this much of the other pole, so a frosted surface is 10% darker than a light page and 10%
// lighter than a dark one, and still shows on a white or black page.
private const val FrostShade = 0.10f

/** The one fill a frosted surface wears over its blur: the veil, then the shade, as a single colour. */
internal fun frostTint(dark: Boolean): Color {
    val (veil, pole) = if (dark) Color.Black to Color.White else Color.White to Color.Black
    return pole.copy(alpha = FrostShade).compositeOver(veil.copy(alpha = FrostVeil))
}

/** Draws this through [frost]'s page layer and keeps the bottom [band] of it blurred. */
@Composable
fun Modifier.frostSource(frost: Frost, band: Dp): Modifier {
    val page = frost.page
    val blurred = frost.band
    if (page == null || blurred == null) return this
    val height = with(LocalDensity.current) { band.roundToPx() }
    // a page without a background of its own shows the ground through its transparent WebView
    val ground = PaneTheme.colors.background
    return onGloballyPositioned { frost.origin = it.positionInRoot() }
        .drawWithContent {
            page.record { this@drawWithContent.drawContent() }
            drawLayer(page)
            val full = size.height.roundToInt()
            val rows = if (frost.whole) full else height
            blurred.topLeft = IntOffset(0, full - rows)
            blurred.record(IntSize(size.width.roundToInt(), rows)) {
                drawRect(ground)
                translate(top = (rows - full).toFloat()) { drawLayer(page) }
            }
        }
}

/**
 * Chrome over its [Frost], frosted: the source blurred and its colour deepened behind the [frostTint]
 * of the bar's tone (light over a light bar, dark over a dark one), with no shadow and no outline. A
 * sheet passes [whole] to blur all of the source while it is up. Without a working blur (no [Frost],
 * or an Android that can't be trusted with one) it is the solid [floating] surface; and should the
 * blur ever draw nothing, the solid [fill] shows in its place, never the sharp source through a veil.
 * The surface's place in the root is measured when it is laid out; a surface that a transform above
 * it carries about (a sheet's slide) gives its [position] instead.
 */
@Composable
fun Modifier.frosted(shape: Shape, whole: Boolean = false, fill: Color? = null, position: (() -> Offset)? = null): Modifier {
    val frost = LocalFrost.current?.takeIf { it.band != null } ?: return floating(shape, fill = fill)
    if (whole) DisposableEffect(frost) {
        frost.whole = true
        onDispose { frost.whole = false }
    }
    val colors = PaneTheme.colors
    val blurred = frost.band!!
    var at by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { at = it.positionInRoot() }
        .clip(shape)
        .background(fill ?: colors.floating)
        .drawBehind {
            val here = position?.invoke() ?: at
            translate(frost.origin.x - here.x, frost.origin.y - here.y) { drawLayer(blurred) }
        }
        .background(frostTint(colors.isDark))
}

/** The [Frost] a sheet frosts over, and that it is covering all of it while it is up; null where there is no working blur. */
@Composable
internal fun rememberSheetFrost(): Frost? {
    val frost = LocalFrost.current?.takeIf { it.band != null } ?: return null
    DisposableEffect(frost) {
        frost.whole = true
        onDispose { frost.whole = false }
    }
    return frost
}

/** The blur of [frost], where a surface whose top-left is [at] in the root sits, in whatever clip the caller has set. */
internal fun DrawScope.drawFrostBlur(frost: Frost, at: Offset) {
    translate(frost.origin.x - at.x, frost.origin.y - at.y) { drawLayer(frost.band!!) }
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
