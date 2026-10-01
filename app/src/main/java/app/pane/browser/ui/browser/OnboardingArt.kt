package app.pane.browser.ui.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import app.pane.browser.ui.theme.PaneTheme
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** The three little films of the onboarding carousel. Each loops, quietly, on its own. */
enum class Scene { Bar, Sideways, Up }

private const val W = 300f
private const val H = 330f

/**
 * One scene, drawn in code: a phone in line art, the floating bar, and a fingertip showing what to do.
 * Everything is vector and drawn in a 300×330 design space that scales to the width it is given, in
 * the colours of the theme (ink on paper, one red dot). Nothing is an image, so it is sharp at any
 * size and costs a handful of shapes per frame.
 */
@Composable
fun OnboardingArt(scene: Scene, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val loop = rememberInfiniteTransition(label = "art")
    val clock = loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (scene == Scene.Bar) 4200 else 4600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val ink = colors.label
    val paper = colors.background
    val signal = colors.signal
    Canvas(modifier.fillMaxWidth().aspectRatio(W / H)) {
        // Read here, in the draw phase: the loop redraws the drawing and recomposes nothing.
        val t = clock.value
        val k = size.width / W
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            dotField(ink)
            when (scene) {
                Scene.Bar -> barScene(t, ink, paper, signal)
                Scene.Sideways -> sidewaysScene(t, ink, paper, signal)
                Scene.Up -> upScene(t, ink, paper, signal)
            }
        }
    }
}

// region Timeline helpers

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 0→1 as [t] runs from [a] to [b], eased at both ends. */
private fun seg(t: Float, a: Float, b: Float): Float = smooth((t - a) / (b - a))

/** Fades up over [a]..[b], and back down over [c]..[d]. */
private fun window(t: Float, a: Float, b: Float, c: Float, d: Float): Float = seg(t, a, b) * (1f - seg(t, c, d))

// endregion

// region Shared pieces

/** A quiet field of dots behind the phone, brightest at the middle and gone at the edges. */
private fun DrawScope.dotField(ink: Color) {
    val step = 20f
    val cx = W / 2f
    val cy = H / 2f
    var y = 10f
    while (y < H) {
        var x = 10f
        while (x < W) {
            val d = hypot(x - cx, y - cy) / (W * 0.62f)
            val a = (1f - d).coerceIn(0f, 1f)
            if (a > 0.02f) drawCircle(ink.copy(alpha = 0.24f * a * a), 1.3f, Offset(x, y))
            x += step
        }
        y += step
    }
}

private class Phone(val l: Float, val t: Float, val w: Float, val b: Float) {
    val r get() = l + w
    val cx get() = l + w / 2f
    val radius get() = w * 0.17f
}

private fun DrawScope.phone(p: Phone, ink: Color, paper: Color) {
    val body = Rect(p.l, p.t, p.r, p.b)
    drawRoundRect(paper, body.topLeft, body.size, CornerRadius(p.radius))
    drawRoundRect(ink.copy(alpha = 0.92f), body.topLeft, body.size, CornerRadius(p.radius), Stroke(1.6f))
}

/** The address pill: ink, a red status dot, a host line. */
private fun DrawScope.pill(rect: Rect, ink: Color, paper: Color, signal: Color, alpha: Float = 1f) {
    drawRoundRect(ink.copy(alpha = alpha), rect.topLeft, rect.size, CornerRadius(rect.height / 2f))
    drawCircle(signal.copy(alpha = alpha), rect.height * 0.12f, Offset(rect.left + rect.height * 0.5f, rect.center.y))
    drawLine(
        paper.copy(alpha = 0.92f * alpha),
        Offset(rect.left + rect.height * 0.95f, rect.center.y),
        Offset(rect.right - rect.height * 0.55f, rect.center.y),
        rect.height * 0.11f,
        StrokeCap.Round,
    )
}

