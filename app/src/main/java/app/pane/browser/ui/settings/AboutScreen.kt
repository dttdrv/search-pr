package app.pane.browser.ui.settings

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.BuildConfig
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.AlertAction
import app.pane.browser.ui.components.AlertStyle
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.components.LocalToasts
import app.pane.browser.ui.components.PaneAlert
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.rememberHaptics

/**
 * Version and the system WebView underneath. Licenses sit under Advanced; putting every setting back
 * to its default is the last row.
 */
@Composable
fun AboutScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val backLabel = rememberBackLabel(Route.About)
    // The WebView implementation the phone currently uses (Chrome, Android System WebView, ...).
    val webView = remember { WebView.getCurrentWebViewPackage()?.versionName ?: "System" }
    val toasts = LocalToasts.current
    val haptics = rememberHaptics()
    var confirmReset by remember { mutableStateOf(false) }

    fun openLink(url: String) {
        container.browser.open(url, newTab = true, private = false)
        navigator.closeAll()
    }

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "About", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "version") {
                GroupedSection(modifier = Modifier.arrive(0)) {
                    row {
                        ListRow(
                            title = "Pane",
                            modifier = Modifier.heightIn(min = 56.dp),
                            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        )
                    }
                    row {
                        ListRow(
                            title = "Android WebView",
                            modifier = Modifier.heightIn(min = 56.dp),
                            value = webView,
                        )
                    }
                }
            }
            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(1)) {
                    GroupedSection(header = "Open source licenses") {
                        row {
                            ListRow(
                                title = "AndroidX and Compose",
                                modifier = Modifier.heightIn(min = 56.dp),
                                value = "Apache-2.0",
                                onClick = { openLink("https://www.apache.org/licenses/LICENSE-2.0") },
                            )
                        }
                        row {
                            ListRow(
                                title = "Kotlin and kotlinx",
                                modifier = Modifier.heightIn(min = 56.dp),
                                value = "Apache-2.0",
                                onClick = { openLink("https://www.apache.org/licenses/LICENSE-2.0") },
                            )
                        }
                    }
                }
            }
            item(key = "reset") {
                GroupedSection(modifier = Modifier.arrive(2)) {
                    row { ActionButtonRow("Reset settings", onClick = { confirmReset = true }, destructive = true) }
                }
            }
        }

        PaneAlert(
            visible = confirmReset,
            title = "Reset settings?",
            message = "Every setting goes back to its default. Tabs, bookmarks and history stay.",
            actions = listOf(
                AlertAction("Cancel", AlertStyle.Cancel) { confirmReset = false },
                AlertAction("Reset", AlertStyle.Destructive) {
                    confirmReset = false
                    container.settings.reset()
                    haptics.confirm()
                    toasts.show("Settings reset", PaneIcons.Check)
                },
            ),
            onDismissRequest = { confirmReset = false },
        )
    }
}
