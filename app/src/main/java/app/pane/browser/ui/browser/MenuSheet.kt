package app.pane.browser.ui.browser

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GlyphInset
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.prompts.FadingColumn
import app.pane.browser.ui.prompts.FlatRow
import app.pane.browser.ui.theme.floating
import app.pane.browser.ui.prompts.FlatSection
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
import app.pane.core.tabs.TabState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** One action in the menu: a familiar glyph on a soft circle, its name under it. */
private class PageAction(val icon: ImageVector, val label: String, val haptic: Boolean = true, val onClick: () -> Unit)

/**
 * The "…" menu: rows of glyphs with their names under them (what to do with the page, the tabs, the
 * places), and between them the page's few switches as flat rows. One sheet, no nested menus; it grows
 * out of its button and what it holds fades in with it. A [locked] private page gets no page actions
 * or switches at all, so nothing can share, copy or search it.
 */
@Composable
fun MenuSheet(
    visible: Boolean,
    origin: Rect,
    tab: TabState?,
    locked: Boolean,
    onDismiss: () -> Unit,
    onFindInPage: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: (private: Boolean) -> Unit,
) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val url = if (locked) "" else tab?.url.orEmpty()
    val isPage = url.startsWith("http")
    val bookmarked by remember(url) {
        if (isPage) container.bookmarks.observeIsBookmarked(url) else flowOf(false)
    }.collectAsStateWithLifecycle(false)

    fun go(route: Route) {
        onDismiss()
        navigator.push(route)
    }

    val pageActions = buildList<PageAction> {
        if (!isPage) return@buildList
        if (tab?.canGoForward == true) {
            add(
                PageAction(PaneIcons.Forward, "Forward") {
                    container.browser.goForward()
                    onDismiss()
                },
            )
        }
        add(
            PageAction(PaneIcons.Share, "Share") {
                onDismiss()
                val clean = if (container.settings.current.stripTrackingParams) TrackingParams.strip(url) else url
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clean)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        add(
            PageAction(if (bookmarked) PaneIcons.BookmarkFill else PaneIcons.Bookmark, if (bookmarked) "Saved" else "Bookmark", haptic = false) {
                haptics.confirm()
                scope.launch {
                    if (bookmarked) {
                        container.bookmarks.removeUrl(url)
                        toasts.show("Bookmark removed")
                    } else {
                        container.bookmarks.add(url, tab?.title.orEmpty())
                        toasts.show("Bookmarked", null, "Favorite") {
                            scope.launch {
                                container.bookmarks.all().firstOrNull { it.url == url }?.let { container.bookmarks.setFavorite(it.id, true) }
                            }
                        }
                    }
                }
            },
        )
        add(
            PageAction(PaneIcons.FindInPage, "Find") {
                onDismiss()
                onFindInPage()
            },
        )
        add(
            PageAction(PaneIcons.Link, "Copy link", haptic = false) {
                val clean = if (container.settings.current.stripTrackingParams) TrackingParams.strip(url) else url
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newRawUri("URL", clean.toUri()))
                haptics.confirm()
                onDismiss()
                toasts.show("Link copied")
            },
        )
    }

    val tabActions = listOf(
        PageAction(PaneIcons.Tabs, "Tabs") { onDismiss(); onTabs() },
        PageAction(PaneIcons.Plus, "New Tab") { onDismiss(); onNewTab(false) },
        PageAction(PaneIcons.Private, "Private") { onDismiss(); onNewTab(true) },
    )
    val places = listOf(
        PageAction(PaneIcons.Book, "Bookmarks") { go(Route.Bookmarks) },
        PageAction(PaneIcons.Clock, "History") { go(Route.History) },
        PageAction(PaneIcons.Download, "Downloads") { go(Route.Downloads) },
        PageAction(PaneIcons.Gear, "Settings") { go(Route.Settings) },
    )
    // every row of glyphs keeps the same columns, so they line up down the sheet
    val columns = maxOf(pageActions.size, tabActions.size, places.size)

    // The sheet itself is the motion: it grows out of the button and what it holds fades in as one, with no
    // second wave of rows arriving after it.
    PaneSheet(visible = visible, onDismiss = onDismiss, origin = origin, maxHeightFraction = 0.9f) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            if (pageActions.isNotEmpty()) {
                ActionRow(pageActions, columns)
                MenuDivider()
                FlatSection(separatorInset = GlyphInset) {
                    row {
                        FlatRow(
                            "Desktop Site",
                            icon = PaneIcons.Desktop,
                            onClick = { container.browser.toggleDesktopMode() },
                            trailing = {
                                PaneSwitch(checked = tab?.desktopMode == true, onCheckedChange = { container.browser.toggleDesktopMode() })
                            },
                        )
                    }
                    if (tab != null && (tab.readerable || tab.inReaderMode)) {
                        row {
                            FlatRow(
                                "Reader",
                                icon = PaneIcons.Reader,
                                onClick = { container.sessions.toggleReader(tab.id) },
                                trailing = {
                                    PaneSwitch(checked = tab.inReaderMode, onCheckedChange = { container.sessions.toggleReader(tab.id) })
                                },
                            )
                        }
                    }
                    row {
                        FlatRow(
                            "Add to Home",
                            icon = PaneIcons.Home,
                            onClick = {
                                onDismiss()
                                if (tab != null) HomeShortcuts.pin(context, tab.url, tab.title)
                            },
                        )
                    }
                }
                MenuDivider()
            }
            ActionRow(tabActions, columns)
            MenuDivider()
            ActionRow(places, columns)
        }
    }
}

/** A row of glyph actions in [columns] equal columns, from the left. */
@Composable
private fun ActionRow(actions: List<PageAction>, columns: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.gutter, top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { PageActionView(it, Modifier.weight(1f)) }
        repeat(columns - actions.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** The air between two cards of rows. */
@Composable
private fun MenuDivider() {
    Spacer(Modifier.height(Spacing.gutter))
}

/** One page action: a glyph on a soft raised circle with its name under it; it presses in. */
@Composable
private fun PageActionView(action: PageAction, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier.pressScale(pressedScale = 0.94f, haptic = action.haptic, onClick = action.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).floating(CircleShape, shadow = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, null, tint = colors.label, modifier = Modifier.size(22.dp))
        }
        Text(
            action.label,
            style = PaneTheme.type.caption,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
