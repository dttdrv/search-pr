package app.pane.browser.ui.settings

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pane.browser.LocalAppContainer
import app.pane.browser.ui.components.CheckRow
import app.pane.browser.ui.components.GroupedSection
import app.pane.browser.ui.components.IconTile
import app.pane.browser.ui.components.LargeTitleScaffold
import app.pane.browser.ui.components.ToggleRow
import app.pane.browser.ui.icons.PaneIcons
import app.pane.browser.ui.library.TileColors
import app.pane.browser.ui.library.rememberBackLabel
import app.pane.browser.ui.navigation.LocalNavigator
import app.pane.browser.ui.navigation.Route
import app.pane.browser.ui.theme.Motion
import app.pane.browser.ui.theme.PaneShapes
import app.pane.browser.ui.theme.PaneTheme
import app.pane.core.library.ProtectionLevel
import app.pane.core.library.ProtectionSummary
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.CookiePolicy
import app.pane.core.settings.DnsOverHttps
import app.pane.core.settings.DohProvider
import app.pane.core.settings.HttpsMode
import app.pane.core.settings.TrackingProtection

/**
 * Every privacy control in one place, each explained in plain words. The card at the top sums up
 * how protected browsing is, so weakening a setting never goes unnoticed.
 */
@Composable
fun PrivacySettingsScreen() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val settings by rememberSettingsState()
    val backLabel = rememberBackLabel(Route.PrivacySettings)
    val summary = remember(settings) { ProtectionSummary.of(settings) }
    val canLock = remember { isDeviceSecure(context) }

    fun update(transform: (BrowserSettings) -> BrowserSettings) = container.settings.update(transform)

    LargeTitleScaffold(title = "Privacy & Security", onBack = navigator::pop, backLabel = backLabel) {
        item(key = "hero") { ProtectionCard(summary) }

        item(key = "tracking") {
            val strict = settings.trackingProtection == TrackingProtection.Strict
            GroupedSection(
                header = "Tracking Protection",
                footer = if (strict) {
                    "Strict also blocks tracking content such as embedded ads, social buttons and comment widgets. " +
                        "If a site looks broken, turn protection off for it from the address bar."
                } else {
                    "Standard blocks known trackers, cryptominers and fingerprinters while keeping every site working."
                },
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

        item(key = "cookies") {
            GroupedSection(header = "Cookies") {
                CookiePolicy.entries.forEach { policy ->
                    row {
                        CheckRow(
                            title = cookieTitle(policy),
                            subtitle = cookieDescription(policy),
                            selected = settings.cookiePolicy == policy,
                            onClick = { update { it.copy(cookiePolicy = policy) } },
                        )
                    }
                }
            }
        }

        item(key = "https") {
            GroupedSection(header = "Secure Connections") {
                HttpsMode.entries.forEach { mode ->
                    row {
                        CheckRow(
                            title = httpsTitle(mode),
                            subtitle = httpsDescription(mode),
                            selected = settings.httpsMode == mode,
                            onClick = { update { it.copy(httpsMode = mode) } },
                        )
                    }
                }
            }
        }

        item(key = "dns") {
            GroupedSection(header = "Secure DNS", footer = dnsDescription(settings.dnsOverHttps)) {
                row {
                    SegmentedRow(
                        options = listOf("Off", "Default", "Max"),
                        selectedIndex = settings.dnsOverHttps.ordinal,
                        onSelect = { i -> update { it.copy(dnsOverHttps = DnsOverHttps.entries[i]) } },
                    )
                }
            }
        }
        if (settings.dnsOverHttps != DnsOverHttps.Off) {
            item(key = "dnsProvider") {
                GroupedSection(
                    header = "DNS Provider",
                    footer = "Your provider sees the names of sites you visit, but your network and internet provider no longer do.",
                    modifier = Modifier.animateItem(),
                ) {
                    DohProvider.entries.forEach { provider ->
                        row {
                            CheckRow(
                                title = provider.label,
                                subtitle = provider.uri.substringAfter("://").substringBefore('/'),
                                selected = settings.dohProvider == provider,
                                onClick = { update { it.copy(dohProvider = provider) } },
                            )
                        }
                    }
                }
            }
        }

        item(key = "web") {
            GroupedSection(header = "Web Protections") {
                row {
                    ToggleRow(
                        title = "Global Privacy Control",
                        subtitle = "Tell sites not to sell or share your data",
                        checked = settings.globalPrivacyControl,
                        onCheckedChange = { on -> update { it.copy(globalPrivacyControl = on) } },
                        leading = { IconTile(PaneIcons.Shield, TileColors.Blue) },
                    )
                }
                row {
                    ToggleRow(
                        title = "Fingerprinting Protection",
                        subtitle = "Make your device look like many others",
                        checked = settings.fingerprintingProtection,
                        onCheckedChange = { on -> update { it.copy(fingerprintingProtection = on) } },
                        leading = { IconTile(PaneIcons.Fingerprint, TileColors.Indigo) },
                    )
                }
                row {
                    ToggleRow(
                        title = "Safe Browsing",
                        subtitle = "Warn before deceptive or dangerous sites",
                        checked = settings.safeBrowsing,
                        onCheckedChange = { on -> update { it.copy(safeBrowsing = on) } },
                        leading = { IconTile(PaneIcons.Warning, TileColors.Orange) },
                    )
                }
                row {
                    ToggleRow(
                        title = "Remove Tracking from Links",
                        subtitle = "Strip codes like utm_source and fbclid",
                        checked = settings.stripTrackingParams,
                        onCheckedChange = { on -> update { it.copy(stripTrackingParams = on) } },
                        leading = { IconTile(PaneIcons.Link, TileColors.Teal) },
                    )
                }
            }
        }

        item(key = "content") {
            GroupedSection(
                header = "Content",
                footer = if (settings.javascriptEnabled) {
                    null
                } else {
                    "With JavaScript off, many sites won't load or work properly."
                },
            ) {
                row {
                    ToggleRow(
                        title = "JavaScript",
                        checked = settings.javascriptEnabled,
                        onCheckedChange = { on -> update { it.copy(javascriptEnabled = on) } },
                        leading = { IconTile(PaneIcons.Sliders, TileColors.Yellow) },
                    )
                }
            }
        }

        item(key = "history") {
            GroupedSection(
                header = "History",
                footer = "Clearing on exit erases history, cookies, cached files and tabs whenever you quit Pane.",
            ) {
                row {
                    ToggleRow(
                        title = "Remember History",
                        checked = settings.rememberHistory,
                        onCheckedChange = { on -> update { it.copy(rememberHistory = on) } },
                        leading = { IconTile(PaneIcons.Clock, TileColors.Blue) },
                    )
                }
                row {
                    ToggleRow(
                        title = "Clear Data on Exit",
                        checked = settings.clearOnExit,
                        onCheckedChange = { on -> update { it.copy(clearOnExit = on) } },
                        leading = { IconTile(PaneIcons.Trash, TileColors.Red) },
                    )
                }
            }
        }

        item(key = "private") {
            GroupedSection(
                header = "Private Browsing",
                footer = if (canLock) {
                    "Private tabs are never saved and their cookies disappear when you close them."
                } else {
                    "Set up a screen lock on this device to lock private tabs."
                },
            ) {
                row {
                    ToggleRow(
                        title = "Lock Private Tabs",
                        subtitle = "Unlock with your fingerprint, face or PIN",
                        checked = settings.lockPrivateTabs && canLock,
                        enabled = canLock,
                        onCheckedChange = { on -> update { it.copy(lockPrivateTabs = on) } },
                        leading = { IconTile(PaneIcons.LockFill, TileColors.Purple) },
                    )
                }
                row {
                    ToggleRow(
                        title = "Block Screenshots",
                        subtitle = "Hide private tabs from screenshots and the app switcher",
                        checked = settings.secureScreenInPrivate,
                        onCheckedChange = { on -> update { it.copy(secureScreenInPrivate = on) } },
                        leading = { IconTile(PaneIcons.Private, TileColors.Gray) },
                    )
                }
            }
        }
    }
}

/** The summary card: level, how many protections are on, and which ones are off. */
@Composable
private fun ProtectionCard(summary: ProtectionSummary) {
    val colors = PaneTheme.colors
    val target = when (summary.level) {
        ProtectionLevel.Maximum -> colors.positive
        ProtectionLevel.High -> TileColors.Teal
        ProtectionLevel.Moderate -> colors.warning
        ProtectionLevel.Low -> colors.destructive
    }
    val tint by animateColorAsState(target, Motion.fade(250), label = "protectionTint")
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(PaneShapes.large)
            .background(colors.surface)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PaneIcons.ShieldCheck, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(summary.level.title, style = PaneTheme.type.title3, color = colors.label)
        Spacer(Modifier.height(4.dp))
        Text(
            "${summary.enabledCount} of ${summary.protections.size} protections on",
            style = PaneTheme.type.subheadline,
            color = colors.secondaryLabel,
        )
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            summary.protections.forEach { protection ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(PaneShapes.pill)
                        .background(if (protection.enabled) tint else colors.fill),
                )
            }
        }
        val off = summary.disabled
        if (off.isNotEmpty()) {
            Text(
                "Off: " + off.joinToString(", ") { it.name },
                style = PaneTheme.type.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun cookieTitle(policy: CookiePolicy) = when (policy) {
    CookiePolicy.IsolateAll -> "Isolate Cross-Site Cookies"
    CookiePolicy.BlockCrossSiteTrackers -> "Block Cross-Site Trackers"
    CookiePolicy.BlockAllThirdParty -> "Block All Third-Party Cookies"
    CookiePolicy.BlockAll -> "Block All Cookies"
}

private fun cookieDescription(policy: CookiePolicy) = when (policy) {
    CookiePolicy.IsolateAll -> "Each site keeps its own cookie jar, so nothing follows you between sites. Recommended."
    CookiePolicy.BlockCrossSiteTrackers -> "Blocks cookies from known trackers; other embedded sites can still share cookies."
    CookiePolicy.BlockAllThirdParty -> "Only the site you're on can set cookies. Some sign-ins and embeds may break."
    CookiePolicy.BlockAll -> "No site can store cookies. Most sign-ins and many sites won't work."
}

private fun httpsTitle(mode: HttpsMode) = when (mode) {
    HttpsMode.HttpsOnly -> "HTTPS-Only"
    HttpsMode.HttpsFirst -> "HTTPS-First"
    HttpsMode.Off -> "Off"
}

private fun httpsDescription(mode: HttpsMode) = when (mode) {
    HttpsMode.HttpsOnly -> "Always connect securely, and ask before opening a page that isn't encrypted."
    HttpsMode.HttpsFirst -> "Try a secure connection first and quietly fall back when a site doesn't offer one."
    HttpsMode.Off -> "Connect the way each link says, encrypted or not."
}

private fun dnsDescription(mode: DnsOverHttps) = when (mode) {
    DnsOverHttps.Off -> "Site names are looked up by your network, which can see and change them."
    DnsOverHttps.Default -> "Site names are looked up over an encrypted connection, falling back to your network if that fails."
    DnsOverHttps.Max -> "Site names are only looked up over an encrypted connection. If it fails, pages won't load."
}

private fun isDeviceSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
