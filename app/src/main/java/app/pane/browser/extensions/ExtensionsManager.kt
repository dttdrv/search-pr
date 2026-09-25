package app.pane.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.pane.browser.engine.BrowserController
import app.pane.browser.engine.SessionManager
import app.pane.browser.engine.WebFetcher
import app.pane.core.extensions.ExtensionPermissions
import app.pane.core.extensions.ExtensionUpdates
import app.pane.core.security.LinkDecision
import app.pane.core.security.LinkPolicy
import app.pane.core.tabs.BrowserStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import kotlin.math.roundToInt

/** An extension's toolbar button as it applies to the selected tab. */
data class ExtensionActionUi(
    val extensionId: String,
    val title: String,
    val icon: ImageBitmap?,
    val badgeText: String?,
    val enabled: Boolean,
)

/**
 * WebExtension support: installing from AMO or files, enabling/disabling, browser actions and
 * popups, and Pane's built-in helper extension (reader mode, page theme colour).
 *
 * Owns every engine-facing delegate for extensions, so the rest of the app deals in plain snapshots
 * ([InstalledExtension], [ExtensionActionUi]) and prompts it answers. Anything Gecko waits on is
 * completed exactly once, and engine failures surface as [events] instead of exceptions.
 *
 * Everything here runs on the main thread, where GeckoView calls its delegates.
 */
