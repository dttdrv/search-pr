package app.pane.core.tabs

sealed interface BrowserAction {
    data class AddTab(val tab: TabState, val select: Boolean = true, val index: Int? = null) : BrowserAction
    data class RemoveTab(val id: String) : BrowserAction
    data class RemoveTabs(val ids: Set<String>) : BrowserAction
    data class RemoveAllTabs(val private: Boolean) : BrowserAction
    data class SelectTab(val id: String) : BrowserAction
    data class MoveTab(val id: String, val toIndex: Int) : BrowserAction
    data object UndoClose : BrowserAction
    data class Restore(val tabs: List<TabState>, val selectedTabId: String?) : BrowserAction

    /** Any change to a single tab's fields: url, title, progress, … */
    data class UpdateTab(val id: String, val update: (TabState) -> TabState) : BrowserAction
}
