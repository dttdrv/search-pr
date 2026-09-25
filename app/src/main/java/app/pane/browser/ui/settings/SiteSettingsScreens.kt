package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.downloads.await
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.EmptyState
import app.pane.browser.ui.library.FileGlyphs
import app.pane.browser.ui.library.RowSeparator
import app.pane.browser.ui.library.SectionTitle
import app.pane.browser.ui.library.SiteTile
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.groupedItem
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.library.SiteOrigins
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.StorageController

/** A per-site permission Gecko keeps in its permission store, with its store key. */
internal enum class SitePermission(
    val type: Int,
    val key: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val description: String,
) {
    Location(
        PermissionDelegate.PERMISSION_GEOLOCATION, "geolocation", "Location",
        PaneIcons.Location, TileColors.Blue, "Lets the site see where you are.",
    ),
    Notifications(
        PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION, "desktop-notification", "Notifications",
        PaneIcons.Bell, TileColors.Red, "Lets the site send you notifications, even while it isn't open.",
    ),
    ProtectedContent(
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS, "media-key-system-access", "Protected Content",
        PaneIcons.Key, TileColors.Orange, "Lets the site play copy-protected (DRM) video and music.",
    ),
    PersistentStorage(
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, "persistent-storage", "Persistent Storage",
        PaneIcons.Download, TileColors.Gray, "Keeps the site's offline data even when your device runs low on space.",
    ),
    LocalNetwork(
        PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS, "local-network", "Local Network",
        PaneIcons.Grid, TileColors.Teal, "Lets the site reach devices on your network, such as printers and TVs.",
    ),
    LocalApps(
        PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, "loopback-network", "Apps on This Device",
        PaneIcons.Phone, TileColors.Mint, "Lets the site talk to other apps running on this device.",
    ),
    VirtualReality(
        PermissionDelegate.PERMISSION_XR, "xr", "Virtual Reality",
        PaneIcons.Desktop, TileColors.Purple, "Lets the site use virtual and augmented reality devices.",
    ),
}

/** Autoplay is two Gecko permissions (with and without sound) shown as one choice, as in Safari. */
private enum class AutoplayChoice(val label: String, val audible: Int, val inaudible: Int) {
    Default("Use Default", ContentPermission.VALUE_PROMPT, ContentPermission.VALUE_PROMPT),
    AllowAll("Allow All Auto-Play", ContentPermission.VALUE_ALLOW, ContentPermission.VALUE_ALLOW),
    StopSound("Stop Media with Sound", ContentPermission.VALUE_DENY, ContentPermission.VALUE_ALLOW),
    Never("Never Auto-Play", ContentPermission.VALUE_DENY, ContentPermission.VALUE_DENY),
    ;

    companion object {
        fun of(audible: Int, inaudible: Int): AutoplayChoice = when {
            audible == ContentPermission.VALUE_ALLOW -> AllowAll
            inaudible == ContentPermission.VALUE_DENY -> Never
            audible == ContentPermission.VALUE_DENY -> StopSound
            else -> Default
        }
    }
}

private const val AUTOPLAY_AUDIBLE_KEY = "autoplay-media-audible"
private const val AUTOPLAY_INAUDIBLE_KEY = "autoplay-media-inaudible"
private const val TRACKING_KEY = "trackingprotection"

/** Values in the order the pickers list them. */
private val PermissionValues = listOf(ContentPermission.VALUE_PROMPT, ContentPermission.VALUE_ALLOW, ContentPermission.VALUE_DENY)

private fun valueLabel(value: Int) = when (value) {
    ContentPermission.VALUE_ALLOW -> "Allow"
    ContentPermission.VALUE_DENY -> "Block"
    else -> "Ask"
}

/** Cookies, storage and caches: what "Clear Site Data" removes. Permissions are reset separately. */
private val SITE_DATA_FLAGS: Long =
    StorageController.ClearFlags.COOKIES or
        StorageController.ClearFlags.DOM_STORAGES or
        StorageController.ClearFlags.ALL_CACHES or
        StorageController.ClearFlags.AUTH_SESSIONS

internal data class SiteEntry(val origin: String, val permissions: List<ContentPermission>)

/**
 * Every stored permission that Pane shows. Private-mode entries vanish with the session anyway,
 * and storage-access grants are made by Gecko's heuristics for most sites a person logs into,
 * so listing them would bury the choices people actually made.
 */
