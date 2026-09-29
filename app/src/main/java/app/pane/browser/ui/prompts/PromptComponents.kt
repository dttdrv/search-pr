package app.pane.browser.ui.prompts

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.autofill
import app.pane.browser.ui.components.excludeFromAutofill
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.ProgressiveEdge
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** The text field inside an alert: a soft pill of fill on the glass, no border, no floating label. */
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
            .height(46.dp)
            .clip(PaneShapes.medium)
            .background(colors.fill)
            .padding(horizontal = 14.dp),
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

/** A round checkbox: an empty ring, or a solid ink disc with a tick. */
@Composable
internal fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val colors = PaneTheme.colors
    val fill by animateColorAsState(if (checked) colors.accent else Color.Transparent, Motion.fade(150), label = "check")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .drawBehind { drawRect(fill) }
            .then(if (checked) Modifier else Modifier.border(1.5.dp, colors.tertiaryLabel, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(PaneIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(size * 0.62f))
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
        CheckCircle(checked, size = 20.dp)
        Text(
            "Block more dialogs",
            style = PaneTheme.type.footnote,
            color = PaneTheme.colors.secondaryLabel,
        )
    }
}

/** A big plain title for the top of a sheet, with an optional line of body text under it. */
@Composable
internal fun SheetTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = PaneTheme.colors
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 14.dp)) {
        Text(title, style = PaneTheme.type.title2, color = colors.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The action buttons that close a sheet: a solid-ink pill for the primary action and, optionally, a
 * tinted pill for the other one, stacked or side by side.
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
    val padding = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)
    if (sideBySide && secondary != null && onSecondary != null) {
        Row(modifier.then(padding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(secondary, onSecondary, Modifier.weight(1f), style = ButtonStyle.Tinted)
            PrimaryButton(primary, onPrimary, Modifier.weight(1f))
        }
    } else {
        Column(modifier.then(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(primary, onPrimary)
            if (secondary != null && onSecondary != null) {
                PrimaryButton(secondary, onSecondary, style = ButtonStyle.Tinted)
            }
        }
    }
}

/** Collects the rows of a [GlassSection] so hairlines can be drawn between them. */
internal class GlassRows {
    val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A group of rows on a translucent fill, so the glass of the sheet shows through (unlike
 * GroupedSection, whose rows sit on an opaque surface). Hairlines are inset 16dp. When
 * [entranceIndex] is 0 or more, each row arrives with a staggered entrance starting at that beat.
 */
@Composable
internal fun GlassSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    entranceIndex: Int = -1,
    rows: GlassRows.() -> Unit,
) {
    val colors = PaneTheme.colors
    val built = GlassRows().apply(rows).rows
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (header != null) {
            Text(
                header,
                style = PaneTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        if (built.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PaneShapes.large)
                    .background(colors.secondaryFill),
            ) {
                built.forEachIndexed { index, row ->
                    Box(if (entranceIndex >= 0) Modifier.entrance(entranceIndex + index) else Modifier) { row() }
                    if (index < built.lastIndex) Separator(Modifier.padding(start = 16.dp))
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

private val EdgeFade = 28.dp

/**
 * The top and bottom edges of scrolling content in a sheet: a progressive blur that dissolves
 * whatever slides under it instead of cutting it off. Each edge fades in as the content moves
 * under it ([top] and [bottom] return how many [EdgeFade]s of content are hidden there, read while
 * drawing so nothing recomposes as it scrolls) and is invisible when there is nothing more to see.
 * The scrolling content must be `hazeSource(haze)`.
 */
@Composable
internal fun BoxScope.ScrollEdges(haze: HazeState, top: () -> Float, bottom: () -> Float) {
    ProgressiveEdge(
        haze,
        top = true,
        height = EdgeFade,
        modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = top().coerceIn(0f, 1f) },
    )
    ProgressiveEdge(
        haze,
        top = false,
        height = EdgeFade,
        modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = bottom().coerceIn(0f, 1f) },
    )
}

/** A vertically scrolling column for sheets whose top and bottom edges dissolve into a progressive blur. */
@Composable
internal fun FadingColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val haze = rememberHazeState()
    val scroll = rememberScrollState()
    val fadePx = with(LocalDensity.current) { EdgeFade.toPx() }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().hazeSource(haze).verticalScroll(scroll), content = content)
        ScrollEdges(haze, top = { scroll.value / fadePx }, bottom = { (scroll.maxValue - scroll.value) / fadePx })
    }
}

/** Shape of one row in a list of separate lazy items, so they read as one rounded card. */
internal fun groupedRowShape(first: Boolean, last: Boolean): Shape {
    val r = CornerSize(20.dp)
    val none = CornerSize(0.dp)
    return when {
        first && last -> PaneShapes.large
        first -> ContinuousRoundedShape(r, r, none, none)
        last -> ContinuousRoundedShape(none, none, r, r)
        else -> RectangleShape
    }
}
