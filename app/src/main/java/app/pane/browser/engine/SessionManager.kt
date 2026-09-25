package app.pane.browser.engine

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.pane.browser.data.HistoryRepository
import app.pane.browser.settings.SettingsStore
import app.pane.core.security.IntentUri
import app.pane.core.security.LinkDecision
import app.pane.core.security.LinkPolicy
import app.pane.core.tabs.BrowserAction
import app.pane.core.tabs.BrowserStore
import app.pane.core.tabs.PersistedSession
import app.pane.core.tabs.SecurityState
import app.pane.core.tabs.TabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.util.UUID

/**
 * Owns one [GeckoSession] per tab and translates engine callbacks into [BrowserStore] actions.
 *
 * Sessions are created lazily: restored and freshly opened "home" tabs cost nothing until they
 * actually load something, and sessions of long-unused tabs are closed (keeping their history)
 * to bound memory.
 */
class SessionManager(
    private val context: Context,
    val runtime: GeckoRuntime,
    private val store: BrowserStore,
    private val settings: SettingsStore,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) {
    private val sessions = LinkedHashMap<String, GeckoSession>()
    private val backEntries = HashMap<String, BackEntry>()

    /** The page a back navigation in [tabId] would return to, for the back-gesture preview. */
    data class BackEntry(val url: String, val title: String)

    fun backEntry(tabId: String): BackEntry? = backEntries[tabId]

    /** Counts trackers blocked across all tabs, shown on the start page. */
    var onTrackerBlocked: () -> Unit = {}
    private val engineState = HashMap<String, String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionFile = AtomicFile(File(context.filesDir, "session.json"))

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Bumped whenever a session is created or destroyed, so views can re-attach. */
    private val _sessionsVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val sessionsVersion: kotlinx.coroutines.flow.StateFlow<Int> = _sessionsVersion

    private val _scroll = MutableSharedFlow<ScrollUpdate>(extraBufferCapacity = 64)
    val scroll: SharedFlow<ScrollUpdate> = _scroll.asSharedFlow()

    /** Hooks for feature modules (extensions, reader mode) that need every session. */
    interface Observer {
        fun onSessionCreated(tabId: String, session: GeckoSession) {}
        fun onSessionClosed(tabId: String) {}
    }

    private val observers = mutableListOf<Observer>()
    fun addObserver(observer: Observer) {
        observers += observer
        sessions.forEach { (id, s) -> observer.onSessionCreated(id, s) }
    }

    // Supplied by feature packages; defaults keep the engine usable on its own.
    var promptDelegateFactory: (tabId: String) -> GeckoSession.PromptDelegate? = { null }
    var permissionDelegateFactory: (tabId: String) -> GeckoSession.PermissionDelegate? = { null }
    var selectionDelegateFactory: (tabId: String) -> GeckoSession.SelectionActionDelegate? = { null }
    var onExternalResponse: (tabId: String, response: WebResponse) -> Unit = { _, _ -> }
    var onContextMenu: (tabId: String, x: Int, y: Int, element: GeckoSession.ContentDelegate.ContextElement) -> Unit = { _, _, _, _ -> }

    /** Called after a tab is gone for good, to drop its prompts and snapshots. */
    var onTabClosed: (tabId: String) -> Unit = {}

    fun session(tabId: String?): GeckoSession? = tabId?.let { sessions[it] }

    val liveSessionCount: Int get() = sessions.size

    // region Tabs

    /**
     * Opens a tab. A null [url] makes a "home" tab that shows the start page and has no engine
     * session until the user goes somewhere.
     */
    fun openTab(
        url: String?,
        private: Boolean,
        parentId: String? = null,
        select: Boolean = true,
        index: Int? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val tab = TabState(
            id = id,
            url = url.orEmpty(),
            isPrivate = private,
            parentId = parentId,
            desktopMode = settings.current.desktopModeByDefault,
            loading = url != null,
        )
        store.dispatch(BrowserAction.AddTab(tab, select = select, index = index))
        if (url != null) load(id, url)
        if (select) onTabSelected(id)
        return id
    }

    fun selectTab(tabId: String) {
        store.dispatch(BrowserAction.SelectTab(tabId))
        onTabSelected(tabId)
    }

    private var activeTabId: String? = null

    /** Keeps exactly one session active (rendering, timers at full speed) and recreates it if needed. */
    fun onTabSelected(tabId: String) {
        val previous = activeTabId
        if (previous != null && previous != tabId) {
            sessions[previous]?.let {
                it.setActive(false)
                runtime.webExtensionController.setTabActive(it, false)
            }
        }
        activeTabId = tabId
        val tab = store.state.value.tab(tabId) ?: return
        val session = if (tab.url.isNotEmpty()) ensureSession(tabId, loadIfEmpty = true) else sessions[tabId]
        session?.let {
            it.setActive(true)
            runtime.webExtensionController.setTabActive(it, true)
        }
        trimSessions()
    }

    fun closeTab(tabId: String) {
        store.dispatch(BrowserAction.RemoveTab(tabId))
        destroySession(tabId)
        engineState.remove(tabId)
        onTabClosed(tabId)
        store.state.value.selectedTabId?.let(::onTabSelected)
        schedulePersist()
    }

    fun closeAllTabs(private: Boolean) {
        val ids = store.state.value.tabsIn(private).map { it.id }
        store.dispatch(BrowserAction.RemoveAllTabs(private))
        ids.forEach {
            destroySession(it)
            engineState.remove(it)
            onTabClosed(it)
        }
        store.state.value.selectedTabId?.let(::onTabSelected)
        schedulePersist()
    }

    /** Reopens the most recently closed tab where it was. */
    fun undoCloseTab(): Boolean {
        val closed = store.state.value.recentlyClosed.firstOrNull() ?: return false
        store.dispatch(BrowserAction.UndoClose)
        val id = closed.tab.id
        if (closed.tab.url.isNotEmpty()) load(id, closed.tab.url)
        onTabSelected(id)
        return true
    }

    private fun destroySession(tabId: String) {
        backEntries.remove(tabId)
        sessions.remove(tabId)?.let { s ->
            if (activeTabId == tabId) activeTabId = null
            s.close()
            observers.forEach { it.onSessionClosed(tabId) }
            _sessionsVersion.value++
        }
    }

    /** Frees engine memory for tabs the user hasn't looked at in a while; their history survives. */
    private fun trimSessions(max: Int = MAX_LIVE_SESSIONS) {
        if (sessions.size <= max) return
        val state = store.state.value
        val victims = sessions.keys
            .filter { it != state.selectedTabId && state.tab(it)?.mediaPlaying != true }
            .sortedBy { state.tab(it)?.lastAccessed ?: 0L }
            .take(sessions.size - max)
        victims.forEach(::destroySession)
    }

    // endregion

    // region Navigation

    fun load(tabId: String, url: String, flags: Int = GeckoSession.LOAD_FLAGS_NONE, headers: Map<String, String>? = null) {
        store.updateTab(tabId) { it.copy(url = url, loading = true, progress = 5, crashed = false) }
        val loader = GeckoSession.Loader().uri(url).flags(flags)
        if (headers != null) loader.additionalHeaders(headers)
        ensureSession(tabId).load(loader)
    }

    fun goBack(tabId: String) = sessions[tabId]?.goBack()
    fun goForward(tabId: String) = sessions[tabId]?.goForward()
    fun stop(tabId: String) = sessions[tabId]?.stop()

    fun reload(tabId: String, bypassCache: Boolean = false) {
        val tab = store.state.value.tab(tabId) ?: return
        val session = sessions[tabId]
        when {
            session == null && tab.url.isNotEmpty() -> load(tabId, tab.url)
            bypassCache -> session?.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
            else -> session?.reload()
        }
    }

    fun setDesktopMode(tabId: String, enabled: Boolean) {
        store.updateTab(tabId) { it.copy(desktopMode = enabled) }
        sessions[tabId]?.settings?.apply {
            setUserAgentMode(if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            setViewportMode(if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        }
        sessions[tabId]?.reload()
    }

    // endregion

    // region Session construction

    /**
     * Returns the tab's session, creating and opening it if needed. A new session resumes the
     * tab's saved history when there is one; otherwise, with [loadIfEmpty], it loads the tab's URL.
     */
    fun ensureSession(tabId: String, loadIfEmpty: Boolean = false): GeckoSession {
        sessions[tabId]?.let { return it }
        val tab = store.state.value.tab(tabId) ?: error("No tab $tabId")
        val session = newSession(tabId, tab.isPrivate, tab.desktopMode)
        session.open(runtime)
        val saved = engineState[tabId]?.let { GeckoSession.SessionState.fromString(it) }
        when {
            saved != null -> session.restoreState(saved)
            loadIfEmpty && tab.url.isNotEmpty() -> session.loadUri(tab.url)
        }
        return session
    }

    private fun newSession(tabId: String, private: Boolean, desktop: Boolean): GeckoSession {
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(private)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(false)
            .allowJavascript(settings.current.javascriptEnabled)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(sessionSettings)
        wire(tabId, session, private)
        sessions[tabId] = session
        observers.forEach { it.onSessionCreated(tabId, session) }
        _sessionsVersion.value++
        return session
    }

    private fun wire(tabId: String, session: GeckoSession, private: Boolean) {
        session.navigationDelegate = NavigationDelegate(tabId, private)
        session.progressDelegate = ProgressDelegate(tabId)
        session.contentDelegate = ContentDelegate(tabId)
        session.historyDelegate = HistoryDelegate(tabId, private)
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                _scroll.tryEmit(ScrollUpdate(tabId, scrollY))
            }
        }
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (event.antiTrackingCategory != 0 || event.cookieBehaviorCategory != 0) {
                    store.updateTab(tabId) { it.copy(trackersBlocked = it.trackersBlocked + 1) }
                    onTrackerBlocked()
                }
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) = store.updateTab(tabId) { it.copy(mediaPlaying = true) }
            override fun onPause(session: GeckoSession, mediaSession: MediaSession) = store.updateTab(tabId) { it.copy(mediaPlaying = false) }
            override fun onStop(session: GeckoSession, mediaSession: MediaSession) = store.updateTab(tabId) { it.copy(mediaPlaying = false) }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) = store.updateTab(tabId) { it.copy(mediaPlaying = false) }
        }
        promptDelegateFactory(tabId)?.let { session.promptDelegate = it }
        permissionDelegateFactory(tabId)?.let { session.permissionDelegate = it }
        selectionDelegateFactory(tabId)?.let { session.selectionActionDelegate = it }
    }

    private inner class NavigationDelegate(private val tabId: String, private val private: Boolean) : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            val newUrl = url ?: return
            store.updateTab(tabId) {
                it.copy(
                    url = newUrl,
                    readerable = false,
                    inReaderMode = ReaderUrls.isReaderUrl(newUrl),
                    themeColor = if (ReaderUrls.sameDocument(it.url, newUrl)) it.themeColor else null,
                )
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = store.updateTab(tabId) { it.copy(canGoBack = canGoBack) }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = store.updateTab(tabId) { it.copy(canGoForward = canGoForward) }

        override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? =
            route(request.uri, request.hasUserGesture)

        override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? =
            when (LinkPolicy.decide(request.uri)) {
                LinkDecision.LoadInBrowser -> null
                // Frames never get to launch apps or touch local files.
                else -> GeckoResult.deny()
            }

        private fun route(uri: String, userGesture: Boolean): GeckoResult<AllowOrDeny>? = when (LinkPolicy.decide(uri)) {
            LinkDecision.LoadInBrowser -> null
            LinkDecision.Block -> GeckoResult.deny()
            LinkDecision.AskToOpenExternally -> {
                val fallback = IntentUri.parse(uri)?.fallbackUrl
                _events.tryEmit(EngineEvent.ExternalLink(tabId, uri, fallback, userGesture))
                GeckoResult.deny()
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            if (LinkPolicy.decide(uri) != LinkDecision.LoadInBrowser) {
                route(uri, true)
                return null
            }
            store.state.value.tab(tabId) ?: return null
            // Gecko opens the returned session itself; it must not be open yet.
            val (_, child) = createTabForEngine(uri, private, parentId = tabId, select = true)
            return GeckoResult.fromValue(child)
        }
    }

    /**
     * Creates a tab whose session is handed to Gecko *unopened* (window.open, extension
     * `tabs.create`). Gecko opens and loads it; this just registers it as a tab.
     */
    fun createTabForEngine(url: String?, private: Boolean, parentId: String?, select: Boolean): Pair<String, GeckoSession> {
        val parent = parentId?.let { store.state.value.tab(it) }
        val desktop = parent?.desktopMode ?: settings.current.desktopModeByDefault
        val id = UUID.randomUUID().toString()
        store.dispatch(
            BrowserAction.AddTab(
                TabState(id = id, url = url.orEmpty(), isPrivate = private, parentId = parentId, desktopMode = desktop, loading = url != null),
                select = select,
            ),
        )
        val session = newSession(id, private, desktop)
        if (select) onTabSelectedDeferred(id) else _events.tryEmit(EngineEvent.OpenedInBackground(id))
        return id to session
    }

    private fun onTabSelectedDeferred(tabId: String) {
        scope.launch { onTabSelected(tabId) }
    }

    private inner class ProgressDelegate(private val tabId: String) : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            store.updateTab(tabId) { it.copy(loading = true, progress = 5, trackersBlocked = 0, security = SecurityState.Unknown, crashed = false) }
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            store.updateTab(tabId) { it.copy(loading = false, progress = 100) }
            _events.tryEmit(EngineEvent.PageSettled(tabId))
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            store.updateTab(tabId) { it.copy(progress = progress.coerceIn(5, 100)) }
        }

        override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
            val state = when {
                securityInfo.mixedModeActive == GeckoSession.ProgressDelegate.SecurityInformation.CONTENT_LOADED -> SecurityState.Broken
                securityInfo.isSecure -> SecurityState.Secure
                else -> SecurityState.Insecure
            }
            store.updateTab(tabId) { it.copy(security = state) }
        }

        override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
            val tab = store.state.value.tab(tabId) ?: return
            if (tab.isPrivate) return
            engineState[tabId] = sessionState.toString()
            schedulePersist()
        }
    }

    private inner class ContentDelegate(private val tabId: String) : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            val t = title.orEmpty()
            store.updateTab(tabId) { it.copy(title = t) }
            val tab = store.state.value.tab(tabId) ?: return
            if (!tab.isPrivate && t.isNotBlank() && settings.current.rememberHistory && tab.url.startsWith("http")) {
                scope.launch { history.updateTitle(tab.url, t) }
            }
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) = store.updateTab(tabId) { it.copy(fullscreen = fullScreen) }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) =
            onContextMenu(tabId, screenX, screenY, element)

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) = onExternalResponse(tabId, response)

        override fun onCloseRequest(session: GeckoSession) {
            // Only script-opened tabs may close themselves.
            if (store.state.value.tab(tabId)?.parentId != null) closeTab(tabId)
        }

        override fun onCrash(session: GeckoSession) = handleCrash()

        override fun onKill(session: GeckoSession) = handleCrash()

        private fun handleCrash() {
            sessions.remove(tabId)?.let { runCatching { it.close() } }
            observers.forEach { it.onSessionClosed(tabId) }
            _sessionsVersion.value++
            if (activeTabId == tabId) activeTabId = null
            store.updateTab(tabId) { it.copy(crashed = true, loading = false) }
            _events.tryEmit(EngineEvent.Crashed(tabId))
        }

        override fun onFirstContentfulPaint(session: GeckoSession) {
            _events.tryEmit(EngineEvent.FirstPaint(tabId))
        }

        override fun onShowDynamicToolbar(session: GeckoSession) {
            _events.tryEmit(EngineEvent.ShowToolbar(tabId))
        }

        override fun onWebAppManifest(session: GeckoSession, manifest: org.json.JSONObject) {
            val color = manifest.optString("theme_color").takeIf { it.isNotBlank() } ?: return
            parseCssColor(color)?.let { c -> store.updateTab(tabId) { it.copy(themeColor = c) } }
        }
    }

    private inner class HistoryDelegate(private val tabId: String, private val private: Boolean) : GeckoSession.HistoryDelegate {
        override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
            if (private || !settings.current.rememberHistory) return GeckoResult.fromValue(false)
            if (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL == 0) return GeckoResult.fromValue(false)
            val skip = GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE or
                GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or
                GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
            if (flags and skip != 0) return GeckoResult.fromValue(false)
            if (!url.startsWith("http://") && !url.startsWith("https://")) return GeckoResult.fromValue(false)
            val title = store.state.value.tab(tabId)?.title
            val result = GeckoResult<Boolean>()
            scope.launch {
                history.recordVisit(url, title)
                result.complete(true)
            }
            return result
        }

        override fun onHistoryStateChange(session: GeckoSession, historyList: GeckoSession.HistoryDelegate.HistoryList) {
            val index = historyList.currentIndex
            val back = if (index > 0) historyList[index - 1] else null
            if (back == null) backEntries.remove(tabId) else backEntries[tabId] = BackEntry(back.uri, back.title.orEmpty())
        }

        override fun getVisited(session: GeckoSession, urls: Array<String>): GeckoResult<BooleanArray>? {
            if (private) return GeckoResult.fromValue(BooleanArray(urls.size))
            val result = GeckoResult<BooleanArray>()
            scope.launch { result.complete(history.visited(urls)) }
            return result
        }
    }

    // endregion

    // region Persistence

    private var persistJob: Job? = null

    fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1_500)
            persistNow()
        }
    }

    suspend fun persistNow() {
        if (!settings.current.restoreTabs) return
        val snapshot = store.snapshot { engineState[it] }
        withContext(Dispatchers.IO) {
            val out = runCatching { sessionFile.startWrite() }.getOrNull() ?: return@withContext
            try {
                out.write(json.encodeToString(PersistedSession.serializer(), snapshot).toByteArray())
                sessionFile.finishWrite(out)
            } catch (e: Exception) {
                sessionFile.failWrite(out)
                Log.w(TAG, "Couldn't save tabs", e)
            }
        }
    }

    /** Restores normal tabs from the last run. Their engines start only when each tab is shown. */
    suspend fun restore(): Boolean {
        if (!settings.current.restoreTabs) return false
        val saved = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(PersistedSession.serializer(), String(sessionFile.readFully())) }.getOrNull()
        } ?: return false
        saved.tabs.forEach { t -> t.engineState?.let { engineState[t.id] = it } }
        store.dispatch(BrowserAction.Restore(BrowserStore.tabsFrom(saved), saved.selectedTabId))
        store.state.value.selectedTabId?.let(::onTabSelected)
        return saved.tabs.isNotEmpty()
    }

    fun deletePersistedSession() {
        sessionFile.delete()
        engineState.clear()
    }

    // endregion

    companion object {
        private const val TAG = "SessionManager"
        const val MAX_LIVE_SESSIONS = 6

        /** `#rgb`, `#rrggbb`, `rgb(r, g, b)` → opaque ARGB. */
        fun parseCssColor(css: String): Int? {
            val s = css.trim().lowercase()
            return try {
                when {
                    s.startsWith("#") && s.length == 4 -> {
                        val r = s[1].digitToInt(16) * 17
                        val g = s[2].digitToInt(16) * 17
                        val b = s[3].digitToInt(16) * 17
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    s.startsWith("#") && s.length >= 7 -> (0xFF shl 24) or s.substring(1, 7).toInt(16)
                    s.startsWith("rgb") -> {
                        val parts = s.substringAfter('(').substringBefore(')').split(',', ' ', '/').filter { it.isNotBlank() }
                        val (r, g, b) = parts.take(3).map { it.trim().toFloat().toInt().coerceIn(0, 255) }
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Reader view URLs are served by the built-in reader extension. */
object ReaderUrls {
    /** Set by the extensions module once the reader extension is installed. */
    @Volatile var readerPageBase: String? = null

    fun isReaderUrl(url: String): Boolean = readerPageBase?.let { url.startsWith(it) } == true

    /** True when two URLs only differ by fragment. */
    fun sameDocument(a: String, b: String): Boolean = a.substringBefore('#') == b.substringBefore('#')
}
