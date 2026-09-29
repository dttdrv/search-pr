package app.pane.browser.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SegmentedControl
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.settings.BrowserSettings

/** Current settings as Compose state; every settings screen reads through this. */
@Composable
internal fun rememberSettingsState(): State<BrowserSettings> =
    LocalAppContainer.current.settings.state.collectAsStateWithLifecycle()

/** Rows are at least this tall, so they are easy to hit for everyone. */
private val RowMin = 56.dp

/**
 * Where a hairline starts in a section whose rows lead with a [SettingsIcon]: 16dp margin, the
 * 34dp tile and the 12dp gap, so separators line up under the titles rather than under the tiles.
 */
internal val IconSeparatorInset = 62.dp

/**
 * The glyph that leads a navigation row: a small neutral tile with a monochrome line icon. Always
 * `fill` behind `label` ink, never a colour, so a column of them reads as one quiet set.
 */
@Composable
internal fun SettingsIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Box(
        modifier.size(34.dp).clip(PaneShapes.small).background(colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.label, modifier = Modifier.size(20.dp))
    }
}

/**
 * A row that opens something: title on the left, the current value on the right. Give it an [icon]
 * on the settings home and on rows that head a screen of their own; inside a section, either every
 * row has one or none does (and the section passes [IconSeparatorInset]).
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

/** A segmented control filling a grouped row, for two or three mutually exclusive modes. */
@Composable
internal fun SegmentedRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    SegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(48.dp),
    )
}

/**
 * Everything an expert may want, out of the way until asked for. Collapsed by default (and
 * remembered across rotation); opening it grows the list on a spring. [content] is a stack of
 * [GroupedSection]s.
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
                    leading = { SettingsIcon(PaneIcons.Options) },
                    showChevron = false,
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        PaneIcons.ChevronDown,
                        contentDescription = if (expanded) "Hide advanced settings" else "Show advanced settings",
                        tint = PaneTheme.colors.tertiaryLabel,
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
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
        Spacer(Modifier.height(24.dp))
    }
}

/** One searchable setting: what it is called, which screen holds it, and other words for it. */
internal data class SettingEntry(
    val title: String,
    val screen: String,
    val route: Route,
    val keywords: String = "",
)

/**
 * Every setting Pane has, flat, for the search field on the settings home. Tapping a result opens
 * the screen that holds it. Keep this in step with the screens.
 */
