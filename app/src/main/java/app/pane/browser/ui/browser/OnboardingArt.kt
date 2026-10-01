package app.pane.browser.ui.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalWindowInfo
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.LocalReduceMotion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import kotlin.math.min

/** The three little films of the onboarding carousel. Each loops, quietly, on its own. */
enum class Scene { Bar, Sideways, Up }

private const val LoopMs = 4800

/** The moment shown when motion is reduced: the gesture done. */
private const val Done = 0.75f

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

/**
 * One scene, drawn in code: this phone's own screen in miniature, filling the height it is given, with
 * Pane's real bar, spacing and text measures at the same scale, and a fingertip showing the gesture.
 * Flat: tones stepped between the raised surface and the ink, the blue only for the touch and the open tab.
 */
@Composable
fun OnboardingArt(scene: Scene, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    val body = PaneTheme.type.body
    val window = LocalWindowInfo.current.containerSize
    val reduce = LocalReduceMotion.current
    val clock = rememberInfiniteTransition(label = "art")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(LoopMs, easing = LinearEasing)), label = "t")
    val ink = remember(colors) { Ink(colors.elevatedSurface, colors.background, colors.label, colors.accent, colors.onAccent) }
    val glyphs = Glyphs(
        lock = rememberVectorPainter(PaneIcons.LockFill),
        more = rememberVectorPainter(PaneIcons.More),
        plus = rememberVectorPainter(PaneIcons.Plus),
    )
    Canvas(modifier) {
        if (window.width == 0 || window.height == 0) return@Canvas
        // read here, in the draw phase: the loop redraws the drawing and recomposes nothing
        val t = if (reduce) Done else clock.value
        // the screen plus its body, a gap wide all round, fitted to the space given
        val frame = 2 * BarMetrics.gap.toPx()
        val k = min(size.width / (window.width + frame), size.height / (window.height + frame))
        val s = Screen(
            w = window.width * k,
            h = window.height * k,
            pill = BarMetrics.pill.toPx() * k,
            mini = BarMetrics.mini.toPx() * k,
            gap = BarMetrics.gap.toPx() * k,
            unit = Spacing.gutter.toPx() * k,
            line = body.fontSize.toPx() * k / 2,
            pitch = body.lineHeight.toPx() * k,
        )
        translate((size.width - s.w) / 2, (size.height - s.h) / 2) {
            when (scene) {
                Scene.Bar -> barScene(t, s, ink, glyphs)
                Scene.Sideways -> sidewaysScene(t, s, ink, glyphs)
                Scene.Up -> upScene(t, s, ink, glyphs)
            }
        }
    }
}

/** The screen at the drawing's scale, and Pane's measures at the same scale. [line] is body text's x-height. */
private class Screen(
    val w: Float,
    val h: Float,
    val pill: Float,
    val mini: Float,
    val gap: Float,
    val unit: Float,
    val line: Float,
    val pitch: Float,
) {
    /** Concentric with the bar's pill, which sits [gap] in from the bottom corners. */
    val corner = pill / 2 + gap

    /** Where content starts, below the status bar and the camera. */
    val top = 3 * unit
    val barTop = h - gap - pill

    /** The pill's width: the row less the two round buttons. */
    val slot = w - 3 * gap - pill
    val reach = h / 4
    val stroke = gap / 4
}

/** A page is [paper]; Pane's own screens are [ground] with paper raised on it, as in the app. */
private class Ink(val paper: Color, val ground: Color, val ink: Color, val accent: Color, val onAccent: Color) {
    fun tone(f: Float) = lerp(paper, ink, f)

    /** The [i]th site's favicon, each a step darker than the one before. */
    fun site(i: Int) = tone(Soft + (Strong - Soft) * i / (Count - 1))
}

private class Glyphs(val lock: VectorPainter, val more: VectorPainter, val plus: VectorPainter)

