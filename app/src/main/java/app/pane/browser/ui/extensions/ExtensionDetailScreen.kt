package app.pane.browser.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.settings.ActionButtonRow
import app.pane.browser.ui.settings.AdvancedSection
import app.pane.browser.ui.settings.NavRow
import app.pane.browser.ui.settings.SwitchRow
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
            item(key = "header") { Header(ext, Modifier.arrive(0)) }
            ext.problem?.let { problem ->
                item(key = "problem") {
                    GroupedSection(modifier = Modifier.arrive(1)) {
                        row {
                            ListRow(
                                title = if (ext.isBlocked) "Blocked" else "Needs attention",
                                modifier = Modifier.heightIn(min = 56.dp),
                                subtitle = problem,
                                titleColor = if (ext.isBlocked) PaneTheme.colors.destructive else PaneTheme.colors.warning,
                                showChevron = false,
                            )
                        }
                    }
                }
            }
            item(key = "switches") {
                GroupedSection(
                    modifier = Modifier.arrive(2),
                    footer = if (ext.canRunInPrivate) null else "Doesn't run in private tabs.",
                ) {
                    row {
                        SwitchRow(
                            title = "Enabled",
                            checked = ext.enabled,
                            onCheckedChange = { manager.setEnabled(ext.id, it) },
                            enabled = !ext.isBlocked,
                        )
                    }
                    if (ext.canRunInPrivate) {
                        row {
                            SwitchRow(
                                title = "Allow in private tabs",
                                checked = ext.allowedInPrivateBrowsing,
                                onCheckedChange = { manager.setAllowedInPrivateBrowsing(ext.id, it) },
                            )
                        }
                    }
                    if (ext.optionsPageUrl != null) {
                        row {
                            NavRow(
                                title = "Settings",
                                value = if (ext.openOptionsPageInTab) "Opens in a tab" else null,
                                onClick = { openExtensionOptions(container, navigator, ext.id) },
                            )
                        }
                    }
                }
            }
            item(key = "permissions") { Permissions(ext, Modifier.arrive(3)) }
            item(key = "remove") {
                GroupedSection(modifier = Modifier.arrive(4)) {
                    row { ActionButtonRow("Remove extension", onClick = { confirmRemove = true }, destructive = true) }
                }
            }
            val listingUrl = ext.amoListingUrl
            val homepage = ext.homepageUrl
            if (listingUrl != null || homepage != null) {
                item(key = "advanced") {
                    AdvancedSection(modifier = Modifier.arrive(5)) {
                        GroupedSection {
                            if (listingUrl != null) {
                                row { NavRow("View on addons.mozilla.org", onClick = { openInNewTab(container, navigator, listingUrl) }) }
                            }
                            if (homepage != null && homepage != listingUrl) {
                                row {
                                    NavRow(
                                        title = "Developer's website",
                                        subtitle = ext.creator,
                                        onClick = { openInNewTab(container, navigator, homepage) },
                                    )
                                }
                            }
                        }
                    }
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
private fun Header(ext: InstalledExtension, modifier: Modifier = Modifier) {
    val colors = PaneTheme.colors
    Column(modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ExtensionIcon(ext.icon, 64.dp, dimmed = !ext.enabled)
            Column(Modifier.weight(1f)) {
                Text(
                    ext.creator?.let { "by $it" } ?: "Extension",
                    style = PaneTheme.type.body,
                    color = colors.label,
                    maxLines = 2,
                )
                Text("Version ${ext.version}", style = PaneTheme.type.footnote, color = colors.secondaryLabel)
            }
        }
        ext.description?.let {
            Text(
                it,
                style = PaneTheme.type.body,
                color = colors.label,
                maxLines = 4,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun Permissions(ext: InstalledExtension, modifier: Modifier = Modifier) {
    val sentences = ext.permissionDescriptions
    val unlisted = ExtensionPermissions.unlisted(ext.permissions + ext.grantedOptionalPermissions)
    val footer = listOfNotNull(
        ext.dataCollectionDescription,
        unlisted.takeIf { it.isNotEmpty() }?.let { "Also uses: ${it.joinToString(", ")}." },
    ).joinToString("\n\n").ifEmpty { null }
    GroupedSection(modifier = modifier, header = "Permissions", footer = footer) {
        if (sentences.isEmpty()) {
            row { ListRow(title = "No special permissions", modifier = Modifier.heightIn(min = 56.dp), showChevron = false) }
        } else {
            sentences.forEach { sentence ->
                row { ListRow(title = sentence, modifier = Modifier.heightIn(min = 56.dp), showChevron = false) }
            }
        }
    }
}