internal suspend fun loadPermissions(runtime: GeckoRuntime): List<ContentPermission> = try {
    runtime.storageController.getAllPermissions().await().orEmpty()
        .filter { !it.privateMode && it.permission != PermissionDelegate.PERMISSION_STORAGE_ACCESS }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    emptyList()
}

internal fun groupSites(permissions: List<ContentPermission>): List<SiteEntry> =
    permissions
        .groupBy { SiteOrigins.originOf(it.uri) }
        .mapNotNull { (origin, list) -> origin?.let { SiteEntry(it, list) } }
        .filter { it.origin.startsWith("https://") || it.origin.startsWith("http://") }
        .sortedBy { SiteOrigins.displayName(it.origin) }

/** "Location allowed, notifications blocked" — only what differs from asking. */
private fun summarize(permissions: List<ContentPermission>): String {
    val parts = mutableListOf<String>()
    fun describe(title: String, value: Int?) {
        when (value) {
            ContentPermission.VALUE_ALLOW -> parts += "$title allowed"
            ContentPermission.VALUE_DENY -> parts += "$title blocked"
        }
    }
    SitePermission.entries.forEach { kind -> describe(kind.title, permissions.firstOrNull { it.permission == kind.type }?.value) }
    describe("Auto-play", permissions.firstOrNull { it.permission == PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE }?.value)
    if (permissions.any { it.permission == PermissionDelegate.PERMISSION_TRACKING && it.value == ContentPermission.VALUE_ALLOW }) {
        parts += "Tracking protection off"
    }
    if (parts.isEmpty()) return "Asks before using anything"
    return parts.mapIndexed { i, p -> if (i == 0) p else p.lowercase() }.joinToString(", ")
}

/**
 * A permission for [key] on the same site as [templateJson]. Gecko only hands out permissions it
 * already stores, but they all carry the site's serialized principal, so a stored one can be
 * re-targeted to set a permission the site has never asked for.
 */
private fun derivePermission(templateJson: String, key: String, value: Int): ContentPermission? = try {
    val json = JSONObject(templateJson)
    json.put("perm", key)
    json.put("value", value)
    json.remove("thirdPartyOrigin")
    ContentPermission.fromJson(json)
} catch (_: Exception) {
    null
}

