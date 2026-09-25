package app.pane.browser.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens pushed over the browser. The browser itself is the implicit root. */
sealed interface Route {
    // Settings
    data object Settings : Route
    data object SearchSettings : Route
    data object PrivacySettings : Route
    data object PasswordSettings : Route
    data object SiteSettings : Route
    data class SitePermissions(val origin: String) : Route
    data object AppearanceSettings : Route
    data object ClearData : Route
    data object About : Route

    // Library
    data object Bookmarks : Route
    data object History : Route
    data object Downloads : Route

    // Extensions
    data object Extensions : Route
    data object AddonStore : Route
    data class ExtensionDetail(val extensionId: String) : Route
    data class ExtensionOptions(val extensionId: String) : Route
}

@Stable
class Navigator {
    val stack = mutableStateListOf<Route>()

    val isEmpty: Boolean get() = stack.isEmpty()
    val top: Route? get() = stack.lastOrNull()

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Dismisses every screen, returning to the browser. */
    fun closeAll() {
        stack.clear()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
