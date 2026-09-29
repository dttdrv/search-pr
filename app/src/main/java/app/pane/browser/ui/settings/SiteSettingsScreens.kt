package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.downloads.await
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.SearchField
import app.pane.browser.ui.library.EmptyState
import app.pane.browser.ui.library.RowSeparator
import app.pane.browser.ui.library.SectionTitle
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.icons.PaneIcons
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
    /** Shown up front; the rest sit under Advanced. */
    val common: Boolean = false,
) {
    Location(PermissionDelegate.PERMISSION_GEOLOCATION, "geolocation", "Location", common = true),
    Notifications(PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION, "desktop-notification", "Notifications", common = true),
    ProtectedContent(PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS, "media-key-system-access", "Protected content"),
    PersistentStorage(PermissionDelegate.PERMISSION_PERSISTENT_STORAGE, "persistent-storage", "Persistent storage"),
    LocalNetwork(PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS, "local-network", "Local network"),
    LocalApps(PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS, "loopback-network", "Apps on this device"),
    VirtualReality(PermissionDelegate.PERMISSION_XR, "xr", "Virtual reality"),
}

/** Autoplay is two Gecko permissions (with and without sound) shown as one choice, as in Safari. */
private enum class AutoplayChoice(val label: String, val audible: Int, val inaudible: Int) {
    Default("Use default", ContentPermission.VALUE_PROMPT, ContentPermission.VALUE_PROMPT),
    AllowAll("Allow all", ContentPermission.VALUE_ALLOW, ContentPermission.VALUE_ALLOW),
    StopSound("Stop media with sound", ContentPermission.VALUE_DENY, ContentPermission.VALUE_ALLOW),
    Never("Never", ContentPermission.VALUE_DENY, ContentPermission.VALUE_DENY),
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
    if (parts.isEmpty()) return "Asks first"
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
            title = "Site permissions",
            onBack = navigator::pop,
            backLabel = backLabel,
            header = if (!sites.isNullOrEmpty()) {
                {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search sites",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            val loaded = sites
            when {
                loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
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
                    itemsIndexed(shown, key = { _, site -> "site:${site.origin}" }) { index, site ->
                        Box(Modifier.animateItem().arrive(index).groupedItem(index == 0, index == shown.lastIndex, colors.surface)) {
                            Column {
                                if (index > 0) RowSeparator()
                                NavRow(
                                    title = SiteOrigins.displayName(site.origin),
                                    subtitle = summarize(site.permissions),
                                    onClick = { navigator.push(Route.SitePermissions(site.origin)) },
                                )
                            }
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
            item(key = "essentials") {
                GroupedSection(modifier = Modifier.arrive(0), header = origin) {
                    SitePermission.entries.filter { it.common }.forEach { kind ->
                        row {
                            NavRow(
                                title = kind.title,
                                value = valueLabel(current(kind.key, kind.type)),
                                onClick = {
                                    pickerKind = kind
                                    pickerVisible = true
                                },
                            )
                        }
                    }
                    row {
                        NavRow(
                            title = "Auto-play",
                            value = when (autoplay) {
                                AutoplayChoice.Default -> "Default"
                                AutoplayChoice.AllowAll -> "Allow"
                                AutoplayChoice.StopSound -> "No sound"
                                AutoplayChoice.Never -> "Never"
                            },
                            onClick = { autoplayVisible = true },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Tracking protection",
                            checked = trackingProtected,
                            onCheckedChange = { on ->
                                val value = if (on) ContentPermission.VALUE_DENY else ContentPermission.VALUE_ALLOW
                                assign(TRACKING_KEY, PermissionDelegate.PERMISSION_TRACKING, value)
                            },
                        )
                    }
                }
            }
            item(key = "data") {
                GroupedSection(modifier = Modifier.arrive(1), footer = "Signs you out of $host.") {
                    row { ActionButtonRow("Clear site data", onClick = { confirmClear = true }, destructive = true) }
                }
            }
            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(2)) {
                    GroupedSection {
                        SitePermission.entries.filterNot { it.common }.forEach { kind ->
                            row {
                                NavRow(
                                    title = kind.title,
                                    value = valueLabel(current(kind.key, kind.type)),
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
            title = kind?.title.orEmpty(),
            message = null,
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
            title = "Auto-play",
            message = null,
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
            title = "Clear data for $host?",
            message = "You'll be signed out.",
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
            title = "Reset permissions?",
            message = "$name will have to ask again.",
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