/** The pill that stands for "a new tab": outlined, with a plus. */
private fun DrawScope.newTabPill(rect: Rect, ink: Color, alpha: Float = 1f) {
    drawRoundRect(ink.copy(alpha = 0.9f * alpha), rect.topLeft, rect.size, CornerRadius(rect.height / 2f), Stroke(1.6f))
    val c = rect.center
    val a = rect.height * 0.17f
    drawLine(ink.copy(alpha = alpha), Offset(c.x - a, c.y), Offset(c.x + a, c.y), 1.7f, StrokeCap.Round)
    drawLine(ink.copy(alpha = alpha), Offset(c.x, c.y - a), Offset(c.x, c.y + a), 1.7f, StrokeCap.Round)
}

/** The round "tabs" and "menu" buttons beside the pill. */
private fun DrawScope.roundButtons(l: Float, y: Float, d: Float, gap: Float, ink: Color, alpha: Float = 1f) {
    val line = ink.copy(alpha = 0.92f * alpha)
    drawCircle(line, d / 2f - 0.8f, Offset(l + d / 2f, y + d / 2f), style = Stroke(1.6f))
    val s = d * 0.27f
    drawRoundRect(line, Offset(l + d / 2f - s / 2f, y + d / 2f - s / 2f), Size(s, s), CornerRadius(s * 0.3f), Stroke(1.4f))
    val x2 = l + d + gap
    drawCircle(line, d / 2f - 0.8f, Offset(x2 + d / 2f, y + d / 2f), style = Stroke(1.6f))
    for (i in -1..1) drawCircle(ink.copy(alpha = alpha), d * 0.05f, Offset(x2 + d / 2f + i * d * 0.17f, y + d / 2f))
}

/** A page: a heading, a few lines of text and a picture, as grey bars. */
private fun DrawScope.page(p: Phone, top: Float, ink: Color, alpha: Float, shift: Float = 0f) {
    if (alpha <= 0.01f) return
    val m = p.w * 0.1f
    val l = p.l + m + shift
    val full = p.w - 2 * m
    fun bar(y: Float, w: Float, h: Float, a: Float) =
        drawRoundRect(ink.copy(alpha = a * alpha), Offset(l, y), Size(w, h), CornerRadius(h / 2f))
    bar(top, full * 0.55f, p.w * 0.055f, 0.82f)
    bar(top + p.w * 0.1f, full * 0.34f, p.w * 0.032f, 0.22f)
    drawRoundRect(ink.copy(alpha = 0.07f * alpha), Offset(l, top + p.w * 0.2f), Size(full, p.w * 0.36f), CornerRadius(p.w * 0.05f))
    var y = top + p.w * 0.66f
    listOf(1f, 0.9f, 0.74f).forEach { f ->
        bar(y, full * f, p.w * 0.03f, 0.15f)
        y += p.w * 0.07f
    }
    y += p.w * 0.06f
    bar(y, full * 0.42f, p.w * 0.045f, 0.62f)
    y += p.w * 0.085f
    listOf(1f, 0.82f).forEach { f ->
        bar(y, full * f, p.w * 0.03f, 0.15f)
        y += p.w * 0.07f
    }
}

/** A fingertip: a soft disc inside a thin ring; it shrinks when it presses. */
private fun DrawScope.finger(c: Offset, press: Float, alpha: Float, ink: Color) {
    if (alpha <= 0.01f) return
    drawCircle(ink.copy(alpha = 0.10f * alpha), 15f - 2f * press, c)
    drawCircle(ink.copy(alpha = 0.55f * alpha), 15f - 2f * press, c, style = Stroke(1.5f))
    drawCircle(ink.copy(alpha = 0.78f * alpha), 3.6f + 1.2f * press, c)
}

/** A short trail of dots behind the fingertip, fading towards where it started. */
private fun DrawScope.trail(from: Offset, to: Offset, ink: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    val length = hypot(to.x - from.x, to.y - from.y)
    if (length < 12f) return
    val n = (length / 9f).toInt().coerceAtMost(14)
    for (i in 1..n) {
        val f = i / (n + 1f)
        val c = Offset(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f)
        drawCircle(ink.copy(alpha = 0.34f * f * alpha), 1.7f, c)
    }
}

