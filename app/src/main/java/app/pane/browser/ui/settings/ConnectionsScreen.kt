package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.core.search.SearchEngines
import app.pane.core.settings.DnsOverHttps

/**
 * Who Pane talks to, and why, in plain words. Each service that has a setting has its switch here
 * too, so seeing a connection and turning it off are the same gesture.
 */
@Composable
fun ConnectionsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.Connections)
    val engine = SearchEngines.byId(settings.searchEngineId)
    val suggestHost = engine.suggestTemplate?.let(::hostOf)

    LargeTitleScaffold(title = "Connections", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "hosts") {
            GroupedSection(
                modifier = Modifier.arrive(0),
                header = "Pane connects to",
                footer = "Pages you open connect to their own servers.",
            ) {
                row {
                    NavRow(
                        title = hostOf(engine.searchTemplate),
                        subtitle = "Searches, when you press Enter",
                        onClick = { navigator.push(Route.SearchSettings) },
                    )
                }
                if (suggestHost != null) {
                    row {
                        SwitchRow(
                            title = suggestHost,
                            subtitle = "Suggestions as you type",
                            checked = settings.searchSuggestions,
                            onCheckedChange = { on -> container.settings.update { it.copy(searchSuggestions = on) } },
                        )
                    }
                }
                row {
                    SwitchRow(
                        title = hostOf(settings.dohProvider.uri),
                        subtitle = "Encrypted site lookups",
                        checked = settings.dnsOverHttps != DnsOverHttps.Off,
                        onCheckedChange = { on ->
                            container.settings.update { it.copy(dnsOverHttps = if (on) DnsOverHttps.Default else DnsOverHttps.Off) }
                        },
                    )
                }
                row {
                    ListRow(
                        title = "addons.mozilla.org",
                        subtitle = "Extensions and their updates",
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
                row {
                    SwitchRow(
                        title = "Safe Browsing",
                        subtitle = "Checks for dangerous sites",
                        checked = settings.safeBrowsing,
                        onCheckedChange = { on -> container.settings.update { it.copy(safeBrowsing = on) } },
                    )
                }
            }
        }
    }
}

/** `https://dns.quad9.net/dns-query` → `dns.quad9.net`. */
private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')
