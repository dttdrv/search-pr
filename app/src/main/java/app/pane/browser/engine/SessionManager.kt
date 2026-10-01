package app.pane.browser.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.os.Parcel
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.pane.browser.data.HistoryRepository
import app.pane.browser.downloads.DownloadRequest
import app.pane.browser.engine.prompts.HitInfo
import app.pane.browser.engine.prompts.PromptBridge
import app.pane.browser.settings.SettingsStore
import app.pane.core.security.IntentUri
import app.pane.core.security.LinkDecision
import app.pane.core.security.LinkPolicy
import app.pane.core.privacy.TrackingParams
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.HttpsMode
import app.pane.core.settings.ThemeMode
import app.pane.core.tabs.BrowserAction
import app.pane.core.tabs.BrowserStore
import app.pane.core.tabs.PersistedSession
import app.pane.core.tabs.SecurityState
import app.pane.core.tabs.TabState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Owns one [PaneWebView] per tab and turns what they report into [BrowserStore] actions.
 *
 * Memory is the budget. Only a few pages stay alive at once (the one on screen and the last couple
 * you used); the rest are saved to a small blob and rebuilt when you come back, and a blank "home"
 * tab costs nothing until it goes somewhere. The system asking for memory back, or the app leaving
 * the screen, trims further and stops background pages from running.
 */
