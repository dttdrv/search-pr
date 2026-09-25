package app.pane.core.tabs

object BrowserReducer {
    const val MAX_RECENTLY_CLOSED = 25

    fun reduce(state: BrowserState, action: BrowserAction, now: Long): BrowserState = when (action) {
        is BrowserAction.AddTab -> addTab(state, action, now)
        is BrowserAction.RemoveTab -> removeTabs(state, setOf(action.id), now)
        is BrowserAction.RemoveTabs -> removeTabs(state, action.ids, now)
        is BrowserAction.RemoveAllTabs -> removeTabs(state, state.tabsIn(action.private).map { it.id }.toSet(), now)
        is BrowserAction.SelectTab -> selectTab(state, action.id, now)
        is BrowserAction.MoveTab -> moveTab(state, action.id, action.toIndex)
        BrowserAction.UndoClose -> undoClose(state, now)
        BrowserAction.ClearRecentlyClosed -> state.copy(recentlyClosed = emptyList())
        is BrowserAction.Restore -> restore(state, action)
        is BrowserAction.UpdateTab -> state.copy(tabs = state.tabs.map { if (it.id == action.id) action.update(it) else it })
    }

    private fun addTab(state: BrowserState, action: BrowserAction.AddTab, now: Long): BrowserState {
        if (state.tabs.any { it.id == action.tab.id }) return state
        val tab = action.tab.copy(
            createdAt = action.tab.createdAt.takeIf { it > 0 } ?: now,
            lastAccessed = if (action.select) now else action.tab.lastAccessed,
        )
        val index = action.index?.coerceIn(0, state.tabs.size)
            // Children open right after their parent's other children, as in Safari and Firefox.
            ?: tab.parentId?.let { parentId ->
                val parentIndex = state.tabs.indexOfFirst { it.id == parentId }
                if (parentIndex < 0) {
                    null
                } else {
                    var i = parentIndex + 1
                    while (i < state.tabs.size && state.tabs[i].parentId == parentId) i++
                    i
                }
            }
            ?: state.tabs.size
        val tabs = state.tabs.toMutableList().apply { add(index, tab) }
        return state.copy(tabs = tabs, selectedTabId = if (action.select) tab.id else state.selectedTabId)
    }

    private fun removeTabs(state: BrowserState, ids: Set<String>, now: Long): BrowserState {
        val removed = state.tabs.withIndex().filter { it.value.id in ids }
        if (removed.isEmpty()) return state
        val remaining = state.tabs.filter { it.id !in ids }
        val selected = state.selectedTab
        val newSelected = when {
            selected == null -> null
            selected.id !in ids -> selected.id
            else -> nextSelection(state.tabs, selected, ids)
        }
        val closed = removed.filter { !it.value.isPrivate }
            .map { ClosedTab(it.value.copy(loading = false, progress = 0), it.index, now) }
            .reversed()
        return state.copy(
            tabs = remaining,
            selectedTabId = newSelected,
            recentlyClosed = (closed + state.recentlyClosed).take(MAX_RECENTLY_CLOSED),
        )
    }

    /**
     * Picks the tab to show after [closing] goes away: its parent if still open, otherwise the
     * nearest neighbour with the same privacy mode (right first, then left). Null means the mode is
     * now empty.
     */
    internal fun nextSelection(tabs: List<TabState>, closing: TabState, removedIds: Set<String>): String? {
        val sameMode = { t: TabState -> t.isPrivate == closing.isPrivate && t.id !in removedIds }
        closing.parentId?.let { parentId ->
            tabs.firstOrNull { it.id == parentId && sameMode(it) }?.let { return it.id }
        }
        val index = tabs.indexOfFirst { it.id == closing.id }
        for (i in index + 1 until tabs.size) if (sameMode(tabs[i])) return tabs[i].id
        for (i in index - 1 downTo 0) if (sameMode(tabs[i])) return tabs[i].id
        return null
    }

    private fun selectTab(state: BrowserState, id: String, now: Long): BrowserState {
        if (state.tabs.none { it.id == id }) return state
        return state.copy(
            selectedTabId = id,
            tabs = state.tabs.map { if (it.id == id) it.copy(lastAccessed = now) else it },
        )
    }

    private fun moveTab(state: BrowserState, id: String, toIndex: Int): BrowserState {
        val from = state.tabs.indexOfFirst { it.id == id }
        if (from < 0) return state
        val tabs = state.tabs.toMutableList()
        val tab = tabs.removeAt(from)
        tabs.add(toIndex.coerceIn(0, tabs.size), tab)
        return state.copy(tabs = tabs)
    }

    private fun undoClose(state: BrowserState, now: Long): BrowserState {
        val last = state.recentlyClosed.firstOrNull() ?: return state
        val tab = last.tab.copy(lastAccessed = now)
        val tabs = state.tabs.toMutableList().apply { add(last.index.coerceIn(0, size), tab) }
        return state.copy(tabs = tabs, selectedTabId = tab.id, recentlyClosed = state.recentlyClosed.drop(1))
    }

    private fun restore(state: BrowserState, action: BrowserAction.Restore): BrowserState {
        val existingIds = state.tabs.map { it.id }.toSet()
        val restored = action.tabs.filter { it.id !in existingIds }
        val tabs = restored + state.tabs
        val selected = state.selectedTabId ?: action.selectedTabId?.takeIf { id -> tabs.any { it.id == id } }
        return state.copy(tabs = tabs, selectedTabId = selected)
    }
}
