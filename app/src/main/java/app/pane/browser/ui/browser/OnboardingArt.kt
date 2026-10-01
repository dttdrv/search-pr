package app.pane.browser.ui.browser

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.util.lerp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.PrivateColors
import app.pane.browser.ui.theme.Spacing
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * the three little films of the onboarding carousel. each plays from its first frame when its page arrives
 * and loops every [ms]
 */
enum class Scene(val ms: Int) {
    Bar(7200),
    Sideways(9600),
    Up(9600),
}

/** Where a loop's after-state has faded out and it starts over. */
private const val Restart = 0.93f

// steps from the screen towards the ink: pictures; text, quiet buttons and the phone's body; headlines
private const val Quiet = 0.06f
private const val Soft = 0.14f
private const val Strong = 0.4f

/** A headline's length, then a paragraph's line lengths, as shares of the text column. */
private const val Headline = 0.66f
private val Rag = floatArrayOf(1f, 0.94f, 0.98f, 0.6f)

/** Letter keys per keyboard row; the space bar spans half the top row. */
private val KeyRows = intArrayOf(10, 9, 7)

/** Tabs in the overview, favourites and recent pages in the editor: four of each. */
private const val Count = 4
private const val OpenTab = 1

// a spring move counts as landed after this many natural periods, where the step response is within e^-Damped of its end
private const val Damped = 7f

/** how long a fingertip takes to appear or leave, and a spring to settle once the finger lets go, as shares of a loop */
private const val Fade = 0.04f
private const val Land = 0.08f

/** how far the pill follows the finger, as a share of a stride, before it is let go and settles on its own */
private const val Follow = 0.45f

/** the share of the bar's collapse after which the padlock and reload are gone: BottomBar's DETAILS_GONE */
private const val DetailsGone = 0.3f

/**
 * one scene, drawn in code: this phone's own screen in miniature, filling the height it is given, with the
 * app's real bar, spacing and text measures at the same scale, and a fingertip showing the gesture. flat:
 * tones stepped between the raised surface and the ink, the blue only for the touch and the open tab. the
 * clock runs while [active] (the page is the one in view), from the first frame each time, and is read
 * while drawing, so the film redraws the canvas and recomposes nothing
 */
@Composable
fun OnboardingArt(scene: Scene, active: Boolean, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val type = PaneTheme.type
    val window = LocalWindowInfo.current.containerSize
    val clock = rememberLoop(scene.ms, run = active)
    val ink = remember(colors) { Ink(colors.elevatedSurface, colors.background, colors.label, colors.accent) }
    val dark = remember { Ink(PrivateColors.elevatedSurface, PrivateColors.background, PrivateColors.label, PrivateColors.accent) }
    val glyphs = Glyphs(
        lock = rememberVectorPainter(PaneIcons.LockFill),
        more = rememberVectorPainter(PaneIcons.More),
        plus = rememberVectorPainter(PaneIcons.Plus),
        back = rememberVectorPainter(PaneIcons.Back),
        reload = rememberVectorPainter(PaneIcons.Reload),
    )
    Canvas(modifier) {
        if (window.width == 0 || window.height == 0) return@Canvas
        val t = clock.value
        // the screen plus its body, a gap wide all round, fitted to the space given
        val frame = 2 * BarMetrics.gap.toPx()
        val k = min(size.width / (window.width + frame), size.height / (window.height + frame))
        val s = Screen(
            w = window.width * k,
            h = window.height * k,
            dp = density * k,
            pill = BarMetrics.pill.toPx() * k,
            mini = BarMetrics.mini.toPx() * k,
            gap = BarMetrics.gap.toPx() * k,
            unit = Spacing.gutter.toPx() * k,
            line = type.body.fontSize.toPx() * k / 2,
            pitch = type.body.lineHeight.toPx() * k,
            small = type.caption.fontSize.value / type.body.fontSize.value,
        )
        translate((size.width - s.w) / 2, (size.height - s.h) / 2) {
            when (scene) {
                Scene.Bar -> barScene(t, s, ink, glyphs)
                Scene.Sideways -> sidewaysScene(t, s, ink, glyphs)
                Scene.Up -> upScene(t, s, ink, dark, glyphs)
            }
        }
    }
}

