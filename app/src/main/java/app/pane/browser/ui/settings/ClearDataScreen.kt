package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.WebData
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Spacing
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.library.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pick a time range and what to erase, confirm, done. Web view data (cookies, caches) has no
 * timestamps the system WebView can filter by, so those are always cleared in full; the footer says so.
 */
@Composable
fun ClearDataScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val haptics = rememberHaptics()
    val backLabel = rememberBackLabel(Route.ClearData)

    var range by rememberSaveable { mutableStateOf(TimeRange.LastHour) }
    var history by rememberSaveable { mutableStateOf(true) }
    var cookies by rememberSaveable { mutableStateOf(true) }
    var cache by rememberSaveable { mutableStateOf(true) }
    var tabs by rememberSaveable { mutableStateOf(false) }
    var downloadList by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val chosen = listOfNotNull(
        "history".takeIf { history },
        "cookies and site data".takeIf { cookies },
        "cached files".takeIf { cache },
        "open tabs".takeIf { tabs },
        "the download list".takeIf { downloadList },
    )

    fun clearNow() {
        working = true
        container.scope.launch {
            val since = range.since(System.currentTimeMillis())
            val allTime = range == TimeRange.AllTime
            try {
                if (history) {
                    if (allTime) container.history.clear() else container.history.deleteSince(since)
                }
                if (cookies) {
                    WebData.clearCookies()
                    WebData.clearSiteData(container.app)
                    container.sitePermissions.clearAll()
                }
                if (cache) container.sessions.clearCache()
                // Cached site icons are a list of visited hosts: they go with the cache or an all-time history clear.
                if (cache || (history && allTime)) container.favicons.clear()
                if (tabs) {
                    container.browser.closeAll(false)
                    container.browser.closeAll(true)
                    container.thumbnails.clearAll()
                    // Like Safari, there is always a tab to come back to.
                    container.browser.newTab(private = false)
                }
                // "Recently Closed" on the start page is history too, and closing all tabs above just filled it.
                if (history || tabs) {
                    container.browser.clearRecentlyClosed()
                    container.snapshots.clear()
                }
                if (downloadList) {
                    if (allTime) container.downloadsRepository.clearFinished() else container.downloadsRepository.clearFinishedSince(since)
                }
                haptics.confirm()
                toasts.show("Data cleared", PaneIcons.Check)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                toasts.show("Some data couldn't be cleared. Try again.", PaneIcons.Warning)
            } finally {
                working = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "Clear data", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "range") {
                GroupedSection(modifier = Modifier.arrive(0), header = "Time range") {
                    TimeRange.entries.forEach { option ->
                        row { ChoiceRow(option.label, selected = range == option, onClick = { range = option }) }
                    }
                }
            }
            item(key = "what") {
                val engineDataAllTime = range != TimeRange.AllTime && (cookies || cache)
                GroupedSection(
                    modifier = Modifier.arrive(1),
                    header = "Clear",
                    footer = if (engineDataAllTime) "Cookies and cache are always cleared for all time." else null,
                ) {
                    row { ChoiceRow("Browsing history", selected = history, onClick = { history = !history }) }
                    row {
                        ChoiceRow(
                            "Cookies and site data",
                            selected = cookies,
                            onClick = { cookies = !cookies },
                            note = "Signs you out of most sites",
                        )
                    }
                    row { ChoiceRow("Cached files", selected = cache, onClick = { cache = !cache }) }
                }
            }
            item(key = "action") {
                PrimaryButton(
                    text = if (working) "Clearing…" else "Clear",
                    style = ButtonStyle.Destructive,
                    icon = PaneIcons.Trash,
                    enabled = chosen.isNotEmpty() && !working,
                    onClick = { confirming = true },
                    modifier = Modifier.arrive(2).padding(horizontal = Spacing.gutter, vertical = 24.dp),
                )
            }
            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(3)) {
                    GroupedSection {
                        row {
                            ChoiceRow(
                                "Open tabs",
                                selected = tabs,
                                onClick = { tabs = !tabs },
                                note = "Closes every tab, including private",
                            )
                        }
                        row { ChoiceRow("Download list", selected = downloadList, onClick = { downloadList = !downloadList }) }
                    }
                }
            }
        }

        PaneAlert(
            visible = confirming,
            title = "Clear data?",
            message = "This removes ${joinNaturally(chosen)} from ${rangePhrase(range)}. It can't be undone.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirming = false },
                AlertAction("Clear", AlertStyle.Destructive) {
                    confirming = false
                    clearNow()
                },
            ),
            onDismissRequest = { confirming = false },
        )
    }
}

private fun rangePhrase(range: TimeRange) = when (range) {
    TimeRange.LastHour -> "the last hour"
    TimeRange.Today -> "today"
    TimeRange.TodayAndYesterday -> "today and yesterday"
    TimeRange.AllTime -> "all time"
}

/** "a", "a and b", "a, b and c". */
private fun joinNaturally(parts: List<String>): String = when (parts.size) {
    0 -> "nothing"
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}