internal val SettingsIndex: List<SettingEntry> = listOf(
    // Privacy
    SettingEntry("Tracker protection", "Privacy", Route.PrivacySettings, "standard strict block trackers ads blocked"),
    SettingEntry("Trackers blocked", "Privacy", Route.PrivacySettings, "count week statistics"),
    SettingEntry("Remember history", "Privacy", Route.PrivacySettings, "browsing history save"),
    SettingEntry("Clear data on exit", "Privacy", Route.PrivacySettings, "quit close erase automatically"),
    SettingEntry("Lock private tabs", "Privacy", Route.PrivacySettings, "biometric fingerprint face pin screen lock"),
    SettingEntry("Connections", "Privacy", Route.Connections, "servers hosts network contacts transparency"),
    SettingEntry("Cookies", "Privacy", Route.PrivacySettings, "advanced third-party isolate block"),
    SettingEntry("Secure connections", "Privacy", Route.PrivacySettings, "advanced https only first encrypted"),
    SettingEntry("Secure DNS", "Privacy", Route.PrivacySettings, "advanced doh encrypted lookups quad9 cloudflare mullvad nextdns adguard provider"),
    SettingEntry("Global Privacy Control", "Privacy", Route.PrivacySettings, "advanced gpc do not sell"),
    SettingEntry("Fingerprinting protection", "Privacy", Route.PrivacySettings, "advanced"),
    SettingEntry("Safe Browsing", "Privacy", Route.PrivacySettings, "advanced phishing malware dangerous sites warn"),
    SettingEntry("Remove link tracking", "Privacy", Route.PrivacySettings, "advanced utm fbclid query parameters strip"),
    SettingEntry("JavaScript", "Privacy", Route.PrivacySettings, "advanced scripts"),
    SettingEntry("Block screenshots", "Privacy", Route.PrivacySettings, "advanced private tabs app switcher"),
    // Search
    SettingEntry("Search engine", "Search", Route.SearchSettings, "duckduckgo google bing qwant default"),
    SettingEntry("Search suggestions", "Search", Route.SearchSettings, "autocomplete typing"),
    SettingEntry("Suggestions in private tabs", "Search", Route.SearchSettings, "advanced"),
    SettingEntry("Search shortcuts", "Search", Route.SearchSettings, "advanced keyword at wikipedia youtube"),
    // Passwords
    SettingEntry("Autofill service", "Passwords", Route.PasswordSettings, "password manager sign in logins"),
    SettingEntry("Passkeys", "Passwords", Route.PasswordSettings, "credential provider webauthn"),
    // Appearance
    SettingEntry("Text size", "Appearance", Route.AppearanceSettings, "font larger bigger smaller zoom accessibility"),
    SettingEntry("Theme", "Appearance", Route.AppearanceSettings, "dark light automatic mode"),
    SettingEntry("Glass effects", "Appearance", Route.AppearanceSettings, "liquid glass blur transparency full light off battery performance"),
    SettingEntry("Reduce motion", "Appearance", Route.AppearanceSettings, "animations accessibility"),
    SettingEntry("Haptics", "Appearance", Route.AppearanceSettings, "haptic feedback vibration"),
    SettingEntry("Request desktop sites", "Appearance", Route.AppearanceSettings, "advanced desktop mode"),
    SettingEntry("Allow zoom on every site", "Appearance", Route.AppearanceSettings, "advanced pinch"),
    // Tabs & toolbar
    SettingEntry("Hide toolbar while scrolling", "Tabs & toolbar", Route.TabsSettings, "bar address bottom"),
    SettingEntry("Website tinting", "Tabs & toolbar", Route.TabsSettings, "toolbar color page tint"),
    SettingEntry("Reopen tabs on launch", "Tabs & toolbar", Route.TabsSettings, "restore session start"),
    SettingEntry("Close tabs after", "Tabs & toolbar", Route.TabsSettings, "days old unused stale automatically"),
    SettingEntry("Show favorites", "Tabs & toolbar", Route.TabsSettings, "start page home bookmarks"),
    // Site permissions
    SettingEntry("Block pop-ups", "Site permissions", Route.SiteSettings, "windows"),
    SettingEntry("Block auto-play", "Site permissions", Route.SiteSettings, "video sound media"),
    SettingEntry("Site permissions", "Site permissions", Route.SiteSettings, "location camera microphone notifications sites"),
    SettingEntry("Reset all permissions", "Site permissions", Route.SiteSettings, "advanced"),
    // Extensions
    SettingEntry("Extensions", "Extensions", Route.Extensions, "add-ons addons installed"),
    SettingEntry("Browse add-ons", "Extensions", Route.Extensions, "store addons.mozilla.org"),
    SettingEntry("Install from file", "Extensions", Route.Extensions, "advanced xpi"),
    SettingEntry("Check for updates", "Extensions", Route.Extensions, "advanced"),
    // Clear data
    SettingEntry("Clear browsing data", "Clear data", Route.ClearData, "history cookies cache erase delete"),
    // About
    SettingEntry("Version", "About", Route.About, "build gecko engine"),
    SettingEntry("Reset settings", "About", Route.About, "defaults restore original"),
    SettingEntry("Open source licenses", "About", Route.About, "advanced"),
)

/** The glyph of the screen that holds a setting, for the rows in search results. */
internal fun settingsIconFor(route: Route): ImageVector = when (route) {
    Route.PrivacySettings -> PaneIcons.Shield
    Route.Connections -> PaneIcons.Transfer
    Route.SearchSettings -> PaneIcons.Magnifier
    Route.PasswordSettings -> PaneIcons.KeyRound
    Route.AppearanceSettings -> PaneIcons.Palette
    Route.TabsSettings -> PaneIcons.Tabs
    Route.SiteSettings -> PaneIcons.Globe
    Route.Extensions -> PaneIcons.Puzzle
    Route.ClearData -> PaneIcons.Trash
    Route.About -> PaneIcons.Info
    else -> PaneIcons.Gear
}

/** Settings whose title, screen or keywords contain every word in [query]. */
internal fun searchSettings(query: String): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return SettingsIndex.filter { entry ->
        val haystack = "${entry.title} ${entry.screen} ${entry.keywords}".lowercase()
        words.all { it in haystack }
    }
}
