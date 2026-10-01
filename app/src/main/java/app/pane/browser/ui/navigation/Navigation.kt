package app.pane.browser.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens pushed over the browser. The browser itself is the implicit root. */
sealed interface Route {
    // Settings
    data object Settings : Route
    data object SearchSettings : Route
    data object PrivacySettings : Route
    data object FilterLists : Route
    data object PasswordSettings : Route
    data object SiteSettings : Route
    data class SitePermissions(val origin: String) : Route
    data object AppearanceSettings : Route
    data object TabsSettings : Route
    data object StartSettings : Route
    data object ClearData : Route
    data object About : Route

    // Library
    data object Bookmarks : Route
    data object History : Route
    data object Downloads : Route
}

@Stable
class Navigator {
    val stack = mutableStateListOf<Route>()
    var closeAllVersion by mutableIntStateOf(0)
        private set

    val isEmpty: Boolean get() = stack.isEmpty()
    val top: Route? get() = stack.lastOrNull()

    // a double tap opens a screen once: each screen's saved state is keyed by its route
    fun push(route: Route) {
        if (top != route) stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Dismisses every screen and browser overlay, even when no screen is pushed. */
    fun closeAll() {
        stack.clear()
        closeAllVersion++
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
