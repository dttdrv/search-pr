package app.pane.browser.ui.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.extensions.InstalledExtension
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.PaneSwitch
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.settings.AdvancedSection
import app.pane.browser.ui.settings.NavRow
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.extensions.AddonMatching
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon
import app.pane.core.extensions.InstalledAddonRef
import app.pane.core.extensions.RecommendedExtensions

/** MIME types a picked add-on file may be reported as; `.xpi` rarely has its own. */
private val XPI_TYPES = arrayOf("application/x-xpinstall", "application/zip", "application/octet-stream")

/**
 * Installed extensions with their on/off switches, the add-on store, and a curated shortlist that
 * installs with one tap. Installing from a file and update checks sit under Advanced.
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
                item(key = "empty") { NoneYet(Modifier.arrive(0)) }
            }
            if (installed.isNotEmpty()) {
                item(key = "installed") {
                    GroupedSection(modifier = Modifier.arrive(0), header = "Installed", separatorInset = TileRowInset) {
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
                GroupedSection(modifier = Modifier.arrive(1)) {
                    row { NavRow("Browse add-ons", onClick = { navigator.push(Route.AddonStore) }) }
                }
            }
            item(key = "recommended") {
                GroupedSection(modifier = Modifier.arrive(2), header = "Recommended", separatorInset = TileRowInset) {
                    // Password managers belong to the phone (Settings › Passwords), not to a shortlist Pane promotes.
                    RecommendedExtensions.all.filter { it.category != "Security" }.forEach { rec ->
                        val listing = listings[rec.slug]
                        val state = when {
                            AddonMatching.isInstalled(rec.slug, rec.name, refs, recorded = recorded) -> GetState.Installed
                            isInstalling(installing, rec.slug, listing?.xpiUrl) -> GetState.Installing
                            else -> GetState.Get
                        }
                        row {
                            ListRow(
                                title = rec.name,
                                modifier = Modifier.heightIn(min = 60.dp),
                                subtitle = rec.summary,
                                leading = { AmoIcon(listing?.iconUrl, ExtensionRowIcon) },
                                showChevron = false,
                                onClick = if (listing != null) ({ detailSlug = rec.slug }) else null,
                            ) {
                                GetButton(state, onClick = { manager.install(Amo.latestXpiUrl(rec.slug), rec.slug) })
                            }
                        }
                    }
                }
            }
            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(3)) {
                    GroupedSection {
                        row {
                            ListRow(
                                title = "Install from file",
                                modifier = Modifier.heightIn(min = 56.dp),
                                subtitle = "Must be signed by Mozilla",
                                showChevron = false,
                                onClick = { if (!installingFile) picker.launch(XPI_TYPES) },
                            ) {
                                if (installingFile) SmallSpinner()
                            }
                        }
                        if (installed.isNotEmpty()) {
                            row {
                                ListRow(
                                    title = "Check for updates",
                                    modifier = Modifier.heightIn(min = 56.dp),
                                    showChevron = false,
                                    onClick = { manager.checkForUpdates() },
                                ) {
                                    if (updating) SmallSpinner()
                                }
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
    ListRow(
        title = ext.name,
        modifier = Modifier.heightIn(min = 60.dp),
        subtitle = ext.problem,
        leading = { ExtensionIcon(ext.icon, ExtensionRowIcon, dimmed = !ext.enabled) },
        showChevron = false,
        onClick = onClick,
    ) {
        PaneSwitch(checked = ext.enabled, onCheckedChange = onToggle, enabled = !ext.isBlocked)
    }
}

/** Shown above the lists when nothing is installed yet. */
@Composable
private fun NoneYet(modifier: Modifier = Modifier) {
    Text(
        "No extensions yet.",
        style = PaneTheme.type.body,
        color = PaneTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
    )
}

@Composable
internal fun SmallSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        color = PaneTheme.colors.secondaryLabel,
        strokeWidth = 1.5.dp,
    )
}
