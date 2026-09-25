package app.pane.browser.ui.navigation

import androidx.compose.runtime.Composable
import app.pane.browser.ui.extensions.AddonStoreScreen
import app.pane.browser.ui.extensions.ExtensionDetailScreen
import app.pane.browser.ui.extensions.ExtensionOptionsScreen
import app.pane.browser.ui.extensions.ExtensionsScreen
import app.pane.browser.ui.library.BookmarksScreen
import app.pane.browser.ui.library.DownloadsScreen
import app.pane.browser.ui.library.HistoryScreen
import app.pane.browser.ui.settings.AboutScreen
import app.pane.browser.ui.settings.AppearanceSettingsScreen
import app.pane.browser.ui.settings.ClearDataScreen
import app.pane.browser.ui.settings.PrivacySettingsScreen
import app.pane.browser.ui.settings.SearchSettingsScreen
import app.pane.browser.ui.settings.SettingsScreen
import app.pane.browser.ui.settings.SitePermissionsScreen
import app.pane.browser.ui.settings.SiteSettingsScreen

@Composable
fun RouteContent(route: Route) {
    when (route) {
        Route.Settings -> SettingsScreen()
        Route.SearchSettings -> SearchSettingsScreen()
        Route.PrivacySettings -> PrivacySettingsScreen()
        Route.SiteSettings -> SiteSettingsScreen()
        is Route.SitePermissions -> SitePermissionsScreen(route.origin)
        Route.AppearanceSettings -> AppearanceSettingsScreen()
        Route.ClearData -> ClearDataScreen()
        Route.About -> AboutScreen()
        Route.Bookmarks -> BookmarksScreen()
        Route.History -> HistoryScreen()
        Route.Downloads -> DownloadsScreen()
        Route.Extensions -> ExtensionsScreen()
        Route.AddonStore -> AddonStoreScreen()
        is Route.ExtensionDetail -> ExtensionDetailScreen(route.extensionId)
        is Route.ExtensionOptions -> ExtensionOptionsScreen(route.extensionId)
    }
}
