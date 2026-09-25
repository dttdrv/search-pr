package app.pane.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme

/** Collects the rows of a [GroupedSection] so separators can be drawn between them. */
class SectionBuilder internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * An inset grouped table section (UITableView.Style.insetGrouped): optional caps header, rounded
 * card of rows with hairline separators, optional footnote footer.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    separatorInset: Dp = 16.dp,
    rows: SectionBuilder.() -> Unit,
) {
    val colors = PaneTheme.colors
    val built = SectionBuilder().apply(rows).rows
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 7.dp),
            )
        } else {
            Spacer(Modifier.height(20.dp))
        }
        if (built.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PaneShapes.medium)
                    .background(if (colors.isDark) colors.surface else colors.surface),
            ) {
                built.forEachIndexed { index, row ->
                    row()
                    if (index < built.lastIndex) Separator(Modifier.padding(start = separatorInset))
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
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

/** The rounded, coloured glyph tile used at the start of settings rows. */
@Composable
fun IconTile(icon: ImageVector, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(29.dp)
            .clip(ContinuousRoundedShape(7.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * The basic row. [trailing] sits at the end; when [onClick] is set and [showChevron] is true a
 * disclosure chevron is appended.
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
            .defaultMinSize(minHeight = 44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = PaneTheme.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = PaneTheme.type.footnote, color = colors.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            // Capped so a long value (an app name, say) truncates instead of squeezing the title.
            Text(
                value,
                style = PaneTheme.type.body,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        trailing?.invoke(this)
        if (onClick != null && showChevron) {
            Icon(PaneIcons.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(16.dp))
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
            if (selected) Icon(PaneIcons.Check, null, tint = PaneTheme.colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** A full-width centred action such as "Clear History" (destructive = red). */
@Composable
fun ActionRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    ListRow(
        title = title,
        modifier = modifier,
        titleColor = if (destructive) PaneTheme.colors.destructive else PaneTheme.colors.accent,
        onClick = onClick,
        showChevron = false,
    )
}
