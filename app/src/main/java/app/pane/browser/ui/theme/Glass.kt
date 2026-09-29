package app.pane.browser.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.core.settings.GlassQuality
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.random.Random

/**
 * The page (or whatever sits behind the chrome) that glass surfaces refract and blur. The browser
 * screen owns one [HazeState], marks the page as its source and provides it here; without one,
 * glass falls back to a near-opaque tint so nothing is ever unreadable.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** How much glass to draw; the user's choice from Settings, stepped down automatically in battery saver. */
val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.Full }

/** Debug builds only: `adb shell am start ... --es glass off|light|full` to compare glass costs. */
object GlassDebug {
    var mode by mutableStateOf("")
}

/** How much of what's behind a glass surface survives. */
enum class GlassStrength(val tint: Float, val shadow: Dp, val frost: Float, val blur: Dp) {
    /** Small floating controls over a live page. */
    Thin(tint = 0.22f, shadow = 6.dp, frost = 0.8f, blur = 20.dp),

    /** Bars and pills: the default. */
    Regular(tint = 0.30f, shadow = 10.dp, frost = 1f, blur = 26.dp),

    /** Sheets and menus, which carry text and need more separation from the page. */
    Thick(tint = 0.52f, shadow = 24.dp, frost = 1.4f, blur = 34.dp),
}