/** The screen at the drawing's scale, and Pane's measures at the same scale. [line] is body text's x-height. */
private class Screen(
    val w: Float,
    val h: Float,
    val dp: Float,
    val pill: Float,
    val mini: Float,
    val gap: Float,
    val unit: Float,
    val line: Float,
    val pitch: Float,
    val small: Float,
) {
    /** Concentric with the bar's pill, which sits [gap] in from the bottom corners. */
    val corner = pill / 2 + gap

    /** Where content starts, below the status bar and the camera. */
    val top = 3 * unit
    val barTop = h - gap - pill

    /** the pill's width with no back button: the row less the menu button */
    val slot = w - 3 * gap - pill
    val reach = h / 4
    val stroke = gap / 4

    /** the host's width in the open pill; [small] times that in the collapsed bar */
    val host = 1.6f * pill

    /** the bar's row of the pill, and of the cluster in the overview */
    val row = Rect(gap, barTop, w - 2 * gap - pill, barTop + pill)
}

/** A page is [paper]; Pane's own screens are [ground] with paper raised on it, as in the app. */
private class Ink(val paper: Color, val ground: Color, val ink: Color, val accent: Color) {
    fun tone(f: Float) = lerp(paper, ink, f)

    /** the bar's glass as a flat tone: the real one blurs the page behind it, which would not read at this size */
    val glass = tone(Soft)

    /** The [i]th site's favicon, each a step darker than the one before. */
    fun site(i: Int) = tone(Soft + (Strong - Soft) * i / (Count - 1))
}

private class Glyphs(val lock: VectorPainter, val more: VectorPainter, val plus: VectorPainter, val back: VectorPainter, val reload: VectorPainter)

/** 0 to 1 as [t] runs from [a] to [b], eased at both ends and never past them: how a fingertip moves. */
private fun seg(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** the same run on a critically damped spring, like the app's own motion: quick off the mark, a long soft landing, no overshoot */
private fun settle(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f) * Damped
    return (1f - (1f + x) * exp(-x)) / (1f - (1f + Damped) * exp(-Damped))
}

/** the fingertip's opacity for a gesture running from [from] to [to]: in just before, out just after */
private fun shown(t: Float, from: Float, to: Float) = seg(t, from - Fade, from) * (1f - seg(t, to, to + Fade))

/** The after-state fades out and the loop starts over, fading in. */
private fun restartFade(t: Float) = if (t >= Restart) seg(t, Restart, 1f) else 1f - seg(t, Restart - 0.07f, Restart)

/** Draws [block] as one layer at [alpha], so overlapping shapes fade together. */
private inline fun DrawScope.layer(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 1f) return block()
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().also { it.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/** A screen scaled into [r], its corners scaled with it; [content] is drawn in the full screen's coordinates. */
private inline fun DrawScope.sheet(r: Rect, s: Screen, c: Ink, content: DrawScope.() -> Unit) {
    val f = r.width / s.w
    val outline = Path().apply { addRoundRect(RoundRect(r, CornerRadius(s.corner * f))) }
    drawPath(outline, c.paper)
    clipPath(outline) { withTransform({ translate(r.left, r.top); scale(f, f, Offset.Zero) }) { content() } }
}

/** The phone: a body a gap wider than the screen, the screen, and the camera over whatever is on it. */
private inline fun DrawScope.phone(s: Screen, c: Ink, content: DrawScope.() -> Unit) {
    drawRoundRect(c.tone(Soft), Offset(-s.gap, -s.gap), Size(s.w + 2 * s.gap, s.h + 2 * s.gap), CornerRadius(s.corner + s.gap))
    sheet(Rect(0f, 0f, s.w, s.h), s, c, content)
    drawCircle(c.ink, s.gap / 2, Offset(s.w / 2, 2 * s.gap))
}

private fun DrawScope.textLine(x: Float, y: Float, width: Float, thickness: Float, color: Color) =
    drawLine(color, Offset(x + thickness / 2, y), Offset(x + width - thickness / 2, y), thickness, StrokeCap.Round)

private fun DrawScope.glyph(p: VectorPainter, at: Offset, side: Float, tint: Color) =
    translate(at.x - side / 2, at.y - side / 2) { with(p) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) } }

