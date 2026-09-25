package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pane.browser.BuildConfig
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ListRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import org.mozilla.geckoview.BuildConfig as GeckoBuildConfig

/** Who made this, what it runs on, and the promise that nothing leaves the device. */
@Composable
fun AboutScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val colors = PaneTheme.colors
    val backLabel = rememberBackLabel(Route.About)

    fun openLink(url: String) {
        container.browser.open(url, newTab = true, private = false)
        navigator.closeAll()
    }

    LargeTitleScaffold(title = "About", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "hero") {
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(size = 96.dp)
                Spacer(Modifier.height(14.dp))
                Text("Pane", style = PaneTheme.type.title1, color = colors.label)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = PaneTheme.type.subheadline,
                    color = colors.secondaryLabel,
                )
            }
        }
        item(key = "privacy") {
            GroupedSection(
                header = "Your Privacy",
                footer = "No telemetry. No accounts. Your data stays on this device.",
                separatorInset = 57.dp,
            ) {
                row {
                    ListRow(
                        title = "No Telemetry",
                        subtitle = "No usage statistics, crash reports or ads",
                        leading = { IconTile(PaneIcons.ShieldCheck, TileColors.Green) },
                    )
                }
                row {
                    ListRow(
                        title = "No Accounts",
                        subtitle = "Nothing to sign in to, nothing synced to a server",
                        leading = { IconTile(PaneIcons.Key, TileColors.Blue) },
                    )
                }
                row {
                    ListRow(
                        title = "On This Device",
                        subtitle = "History, bookmarks and settings never leave your phone",
                        leading = { IconTile(PaneIcons.Phone, TileColors.Indigo) },
                    )
                }
            }
        }
        item(key = "engine") {
            GroupedSection(header = "Engine") {
                row { ListRow(title = "Pane", value = BuildConfig.VERSION_NAME) }
                row { ListRow(title = "Gecko", value = GeckoBuildConfig.MOZILLA_VERSION) }
            }
        }
        item(key = "licenses") {
            GroupedSection(header = "Open Source", footer = "Pane is built on these projects. Tap one to read its license.") {
                row {
                    ListRow(
                        title = "GeckoView",
                        subtitle = "Mozilla",
                        value = "MPL-2.0",
                        onClick = { openLink("https://www.mozilla.org/MPL/2.0/") },
                    )
                }
                row {
                    ListRow(
                        title = "AndroidX & Jetpack Compose",
                        subtitle = "The Android Open Source Project",
                        value = "Apache-2.0",
                        onClick = { openLink("https://www.apache.org/licenses/LICENSE-2.0") },
                    )
                }
                row {
                    ListRow(
                        title = "Kotlin & kotlinx",
                        subtitle = "JetBrains",
                        value = "Apache-2.0",
                        onClick = { openLink("https://www.apache.org/licenses/LICENSE-2.0") },
                    )
                }
                row {
                    ListRow(
                        title = "Readability",
                        subtitle = "Mozilla",
                        value = "Apache-2.0",
                        onClick = { openLink("https://github.com/mozilla/readability/blob/main/LICENSE.md") },
                    )
                }
            }
        }
    }
}
