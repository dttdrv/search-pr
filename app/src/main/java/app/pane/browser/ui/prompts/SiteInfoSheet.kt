package app.pane.browser.ui.prompts

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.prompts.SitePermissions
import app.pane.browser.ui.components.ActionRow
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.SheetHeader
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.SitePermission
import app.pane.core.tabs.SecurityState
import app.pane.core.url.UrlInput
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.StorageController
import kotlin.coroutines.resume

private class Connection(val title: String, val detail: String, val icon: ImageVector, val tint: Color)

/** A stored site permission and the value the user may just have changed it to. */
private class SiteGrant(val permission: ContentPermission, val kind: SitePermission, val value: Int)

/**
 * The sheet behind the lock icon: how the connection is protected, what was blocked, the site's
 * remembered permissions (switchable in place) and a way to wipe its cookies and storage.
 */
@Composable
fun SiteInfoSheet(visible: Boolean, tabId: String?, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = PaneTheme.colors
    val toasts = LocalToasts.current
    val browserState by container.store.state.collectAsStateWithLifecycle()
    val tab by remember(tabId) { derivedStateOf { browserState.tab(tabId) } }
    val url = tab?.url.orEmpty()
    val isWeb = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
    val host = if (isWeb) PermissionText.displayHost(url) else "Pane"
    var grants by remember(url) { mutableStateOf<List<SiteGrant>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(visible, url) {
        if (visible && isWeb) grants = loadGrants(container, url, tab?.isPrivate == true)
    }

    PaneSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(host, onDone = onDismiss)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val connection = connectionFor(tab?.security ?: SecurityState.Unknown, url, colors.positive, colors.destructive, colors.warning)
            GroupedSection(separatorInset = 57.dp) {
                row {
                    ListRow(
                        title = connection.title,
                        subtitle = connection.detail,
                        leading = { IconTile(connection.icon, connection.tint) },
                    )
                }
                if (isWeb) {
                    row {
                        ListRow(
                            title = "Trackers Blocked",
                            subtitle = "Trackers follow you from site to site. Pane stops the known ones.",
                            value = (tab?.trackersBlocked ?: 0).toString(),
                            leading = { IconTile(PaneIcons.ShieldCheck, TileColors.teal) },
                        )
                    }
                }
            }

            if (isWeb) {
                GroupedSection(
                    header = "Permissions",
                    footer = if (grants.isEmpty()) "Permissions you allow or deny for $host appear here." else null,
                    separatorInset = 57.dp,
                ) {
                    grants.forEach { grant ->
                        row {
                            val (icon, tint) = permissionIcon(grant.kind)
                            ToggleRow(
                                title = PermissionText.settingLabel(grant.kind, grant.permission.thirdPartyOrigin),
                                checked = grant.value == ContentPermission.VALUE_ALLOW,
                                onCheckedChange = { allow ->
                                    setGrant(container, grant.permission, allow)
                                    grants = grants.map {
                                        if (it === grant) {
                                            SiteGrant(it.permission, it.kind, if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
                                        } else {
                                            it
                                        }
                                    }
                                },
                                leading = { IconTile(icon, tint) },
                            )
                        }
                    }
                    if (grants.isNotEmpty()) {
                        row {
                            ActionRow("Reset Permissions", onClick = {
                                grants.forEach { resetGrant(container, it.permission) }
                                grants = emptyList()
                                toasts.show("Permissions reset for $host", PaneIcons.Check)
                            })
                        }
                    }
                }

                GroupedSection(footer = "Signs you out of $host and removes what it stored on this device.") {
                    row { ActionRow("Clear Cookies and Site Data", onClick = { confirmClear = true }, destructive = true) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    PaneAlert(
        visible = confirmClear,
        title = "Clear Cookies and Site Data?",
        message = "You’ll be signed out of $host, and it will forget your preferences.",
        actions = listOf(
            AlertAction("Cancel", AlertStyle.Cancel) { confirmClear = false },
            AlertAction("Clear", AlertStyle.Destructive) {
                confirmClear = false
                val siteHost = UrlInput.hostOf(url)
                if (siteHost != null) {
                    clearSiteData(container, siteHost)
                    val id = tabId
                    val reload: (() -> Unit)? = if (id != null) ({ container.sessions.reload(id) }) else null
                    toasts.show("Cleared data for $host", PaneIcons.Trash, if (reload != null) "Reload" else null, reload)
                }
            },
        ),
        onDismissRequest = { confirmClear = false },
    )
}

private fun connectionFor(security: SecurityState, url: String, secure: Color, insecure: Color, mixed: Color): Connection = when {
    url.isEmpty() || url.startsWith("about:") || url.startsWith("moz-extension:") ->
        Connection("Pane Page", "This page is part of Pane and never leaves your device.", PaneIcons.Info, TileColors.gray)
    security == SecurityState.Secure ->
        Connection("Connection Is Secure", "Passwords and card numbers you send to this site are encrypted.", PaneIcons.LockFill, secure)
    security == SecurityState.Broken ->
        Connection("Partly Secure", "Some parts of this page, like images or scripts, load without encryption.", PaneIcons.Warning, mixed)
    security == SecurityState.Insecure || url.startsWith("http://", ignoreCase = true) ->
        Connection("Not Secure", "Don’t enter passwords or card numbers here. Others on the network could see them.", PaneIcons.Warning, insecure)
    else -> Connection("Checking Connection…", "The page is still loading.", PaneIcons.Lock, TileColors.gray)
}

/** The site's remembered permissions, most useful first; muted autoplay is always on, so it's left out. */
private suspend fun loadGrants(container: AppContainer, url: String, private: Boolean): List<SiteGrant> {
    val stored = suspendCancellableCoroutine<List<ContentPermission>> { cont ->
        container.runtime.storageController.getPermissions(url, private).accept(
            { list -> if (cont.isActive) cont.resume(list?.filterNotNull().orEmpty()) },
            { _ -> if (cont.isActive) cont.resume(emptyList()) },
        )
    }
    return stored.mapNotNull { permission ->
        val kind = SitePermissions.fromGecko(permission.permission) ?: return@mapNotNull null
        if (kind == SitePermission.AutoplayMuted || permission.value == ContentPermission.VALUE_PROMPT) return@mapNotNull null
        SiteGrant(permission, kind, permission.value)
    }.sortedBy { it.kind.ordinal }
}

private fun setGrant(container: AppContainer, permission: ContentPermission, allow: Boolean) {
    container.runtime.storageController.setPermission(
        permission,
        if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY,
    )
}

private fun resetGrant(container: AppContainer, permission: ContentPermission) {
    container.runtime.storageController.setPermission(permission, ContentPermission.VALUE_PROMPT)
}

// The flags parameter is a bit field; GeckoView's annotation just doesn't say so.
@SuppressLint("WrongConstant")
private fun clearSiteData(container: AppContainer, host: String) {
    // Gecko widens the host to its registrable domain, so cookies set for the parent domain go too.
    val flags = StorageController.ClearFlags.COOKIES or
        StorageController.ClearFlags.DOM_STORAGES or
        StorageController.ClearFlags.AUTH_SESSIONS or
        StorageController.ClearFlags.ALL_CACHES
    container.runtime.storageController.clearDataFromBaseDomain(host, flags)
}
