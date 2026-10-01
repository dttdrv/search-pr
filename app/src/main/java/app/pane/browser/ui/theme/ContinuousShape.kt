package app.pane.browser.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Corners whose curvature, and the rate it changes at, are continuous with the straight edge (G3):
 * the curvature eases up along a smoothstep, holds, and eases down the same way, so there is no
 * point where the edge ends and the corner begins. [smoothing] is how much of the curve is spent
 * easing in and out: 0 is a plain circular arc, 1 is all ease.
 */
class ContinuousRoundedShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = 0.6f,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp, smoothing: Float = 0.6f) :
        this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

    private val curve = Curve.of(smoothing)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        ContinuousRoundedShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val ltr = layoutDirection == LayoutDirection.Ltr
        val maxExtent = min(size.width, size.height) / 2
        fun extent(radius: Float) = min((1 + smoothing) * radius, maxExtent)
        val tl = extent(if (ltr) topStart else topEnd)
        val tr = extent(if (ltr) topEnd else topStart)
        val br = extent(if (ltr) bottomEnd else bottomStart)
        val bl = extent(if (ltr) bottomStart else bottomEnd)
        val w = size.width
        val h = size.height
        return Outline.Generic(
            Path().apply {
                moveTo(tl, 0f)
                lineTo(w - tr, 0f)
                corner(tr, w - tr, 0f, Quarter.TopRight)
                lineTo(w, h - br)
                corner(br, w, h - br, Quarter.BottomRight)
                lineTo(bl, h)
                corner(bl, bl, h, Quarter.BottomLeft)
                lineTo(0f, tl)
                corner(tl, 0f, tl, Quarter.TopLeft)
                close()
            },
        )
    }

    /** The unit corner turned to [quarter], scaled so it spans [extent] along each edge and starting at ([x], [y]). */
    private fun Path.corner(extent: Float, x: Float, y: Float, quarter: Quarter) {
        if (extent <= 0f) return
        val scale = extent / curve.extent
        for (i in 1..Curve.STEPS) {
            val (u, v) = quarter.turn(curve.xs[i], curve.ys[i])
            lineTo(x + scale * u, y + scale * v)
        }
    }

    /** Which way the unit corner, which starts heading right and ends heading down, is turned. */
    private enum class Quarter(val turn: (Float, Float) -> Pair<Float, Float>) {
        TopRight({ x, y -> x to y }),
        BottomRight({ x, y -> -y to x }),
        BottomLeft({ x, y -> -x to -y }),
        TopLeft({ x, y -> y to -x }),
    }

    /**
     * One corner of unit length, heading right and turning a quarter to head down. Its curvature is
     * a smoothstep ramp up over the first [q] of the length, flat, and the mirror ramp down, which
     * makes the curvature and its slope continuous at both ends. Integrated once per [smoothing].
     */
    private class Curve(smoothing: Float) {
        private val q = (smoothing / 2).coerceIn(0f, 0.5f).toDouble()
        val xs = FloatArray(STEPS + 1)
        val ys = FloatArray(STEPS + 1)
        val extent: Float get() = xs[STEPS]

        init {
            val peak = PI / 2 / (1 - q)
            var x = 0.0
            var y = 0.0
            var before = 0.0
            for (i in 1..STEPS) {
                val heading = peak * easedLength(i.toDouble() / STEPS)
                val mid = (before + heading) / 2
                x += cos(mid) / STEPS
                y += sin(mid) / STEPS
                xs[i] = x.toFloat()
                ys[i] = y.toFloat()
                before = heading
            }
        }

        /** The integral of the curvature profile (1 on the plateau, a smoothstep on each ramp) up to length [s]. */
        private fun easedLength(s: Double): Double {
            if (q == 0.0) return s
            fun ramp(t: Double): Double {
                val u = t / q
                return q * (u * u * u - u * u * u * u / 2)
            }
            return when {
                s < q -> ramp(s)
                s <= 1 - q -> q / 2 + (s - q)
                else -> (1 - q) - ramp(1 - s)
            }
        }

        companion object {
            const val STEPS = 48
            private val cache = ConcurrentHashMap<Float, Curve>()
            fun of(smoothing: Float): Curve = cache.getOrPut(smoothing) { Curve(smoothing) }
        }
    }
}

object PaneShapes {
    val small = ContinuousRoundedShape(12.dp)
    val medium = ContinuousRoundedShape(20.dp)
    val large = ContinuousRoundedShape(24.dp)
    val card = ContinuousRoundedShape(30.dp)
    val sheet = ContinuousRoundedShape(CornerSize(38.dp), CornerSize(38.dp), CornerSize(0.dp), CornerSize(0.dp))

    /** A card that floats clear of the screen edges, as glass menus and sheets do. */
    val floating = ContinuousRoundedShape(36.dp)
    val pill = ContinuousRoundedShape(100.dp, smoothing = 0f)
}