/** A web page in tones: a picture, a headline and a paragraph, over and over to the screen's end. */
private fun DrawScope.page(s: Screen, c: Ink, seed: Int, scroll: Float = 0f) {
    val column = s.w - 2 * s.unit
    var y = s.top - scroll
    var i = seed
    while (y < s.h) {
        val picture = column / (2 + i % 2)
        drawRoundRect(c.tone(Quiet), Offset(s.unit, y), Size(column, picture), CornerRadius(s.gap))
        y += picture + s.unit
        textLine(s.unit, y + s.line, column * Headline, 2 * s.line, c.tone(Strong))
        y += 2 * s.line + s.unit
        Rag.forEachIndexed { j, f -> textLine(s.unit, y + s.line / 2 + j * s.pitch, column * f, s.line, c.tone(Soft)) }
        y += (Rag.size - 1) * s.pitch + s.line + 2 * s.unit
        i++
    }
}

/** the address pill as BottomBar lays it out: padlock and reload at its ends, fading as [details] goes to 0, the host between at [k] of its size */
private fun DrawScope.pill(r: Rect, s: Screen, c: Ink, g: Glyphs, details: Float = 1f, k: Float = 1f) {
    drawRoundRect(c.glass, r.topLeft, r.size, CornerRadius(r.height / 2))
    if (details > 0f) {
        val tint = c.ink.copy(alpha = details)
        glyph(g.lock, Offset(r.left + 23 * s.dp, r.center.y), 12 * s.dp, tint)
        glyph(g.reload, Offset(r.right - 23 * s.dp, r.center.y), 17 * s.dp, tint)
    }
    textLine(r.center.x - s.host * k / 2, r.center.y, s.host * k, s.line * k, c.ink)
}

/** one of the bar's round buttons, centred on [at] and scaled by [k] about its middle */
private fun DrawScope.round(at: Offset, k: Float, s: Screen, c: Ink, p: VectorPainter) {
    if (k <= 0f) return
    withTransform({ scale(k, k, at) }) {
        drawCircle(c.glass, s.pill / 2, at)
        glyph(p, at, s.pill / 2, c.ink)
    }
}

private fun DrawScope.menu(s: Screen, c: Ink, g: Glyphs) =
    round(Offset(s.w - s.gap - s.pill / 2, s.barTop + s.pill / 2), 1f, s, c, g.more)

/**
 * the whole bar at collapse [p], laid out as BottomBar does: the pill shrinks to the host label, its middle
 * travelling to the screen's with the bottom edge staying put, the host shrinks from body to caption, the
 * padlock and reload are gone by [DetailsGone], and the round buttons shrink into the pill's middle, drawn
 * first so it covers them. [back] is how far the back button has come out
 */
private fun DrawScope.bar(s: Screen, c: Ink, g: Glyphs, p: Float, back: Float = 1f) {
    val start = s.gap + back * (s.pill + s.gap)
    val end = s.w - 2 * s.gap - s.pill
    val width = lerp(end - start, s.host * s.small + s.mini, p)
    val height = lerp(s.pill, s.mini, p)
    val x = lerp((start + end) / 2, s.w / 2, p)
    val r = Rect(x - width / 2, s.barTop + s.pill - height, x + width / 2, s.barTop + s.pill)
    val tucked = max(p, 1f - back)
    round(Offset(lerp(s.w - s.gap - s.pill / 2, x, p), r.center.y), 1f - p, s, c, g.more)
    round(Offset(lerp(s.gap + s.pill / 2, x, tucked), r.center.y), 1f - tucked, s, c, g.back)
    pill(r, s, c, g, details = 1f - p / DetailsGone, k = lerp(1f, s.small, p))
}

