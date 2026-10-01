package app.pane.browser.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.RowMargin
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Page text sizes offered by the slider: 80% to 200% in 10% steps. */
private val TextScales: List<Float> = (8..20).map { it / 10f }

/**
 * Text size first, a percentage and a slider, then theme and the two everyday switches. What
 * the toolbar and tabs do lives in Tabs & toolbar; the rest sits under Advanced. Every change
 * applies live.
 */
@Composable
fun AppearanceSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.AppearanceSettings)

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    LargeTitleScaffold(title = "Appearance", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "text") {
            val percent = (settings.textScale * 100).roundToInt()
            GroupedSection(modifier = Modifier.arrive(0), header = "Text size") {
                row {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                        Text("$percent%", style = PaneTheme.type.title1, color = PaneTheme.colors.label, modifier = Modifier.padding(horizontal = RowMargin))
                        Text(
                            "The quick brown fox",
                            style = PaneTheme.type.body.copy(fontSize = (17f * settings.textScale).sp, lineHeight = (23f * settings.textScale).sp),
                            color = PaneTheme.colors.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(start = RowMargin, end = RowMargin, top = 16.dp, bottom = 4.dp),
                        )
                        TextSizeSlider(
                            value = settings.textScale,
                            onValueChange = { scale -> update { it.copy(textScale = scale) } },
                        )
                    }
                }
            }
        }

        item(key = "theme") {
            GroupedSection(modifier = Modifier.arrive(1), header = "Theme") {
                row {
                    SegmentedRow(
                        options = listOf("Automatic", "Light", "Dark"),
                        selectedIndex = settings.theme.ordinal,
                        onSelect = { i -> update { it.copy(theme = ThemeMode.entries[i]) } },
                    )
                }
                row {
                    // Pages are only darkened in a dark app, so the switch waits for one.
                    val dark = settings.theme == ThemeMode.Dark || (settings.theme == ThemeMode.System && isSystemInDarkTheme())
                    SwitchRow(
                        title = "Dark pages",
                        subtitle = if (dark) null else "Applies in dark theme",
                        checked = settings.darkPages,
                        enabled = dark,
                        onCheckedChange = { on -> update { it.copy(darkPages = on) } },
                    )
                }
            }
        }

        item(key = "basics") {
            GroupedSection(modifier = Modifier.arrive(3)) {
                row {
                    SwitchRow(
                        title = "Reduce motion",
                        checked = settings.reduceMotion,
                        onCheckedChange = { on -> update { it.copy(reduceMotion = on) } },
                    )
                }
                row {
                    SwitchRow(
                        title = "Haptics",
                        checked = settings.haptics,
                        onCheckedChange = { on -> update { it.copy(haptics = on) } },
                    )
                }
            }
        }

        item(key = "advanced") {
            AdvancedSection(modifier = Modifier.arrive(4)) {
                GroupedSection(header = "Websites") {
                    row {
                        SwitchRow(
                            title = "Request desktop sites",
                            checked = settings.desktopModeByDefault,
                            onCheckedChange = { on -> update { it.copy(desktopModeByDefault = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Allow zoom on every site",
                            checked = settings.forceZoom,
                            onCheckedChange = { on -> update { it.copy(forceZoom = on) } },
                        )
                    }
                }
            }
        }
    }
}

/** small "A", Material's stepped slider, large "A". */
@Composable
private fun TextSizeSlider(value: Float, onValueChange: (Float) -> Unit) {
    val colors = PaneTheme.colors
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = RowMargin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("A", style = PaneTheme.type.body, color = colors.label)
        Slider(
            value = value,
            onValueChange = { position ->
                val scale = TextScales.minBy { abs(it - position) }
                if (scale != value) {
                    haptics.tick()
                    onValueChange(scale)
                }
            },
            valueRange = TextScales.first()..TextScales.last(),
            steps = TextScales.size - 2,
            colors = SliderDefaults.colors(inactiveTrackColor = colors.fill, activeTickColor = colors.fill),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Page text size"
                    stateDescription = "${(value * 100).roundToInt()}%"
                },
        )
        Text("A", style = PaneTheme.type.largeTitle, color = colors.label)
    }
}
