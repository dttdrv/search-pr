package app.pane.browser.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.extensions.InstalledExtension
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.ExtensionPermissions

/**
 * One extension: who made it, switches for running it (and in private tabs), its settings, what it
 * is allowed to do in plain language, and removal.
 */
@Composable
fun ExtensionDetailScreen(extensionId: String) {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val installed by manager.installed.collectAsStateWithLifecycle()
    val loaded by manager.loaded.collectAsStateWithLifecycle()
    // Keeps the page on screen while it slides away after "Remove".
    val ext = rememberRetained(installed.firstOrNull { it.id == extensionId })
    var confirmRemove by remember { mutableStateOf(false) }

    if (ext == null) {
        LargeTitleScaffold(title = "Extension", onBack = navigator::pop, backLabel = "Extensions") {
            item(key = "missing") {
                Text(
                    if (loaded) "This extension isn’t installed anymore." else "Loading…",
                    style = PaneTheme.type.subheadline,
                    color = PaneTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                )
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = ext.name, onBack = navigator::pop, backLabel = "Extensions") {
            item(key = "header") { Header(ext) }
            ext.problem?.let { problem ->
                item(key = "problem") {
                    GroupedSection {
                        row {
                            ListRow(
                                title = if (ext.isBlocked) "Blocked" else "Needs Attention",
                                subtitle = problem,
                                leading = { IconTile(PaneIcons.Warning, if (ext.isBlocked) TileColors.red else TileColors.orange) },
                                showChevron = false,
                            )
                        }
                    }
                }
            }
            item(key = "switches") {
                GroupedSection(
                    footer = if (ext.canRunInPrivate) {
                        "Extensions allowed in private tabs can see and change what you do there."
                    } else {
                        "This extension doesn’t run in private tabs."
                    },
                ) {
                    row {
                        ToggleRow(
                            title = "Enabled",
                            checked = ext.enabled,
                            onCheckedChange = { manager.setEnabled(ext.id, it) },
                            enabled = !ext.isBlocked,
                        )
                    }
                    if (ext.canRunInPrivate) {
                        row {
                            ToggleRow(
                                title = "Allow in Private Tabs",
                                checked = ext.allowedInPrivateBrowsing,
                                onCheckedChange = { manager.setAllowedInPrivateBrowsing(ext.id, it) },
                            )
                        }
                    }
                }
            }
            val listingUrl = ext.amoListingUrl
            val homepage = ext.homepageUrl
            if (ext.optionsPageUrl != null || listingUrl != null || homepage != null) {
                item(key = "links") {
                    GroupedSection(separatorInset = TileRowInset) {
                        if (ext.optionsPageUrl != null) {
                            row {
                                ListRow(
                                    title = "Settings",
                                    leading = { IconTile(PaneIcons.Sliders, TileColors.gray) },
                                    value = if (ext.openOptionsPageInTab) "Opens in a tab" else null,
                                    onClick = { openExtensionOptions(container, navigator, ext.id) },
                                )
                            }
                        }
                        if (listingUrl != null) {
                            row {
                                ListRow(
                                    title = "View on addons.mozilla.org",
                                    leading = { IconTile(PaneIcons.Puzzle, TileColors.blue) },
                                    showChevron = false,
                                    onClick = { openInNewTab(container, navigator, listingUrl) },
                                ) {
                                    OpenIcon()
                                }
                            }
                        }
                        if (homepage != null && homepage != listingUrl) {
                            row {
                                ListRow(
                                    title = "Developer’s Website",
                                    subtitle = ext.creator,
                                    leading = { IconTile(PaneIcons.Globe, TileColors.indigo) },
                                    showChevron = false,
                                    onClick = { openInNewTab(container, navigator, homepage) },
                                ) {
                                    OpenIcon()
                                }
                            }
                        }
                    }
                }
            }
            item(key = "permissions") { Permissions(ext) }
            item(key = "remove") {
                GroupedSection {
                    row { ActionRow("Remove Extension", onClick = { confirmRemove = true }, destructive = true) }
                }
            }
        }
        PaneAlert(
            visible = confirmRemove,
            title = "Remove “${ext.name}”?",
            message = "Its settings and data will be deleted from Pane.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmRemove = false },
                AlertAction("Remove", AlertStyle.Destructive) {
                    confirmRemove = false
                    manager.uninstall(ext.id)
                    navigator.pop()
                },
            ),
            onDismissRequest = { confirmRemove = false },
        )
    }
}

@Composable
private fun Header(ext: InstalledExtension) {
    val colors = PaneTheme.colors
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ExtensionIcon(ext.icon, 64.dp, dimmed = !ext.enabled)
            Column(Modifier.weight(1f)) {
                Text(
                    ext.creator?.let { "by $it" } ?: "Extension",
                    style = PaneTheme.type.subheadline,
                    color = colors.label,
                    maxLines = 2,
                )
                Text("Version ${ext.version}", style = PaneTheme.type.footnote, color = colors.secondaryLabel)
                if (!ext.enabled) {
                    Text("Turned off", style = PaneTheme.type.footnote, color = colors.warning)
                }
            }
        }
        ext.description?.let {
            Text(
                it,
                style = PaneTheme.type.body,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun Permissions(ext: InstalledExtension) {
    val sentences = ext.permissionDescriptions
    val unlisted = ExtensionPermissions.unlisted(ext.permissions + ext.grantedOptionalPermissions)
    val footer = listOfNotNull(
        ext.dataCollectionDescription,
        unlisted.takeIf { it.isNotEmpty() }?.let { "Also uses: ${it.joinToString(", ")}." },
    ).joinToString("\n\n").ifEmpty { null }
    GroupedSection(header = "Permissions", footer = footer, separatorInset = 46.dp) {
        if (sentences.isEmpty()) {
            row { ListRow(title = "No special permissions", titleColor = PaneTheme.colors.secondaryLabel, showChevron = false) }
        } else {
            sentences.forEach { sentence ->
                row {
                    ListRow(
                        title = sentence,
                        leading = {
                            Icon(PaneIcons.Check, contentDescription = null, tint = PaneTheme.colors.accent, modifier = Modifier.size(18.dp))
                        },
                        showChevron = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenIcon() {
    Icon(PaneIcons.OpenExternal, contentDescription = null, tint = PaneTheme.colors.tertiaryLabel, modifier = Modifier.size(16.dp))
}
