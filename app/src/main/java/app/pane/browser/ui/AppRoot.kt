package app.pane.browser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.AppContainer
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.browser.BrowserScreen
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.ToastHost
import app.pane.browser.ui.components.ToastState
import app.pane.browser.ui.extensions.ExtensionOverlays
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Navigator
import app.pane.browser.ui.navigation.RouteContent
import app.pane.browser.ui.navigation.RouteHost
import app.pane.browser.ui.prompts.PromptHost
import app.pane.browser.ui.theme.PaneTheme

/** Layering, bottom to top: browser, pushed screens, extension overlays, page prompts, toasts. */
@Composable
fun AppRoot(container: AppContainer, navigator: Navigator) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val toasts = remember { ToastState() }
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalNavigator provides navigator,
        LocalToasts provides toasts,
    ) {
        PaneTheme(mode = settings.theme, hapticsEnabled = settings.haptics, reduceMotion = settings.reduceMotion) {
            Box(Modifier.fillMaxSize()) {
                BrowserScreen()
                RouteHost(navigator) { RouteContent(it) }
                ExtensionOverlays()
                PromptHost()
                ToastHost(toasts)
            }
        }
    }
}
