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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Apple-style continuous ("squircle") corners: the curvature eases into the straight edge instead
 * of jumping, which is most of why iOS cards look softer than plain rounded rectangles.
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
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd
        return Outline.Generic(path(size, tl, tr, br, bl))
    }

    private data class Corner(val p: Float, val a: Float, val b: Float, val c: Float, val d: Float, val r: Float, val arc: Float)

    private fun corner(radius: Float, maxP: Float): Corner {
        if (radius <= 0f) return Corner(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        var smooth = smoothing
        val r = min(radius, maxP)
        var p = (1 + smooth) * r
        if (p > maxP) {
            smooth = (maxP / r - 1).coerceAtLeast(0f)
            p = maxP
        }
        val arcMeasure = 90f * (1 - smooth)
        val arcSection = sin(rad(arcMeasure / 2)) * r * sqrt(2f)
        val angleAlpha = (90f - arcMeasure) / 2
        val p3ToP4 = r * tan(rad(angleAlpha / 2))
        val angleBeta = 45f * smooth
        val c = p3ToP4 * cos(rad(angleBeta))
        val d = c * tan(rad(angleBeta))
        val b = (p - arcSection - c - d) / 3
        val a = 2 * b
        return Corner(p, a, b, c, d, r, arcMeasure)
    }

    private fun path(size: Size, tl: Float, tr: Float, br: Float, bl: Float): Path {
        val w = size.width
        val h = size.height
        val maxP = min(w, h) / 2
        val cTL = corner(tl, maxP)
        val cTR = corner(tr, maxP)
        val cBR = corner(br, maxP)
        val cBL = corner(bl, maxP)
        return Path().apply {
            moveTo(cTL.p, 0f)
            // Top edge → top-right corner.
            lineTo(w - cTR.p, 0f)
            if (cTR.r > 0) {
                cubicTo(w - cTR.p + cTR.a, 0f, w - cTR.p + cTR.a + cTR.b, 0f, w - cTR.p + cTR.a + cTR.b + cTR.c, cTR.d)
                arcTo(Rect(Offset(w - 2 * cTR.r, 0f), Size(2 * cTR.r, 2 * cTR.r)), -45f - cTR.arc / 2, cTR.arc, false)
                cubicTo(w, cTR.p - cTR.a - cTR.b, w, cTR.p - cTR.a, w, cTR.p)
            }
            lineTo(w, h - cBR.p)
            if (cBR.r > 0) {
                cubicTo(w, h - cBR.p + cBR.a, w, h - cBR.p + cBR.a + cBR.b, w - cBR.d, h - cBR.p + cBR.a + cBR.b + cBR.c)
                arcTo(Rect(Offset(w - 2 * cBR.r, h - 2 * cBR.r), Size(2 * cBR.r, 2 * cBR.r)), 45f - cBR.arc / 2, cBR.arc, false)
                cubicTo(w - cBR.p + cBR.a + cBR.b, h, w - cBR.p + cBR.a, h, w - cBR.p, h)
            }
            lineTo(cBL.p, h)
            if (cBL.r > 0) {
                cubicTo(cBL.p - cBL.a, h, cBL.p - cBL.a - cBL.b, h, cBL.p - cBL.a - cBL.b - cBL.c, h - cBL.d)
                arcTo(Rect(Offset(0f, h - 2 * cBL.r), Size(2 * cBL.r, 2 * cBL.r)), 135f - cBL.arc / 2, cBL.arc, false)
                cubicTo(0f, h - cBL.p + cBL.a + cBL.b, 0f, h - cBL.p + cBL.a, 0f, h - cBL.p)
            }
            lineTo(0f, cTL.p)
            if (cTL.r > 0) {
                cubicTo(0f, cTL.p - cTL.a, 0f, cTL.p - cTL.a - cTL.b, cTL.d, cTL.p - cTL.a - cTL.b - cTL.c)
                arcTo(Rect(Offset.Zero, Size(2 * cTL.r, 2 * cTL.r)), 225f - cTL.arc / 2, cTL.arc, false)
                cubicTo(cTL.p - cTL.a - cTL.b, 0f, cTL.p - cTL.a, 0f, cTL.p, 0f)
            }
            close()
        }
    }

    private fun rad(deg: Float) = (deg * Math.PI / 180.0).toFloat()
}

object PaneShapes {
    val small = ContinuousRoundedShape(8.dp)
    val medium = ContinuousRoundedShape(12.dp)
    val large = ContinuousRoundedShape(16.dp)
    val card = ContinuousRoundedShape(22.dp)
    val sheet = ContinuousRoundedShape(CornerSize(28.dp), CornerSize(28.dp), CornerSize(0.dp), CornerSize(0.dp))
    val pill = ContinuousRoundedShape(100.dp, smoothing = 0f)
}
