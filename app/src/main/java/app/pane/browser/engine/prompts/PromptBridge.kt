package app.pane.browser.engine.prompts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import app.pane.browser.engine.PromptQueue
import app.pane.browser.engine.SitePermissionStore
import app.pane.browser.settings.SettingsStore
import app.pane.core.prompts.DialogThrottle
import app.pane.core.prompts.DialogVerdict
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.PromptText
import app.pane.core.prompts.SitePermission
import app.pane.core.prompts.TemporaryDecisions
import app.pane.core.tabs.BrowserStore
import app.pane.core.tabs.TabState
import app.pane.core.url.UrlInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One tab's end of everything the page can ask the user: the WebView chrome client's dialogs,
 * HTTP auth, permission requests, the file picker and blocked pop-ups, turned into [PromptQueue]
 * requests the prompt UI renders (and, for the picker, the system's own UI).
 *
 * Every WebView callback handed in here is completed exactly once: by the user, by the page
 * moving on ([cancelAll]) or immediately when there is nothing to show (tab gone, dialogs
 * silenced). Call all of it on the main thread.
 */
class PromptBridge(
    private val tabId: String,
    private val queue: PromptQueue,
    private val store: BrowserStore,
    private val permissions: SitePermissionStore,
    private val settings: SettingsStore,
) {
    private val throttle = DialogThrottle()

    /** Answers given without "Remember" hold for a while in this tab, so a page can't nag. */
    private val temporary = TemporaryDecisions<String>()

    /** What this bridge has in the queue (or waiting on the system), so [cancelAll] touches only its own. */
    private val live: MutableSet<WebViewPromptRequest> = ConcurrentHashMap.newKeySet()

    /** Identical permission requests that arrive while the first one is open share its answer. */
    private val waiting = HashMap<String, MutableList<(Boolean) -> Unit>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var chooser: FileChooserSession? = null

    private val tab: TabState? get() = store.state.value.tab(tabId)
    private val isPrivate: Boolean get() = tab?.isPrivate == true

    // region Script dialogs

    fun jsAlert(url: String, message: String, result: JsResult): Boolean =
        scriptDialog(url, suppress = { result.confirm() }) { source, optOut, onOptOut ->
            AlertRequest(tabId, result, source, message, optOut, onOptOut)
        }

    fun jsConfirm(url: String, message: String, result: JsResult): Boolean =
        scriptDialog(url, suppress = { result.cancel() }) { source, optOut, onOptOut ->
            ConfirmRequest(tabId, result, source, message, optOut, onOptOut)
        }

    fun jsPrompt(url: String, message: String, default: String?, result: JsPromptResult): Boolean =
        scriptDialog(url, suppress = { result.cancel() }) { source, optOut, onOptOut ->
            TextInputRequest(tabId, result, source, message, default.orEmpty(), optOut, onOptOut)
        }

    /** Leaving is the page's right unless the user says to stay; with no tab left to ask in, leave. */
    fun jsBeforeUnload(url: String, message: String, result: JsResult): Boolean {
        if (tab == null) {
            quietly { result.confirm() }
            return true
        }
        enqueue(BeforeUnloadRequest(tabId, result))
        return true
    }

    /** Shared path for alert/confirm/prompt, which a page can fire in a loop. */
    private fun scriptDialog(
        url: String,
        suppress: () -> Unit,
        create: (source: String?, offerOptOut: Boolean, onOptOut: () -> Unit) -> ScriptDialogRequest,
    ): Boolean {
        val tab = tab
        if (tab == null) {
            quietly(suppress)
            return true
        }
        val page = tab.url.ifBlank { url }
        val verdict = throttle.onDialogOpened(UrlInput.hostOf(page) ?: page)
        if (verdict == DialogVerdict.Suppress) {
            quietly(suppress)
            return true
        }
        val request = create(PromptText.dialogSource(null, url.ifBlank { page }), verdict == DialogVerdict.ShowWithOptOut) { throttle.block() }
        request.onSettled { throttle.onDialogClosed() }
        enqueue(request)
        return true
    }

    // endregion

    // region HTTP auth

    fun httpAuth(handler: HttpAuthHandler, host: String, realm: String) {
        val tab = tab
        if (tab == null) {
            quietly { handler.cancel() }
            return
        }
        val tabHost = UrlInput.hostOf(tab.url)
        val crossOrigin = tabHost != null && !tabHost.equals(host.substringBefore(':'), ignoreCase = true)
        val insecure = !crossOrigin && tab.url.startsWith("http://", ignoreCase = true)
        enqueue(AuthRequest(tabId, handler, host, realm, crossOrigin, insecure))
    }

    // endregion

    // region Permissions

    /**
     * Camera, microphone, protected media and MIDI. WebView can only take the whole request or
     * nothing (a partial grant counts as a refusal), so every part has to be allowed.
     */
    fun webPermissions(request: PermissionRequest) {
        val asked = request.resources.orEmpty().toList()
        val wanted = asked.mapNotNull { id -> Resource.entries.firstOrNull { it.id == id } }
        if (tab == null || wanted.isEmpty() || wanted.size != asked.size) {
            quietly { request.deny() }
            return
        }
        val origin = originOf(request.origin.toString())
        val host = PermissionText.displayHost(origin)
        val key = "web|$origin|${wanted.joinToString(",") { it.name }}"
        val camera = Resource.Camera in wanted
        val microphone = Resource.Microphone in wanted
        val title = when {
            camera || microphone -> PermissionText.mediaTitle(host, camera = camera, microphone = microphone)
            Resource.ProtectedMedia in wanted -> PermissionText.requestTitle(SitePermission.ProtectedContent, host)
            else -> "$host wants to use MIDI devices"
        }
        val android = buildList {
            if (camera) add(Manifest.permission.CAMERA)
            if (microphone) add(Manifest.permission.RECORD_AUDIO)
        }
        resolve(origin, key, wanted.map { it.kind }, title, sensitive = camera || microphone) { allowed ->
            if (!allowed) {
                quietly { request.deny() }
            } else {
                withAndroid(android, requireAll = true) { granted ->
                    quietly { if (granted) request.grant(request.resources) else request.deny() }
                }
            }
        }
    }

    fun geolocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (tab == null) {
            quietly { callback.invoke(origin, false, false) }
            return
        }
        val site = originOf(origin)
        val title = PermissionText.requestTitle(SitePermission.Location, PermissionText.displayHost(site))
        resolve(site, "geo|$site", listOf(SitePermission.Location), title, sensitive = false) { allowed ->
            if (!allowed) {
                quietly { callback.invoke(origin, false, false) }
            } else {
                // Either precision will do; the system offers both.
                val location = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                withAndroid(location, requireAll = false) { granted -> quietly { callback.invoke(origin, granted, false) } }
            }
        }
    }

    /**
     * Answers from what the user said before (never in a private tab, which keeps nothing), else
     * asks. [answer] runs once per caller, now or when the sheet is answered.
     */
    private fun resolve(
        origin: String,
        key: String,
        kinds: List<SitePermission?>,
        title: String,
        sensitive: Boolean,
        answer: (Boolean) -> Unit,
    ) {
        decided(origin, kinds, key)?.let { allowed ->
            answer(allowed)
            return
        }
        waiting[key]?.let { followers ->
            followers += answer
            return
        }
        waiting[key] = mutableListOf(answer)
        // Rememberable only when every part is something Site Settings can show and change.
        val canRemember = !isPrivate && kinds.all { it != null }
        enqueue(
            ContentPermissionRequest(tabId, origin, kinds.filterNotNull(), title, canRemember) { allowed, remember ->
                when {
                    // Withdrawn: the page moved on, so nothing is held against it.
                    remember == null -> Unit
                    remember -> {
                        kinds.filterNotNull().forEach { permissions.set(origin, it, allowed) }
                        temporary.forget(key)
                    }
                    // Allowing the camera or microphone always asks again; declining holds for a while.
                    allowed && sensitive -> temporary.forget(key)
                    else -> temporary.record(key, allowed)
                }
                waiting.remove(key).orEmpty().forEach { it(allowed) }
            },
        )
    }

    private fun decided(origin: String, kinds: List<SitePermission?>, key: String): Boolean? {
        if (!isPrivate) {
            val stored = kinds.map { kind -> kind?.let { permissions.get(origin, it) } }
            if (stored.any { it == false }) return false
            if (stored.all { it == true }) return true
        }
        return temporary.lookup(key)
    }

    /** Asks the system for what Android itself requires before the page can have the feature. */
    private fun withAndroid(needed: List<String>, requireAll: Boolean, then: (Boolean) -> Unit) {
        if (needed.isEmpty()) {
            then(true)
            return
        }
        enqueue(AndroidPermissionRequest(tabId, needed, requireAll, then))
    }

    private fun originOf(url: String): String = SitePermissionStore.normalize(url) ?: url

    /** The parts of a WebView permission request, and what they are called in Site Settings. */
    private enum class Resource(val id: String, val kind: SitePermission?) {
        Camera(PermissionRequest.RESOURCE_VIDEO_CAPTURE, SitePermission.Camera),
        Microphone(PermissionRequest.RESOURCE_AUDIO_CAPTURE, SitePermission.Microphone),
        ProtectedMedia(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID, SitePermission.ProtectedContent),

        /** Not in Site Settings: always asked, never remembered. */
        Midi(PermissionRequest.RESOURCE_MIDI_SYSEX, null),
    }

    // endregion

    // region File picker

    /**
     * `<input type=file>`: the system picker (through [PromptEnvironment.launchFileChooser]). The
     * choice is copied into the app's cache first, so it stays readable however long the page
     * waits to submit it. Returns true: [callback] is always completed, with null on cancel.
     */
    fun fileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        // WebView won't ask again until the previous chooser has been answered.
        chooser?.cancel()
        val launch = PromptEnvironment.launchFileChooser
        if (launch == null || tab == null) {
            quietly { callback.onReceiveValue(null) }
            return true
        }
        val session = FileChooserSession(callback)
        chooser = session
        val started = runCatching { launch(pickerIntent(params)) { resultCode, data -> session.onResult(resultCode, data) } }.isSuccess
        if (!started) session.cancel()
        return true
    }

    private inner class FileChooserSession(private val callback: ValueCallback<Array<Uri>>) {
        private val done = AtomicBoolean(false)
        private var staging: Job? = null

        fun cancel() {
            staging?.cancel()
            finish(null)
        }

        fun onResult(resultCode: Int, data: Intent?) {
            if (done.get()) return
            val picked = WebChromeClient.FileChooserParams.parseResult(resultCode, data)?.toList().orEmpty()
            if (picked.isEmpty()) {
                finish(null)
                return
            }
            staging = scope.launch {
                val copies = Uploads.stage(permissions.appContext, picked)
                // If any copy failed, hand over the originals: WebView can read content: URIs itself.
                finish((if (copies.size == picked.size) copies else picked).toTypedArray())
            }
        }

        private fun finish(files: Array<Uri>?) {
            if (!done.compareAndSet(false, true)) return
            if (chooser === this) chooser = null
            quietly { callback.onReceiveValue(files) }
        }
    }

    private fun pickerIntent(params: WebChromeClient.FileChooserParams): Intent {
        val tokens = params.acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val mimes = tokens.mapNotNull(::mimeOf).distinct()
        // An accept token we can't map would hide files the page wants; then don't narrow at all.
        val narrow = mimes.isNotEmpty() && mimes.size >= tokens.size
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (narrow && mimes.size == 1) mimes.first() else "*/*"
            if (narrow && mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    /** A full MIME type (including wildcards) stays as it is; `.pdf` becomes `application/pdf`; unknown extensions give null. */
    private fun mimeOf(token: String): String? {
        if ('/' in token) return token
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(token.removePrefix("*").removePrefix("."))
    }

    // endregion

    // region Pop-ups

    /** [open] opens the held-back window; it runs now when pop-ups are allowed, else if the user taps Allow. */
    fun blockedPopup(url: String, open: () -> Unit) {
        if (!settings.current.blockPopups) {
            open()
            return
        }
        if (tab == null) return
        // One banner per tab: a burst of pop-ups stays blocked without asking again.
        if (live.any { it is PopupRequest }) return
        enqueue(PopupRequest(tabId, url, open))
    }

    // endregion

    /** The page navigated or the tab closed: whatever it was waiting on gets the neutral answer. */
    fun cancelAll() {
        chooser?.cancel()
        live.toList().forEach { it.dismiss() }
    }

    private fun enqueue(request: WebViewPromptRequest) {
        live += request
        request.onSettled {
            live -= request
            queue.remove(request)
        }
        queue.enqueue(request)
    }
}
