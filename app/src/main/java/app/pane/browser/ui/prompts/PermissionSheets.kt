package app.pane.browser.ui.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pane.browser.engine.prompts.ContentPermissionRequest
import app.pane.browser.engine.prompts.MediaPermissionRequest
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.theme.entrance
import app.pane.core.prompts.PermissionText
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource

/**
 * "example.com wants to use your location": the title says it all, then a Remember switch (off by
 * default in private tabs, where it only lasts until they close) and Allow / Don't Allow.
 * Swiping the sheet away declines for now.
 */
@Composable
internal fun ContentPermissionSheet(request: ContentPermissionRequest, visible: Boolean, onDone: () -> Unit) {
    var keep by remember { mutableStateOf(!request.isPrivate) }
    LaunchedEffect(request.id) { request.markShown() }

    PaneSheet(visible = visible, onDismiss = {
        request.deny(remember = false)
        onDone()
    }) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            SheetTitle(request.title, Modifier.entrance(1))
            GlassSection(
                modifier = Modifier.entrance(2),
                footer = if (request.isPrivate) "Forgotten when private tabs close." else null,
            ) {
                row { ToggleRow("Remember this site", checked = keep, onCheckedChange = { keep = it }) }
            }
        }
        SheetButtons(
            primary = "Allow",
            onPrimary = {
                request.allow(remember = keep)
                onDone()
            },
            modifier = Modifier.entrance(3),
            secondary = "Don’t Allow",
            onSecondary = {
                request.deny(remember = keep)
                onDone()
            },
        )
    }
}

/** Camera and microphone. With several devices of a kind, the user picks which one the site gets. */
@Composable
internal fun MediaPermissionSheet(request: MediaPermissionRequest, visible: Boolean, onDone: () -> Unit) {
    var camera by remember { mutableStateOf<MediaSource?>(request.video.firstOrNull()) }
    var microphone by remember { mutableStateOf<MediaSource?>(request.audio.firstOrNull()) }

    PaneSheet(visible = visible, onDismiss = {
        request.reject()
        onDone()
    }) {
        FadingColumn(Modifier.weight(1f, fill = false)) {
            SheetTitle(request.title, Modifier.entrance(1))
            if (request.video.size > 1) {
                GlassSection(header = if (request.wantsScreen) "Share" else "Camera", entranceIndex = 2) {
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
                GlassSection(header = "Microphone", entranceIndex = 3) {
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
        }
        SheetButtons(
            primary = "Allow",
            onPrimary = {
                request.grant(camera, microphone)
                onDone()
            },
            modifier = Modifier.entrance(4),
            secondary = "Don’t Allow",
            onSecondary = {
                request.reject()
                onDone()
            },
        )
    }
}