class SessionManager(
    private val context: Context,
    private val store: BrowserStore,
    private val settings: SettingsStore,
    private val history: HistoryRepository,
    private val adBlocker: AdBlocker,
    private val scope: CoroutineScope,
) {
    private val pages = LinkedHashMap<String, PaneWebView>()
    private val bridge = DownloadBridge()
    private val bridges = HashMap<String, PromptBridge>()
    private val backEntries = HashMap<String, BackEntry>()
    private val upgraded = HashMap<String, String>()

    /** Tab -> the site whose secure address failed under [HttpsMode.Only]; the person may continue to it over http. */
    private val httpOffers = HashMap<String, String>()

    /** Sites the person chose to open over http; no longer upgraded until Pane restarts. */
    private val httpAllowed = HashSet<String>()

    private val barInsetScripts = java.util.WeakHashMap<PaneWebView, ScriptHandler>()
    private var barInset = 0
    private val barInsetJs by lazy { context.assets.open("bar-inset.js").bufferedReader().use { it.readText() } }

    /**
     * The floating bar's height in css pixels. Pages run under the bar, so each keeps this much clear
     * after the end of its content and lifts fixed bottom ui above the bar. 0 when the bar is away.
     */
    fun setBarInset(px: Int) {
        if (px == barInset) return
        barInset = px
        pages.values.forEach(::applyBarInset)
    }

    private fun applyBarInset(page: PaneWebView) {
        val script = "($barInsetJs)($barInset)"
        page.evaluateJavascript(script, null)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        barInsetScripts.remove(page)?.remove()
        runCatching { WebViewCompat.addDocumentStartJavaScript(page, script, setOf("*")) }
            .onSuccess { barInsetScripts[page] = it }
    }

    /** The `navigator.globalPrivacyControl` script each page currently carries, so it can be taken off again. */
    private val gpcScripts = java.util.WeakHashMap<PaneWebView, ScriptHandler>()

    /** The page a back navigation in a tab would return to, for the back-gesture preview. */
    data class BackEntry(val url: String, val title: String)

    fun backEntry(tabId: String): BackEntry? = backEntries[tabId]

    /** Saved page state (history and position) of tabs whose view was released, as Base64. */
    private val engineState = HashMap<String, String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionFile = AtomicFile(File(context.filesDir, "session.json"))

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Bumped whenever a page is created or released, so views can re-attach. */
    private val _version = MutableStateFlow(0)
    val sessionsVersion: StateFlow<Int> = _version

    private val _scroll = MutableSharedFlow<ScrollUpdate>(extraBufferCapacity = 64)
    val scroll: SharedFlow<ScrollUpdate> = _scroll.asSharedFlow()

    /** A fullscreen video's own view, while there is one. The screen puts it above everything. */
    private val _customView = MutableStateFlow<View?>(null)
    val customView: StateFlow<View?> = _customView
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var customViewTab: String? = null

    // Supplied by the feature packages; the defaults keep the engine usable on its own.
    var bridgeFactory: (tabId: String) -> PromptBridge? = { null }
    var onDownload: (DownloadRequest) -> Unit = {}
    var onContextMenu: (tabId: String, x: Int, y: Int, hit: HitInfo) -> Unit = { _, _, _, _ -> }
    var onFavicon: (pageUrl: String, icon: Bitmap) -> Unit = { _, _ -> }

    /** Called after a tab is gone for good, to drop its prompts and snapshots. */
    var onTabClosed: (tabId: String) -> Unit = {}

    fun session(tabId: String?): PaneWebView? = tabId?.let { pages[it] }

    val liveSessionCount: Int get() = pages.size

    private var activeTabId: String? = null
    private var foreground = true
    private var hostContext: Context? = null

    private val maxLive: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) 2 else 3
    }

    // region Tabs

    /**
     * Opens a tab. A null [url] makes a "home" tab that shows the start page and has no page of
     * its own until the user goes somewhere.
     */
    fun openTab(url: String?, private: Boolean, parentId: String? = null, select: Boolean = true, index: Int? = null): String {
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

    /** Keeps exactly one page running at full speed, and rebuilds it if it had been released. */
    fun onTabSelected(tabId: String) {
        // A stale id (a tab closed since a suggestion or toast was shown) must not deactivate the page on screen.
        val tab = store.state.value.tab(tabId) ?: return
        val page = if (tab.url.isNotEmpty()) ensureSession(tabId, loadIfEmpty = true) else pages[tabId]
        activate(tabId, page)
        trim()
    }

    private fun activate(tabId: String, page: PaneWebView?) {
        val previous = activeTabId
        if (previous != null && previous != tabId) pages[previous]?.onPause()
        activeTabId = tabId
        if (foreground) page?.onResume()
    }

    fun closeTab(tabId: String) {
        store.dispatch(BrowserAction.RemoveTab(tabId))
        release(tabId)
        engineState.remove(tabId)
        onTabClosed(tabId)
        store.state.value.selectedTabId?.let(::onTabSelected)
        dropPrivateIfUnused()
        schedulePersist()
    }

    fun closeAllTabs(private: Boolean) {
        val ids = store.state.value.tabsIn(private).map { it.id }
        store.dispatch(BrowserAction.RemoveAllTabs(private))
        ids.forEach {
            release(it)
            engineState.remove(it)
            onTabClosed(it)
        }
        store.state.value.selectedTabId?.let(::onTabSelected)
        dropPrivateIfUnused()
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

    /** Once the last private tab is gone, so are its cookies, storage and cache. */
    private fun dropPrivateIfUnused() {
        if (store.state.value.privateTabs.isNotEmpty()) return
        if (pages.values.any { it.tabId in privateIds }) return
        Profiles.dropPrivate()
    }

    private val privateIds = HashSet<String>()

    private fun release(tabId: String) {
        backEntries.remove(tabId)
        upgraded.remove(tabId)
        httpOffers.remove(tabId)
        bridges.remove(tabId)?.cancelAll()
        if (customViewTab == tabId) exitFullscreen()
        pages.remove(tabId)?.let { page ->
            if (activeTabId == tabId) activeTabId = null
            page.release()
            privateIds.remove(tabId)
            _version.value++
            store.updateTab(tabId) {
                it.copy(loading = false, progress = 0, canGoBack = false, canGoForward = false, fullscreen = false, mediaPlaying = false)
            }
        }
    }

    /** Frees the memory of pages not looked at in a while, keeping their history to come back to. */
    private fun trim(max: Int = maxLive) {
        if (pages.size <= max) return
        val state = store.state.value
        val victims = pages.keys
            .filter { it != state.selectedTabId }
            .sortedBy { state.tab(it)?.lastAccessed ?: 0L }
            .take(pages.size - max)
        victims.forEach { id ->
            pages[id]?.let { page -> save(id, page) }
            release(id)
        }
    }

    /** The system wants memory back: keep only the page on screen. */
    fun onTrimMemory(level: Int) {
        when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> trim(1)
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> trim(1)
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> trim(2)
        }
    }

    /**
     * Pages that aren't on screen shouldn't run: timers, animation and media all stop with the app. Only
     * the window the pages are built in counts: an old one stopping behind a new one changes nothing.
     */
    fun setForeground(window: Context, value: Boolean) {
        if (window !== hostContext) return
        foreground = value
        val active = activeTabId?.let { pages[it] }
        if (value) {
            active?.onResume()
            active?.resumeTimers()
        } else {
            pages.values.forEach { it.onPause() }
            active?.pauseTimers()
            schedulePersistNow()
        }
    }

    // endregion

    // region Navigation

    /** Navigates [tabId] to [url]. Does nothing if the tab has closed meanwhile (e.g. a late prompt's fallback). */
    fun load(tabId: String, url: String) {
        if (store.state.value.tab(tabId) == null) return
        val target = prepare(tabId, url)
        store.updateTab(tabId) { it.copy(url = target, loading = true, progress = 5, crashed = false) }
        ensureSession(tabId)?.let { open(it, target) }
    }

    /** Starts a main-frame load of an address Pane itself chose, with `Sec-GPC: 1` when asked for. */
    private fun open(view: WebView, url: String) {
        val web = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        if (web && settings.current.globalPrivacyControl) view.loadUrl(url, mapOf("Sec-GPC" to "1")) else view.loadUrl(url)
    }

    /** Cleans tracking parameters off [url] and, where asked, tries the secure version of a plain-http address first. */
    private fun prepare(tabId: String, url: String): String {
        var target = url
        val s = settings.current
        if (s.stripTrackingParams) target = TrackingParams.strip(target)
        if (s.httpsMode != HttpsMode.Off && target.startsWith("http://", ignoreCase = true) && !isLocal(target) &&
            Uri.parse(target).host?.lowercase() !in httpAllowed
        ) {
            val secure = "https://" + target.substring(7)
            upgraded[tabId] = target
            return secure
        }
        return target
    }

    /** True when [uri] is the http address a failed HTTPS-only load offered to continue to; remembers the site. */
    private fun continueOverHttp(tabId: String, uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (!uri.scheme.equals("http", ignoreCase = true) || httpOffers[tabId] != host) return false
        httpOffers.remove(tabId)
        httpAllowed += host
        return true
    }

    private fun isLocal(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return true
        return host == "localhost" || host.endsWith(".local") || host.endsWith(".localhost") ||
            host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || host.contains(':') || !host.contains('.')
    }

    /** Offers to open [uri] (a typed `mailto:`, `tel:`, …) in another app, without navigating the tab. */
    fun openExternally(tabId: String?, uri: String, userGesture: Boolean = true) {
        val fallback = IntentUri.parse(uri)?.fallbackUrl
        _events.tryEmit(EngineEvent.ExternalLink(tabId, uri, fallback, userGesture))
    }

    fun goBack(tabId: String) = pages[tabId]?.takeIf { it.canGoBack() }?.goBack()
    fun goForward(tabId: String) = pages[tabId]?.takeIf { it.canGoForward() }?.goForward()
    fun stop(tabId: String) = pages[tabId]?.stopLoading()

    fun reload(tabId: String, bypassCache: Boolean = false) {
        val tab = store.state.value.tab(tabId) ?: return
        val page = pages[tabId]
        when {
            page == null && tab.url.isNotEmpty() -> load(tabId, tab.url)
            bypassCache -> page?.let { p ->
                p.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                p.reload()
                p.settings.cacheMode = WebSettings.LOAD_DEFAULT
            }
            else -> page?.reload()
        }
    }

    fun setDesktopMode(tabId: String, enabled: Boolean) {
        store.updateTab(tabId) { it.copy(desktopMode = enabled) }
        pages[tabId]?.let { page ->
            applyDesktop(page, enabled)
            page.reload()
        }
    }

    // endregion

    // region Find, capture, cache

    fun find(tabId: String, query: String, onResult: (active: Int, total: Int) -> Unit) {
        val page = pages[tabId] ?: return
        if (query.isEmpty()) {
            page.clearMatches()
            onResult(0, 0)
            return
        }
        page.setFindListener { active, total, _ -> onResult(if (total == 0) 0 else active + 1, total) }
        page.findAllAsync(query)
    }

    fun findNext(tabId: String, forward: Boolean) = pages[tabId]?.findNext(forward)

    fun clearFind(tabId: String) {
        pages[tabId]?.apply {
            clearMatches()
            setFindListener(null)
        }
    }

    /** A snapshot of the visible page, scaled so its longer side is at most [maxSide] pixels. */
    fun capture(tabId: String, maxSide: Int): Bitmap? {
        val page = pages[tabId] ?: return null
        if (page.width <= 0 || page.height <= 0) return null
        val scale = (maxSide.toFloat() / maxOf(page.width, page.height)).coerceAtMost(1f)
        val w = (page.width * scale).toInt().coerceAtLeast(1)
        val h = (page.height * scale).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = android.graphics.Canvas(bitmap)
                canvas.scale(scale, scale)
                page.draw(canvas)
            }
        }.getOrNull()
    }

    /** Clears the HTTP cache. Needs a live view; borrows one when every page has been released. */
    fun clearCache() {
        val page = pages.values.firstOrNull()
        if (page != null) {
            page.clearCache(true)
            return
        }
        runCatching {
            PaneWebView(context).also {
                it.clearCache(true)
                it.release()
            }
        }
    }

    // endregion

    // region Page construction

    /**
     * Returns the tab's page, building it if needed, or null if there is no such tab or no window to
     * build it in. A rebuilt page resumes the tab's saved history when there is one; otherwise, with
     * [loadIfEmpty], it loads the tab's URL.
     */
    fun ensureSession(tabId: String, loadIfEmpty: Boolean = false): PaneWebView? {
        pages[tabId]?.let { return it }
        val tab = store.state.value.tab(tabId) ?: return null
        val page = newPage(hostContext ?: return null, tabId, tab.isPrivate, tab.desktopMode)
        if (tabId == store.state.value.selectedTabId) activate(tabId, page)
        val saved = engineState[tabId]?.let(::decode)
        when {
            saved != null && page.restoreState(saved) != null -> Unit
            loadIfEmpty && tab.url.isNotEmpty() -> open(page, prepare(tabId, tab.url))
        }
        return page
    }

    private fun newPage(window: Context, tabId: String, private: Boolean, desktop: Boolean): PaneWebView {
        val page = PaneWebView(window)
        // The private profile has to be chosen before the view does anything at all.
        Profiles.assign(page, private)
        if (private) {
            privateIds += tabId
            if (!Profiles.supported) _events.tryEmit(EngineEvent.Message(tabId, "Update Android System WebView to keep private tabs fully separate."))
        }
        page.tabId = tabId
        page.onScroll = { y -> _scroll.tryEmit(ScrollUpdate(tabId, y)) }
        configure(page, desktop)
        wire(page, tabId, private)
        pages[tabId] = page
        _version.value++
        return page
    }

    private fun configure(page: PaneWebView, desktop: Boolean) {
        val s = settings.current
        page.settings.apply {
            javaScriptEnabled = s.javascriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = s.autoplayBlocked
            setSupportZoom(s.forceZoom)
            builtInZoomControls = s.forceZoom
            displayZoomControls = false
            textZoom = (s.textScale * 100).toInt().coerceIn(50, 250)
            setGeolocationEnabled(true)
            setOffscreenPreRaster(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION")
            saveFormData = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(page, !s.blockThirdPartyCookies)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(page.settings, darkenPages(s))
        }
        applyPrivacy(page, s)
        if (barInsetScripts[page] == null) applyBarInset(page)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            // Passkeys: let the system's credential provider answer for the sites you visit.
            runCatching {
                WebSettingsCompat.setWebAuthenticationSupport(page.settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
            }
        }
        applyDesktop(page, desktop)
        page.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        page.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        page.isVerticalScrollBarEnabled = false
    }

    /**
     * Dark pages darken only in a dark app: the Dark theme, or Automatic while the system is dark. The
     * engine then decides page by page (a page with its own dark style keeps it); its own idea of "dark
     * app" is the activity's night mode, which follows the system.
     */
    private fun darkenPages(s: BrowserSettings): Boolean {
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val systemDark = night == android.content.res.Configuration.UI_MODE_NIGHT_YES
        return s.darkPages && (s.theme == ThemeMode.Dark || (s.theme == ThemeMode.System && systemDark))
    }

    /**
     * What a page is allowed to learn and what it is told, per page and each only where this WebView
     * has the feature (an older one keeps its own behaviour): no `X-Requested-With` (which would hand
     * every site Pane's package name), Safe Browsing, and Global Privacy Control in script.
     */
    private fun applyPrivacy(page: PaneWebView, s: BrowserSettings) {
        // its feature constant is restricted API, so support is read from the call itself
        try {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(page.settings, emptySet())
        } catch (_: UnsupportedOperationException) {
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            runCatching { WebSettingsCompat.setSafeBrowsingEnabled(page.settings, s.safeBrowsing) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val attached = gpcScripts[page]
            if (s.globalPrivacyControl && attached == null) {
                runCatching { WebViewCompat.addDocumentStartJavaScript(page, GPC_SCRIPT, setOf("*")) }
                    .onSuccess { gpcScripts[page] = it }
            } else if (!s.globalPrivacyControl && attached != null) {
                runCatching { attached.remove() }
                gpcScripts.remove(page)
            }
        }
        adBlocker.cosmetics.setEnabled(page, s.blockAds)
    }

    private fun applyDesktop(page: PaneWebView, desktop: Boolean) {
        page.settings.apply {
            userAgentString = if (desktop) UserAgents.desktop(context) else UserAgents.mobile(context)
            useWideViewPort = true
            loadWithOverviewMode = desktop
        }
    }

    /** Settings changed: bring every live page in line. */
    fun applySettings(s: BrowserSettings) {
        pages.forEach { (id, page) ->
            configure(page, store.state.value.tab(id)?.desktopMode ?: s.desktopModeByDefault)
        }
    }

    /** The system switching between light and dark re-decides Dark pages, without a change in settings. */
    private val nightWatcher = object : android.content.ComponentCallbacks {
        private var night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK

        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            val now = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (now == night) return
            night = now
            applySettings(settings.current)
        }

        override fun onLowMemory() = Unit
    }

    init {
        context.registerComponentCallbacks(nightWatcher)
    }

    /**
     * The window pages are built in. A page is bound to its window for good (see [PaneWebView]): a new
     * window releases the old window's pages, keeping their history, and rebuilds the selected one.
     * Detaching lets the pages go only once their window is destroyed, so an old window's late detach
     * leaves a newer one alone.
     */
    fun attachHost(activity: Context?) {
        val alive = (hostContext as? LifecycleOwner)?.lifecycle?.currentState != Lifecycle.State.DESTROYED
        if (activity === hostContext || activity == null && alive) return
        hostContext = activity
        pages.keys.toList().forEach { id ->
            pages[id]?.let { save(id, it) }
            release(id)
        }
        if (activity != null) store.state.value.selectedTabId?.let(::onTabSelected)
    }

    private fun wire(page: PaneWebView, tabId: String, private: Boolean) {
        page.webViewClient = Client(tabId, private)
        page.webChromeClient = Chrome(tabId, private)
        bridge.attach(page)
        page.setDownloadListener { url, userAgent, disposition, mime, length ->
            val request = DownloadRequest(tabId, url, userAgent, disposition, mime, length, private, page.url, bridge.reader(page))
            bridge.about(page, url) { name, form -> onDownload(request.copy(name = name, form = form)) }
        }
        page.setOnLongClickListener { view -> longPress(tabId, view as PaneWebView) }
    }

    private fun longPress(tabId: String, page: PaneWebView): Boolean {
        val result = page.hitTestResult
        val type = result.type
        if (type == WebView.HitTestResult.UNKNOWN_TYPE || type == WebView.HitTestResult.EDIT_TEXT_TYPE) return false
        val extra = result.extra
        when (type) {
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // A linked image has two addresses; only the page itself can say which is which.
                val message = android.os.Handler(android.os.Looper.getMainLooper()) { m ->
                    val data = m.data
                    onContextMenu(tabId, page.touchX, page.touchY, HitInfo(type, extra, data.getString("url"), data.getString("src") ?: extra, data.getString("title"), page.url))
                    true
                }.obtainMessage()
                page.requestFocusNodeHref(message)
            }
            else -> onContextMenu(
                tabId, page.touchX, page.touchY,
                HitInfo(
                    type, extra,
                    linkUrl = if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) extra else null,
                    imageUrl = if (type == WebView.HitTestResult.IMAGE_TYPE) extra else null,
                    title = null,
                    pageUrl = page.url,
                ),
            )
        }
        return true
    }

    // endregion

    // region What the page tells us

    private inner class Client(private val tabId: String, private val private: Boolean) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when (LinkPolicy.decide(url)) {
                LinkDecision.LoadInBrowser -> {
                    if (!request.isForMainFrame) return false
                    // The "continue over HTTP" button of a secure address that failed in HTTPS-only mode.
                    if (request.hasGesture() && continueOverHttp(tabId, request.url)) return false
                    val target = prepare(tabId, url)
                    if (target != url) {
                        open(view, target)
                        true
                    } else {
                        false
                    }
                }
                LinkDecision.Block -> true
                LinkDecision.AskToOpenExternally -> {
                    openExternally(tabId, url, request.hasGesture())
                    true
                }
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Runs on a background thread: WebView methods (url, settings) are off limits here.
            val page = view as? PaneWebView ?: return null
            if (request.isForMainFrame) {
                page.siteHost = request.url.host
                return null
            }
            return adBlocker.intercept(request, page.siteHost)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            store.updateTab(tabId) {
                it.copy(
                    url = url, loading = true, progress = 5, crashed = false,
                    // A new document (a reload included) is no longer showing an article.
                    readerable = false, inReaderMode = false,
                    security = if (url.startsWith("https://")) SecurityState.Secure else SecurityState.Insecure,
                    themeColor = if (sameDocument(it.url, url)) it.themeColor else null,
                )
            }
            syncNavigation(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            store.updateTab(tabId) { it.copy(loading = false, progress = 100) }
            syncNavigation(view)
            readThemeColor(view)
            detectReader(view, url)
            _events.tryEmit(EngineEvent.PageSettled(tabId))
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            _events.tryEmit(EngineEvent.FirstPaint(tabId))
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (url.startsWith("data:") || url == "about:blank") return
            store.updateTab(tabId) { it.copy(url = url) }
            syncNavigation(view)
            if (isReload || private || !settings.current.rememberHistory) return
            if (!url.startsWith("http://") && !url.startsWith("https://")) return
            val title = view.title
            scope.launch {
                try {
                    history.recordVisit(url, title)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't record visit", e)
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val url = request.url.toString()
            // A secure address we tried on the user's behalf that didn't answer: fall back to the one they gave.
            upgraded.remove(tabId)?.let { original ->
                if (url.startsWith("https://") && sameAddress(original.removePrefix("http://"), url.removePrefix("https://"))) {
                    if (settings.current.httpsMode != HttpsMode.Only) {
                        open(view, original)
                    } else if (error.errorCode == ERROR_HOST_LOOKUP) {
                        showError(view, url, error.description?.toString().orEmpty())
                    } else {
                        // HTTPS-only never falls back by itself: say so, and let the person choose to continue.
                        Uri.parse(original).host?.let { httpOffers[tabId] = it.lowercase() }
                        showError(view, url, "This site doesn't offer a secure connection.", continueUrl = original)
                    }
                    return
                }
            }
            if (error.errorCode == ERROR_UNSUPPORTED_SCHEME) return
            showError(view, url, error.description?.toString().orEmpty())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            store.updateTab(tabId) { it.copy(security = SecurityState.Broken, loading = false) }
            showError(view, error.url.orEmpty(), "This site's security certificate can't be trusted.")
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            val bridge = bridge(tabId)
            if (bridge == null) handler.cancel() else bridge.httpAuth(handler, host, realm)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val page = pages[tabId]
            if (page !== view) return true
            release(tabId)
            store.updateTab(tabId) { it.copy(crashed = true, loading = false, fullscreen = false, mediaPlaying = false) }
            _events.tryEmit(EngineEvent.Crashed(tabId))
            return true
        }

        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) = dontResend.sendToTarget()
    }

    private inner class Chrome(private val tabId: String, private val private: Boolean) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            store.updateTab(tabId) { it.copy(progress = newProgress.coerceIn(5, 100)) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            val t = title.orEmpty()
            store.updateTab(tabId) { it.copy(title = t) }
            val tab = store.state.value.tab(tabId) ?: return
            if (!private && t.isNotBlank() && settings.current.rememberHistory && tab.url.startsWith("http")) {
                scope.launch {
                    try {
                        history.updateTitle(tab.url, t)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't save title", e)
                    }
                }
            }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            if (icon == null || private || !settings.current.rememberHistory) return
            view.url?.takeIf { it.startsWith("http") }?.let { onFavicon(it, icon) }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            if (store.state.value.tab(tabId) == null) return false
            if (!isUserGesture && settings.current.blockPopups) {
                bridge(tabId)?.blockedPopup(view.hitTestResult.extra.orEmpty()) {}
                return false
            }
            val childId = UUID.randomUUID().toString()
            val parent = store.state.value.tab(tabId)
            store.dispatch(
                BrowserAction.AddTab(
                    TabState(id = childId, url = "", isPrivate = private, parentId = tabId, desktopMode = parent?.desktopMode ?: false, loading = true),
                    select = true,
                ),
            )
            val child = newPage(view.context, childId, private, parent?.desktopMode ?: false)
            transport.webView = child
            resultMsg.sendToTarget()
            scope.launch { onTabSelected(childId) }
            return true
        }

        override fun onCloseWindow(window: WebView) {
            val id = (window as? PaneWebView)?.tabId ?: return
            // Only script-opened tabs may close themselves.
            if (store.state.value.tab(id)?.parentId != null) closeTab(id)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback?.onCustomViewHidden()
            customViewCallback = callback
            customViewTab = tabId
            _customView.value = view
            store.updateTab(tabId) { it.copy(fullscreen = true) }
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean =
            bridge(tabId)?.jsAlert(url, message, result) ?: false.also { result.cancel() }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean =
            bridge(tabId)?.jsConfirm(url, message, result) ?: false.also { result.cancel() }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean =
            bridge(tabId)?.jsPrompt(url, message, defaultValue, result) ?: false.also { result.cancel() }

        override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean =
            bridge(tabId)?.jsBeforeUnload(url, message, result) ?: false.also { result.cancel() }

        override fun onPermissionRequest(request: PermissionRequest) {
            val bridge = bridge(tabId)
            if (bridge == null) request.deny() else bridge.webPermissions(request)
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val bridge = bridge(tabId)
            if (bridge == null) callback.invoke(origin, false, false) else bridge.geolocation(origin, callback)
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            val bridge = bridge(tabId)
            if (bridge == null) {
                callback.onReceiveValue(null)
                return true
            }
            return bridge.fileChooser(callback, params)
        }

        override fun getDefaultVideoPoster(): Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
    }

    private fun bridge(tabId: String): PromptBridge? = bridges[tabId] ?: bridgeFactory(tabId)?.also { bridges[tabId] = it }

    fun exitFullscreen() {
        val tab = customViewTab
        _customView.value = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        customViewTab = null
        if (tab != null) store.updateTab(tab) { it.copy(fullscreen = false) }
    }

    private fun syncNavigation(view: WebView) {
        val tabId = (view as? PaneWebView)?.tabId ?: return
        store.updateTab(tabId) { it.copy(canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
        val list = view.copyBackForwardList()
        val index = list.currentIndex
        val back = if (index > 0) list.getItemAtIndex(index - 1) else null
        if (back == null) backEntries.remove(tabId) else backEntries[tabId] = BackEntry(back.url, back.title.orEmpty())
        if (store.state.value.tab(tabId)?.isPrivate != true) schedulePersist()
    }

    private fun readThemeColor(view: WebView) {
        val tabId = (view as? PaneWebView)?.tabId ?: return
        view.evaluateJavascript("(document.querySelector('meta[name=theme-color]')||{}).content||''") { raw ->
            val css = raw?.trim('"').orEmpty()
            if (css.isNotEmpty()) parseCssColor(css)?.let { c -> store.updateTab(tabId) { it.copy(themeColor = c) } }
        }
    }

    // region Reader

    private val readerScripts by lazy { ReaderScripts(context) }

    /**
     * Asks the finished page whether it has an article, so the menu can offer Reader. Single-page
     * apps often fill in their content a moment after loading, so a page that says no is asked once more.
     */
    private fun detectReader(view: WebView, url: String, retry: Boolean = true) {
        val tabId = (view as? PaneWebView)?.tabId ?: return
        if (!ReaderScripts.isReadableScheme(url) || !settings.current.javascriptEnabled) return
        view.evaluateJavascript(readerScripts.detect()) { raw ->
            if (pages[tabId] !== view) return@evaluateJavascript
            val state = raw?.trim()?.toIntOrNull() ?: 0
            store.updateTab(tabId) { it.copy(readerable = state != 0, inReaderMode = state == 2) }
            if (state == 0 && retry) {
                view.postDelayed({
                    if (pages[tabId] === view && sameDocument(view.url.orEmpty(), url)) detectReader(view, url, retry = false)
                }, 1500)
            }
        }
    }

    /**
     * Shows the page's article in place of the page, or brings the page back. The article replaces
     * the document without navigating, so Back leaves it; leaving Reader reloads the page.
     */
    /** The ground the reader page is drawn on (light or dark, following the app theme), as an ARGB int. */
    private fun readerGround(): Int {
        val dark = when (settings.current.theme) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return if (dark) 0xFF131313.toInt() else 0xFFF3F3F4.toInt()
    }

    fun toggleReader(tabId: String) {
        val tab = store.state.value.tab(tabId) ?: return
        if (tab.inReaderMode) {
            store.updateTab(tabId) { it.copy(inReaderMode = false, themeColor = null) }
            reload(tabId)
            return
        }
        val page = pages[tabId] ?: return
        val script = readerScripts.enter(settings.current.theme, "View original")
        page.evaluateJavascript(script) { raw ->
            if (pages[tabId] !== page) return@evaluateJavascript
            when (raw?.trim('"')) {
                // The status area and the bar take the reader's own ground, not the article site's colour.
                "ok", "active" -> store.updateTab(tabId) { it.copy(inReaderMode = true, readerable = true, themeColor = readerGround()) }
                else -> _events.tryEmit(EngineEvent.Message(tabId, "Reader isn’t available for this page."))
            }
        }
    }

    // endregion

    /** A plain page for failures: the address, what went wrong, and a way to try again. */
    private fun showError(view: WebView, url: String, reason: String, continueUrl: String? = null) {
        val safeUrl = android.text.TextUtils.htmlEncode(url)
        val safeReason = android.text.TextUtils.htmlEncode(reason)
        // Only HTTPS-only mode passes [continueUrl]: the plain-http address, one quiet link under the reload button.
        val heading = if (continueUrl == null) "Can't open this page" else "No secure connection"
        val proceed = continueUrl?.let { """<a class=alt href="${android.text.TextUtils.htmlEncode(it)}">Continue over HTTP</a>""" }.orEmpty()
        // Centred, with the app's own look: soft ground, blue pill, a reload glyph instead of words.
        val html = """
            <!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
            <meta name=pane-error content=1>
            <style>
              :root{color-scheme:light dark;--ground:#fff;--ink:#171717;--muted:#8c8c8c;--blue:#0285ff}
              @media (prefers-color-scheme:dark){:root{--ground:#1c1c1c;--ink:#ededed;--muted:#949494;--blue:#2a8bf2}}
              body{margin:0;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
                   text-align:center;padding:0 32px;font:16px/1.5 system-ui,sans-serif;background:var(--ground);color:var(--ink)}
              h1{font-size:24px;font-weight:600;margin:0 0 10px}
              p{margin:0 0 4px;color:var(--muted);word-break:break-all;max-width:420px}
              a{display:flex;align-items:center;justify-content:center;width:64px;height:48px;margin-top:28px;border-radius:99px;
                background:var(--blue);box-shadow:0 6px 18px rgba(2,133,255,.28)}
              svg{width:22px;height:22px;stroke:#fff;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
              a.alt{display:inline;width:auto;height:auto;margin-top:22px;background:none;box-shadow:none;color:var(--muted);font-size:15px;text-decoration:underline}
            </style>
            <h1>$heading</h1><p>$safeUrl</p><p>$safeReason</p>
            <a href="$safeUrl" aria-label="Try again"><svg viewBox="0 0 24 24"><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.5 4.5v4h-4"/></svg></a>
            $proceed
        """.trimIndent()
        view.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
    }

    // endregion

    // region Saved state

    private fun save(tabId: String, page: PaneWebView) {
        val bundle = Bundle()
        page.saveState(bundle)
        if (!bundle.isEmpty) engineState[tabId] = encode(bundle)
    }

    private fun encode(bundle: Bundle): String {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        } finally {
            parcel.recycle()
        }
    }

    private fun decode(text: String): Bundle? {
        val parcel = Parcel.obtain()
        return try {
            val bytes = Base64.decode(text, Base64.NO_WRAP)
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readBundle(SessionManager::class.java.classLoader)
        } catch (_: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    // endregion

    // region Persistence

    private var persistJob: Job? = null
    private val persistence = Mutex()

    init {
        scope.launch {
            store.state.map { store.snapshot() }.distinctUntilChanged().drop(1).collect { schedulePersist() }
        }
    }

    fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1_500)
            persistNow()
        }
    }

    private fun schedulePersistNow() {
        persistJob?.cancel()
        persistJob = scope.launch { persistNow() }
    }

    suspend fun persistNow() = persistence.withLock {
        if (!settings.current.restoreTabs) {
            // Tabs saved before the setting was turned off mustn't come back if it's turned on again.
            withContext(Dispatchers.IO) { sessionFile.delete() }
            return@withLock
        }
        // Live pages hand over their history now, so a restart resumes exactly where each tab was.
        pages.forEach { (id, page) -> if (store.state.value.tab(id)?.isPrivate == false) save(id, page) }
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

    /** Restores normal tabs from the last run. Their pages are built only when each tab is shown. */
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

    suspend fun deletePersistedSession() = persistence.withLock {
        persistJob?.cancel()
        withContext(Dispatchers.IO) { sessionFile.delete() }
        engineState.clear()
    }

    // endregion

    companion object {
        private const val TAG = "SessionManager"

        /** Defines `navigator.globalPrivacyControl` as `true`, before any of the page's own script runs. */
        private const val GPC_SCRIPT =
            "try{Object.defineProperty(Navigator.prototype,'globalPrivacyControl'," +
                "{get:function(){return true},configurable:true,enumerable:true})}catch(e){}"

        /** Two addresses without their scheme, as typed and as the engine reports them (it adds a `/` and lower-cases the host). */
        internal fun sameAddress(a: String, b: String): Boolean = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)

        /** True when two URLs only differ by fragment. */
        fun sameDocument(a: String, b: String): Boolean = a.substringBefore('#') == b.substringBefore('#')

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