// endregion

// region Scenes

/** The bar, floating over a page: it rises and settles, and its shadow breathes with it. */
private fun DrawScope.barScene(t: Float, ink: Color, paper: Color, signal: Color) {
    val p = Phone(30f, -60f, 240f, 322f)
    phone(p, ink, paper)
    page(p, 24f, ink, 1f)
    val bob = sin(t * 2f * PI.toFloat())
    val lift = 3.4f * bob
    val barH = 38f
    val margin = 14f
    val pillW = 116f
    val gap = 8f
    val y = p.b - 18f - barH - lift
    val l = p.l + margin
    // The shadow: wider and fainter the higher the bar floats.
    val spread = 1f + 0.05f * bob
    drawOval(
        ink.copy(alpha = 0.11f - 0.03f * bob),
        Offset(p.cx - 88f * spread, p.b - 16f),
        Size(176f * spread, 9f),
    )
    pill(Rect(l, y, l + pillW, y + barH), ink, paper, signal)
    roundButtons(l + pillW + gap, y, barH, gap, ink)
}

/** Swipe the bar sideways: the pill slides off and the next page (a new tab) slides in. */
private fun DrawScope.sidewaysScene(t: Float, ink: Color, paper: Color, signal: Color) {
    val p = Phone(30f, -60f, 240f, 322f)
    phone(p, ink, paper)

    // One gesture per loop, then a soft reset.
    val drag = seg(t, 0.14f, 0.50f)
    val fingerIn = seg(t, 0.04f, 0.14f)
    val fingerOut = 1f - seg(t, 0.50f, 0.60f)
    val resetOut = seg(t, 0.86f, 0.93f)
    val resetIn = seg(t, 0.93f, 1.0f)
    val visibility = if (t < 0.86f) 1f else if (t < 0.93f) 1f - resetOut else resetIn
    val p0 = if (t < 0.93f) drag else 0f

    // The page behind: the current one gives way to an empty one.
    val barH = 38f
    val barY = p.b - 18f - barH
    clipRect(p.l + 1f, p.t + 1f, p.r - 1f, p.b - 1f) {
        page(p, 24f, ink, (1f - p0) * visibility, shift = -p0 * 40f)
        // The new, empty page: just a field waiting for a word.
        val a = p0 * visibility
        if (a > 0.01f) {
            val fieldW = p.w * 0.62f
            val field = Rect(p.cx - fieldW / 2f + (1f - p0) * 40f, 118f, p.cx + fieldW / 2f + (1f - p0) * 40f, 146f)
            drawRoundRect(ink.copy(alpha = 0.85f * a), field.topLeft, field.size, CornerRadius(14f), Stroke(1.5f))
            drawLine(ink.copy(alpha = 0.22f * a), Offset(field.left + 14f, field.center.y), Offset(field.left + 14f + 52f, field.center.y), 3f, StrokeCap.Round)
        }
    }

    // The bar row: the pill slot slides, the round buttons stay.
    val margin = 14f
    val pillW = 112f
    val circle = barH
    val gap = 8f
    val slotL = p.l + margin
    val stride = pillW + 14f
    val slide = -stride * p0
    clipRect(slotL, barY - 12f, slotL + pillW, barY + barH + 12f) {
        pill(Rect(slotL + slide, barY, slotL + slide + pillW, barY + barH), ink, paper, signal, visibility)
        newTabPill(Rect(slotL + slide + stride, barY, slotL + slide + stride + pillW, barY + barH), ink, visibility)
    }
    roundButtons(slotL + pillW + gap, barY, circle, gap, ink, visibility)

    // The fingertip travels with the pill.
    val start = Offset(slotL + pillW * 0.78f, barY + barH / 2f)
    val c = Offset(start.x - stride * 0.46f * drag, start.y)
    val show = fingerIn * fingerOut * visibility
    trail(start, c, ink, show)
    finger(c, press = 1f - seg(t, 0.50f, 0.56f), alpha = show, ink = ink)
}