/** The fingertip, as wide as the pill is tall, and the streak of where it has been. */
private fun DrawScope.touch(from: Offset, at: Offset, alpha: Float, s: Screen, c: Ink) {
    if (alpha <= 0f) return
    val radius = s.pill / 2 * (0.75f + 0.25f * alpha)
    drawLine(c.accent.copy(alpha = 0.16f * alpha), from, at, s.pill, StrokeCap.Round)
    drawCircle(c.accent.copy(alpha = 0.3f * alpha), radius, at)
    drawCircle(c.accent.copy(alpha = alpha), radius, at, style = Stroke(s.stroke))
}

/** scrolling the page: the bar melts into its host label as the content moves up, and comes back as it moves down */
private fun DrawScope.barScene(t: Float, s: Screen, c: Ink, g: Glyphs) {
    val down = seg(t, 0.12f, 0.36f)
    val up = seg(t, 0.60f, 0.84f)
    // the collapse follows the scroll over the strip the bar takes (BrowserScreen's dynamicPx), then holds; scrolling back unfolds it the same way
    val strip = s.pill + 2 * s.gap
    val p = min(1f, s.reach * down / strip) - min(1f, s.reach * up / strip)
    phone(s, c) {
        page(s, c, seed = 0, scroll = s.reach * (down - up))
        bar(s, c, g, p)
    }
    val x = s.w / 2
    touch(Offset(x, s.h * 0.62f), Offset(x, s.h * 0.62f - s.reach * down), shown(t, 0.12f, 0.36f), s, c)
    touch(Offset(x, s.h * 0.4f), Offset(x, s.h * 0.4f + s.reach * up), shown(t, 0.6f, 0.84f), s, c)
}

/**
 * swiping the pill left twice: to the next tab, then past the last one to a new tab, whose pill opens into
 * the address field as the keyboard comes up. the pill follows the finger, then is let go and settles on a
 * spring, as the real one does
 */
private fun DrawScope.sidewaysScene(t: Float, s: Screen, c: Ink, g: Glyphs) {
    val visible = restartFade(t)
    val u = if (t >= Restart) 0f else t
    val stride = s.slot + s.gap
    fun hop(a: Float, b: Float): Float {
        val follow = Follow * seg(u, a, b)
        return follow + (1f - follow) * settle(u, b, b + Land)
    }
    val pos = hop(0.10f, 0.19f) + hop(0.35f, 0.44f)
    val open = settle(u, 0.58f, 0.70f)
    // the keyboard comes up, and the field and the editor's sites ride on it
    val rise = ((KeyRows.size + 1) * s.pill + 2 * s.gap) * open
    val wider = s.w - 2 * s.gap - s.slot
    val slot = Rect(s.gap, s.barTop, s.gap + s.slot, s.barTop + s.pill)
    phone(s, c) {
        layer(visible) {
            if (pos < 1f) translate(left = -s.w * pos) { page(s, c, seed = 0) }
            if (pos > 0f && pos < 2f) translate(left = s.w * (1f - pos)) { page(s, c, seed = 1) }
            // the new tab: the editor's own page, on the ground
            if (pos > 1f) {
                translate(left = s.w * (2f - pos)) {
                    drawRect(c.ground, size = Size(s.w, s.h))
                    layer(open) { translate(top = -rise) { editor(s, c) } }
                }
            }
            layer(1f - open) { menu(s, c, g) }
            keyboard(s.h - rise, s, c)
            if (open > 0f) {
                // the new tab's pill has become the field, with its cursor, and has risen with the keyboard
                val field = Rect(slot.left, slot.top - rise, slot.right + wider * open, slot.bottom - rise)
                drawRoundRect(c.glass, field.topLeft, field.size, CornerRadius(s.pill / 2))
                val x = field.left + s.pill / 2
                drawLine(c.accent, Offset(x, field.center.y - s.pill / 4), Offset(x, field.center.y + s.pill / 4), s.stroke)
            } else {
                // the pills pass through the strip's slot, cut off at its sides as the real bar's are
                clipRect(slot.left, slot.top - s.pill, slot.right, slot.bottom + s.pill) {
                    repeat(3) { i ->
                        val r = slot.translate(stride * (i - pos), 0f)
                        if (i < 2) pill(r, s, c, g, k = 1f - 0.15f * i) else newTab(r, s, c)
                    }
                }
            }
        }
    }
    val from = Offset(slot.left + s.slot * 3 / 4, slot.center.y)
    fun swipe(a: Float, b: Float) = touch(from, from.copy(x = from.x - Follow * stride * seg(u, a, b)), shown(u, a, b), s, c)
    swipe(0.10f, 0.19f)
    swipe(0.35f, 0.44f)
}

