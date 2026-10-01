package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.WebData
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.SiteIcon
import app.pane.browser.ui.library.EmptyState
import app.pane.browser.ui.library.RowSeparator
import app.pane.browser.ui.library.SiteRowInset
import app.pane.browser.ui.library.SectionTitle
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.Spacing
import app.pane.core.library.SiteOrigins
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.SitePermission
import kotlinx.coroutines.launch

/** Shown up front; the rest sit under Advanced. */
private val CommonKinds = listOf(SitePermission.Location, SitePermission.Camera, SitePermission.Microphone)
private val AdvancedKinds = SitePermission.entries - CommonKinds.toSet()

/** The answers a site can have, in the order the picker lists them: null asks every time. */
private val Answers = listOf<Boolean?>(null, true, false)

private fun answerLabel(answer: Boolean?) = when (answer) {
    true -> "Allow"
    false -> "Block"
    null -> "Ask"
}

/** "Location allowed, camera blocked": only what differs from asking. */
private fun summarize(answers: Map<SitePermission, Boolean>): String {
    if (answers.isEmpty()) return "Asks first"
    return answers.entries.mapIndexed { i, (kind, allowed) ->
        val label = PermissionText.settingLabel(kind).let { if (i == 0) it else it.lowercase() }
        "$label ${if (allowed) "allowed" else "blocked"}"
    }.joinToString(", ")
}

/** Sites with remembered permissions, plus the defaults that apply everywhere. */
@Composable
fun SiteSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.SiteSettings)

    val permissions = container.sitePermissions
    val version by permissions.changes.collectAsStateWithLifecycle()
    val sites = remember(version) { permissions.origins().filter { it.startsWith("https://") || it.startsWith("http://") } }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val shown = remember(sites, query) {
        val q = query.trim()
        if (q.isEmpty()) sites else sites.filter { SiteOrigins.displayName(it).contains(q, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Site permissions",
            onBack = navigator::pop,
            backLabel = backLabel,
            header = if (sites.isNotEmpty()) {
                {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search sites",
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp),
                    )
                }
            } else {
                null
            },
        ) {
            item(key = "defaults") {
                GroupedSection(modifier = Modifier.arrive(0), header = "All sites") {
                    row {
                        SwitchRow(
                            title = "Block pop-ups",
                            checked = settings.blockPopups,
                            onCheckedChange = { on -> container.settings.update { it.copy(blockPopups = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Block auto-play with sound",
                            checked = settings.autoplayBlocked,
                            onCheckedChange = { on -> container.settings.update { it.copy(autoplayBlocked = on) } },
                        )
                    }
                }
            }
            when {
                sites.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "No site permissions",
                        message = "Answers you give to sites appear here.",
                        modifier = Modifier.animateItem(),
                    )
                }
                shown.isEmpty() -> item(key = "no-results") {
                    EmptyState(
                        title = "No results",
                        message = "No sites match “${query.trim()}”.",
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> {
                    item(key = "sites-title") { SectionTitle("Sites", Modifier.animateItem()) }
                    itemsIndexed(shown, key = { _, origin -> "site:$origin" }) { index, origin ->
                        Column(Modifier.animateItem().arrive(index)) {
                            if (index > 0) RowSeparator(SiteRowInset)
                            ListRow(
                                title = SiteOrigins.displayName(origin),
                                modifier = Modifier.heightIn(min = 64.dp),
                                subtitle = summarize(permissions.forOrigin(origin)),
                                leading = { SiteIcon(origin, 28.dp) },
                                onClick = { navigator.push(Route.SitePermissions(origin)) },
                            )
                        }
                    }
                    item(key = "advanced") {
                        AdvancedSection(modifier = Modifier.animateItem()) {
                            GroupedSection {
                                row { ActionButtonRow("Reset all permissions", onClick = { confirmReset = true }, destructive = true) }
                            }
                        }
                    }
                }
            }
        }

        PaneAlert(
            visible = confirmReset,
            title = "Reset all permissions?",
            message = "Every site will have to ask again.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmReset = false },
                AlertAction("Reset", AlertStyle.Destructive) {
                    confirmReset = false
                    permissions.clearAll()
                    toasts.show("Site permissions reset", PaneIcons.Check)
                },
            ),
            onDismissRequest = { confirmReset = false },
        )
    }
}

/** One site's permissions, each Ask / Allow / Block, plus clearing what the site has stored. */
@Composable
fun SitePermissionsScreen(origin: String) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val colors = PaneTheme.colors
    val permissions = container.sitePermissions
    val backLabel = rememberBackLabel(Route.SitePermissions(origin))
    val name = SiteOrigins.displayName(origin)
    val host = SiteOrigins.hostOf(origin)

    val version by permissions.changes.collectAsStateWithLifecycle()
    val stored = remember(version) { permissions.forOrigin(origin) }

    var pickerKind by remember { mutableStateOf<SitePermission?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = name, onBack = navigator::pop, backLabel = backLabel) {
            item(key = "origin") {
                // Which exact address these answers belong to (http or https matters).
                Row(
                    Modifier.fillMaxWidth().arrive(0).padding(horizontal = Spacing.gutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SiteIcon(origin, 28.dp)
                    Text(origin, style = PaneTheme.type.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(key = "essentials") {
                GroupedSection(modifier = Modifier.arrive(1)) {
                    CommonKinds.forEach { kind ->
                        row {
                            NavRow(
                                title = PermissionText.settingLabel(kind),
                                value = answerLabel(stored[kind]),
                                onClick = {
                                    pickerKind = kind
                                    pickerVisible = true
                                },
                            )
                        }
                    }
                }
            }
            item(key = "data") {
                GroupedSection(modifier = Modifier.arrive(2), footer = "Signs you out of $host.") {
                    row { ActionButtonRow("Clear site data", onClick = { confirmClear = true }, destructive = true) }
                }
            }
            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(3)) {
                    GroupedSection {
                        AdvancedKinds.forEach { kind ->
                            row {
                                NavRow(
                                    title = PermissionText.settingLabel(kind),
                                    value = answerLabel(stored[kind]),
                                    onClick = {
                                        pickerKind = kind
                                        pickerVisible = true
                                    },
                                )
                            }
                        }
                    }
                    GroupedSection {
                        row { ActionButtonRow("Reset permissions", onClick = { confirmReset = true }, destructive = true) }
                    }
                }
            }
        }

        val kind = pickerKind
        ChoiceSheet(
            visible = pickerVisible,
            title = kind?.let(PermissionText::settingLabel).orEmpty(),
            message = null,
            options = Answers.map(::answerLabel),
            selectedIndex = if (kind == null) 0 else Answers.indexOf(stored[kind]),
            onSelect = { i ->
                if (kind != null) permissions.set(origin, kind, Answers[i])
                pickerVisible = false
            },
            onDismiss = { pickerVisible = false },
        )

        PaneAlert(
            visible = confirmClear,
            title = "Clear data for $host?",
            message = "You'll be signed out.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmClear = false },
                AlertAction("Clear", AlertStyle.Destructive) {
                    confirmClear = false
                    container.scope.launch {
                        WebData.clearSite(host)
                        toasts.show("Website data removed", PaneIcons.Check)
                    }
                },
            ),
            onDismissRequest = { confirmClear = false },
        )

        PaneAlert(
            visible = confirmReset,
            title = "Reset permissions?",
            message = "$name will have to ask again.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmReset = false },
                AlertAction("Reset", AlertStyle.Destructive) {
                    confirmReset = false
                    permissions.clear(origin)
                },
            ),
            onDismissRequest = { confirmReset = false },
        )
    }
}
