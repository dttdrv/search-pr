package app.pane.browser.ui.prompts

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.prompts.SitePermissions
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.StatusDot
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.entrance
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.SitePermission
import app.pane.core.tabs.SecurityState
import app.pane.core.url.UrlInput
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.StorageController
import kotlin.coroutines.resume

/** How the connection reads: a plain line and its detail. [tone] is only coloured where it carries meaning. */
private class Connection(val title: String, val detail: String?, val tone: Tone, val locked: Boolean)

private enum class Tone { Normal, Caution, Danger }

/** A stored site permission and the value the user may just have changed it to. */
private class SiteGrant(val permission: ContentPermission, val kind: SitePermission, val value: Int)

/**
 * The sheet behind the lock mark: the host as a plain title, one line on the connection (with a red
 * light only when it is unsafe), trackers blocked, the site's remembered permissions (tap one to
 * switch it in place) and a way to wipe its cookies and storage.
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
        val connection = connectionFor(tab?.security ?: SecurityState.Unknown, url)
        val connectionColor = when (connection.tone) {
            Tone.Normal -> colors.secondaryLabel
            Tone.Caution -> colors.warning
            Tone.Danger -> colors.destructive
        }
        FadingColumn(Modifier.weight(1f, fill = false)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 2.dp).entrance(1),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(host, style = PaneTheme.type.title3, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // A red light only when the connection is not safe; a safe one just says so.
                        if (connection.tone != Tone.Normal) StatusDot(color = colors.signal, size = 6.dp)
                        Text(connection.title, style = PaneTheme.type.subheadline, color = connectionColor)
                    }
                }
                TextButton("Done", onClick = onDismiss, bold = true)
            }
            if (connection.detail != null) {
                Text(
                    connection.detail,
                    style = PaneTheme.type.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp).entrance(2),
                )
            }
            Spacer(Modifier.height(10.dp))

            if (isWeb) {
                Separator(Modifier.padding(vertical = 4.dp))
                FlatSection(entranceIndex = 3) {
                    row { FlatRow("Trackers Blocked", value = (tab?.trackersBlocked ?: 0).toString()) }
                }

                Separator(Modifier.padding(vertical = 4.dp))
                FlatSection(
                    header = "Permissions",
                    footer = if (grants.isEmpty()) "None saved yet." else "Tap to change.",
                    entranceIndex = 4,
                ) {
                    grants.forEach { grant ->
                        row {
                            val allowed = grant.value == ContentPermission.VALUE_ALLOW
                            FlatRow(
                                title = PermissionText.settingLabel(grant.kind, grant.permission.thirdPartyOrigin),
                                value = if (allowed) "Allowed" else "Blocked",
                                onClick = {
                                    val allow = !allowed
                                    setGrant(container, grant.permission, allow)
                                    grants = grants.map {
                                        if (it === grant) {
                                            SiteGrant(it.permission, it.kind, if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
                                        } else {
                                            it
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (grants.isNotEmpty()) {
                        row {
                            FlatRow("Reset Permissions", onClick = {
                                grants.forEach { resetGrant(container, it.permission) }
                                grants = emptyList()
                                toasts.show("Permissions reset for $host")
                            })
                        }
                    }
                }

                Separator(Modifier.padding(vertical = 4.dp))
                FlatSection(entranceIndex = 5 + grants.size.coerceAtMost(3)) {
                    row { FlatRow("Clear Site Data", titleColor = colors.destructive, onClick = { confirmClear = true }) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    PaneAlert(
        visible = confirmClear,
        title = "Clear Site Data?",
        message = "You’ll be signed out of $host.",
        actions = listOf(
            AlertAction("Cancel", AlertStyle.Cancel) { confirmClear = false },
            AlertAction("Clear", AlertStyle.Destructive) {
                confirmClear = false
                val siteHost = UrlInput.hostOf(url)
                if (siteHost != null) {
                    clearSiteData(container, siteHost)
                    val id = tabId
                    val reload: (() -> Unit)? = if (id != null) ({ container.sessions.reload(id) }) else null
                    toasts.show("Cleared data for $host", null, if (reload != null) "Reload" else null, reload)
                }
            },
        ),
        onDismissRequest = { confirmClear = false },
    )
}

private fun connectionFor(security: SecurityState, url: String): Connection = when {
    url.isEmpty() || url.startsWith("about:") || url.startsWith("moz-extension:") ->
        Connection("Pane page", null, Tone.Normal, locked = false)
    security == SecurityState.Secure ->
        Connection("Connection secure", null, Tone.Normal, locked = true)
    security == SecurityState.Broken ->
        Connection("Partly secure", "Some content loads unencrypted.", Tone.Caution, locked = false)
    security == SecurityState.Insecure || url.startsWith("http://", ignoreCase = true) ->
        Connection("Connection not secure", "Don’t enter passwords or card numbers.", Tone.Danger, locked = false)
    else -> Connection("Checking…", null, Tone.Normal, locked = false)
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
