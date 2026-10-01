package app.pane.core.tabs

import kotlinx.serialization.Serializable

/** Everything the chrome knows about a tab. Engine objects live elsewhere, keyed by [id]. */
data class TabState(
    val id: String,
    val url: String,
    val title: String = "",
    val isPrivate: Boolean = false,
    /** The tab that opened this one; closing a child returns to its parent, like Safari. */
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val lastAccessed: Long = 0L,
    val loading: Boolean = false,
    /** 0..100 */
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val security: SecurityState = SecurityState.Unknown,
    /** Page-provided `theme-color`, as ARGB, used to tint the chrome. */
    val themeColor: Int? = null,
    val desktopMode: Boolean = false,
    val fullscreen: Boolean = false,
    val mediaPlaying: Boolean = false,
    /** The page looks like an article, so the menu can offer Reader. */
    val readerable: Boolean = false,
    /** The page is currently showing its article in place of itself. */
    val inReaderMode: Boolean = false,
    /** Number of trackers blocked on the current page. */
    /** Bumped whenever a fresh thumbnail has been captured. */
    val thumbnailVersion: Int = 0,
    /** Set when the engine process for this tab crashed; the UI offers a reload. */
    val crashed: Boolean = false,
)

enum class SecurityState { Unknown, Secure, Insecure, Broken }

data class ClosedTab(val tab: TabState, val index: Int, val closedAt: Long)

data class BrowserState(
    val tabs: List<TabState> = emptyList(),
    val selectedTabId: String? = null,
    /** Most recent first; private tabs are never kept here. */
    val recentlyClosed: List<ClosedTab> = emptyList(),
) {
    val selectedTab: TabState? get() = tabs.firstOrNull { it.id == selectedTabId }
    val normalTabs: List<TabState> get() = tabs.filter { !it.isPrivate }
    val privateTabs: List<TabState> get() = tabs.filter { it.isPrivate }
    fun tab(id: String?): TabState? = if (id == null) null else tabs.firstOrNull { it.id == id }
    fun tabsIn(private: Boolean): List<TabState> = tabs.filter { it.isPrivate == private }
}

/** What is written to disk to restore normal tabs after the process dies. Private tabs never are. */
@Serializable
data class PersistedTab(
    val id: String,
    val url: String,
    val title: String,
    val parentId: String? = null,
    val createdAt: Long,
    val lastAccessed: Long,
    val desktopMode: Boolean = false,
    /** Opaque engine history (back/forward list, scroll positions). */
    val engineState: String? = null,
)

@Serializable
data class PersistedSession(
    val version: Int = 1,
    val tabs: List<PersistedTab>,
    val selectedTabId: String?,
)
