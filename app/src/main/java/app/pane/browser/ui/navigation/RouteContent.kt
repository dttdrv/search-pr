package app.pane.browser.ui.navigation

import androidx.compose.runtime.Composable
import app.pane.browser.ui.library.BookmarksScreen
import app.pane.browser.ui.library.DownloadsScreen
import app.pane.browser.ui.library.HistoryScreen
import app.pane.browser.ui.settings.AboutScreen
import app.pane.browser.ui.settings.AppearanceSettingsScreen
import app.pane.browser.ui.settings.ClearDataScreen
import app.pane.browser.ui.settings.FilterListsScreen
import app.pane.browser.ui.settings.PasswordsScreen
import app.pane.browser.ui.settings.PrivacySettingsScreen
import app.pane.browser.ui.settings.SearchSettingsScreen
import app.pane.browser.ui.settings.SettingsScreen
import app.pane.browser.ui.settings.SitePermissionsScreen
import app.pane.browser.ui.settings.SiteSettingsScreen
import app.pane.browser.ui.settings.StartSettingsScreen
import app.pane.browser.ui.settings.TabsSettingsScreen

@Composable
fun RouteContent(route: Route) {
    when (route) {
        Route.Settings -> SettingsScreen()
        Route.SearchSettings -> SearchSettingsScreen()
        Route.PrivacySettings -> PrivacySettingsScreen()
        Route.FilterLists -> FilterListsScreen()
        Route.PasswordSettings -> PasswordsScreen()
        Route.SiteSettings -> SiteSettingsScreen()
        is Route.SitePermissions -> SitePermissionsScreen(route.origin)
        Route.AppearanceSettings -> AppearanceSettingsScreen()
        Route.TabsSettings -> TabsSettingsScreen()
        Route.StartSettings -> StartSettingsScreen()
        Route.ClearData -> ClearDataScreen()
        Route.About -> AboutScreen()
        Route.Bookmarks -> BookmarksScreen()
        Route.History -> HistoryScreen()
        Route.Downloads -> DownloadsScreen()
    }
}
