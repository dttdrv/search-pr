package app.pane.browser

import android.app.Application
import android.webkit.WebView
import androidx.compose.runtime.staticCompositionLocalOf
import app.pane.browser.data.BookmarksRepository
import app.pane.browser.data.DownloadsRepository
import app.pane.browser.data.HistoryRepository
import app.pane.browser.data.PaneDatabase
import app.pane.browser.downloads.DownloadController
import app.pane.browser.engine.AdBlocker
import app.pane.browser.engine.BrowserController
import app.pane.browser.engine.FilterLists
import app.pane.browser.engine.FaviconStore
import app.pane.browser.engine.PageSnapshots
import app.pane.browser.engine.Profiles
import app.pane.browser.engine.PromptQueue
import app.pane.browser.engine.SessionManager
import app.pane.browser.engine.SitePermissionStore
import app.pane.browser.engine.Thumbnails
import app.pane.browser.engine.WebFetcher
import app.pane.browser.engine.prompts.ContextMenus
import app.pane.browser.engine.prompts.PromptBridge
import app.pane.browser.settings.NightMode
import app.pane.browser.settings.SettingsStore
import app.pane.core.tabs.BrowserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual dependency graph, created lazily from the main activity. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(app)
    val database = PaneDatabase(app)
    val history = HistoryRepository(database)
    val bookmarks = BookmarksRepository(database)
    val downloadsRepository = DownloadsRepository(database)
    val store = BrowserStore()
    val prompts = PromptQueue()
    val thumbnails = Thumbnails(app)
    val snapshots = PageSnapshots()
    val sitePermissions = SitePermissionStore(app)
    val filterLists = FilterLists(app, settings, scope)
    val adBlocker = AdBlocker(filterLists) { settings.current.blockAds }
    val fetcher = WebFetcher()
    val favicons = FaviconStore(app)
    val sessions = SessionManager(app, store, settings, history, adBlocker, scope)
    val browser = BrowserController(store, sessions, settings)
    val downloads = DownloadController(app, downloadsRepository, scope)

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        // A private profile never survives a process restart.
        Profiles.dropPrivate()

        // The saved filter index loads in the background; the first update check waits until the UI has been up a moment.
        filterLists.start()
        scope.launch {
            delay(FILTER_CHECK_DELAY_MS)
            filterLists.updateIfStale()
        }

        sessions.bridgeFactory = { tabId -> PromptBridge(tabId, prompts, store, sitePermissions, settings) }
        sessions.onDownload = downloads::start
        sessions.onContextMenu = { tabId, x, y, hit ->
            ContextMenus.request(prompts, tabId, store.state.value.tab(tabId)?.isPrivate == true, x, y, hit)
        }
        // Site icons come from normal-tab pages and, like history, are only kept while history is on.
        sessions.onFavicon = { pageUrl, icon ->
            val isPrivate = store.state.value.tabs.firstOrNull { it.url == pageUrl }?.isPrivate == true
            if (!isPrivate && settings.current.rememberHistory) favicons.put(pageUrl, icon)
        }
        sessions.onTabClosed = { tabId ->
            prompts.dismissForTab(tabId)
            thumbnails.remove(tabId)
            snapshots.remove(tabId)
        }
        scope.launch {
            settings.state.drop(1).distinctUntilChanged().collect {
                sessions.applySettings(it)
                sessions.schedulePersist()
            }
        }
        scope.launch { settings.state.map { it.theme }.distinctUntilChanged().collect { NightMode.apply(app, it) } }
    }
}

private const val FILTER_CHECK_DELAY_MS = 4_000L

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
