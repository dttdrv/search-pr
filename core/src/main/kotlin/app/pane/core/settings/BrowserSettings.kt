package app.pane.core.settings

import kotlinx.serialization.Serializable

@Serializable
enum class TrackingProtection { Standard, Strict }

@Serializable
enum class HttpsMode {
    /** Upgrade every load; show an interstitial before falling back to HTTP. */
    HttpsOnly,

    /** Upgrade when possible and fall back silently. */
    HttpsFirst,
    Off,
}

@Serializable
enum class CookiePolicy {
    /** Total Cookie Protection: every site gets its own cookie jar. */
    IsolateAll,
    BlockCrossSiteTrackers,
    BlockAllThirdParty,
    BlockAll,
}

@Serializable
enum class DnsOverHttps {
    Off,

    /** Use DoH, fall back to the system resolver when it fails. */
    Default,

    /** Only DoH; fail rather than leak queries. */
    Max,
}

@Serializable
enum class DohProvider(val label: String, val uri: String) {
    Quad9("Quad9", "https://dns.quad9.net/dns-query"),
    Cloudflare("Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"),
    Mullvad("Mullvad", "https://dns.mullvad.net/dns-query"),
    NextDns("NextDNS", "https://firefox.dns.nextdns.io/"),
    AdGuard("AdGuard", "https://dns.adguard-dns.com/dns-query"),
}

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class ToolbarPosition { Bottom, Top }

/**
 * User preferences. Defaults are the most private options that don't break the web.
 */
@Serializable
data class BrowserSettings(
    val searchEngineId: String = "ddg",
    val searchSuggestions: Boolean = true,
    val searchSuggestionsInPrivate: Boolean = false,
    val trackingProtection: TrackingProtection = TrackingProtection.Strict,
    val cookiePolicy: CookiePolicy = CookiePolicy.IsolateAll,
    val httpsMode: HttpsMode = HttpsMode.HttpsOnly,
    val dnsOverHttps: DnsOverHttps = DnsOverHttps.Default,
    val dohProvider: DohProvider = DohProvider.Quad9,
    val globalPrivacyControl: Boolean = true,
    val fingerprintingProtection: Boolean = true,
    val cookieBannerReduction: Boolean = true,
    val safeBrowsing: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val javascriptEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val autoplayBlocked: Boolean = true,
    val lockPrivateTabs: Boolean = false,
    val clearOnExit: Boolean = false,
    val rememberHistory: Boolean = true,
    val secureScreenInPrivate: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    val forceDarkWebContent: Boolean = false,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.Bottom,
    val hideToolbarOnScroll: Boolean = true,
    val tintToolbarWithPage: Boolean = true,
    val textScale: Float = 1.0f,
    val forceZoom: Boolean = true,
    val desktopModeByDefault: Boolean = false,
    val showHomeFavorites: Boolean = true,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val restoreTabs: Boolean = true,
    val closeTabsAfterDays: Int = 0,
    val onboardingDone: Boolean = false,
)
