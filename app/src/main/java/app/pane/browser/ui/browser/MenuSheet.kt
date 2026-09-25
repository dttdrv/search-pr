package app.pane.browser.ui.browser

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.components.pressScale
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import app.pane.core.privacy.TrackingParams
import app.pane.core.tabs.TabState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The "…" menu: page actions up top as big tappable tiles, extension buttons, then everything
 * else as a short grouped list. One sheet, no nested menus.
 */
@Composable
fun MenuSheet(
    visible: Boolean,
    tab: TabState?,
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
    val url = tab?.url.orEmpty()
    val isPage = url.startsWith("http")
    val bookmarked by remember(url) {
        if (isPage) container.bookmarks.observeIsBookmarked(url) else flowOf(false)
    }.collectAsStateWithLifecycle(false)
    val actions by container.extensions.actions.collectAsStateWithLifecycle()

    fun go(route: Route) {
        onDismiss()
        navigator.push(route)
    }

    PaneSheet(visible = visible, onDismiss = onDismiss, maxHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            if (isPage) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAction(PaneIcons.Share, "Share", Modifier.weight(1f)) {
                        onDismiss()
                        val clean = if (container.settings.current.stripTrackingParams) TrackingParams.strip(url) else url
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clean)
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    QuickAction(if (bookmarked) PaneIcons.BookmarkFill else PaneIcons.Bookmark, if (bookmarked) "Saved" else "Bookmark", Modifier.weight(1f)) {
                        haptics.confirm()
                        scope.launch {
                            if (bookmarked) {
                                container.bookmarks.removeUrl(url)
                                toasts.show("Bookmark removed", PaneIcons.Bookmark)
                            } else {
                                container.bookmarks.add(url, tab?.title.orEmpty())
                                toasts.show("Bookmarked", PaneIcons.BookmarkFill, "Favorite") {
                                    scope.launch {
                                        container.bookmarks.all().firstOrNull { it.url == url }?.let { container.bookmarks.setFavorite(it.id, true) }
                                    }
                                }
                            }
                        }
                    }
                    QuickAction(PaneIcons.FindInPage, "Find", Modifier.weight(1f)) {
                        onDismiss()
                        onFindInPage()
                    }
                    QuickAction(PaneIcons.Copy, "Copy Link", Modifier.weight(1f)) {
                        val clean = if (container.settings.current.stripTrackingParams) TrackingParams.strip(url) else url
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newRawUri("URL", android.net.Uri.parse(clean)))
                        haptics.confirm()
                        onDismiss()
                        toasts.show("Link copied", PaneIcons.Link)
                    }
                }
            }

            if (actions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    actions.forEach { action ->
                        Column(
                            Modifier.width(64.dp).pressScale(enabled = action.enabled, haptic = true) {
                                onDismiss()
                                container.extensions.clickAction(action.extensionId)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Box(
                                    Modifier.size(48.dp).clip(ContinuousRoundedShape(12.dp)).background(colors.surface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val icon = action.icon
                                    if (icon != null) {
                                        Image(icon, null, modifier = Modifier.size(28.dp))
                                    } else {
                                        Icon(PaneIcons.Puzzle, null, tint = colors.secondaryLabel, modifier = Modifier.size(24.dp))
                                    }
                                }
                                if (!action.badgeText.isNullOrEmpty()) {
                                    Text(
                                        action.badgeText,
                                        style = PaneTheme.type.caption2,
                                        color = colors.onAccent,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clip(ContinuousRoundedShape(7.dp))
                                            .background(colors.destructive)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(action.title, style = PaneTheme.type.caption2, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            if (isPage) {
                GroupedSection {
                    if (tab != null && (tab.readerable || tab.inReaderMode)) {
                        row {
                            ListRow(
                                "Reader View",
                                leading = { IconTile(PaneIcons.Reader, colors.accent) },
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
                            leading = { IconTile(PaneIcons.Desktop, colors.label.copy(alpha = 0.55f)) },
                            showChevron = false,
                            onClick = { container.browser.toggleDesktopMode() },
                        ) {
                            PaneSwitch(checked = tab?.desktopMode == true, onCheckedChange = { container.browser.toggleDesktopMode() })
                        }
                    }
                    row {
                        ListRow(
                            "Add to Home Screen",
                            leading = { IconTile(PaneIcons.Plus, colors.positive) },
                            showChevron = false,
                            onClick = {
                                onDismiss()
                                if (tab != null) HomeShortcuts.pin(context, tab.url, tab.title)
                            },
                        )
                    }
                }
            }

            GroupedSection {
                row { ListRow("New Tab", leading = { IconTile(PaneIcons.Plus, colors.accent) }, showChevron = false, onClick = { onDismiss(); onNewTab(false) }) }
                row { ListRow("New Private Tab", leading = { IconTile(PaneIcons.Private, androidx.compose.ui.graphics.Color(0xFF7C5CE6)) }, showChevron = false, onClick = { onDismiss(); onNewTab(true) }) }
            }
            GroupedSection {
                row { ListRow("Bookmarks", leading = { IconTile(PaneIcons.Book, androidx.compose.ui.graphics.Color(0xFF0A84FF)) }, onClick = { go(Route.Bookmarks) }) }
                row { ListRow("History", leading = { IconTile(PaneIcons.Clock, androidx.compose.ui.graphics.Color(0xFF8E8E93)) }, onClick = { go(Route.History) }) }
                row { ListRow("Downloads", leading = { IconTile(PaneIcons.Download, androidx.compose.ui.graphics.Color(0xFF30B0C7)) }, onClick = { go(Route.Downloads) }) }
                row { ListRow("Extensions", leading = { IconTile(PaneIcons.Puzzle, androidx.compose.ui.graphics.Color(0xFFFF9F0A)) }, onClick = { go(Route.Extensions) }) }
                row { ListRow("Settings", leading = { IconTile(PaneIcons.Gear, androidx.compose.ui.graphics.Color(0xFF636366)) }, onClick = { go(Route.Settings) }) }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = PaneTheme.colors
    Column(
        modifier
            .clip(ContinuousRoundedShape(14.dp))
            .background(colors.surface)
            .pressScale(pressedScale = 0.95f, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = colors.label, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = PaneTheme.type.caption, color = colors.label, maxLines = 1)
    }
}
