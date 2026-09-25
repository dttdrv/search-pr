package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.downloads.await
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.library.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mozilla.geckoview.StorageController

/**
 * Pick a time range and what to erase, confirm, done. Engine data (cookies, caches) has no
 * timestamps Gecko can filter by, so those are always cleared in full; the footer says so.
 */
@Composable
fun ClearDataScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val haptics = rememberHaptics()
    val colors = PaneTheme.colors
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
                var flags = 0L
                if (cookies) {
                    flags = flags or StorageController.ClearFlags.COOKIES or
                        StorageController.ClearFlags.DOM_STORAGES or
                        StorageController.ClearFlags.AUTH_SESSIONS
                }
                if (cache) flags = flags or StorageController.ClearFlags.ALL_CACHES
                if (flags != 0L) container.runtime.storageController.clearData(flags).await()
                if (tabs) {
                    container.browser.closeAll(false)
                    container.browser.closeAll(true)
                    container.thumbnails.clearAll()
                    // Like Safari, there is always a tab to come back to.
                    container.browser.newTab(private = false)
                }
                if (downloadList) {
                    if (allTime) container.downloadsRepository.clearFinished() else container.downloadsRepository.clearFinishedSince(since)
                }
                haptics.confirm()
                toasts.show("Browsing data cleared", PaneIcons.Check)
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
        // "Clear Browsing Data" is too long for a large title on narrow phones.
        LargeTitleScaffold(title = "Clear Data", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "range") {
                GroupedSection(header = "Time Range") {
                    TimeRange.entries.forEach { option ->
                        row { CheckRow(option.label, selected = range == option, onClick = { range = option }) }
                    }
                }
            }
            item(key = "what") {
                val engineDataAllTime = range != TimeRange.AllTime && (cookies || cache)
                GroupedSection(
                    header = "Data",
                    footer = if (engineDataAllTime) "Cookies, site data and cached files are always cleared for all time." else null,
                    separatorInset = 57.dp,
                ) {
                    row {
                        CheckRow(
                            title = "Browsing History",
                            subtitle = "Pages you visited",
                            selected = history,
                            onClick = { history = !history },
                            leading = { IconTile(PaneIcons.Clock, TileColors.Blue) },
                        )
                    }
                    row {
                        CheckRow(
                            title = "Cookies & Site Data",
                            subtitle = "Signs you out of most sites",
                            selected = cookies,
                            onClick = { cookies = !cookies },
                            leading = { IconTile(PaneIcons.Globe, TileColors.Indigo) },
                        )
                    }
                    row {
                        CheckRow(
                            title = "Cached Files",
                            subtitle = "Frees space; some pages load slower at first",
                            selected = cache,
                            onClick = { cache = !cache },
                            leading = { IconTile(PaneIcons.Download, TileColors.Gray) },
                        )
                    }
                    row {
                        CheckRow(
                            title = "Open Tabs",
                            subtitle = "Closes every tab, including private ones",
                            selected = tabs,
                            onClick = { tabs = !tabs },
                            leading = { IconTile(PaneIcons.Tabs, TileColors.Teal) },
                        )
                    }
                    row {
                        CheckRow(
                            title = "Download List",
                            subtitle = "Downloaded files stay on your device",
                            selected = downloadList,
                            onClick = { downloadList = !downloadList },
                            leading = { IconTile(PaneIcons.Download, TileColors.Green) },
                        )
                    }
                }
            }
            item(key = "action") {
                val enabled = chosen.isNotEmpty() && !working
                GroupedSection {
                    row {
                        ListRow(
                            title = "Clear Browsing Data",
                            titleColor = if (enabled) colors.destructive else colors.tertiaryLabel,
                            showChevron = false,
                            onClick = if (enabled) ({ confirming = true }) else null,
                        ) {
                            if (working) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.secondaryLabel, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        PaneAlert(
            visible = confirming,
            title = "Clear Browsing Data?",
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