/** 0 to 1 as [t] runs from [a] to [b], eased at both ends and never past them. */
private fun seg(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

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

/** The address pill in ink: the padlock, and the host in the middle. */
private fun DrawScope.pill(r: Rect, s: Screen, c: Ink, g: Glyphs) {
    drawRoundRect(c.ink, r.topLeft, r.size, CornerRadius(r.height / 2))
    glyph(g.lock, Offset(r.left + r.height / 2, r.center.y), s.pill / 4, c.paper)
    textLine(r.center.x - s.pill, r.center.y, 2 * s.pill, s.line, c.paper)
}

private fun DrawScope.button(x: Float, s: Screen, fill: Color, p: VectorPainter, tint: Color) {
    val at = Offset(x, s.barTop + s.pill / 2)
    drawCircle(fill, s.pill / 2, at)
    glyph(p, at, s.pill / 2, tint)
}

/** The menu button at the bar's end. */
private fun DrawScope.buttons(s: Screen, c: Ink, g: Glyphs) {
    button(s.w - s.gap - s.pill / 2, s, c.tone(Soft), g.more, c.ink)
}

/** The fingertip, as wide as the pill is tall, and the streak of where it has been. */
private fun DrawScope.touch(from: Offset, at: Offset, alpha: Float, s: Screen, c: Ink) {
    if (alpha <= 0f) return
    drawLine(c.accent.copy(alpha = 0.16f * alpha), from, at, s.pill, StrokeCap.Round)
    drawCircle(c.accent.copy(alpha = 0.3f * alpha), s.pill / 2, at)
    drawCircle(c.accent.copy(alpha = alpha), s.pill / 2, at, style = Stroke(s.stroke))
}

/** In, the gesture, out: the fingertip's opacity over a loop whose gesture ends at [end]. */
private fun shownFinger(t: Float, end: Float) = seg(t, 0.04f, 0.12f) * (1f - seg(t, end, end + 0.08f))

/** The after-state fades out and the loop starts over, fading in. */
private fun restartFade(t: Float) = if (t >= Restart) seg(t, Restart, 1f) else 1f - seg(t, Restart - 0.07f, Restart)

/** Scrolling the page tucks the bar into a small host label; then it all comes back. */
private fun DrawScope.barScene(t: Float, s: Screen, c: Ink, g: Glyphs) {
    val scroll = seg(t, 0.12f, 0.5f) * (1f - seg(t, 0.78f, 0.96f))
    phone(s, c) {
        page(s, c, seed = 0, scroll = s.reach * scroll)
        layer(1f - seg(scroll, 0f, 0.5f)) {
            translate(top = s.gap * scroll) {
                pill(Rect(s.gap, s.barTop, s.gap + s.slot, s.barTop + s.pill), s, c, g)
                buttons(s, c, g)
            }
        }
        layer(seg(scroll, 0.5f, 1f)) {
            val label = Rect(Offset(s.w / 2 - s.pill, s.h - s.gap - s.mini), Size(2 * s.pill, s.mini))
            drawRoundRect(c.ink, label.topLeft, label.size, CornerRadius(s.mini / 2))
            textLine(label.center.x - s.pill / 2, label.center.y, s.pill, s.line, c.paper)
        }
    }
    val from = Offset(s.w / 2, (s.h + s.reach) / 2)
    touch(from, from.copy(y = from.y - s.reach * scroll), shownFinger(t, 0.5f), s, c)
}

/** Swiping the pill left: past the last tab comes a new one, and its pill opens into the address field. */
private fun DrawScope.sidewaysScene(t: Float, s: Screen, c: Ink, g: Glyphs) {
    val restart = t >= Restart
    val drag = if (restart) 0f else seg(t, 0.12f, 0.48f)
    val open = if (restart) 0f else seg(t, 0.52f, 0.7f)
    val stride = s.slot + s.gap
    val wider = s.w - 2 * s.gap - s.slot
    // the keyboard comes up, and the field and the editor's sites ride on it
    val rise = ((KeyRows.size + 1) * s.pill + 2 * s.gap) * open
    val shown = restartFade(t)
    phone(s, c) {
        layer(shown) {
            translate(left = -s.w * drag) { page(s, c, seed = 0) }
            // the new tab: the editor's own page, on the ground
            translate(left = s.w * (1f - drag)) {
                drawRect(c.ground, size = Size(s.w, s.h))
                layer(open) { translate(top = -rise) { editor(s, c) } }
            }
            layer(1f - open) { buttons(s, c, g) }
            keyboard(s.h - rise, s, c)
            // the new tab's pill: a plus, then the field with its cursor
            fun field(r: Rect) {
                drawRoundRect(c.ink, r.topLeft, r.size, CornerRadius(s.pill / 2))
                glyph(g.plus, r.center, s.pill / 2, c.paper.copy(alpha = 1f - open))
                val x = r.left + s.pill / 2
                drawLine(c.accent.copy(alpha = open), Offset(x, r.center.y - s.pill / 4), Offset(x, r.center.y + s.pill / 4), s.stroke)
            }
            val slot = Rect(s.gap, s.barTop, s.gap + s.slot, s.barTop + s.pill)
            if (open > 0f) {
                field(Rect(slot.left, slot.top - rise, slot.right + wider * open, slot.bottom - rise))
            } else {
                // the pills pass through a pill-shaped window, so their ends stay round
                clipPath(Path().apply { addRoundRect(RoundRect(slot, CornerRadius(s.pill / 2))) }) {
                    pill(slot.translate(-stride * drag, 0f), s, c, g)
                    field(slot.translate(stride * (1f - drag), 0f))
                }
            }
        }
    }
    val from = Offset(s.gap + s.slot * 3 / 4, s.barTop + s.pill / 2)
    touch(from, from.copy(x = from.x - s.slot / 2 * drag), shown * shownFinger(t, 0.48f), s, c)
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

/** Swiping the pill up: the page shrinks into its card among the others, and the overview's buttons rise. */
private fun DrawScope.upScene(t: Float, s: Screen, c: Ink, g: Glyphs) {
    val up = if (t >= Restart) 0f else seg(t, 0.12f, 0.5f)
    val shown = restartFade(t)
    // two rows of cards and captions fill the screen above the overview's row; pages crop to fit, as thumbnails do
    val card = Size((s.w - 3 * s.unit) / 2, (s.barTop - s.top - 2 * s.gap - 4 * s.unit) / 2)
    fun at(i: Int) = Rect(Offset(s.unit + i % 2 * (card.width + s.unit), s.top + i / 2 * (card.height + s.gap + 2 * s.unit)), card)
    phone(s, c) {
        layer(shown) {
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
            layer(1f - seg(up, 0f, 0.5f)) {
                pill(Rect(s.gap, s.barTop, s.gap + s.slot, s.barTop + s.pill), s, c, g)
                buttons(s, c, g)
            }
            val row = seg(up, 0.5f, 1f)
            layer(row) { translate(top = s.unit * (1f - row)) { cluster(s, c, g) } }
        }
    }
    val from = Offset(s.gap + s.slot / 2, s.barTop + s.pill / 2)
    touch(from, from.copy(y = from.y - s.reach * up), shown * shownFinger(t, 0.5f), s, c)
}

/** The overview's row: the private and normal switch, and a new tab. */
private fun DrawScope.cluster(s: Screen, c: Ink, g: Glyphs) {
    val r = Rect(s.gap, s.barTop, s.w - 2 * s.gap - s.pill, s.barTop + s.pill)
    drawRoundRect(c.tone(Soft), r.topLeft, r.size, CornerRadius(r.height / 2))
    val on = Rect(r.center.x, r.top, r.right, r.bottom).deflate(s.gap / 2)
    drawRoundRect(c.paper, on.topLeft, on.size, CornerRadius(on.height / 2))
    textLine(on.center.x - s.pill / 2, on.center.y, s.pill, s.line, c.ink)
    textLine(r.left + r.width / 4 - s.pill / 2, r.center.y, s.pill, s.line, c.tone(Strong))
    button(s.w - s.gap - s.pill / 2, s, c.tone(Soft), g.plus, c.ink)
}
