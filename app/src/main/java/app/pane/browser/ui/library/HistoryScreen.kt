package app.pane.browser.ui.library

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.data.HistoryItem
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.library.DaySection
import app.pane.core.library.HistoryGrouping
import app.pane.core.library.TimeRange
import app.pane.core.url.UrlDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HISTORY_LIMIT = 1000

/**
 * Visited pages grouped by day (Today, Yesterday, weekdays, Earlier), newest first. Search filters
 * the whole history, not just what is loaded, and the list stays live while rows are deleted.
 */
@Composable
fun HistoryScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    val backLabel = rememberBackLabel(Route.History)
    val settings by container.settings.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<HistoryItem>?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        // Debounce typing; the full history is searched in SQLite.
        if (q.isNotEmpty()) delay(150)
        val source = if (q.isEmpty()) container.history.observeRecent(HISTORY_LIMIT) else container.history.observeSearch(q, HISTORY_LIMIT)
        source.collect { items = it }
    }
    val groups = remember(items) { HistoryGrouping.group(items.orEmpty(), System.currentTimeMillis()) { it.lastVisited } }

    var menuTarget by remember { mutableStateOf<HistoryItem?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var clearVisible by remember { mutableStateOf(false) }

    fun open(item: HistoryItem, newTab: Boolean = false, private: Boolean = container.browser.isPrivate) {
        container.browser.open(item.url, newTab = newTab, private = private)
        navigator.closeAll()
    }

    fun delete(item: HistoryItem) {
        container.scope.launch { container.history.delete(item.url) }
    }

    fun clear(range: TimeRange) {
        clearVisible = false
        container.scope.launch {
            if (range == TimeRange.AllTime) {
                container.history.clear()
            } else {
                container.history.deleteSince(range.since(System.currentTimeMillis()))
            }
            toasts.show("History cleared", PaneIcons.Check)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "History",
            onBack = navigator::pop,
            backLabel = backLabel,
            actions = {
                TextButton("Clear", onClick = { clearVisible = true }, enabled = !items.isNullOrEmpty() || query.isNotBlank())
            },
            header = {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search History",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            },
        ) {
            val loaded = items
            when {
                loaded == null -> Unit
                loaded.isEmpty() && query.isNotBlank() -> item(key = "no-results") {
                    EmptyState(
                        icon = PaneIcons.Search,
                        title = "No Results",
                        message = "Nothing in your history matches “${query.trim()}”.",
                        modifier = Modifier.animateItem(),
                    )
                }
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = PaneIcons.Clock,
                        title = if (settings.rememberHistory) "No History" else "History Is Off",
                        message = if (settings.rememberHistory) {
                            "Pages you visit appear here. Private tabs are never remembered."
                        } else {
                            "Turn on Remember History in Privacy & Security to keep a list of pages you visit."
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> groups.forEach { group ->
                    item(key = "section:${group.section.daysAgo}") {
                        SectionTitle(group.section.title(), Modifier.animateItem())
                    }
                    itemsIndexed(group.items, key = { _, h -> "h:${h.url}" }) { index, entry ->
                        val first = index == 0
                        SwipeToDelete(
                            onDelete = { delete(entry) },
                            modifier = Modifier.animateItem().groupedItem(first, index == group.items.lastIndex, colors.surface),
                        ) {
                            Column {
                                if (!first) RowSeparator()
                                LibraryRow(
                                    title = entry.title.ifBlank { UrlDisplay.toolbarText(entry.url).ifEmpty { entry.url } },
                                    subtitle = UrlDisplay.toolbarText(entry.url).ifEmpty { entry.url },
                                    leading = { SiteTile(entry.url, entry.title) },
                                    onClick = { open(entry) },
                                    onLongClick = {
                                        menuTarget = entry
                                        menuVisible = true
                                    },
                                    trailing = {
                                        Text(
                                            visitTime(context, entry.lastVisited, group.section),
                                            style = PaneTheme.type.footnote,
                                            color = colors.tertiaryLabel,
                                            maxLines = 1,
                                            modifier = Modifier.padding(end = 6.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (!loaded.isNullOrEmpty() && !settings.rememberHistory) {
                item(key = "paused") {
                    SectionFooter("History is paused. New pages won't be added until you turn Remember History back on.", Modifier.animateItem())
                }
            }
        }

        val target = menuTarget
        ActionSheet(
            visible = menuVisible,
            title = target?.title?.ifBlank { null } ?: target?.url.orEmpty(),
            subtitle = target?.let { UrlDisplay.toolbarText(it.url) },
            leading = if (target != null) {
                { SiteTile(target.url, target.title, size = 40.dp) }
            } else {
                null
            },
            onDismiss = { menuVisible = false },
            actions = if (target == null) emptyList() else listOf(
                SheetAction("Open in New Tab", PaneIcons.Plus) { open(target, newTab = true, private = false) },
                SheetAction("Open in Private Tab", PaneIcons.Private) { open(target, newTab = true, private = true) },
                SheetAction("Copy Link", PaneIcons.Copy) { copyToClipboard(context, target.url, toasts) },
                SheetAction("Share…", PaneIcons.Share) { shareLink(context, target.url, target.title) },
                SheetAction("Delete from History", PaneIcons.Trash, destructive = true) { delete(target) },
            ),
        )

        PaneSheet(visible = clearVisible, onDismiss = { clearVisible = false }) {
            SheetHeader("Clear History", onDone = { clearVisible = false }, doneLabel = "Cancel")
            GroupedSection(
                footer = "Removes pages from your history. To also clear cookies and cached files, use Clear Browsing Data.",
            ) {
                TimeRange.entries.forEach { range ->
                    row { ActionRow(range.label, onClick = { clear(range) }, destructive = range == TimeRange.AllTime) }
                }
            }
            GroupedSection {
                row {
                    ListRow(
                        title = "Clear Browsing Data…",
                        onClick = {
                            clearVisible = false
                            navigator.push(Route.ClearData)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Time of day for recent sections, the date for "Earlier". */
private fun visitTime(context: Context, millis: Long, section: DaySection): String {
    val flags = if (section == DaySection.Earlier) {
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    } else {
        DateUtils.FORMAT_SHOW_TIME
    }
    return DateUtils.formatDateTime(context, millis, flags)
}