class ExtensionsManager(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val sessions: SessionManager,
    private val browser: BrowserController,
    private val store: BrowserStore,
    private val fetcher: WebFetcher,
    private val scope: CoroutineScope,
) {
    private val controller: WebExtensionController get() = runtime.webExtensionController
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val iconPx = (ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(48)
    private val actionIconPx = (ACTION_ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(32)

    /** Everything the engine reported, built-ins included, by id. */
    private val extensions = LinkedHashMap<String, WebExtension>()
    private val icons = HashMap<String, ImageBitmap>()
    private val iconsLoading = HashSet<String>()
    private val liveSessions = LinkedHashMap<String, GeckoSession>()
    private val sessionDelegates = HashMap<String, SessionDelegates>()

    /** Extensions the user just approved; their "added" confirmation is shown once installed. */
    private val pendingAdds = HashSet<String>()
    private var started = false

    /** The add-on store client (search, listings, icons). */
    val amo = AmoClient(runtime, fetcher)

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())

    /** User-visible installed extensions, sorted by name. Built-in ones are hidden. */
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until the engine has listed installed extensions once, so screens don't flash "empty". */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _installing = MutableStateFlow<Set<String>>(emptySet())

    /** Installs in flight, keyed by the URL passed to [install] or the picked file's URI. */
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val _prompts = MutableStateFlow<List<ExtensionPrompt>>(emptyList())

    /** Requests waiting on the user, oldest first; the UI shows the head. */
    val prompts: StateFlow<List<ExtensionPrompt>> = _prompts.asStateFlow()

    private val _popup = MutableStateFlow<ExtensionPopup?>(null)

    /** The open browser-action popup, if any. */
    val popup: StateFlow<ExtensionPopup?> = _popup.asStateFlow()

    private val _events = MutableSharedFlow<ExtensionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ExtensionEvent> = _events.asSharedFlow()

    private val _recordedSlugs = MutableStateFlow(loadRecordedSlugs())

    /** AMO slug → extension id for installs made in Pane, to recognise curated listings. */
    val recordedSlugs: StateFlow<Map<String, String>> = _recordedSlugs.asStateFlow()

    private val actionTracker = ActionTracker(actionIconPx)
    private val helper = HelperBridge(store, sessions, prefs) { _events.tryEmit(ExtensionEvent.Message(it)) }

    /** Browser/page actions of enabled extensions for the selected tab, shown in the menu. */
    val actions: StateFlow<List<ExtensionActionUi>> = combine(
        store.state.map { it.selectedTabId }.distinctUntilChanged(),
        actionTracker.defaults,
        actionTracker.perTab,
        _installed,
    ) { tabId, defaults, perTab, installed -> ActionTracker.toUi(tabId, defaults, perTab, installed) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Called once after the runtime is up. */
    fun start() {
        if (started) return
        started = true
        try {
            controller.setPromptDelegate(promptDelegate)
            controller.setAddonManagerDelegate(addonDelegate)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't register extension delegates", e)
        }
        sessions.addObserver(sessionObserver)
        installHelper()
        refresh()
        scope.launch {
            store.state
                .map { state -> state.tabs.associate { it.id to it.url } }
                .distinctUntilChanged()
                .collect { helper.onTabUrls(it) }
        }
        scope.launch {
            store.state.map { it.selectedTabId }.distinctUntilChanged().collect { actionTracker.onTabSelected(it) }
        }
        scope.launch {
            _loaded.first { it }
            delay(UPDATE_CHECK_DELAY_MS)
            if (ExtensionUpdates.isDue(prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L), System.currentTimeMillis())) {
                checkForUpdates(interactive = false)
            }
        }
    }

    // region Queries

    fun extension(extensionId: String): InstalledExtension? = _installed.value.firstOrNull { it.id == extensionId }

    /** Re-reads the installed list from the engine. */
    fun refresh() {
        val result = try {
            controller.list()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't list extensions", e)
            _loaded.value = true
            return
        }
        result.accept(
            { list ->
                val all = list.orEmpty()
                val ids = all.mapTo(HashSet()) { it.id }
                (extensions.keys - ids - HELPER_ID).forEach(::forget)
                all.forEach { track(it, attachDelegates = true) }
                _loaded.value = true
                publish()
            },
            { error ->
                Log.w(TAG, "Couldn't list extensions", error)
                _loaded.value = true
            },
        )
    }

    // endregion

    // region Install, remove, enable

    /** Installs from an AMO (or any signed) XPI [url]. [slug] lets curated listings recognise it later. */
    fun install(url: String, slug: String? = null) {
        if (url in _installing.value) return
        startInstall(url, url, fromFile = false, slug = slug, cleanup = null)
    }

    /** Installs an XPI the user picked. Gecko needs a real file, so the document is copied first. */
    fun installFromFile(uri: Uri) {
        val key = uri.toString()
        if (key in _installing.value) return
        _installing.update { it + key }
        scope.launch {
            val file = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (file == null) {
                _installing.update { it - key }
                _events.tryEmit(ExtensionEvent.Failed("Pane couldn’t read that file."))
                return@launch
            }
            startInstall(Uri.fromFile(file).toString(), key, fromFile = true, slug = null) {
                scope.launch(Dispatchers.IO) { file.delete() }
            }
        }
    }

    private fun startInstall(url: String, key: String, fromFile: Boolean, slug: String?, cleanup: (() -> Unit)?) {
        _installing.update { it + key }
        val result = try {
            // Constants passed directly: the parameter is a @StringDef that lint checks.
            if (fromFile) {
                controller.install(url, WebExtensionController.INSTALLATION_METHOD_FROM_FILE)
            } else {
                controller.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            }
        } catch (e: Exception) {
            finishInstall(key, cleanup)
            installErrorMessage(e)?.let { _events.tryEmit(ExtensionEvent.Failed(it)) }
            return
        }
        result.accept(
            { ext ->
                finishInstall(key, cleanup)
                if (ext != null) {
                    if (slug != null) recordSlug(slug, ext.id)
                    track(ext, attachDelegates = true)
                    publish()
                    if (pendingAdds.remove(ext.id)) _events.tryEmit(ExtensionEvent.Installed(ext.id, displayName(ext)))
                }
            },
            { error ->
                finishInstall(key, cleanup)
                installErrorMessage(error)?.let { _events.tryEmit(ExtensionEvent.Failed(it)) }
            },
        )
    }

    private fun finishInstall(key: String, cleanup: (() -> Unit)?) {
        _installing.update { it - key }
        cleanup?.let { runCatching { it() } }
    }

    fun uninstall(extensionId: String) {
        val ext = extensions[extensionId] ?: return
        val result = try {
            controller.uninstall(ext)
        } catch (e: Exception) {
            operationFailed("Couldn’t remove the extension.", e)
            return
        }
        result.accept(
            {
                forget(extensionId)
                forgetSlugsFor(extensionId)
                publish()
            },
            { error ->
                operationFailed("Couldn’t remove ${displayName(ext)}.", error)
                refresh()
            },
        )
    }

    fun setEnabled(extensionId: String, enabled: Boolean) {
        val ext = extensions[extensionId] ?: return
        patch(extensionId) { it.copy(enabled = enabled) }
        if (!enabled) {
            actionTracker.removeExtension(extensionId)
            closePopupOf(extensionId)
        }
        val result = try {
            if (enabled) {
                controller.enable(ext, WebExtensionController.EnableSource.USER)
            } else {
                controller.disable(ext, WebExtensionController.EnableSource.USER)
            }
        } catch (e: Exception) {
            operationFailed("Couldn’t change ${displayName(ext)}.", e)
            publish()
            return
        }
        result.accept(
            { updated ->
                if (updated != null) {
                    track(updated, attachDelegates = enabled)
                    publish()
                } else {
                    refresh()
                }
            },
            { error ->
                operationFailed("Couldn’t ${if (enabled) "turn on" else "turn off"} ${displayName(ext)}.", error)
                publish()
            },
        )
    }

    fun setAllowedInPrivateBrowsing(extensionId: String, allowed: Boolean) {
        val ext = extensions[extensionId] ?: return
        patch(extensionId) { it.copy(allowedInPrivateBrowsing = allowed) }
        val result = try {
            controller.setAllowedInPrivateBrowsing(ext, allowed)
        } catch (e: Exception) {
            operationFailed("Couldn’t change private browsing access.", e)
            publish()
            return
        }
        result.accept(
            { updated ->
                if (updated != null) {
                    track(updated, attachDelegates = false)
                    publish()
                } else {
                    refresh()
                }
            },
            { error ->
                operationFailed("Couldn’t change private browsing access.", error)
                publish()
            },
        )
    }

    /** Asks AMO for newer versions of every installed extension. [interactive] reports the outcome. */
    fun checkForUpdates(interactive: Boolean = true) {
        if (_updating.value) return
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
        val targets = extensions.values.filter { !it.isBuiltIn }
        if (targets.isEmpty()) {
            if (interactive) _events.tryEmit(ExtensionEvent.Message("No extensions to update."))
            return
        }
        _updating.value = true
        scope.launch {
            var updated = 0
            var failed = 0
            for (ext in targets) {
                val result = try {
                    controller.update(ext).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Update check failed for ${ext.id}", e)
                    failed++
                    null
                }
                if (result != null) {
                    updated++
                    track(result, attachDelegates = true)
                }
            }
            publish()
            _updating.value = false
            if (interactive) {
                val message = when {
                    updated > 0 -> if (updated == 1) "Updated 1 extension." else "Updated $updated extensions."
                    failed == targets.size -> "Couldn’t check for updates. Try again later."
                    else -> "Your extensions are up to date."
                }
                _events.tryEmit(ExtensionEvent.Message(message))
            }
        }
    }

    /** Checks every extension for updates now and reports the outcome. */
    fun updateAll() = checkForUpdates(interactive = true)

    // endregion

    // region Actions, popups, options

    /** Performs an extension's action (usually opens its popup). */
    fun clickAction(extensionId: String) {
        val targets = actionTracker.clickTargets(extensionId, store.state.value.selectedTabId)
        for (action in targets) {
            try {
                action.click()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Action click failed for $extensionId", e)
            }
        }
    }

    fun dismissPopup() {
        val current = _popup.value ?: return
        _popup.value = null
        // The sheet releases its view while animating away; the session is closed after that.
        main.postDelayed({ current.close() }, POPUP_CLOSE_DELAY_MS)
    }

    private fun closePopupOf(extensionId: String) {
        if (_popup.value?.extensionId == extensionId) dismissPopup()
    }

    private fun togglePopup(ext: WebExtension): GeckoResult<GeckoSession>? {
        if (_popup.value?.extensionId == ext.id) {
            dismissPopup()
            return null
        }
        return openPopup(ext)
    }

    /** Gecko loads the popup page into the returned, already open, session. */
    private fun openPopup(ext: WebExtension): GeckoResult<GeckoSession>? {
        val session = try {
            openAuxSession(url = null) { dismissPopup() }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open a popup for ${ext.id}", e)
            return null
        }
        dismissPopup()
        val info = _installed.value.firstOrNull { it.id == ext.id }
        _popup.value = ExtensionPopup(ext.id, info?.name ?: displayName(ext), info?.icon, session)
        return GeckoResult.fromValue(session)
    }

    /**
     * An open engine session showing [extensionId]'s options page, or null if it has none. The
     * caller owns it and must close it.
     */
    fun openOptionsSession(extensionId: String): GeckoSession? {
        val url = extensions[extensionId]?.metaData?.optionsPageUrl?.takeIf { it.isNotBlank() } ?: return null
        return try {
            openAuxSession(url) {}
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open options for $extensionId", e)
            null
        }
    }

    /**
     * A session for extension UI (popups, options pages). Links that open new windows become tabs,
     * nothing but web and extension pages may load, and `window.close()` calls [onClose].
     */
    private fun openAuxSession(url: String?, onClose: () -> Unit): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .build()
        val session = GeckoSession(settings)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? =
                if (LinkPolicy.decide(request.uri) == LinkDecision.LoadInBrowser) null else GeckoResult.deny()

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (LinkPolicy.decide(uri) != LinkDecision.LoadInBrowser) return null
                val (tabId, child) = sessions.createTabForEngine(uri, private = false, parentId = null, select = true)
                if (_popup.value?.session === session) dismissPopup()
                _events.tryEmit(ExtensionEvent.TabOpened(tabId))
                return GeckoResult.fromValue(child)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) = onClose()
        }
        sessions.promptDelegateFactory(AUX_PROMPT_OWNER)?.let { session.promptDelegate = it }
        session.open(runtime)
        if (url != null) session.loadUri(url)
        return session
    }

    /** Toggles reader view for a tab (called from the toolbar). */
    fun toggleReaderMode(tabId: String) = helper.toggleReader(tabId)

    // endregion

    // region Engine delegates

    private val sessionObserver = object : SessionManager.Observer {
        override fun onSessionCreated(tabId: String, session: GeckoSession) {
            liveSessions[tabId] = session
            helper.attachSession(tabId, session)
            extensions.values.forEach { ext ->
                if (ext.id != HELPER_ID && !ext.isBuiltIn) attachSession(ext, tabId, session)
            }
        }

        override fun onSessionClosed(tabId: String) {
            liveSessions.remove(tabId)
            sessionDelegates.remove(tabId)
            actionTracker.removeTab(tabId)
            helper.onSessionClosed(tabId)
        }
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) =
            actionTracker.onAction(extension.id, null, action, isPageAction = false)

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) =
            actionTracker.onAction(extension.id, null, action, isPageAction = true)

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            togglePopup(extension)

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
            openPopup(extension)
    }

    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(extension: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            return try {
                val url = createDetails.url?.takeIf { it.isNotBlank() }
                val select = createDetails.active != false
                // Gecko opens and loads the returned session itself.
                val (tabId, session) = sessions.createTabForEngine(url, private = false, parentId = null, select = select)
                if (select) {
                    dismissPopup()
                    _events.tryEmit(ExtensionEvent.TabOpened(tabId))
                }
                GeckoResult.fromValue(session)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open a tab for ${extension.id}", e)
                null
            }
        }

        override fun onOpenOptionsPage(extension: WebExtension) {
            _events.tryEmit(ExtensionEvent.OpenOptions(extension.id))
        }
    }

    /** Per-tab delegates: tab-specific action state and `tabs.update` / `tabs.remove`. */
    private inner class SessionDelegates(private val tabId: String) {
        val action = object : WebExtension.ActionDelegate {
            override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) =
                actionTracker.onAction(extension.id, tabId, action, isPageAction = false)

            override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) =
                actionTracker.onAction(extension.id, tabId, action, isPageAction = true)

            override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
                togglePopup(extension)

            override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? =
                openPopup(extension)
        }

        val tab = object : WebExtension.SessionTabDelegate {
            override fun onCloseTab(extension: WebExtension?, session: GeckoSession): GeckoResult<AllowOrDeny> {
                if (store.state.value.tab(tabId) == null) return GeckoResult.deny()
                // Not from inside the engine callback that is still using this session.
                main.post { browser.close(tabId) }
                return GeckoResult.allow()
            }

            override fun onUpdateTab(
                extension: WebExtension,
                session: GeckoSession,
                updateDetails: WebExtension.UpdateTabDetails,
            ): GeckoResult<AllowOrDeny> {
                if (store.state.value.tab(tabId) == null) return GeckoResult.deny()
                if (updateDetails.active == true) browser.select(tabId)
                updateDetails.url?.takeIf { it.isNotBlank() }?.let { sessions.load(tabId, it) }
                return GeckoResult.allow()
            }
        }
    }

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val meta = extension.metaData
            val pending = PendingResult<WebExtension.PermissionPromptResponse>()
            val prompt = InstallPrompt(
                id = ExtensionPrompt.nextId(),
                extensionId = extension.id,
                name = displayName(extension),
                creator = meta.creatorName?.takeIf { it.isNotBlank() },
                version = meta.version,
                permissions = ExtensionPermissions.describe(permissions.toList(), origins.toList()),
                dataCollection = ExtensionPermissions.dataCollection(dataCollectionPermissions.toList()),
                canRunInPrivate = meta.incognito != "not_allowed",
                offersTechnicalData = meta.optionalDataCollectionPermissions?.contains(TECHNICAL_DATA) == true,
                pending = pending,
                onAnswered = { answered, added ->
                    removePrompt(answered)
                    if (added) pendingAdds += answered.extensionId
                },
            )
            _prompts.update { it + prompt }
            loadImage(meta.icon, iconPx) { prompt.setIcon(it) }
            return pending.result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<out String>,
            newOrigins: Array<out String>,
            newDataCollectionPermissions: Array<out String>,
        ): GeckoResult<AllowOrDeny>? =
            permissionPrompt(extension, PermissionPrompt.Kind.Update, newPermissions, newOrigins, newDataCollectionPermissions)

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<AllowOrDeny>? =
            permissionPrompt(extension, PermissionPrompt.Kind.Optional, permissions, origins, dataCollectionPermissions)
    }

    private fun permissionPrompt(
        extension: WebExtension,
        kind: PermissionPrompt.Kind,
        permissions: Array<out String>,
        origins: Array<out String>,
        dataCollection: Array<out String>,
    ): GeckoResult<AllowOrDeny> {
        val pending = PendingResult<AllowOrDeny>()
        val sentences = ExtensionPermissions.describe(permissions.toList(), origins.toList()) +
            listOfNotNull(ExtensionPermissions.dataCollection(dataCollection.toList()))
        val prompt = PermissionPrompt(
            id = ExtensionPrompt.nextId(),
            extensionId = extension.id,
            name = displayName(extension),
            kind = kind,
            permissions = sentences,
            pending = pending,
            onAnswered = { removePrompt(it) },
        )
        _prompts.update { it + prompt }
        return pending.result
    }

    private fun removePrompt(prompt: ExtensionPrompt) = _prompts.update { list -> list.filterNot { it.id == prompt.id } }

    private val addonDelegate = object : WebExtensionController.AddonManagerDelegate {
        override fun onInstalled(extension: WebExtension) {
            track(extension, attachDelegates = true)
            publish()
            if (pendingAdds.remove(extension.id)) _events.tryEmit(ExtensionEvent.Installed(extension.id, displayName(extension)))
        }

        override fun onUninstalled(extension: WebExtension) {
            forget(extension.id)
            publish()
        }

        override fun onEnabled(extension: WebExtension) {
            track(extension, attachDelegates = true)
            publish()
        }

        override fun onDisabled(extension: WebExtension) {
            track(extension, attachDelegates = false)
            actionTracker.removeExtension(extension.id)
            closePopupOf(extension.id)
            publish()
        }

        override fun onReady(extension: WebExtension) {
            // The background page is running now; (re)attaching makes it resend its action state.
            track(extension, attachDelegates = true)
            publish()
        }

        override fun onOptionalPermissionsChanged(extension: WebExtension) {
            track(extension, attachDelegates = false)
            publish()
        }

        override fun onInstallationFailed(extension: WebExtension?, installException: WebExtension.InstallException) {
            // Installs started in Pane report their own failures; this covers ones started by pages (AMO).
            if (_installing.value.isNotEmpty()) return
            installErrorMessage(installException)?.let { _events.tryEmit(ExtensionEvent.Failed(it)) }
        }
    }

    // endregion

    // region Bookkeeping

    private fun installHelper() {
        val result = try {
            controller.ensureBuiltIn(HELPER_URI, HELPER_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't install the helper extension", e)
            return
        }
        result.accept(
            { ext -> if (ext != null) onHelperReady(ext) },
            { error -> Log.e(TAG, "Couldn't install the helper extension", error) },
        )
    }

    private fun onHelperReady(ext: WebExtension) {
        extensions[ext.id] = ext
        helper.attach(ext, liveSessions)
        // Reader view and page colours should work in private tabs too.
        if (!ext.metaData.allowedInPrivateBrowsing) {
            runCatching { controller.setAllowedInPrivateBrowsing(ext, true) }.getOrNull()?.accept(
                { updated -> if (updated != null) extensions[updated.id] = updated },
                { error -> Log.w(TAG, "Helper can't run in private tabs", error) },
            )
        }
        if (!ext.metaData.enabled) {
            runCatching { controller.enable(ext, WebExtensionController.EnableSource.APP) }.getOrNull()?.accept(
                { updated -> if (updated != null) extensions[updated.id] = updated },
                { error -> Log.w(TAG, "Couldn't enable the helper", error) },
            )
        }
    }

    /** Remembers [ext] and, for regular extensions, hooks up delegates and loads its icon. */
    private fun track(ext: WebExtension, attachDelegates: Boolean) {
        extensions[ext.id] = ext
        if (ext.id == HELPER_ID) {
            helper.attach(ext, liveSessions)
            return
        }
        if (ext.isBuiltIn) return
        if (attachDelegates) attach(ext)
        loadIcon(ext)
    }

    private fun attach(ext: WebExtension) {
        try {
            ext.setActionDelegate(actionDelegate)
            ext.setTabDelegate(tabDelegate)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't attach delegates to ${ext.id}", e)
        }
        liveSessions.forEach { (tabId, session) -> attachSession(ext, tabId, session) }
    }

    private fun attachSession(ext: WebExtension, tabId: String, session: GeckoSession) {
        val delegates = sessionDelegates.getOrPut(tabId) { SessionDelegates(tabId) }
        try {
            val controller = session.webExtensionController
            controller.setActionDelegate(ext, delegates.action)
            controller.setTabDelegate(ext, delegates.tab)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't attach ${ext.id} to $tabId", e)
        }
    }

    private fun forget(extensionId: String) {
        extensions.remove(extensionId)
        actionTracker.removeExtension(extensionId)
        closePopupOf(extensionId)
        icons.keys.removeAll { it.startsWith("$extensionId@") }
        _prompts.value.filter { it.extensionId == extensionId && it is PermissionPrompt }.forEach { it.dismiss() }
    }

    private fun publish() {
        _installed.value = extensions.values
            .filter { !it.isBuiltIn && it.id != HELPER_ID }
            .map { it.toInstalled(icons[iconKey(it)]) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Applies an optimistic change so switches respond instantly; [publish] later restores the truth. */
    private fun patch(extensionId: String, transform: (InstalledExtension) -> InstalledExtension) {
        _installed.update { list -> list.map { if (it.id == extensionId) transform(it) else it } }
    }

    private fun loadIcon(ext: WebExtension) {
        val key = iconKey(ext)
        if (icons.containsKey(key) || !iconsLoading.add(key)) return
        loadImage(ext.metaData.icon, iconPx) { bitmap ->
            iconsLoading.remove(key)
            if (bitmap != null && extensions.containsKey(ext.id)) {
                icons[key] = bitmap
                publish()
            }
        }
    }

    private fun loadImage(image: Image?, sizePx: Int, onResult: (ImageBitmap?) -> Unit) {
        if (image == null) {
            onResult(null)
            return
        }
        try {
            image.getBitmap(sizePx).accept(
                { bitmap -> onResult(bitmap?.asImageBitmap()) },
                { error ->
                    Log.d(TAG, "Extension icon unavailable", error)
                    onResult(null)
                },
            )
        } catch (e: Exception) {
            onResult(null)
        }
    }

    private fun iconKey(ext: WebExtension) = "${ext.id}@${ext.metaData.version}"

    private fun displayName(ext: WebExtension): String = ext.metaData.name?.takeIf { it.isNotBlank() } ?: ext.id

    private fun WebExtension.toInstalled(icon: ImageBitmap?): InstalledExtension {
        val m = metaData
        return InstalledExtension(
            id = id,
            name = displayName(this),
            version = m.version.orEmpty(),
            description = m.description?.takeIf { it.isNotBlank() },
            creator = m.creatorName?.takeIf { it.isNotBlank() },
            creatorUrl = m.creatorUrl?.takeIf { it.isNotBlank() },
            homepageUrl = m.homepageUrl?.takeIf { it.isNotBlank() },
            icon = icon,
            enabled = m.enabled,
            allowedInPrivateBrowsing = m.allowedInPrivateBrowsing,
            canRunInPrivate = m.incognito != "not_allowed",
            optionsPageUrl = m.optionsPageUrl?.takeIf { it.isNotBlank() },
            openOptionsPageInTab = m.openOptionsPageInTab,
            isBuiltIn = isBuiltIn,
            permissions = m.requiredPermissions?.toList().orEmpty(),
            origins = m.requiredOrigins?.toList().orEmpty(),
            grantedOptionalPermissions = m.grantedOptionalPermissions?.toList().orEmpty(),
            grantedOptionalOrigins = m.grantedOptionalOrigins?.toList().orEmpty(),
            dataCollection = m.requiredDataCollectionPermissions?.toList().orEmpty(),
            amoListingUrl = m.amoListingUrl?.takeIf { it.isNotBlank() },
            blocklistState = m.blocklistState,
            signedState = m.signedState,
            disabledFlags = m.disabledFlags,
            problem = problemOf(m),
        )
    }

    private fun problemOf(m: WebExtension.MetaData): String? {
        val flags = m.disabledFlags
        return when {
            m.blocklistState == WebExtension.BlocklistStateFlags.BLOCKED ->
                "Mozilla blocked this extension because it violates its policies or puts your security at risk."
            m.blocklistState == WebExtension.BlocklistStateFlags.SOFTBLOCKED ->
                "Mozilla restricted this extension. You can turn it on, but it may put your security at risk."
            (flags and WebExtension.DisabledFlags.SIGNATURE) != 0 ->
                "Turned off because Mozilla couldn’t verify it."
            (flags and WebExtension.DisabledFlags.APP_VERSION) != 0 ->
                "Turned off because it doesn’t work with this version of Pane."
            m.blocklistState == WebExtension.BlocklistStateFlags.VULNERABLE_UPDATE_AVAILABLE ->
                "This version has a known vulnerability. Check for updates to fix it."
            m.blocklistState == WebExtension.BlocklistStateFlags.VULNERABLE_NO_UPDATE ->
                "This version has a known vulnerability and no fix yet."
            m.blocklistState == WebExtension.BlocklistStateFlags.OUTDATED ->
                "This version is out of date. Check for updates."
            else -> null
        }
    }

    private fun installErrorMessage(error: Throwable?): String? {
        val e = error as? WebExtension.InstallException ?: return "Couldn’t install the extension."
        val name = e.extensionName?.takeIf { it.isNotBlank() } ?: "This extension"
        return when (e.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED,
            WebExtension.InstallException.ErrorCodes.ERROR_POSTPONED,
            -> null
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                "Couldn’t download the extension. Check your connection and try again."
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH,
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE,
            -> "This file isn’t a valid extension, or it’s damaged."
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS -> "Pane couldn’t read the extension file."
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                "$name isn’t verified by Mozilla, so it can’t be installed."
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_TYPE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNSUPPORTED_ADDON_TYPE,
            -> "This kind of add-on isn’t supported in Pane."
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE,
            WebExtension.InstallException.ErrorCodes.ERROR_UNEXPECTED_ADDON_VERSION,
            -> "$name doesn’t work with this version of Pane."
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                "$name was blocked by Mozilla for security reasons."
            WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED ->
                "$name was restricted by Mozilla and can’t be installed."
            WebExtension.InstallException.ErrorCodes.ERROR_ADMIN_INSTALL_ONLY ->
                "$name can only be installed by an administrator."
            else -> "Couldn’t install $name."
        }
    }

    private fun operationFailed(message: String, error: Throwable?) {
        Log.w(TAG, message, error)
        _events.tryEmit(ExtensionEvent.Failed(message))
    }

    private fun copyToCache(uri: Uri): File? {
        return try {
            val dir = File(context.cacheDir, "xpi").apply { mkdirs() }
            // Leftovers from installs interrupted by the process dying.
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > STALE_IMPORT_MS) it.delete() }
            val file = File(dir, "import-${System.currentTimeMillis()}.xpi")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            var total = 0L
            input.use { source ->
                file.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_XPI_BYTES) {
                            sink.close()
                            file.delete()
                            return null
                        }
                        sink.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) {
                file.delete()
                null
            } else {
                file
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't copy the picked extension", e)
            null
        }
    }

    private fun loadRecordedSlugs(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_SLUG_PREFIX) && value is String) key.removePrefix(KEY_SLUG_PREFIX) to value else null
        }.toMap()

    private fun recordSlug(slug: String, extensionId: String) {
        prefs.edit().putString(KEY_SLUG_PREFIX + slug, extensionId).apply()
        _recordedSlugs.update { it + (slug to extensionId) }
    }

    private fun forgetSlugsFor(extensionId: String) {
        val slugs = _recordedSlugs.value.filterValues { it == extensionId }.keys
        if (slugs.isEmpty()) return
        val editor = prefs.edit()
        slugs.forEach { editor.remove(KEY_SLUG_PREFIX + it) }
        editor.apply()
        _recordedSlugs.update { it - slugs }
    }

    // endregion

    companion object {
        private const val TAG = "Extensions"
        private const val PREFS = "pane_extensions"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_SLUG_PREFIX = "slug:"
        private const val ICON_DP = 64
        private const val ACTION_ICON_DP = 32
        private const val UPDATE_CHECK_DELAY_MS = 20_000L
        private const val POPUP_CLOSE_DELAY_MS = 1_500L
        private const val STALE_IMPORT_MS = 60 * 60 * 1000L
        private const val MAX_XPI_BYTES = 256L * 1024 * 1024
        private const val TECHNICAL_DATA = "technicalAndInteraction"

        /** Prompts raised by extension popups and options pages are attributed to this owner id. */
        const val AUX_PROMPT_OWNER = "extension-ui"

        const val HELPER_ID = "helper@pane.app"
        private const val HELPER_URI = "resource://android/assets/extensions/pane-helper/"
    }
}
