package app.pane.browser.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GlyphSize
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.settings.BrowserSettings

/** Current settings as Compose state; every settings screen reads through this. */
@Composable
internal fun rememberSettingsState(): State<BrowserSettings> =
    LocalAppContainer.current.settings.state.collectAsStateWithLifecycle()

/** Rows are at least this tall, so they are easy to hit for everyone. */
private val RowMin = 56.dp

/**
 * The glyph that leads a navigation row on the settings home: a real line icon in ink, with
 * nothing behind it. Sub-screens have no glyphs, only chevrons and checks.
 */
@Composable
internal fun SettingsIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = PaneTheme.colors.label, modifier = modifier.size(GlyphSize))
}

/**
 * A row that opens something: title on the left, the current value on the right. Give it an [icon]
 * only on the settings home; inside a section, either every row has one or none does (and the
 * section passes [GlyphInset]).
 */
@Composable
internal fun NavRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    subtitle: String? = null,
    large: Boolean = false,
    icon: ImageVector? = null,
    titleColor: Color = PaneTheme.colors.label,
) {
    val leading: (@Composable () -> Unit)? = if (icon != null) {
        { SettingsIcon(icon) }
    } else {
        null
    }
    ListRow(
        title = title,
        modifier = Modifier.heightIn(min = if (large) 60.dp else RowMin),
        subtitle = subtitle,
        leading = leading,
        value = value,
        titleColor = titleColor,
        onClick = onClick,
    )
}

/** A row with a switch. Add a [subtitle] only when it changes what the person would choose. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ToggleRow(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.heightIn(min = RowMin),
        subtitle = subtitle,
        enabled = enabled,
    )
}

/** One choice of several; the recommended one says so in [note]. */
@Composable
internal fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit, note: String? = null) {
    CheckRow(
        title = title,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = RowMin),
        subtitle = note,
    )
}

/** A full-width action such as "Clear site data" (red when it destroys something). */
@Composable
internal fun ActionButtonRow(title: String, onClick: () -> Unit, destructive: Boolean = false) {
    ActionRow(title = title, onClick = onClick, modifier = Modifier.heightIn(min = RowMin), destructive = destructive)
}

/** A segmented control on its own row, for two or three mutually exclusive modes. */
@Composable
internal fun SegmentedRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowMargin, vertical = 8.dp),
        height = 48.dp,
    )
}

/**
 * Everything an expert may want, out of the way until asked for: one quiet row that opens in place.
 * Collapsed by default (and remembered across rotation); opening it grows the list on a spring.
 * [content] is a stack of [GroupedSection]s.
 */
@Composable
internal fun AdvancedSection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "advancedTurn")
    Column(modifier) {
        GroupedSection {
            row {
                ListRow(
                    title = "Advanced",
                    modifier = Modifier.heightIn(min = RowMin),
                    titleColor = PaneTheme.colors.secondaryLabel,
                    showChevron = false,
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        PaneIcons.ChevronDown,
                        contentDescription = if (expanded) "Hide advanced settings" else "Show advanced settings",
                        tint = PaneTheme.colors.tertiaryLabel,
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.sizeSpring) + fadeIn(Motion.fade()),
            exit = shrinkVertically(Motion.sizeSpring) + fadeOut(Motion.fade()),
        ) {
            Column { content() }
        }
    }
}

/**
 * A single-choice picker in a sheet, used where a choice doesn't warrant its own screen (per-site
 * permission values).
 */
@Composable
internal fun ChoiceSheet(
    visible: Boolean,
    title: String,
    message: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PaneTheme.colors
    PaneSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(title, onDone = onDismiss)
        if (message != null) {
            Text(
                message,
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
        GroupedSection {
            options.forEachIndexed { index, option ->
                row { ChoiceRow(option, selected = index == selectedIndex, onClick = { onSelect(index) }) }
            }
        }
    }
}
