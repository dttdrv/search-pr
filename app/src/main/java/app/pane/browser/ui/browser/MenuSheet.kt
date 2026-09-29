package app.pane.browser.ui.browser

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import app.pane.browser.ui.icons.PaneIcons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.prompts.FadingColumn
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
import app.pane.core.tabs.TabState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** One page action at the top of the menu: a familiar glyph over its name. */
private class QuickChip(val icon: ImageVector, val label: String, val haptic: Boolean = true, val onClick: () -> Unit)

/**
 * The "…" menu: page actions as a row of glyph tiles up top, extension buttons, then everything
 * else as a few plain lists on translucent fills so the glass shows through. One sheet, no nested
 * menus; its contents arrive one after another a beat behind the card. A [locked] private page gets
 * no page actions at all, so nothing can share, copy or search it.
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

    val chips = buildList<QuickChip> {
        if (!isPage) return@buildList
        if (tab?.canGoForward == true) {
            add(
                QuickChip(PaneIcons.Forward, "Forward") {
                    container.browser.goForward()
                    onDismiss()
                },
            )
        }
        add(
            QuickChip(PaneIcons.Share, "Share") {
                onDismiss()
                val clean = if (container.settings.current.stripTrackingParams) TrackingParams.strip(url) else url
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clean)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        add(
            QuickChip(if (bookmarked) PaneIcons.BookmarkFill else PaneIcons.Bookmark, if (bookmarked) "Saved" else "Bookmark", haptic = false) {
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
            QuickChip(PaneIcons.FindInPage, "Find") {
                onDismiss()
                onFindInPage()
            },
        )
        add(
            QuickChip(PaneIcons.Link, "Copy link", haptic = false) {
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

    // Beats for the staggered arrival: chips first, then the extension row, then each list in turn.
    // entrance() caps its delay, so the tail of a long menu lands together.
    val chipBeat = 1
    val extensionBeat = chipBeat + chips.size
    val pageBeat = extensionBeat + if (showExtensions) 1 else 0
    val pageRows = (if (showReader) 1 else 0) + 2
    val tabBeat = pageBeat + if (isPage) pageRows else 0
    val libraryBeat = tabBeat + 2

    PaneSheet(visible = visible, onDismiss = onDismiss, maxHeightFraction = 0.9f) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            if (chips.isNotEmpty()) {
                // Up to four pills share a row; a fifth (Forward) would squeeze the labels, so it wraps 3 + 2.
                val perRow = if (chips.size > 4) 3 else 4
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.chunked(perRow).forEachIndexed { rowIndex, rowChips ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowChips.forEachIndexed { i, chip ->
                                QuickChipView(chip, Modifier.weight(1f).entrance(chipBeat + rowIndex * perRow + i))
                            }
                        }
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            // The extension's own icon is content and stays; with none, its name fills the tile.
                            val icon = action.icon
                            Box(
                                Modifier.size(52.dp).clip(PaneShapes.medium).background(colors.secondaryFill),
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
                                        modifier = Modifier.padding(horizontal = 4.dp),
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
                MenuSection(firstBeat = pageBeat) {
                    if (showReader && tab != null) {
                        row {
                            ListRow(
                                "Reader View",
                                showChevron = false,
                                onClick = {
                                    onDismiss()
                                    container.extensions.toggleReaderMode(tab.id)
                                },
                            ) {
                                PaneSwitch(checked = tab.inReaderMode, onCheckedChange = {
                                    onDismiss()
                                    container.extensions.toggleReaderMode(tab.id)
                                })
                            }
                        }
                    }
                    row {
                        ListRow(
                            "Desktop Site",
                            showChevron = false,
                            onClick = { container.browser.toggleDesktopMode() },
                        ) {
                            PaneSwitch(checked = tab?.desktopMode == true, onCheckedChange = { container.browser.toggleDesktopMode() })
                        }
                    }
                    row {
                        ListRow(
                            "Add to Home",
                            showChevron = false,
                            onClick = {
                                onDismiss()
                                if (tab != null) HomeShortcuts.pin(context, tab.url, tab.title)
                            },
                        )
                    }
                }
            }

            MenuSection(firstBeat = tabBeat) {
                row { ListRow("New Tab", showChevron = false, onClick = { onDismiss(); onNewTab(false) }) }
                row { ListRow("New Private Tab", showChevron = false, onClick = { onDismiss(); onNewTab(true) }) }
            }
            MenuSection(firstBeat = libraryBeat) {
                row { ListRow("Bookmarks", onClick = { go(Route.Bookmarks) }) }
                row { ListRow("History", onClick = { go(Route.History) }) }
                row { ListRow("Downloads", onClick = { go(Route.Downloads) }) }
                row { ListRow("Extensions", onClick = { go(Route.Extensions) }) }
                row { ListRow("Settings", onClick = { go(Route.Settings) }) }
            }
            Box(Modifier.height(10.dp))
        }
    }
}

/** One page action: equal width with its neighbours, a soft fill, a glyph over its name; it presses in. */
@Composable
private fun QuickChipView(chip: QuickChip, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(
        modifier
            .height(72.dp)
            .pressScale(pressedScale = 0.94f, haptic = chip.haptic, onClick = chip.onClick)
            .clip(PaneShapes.large)
            .background(colors.secondaryFill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(chip.icon, null, tint = colors.label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            chip.label,
            style = PaneTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Collects the rows of a [MenuSection] so hairlines can be drawn between them. */
private class MenuRows {
    val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * A rounded list of plain rows on a translucent fill (not an opaque card), so the glass behind it
 * shows through. Hairlines are inset 16dp; each row arrives a beat after the one above, starting
 * at [firstBeat].
 */
@Composable
private fun MenuSection(firstBeat: Int, rows: MenuRows.() -> Unit) {
    val built = MenuRows().apply(rows).rows
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(PaneShapes.large)
            .background(PaneTheme.colors.secondaryFill),
    ) {
        built.forEachIndexed { index, row ->
            Box(Modifier.entrance(firstBeat + index)) { row() }
            if (index < built.lastIndex) Separator(Modifier.padding(start = 16.dp))
        }
    }
}