/** Swipe the bar up: the page steps back and your tabs unfold. */
private fun DrawScope.upScene(t: Float, ink: Color, paper: Color, signal: Color) {
    val p = Phone(30f, -60f, 240f, 322f)
    phone(p, ink, paper)

    val up = seg(t, 0.14f, 0.52f)
    val fingerIn = seg(t, 0.04f, 0.14f)
    val fingerOut = 1f - seg(t, 0.52f, 0.62f)
    val visibility = if (t < 0.86f) 1f else if (t < 0.93f) 1f - seg(t, 0.86f, 0.93f) else seg(t, 0.93f, 1f)
    val u = if (t < 0.93f) up else 0f

    val barH = 38f
    val barY = p.b - 18f - barH

    clipRect(p.l + 1f, p.t + 1f, p.r - 1f, p.b - 1f) {
        // The page steps back as the tabs open.
        withTransform({ scale(1f - 0.12f * u, 1f - 0.12f * u, pivot = Offset(p.cx, 190f)) }) {
            page(p, 24f, ink, (1f - 0.62f * u) * visibility)
        }
        // Four tabs, unfolding in turn.
        val cw = 88f
        val ch = 98f
        val gx = 12f
        val gy = 12f
        val left = p.cx - cw - gx / 2f
        val top = 40f
        for (i in 0 until 4) {
            val s = seg(u, 0.12f + 0.12f * i, 0.62f + 0.1f * i)
            if (s <= 0.01f) continue
            val col = i % 2
            val row = i / 2
            val x = left + col * (cw + gx)
            val y = top + row * (ch + gy)
            val sc = 0.72f + 0.28f * s
            val a = s * visibility
            withTransform({ scale(sc, sc, pivot = Offset(p.cx, barY)) }) {
                drawRoundRect(paper.copy(alpha = a), Offset(x, y), Size(cw, ch), CornerRadius(12f))
                drawRoundRect(ink.copy(alpha = (if (i == 1) 0.95f else 0.55f) * a), Offset(x, y), Size(cw, ch), CornerRadius(12f), Stroke(if (i == 1) 2f else 1.4f))
                // A tab's face: the same page, very small.
                val m = 10f
                drawRoundRect(ink.copy(alpha = 0.75f * a), Offset(x + m, y + m + 2f), Size(cw * 0.5f, 6f), CornerRadius(3f))
                drawRoundRect(ink.copy(alpha = 0.07f * a), Offset(x + m, y + 26f), Size(cw - 2 * m, 28f), CornerRadius(6f))
                var ly = y + 62f
                listOf(1f, 0.84f, 0.62f).forEach { f ->
                    drawRoundRect(ink.copy(alpha = 0.16f * a), Offset(x + m, ly), Size((cw - 2 * m) * f, 4f), CornerRadius(2f))
                    ly += 10f
                }
            }
        }
    }

    // The bar rides up a little with the finger, then rests.
    val margin = 14f
    val pillW = 116f
    val gap = 8f
    val circle = barH
    val slotL = p.l + margin
    val rise = -10f * u
    val fade = (1f - 0.55f * u) * visibility
    pill(Rect(slotL, barY + rise, slotL + pillW, barY + barH + rise), ink, paper, signal, fade)
    roundButtons(slotL + pillW + gap, barY + rise, circle, gap, ink, fade)

    val start = Offset(slotL + pillW * 0.5f, barY + barH / 2f)
    val c = Offset(start.x, start.y - 70f * u)
    val show = fingerIn * fingerOut * visibility
    trail(start, c, ink, show)
    finger(c, press = 1f - seg(t, 0.52f, 0.58f), alpha = show, ink = ink)
}

// endregion
