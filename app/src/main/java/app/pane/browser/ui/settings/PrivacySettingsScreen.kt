package app.pane.browser.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.browser.BiometricGate
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.HttpsMode

/** The secure-connection choices in the order they are offered, with what each is called. */
private val HttpsChoices: List<Pair<HttpsMode, String>> = listOf(
    HttpsMode.Off to "Off",
    HttpsMode.First to "Prefer HTTPS",
    HttpsMode.Only to "HTTPS only",
)

/** How Android's Private DNS is set, in a word: Automatic (the default), Off, or the provider's host name. */
private fun privateDnsLabel(context: Context): String {
    val resolver = context.contentResolver
    return when (Settings.Global.getString(resolver, "private_dns_mode")) {
        "off" -> "Off"
        "hostname" -> Settings.Global.getString(resolver, "private_dns_specifier")?.takeIf { it.isNotBlank() } ?: "Provider"
        else -> "Automatic"
    }
}

/** Private DNS has no public shortcut of its own; it sits in Network & internet, under Advanced. */
private fun openNetworkSettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/**
 * What pages may do to you and what the browser checks for you, then what Pane remembers. Nothing
 * else is on show; the rarely used switches sit under Advanced.
 */
@Composable
fun PrivacySettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.PrivacySettings)
    // The same check the browser uses to lock, so the switch never promises a lock that won't happen.
    val canLock = remember { BiometricGate.canLock(context) }
    var httpsSheet by rememberSaveable { mutableStateOf(false) }

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    Box(Modifier.fillMaxSize()) {
        LargeTitleScaffold(title = "Privacy", onBack = navigator::pop, backLabel = backLabel) {
            item(key = "protection") {
                GroupedSection(modifier = Modifier.arrive(0)) {
                    row {
                        SwitchRow(
                            title = "Block ads & trackers",
                            checked = settings.blockAds,
                            onCheckedChange = { on -> update { it.copy(blockAds = on) } },
                        )
                    }
                    row {
                        NavRow(
                            title = "Filter lists",
                            value = "${settings.enabledFilterLists.size} on",
                            onClick = { navigator.push(Route.FilterLists) },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Block third-party cookies",
                            checked = settings.blockThirdPartyCookies,
                            onCheckedChange = { on -> update { it.copy(blockThirdPartyCookies = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Safe Browsing",
                            checked = settings.safeBrowsing,
                            onCheckedChange = { on -> update { it.copy(safeBrowsing = on) } },
                        )
                    }
                    row {
                        NavRow(
                            title = "Secure connections",
                            value = HttpsChoices.first { it.first == settings.httpsMode }.second,
                            onClick = { httpsSheet = true },
                        )
                    }
                    row {
                        // DNS belongs to the system: a web view can't choose its own resolver. This row
                        // shows what Android is set to and opens its network settings, where Private DNS lives.
                        NavRow(
                            title = "Private DNS",
                            value = remember { privateDnsLabel(context) },
                            onClick = { openNetworkSettings(context) },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Remove tracking parameters",
                            checked = settings.stripTrackingParams,
                            onCheckedChange = { on -> update { it.copy(stripTrackingParams = on) } },
                        )
                    }
                }
            }

            item(key = "history") {
                GroupedSection(modifier = Modifier.arrive(1), header = "History") {
                    row {
                        SwitchRow(
                            title = "Remember history",
                            checked = settings.rememberHistory,
                            onCheckedChange = { on -> update { it.copy(rememberHistory = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Clear data on exit",
                            checked = settings.clearOnExit,
                            onCheckedChange = { on -> update { it.copy(clearOnExit = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Lock private tabs",
                            subtitle = if (canLock) null else "Set up a screen lock first",
                            checked = settings.lockPrivateTabs && canLock,
                            enabled = canLock,
                            onCheckedChange = { on -> update { it.copy(lockPrivateTabs = on) } },
                        )
                    }
                }
            }

            item(key = "advanced") {
                AdvancedSection(modifier = Modifier.arrive(2)) {
                    GroupedSection(
                        header = "Content",
                        footer = if (settings.javascriptEnabled) null else "Many sites won't work without it.",
                    ) {
                        row {
                            SwitchRow(
                                title = "JavaScript",
                                checked = settings.javascriptEnabled,
                                onCheckedChange = { on -> update { it.copy(javascriptEnabled = on) } },
                            )
                        }
                    }
                    GroupedSection(header = "Sent to sites") {
                        row {
                            SwitchRow(
                                title = "Global Privacy Control",
                                checked = settings.globalPrivacyControl,
                                onCheckedChange = { on -> update { it.copy(globalPrivacyControl = on) } },
                            )
                        }
                    }
                    GroupedSection(header = "Private tabs") {
                        row {
                            SwitchRow(
                                title = "Block screenshots",
                                checked = settings.secureScreenInPrivate,
                                onCheckedChange = { on -> update { it.copy(secureScreenInPrivate = on) } },
                            )
                        }
                    }
                }
            }
        }

        ChoiceSheet(
            visible = httpsSheet,
            title = "Secure connections",
            message = "HTTPS only asks before opening a site that has no secure address.",
            options = HttpsChoices.map { it.second },
            selectedIndex = HttpsChoices.indexOfFirst { it.first == settings.httpsMode },
            onSelect = { index ->
                update { it.copy(httpsMode = HttpsChoices[index].first) }
                httpsSheet = false
            },
            onDismiss = { httpsSheet = false },
        )
    }
}
