package app.pane.browser.ui.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.components.GlyphSize
import app.pane.browser.ui.components.LeadingGap
import app.pane.browser.ui.components.QuietButton
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.components.SectionLabel
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.SheetCardRadius
import app.pane.browser.ui.components.autofill
import app.pane.browser.ui.components.excludeFromAutofill
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.EdgeFade
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import app.pane.browser.ui.theme.canScroll
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics

/** The text field inside an alert: a hairline-outlined box, no fill, no floating label. */
@Composable
internal fun AlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    requester: FocusRequester? = null,
    autofillType: ContentType? = null,
) {
    val colors = PaneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(PaneShapes.medium)
            .border(1.dp, colors.tertiaryLabel, PaneShapes.medium)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = PaneTheme.type.body, color = colors.tertiaryLabel, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = PaneTheme.type.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onNext = { onImeAction() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .then(if (autofillType != null) Modifier.autofill(autofillType) else Modifier.excludeFromAutofill()),
        )
    }
}

/** "Block more dialogs", shown inside alerts from a page that keeps opening them. */
@Composable
internal fun OptOutRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PaneShapes.small)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                haptics.toggle(!checked)
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            "Block more dialogs",
            style = PaneTheme.type.footnote,
            color = PaneTheme.colors.secondaryLabel,
        )
    }
}

/** The title of a sheet in `title3`, with an optional quiet line under it. */
@Composable
internal fun SheetTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = PaneTheme.colors
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 12.dp)) {
        Text(title, style = PaneTheme.type.title3, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The action buttons that close a sheet: a solid-ink pill for the primary action and, optionally, a
 * hairline-outlined pill for the other one, stacked or side by side.
 */
@Composable
internal fun SheetButtons(
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    sideBySide: Boolean = false,
) {
    val padding = Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.gutter)
    if (sideBySide && secondary != null && onSecondary != null) {
        Row(modifier.then(padding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(secondary, onSecondary, Modifier.weight(1f), icon = PaneIcons.Close)
            PrimaryButton(primary, onPrimary, Modifier.weight(1f), icon = PaneIcons.Check)
        }
    } else {
        Column(modifier.then(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(primary, onPrimary, icon = PaneIcons.Check)
            if (secondary != null && onSecondary != null) {
                QuietButton(secondary, onSecondary, Modifier.fillMaxWidth(), icon = PaneIcons.Close)
            }
        }
    }
}

/**
 * One flat, full-width row for sheets: a title, an optional quiet value, an optional control
 * ([trailing], such as a switch) and an optional chevron. No fill, no box; the hairlines between
 * rows come from [FlatSection].
 */
@Composable
internal fun FlatRow(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = PaneTheme.colors.label,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val colors = PaneTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = RowMargin, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LeadingGap),
    ) {
        if (icon != null) Icon(icon, null, tint = titleColor, modifier = Modifier.size(GlyphSize))
        Text(
            title,
            style = PaneTheme.type.body,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                value,
                style = PaneTheme.type.body,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
        trailing?.invoke()
        if (chevron) Icon(PaneIcons.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(15.dp))
    }
}

/** A [FlatRow] in a single-choice list: a tick at the end when selected. */
@Composable
internal fun FlatCheckRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlatRow(
        title,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(PaneIcons.Check, null, tint = PaneTheme.colors.label, modifier = Modifier.size(19.dp))
            }
        },
    )
}

/** Collects the rows of a [FlatSection] so hairlines can be drawn between them. */
internal class FlatRows {
    val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A group of [FlatRow]s in a sheet, on one flat card: a hairline between rows (inset to the text), an
 * optional small-capitals [header] and a quiet [footer]. When [entranceIndex] is 0 or more, each
 * row arrives with a staggered entrance starting at that beat.
 */
@Composable
internal fun FlatSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    entranceIndex: Int = -1,
    /** Where the hairline between rows starts: after the glyph column for rows that have glyphs. */
    separatorInset: Dp = RowMargin,
    rows: FlatRows.() -> Unit,
) {
    val colors = PaneTheme.colors
    val built = FlatRows().apply(rows).rows
    // the same gutters as GroupedSection: headings and footnotes line up with the rows' text.
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        if (header != null) {
            SectionLabel(header, modifier = Modifier.padding(start = RowMargin, end = RowMargin, top = Spacing.gutter, bottom = Spacing.gutter / 2))
        }
        // The group is one flat card on the sheet's grey ground; rows are told apart by a short
        // hairline inside it, and groups by the space between cards.
        Column(Modifier.floating(GroupShape, shadow = 0.dp, fill = colors.elevatedSurface)) {
            built.forEachIndexed { index, row ->
                Box(if (entranceIndex >= 0) Modifier.entrance(entranceIndex + index) else Modifier) { row() }
                if (index < built.lastIndex) Separator(Modifier.padding(start = separatorInset, end = RowMargin))
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = RowMargin, end = RowMargin, top = Spacing.gutter / 2),
            )
        }
    }
}

private val GroupShape = ContinuousRoundedShape(SheetCardRadius)

private val ScrollFadeHeight = 28.dp

/**
 * The top and bottom edges of scrolling content in a sheet: a plain fade into the sheet's own fill
 * instead of a hard cut. Each edge fades in as the content moves under it ([top] and [bottom]
 * return how many fade-heights of content are hidden there, read while drawing so nothing
 * recomposes as it scrolls) and is invisible when there is nothing more to see.
 */
@Composable
internal fun BoxScope.ScrollEdges(top: () -> Float, bottom: () -> Float) {
    val fill = PaneTheme.colors.background
    EdgeFade(
        top = true,
        height = ScrollFadeHeight,
        modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = top().coerceIn(0f, 1f) },
        color = fill,
    )
    EdgeFade(
        top = false,
        height = ScrollFadeHeight,
        modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = bottom().coerceIn(0f, 1f) },
        color = fill,
    )
}

/** A vertically scrolling column for sheets whose top and bottom edges fade into the sheet. */
@Composable
internal fun FadingColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val fadePx = with(LocalDensity.current) { ScrollFadeHeight.toPx() }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = scroll.canScroll), content = content)
        ScrollEdges(top = { scroll.value / fadePx }, bottom = { (scroll.maxValue - scroll.value) / fadePx })
    }
}
