package app.pane.browser

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import app.pane.browser.data.BookmarksRepository
import app.pane.browser.data.DownloadsRepository
import app.pane.browser.data.HistoryRepository
import app.pane.browser.data.PaneDatabase
import app.pane.browser.downloads.DownloadController
import app.pane.browser.engine.BrowserController
import app.pane.browser.engine.EngineRuntime
import app.pane.browser.engine.PageSnapshots
import app.pane.browser.engine.PrivacyStats
import app.pane.browser.engine.PromptQueue
import app.pane.browser.engine.SessionManager
import app.pane.browser.engine.Thumbnails
import app.pane.browser.engine.WebFetcher
import app.pane.browser.engine.prompts.ContextMenus
import app.pane.browser.engine.prompts.PromptEnvironment
import app.pane.browser.engine.prompts.WebPermissionDelegate
import app.pane.browser.engine.prompts.WebPromptDelegate
import app.pane.browser.extensions.ExtensionsManager
import app.pane.browser.settings.SettingsStore
import app.pane.core.tabs.BrowserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime

/**
 * Manual dependency graph. Created lazily from the main activity so Gecko's child processes,
 * which also run [PaneApp.onCreate], never start a runtime of their own.
 */
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
    val privacyStats = PrivacyStats(app)

    val runtime: GeckoRuntime = EngineRuntime.create(app, settings.current)
    val fetcher = WebFetcher(runtime)
    val sessions = SessionManager(app, runtime, store, settings, history, scope)
    val browser = BrowserController(store, sessions, settings)
    val downloads = DownloadController(app, downloadsRepository, scope)
    val extensions = ExtensionsManager(app, runtime, sessions, browser, store, fetcher, scope)

    init {
        downloads.runtime = runtime
        PromptEnvironment.install(runtime, settings, store)
        sessions.promptDelegateFactory = { tabId -> WebPromptDelegate(tabId, prompts) }
        sessions.permissionDelegateFactory = { tabId -> WebPermissionDelegate(tabId, prompts) }
        sessions.onExternalResponse = { tabId, response ->
            downloads.onExternalResponse(tabId, response, store.state.value.tab(tabId)?.isPrivate == true)
        }
        sessions.onTrackerBlocked = privacyStats::increment
        sessions.onTabClosed = { tabId ->
            prompts.dismissForTab(tabId)
            thumbnails.remove(tabId)
        }
        sessions.onContextMenu = { tabId, x, y, element ->
            ContextMenus.request(prompts, tabId, store.state.value.tab(tabId)?.isPrivate == true, x, y, element)
        }
        scope.launch {
            settings.state.drop(1).distinctUntilChanged().collect { EngineRuntime.apply(runtime, it) }
        }
        extensions.start()
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
