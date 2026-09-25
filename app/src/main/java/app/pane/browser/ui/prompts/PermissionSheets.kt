package app.pane.browser.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.engine.prompts.ContentPermissionRequest
import app.pane.browser.engine.prompts.MediaPermissionRequest
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.SitePermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource

/** Icon and tile colour for a permission, shared with Site Settings. */
internal fun permissionIcon(permission: SitePermission): Pair<ImageVector, Color> = when (permission) {
    SitePermission.Location -> PaneIcons.Location to TileColors.blue
    SitePermission.Notifications -> PaneIcons.Bell to TileColors.red
    SitePermission.PersistentStorage -> PaneIcons.Download to TileColors.orange
    SitePermission.VirtualReality -> PaneIcons.Globe to TileColors.indigo
    SitePermission.Autoplay, SitePermission.AutoplayMuted -> PaneIcons.Forward to TileColors.purple
    SitePermission.ProtectedContent -> PaneIcons.Key to TileColors.gray
    SitePermission.Trackers -> PaneIcons.Shield to TileColors.teal
    SitePermission.StorageAccess -> PaneIcons.Globe to TileColors.purple
    SitePermission.LocalDevices -> PaneIcons.Phone to TileColors.green
    SitePermission.LocalNetwork -> PaneIcons.Desktop to TileColors.teal
    SitePermission.Camera -> PaneIcons.Camera to TileColors.green
    SitePermission.Microphone -> PaneIcons.Mic to TileColors.orange
}

/**
 * "example.com wants to use your location": hero tile, what allowing means, a Remember switch
 * (off by default in private tabs, where it only lasts until they close), Allow / Don't Allow.
 * Swiping the sheet away declines for now.
 */
@Composable
internal fun ContentPermissionSheet(request: ContentPermissionRequest, visible: Boolean, onDone: () -> Unit) {
    var keep by remember { mutableStateOf(!request.isPrivate) }
    val (icon, tint) = permissionIcon(request.kind)
    LaunchedEffect(request.id) { request.markShown() }

    PaneSheet(visible = visible, onDismiss = {
        request.deny(remember = false)
        onDone()
    }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PermissionHeader(icon, tint, request.title, request.explanation)
            GroupedSection(
                footer = if (request.isPrivate) "In private tabs, this is forgotten when you close them." else "You can change this later in Site Settings.",
            ) {
                row { ToggleRow("Remember for This Site", checked = keep, onCheckedChange = { keep = it }) }
            }
            DecisionButtons(
                onAllow = {
                    request.allow(remember = keep)
                    onDone()
                },
                onDeny = {
                    request.deny(remember = keep)
                    onDone()
                },
            )
        }
    }
}

/** Camera and microphone. With several devices of a kind, the user picks which one the site gets. */
@Composable
internal fun MediaPermissionSheet(request: MediaPermissionRequest, visible: Boolean, onDone: () -> Unit) {
    var camera by remember { mutableStateOf<MediaSource?>(request.video.firstOrNull()) }
    var microphone by remember { mutableStateOf<MediaSource?>(request.audio.firstOrNull()) }
    val (icon, tint) = permissionIcon(if (request.video.isNotEmpty()) SitePermission.Camera else SitePermission.Microphone)

    PaneSheet(visible = visible, onDismiss = {
        request.reject()
        onDone()
    }) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            PermissionHeader(icon, tint, request.title, PermissionText.explanation(SitePermission.Camera))
            if (request.video.size > 1) {
                GroupedSection(header = if (request.wantsScreen) "Share" else "Camera") {
                    request.video.forEachIndexed { index, source ->
                        row {
                            CheckRow(
                                title = PermissionText.cameraName(source.name, index),
                                selected = camera === source,
                                onClick = { camera = source },
                            )
                        }
                    }
                }
            }
            if (request.audio.size > 1) {
                GroupedSection(header = "Microphone") {
                    request.audio.forEachIndexed { index, source ->
                        row {
                            CheckRow(
                                title = PermissionText.microphoneName(source.name, index),
                                selected = microphone === source,
                                onClick = { microphone = source },
                            )
                        }
                    }
                }
            }
            DecisionButtons(
                onAllow = {
                    request.grant(camera, microphone)
                    onDone()
                },
                onDeny = {
                    request.reject()
                    onDone()
                },
            )
        }
    }
}

@Composable
private fun PermissionHeader(icon: ImageVector, tint: Color, title: String, message: String?) {
    val colors = PaneTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeroTile(icon, tint)
        Text(
            title,
            style = PaneTheme.type.title3,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (message != null) {
            Text(
                message,
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DecisionButtons(onAllow: () -> Unit, onDeny: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryButton("Allow", onClick = onAllow)
        PrimaryButton("Don’t Allow", onClick = onDeny, style = ButtonStyle.Tinted)
    }
}
