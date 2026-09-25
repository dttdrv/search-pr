package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.LetterTile
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.search.SearchEngine
import app.pane.core.search.SearchEngines

/**
 * Default engine, suggestions and `@keyword` shortcuts. Private engines are marked, since what an
 * engine does with queries matters more than anything else on this screen.
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
            GroupedSection(
                header = "Search Engine",
                footer = "Engines marked Private don't keep a profile of you built from your searches.",
                separatorInset = 57.dp,
            ) {
                SearchEngines.defaults.forEach { engine ->
                    row {
                        ListRow(
                            title = engine.name,
                            leading = { EngineTile(engine) },
                            showChevron = false,
                            onClick = { container.settings.update { it.copy(searchEngineId = engine.id) } },
                        ) {
                            if (engine.privacyFocused) Tag("Private", colors.positive)
                            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                                if (engine.id == current.id) {
                                    Icon(PaneIcons.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "suggestions") {
            GroupedSection(
                header = "Suggestions",
                footer = "As you type, ${current.name} suggests searches. Requests carry no cookies. " +
                    "In private tabs suggestions stay off unless you allow them.",
            ) {
                row {
                    ToggleRow(
                        title = "Search Suggestions",
                        checked = settings.searchSuggestions,
                        onCheckedChange = { on -> container.settings.update { it.copy(searchSuggestions = on) } },
                    )
                }
                row {
                    ToggleRow(
                        title = "Suggestions in Private Tabs",
                        checked = settings.searchSuggestions && settings.searchSuggestionsInPrivate,
                        enabled = settings.searchSuggestions,
                        onCheckedChange = { on -> container.settings.update { it.copy(searchSuggestionsInPrivate = on) } },
                    )
                }
            }
        }
        item(key = "shortcuts") {
            GroupedSection(
                header = "Search Shortcuts",
                footer = "Start with @ and a keyword to search somewhere else once. " +
                    "For example, “@w lighthouse” searches Wikipedia and “@yt lofi” searches YouTube.",
                separatorInset = 57.dp,
            ) {
                SearchEngines.all.forEach { engine ->
                    row {
                        ListRow(
                            title = engine.name,
                            leading = { EngineTile(engine) },
                            value = "@${engine.keyword}",
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineTile(engine: SearchEngine) {
    LetterTile(letter = engine.name.take(1).uppercase(), colorKey = engine.id)
}
