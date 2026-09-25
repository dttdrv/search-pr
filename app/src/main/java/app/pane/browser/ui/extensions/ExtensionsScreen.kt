package app.pane.browser.ui.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.extensions.InstalledExtension
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.ContinuousRoundedShape
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.AddonMatching
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon
import app.pane.core.extensions.InstalledAddonRef
import app.pane.core.extensions.RecommendedExtensions

/** MIME types a picked add-on file may be reported as; `.xpi` rarely has its own. */
private val XPI_TYPES = arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream")

/**
 * Installed extensions with their on/off switches, ways to get more (the add-on store, a file),
 * and a curated shortlist that installs with one tap.
 */
@Composable
fun ExtensionsScreen() {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val installed by manager.installed.collectAsStateWithLifecycle()
    val loaded by manager.loaded.collectAsStateWithLifecycle()
    val installing by manager.installing.collectAsStateWithLifecycle()
    val updating by manager.updating.collectAsStateWithLifecycle()
    val recorded by manager.recordedSlugs.collectAsStateWithLifecycle()
    val listings by produceState(initialValue = emptyMap<String, AmoAddon>(), manager) {
        value = manager.amo.recommendedListings()
    }
    val refs = remember(installed) { installed.map { InstalledAddonRef(it.id, it.name, it.amoListingUrl) } }
    var detailSlug by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) manager.installFromFile(uri)
    }
    val installingFile = installing.any { !it.startsWith("http") }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "Extensions", onBack = navigator::pop) {
            if (loaded && installed.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
            if (installed.isNotEmpty()) {
                item(key = "installed") {
                    GroupedSection(header = "Installed", separatorInset = TileRowInset) {
                        installed.forEach { ext ->
                            row {
                                InstalledRow(
                                    ext = ext,
                                    onToggle = { manager.setEnabled(ext.id, it) },
                                    onClick = { navigator.push(Route.ExtensionDetail(ext.id)) },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "get") {
                GroupedSection(
                    header = "Get Extensions",
                    footer = "Add-ons from addons.mozilla.org are reviewed and signed by Mozilla. Files must be signed to install.",
                    separatorInset = TileRowInset,
                ) {
                    row {
                        ListRow(
                            title = "Browse Add-ons",
                            leading = { IconTile(PaneIcons.Puzzle, TileColors.blue) },
                            onClick = { navigator.push(Route.AddonStore) },
                        )
                    }
                    row {
                        ListRow(
                            title = "Install from File…",
                            leading = { IconTile(PaneIcons.Download, TileColors.gray) },
                            showChevron = false,
                            onClick = { if (!installingFile) picker.launch(XPI_TYPES) },
                        ) {
                            if (installingFile) SmallSpinner()
                        }
                    }
                    if (installed.isNotEmpty()) {
                        row {
                            ListRow(
                                title = "Check for Updates",
                                subtitle = "Pane also checks once a day.",
                                leading = { IconTile(PaneIcons.Reload, TileColors.green) },
                                showChevron = false,
                                onClick = { manager.checkForUpdates() },
                            ) {
                                if (updating) SmallSpinner()
                            }
                        }
                    }
                }
            }
            item(key = "recommended") {
                GroupedSection(header = "Recommended", separatorInset = TileRowInset) {
                    RecommendedExtensions.all.forEach { rec ->
                        val listing = listings[rec.slug]
                        val state = when {
                            AddonMatching.isInstalled(rec.slug, rec.name, refs, recorded = recorded) -> GetState.Installed
                            isInstalling(installing, rec.slug, listing?.xpiUrl) -> GetState.Installing
                            else -> GetState.Get
                        }
                        row {
                            ListRow(
                                title = rec.name,
                                subtitle = rec.summary,
                                leading = { AmoIcon(listing?.iconUrl, 29.dp) { CategoryTile(rec.category, 29.dp) } },
                                showChevron = false,
                                onClick = if (listing != null) ({ detailSlug = rec.slug }) else null,
                            ) {
                                GetButton(state, onClick = { manager.install(Amo.latestXpiUrl(rec.slug), rec.slug) })
                            }
                        }
                    }
                }
            }
        }
        AddonDetailSheet(addon = detailSlug?.let { listings[it] }, onDismiss = { detailSlug = null })
    }
}

@Composable
private fun InstalledRow(ext: InstalledExtension, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val subtitle = ext.problem ?: if (ext.enabled) "Version ${ext.version}" else "Off · Version ${ext.version}"
    ListRow(
        title = ext.name,
        subtitle = subtitle,
        leading = { ExtensionIcon(ext.icon, 29.dp, dimmed = !ext.enabled) },
        showChevron = false,
        onClick = onClick,
    ) {
        PaneSwitch(checked = ext.enabled, onCheckedChange = onToggle, enabled = !ext.isBlocked)
    }
}

@Composable
private fun EmptyState() {
    val colors = PaneTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .padding(top = 20.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(ContinuousRoundedShape(15.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF5E9BFF), Color(0xFF3A5BFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PaneIcons.Puzzle, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Firefox add-ons work in Pane",
            style = PaneTheme.type.title3,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Block ads and trackers, fill in passwords, or restyle the web. Browse the add-on store, or start with one of the favorites below.",
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SmallSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        color = PaneTheme.colors.secondaryLabel,
        strokeWidth = 2.dp,
    )
}
