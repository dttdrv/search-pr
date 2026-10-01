package app.pane.browser.ui.browser

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
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
import app.pane.browser.ui.prompts.FlatSection
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
import app.pane.core.tabs.TabState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** One page action at the top of the menu: a familiar glyph in a hairline circle, its name under it. */
private class PageAction(val icon: ImageVector, val label: String, val haptic: Boolean = true, val onClick: () -> Unit)

/**
 * The "…" menu: a row of page actions (glyph over name, no fill), extension buttons, then everything
 * else as flat rows with hairlines between them. One sheet, no nested menus; its contents arrive
 * one after another a beat behind the card. A [locked] private page gets no page actions at all, so
 * nothing can share, copy or search it.
 */
@Composable
fun MenuSheet(
    visible: Boolean,
    tab: TabState?,
    locked: Boolean,
    onDismiss: () -> Unit,
    onFindInPage: () -> Unit,
    onNewTab: (private: Boolean) -> Unit,
) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val colors = PaneTheme.colors
    val url = if (locked) "" else tab?.url.orEmpty()
    val isPage = url.startsWith("http")
    val bookmarked by remember(url) {
        if (isPage) container.bookmarks.observeIsBookmarked(url) else flowOf(false)
    }.collectAsStateWithLifecycle(false)
    val actions by container.extensions.actions.collectAsStateWithLifecycle()

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
    val showExtensions = actions.isNotEmpty() && !locked
    val showReader = tab != null && (tab.readerable || tab.inReaderMode)

    // Beats for the staggered arrival: page actions first, then the extension row, then each list in
    // turn. entrance() caps its delay, so the tail of a long menu lands together.
    val actionBeat = 1
    val extensionBeat = actionBeat + pageActions.size
    val pageBeat = extensionBeat + if (showExtensions) 1 else 0
    val pageRows = (if (showReader) 1 else 0) + 3
    val tabBeat = pageBeat + if (isPage) pageRows else 0
    val libraryBeat = tabBeat + 2

    PaneSheet(visible = visible, onDismiss = onDismiss, maxHeightFraction = 0.9f) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            if (pageActions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    pageActions.forEachIndexed { i, action ->
                        PageActionView(action, Modifier.weight(1f).entrance(actionBeat + i))
                    }
                }
            }

            // Extension popups act on (and can show) the current page. Their icons are content, so they stay.
            if (showExtensions) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .entrance(extensionBeat)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    actions.forEach { action ->
                        Column(
                            Modifier.width(64.dp).pressScale(enabled = action.enabled, haptic = true) {
                                onDismiss()
                                container.extensions.clickAction(action.extensionId)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The extension's own icon is content and stays; with none, its name sits in the ring.
                            val icon = action.icon
                            Box(
                                Modifier.size(52.dp).border(1.dp, colors.tertiaryLabel, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (icon != null) {
                                    Image(icon, null, modifier = Modifier.size(28.dp))
                                } else {
                                    Text(
                                        action.title,
                                        style = PaneTheme.type.caption2,
                                        color = colors.label,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                }
                            }
                            if (icon != null) {
                                Text(
                                    action.title,
                                    style = PaneTheme.type.caption2,
                                    color = colors.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            if (!action.badgeText.isNullOrEmpty()) {
                                Text(
                                    action.badgeText,
                                    style = PaneTheme.type.caption2,
                                    color = colors.tertiaryLabel,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            if (isPage) {
                MenuDivider()
                FlatSection(entranceIndex = pageBeat) {
                    if (showReader && tab != null) {
                        row {
                            FlatRow(
                                "Reader View",
                                onClick = {
                                    onDismiss()
                                    container.extensions.toggleReaderMode(tab.id)
                                },
                                trailing = {
                                    PaneSwitch(checked = tab.inReaderMode, onCheckedChange = {
                                        onDismiss()
                                        container.extensions.toggleReaderMode(tab.id)
                                    })
                                },
                            )
                        }
                    }
                    row {
                        FlatRow(
                            "Desktop Site",
                            onClick = { container.browser.toggleDesktopMode() },
                            trailing = {
                                PaneSwitch(checked = tab?.desktopMode == true, onCheckedChange = { container.browser.toggleDesktopMode() })
                            },
                        )
                    }
                    row {
                        FlatRow(
                            "Add to Home",
                            onClick = {
                                onDismiss()
                                if (tab != null) HomeShortcuts.pin(context, tab.url, tab.title)
                            },
                        )
                    }
                }
            }

            if (isPage || showExtensions) MenuDivider()
            FlatSection(entranceIndex = tabBeat) {
                row { FlatRow("New Tab", onClick = { onDismiss(); onNewTab(false) }) }
                row { FlatRow("New Private Tab", onClick = { onDismiss(); onNewTab(true) }) }
            }
            MenuDivider()
            FlatSection(entranceIndex = libraryBeat) {
                row { FlatRow("Bookmarks", chevron = true, onClick = { go(Route.Bookmarks) }) }
                row { FlatRow("History", chevron = true, onClick = { go(Route.History) }) }
                row { FlatRow("Downloads", chevron = true, onClick = { go(Route.Downloads) }) }
                row { FlatRow("Extensions", chevron = true, onClick = { go(Route.Extensions) }) }
                row { FlatRow("Settings", chevron = true, onClick = { go(Route.Settings) }) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A hairline between groups of rows, with a little air either side. */
@Composable
private fun MenuDivider() {
    Separator(Modifier.padding(vertical = 4.dp))
}

/** One page action: a glyph in a hairline circle with its name under it; it presses in. */
@Composable
private fun PageActionView(action: PageAction, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier.pressScale(pressedScale = 0.94f, haptic = action.haptic, onClick = action.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).border(1.dp, colors.tertiaryLabel, CircleShape),
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
