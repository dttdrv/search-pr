package app.pane.browser.ui.library

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.SiteIcon
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

/** Where a row's text starts (16dp padding, 30dp icon, 12dp gap), so hairlines line up with it. */
private val TextInset = 58.dp

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
                // The icon cache is derived from what was visited, so it goes with the history.
                container.favicons.clear()
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
                TextButton(
                    "Clear",
                    onClick = { clearVisible = true },
                    enabled = !items.isNullOrEmpty() || query.isNotBlank(),
                    color = colors.destructive,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            },
            header = {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search history",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            },
        ) {
            val loaded = items
            when {
                loaded == null -> Unit
                loaded.isEmpty() && query.isNotBlank() -> item(key = "no-results") {
                    EmptyState(
                        title = "No results",
                        message = "Nothing in your history matches “${query.trim()}”.",
                        modifier = Modifier.animateItem(),
                    )
                }
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = if (settings.rememberHistory) "No history" else "History is off",
                        message = if (settings.rememberHistory) {
                            "Pages you visit appear here."
                        } else {
                            "Turn on Remember history in Privacy to keep a list."
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> {
                    // Rows arrive in one cascade down the whole list, not once per day section.
                    var arrivals = 0
                    groups.forEach { group ->
                        val base = arrivals
                        arrivals += group.items.size
                        item(key = "section:${group.section.daysAgo}") {
                            SectionTitle(group.section.title(), Modifier.animateItem())
                        }
                        itemsIndexed(group.items, key = { _, h -> "h:${h.url}" }) { index, entry ->
                            val first = index == 0
                            SwipeToDelete(
                                onDelete = { delete(entry) },
                                modifier = Modifier
                                    .animateItem()
                                    .arrive(base + index)
                                    .groupedItem(first, index == group.items.lastIndex, colors.surface),
                            ) {
                                Column {
                                    if (!first) Separator(Modifier.padding(start = TextInset))
                                    LibraryRow(
                                        title = entry.title.ifBlank { UrlDisplay.toolbarText(entry.url).ifEmpty { entry.url } },
                                        subtitle = UrlDisplay.toolbarText(entry.url).ifEmpty { entry.url },
                                        leading = { SiteIcon(entry.url, 30.dp) },
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
            }
            if (!loaded.isNullOrEmpty() && !settings.rememberHistory) {
                item(key = "paused") {
                    SectionFooter("History is off. New pages aren't added.", Modifier.animateItem())
                }
            }
        }

        val target = menuTarget
        ActionSheet(
            visible = menuVisible,
            title = target?.title?.ifBlank { null } ?: target?.url.orEmpty(),
            subtitle = target?.let { UrlDisplay.toolbarText(it.url) },
            onDismiss = { menuVisible = false },
            actions = if (target == null) emptyList() else listOf(
                SheetAction("Open in new tab") { open(target, newTab = true, private = false) },
                SheetAction("Open in private tab") { open(target, newTab = true, private = true) },
                SheetAction("Copy link") { copyToClipboard(context, target.url, toasts) },
                SheetAction("Share…") { shareLink(context, target.url, target.title) },
                SheetAction("Delete from history", destructive = true) { delete(target) },
            ),
        )

        PaneSheet(visible = clearVisible, onDismiss = { clearVisible = false }) {
            SheetHeader("Clear history", onDone = { clearVisible = false }, doneLabel = "Cancel")
            GroupedSection {
                TimeRange.entries.forEach { range ->
                    row { ActionRow(range.label, onClick = { clear(range) }, modifier = Modifier.heightIn(min = 56.dp), destructive = true) }
                }
            }
            GroupedSection {
                row {
                    ListRow(
                        title = "Clear cookies and more…",
                        modifier = Modifier.heightIn(min = 56.dp),
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
