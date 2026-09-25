package app.pane.browser.ui.components

import androidx.compose.runtime.Composable
import app.pane.browser.ui.navigation.LocalNavigator

/** Temporary body for screens that haven't been built yet. */
@Composable
fun PlaceholderScreen(title: String) {
    val navigator = LocalNavigator.current
    LargeTitleScaffold(title = title, onBack = navigator::pop) {}
}
