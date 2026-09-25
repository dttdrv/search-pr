package app.pane.browser.engine

import app.pane.browser.settings.SettingsStore
import app.pane.core.search.SearchEngines
import app.pane.core.tabs.BrowserState
import app.pane.core.tabs.BrowserStore
import app.pane.core.tabs.TabState
import app.pane.core.url.InputAction
import app.pane.core.url.UrlInput
import kotlinx.coroutines.flow.StateFlow

/**
 * What the chrome asks for, in user terms ("open this", "search that", "new private tab").
 * Screens call this instead of poking sessions directly.
 */
class BrowserController(
    private val store: BrowserStore,
    val sessions: SessionManager,
    private val settings: SettingsStore,
) {
    val state: StateFlow<BrowserState> get() = store.state
    val selectedTab: TabState? get() = store.state.value.selectedTab

    /** Whether the tab strip currently shows private tabs (last selected tab's mode, or the explicit toggle). */
    val isPrivate: Boolean get() = selectedTab?.isPrivate ?: false

    /** Handles text submitted from the address bar. */
    fun submit(text: String, tabId: String? = store.state.value.selectedTabId, private: Boolean = isPrivate) {
        val keyword = SearchEngines.parseKeyword(text)
        val url = when {
            keyword != null && keyword.second.isNotBlank() -> keyword.first.searchUrl(keyword.second)
            else -> when (val action = UrlInput.classify(text)) {
                null -> return
                is InputAction.Navigate -> action.url
                is InputAction.Search -> SearchEngines.byId(settings.current.searchEngineId).searchUrl(action.query)
                is InputAction.External -> {
                    sessions.load(tabId ?: return, action.url)
                    return
                }
            }
        }
        if (tabId == null) {
            sessions.openTab(url, private)
        } else {
            sessions.load(tabId, url)
        }
    }

    /**
     * Opens [url]. With [newTab] a tab is created (in the background when [background]); otherwise
     * the current tab navigates, or a new one is made if there is none.
     */
    fun open(url: String, newTab: Boolean = false, private: Boolean = isPrivate, background: Boolean = false, fromTabId: String? = null) {
        val current = store.state.value.selectedTabId
        if (!newTab && current != null && store.state.value.tab(current)?.isPrivate == private) {
            sessions.load(current, url)
        } else {
            val id = sessions.openTab(url, private, parentId = fromTabId, select = !background)
            if (background) sessions.ensureSession(id)
        }
    }

    fun newTab(private: Boolean = isPrivate): String = sessions.openTab(null, private)

    fun select(tabId: String) = sessions.selectTab(tabId)

    fun close(tabId: String) = sessions.closeTab(tabId)

    fun closeAll(private: Boolean) = sessions.closeAllTabs(private)

    fun undoClose(): Boolean = sessions.undoCloseTab()

    fun goBack() = store.state.value.selectedTabId?.let(sessions::goBack)
    fun goForward() = store.state.value.selectedTabId?.let(sessions::goForward)
    fun reload(bypassCache: Boolean = false) = store.state.value.selectedTabId?.let { sessions.reload(it, bypassCache) }
    fun stop() = store.state.value.selectedTabId?.let(sessions::stop)

    fun toggleDesktopMode() {
        val tab = selectedTab ?: return
        sessions.setDesktopMode(tab.id, !tab.desktopMode)
    }

    /** The query to show when editing a search results page, instead of the raw URL. */
    fun searchTermsFor(url: String): String? = SearchEngines.all.firstNotNullOfOrNull { it.extractQuery(url) }
}
