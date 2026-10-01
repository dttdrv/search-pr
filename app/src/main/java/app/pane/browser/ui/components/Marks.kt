package app.pane.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme

/**
 * A section heading: a few small words in the muted grey, in sentence case. No capitals, no
 * tracking; the quiet colour and the space above it do the work.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = PaneTheme.colors.secondaryLabel) {
    Text(
        text,
        modifier = modifier,
        style = PaneTheme.type.footnote.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A small round status light. Plain ink by default: it says "working" or "on", not "alert". Pass
 * the amber `PaneTheme.colors.warning` for an unsafe connection, and nothing else.
 */
@Composable
fun StatusDot(modifier: Modifier = Modifier, color: Color = PaneTheme.colors.label, size: Dp = 7.dp) {
    Box(modifier.size(size).clip(PaneShapes.pill).background(color))
}