/** the new tab's pill: a short line of muted text where a host would be */
private fun DrawScope.newTab(r: Rect, s: Screen, c: Ink) {
    drawRoundRect(c.glass, r.topLeft, r.size, CornerRadius(r.height / 2))
    textLine(r.center.x - s.host * 0.35f, r.center.y, s.host * 0.7f, s.line, c.tone(Strong))
}

/** The keyboard from [top] down: rows of raised keys on the editor's ground, and the space bar. */
private fun DrawScope.keyboard(top: Float, s: Screen, c: Ink) {
    if (top >= s.h) return
    val pitch = s.w / KeyRows[0]
    fun key(n: Int, row: Int, span: Int = 1) = (0 until n).forEach { i ->
        val x = (s.w - n * span * pitch) / 2 + i * span * pitch
        drawRoundRect(c.paper, Offset(x + s.gap / 4, top + s.gap + row * s.pill), Size(span * pitch - s.gap / 2, s.pill - s.gap), CornerRadius(s.gap / 2))
    }
    KeyRows.forEachIndexed { row, n -> key(n, row) }
    key(1, KeyRows.size, span = KeyRows[0] / 2)
}

/** The empty address editor above its field: favourite sites on tiles, then recent pages, a favicon each. */
private fun DrawScope.editor(s: Screen, c: Ink) {
    val column = s.w - 2 * s.unit
    val icon = s.pill / 2
    var y = s.barTop - s.unit
    for (i in 0 until Count) {
        y -= s.pill
        val mid = y + s.pill / 2
        drawRoundRect(c.site(i), Offset(s.unit, mid - icon / 2), Size(icon, icon), CornerRadius(s.gap / 2))
        textLine(s.unit + icon + s.unit, mid, (column - icon - s.unit) * Rag[i], s.line, c.tone(Soft))
    }
    y -= 2 * s.unit + s.line + s.gap + s.pill
    val cell = column / Count
    for (i in 0 until Count) {
        val x = s.unit + cell * i + (cell - s.pill) / 2
        drawRoundRect(c.paper, Offset(x, y), Size(s.pill, s.pill), CornerRadius(s.gap))
        drawRoundRect(c.site(i), Offset(x + icon / 2, y + icon / 2), Size(icon, icon), CornerRadius(s.gap / 2))
        textLine(x + s.gap, y + s.pill + s.gap + s.line / 2, s.pill - 2 * s.gap, s.line, c.tone(Soft))
    }
}

/**
 * swiping the pill up: the page shrinks into its card among the others and the overview's row rises. then a
 * tap on private: the switch slides over and the overview goes to the private ground, empty; a tap on the
 * tabs brings the cards back
 */
private fun DrawScope.upScene(t: Float, s: Screen, c: Ink, dark: Ink, g: Glyphs) {
    val visible = restartFade(t)
    val u = if (t >= Restart) 0f else t
    val up = seg(u, 0.10f, 0.30f)
    // 1 while the private side is showing: the switch slides over on a spring, and back
    val away = settle(u, 0.48f, 0.58f) - settle(u, 0.72f, 0.82f)
    phone(s, c) {
        layer(visible) {
            layer(1f - away) { overview(s, c, g, up, away) }
            layer(away) { emptyOverview(s, dark, g, away) }
        }
    }
    val from = Offset(s.gap + s.slot / 2, s.barTop + s.pill / 2)
    touch(from, from.copy(y = from.y - s.reach * up), shown(u, 0.10f, 0.30f), s, c)
    // the taps land on the two halves of the switch
    fun tap(a: Float, half: Float) {
        val at = Offset(s.row.left + s.row.width * half, s.row.center.y)
        touch(at, at, shown(u, a, a + 0.02f), s, c)
    }
    tap(0.46f, 0.25f)
    tap(0.70f, 0.75f)
}

