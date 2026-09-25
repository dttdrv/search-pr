package app.pane.core.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for tab state. Thread-safe; [dispatch] may be called from any thread.
 */
class BrowserStore(
    initial: BrowserState = BrowserState(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    fun dispatch(action: BrowserAction) {
        _state.update { BrowserReducer.reduce(it, action, clock()) }
    }

    fun updateTab(id: String, update: (TabState) -> TabState) = dispatch(BrowserAction.UpdateTab(id, update))

    /** Normal tabs only; [engineState] supplies each tab's serialized engine history when known. */
    fun snapshot(engineState: (String) -> String? = { null }): PersistedSession {
        val s = state.value
        val tabs = s.normalTabs.map {
            PersistedTab(it.id, it.url, it.title, it.parentId, it.createdAt, it.lastAccessed, it.desktopMode, engineState(it.id))
        }
        return PersistedSession(tabs = tabs, selectedTabId = s.selectedTabId?.takeIf { id -> tabs.any { it.id == id } })
    }

    companion object {
        fun tabsFrom(session: PersistedSession): List<TabState> = session.tabs.map {
            TabState(
                id = it.id, url = it.url, title = it.title, parentId = it.parentId,
                createdAt = it.createdAt, lastAccessed = it.lastAccessed, desktopMode = it.desktopMode,
            )
        }
    }
}
