package app.pane.core.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserReducerTest {
    private var now = 1_000L
    private val store = BrowserStore(clock = { now++ })

    private fun add(id: String, private: Boolean = false, parent: String? = null, select: Boolean = true) =
        store.dispatch(BrowserAction.AddTab(TabState(id, "https://$id.com/", isPrivate = private, parentId = parent), select = select))

    private val ids get() = store.state.value.tabs.map { it.id }
    private val selected get() = store.state.value.selectedTabId

    @Test fun addSelectsAndAppends() {
        add("a"); add("b")
        assertEquals(listOf("a", "b"), ids)
        assertEquals("b", selected)
    }

    @Test fun childrenOpenNextToTheirParent() {
        add("a"); add("b")
        store.dispatch(BrowserAction.SelectTab("a"))
        add("c", parent = "a", select = false)
        add("d", parent = "a", select = false)
        assertEquals(listOf("a", "c", "d", "b"), ids)
    }

    @Test fun closingChildReturnsToParent() {
        add("a"); add("b"); add("c", parent = "a")
        store.dispatch(BrowserAction.RemoveTab("c"))
        assertEquals("a", selected)
    }

    @Test fun closingPrefersRightNeighbourThenLeft() {
        add("a"); add("b"); add("c")
        store.dispatch(BrowserAction.SelectTab("b"))
        store.dispatch(BrowserAction.RemoveTab("b"))
        assertEquals("c", selected)
        store.dispatch(BrowserAction.RemoveTab("c"))
        assertEquals("a", selected)
    }

    @Test fun selectionNeverCrossesPrivacyModes() {
        add("a"); add("p", private = true)
        store.dispatch(BrowserAction.RemoveTab("p"))
        assertNull(selected)
    }

    @Test fun privateTabsAreNeverRemembered() {
        add("p", private = true)
        store.dispatch(BrowserAction.RemoveTab("p"))
        assertTrue(store.state.value.recentlyClosed.isEmpty())
    }

    @Test fun undoRestoresPositionAndSelection() {
        add("a"); add("b"); add("c")
        store.dispatch(BrowserAction.RemoveTab("b"))
        store.dispatch(BrowserAction.UndoClose)
        assertEquals(listOf("a", "b", "c"), ids)
        assertEquals("b", selected)
    }

    @Test fun clearRecentlyClosedForgetsClosedTabs() {
        add("a"); add("b"); add("c")
        store.dispatch(BrowserAction.RemoveTab("c"))
        store.dispatch(BrowserAction.RemoveAllTabs(private = false))
        assertEquals(3, store.state.value.recentlyClosed.size)
        store.dispatch(BrowserAction.ClearRecentlyClosed)
        assertTrue(store.state.value.recentlyClosed.isEmpty())
        store.dispatch(BrowserAction.UndoClose)
        assertTrue(ids.isEmpty())
    }

    @Test fun removeAllOnlyTouchesOneMode() {
        add("a"); add("p", private = true); add("b")
        store.dispatch(BrowserAction.RemoveAllTabs(private = true))
        assertEquals(listOf("a", "b"), ids)
        assertEquals("b", selected)
    }

    @Test fun moveTab() {
        add("a"); add("b"); add("c")
        store.dispatch(BrowserAction.MoveTab("c", 0))
        assertEquals(listOf("c", "a", "b"), ids)
    }

    @Test fun snapshotSkipsPrivateTabsAndRoundTrips() {
        add("a"); add("p", private = true)
        val snap = store.snapshot()
        assertEquals(listOf("a"), snap.tabs.map { it.id })
        assertNull(snap.selectedTabId)
        val restored = BrowserStore()
        restored.dispatch(BrowserAction.Restore(BrowserStore.tabsFrom(snap), "a"))
        assertEquals("a", restored.state.value.selectedTabId)
    }

    @Test fun updateTab() {
        add("a")
        store.updateTab("a") { it.copy(title = "Hello", progress = 50) }
        assertEquals("Hello", store.state.value.tab("a")!!.title)
    }
}