/** the overview as the page shrinks into its card at [up]: cards and captions, the open tab wearing the accent, and the row */
private fun DrawScope.overview(s: Screen, c: Ink, g: Glyphs, up: Float, away: Float) {
    // two rows of cards and captions fill the screen above the overview's row; pages crop to fit, as thumbnails do
    val card = Size((s.w - 3 * s.unit) / 2, (s.barTop - s.top - 2 * s.gap - 4 * s.unit) / 2)
    fun at(i: Int) = Rect(Offset(s.unit + i % 2 * (card.width + s.unit), s.top + i / 2 * (card.height + s.gap + 2 * s.unit)), card)
    drawRect(lerp(c.paper, c.ground, up), size = Size(s.w, s.h))
    layer(up) {
        for (i in 0 until Count) {
            val r = at(i)
            if (i != OpenTab) sheet(r, s, c) { page(s, c, seed = i) }
            drawRoundRect(c.site(i), Offset(r.left, r.bottom + s.gap), Size(s.unit, s.unit), CornerRadius(s.gap / 2))
            textLine(r.left + s.unit + s.gap, r.bottom + s.gap + s.unit / 2, r.width / 2, s.line, c.tone(Soft))
        }
    }
    val flying = lerp(Rect(0f, 0f, s.w, s.h), at(OpenTab), up)
    sheet(flying, s, c) { page(s, c, seed = OpenTab) }
    val corner = CornerRadius(s.corner * flying.width / s.w)
    drawRoundRect(c.accent.copy(alpha = seg(up, 0.5f, 1f)), flying.topLeft, flying.size, corner, Stroke(s.stroke))
    // the bar goes, then the overview's row comes up in its place
    layer(1f - seg(up, 0f, 0.5f)) { bar(s, c, g, p = 0f, back = 0f) }
    val row = seg(up, 0.5f, 1f)
    layer(row) { translate(top = s.unit * (1f - row)) { cluster(s, c, g, away) } }
}

/** the private side with nothing open: the private ground, a title and a line under it, and the row */
private fun DrawScope.emptyOverview(s: Screen, c: Ink, g: Glyphs, away: Float) {
    drawRect(c.ground, size = Size(s.w, s.h))
    val y = (s.top + s.barTop) / 2
    textLine(s.w / 2 - s.pill * 0.6f, y, s.pill * 1.2f, 2 * s.line, c.ink)
    textLine(s.w / 2 - s.pill * 0.9f, y + s.unit + s.line, s.pill * 1.8f, s.line, c.tone(Soft))
    cluster(s, c, g, away)
}

/** the overview's row: the private and normal switch, its thumb [away] of the way to the private side, and a new tab */
private fun DrawScope.cluster(s: Screen, c: Ink, g: Glyphs, away: Float) {
    val r = s.row
    drawRoundRect(c.tone(Soft), r.topLeft, r.size, CornerRadius(r.height / 2))
    val half = r.width / 2
    val on = Rect(r.left + half * (1f - away), r.top, r.left + half * (2f - away), r.bottom).deflate(s.gap / 2)
    drawRoundRect(c.paper, on.topLeft, on.size, CornerRadius(on.height / 2))
    textLine(r.left + r.width / 4 - s.pill / 2, r.center.y, s.pill, s.line, lerp(c.tone(Strong), c.ink, away))
    textLine(r.left + r.width * 3 / 4 - s.pill / 2, r.center.y, s.pill, s.line, lerp(c.ink, c.tone(Strong), away))
    val at = Offset(s.w - s.gap - s.pill / 2, s.barTop + s.pill / 2)
    drawCircle(c.tone(Soft), s.pill / 2, at)
    glyph(g.plus, at, s.pill / 2, c.ink)
}
