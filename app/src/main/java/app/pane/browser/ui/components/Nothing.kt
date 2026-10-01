package app.pane.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme

/**
 * Pane's small typographic signature, after Nothing: text built from round dots on a 5×7 grid.
 * Used sparingly, for the wordmark and for one big number at a time; it is drawn, not a font, so it
 * adds nothing to the app and scales to any size. Letters A–Z, digits and a little punctuation.
 */
@Composable
fun DotText(
    text: String,
    modifier: Modifier = Modifier,
    dot: Dp = 4.dp,
    color: Color = PaneTheme.colors.label,
) {
    val glyphs = remember(text) { text.uppercase().map { DotFont[it] ?: DotFont[' ']!! } }
    val density = LocalDensity.current
    val pitch = with(density) { (dot * 1.6f).toPx() }
    val letterGap = pitch * 1.4f
    val width = glyphs.size * (5 * pitch) + (glyphs.size - 1).coerceAtLeast(0) * letterGap - (pitch - with(density) { dot.toPx() })
    val height = 7 * pitch - (pitch - with(density) { dot.toPx() })
    Canvas(
        modifier
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .semantics { contentDescription = text },
    ) {
        val r = dot.toPx() / 2f
        var x = 0f
        for (glyph in glyphs) {
            for (row in 0 until 7) {
                for (col in 0 until 5) {
                    if (glyph[row][col] == '1') {
                        drawCircle(color, r, Offset(x + col * pitch + r, row * pitch + r))
                    }
                }
            }
            x += 5 * pitch + letterGap
        }
    }
}

/** A section heading in small capitals with open tracking: "PRIVACY". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = PaneTheme.colors.tertiaryLabel) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = PaneTheme.type.caption2.copy(fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A small round status light: Nothing's red for "active", ink for "on". */
@Composable
fun StatusDot(modifier: Modifier = Modifier, color: Color = PaneTheme.colors.signal, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(PaneShapes.pill).background(color))
}

/** A secondary action: a hairline-outlined pill, no fill. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .height(52.dp)
            .pressScale(enabled = enabled, pressedScale = 0.97f, haptic = true, onClick = onClick)
            .clip(PaneShapes.pill)
            .border(1.dp, colors.label.copy(alpha = if (enabled) 0.85f else 0.25f), PaneShapes.pill)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PaneTheme.type.headline, color = if (enabled) colors.label else colors.tertiaryLabel, maxLines = 1)
    }
}

private fun g(vararg rows: String) = rows.toList()

private val DotFont: Map<Char, List<String>> = mapOf(
    ' ' to g("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    'A' to g("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'B' to g("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
    'C' to g("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
    'D' to g("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to g("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'F' to g("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'G' to g("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
    'H' to g("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to g("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
    'J' to g("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'K' to g("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    'L' to g("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'M' to g("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'N' to g("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to g("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'P' to g("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'Q' to g("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
    'R' to g("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    'S' to g("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'T' to g("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'U' to g("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'V' to g("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'W' to g("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
    'X' to g("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    'Y' to g("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'Z' to g("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
    '0' to g("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to g("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to g("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to g("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to g("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to g("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to g("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to g("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to g("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to g("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    '.' to g("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    ',' to g("00000", "00000", "00000", "00000", "01100", "00100", "01000"),
    ':' to g("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
    '-' to g("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '+' to g("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
    '/' to g("00001", "00010", "00010", "00100", "01000", "01000", "10000"),
    '%' to g("11001", "11010", "00010", "00100", "01000", "01011", "10011"),
)
