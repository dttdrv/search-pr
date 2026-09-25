package app.pane.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.ChromeButton
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.url.UrlDisplay

/** Temporary browser chrome; replaced by the full iOS-style chrome. */
@Composable
fun BrowserScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val state by container.store.state.collectAsStateWithLifecycle()
    val tab = state.selectedTab
    val colors = PaneTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            EngineView(tab, Modifier.fillMaxSize(), coverColor = colors.background.toArgb())
        }
        Row(
            Modifier.fillMaxWidth().background(colors.chrome).windowInsetsPadding(WindowInsets.navigationBars).height(52.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(PaneIcons.Back, "Back", { container.browser.goBack() }, enabled = tab?.canGoBack == true)
            Box(
                Modifier.weight(1f).height(38.dp).clip(PaneShapes.medium).background(colors.fill),
                contentAlignment = Alignment.Center,
            ) {
                Text(tab?.url?.let(UrlDisplay::toolbarText).orEmpty().ifEmpty { "Search or enter address" }, color = colors.label)
            }
            ChromeButton(PaneIcons.Tabs, "Tabs", { container.browser.newTab() })
            ChromeButton(PaneIcons.More, "Menu", { navigator.push(Route.Settings) })
        }
    }
}