/**
 * Liquid glass, in four layers:
 *
 *  1. **The lens.** Haze's refractive glass: the page behind is bent along the rim like a thick
 *     slab of glass, lit by a specular highlight from the top-left and shadowed along the lower
 *     edge. Pressing it lifts the light and deepens the refraction. (Full quality only.)
 *  2. **The diffusion.** The lens is blurred with depth, so what's under the glass is soft, not
 *     merely tinted: this is what makes it frosted.
 *  3. **The frost.** A milky top-lit veil and fine grain over the lens.
 *  4. **The lift.** A soft shadow so it floats above the page, and a hairline to define its edge
 *     over pages the same colour as the glass.
 *
 * At [GlassQuality.Light] only the blur, veil and grain are drawn (no lens: far cheaper); at
 * [GlassQuality.Off] it is a plain translucent surface. Apply before padding so the lens spans the
 * full bounds; content drawn inside sits on top.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glass(
    shape: Shape,
    strength: GlassStrength = GlassStrength.Regular,
    lifted: Boolean = true,
): Modifier {
    val colors = PaneTheme.colors
    val context = LocalContext.current
    val requested = LocalGlassQuality.current
    val debug = if (app.pane.browser.BuildConfig.DEBUG) GlassDebug.mode else ""
    val quality = when {
        debug == "off" -> GlassQuality.Off
        debug == "light" -> GlassQuality.Light
        debug == "full" -> GlassQuality.Full
        // Battery saver: keep the look, drop the lens.
        requested == GlassQuality.Full && context.isPowerSaving() -> GlassQuality.Light
        else -> requested
    }
    val haze = LocalHazeState.current.takeIf { quality != GlassQuality.Off }
    val rounded = remember(shape) { shape.asRounded() }
    val frost = remember(colors, strength) { frostBrushes(colors, strength) }
    return this
        .then(
            if (lifted) {
                Modifier.shadow(
                    elevation = strength.shadow,
                    shape = shape,
                    ambientColor = colors.shadow.copy(alpha = colors.shadow.alpha * 0.45f),
                    spotColor = colors.shadow.copy(alpha = colors.shadow.alpha * 0.9f),
                )
            } else {
                Modifier
            },
        )
        .then(
            when {
                haze == null -> Modifier.clip(shape).background(colors.background.copy(alpha = 0.94f))
                quality == GlassQuality.Light -> {
                    val style = remember(colors, strength) { blurStyle(colors, strength) }
                    Modifier.clip(shape).hazeBlur(input = HazeInput.Sources(haze), style = style)
                }
                else -> {
                    val style = remember(colors, strength, rounded) { glassStyle(colors, strength, rounded) }
                    Modifier.hazeGlass(input = HazeInput.Sources(haze), style = style)
                }
            },
        )
        .clip(shape)
        .drawBehind {
            drawRect(frost.veil)
            drawRect(brush = frost.grain, alpha = frost.grainAlpha)
        }
        .border(0.6.dp, colors.chromeBorder, shape)
}

@OptIn(ExperimentalHazeApi::class)
private fun glassStyle(colors: PaneColors, strength: GlassStrength, shape: RoundedCornerShape): GlassStyle {
    val tone = if (colors.isDark) colors.chrome else Color.White
    return GlassStyle.regular.then {
        shape(shape)
        // What shows through where the page is missing or transparent.
        backgroundColor(colors.background)
        tint(tone.copy(alpha = strength.tint))
        specularIntensity(if (colors.isDark) 0.5f else 0.6f)
        ambientResponse(0.2f)
        edgeShadow(Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.10f))
        lightPosition(Alignment.TopStart)
        // A frosted core (real diffusion) inside a lens that only bends the last stretch of the rim.
        // The secondary edge-detail pass is left off: it is the costly part and the one that shimmers.
        optics(
            refractionStrength = if (strength == GlassStrength.Thick) 0.5f else 0.72f,
            refractionHeightFraction = 0.16f,
            refractionDisplacement = if (strength == GlassStrength.Thick) 26.dp else 30.dp,
            depth = 1f,
            blurRadius = strength.blur,
            refractionDetailIntensity = 0f,
            refractionProfile = RefractionProfile.Edge(if (strength == GlassStrength.Thick) 22.dp else 16.dp),
        )
        // Pressing wakes the lens: the light follows the finger and the refraction deepens.
        pressed {
            lightingIntensity(1f)
            refractionMultiplier(1.4f)
            scale(0.955f)
        }
    }
}

private fun blurStyle(colors: PaneColors, strength: GlassStrength): HazeBlurStyle {
    val tone = if (colors.isDark) colors.chrome else Color.White
    return HazeBlurStyle {
        blurRadius(strength.blur)
        noiseFactor(0f)
        backgroundColor(colors.background)
        colorEffects(listOf(HazeColorEffect.tint(tone.copy(alpha = strength.tint + 0.10f))))
        fallbackColorEffect(HazeColorEffect.tint(colors.background.copy(alpha = 0.94f)))
    }
}

private class Frost(val veil: Brush, val grain: Brush, val grainAlpha: Float)

private fun frostBrushes(colors: PaneColors, strength: GlassStrength): Frost {
    val milk = if (colors.isDark) 0.06f else 0.20f
    val veil = Brush.verticalGradient(
        0f to Color.White.copy(alpha = milk * strength.frost),
        0.45f to Color.White.copy(alpha = milk * 0.3f * strength.frost),
        1f to Color.White.copy(alpha = milk * 0.08f * strength.frost),
    )
    return Frost(veil, GrainBrush, grainAlpha = (if (colors.isDark) 0.06f else 0.085f) * strength.frost)
}

/** A tile of fine monochrome grain, repeated across the surface. Built once. */
private val GrainBrush: Brush by lazy {
    val size = 96
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val v = 96 + random.nextInt(160)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun Context.isPowerSaving(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

/** Haze draws its material on a rounded rectangle; carry any of Pane's corner-based shapes across. */
private fun Shape.asRounded(): RoundedCornerShape = when (this) {
    is RoundedCornerShape -> this
    is CornerBasedShape -> RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    else -> RoundedCornerShape(24.dp)
}

/**
 * The soft edge where scrolling content meets a bar: instead of a hard cut, whatever slides under
 * it is blurred more and more towards the edge and fades into the background. Put
 * `Modifier.hazeSource(state)` on the scrolling content and this above it, at the top or bottom.
 */
@Composable
fun ProgressiveEdge(state: HazeState, top: Boolean, height: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    val colors = PaneTheme.colors
    val veil = tint ?: colors.background
    val style = remember(colors, top, veil) {
        HazeBlurStyle {
            blurRadius(22.dp)
            noiseFactor(0f)
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(veil.copy(alpha = 0.5f))))
            progressive(
                HazeProgressive.verticalGradient(
                    startIntensity = if (top) 1f else 0f,
                    endIntensity = if (top) 0f else 1f,
                ),
            )
            mask(
                Brush.verticalGradient(
                    if (top) listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    else listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black),
                ),
            )
        }
    }
    Box(modifier.fillMaxWidth().height(height).hazeBlur(input = HazeInput.Sources(state), style = style))
}
