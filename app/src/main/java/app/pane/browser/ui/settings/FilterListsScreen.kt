package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.FilterLists
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import java.text.NumberFormat

/**
 * The lists the ad blocker runs on: one switch each, with how many rules it holds and when it was last
 * checked. The glyph in the bar asks every enabled list for news, on any connection; without it the lists
 * refresh by themselves every few days on an unmetered one.
 */
@Composable
fun FilterListsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val settings by rememberSettingsState()
    val lists by container.filterLists.state.collectAsStateWithLifecycle()
    val backLabel = rememberBackLabel(Route.FilterLists)
    val now = System.currentTimeMillis()

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Filter lists",
            onBack = navigator::pop,
            backLabel = backLabel,
            actions = {
                ChromeButton(
                    PaneIcons.Reload,
                    "Update lists",
                    enabled = !lists.updating,
                    onClick = { container.filterLists.updateNow() },
                )
            },
        ) {
            item(key = "lists") {
                GroupedSection(
                    modifier = Modifier.arrive(0),
                    footer = when {
                        lists.updating -> "Updating…"
                        lists.failed -> "Couldn't update. Check the connection."
                        else -> null
                    },
                ) {
                    for (info in container.filterLists.catalogue) {
                        row {
                            SwitchRow(
                                title = info.name,
                                subtitle = quietValue(lists.lists[info.id], now),
                                checked = info.id in settings.enabledFilterLists,
                                onCheckedChange = { on ->
                                    container.settings.update { s ->
                                        val ids = if (on) (s.enabledFilterLists + info.id).distinct() else s.enabledFilterLists - info.id
                                        s.copy(enabledFilterLists = ids)
                                    }
                                    if (on) container.filterLists.fetchMissing()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "41,203 rules · updated 2d ago", or "Not downloaded" for a list that was never fetched. */
private fun quietValue(meta: FilterLists.Meta?, now: Long): String {
    if (meta == null || meta.checkedAt == 0L) return "Not downloaded"
    val rules = NumberFormat.getIntegerInstance().format(meta.rules)
    val age = ago(now - meta.checkedAt)
    return if (meta.rules > 0) "$rules rules · updated $age" else "Updated $age"
}

private fun ago(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
