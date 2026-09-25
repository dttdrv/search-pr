package app.pane.browser.ui.prompts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.engine.EngineEvent
import app.pane.browser.extensions.ExtensionsManager
import app.pane.browser.engine.PromptRequest
import app.pane.browser.engine.prompts.AndroidPermissionRequest
import app.pane.browser.engine.prompts.AuthRequest
import app.pane.browser.engine.prompts.BeforeUnloadRequest
import app.pane.browser.engine.prompts.ChoiceRequest
import app.pane.browser.engine.prompts.ColorRequest
import app.pane.browser.engine.prompts.ContentPermissionRequest
import app.pane.browser.engine.prompts.ContextMenuRequest
import app.pane.browser.engine.prompts.DateTimeRequest
import app.pane.browser.engine.prompts.ExternalAppRequest
import app.pane.browser.engine.prompts.ExternalApps
import app.pane.browser.engine.prompts.FileRequest
import app.pane.browser.engine.prompts.MediaPermissionRequest
import app.pane.browser.engine.prompts.PopupRequest
import app.pane.browser.engine.prompts.PromptEnvironment
import app.pane.browser.engine.prompts.RedirectRequest
import app.pane.browser.engine.prompts.RepostRequest
import app.pane.browser.engine.prompts.ScriptDialogRequest
import app.pane.browser.engine.prompts.ShareRequest
import app.pane.browser.engine.prompts.Uploads
import app.pane.browser.ui.browser.BrowserChrome
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.icons.PaneIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders the page prompts waiting in the queue: dialogs, pickers, permission sheets, context
 * menus and blocked pop-up banners.
 *
 * Only the first request for the selected tab (or for no tab) is shown; prompts from background
 * tabs wait until their tab comes forward. Consecutive prompts don't overlap: the current one
 * finishes leaving before the next arrives. Also turns one-off engine events (links to other
 * apps, tabs opened in the background, crashes) into alerts and toasts.
 */
