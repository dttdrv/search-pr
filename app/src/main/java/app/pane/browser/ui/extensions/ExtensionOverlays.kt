package app.pane.browser.ui.extensions

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.extensions.ExtensionEvent
import app.pane.browser.extensions.ExtensionPopup
import app.pane.browser.extensions.InstallPrompt
import app.pane.browser.extensions.PermissionPrompt
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.ButtonStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.PaneSheet
import app.pane.browser.ui.components.PrimaryButton
import app.pane.browser.ui.components.Separator
import app.pane.browser.ui.components.TextButton
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.theme.PaneTheme
import app.pane.browser.ui.theme.rememberHaptics
import org.mozilla.geckoview.GeckoView

/** Install/permission prompts and extension popups, drawn above everything. */
@Composable
fun ExtensionOverlays() {
    val container = LocalAppContainer.current
    val manager = container.extensions
    val navigator = LocalNavigator.current
    val toasts = LocalToasts.current
    val haptics = rememberHaptics()
    val prompts by manager.prompts.collectAsStateWithLifecycle()
    val popup by manager.popup.collectAsStateWithLifecycle()

    LaunchedEffect(manager) {
        manager.events.collect { event ->
            when (event) {
                is ExtensionEvent.OpenOptions -> openExtensionOptions(container, navigator, event.extensionId)
                is ExtensionEvent.Installed -> {
                    haptics.confirm()
                    toasts.show("“${event.name}” was added", PaneIcons.Check)
                }
                is ExtensionEvent.Failed -> {
                    haptics.reject()
                    toasts.show(event.message, PaneIcons.Warning)
                }
                is ExtensionEvent.Message -> toasts.show(event.message)
                is ExtensionEvent.TabOpened -> if (!navigator.isEmpty) {
                    toasts.show("Opened in a new tab", PaneIcons.Tabs, actionLabel = "Show") { navigator.closeAll() }
                }
            }
        }
    }

    val head = prompts.firstOrNull()
    InstallSheet(head as? InstallPrompt)
    PermissionAlert(head as? PermissionPrompt)
    PopupSheet(popup, onDismiss = manager::dismissPopup)
}

@Composable
private fun InstallSheet(prompt: InstallPrompt?) {
    val shown = rememberRetained(prompt)
    PaneSheet(visible = prompt != null, onDismiss = { prompt?.dismiss() }) {
        if (shown != null) {
            key(shown.id) { InstallContent(shown) }
        }
    }
}

@Composable
private fun ColumnScope.InstallContent(prompt: InstallPrompt) {
    val colors = PaneTheme.colors
    val icon by prompt.icon.collectAsStateWithLifecycle()
    var allowPrivate by remember { mutableStateOf(false) }
    var shareTechnical by remember { mutableStateOf(false) }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExtensionIcon(icon, 72.dp)
            Spacer(Modifier.height(14.dp))
            Text("Add “${prompt.name}”?", style = PaneTheme.type.title2, color = colors.label, textAlign = TextAlign.Center)
            val byline = listOfNotNull(prompt.creator?.let { "by $it" }, prompt.version?.takeIf { it.isNotBlank() }?.let { "version $it" })
            if (byline.isNotEmpty()) {
                Text(
                    byline.joinToString(" · "),
                    style = PaneTheme.type.subheadline,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (prompt.permissions.isEmpty()) {
            Text(
                "It doesn’t need any special permissions.",
                style = PaneTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            GroupedSection(header = "It will be able to", separatorInset = 46.dp) {
                prompt.permissions.forEach { sentence ->
                    row {
                        ListRow(
                            title = sentence,
                            leading = {
                                Icon(PaneIcons.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            },
                            showChevron = false,
                        )
                    }
                }
            }
        }
        if (prompt.canRunInPrivate || prompt.offersTechnicalData) {
            GroupedSection(footer = prompt.dataCollection) {
                if (prompt.canRunInPrivate) {
                    row {
                        ToggleRow(
                            title = "Allow in Private Tabs",
                            subtitle = "It could see what you do in private tabs.",
                            checked = allowPrivate,
                            onCheckedChange = { allowPrivate = it },
                        )
                    }
                }
                if (prompt.offersTechnicalData) {
                    row {
                        ToggleRow(
                            title = "Share Technical Data",
                            subtitle = "Send technical and interaction data to the developer.",
                            checked = shareTechnical,
                            onCheckedChange = { shareTechnical = it },
                        )
                    }
                }
            }
        } else {
            prompt.dataCollection?.let { text ->
                Text(
                    text,
                    style = PaneTheme.type.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp)) {
        PrimaryButton(text = "Add", onClick = { prompt.add(allowPrivate, shareTechnical) })
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = "Cancel", style = ButtonStyle.Plain, onClick = { prompt.dismiss() })
    }
}

@Composable
private fun PermissionAlert(prompt: PermissionPrompt?) {
    val shown = rememberRetained(prompt)
    val update = shown?.kind == PermissionPrompt.Kind.Update
    val title = shown?.let {
        if (update) "“${it.name}” needs new permissions" else "“${it.name}” requests more access"
    }
    val message = shown?.let { p ->
        val intro = if (update) "To update, it needs to be able to:" else "It would be able to:"
        if (p.permissions.isEmpty()) {
            if (update) "The update changes what it can do." else "It asks for additional access."
        } else {
            intro + "\n\n" + p.permissions.joinToString("\n") { "• $it" }
        }
    }
    PaneAlert(
        visible = prompt != null,
        title = title,
        message = message,
        actions = listOf(
            AlertAction(if (update) "Not Now" else "Deny", AlertStyle.Cancel) { prompt?.dismiss() },
            AlertAction(if (update) "Update" else "Allow") { prompt?.allow() },
        ),
        onDismissRequest = { prompt?.dismiss() },
    )
}

@Composable
private fun PopupSheet(popup: ExtensionPopup?, onDismiss: () -> Unit) {
    val shown = rememberRetained(popup)
    val colors = PaneTheme.colors
    val background = colors.background.toArgb()
    PaneSheet(visible = popup != null, onDismiss = onDismiss) {
        if (shown != null) {
            PopupHeader(shown, onDone = onDismiss)
            Separator()
            // A new popup gets a new view; the old one releases and closes its session.
            key(shown) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f),
                    factory = { context ->
                        GeckoView(context).also { view ->
                            // A TextureView moves and clips with the sheet; a SurfaceView would not.
                            view.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                            view.coverUntilFirstPaint(background)
                            view.setSession(shown.session)
                        }
                    },
                    onRelease = { view ->
                        try {
                            view.releaseSession()
                        } catch (e: Exception) {
                            Log.w("ExtensionPopup", "Couldn't release the popup", e)
                        }
                        shown.close()
                    },
                )
            }
        }
    }
}

@Composable
private fun PopupHeader(popup: ExtensionPopup, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExtensionIcon(popup.icon, 24.dp)
        Box(Modifier.weight(1f)) {
            Text(
                popup.title,
                style = PaneTheme.type.headline,
                color = PaneTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton("Done", onClick = onDone, bold = true)
    }
}
