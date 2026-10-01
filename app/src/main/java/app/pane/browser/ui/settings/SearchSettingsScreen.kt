package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.EngineIcon
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.search.SearchEngines

/** Engine logos are 28dp; with the 20dp margin and 16dp gap, titles (and hairlines) start at 64dp. */
private val ENGINE_ICON = 28.dp
private val EngineSeparatorInset = 64.dp

/**
 * The default engine and suggestions. Engines that don't build a profile of you say "Private" in
 * plain text; the one to pick if unsure says "Recommended". Suggestions in private tabs and the
 * `@keyword` shortcuts are under Advanced.
 */
@Composable
fun SearchSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val colors = PaneTheme.colors
    val backLabel = rememberBackLabel(Route.SearchSettings)
    val current = SearchEngines.byId(settings.searchEngineId)

    LargeTitleScaffold(title = "Search", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "engines") {
            GroupedSection(modifier = Modifier.arrive(0), header = "Search engine", separatorInset = EngineSeparatorInset) {
                SearchEngines.defaults.forEach { engine ->
                    row {
                        ListRow(
                            title = engine.name,
                            modifier = Modifier.heightIn(min = 60.dp),
                            leading = { EngineIcon(engine.id, ENGINE_ICON) },
                            value = when {
                                engine.id == SearchEngines.DuckDuckGo.id -> "Recommended"
                                engine.privacyFocused -> "Private"
                                else -> null
                            },
                            showChevron = false,
                            onClick = { container.settings.update { it.copy(searchEngineId = engine.id) } },
                        ) {
                            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                                if (engine.id == current.id) {
                                    Icon(PaneIcons.Check, contentDescription = "Selected", tint = colors.label, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "suggestions") {
            GroupedSection(modifier = Modifier.arrive(1)) {
                row {
                    SwitchRow(
                        title = "Search suggestions",
                        checked = settings.searchSuggestions,
                        onCheckedChange = { on -> container.settings.update { it.copy(searchSuggestions = on) } },
                    )
                }
            }
        }
        item(key = "advanced") {
            AdvancedSection(modifier = Modifier.arrive(2)) {
                GroupedSection {
                    row {
                        SwitchRow(
                            title = "Suggestions in private tabs",
                            checked = settings.searchSuggestions && settings.searchSuggestionsInPrivate,
                            enabled = settings.searchSuggestions,
                            onCheckedChange = { on -> container.settings.update { it.copy(searchSuggestionsInPrivate = on) } },
                        )
                    }
                }
                GroupedSection(header = "Shortcuts", footer = "Type @ and a keyword, like @w, to search there once.") {
                    SearchEngines.all.forEach { engine ->
                        row {
                            ListRow(
                                title = engine.name,
                                modifier = Modifier.heightIn(min = 56.dp),
                                value = "@${engine.keyword}",
                                showChevron = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