@Composable
fun PromptHost() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val toasts = LocalToasts.current
    val queue = container.prompts

    LaunchedEffect(container) {
        // Idempotent; the container installs it too so callbacks before the first frame see real settings.
        PromptEnvironment.install(container.runtime, container.settings, container.store)
    }
    LaunchedEffect(container) {
        container.sessions.events.collect { event -> onEngineEvent(container, context, toasts, event) }
    }

    val requests by queue.requests.collectAsStateWithLifecycle()
    val browserState by container.store.state.collectAsStateWithLifecycle()
    // Only the selected tab matters here; progress ticks on other tabs shouldn't recompose prompts.
    val selectedTabId by remember { derivedStateOf { browserState.selectedTabId } }
    // Extension popups and options pages have no tab of their own.
    // A locked private tab's prompts wait behind the lock rather than showing its content over it.
    val pageLocked by BrowserChrome.pageLocked.collectAsStateWithLifecycle()
    val foreground = requests.filter {
        it.tabId == null || (it.tabId == selectedTabId && !pageLocked) || it.tabId == ExtensionsManager.AUX_PROMPT_OWNER
    }
    // Blocked pop-up banners don't stop the page, so they get their own lane and never hold up a dialog.
    val banner = rememberPresentation(foreground.firstOrNull { it.isBanner })
    val modal = rememberPresentation(foreground.firstOrNull { !it.isBanner })

    // System pickers and permission dialogs. Registered unconditionally so a result still finds
    // its request after the activity is recreated.
    val appContext = context.applicationContext
    var pendingFileId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingPermissionId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun pending(id: Long?): PromptRequest? = id?.let { wanted -> requests.firstOrNull { it.id == wanted } }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pending(pendingFileId) as? FileRequest
        pendingFileId = null
        if (request != null) deliverFiles(container, appContext, request, listOfNotNull(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val request = pending(pendingFileId) as? FileRequest
        pendingFileId = null
        if (request != null) deliverFiles(container, appContext, request, uris)
    }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val request = pending(pendingPermissionId) as? AndroidPermissionRequest
        pendingPermissionId = null
        if (request != null) {
            val granted = request.permissions.all { isGranted(appContext, it) }
            request.complete(granted)
            queue.remove(request)
            // Only point to Settings when Android no longer shows its own dialog.
            val blocked = activity != null && request.permissions.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (!granted && blocked) toasts.show(deniedMessage(request.permissions), PaneIcons.Warning, "Settings") { openAppSettings(context) }
        }
    }

    banner.shown?.let { request ->
        key(request.id) {
            when (request) {
                is PopupRequest -> PopupBanner(request, banner.visible) { queue.remove(request) }
                is RedirectRequest -> RedirectBanner(request, banner.visible) { queue.remove(request) }
                else -> Unit
            }
        }
    }

    val request = modal.shown ?: return
    val visible = modal.visible
    val onDone: () -> Unit = { queue.remove(request) }
    key(request.id) {
        when (request) {
            is ScriptDialogRequest<*> -> ScriptDialog(request, visible, onDone)
            is AuthRequest -> AuthDialog(request, visible, onDone)
            is RepostRequest -> RepostDialog(request, visible, onDone)
            is BeforeUnloadRequest -> BeforeUnloadDialog(request, visible, onDone)
            is ExternalAppRequest -> ExternalAppDialog(request, visible, onDone)
            is ChoiceRequest -> ChoiceSheet(request, visible, onDone)
            is ColorRequest -> ColorSheet(request, visible, onDone)
            is DateTimeRequest -> DateTimeSheet(request, visible, onDone)
            is ContentPermissionRequest -> ContentPermissionSheet(request, visible, onDone)
            is MediaPermissionRequest -> MediaPermissionSheet(request, visible, onDone)
            is ContextMenuRequest -> ContextMenuSheet(request, visible, onDone)
            is FileRequest -> LaunchedEffect(Unit) {
                if (pendingFileId == request.id) return@LaunchedEffect
                pendingFileId = request.id
                val launched = runCatching {
                    if (request.allowsMultiple) pickMany.launch(request.mimeTypes) else pickOne.launch(request.mimeTypes)
                }.isSuccess
                if (!launched) {
                    pendingFileId = null
                    request.dismiss()
                    onDone()
                    toasts.show("No app can choose files", PaneIcons.Warning)
                }
            }
            is AndroidPermissionRequest -> LaunchedEffect(Unit) {
                if (pendingPermissionId == request.id) return@LaunchedEffect
                val missing = request.permissions.filterNot { isGranted(appContext, it) }
                if (missing.isEmpty()) {
                    request.complete(true)
                    onDone()
                    return@LaunchedEffect
                }
                pendingPermissionId = request.id
                if (runCatching { askPermissions.launch(missing.toTypedArray()) }.isFailure) {
                    pendingPermissionId = null
                    request.complete(false)
                    onDone()
                }
            }
            is ShareRequest -> LaunchedEffect(Unit) {
                request.finish(share(context, request))
                onDone()
            }
            // Other features render their own request types (extension prompts, for one).
            else -> Unit
        }
    }
}

/** What a lane is showing: [shown] lags the queue so a leaving prompt can finish animating out. */
@Stable
private class Presentation {
    var shown by mutableStateOf<PromptRequest?>(null)
    var visible by mutableStateOf(false)
}

@Composable
private fun rememberPresentation(target: PromptRequest?): Presentation {
    val presentation = remember { Presentation() }
    LaunchedEffect(target?.id) {
        val current = presentation.shown
        if (current != null && current.id != target?.id) {
            presentation.visible = false
            delay(exitDurationOf(current))
        }
        presentation.shown = target
        presentation.visible = target != null
    }
    return presentation
}

private val PromptRequest.isBanner: Boolean get() = this is PopupRequest || this is RedirectRequest

/** Answers a file input with private copies of what was picked; nothing picked cancels it. */
private fun deliverFiles(container: AppContainer, context: Context, request: FileRequest, uris: List<Uri>) {
    if (uris.isEmpty()) {
        request.dismiss()
        container.prompts.remove(request)
        return
    }
    container.scope.launch {
        request.pick(context, Uploads.stage(context, uris))
        container.prompts.remove(request)
    }
}

/** "Open in “Maps”?" before a page hands a link to another app. */
@Composable
private fun ExternalAppDialog(request: ExternalAppRequest, visible: Boolean, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val message = buildString {
        append(request.target)
        if (request.isPrivate) append("\n\nThe other app won’t know you’re browsing privately.")
    }
    PaneAlert(
        visible = visible,
        title = request.appLabel?.let { "Open in “$it”?" } ?: "Open in Another App?",
        message = message,
        actions = listOf(
            AlertAction("Cancel", AlertStyle.Cancel) { onDone() },
            AlertAction("Open") {
                onDone()
                openOrFallBack(container, context, toasts, request)
            },
        ),
        onDismissRequest = onDone,
    )
}