/** Sites with remembered permissions, plus the defaults that apply everywhere. */
@Composable
fun SiteSettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val colors = PaneTheme.colors
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.SiteSettings)

    var sites by remember { mutableStateOf<List<SiteEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var reloads by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    // Reload whenever this screen is back on top, e.g. after changing a site.
    val onTop = navigator.top == Route.SiteSettings
    LaunchedEffect(onTop, reloads) {
        if (onTop) sites = groupSites(loadPermissions(container.runtime))
    }
    val shown = remember(sites, query) {
        val q = query.trim()
        val all = sites.orEmpty()
        if (q.isEmpty()) all else all.filter { SiteOrigins.displayName(it.origin).contains(q, ignoreCase = true) }
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = "Site Settings",
            onBack = navigator::pop,
            backLabel = backLabel,
            header = if (!sites.isNullOrEmpty()) {
                {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search Sites",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                null
            },
        ) {
            item(key = "defaults") {
                GroupedSection(header = "All Sites", separatorInset = 57.dp) {
                    row {
                        ToggleRow(
                            title = "Block Auto-Play with Sound",
                            subtitle = "Videos with sound wait until you press play",
                            checked = settings.autoplayBlocked,
                            onCheckedChange = { on -> container.settings.update { it.copy(autoplayBlocked = on) } },
                            leading = { IconTile(FileGlyphs.Video, TileColors.Pink) },
                        )
                    }
                    row {
                        ToggleRow(
                            title = "Block Pop-ups",
                            subtitle = "Only open new windows when you tap",
                            checked = settings.blockPopups,
                            onCheckedChange = { on -> container.settings.update { it.copy(blockPopups = on) } },
                            leading = { IconTile(PaneIcons.Tabs, TileColors.Indigo) },
                        )
                    }
                }
            }
            val loaded = sites
            when {
                loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = PaneIcons.Globe,
                        title = "No Site Permissions",
                        message = "When a site asks for your location, to send notifications or to play protected content, your answer is remembered here.",
                        modifier = Modifier.animateItem(),
                    )
                }
                shown.isEmpty() -> item(key = "no-results") {
                    EmptyState(
                        icon = PaneIcons.Search,
                        title = "No Results",
                        message = "No sites match “${query.trim()}”.",
                        modifier = Modifier.animateItem(),
                    )
                }
                else -> {
                    item(key = "sites-title") { SectionTitle("Sites", Modifier.animateItem()) }
                    itemsIndexed(shown, key = { _, site -> "site:${site.origin}" }) { index, site ->
                        Box(Modifier.animateItem().groupedItem(index == 0, index == shown.lastIndex, colors.surface)) {
                            Column {
                                if (index > 0) RowSeparator()
                                ListRow(
                                    title = SiteOrigins.displayName(site.origin),
                                    subtitle = summarize(site.permissions),
                                    leading = { SiteTile(site.origin, null) },
                                    onClick = { navigator.push(Route.SitePermissions(site.origin)) },
                                )
                            }
                        }
                    }
                    item(key = "reset") {
                        GroupedSection(modifier = Modifier.animateItem()) {
                            row { ActionRow("Reset All Site Permissions", onClick = { confirmReset = true }, destructive = true) }
                        }
                    }
                }
            }
        }

        PaneAlert(
            visible = confirmReset,
            title = "Reset All Site Permissions?",
            message = "Every site will have to ask again before using your location, notifications and more.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmReset = false },
                AlertAction("Reset", AlertStyle.Destructive) {
                    confirmReset = false
                    container.scope.launch {
                        runCatching { container.runtime.storageController.clearData(StorageController.ClearFlags.PERMISSIONS).await() }
                        reloads++
                        toasts.show("Site permissions reset", PaneIcons.Check)
                    }
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
    val controller = container.runtime.storageController
    val backLabel = rememberBackLabel(Route.SitePermissions(origin))
    val name = SiteOrigins.displayName(origin)
    val host = SiteOrigins.hostOf(origin)

    var stored by remember { mutableStateOf<List<ContentPermission>?>(null) }
    var template by remember { mutableStateOf<String?>(null) }
    // Values changed on this screen; Gecko applies them asynchronously, so show them right away.
    val overrides = remember { mutableStateMapOf<String, Int>() }
    var reloads by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloads) {
        val mine = loadPermissions(container.runtime).filter { SiteOrigins.originOf(it.uri) == origin }
        stored = mine
        if (template == null) template = mine.firstNotNullOfOrNull { p -> runCatching { p.toJson().toString() }.getOrNull() }
        overrides.clear()
    }

    var pickerKind by remember { mutableStateOf<SitePermission?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var autoplayVisible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    fun storedFor(type: Int): ContentPermission? = stored.orEmpty().firstOrNull { it.permission == type }

    fun current(key: String, type: Int, default: Int = ContentPermission.VALUE_PROMPT): Int =
        overrides[key] ?: storedFor(type)?.value ?: default

    fun assign(key: String, type: Int, value: Int) {
        val permission = storedFor(type) ?: template?.let { derivePermission(it, key, value) }
        if (permission == null) {
            toasts.show("Visit $name once to change its permissions", PaneIcons.Info)
            return
        }
        controller.setPermission(permission, value)
        overrides[key] = value
    }

    val trackingProtected = current(TRACKING_KEY, PermissionDelegate.PERMISSION_TRACKING, ContentPermission.VALUE_DENY) != ContentPermission.VALUE_ALLOW
    val autoplay = AutoplayChoice.of(
        current(AUTOPLAY_AUDIBLE_KEY, PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE),
        current(AUTOPLAY_INAUDIBLE_KEY, PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE),
    )

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = name, onBack = navigator::pop, backLabel = backLabel) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SiteTile(origin, null, size = 64.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(origin, style = PaneTheme.type.footnote, color = colors.secondaryLabel)
                }
            }
            item(key = "permissions") {
                GroupedSection(
                    header = "Permissions",
                    footer = "Ask means $name has to ask you first. Camera and microphone are asked for every visit.",
                    separatorInset = 57.dp,
                ) {
                    SitePermission.entries.forEach { kind ->
                        row {
                            ListRow(
                                title = kind.title,
                                leading = { IconTile(kind.icon, kind.color) },
                                value = valueLabel(current(kind.key, kind.type)),
                                onClick = {
                                    pickerKind = kind
                                    pickerVisible = true
                                },
                            )
                        }
                    }
                    row {
                        ListRow(
                            title = "Auto-Play",
                            leading = { IconTile(FileGlyphs.Video, TileColors.Pink) },
                            value = if (autoplay == AutoplayChoice.Default) "Default" else autoplay.label.removeSuffix(" Auto-Play"),
                            onClick = { autoplayVisible = true },
                        )
                    }
                }
            }
            item(key = "tracking") {
                GroupedSection(
                    footer = if (trackingProtected) {
                        "Trackers are blocked on $name."
                    } else {
                        "Trackers can follow you on $name. Turn protection back on unless the site breaks without them."
                    },
                ) {
                    row {
                        ToggleRow(
                            title = "Tracking Protection",
                            checked = trackingProtected,
                            onCheckedChange = { on ->
                                val value = if (on) ContentPermission.VALUE_DENY else ContentPermission.VALUE_ALLOW
                                assign(TRACKING_KEY, PermissionDelegate.PERMISSION_TRACKING, value)
                            },
                            leading = { IconTile(PaneIcons.ShieldCheck, TileColors.Green) },
                        )
                    }
                }
            }
            item(key = "data") {
                GroupedSection(footer = "Clearing site data signs you out of $host and removes what it saved on this device.") {
                    row { ActionRow("Clear Site Data", onClick = { confirmClear = true }, destructive = true) }
                    row { ActionRow("Reset Permissions", onClick = { confirmReset = true }, destructive = true) }
                }
            }
        }

        val kind = pickerKind
        ChoiceSheet(
            visible = pickerVisible,
            title = kind?.title.orEmpty(),
            message = kind?.description,
            options = PermissionValues.map(::valueLabel),
            selectedIndex = if (kind == null) 0 else PermissionValues.indexOf(current(kind.key, kind.type)),
            onSelect = { i ->
                if (kind != null) assign(kind.key, kind.type, PermissionValues[i])
                pickerVisible = false
            },
            onDismiss = { pickerVisible = false },
        )

        ChoiceSheet(
            visible = autoplayVisible,
            title = "Auto-Play",
            message = "Whether videos on $name can start playing on their own. Default follows your setting for all sites.",
            options = AutoplayChoice.entries.map { it.label },
            selectedIndex = autoplay.ordinal,
            onSelect = { i ->
                val choice = AutoplayChoice.entries[i]
                assign(AUTOPLAY_AUDIBLE_KEY, PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, choice.audible)
                assign(AUTOPLAY_INAUDIBLE_KEY, PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE, choice.inaudible)
                autoplayVisible = false
            },
            onDismiss = { autoplayVisible = false },
        )

        PaneAlert(
            visible = confirmClear,
            title = "Clear Data for $host?",
            message = "Cookies, storage and cached files for this site will be removed. You'll be signed out.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmClear = false },
                AlertAction("Clear", AlertStyle.Destructive) {
                    confirmClear = false
                    container.scope.launch {
                        // IP addresses and single-label hosts have no base domain to widen to.
                        val byHost = host.startsWith("[") || host.none { it.isLetter() } || !host.contains('.')
                        val done = withTimeoutOrNull(CLEAR_TIMEOUT_MS) {
                            runCatching {
                                if (byHost) {
                                    controller.clearDataFromHost(host, SITE_DATA_FLAGS).await()
                                } else {
                                    controller.clearDataFromBaseDomain(host, SITE_DATA_FLAGS).await()
                                }
                            }.isSuccess
                        } == true
                        if (done) {
                            toasts.show("Website data removed", PaneIcons.Check)
                        } else {
                            toasts.show("Couldn't clear all data for $host", PaneIcons.Warning)
                        }
                    }
                },
            ),
            onDismissRequest = { confirmClear = false },
        )

        PaneAlert(
            visible = confirmReset,
            title = "Reset Permissions?",
            message = "$name will have to ask again before using anything, and tracking protection is turned back on.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmReset = false },
                AlertAction("Reset", AlertStyle.Destructive) {
                    confirmReset = false
                    container.scope.launch {
                        withTimeoutOrNull(CLEAR_TIMEOUT_MS) {
                            runCatching { controller.clearDataFromHost(host, StorageController.ClearFlags.PERMISSIONS).await() }
                        }
                        reloads++
                    }
                },
            ),
            onDismissRequest = { confirmReset = false },
        )
    }
}

private const val CLEAR_TIMEOUT_MS = 15_000L
