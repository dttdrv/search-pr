package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.core.settings.BrowserSettings

/** How long an unused tab may sit before Pane closes it when it starts; 0 days means never. */
private val CloseAfterChoices: List<Pair<Int, String>> = listOf(
    0 to "Never",
    1 to "1 day",
    7 to "7 days",
    30 to "30 days",
)

/** The choice's label, or a plain "N days" for a value set some other way. */
private fun closeAfterLabel(days: Int): String =
    CloseAfterChoices.firstOrNull { it.first == days }?.second ?: if (days == 1) "1 day" else "$days days"

/**
 * How the toolbar behaves and what happens to tabs between visits, moved out of Appearance so
 * looks and behaviour each have their own screen. Every change applies live.
 */
@Composable
fun TabsSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.TabsSettings)
    var choosingClose by remember { mutableStateOf(false) }

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "Tabs & toolbar", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "toolbar") {
                GroupedSection(modifier = Modifier.arrive(0), header = "Toolbar") {
                    row {
                        SwitchRow(
                            title = "Hide toolbar while scrolling",
                            checked = settings.hideToolbarOnScroll,
                            onCheckedChange = { on -> update { it.copy(hideToolbarOnScroll = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Website tinting",
                            checked = settings.tintToolbarWithPage,
                            onCheckedChange = { on -> update { it.copy(tintToolbarWithPage = on) } },
                        )
                    }
                }
            }

            item(key = "tabs") {
                GroupedSection(
                    modifier = Modifier.arrive(1),
                    header = "Tabs",
                    footer = if (settings.closeTabsAfterDays > 0) "Unused tabs close when Pane opens." else null,
                ) {
                    row {
                        SwitchRow(
                            title = "Reopen tabs on launch",
                            checked = settings.restoreTabs,
                            onCheckedChange = { on -> update { it.copy(restoreTabs = on) } },
                        )
                    }
                    row {
                        NavRow(
                            title = "Close tabs after",
                            value = closeAfterLabel(settings.closeTabsAfterDays),
                            onClick = { choosingClose = true },
                        )
                    }
                }
            }

            item(key = "start") {
                GroupedSection(modifier = Modifier.arrive(2), header = "Start page") {
                    row {
                        SwitchRow(
                            title = "Show favorites",
                            checked = settings.showHomeFavorites,
                            onCheckedChange = { on -> update { it.copy(showHomeFavorites = on) } },
                        )
                    }
                }
            }
        }

        ChoiceSheet(
            visible = choosingClose,
            title = "Close tabs after",
            message = null,
            options = CloseAfterChoices.map { it.second },
            selectedIndex = CloseAfterChoices.indexOfFirst { it.first == settings.closeTabsAfterDays },
            onSelect = { index ->
                update { it.copy(closeTabsAfterDays = CloseAfterChoices[index].first) }
                choosingClose = false
            },
            onDismiss = { choosingClose = false },
        )
    }
}
