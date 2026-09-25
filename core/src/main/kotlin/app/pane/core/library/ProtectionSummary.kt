package app.pane.core.library

import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.CookiePolicy
import app.pane.core.settings.DnsOverHttps
import app.pane.core.settings.HttpsMode
import app.pane.core.settings.TrackingProtection

enum class ProtectionLevel(val title: String) {
    Maximum("Maximum Protection"),
    High("High Protection"),
    Moderate("Moderate Protection"),
    Low("Low Protection"),
}

/** One protection the privacy screen counts, e.g. "Secure DNS", and whether it is on. */
data class Protection(val name: String, val enabled: Boolean)

/**
 * A one-glance verdict for the top of the privacy screen. Each protection counts equally; the
 * point is to make a weakened setup visible, not to score it precisely.
 */
data class ProtectionSummary(val level: ProtectionLevel, val protections: List<Protection>) {
    val enabledCount: Int get() = protections.count { it.enabled }
    val disabled: List<Protection> get() = protections.filterNot { it.enabled }

    companion object {
        fun of(settings: BrowserSettings): ProtectionSummary {
            val protections = listOf(
                Protection("Strict tracking protection", settings.trackingProtection == TrackingProtection.Strict),
                // Only "block trackers only" still lets ordinary third parties share one cookie jar.
                Protection("Cookie isolation", settings.cookiePolicy != CookiePolicy.BlockCrossSiteTrackers),
                Protection("HTTPS-Only", settings.httpsMode == HttpsMode.HttpsOnly),
                Protection("Secure DNS", settings.dnsOverHttps != DnsOverHttps.Off),
                Protection("Global Privacy Control", settings.globalPrivacyControl),
                Protection("Fingerprinting protection", settings.fingerprintingProtection),
                Protection("Safe Browsing", settings.safeBrowsing),
                Protection("Tracking parameter removal", settings.stripTrackingParams),
            )
            val on = protections.count { it.enabled }
            val level = when {
                on == protections.size -> ProtectionLevel.Maximum
                on >= protections.size - 2 -> ProtectionLevel.High
                on >= protections.size / 2 -> ProtectionLevel.Moderate
                else -> ProtectionLevel.Low
            }
            return ProtectionSummary(level, protections)
        }
    }
}
