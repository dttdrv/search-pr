package app.pane.browser.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.data.Bookmark
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.url.InputAction
import app.pane.core.url.UrlDisplay
import app.pane.core.url.UrlInput
import kotlinx.coroutines.launch

/**
 * Saved pages. Favorites are the ones shown on the start page; everything else about a bookmark
 * lives behind a long-press.
 */
@Composable
fun BookmarksScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val colors = PaneTheme.colors
    val backLabel = rememberBackLabel(Route.Bookmarks)

    val bookmarks by remember { container.bookmarks.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(bookmarks, query) {
        val all = bookmarks.orEmpty()
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
    }

    var menuTarget by remember { mutableStateOf<Bookmark?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Bookmark?>(null) }
    var editVisible by remember { mutableStateOf(false) }

    fun open(bookmark: Bookmark, newTab: Boolean = false, private: Boolean = container.browser.isPrivate) {
        container.browser.open(bookmark.url, newTab = newTab, private = private)
        navigator.closeAll()
    }

    fun delete(bookmark: Bookmark) {
        container.scope.launch {
            container.bookmarks.remove(bookmark.id)
            toasts.show("Bookmark deleted", PaneIcons.Trash, actionLabel = "Undo") {
                container.scope.launch { container.bookmarks.restore(bookmark) }
            }
        }
    }

    fun setFavorite(bookmark: Bookmark, favorite: Boolean) {
        container.scope.launch { container.bookmarks.setFavorite(bookmark.id, favorite) }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Bookmarks",
            onBack = navigator::pop,
            backLabel = backLabel,
            header = {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search bookmarks",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            },
        ) {
            val loaded = bookmarks
            when {
                loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "No bookmarks",
                        message = "Pages you bookmark appear here.",
                        modifier = Modifier.animateItem(),
                    )
                }
                shown.isEmpty() -> item(key = "no-results") {
                    EmptyState(
                        title = "No results",
                        message = "No bookmarks match “${query.trim()}”.",
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> {
                    item(key = "top") { Spacer(Modifier.height(8.dp)) }
                    itemsIndexed(shown, key = { _, b -> b.id }) { index, bookmark ->
                        SwipeToDelete(
                            onDelete = { delete(bookmark) },
                            modifier = Modifier.animateItem().arrive(index),
                        ) {
                            Column {
                                if (index > 0) RowSeparator(SiteRowInset)
                                LibraryRow(
                                    title = bookmark.title.ifBlank { bookmark.url },
                                    subtitle = UrlDisplay.toolbarText(bookmark.url).ifEmpty { bookmark.url },
                                    leading = { SiteIcon(bookmark.url, 28.dp) },
                                    onClick = { open(bookmark) },
                                    onLongClick = {
                                        menuTarget = bookmark
                                        menuVisible = true
                                    },
                                    trailing = {
                                        if (bookmark.favorite) {
                                            Icon(
                                                PaneIcons.StarFill,
                                                contentDescription = "Favorite",
                                                tint = colors.label,
                                                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item(key = "footer") {
                        val favorites = shown.count { it.favorite }
                        SectionFooter(
                            if (favorites > 0) "${shown.size} bookmarks · $favorites favorites" else "${shown.size} bookmarks",
                            Modifier.animateItem(),
                        )
                    }
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
                SheetAction(if (target.favorite) "Remove from favorites" else "Add to favorites") {
                    setFavorite(target, !target.favorite)
                },
                SheetAction("Edit…") {
                    editTarget = target
                    editVisible = true
                },
                SheetAction("Copy link") { copyToClipboard(context, target.url, toasts) },
                SheetAction("Share…") { shareLink(context, target.url, target.title) },
                SheetAction("Delete", destructive = true) { delete(target) },
            ),
        )

        EditBookmarkSheet(
            visible = editVisible,
            bookmark = editTarget,
            onDismiss = { editVisible = false },
            onSave = { bookmark, title, url, favorite ->
                container.scope.launch {
                    val saved = runCatching { container.bookmarks.update(bookmark.id, title, url) }.isSuccess
                    if (saved) {
                        if (favorite != bookmark.favorite) container.bookmarks.setFavorite(bookmark.id, favorite)
                        editVisible = false
                    } else {
                        toasts.show("You already have a bookmark for this address", PaneIcons.Warning)
                    }
                }
            },
            onDelete = { bookmark ->
                editVisible = false
                delete(bookmark)
            },
        )
    }
}

@Composable
private fun EditBookmarkSheet(
    visible: Boolean,
    bookmark: Bookmark?,
    onDismiss: () -> Unit,
    onSave: (bookmark: Bookmark, title: String, url: String, favorite: Boolean) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    val toasts = LocalToasts.current
    var title by remember(bookmark) { mutableStateOf(bookmark?.title.orEmpty()) }
    var url by remember(bookmark) { mutableStateOf(bookmark?.url.orEmpty()) }
    var favorite by remember(bookmark) { mutableStateOf(bookmark?.favorite ?: false) }

    PaneSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = "Edit bookmark",
            onDone = done@{
                val b = bookmark ?: return@done
                val normalized = when (val action = UrlInput.classify(url)) {
                    is InputAction.Navigate -> action.url
                    else -> null
                }
                if (normalized == null) {
                    toasts.show("Enter a valid web address", PaneIcons.Warning)
                } else {
                    onSave(b, title.trim().ifEmpty { normalized }, normalized, favorite)
                }
            },
        )
        GroupedSection {
            row { FieldRow(value = title, onValueChange = { title = it }, placeholder = "Title") }
            row { FieldRow(value = url, onValueChange = { url = it }, placeholder = "Address", keyboardType = KeyboardType.Uri) }
        }
        GroupedSection {
            row {
                ToggleRow(
                    title = "Favorite",
                    subtitle = "Shown on your start page",
                    checked = favorite,
                    onCheckedChange = { favorite = it },
                )
            }
        }
        GroupedSection {
            row { ActionRow("Delete bookmark", onClick = { bookmark?.let(onDelete) }, destructive = true) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
