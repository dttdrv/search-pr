package app.pane.browser.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.browser.BiometricGate
import app.pane.browser.ui.components.DotText
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.library.arrive
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.CookiePolicy
import app.pane.core.settings.DnsOverHttps
import app.pane.core.settings.DohProvider
import app.pane.core.settings.HttpsMode
import app.pane.core.settings.TrackingProtection

/**
 * Privacy in two tiers. The essentials are the few things everyone should know about: how hard
 * trackers are blocked, what Pane remembers, and who it talks to. Everything an expert might tune
 * sits under Advanced, closed until asked for; nothing is hidden for good.
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

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    LargeTitleScaffold(title = "Privacy", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "protection") {
            val strict = settings.trackingProtection == TrackingProtection.Strict
            GroupedSection(
                modifier = Modifier.arrive(0),
                header = "Tracker protection",
                footer = "Strict can break some sites.",
            ) {
                row {
                    SegmentedRow(
                        options = listOf("Standard", "Strict"),
                        selectedIndex = if (strict) 1 else 0,
                        onSelect = { i ->
                            update { it.copy(trackingProtection = if (i == 1) TrackingProtection.Strict else TrackingProtection.Standard) }
                        },
                    )
                }
            }
        }

        item(key = "basics") {
            GroupedSection(modifier = Modifier.arrive(1)) {
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

        item(key = "connections") {
            GroupedSection(modifier = Modifier.arrive(2)) {
                row { NavRow("Connections", onClick = { navigator.push(Route.Connections) }) }
            }
        }

        item(key = "advanced") {
            AdvancedSection(modifier = Modifier.arrive(3)) {
                GroupedSection(header = "Cookies") {
                    CookiePolicy.entries.forEach { policy ->
                        row {
                            ChoiceRow(
                                title = cookieTitle(policy),
                                selected = settings.cookiePolicy == policy,
                                onClick = { update { it.copy(cookiePolicy = policy) } },
                                note = cookieNote(policy),
                            )
                        }
                    }
                }
                GroupedSection(header = "Secure connections") {
                    HttpsMode.entries.forEach { mode ->
                        row {
                            ChoiceRow(
                                title = httpsTitle(mode),
                                selected = settings.httpsMode == mode,
                                onClick = { update { it.copy(httpsMode = mode) } },
                                note = httpsNote(mode),
                            )
                        }
                    }
                }
                GroupedSection(
                    header = "Secure DNS",
                    footer = if (settings.dnsOverHttps == DnsOverHttps.Max) "If it fails, pages won't load." else null,
                ) {
                    row {
                        SegmentedRow(
                            options = listOf("Off", "Automatic", "Always"),
                            selectedIndex = settings.dnsOverHttps.ordinal,
                            onSelect = { i -> update { it.copy(dnsOverHttps = DnsOverHttps.entries[i]) } },
                        )
                    }
                    if (settings.dnsOverHttps != DnsOverHttps.Off) {
                        DohProvider.entries.forEach { provider ->
                            row {
                                ChoiceRow(
                                    title = provider.label,
                                    selected = settings.dohProvider == provider,
                                    onClick = { update { it.copy(dohProvider = provider) } },
                                    note = if (provider == DohProvider.Quad9) "Recommended" else null,
                                )
                            }
                        }
                    }
                }
                GroupedSection(header = "Web protections") {
                    row {
                        SwitchRow(
                            title = "Global Privacy Control",
                            checked = settings.globalPrivacyControl,
                            onCheckedChange = { on -> update { it.copy(globalPrivacyControl = on) } },
                        )
                    }
                    row {
                        SwitchRow(
                            title = "Fingerprinting protection",
                            checked = settings.fingerprintingProtection,
                            onCheckedChange = { on -> update { it.copy(fingerprintingProtection = on) } },
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
                        SwitchRow(
                            title = "Remove link tracking",
                            checked = settings.stripTrackingParams,
                            onCheckedChange = { on -> update { it.copy(stripTrackingParams = on) } },
                        )
                    }
                }
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
}


private fun cookieTitle(policy: CookiePolicy) = when (policy) {
    CookiePolicy.IsolateAll -> "Isolate cross-site cookies"
    CookiePolicy.BlockCrossSiteTrackers -> "Block cross-site trackers"
    CookiePolicy.BlockAllThirdParty -> "Block third-party cookies"
    CookiePolicy.BlockAll -> "Block all cookies"
}

private fun cookieNote(policy: CookiePolicy): String? = when (policy) {
    CookiePolicy.IsolateAll -> "Recommended"
    CookiePolicy.BlockCrossSiteTrackers -> null
    CookiePolicy.BlockAllThirdParty -> "Some sign-ins may break"
    CookiePolicy.BlockAll -> "Most sign-ins won't work"
}

private fun httpsTitle(mode: HttpsMode) = when (mode) {
    HttpsMode.HttpsOnly -> "HTTPS-only"
    HttpsMode.HttpsFirst -> "HTTPS-first"
    HttpsMode.Off -> "Off"
}

private fun httpsNote(mode: HttpsMode): String? = when (mode) {
    HttpsMode.HttpsOnly -> "Blocks plain HTTP sites"
    HttpsMode.HttpsFirst -> "Recommended"
    HttpsMode.Off -> "Pages may load unencrypted"
}