private fun onEngineEvent(container: AppContainer, context: Context, toasts: ToastState, event: EngineEvent) {
    when (event) {
        // Links that fire without a tap are how redirect spam launches apps; drop them silently.
        is EngineEvent.ExternalLink -> if (event.userGesture) openExternally(container, context, toasts, event)
        is EngineEvent.OpenedInBackground -> toasts.show("Opened in new tab", PaneIcons.Tabs, "Show") { container.browser.select(event.tabId) }
        else -> Unit
    }
}

private fun openExternally(container: AppContainer, context: Context, toasts: ToastState, event: EngineEvent.ExternalLink) {
    val tabId = event.tabId
    val private = container.store.state.value.tab(tabId)?.isPrivate == true
    val intent = ExternalApps.intentFor(event.uri)
    if (intent == null) {
        fallBack(container, toasts, tabId, event.fallbackUrl)
        return
    }
    val handler = ExternalApps.handlerFor(context, intent)
    if (handler != ExternalApps.Handler.None) {
        val label = (handler as? ExternalApps.Handler.App)?.label
        container.prompts.enqueue(ExternalAppRequest(tabId, intent, label, event.fallbackUrl, private, ExternalApps.describe(intent, event.uri)))
        return
    }
    // Nothing installed can open it: the page's own fallback, else the app's store page.
    val storeIntent = ExternalApps.storeIntentFor(intent)
    val store = storeIntent?.let { ExternalApps.handlerFor(context, it) } as? ExternalApps.Handler.App
    if (event.fallbackUrl == null && storeIntent != null && store != null) {
        container.prompts.enqueue(ExternalAppRequest(tabId, storeIntent, store.label, null, private, "The app for this link isn’t installed."))
    } else {
        fallBack(container, toasts, tabId, event.fallbackUrl)
    }
}

private fun openOrFallBack(container: AppContainer, context: Context, toasts: ToastState, request: ExternalAppRequest) {
    if (ExternalApps.launch(context, request.intent)) return
    // Android 11+ hides apps we haven't declared, so "not installed" can only show up now.
    val store = if (request.fallbackUrl == null) ExternalApps.storeIntentFor(request.intent) else null
    if (store != null && ExternalApps.launch(context, store)) return
    fallBack(container, toasts, request.tabId, request.fallbackUrl)
}

private fun fallBack(container: AppContainer, toasts: ToastState, tabId: String?, fallbackUrl: String?) {
    if (tabId != null && fallbackUrl != null) {
        container.sessions.load(tabId, fallbackUrl)
    } else {
        toasts.show("No app can open this link", PaneIcons.OpenExternal)
    }
}

/** `navigator.share()` through the Android share sheet. True if the sheet opened. */
private fun share(context: Context, request: ShareRequest): Boolean {
    val text = listOfNotNull(request.text, request.uri).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    if (text.isEmpty()) return false
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    request.title?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
    return runCatching { context.startActivity(Intent.createChooser(send, null)) }.isSuccess
}

/** How long a prompt takes to leave, so the next one doesn't land on top of it. */
private fun exitDurationOf(request: PromptRequest): Long = when (request) {
    is ScriptDialogRequest<*>, is AuthRequest, is RepostRequest, is BeforeUnloadRequest, is ExternalAppRequest -> 190L
    is ChoiceRequest, is ColorRequest, is DateTimeRequest, is ContentPermissionRequest,
    is MediaPermissionRequest, is ContextMenuRequest, is PopupRequest, is RedirectRequest,
    -> 340L
    else -> 0L
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun deniedMessage(permissions: List<String>): String {
    val camera = permissions.any { it.endsWith(".CAMERA") }
    val microphone = permissions.any { it.endsWith(".RECORD_AUDIO") }
    return when {
        camera && microphone -> "Allow camera and microphone for Pane in Settings"
        camera -> "Allow camera access for Pane in Settings"
        microphone -> "Allow microphone access for Pane in Settings"
        permissions.any { it.contains("LOCATION") } -> "Allow location access for Pane in Settings"
        permissions.any { it.endsWith(".POST_NOTIFICATIONS") } -> "Allow notifications for Pane in Settings"
        else -> "Pane needs a permission; allow it in Settings"
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}
