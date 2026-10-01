package app.pane.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme

/** The screen margin: where text starts, and where hairlines and trailing controls stop. */
internal val RowMargin = 20.dp

/** Every row is at least this tall, so it is easy to hit. */
internal val RowMinHeight = 56.dp

/** The gap between a row's glyph (or icon) and its title. */
internal val LeadingGap = 16.dp

/** The space above a section that has no heading of its own. */
private val HeadlessGap = 20.dp

/** The space above a section heading. */
private val HeadingGap = 28.dp

/** Collects the rows of a [GroupedSection] so separators can be drawn between them. */
class SectionBuilder internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A flat list section: an optional small-caps heading, full-width rows with a hairline between them
 * (starting at [separatorInset], where the text starts), and an optional footnote. No card, no fill:
 * the rows sit directly on the page.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    separatorInset: Dp = RowMargin,
    rows: SectionBuilder.() -> Unit,
) {
    val colors = PaneTheme.colors
    val built = SectionBuilder().apply(rows).rows
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            SectionLabel(
                header,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = RowMargin, end = RowMargin, top = HeadingGap, bottom = 4.dp),
            )
        } else {
            Spacer(Modifier.height(HeadlessGap))
        }
        built.forEachIndexed { index, row ->
            row()
            if (index < built.lastIndex) Separator(Modifier.padding(start = separatorInset, end = RowMargin))
        }
        if (footer != null) {
            Text(
                footer,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = RowMargin, end = RowMargin, top = 10.dp),
            )
        }
    }
}

@Composable
fun Separator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(PaneTheme.colors.separator),
    )
}

/**
 * Rows used to lead with a coloured glyph tile. Pane's rows are plain type, so this draws nothing;
 * call sites keep compiling.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IconTile(icon: ImageVector, background: Color, modifier: Modifier = Modifier) = Unit

/**
 * Press feedback for a row: it dims while held and springs back, instead of a fill flash or a
 * ripple. Also takes a long press. The alpha is read while drawing, so nothing recomposes.
 */
@Composable
internal fun Modifier.rowPress(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dim = animateFloatAsState(if (pressed) 0.4f else 1f, Motion.snappy(), label = "rowDim")
    return this
        .graphicsLayer { alpha = dim.value }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * The basic row: title on the left, the current value (quiet) and any control on the right. When
 * [onClick] is set and [showChevron] is true a small disclosure chevron ends the row. A row may
 * lead with a [leading] glyph; it is drawn as it is, with nothing behind it.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    titleColor: Color = PaneTheme.colors.label,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = PaneTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = RowMinHeight)
            .then(if (onClick != null) Modifier.rowPress(onClick = onClick) else Modifier)
            .padding(horizontal = RowMargin, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(LeadingGap))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PaneTheme.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        val chevron = onClick != null && showChevron
        if (value != null || trailing != null || chevron) {
            // The value, any control and the chevron travel together, apart from the title.
            Row(
                Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (value != null) {
                    // Capped so a very long value truncates instead of squeezing the title out.
                    Text(
                        value,
                        style = PaneTheme.type.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                }
                trailing?.invoke(this)
                if (chevron) {
                    Icon(PaneIcons.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = leading,
        modifier = modifier,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        showChevron = false,
    ) {
        PaneSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A row in a single-choice list; shows a checkmark when selected. */
@Composable
fun CheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    ListRow(title = title, subtitle = subtitle, leading = leading, modifier = modifier, onClick = onClick, showChevron = false) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(PaneIcons.Check, null, tint = PaneTheme.colors.label, modifier = Modifier.size(20.dp))
        }
    }
}

/** A full-width action such as "Clear History" (destructive = red text). */
@Composable
fun ActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    ListRow(
        title = title,
        modifier = modifier,
        titleColor = if (destructive) PaneTheme.colors.destructive else PaneTheme.colors.label,
        onClick = onClick,
        showChevron = false,
    )
}
