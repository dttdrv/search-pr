package app.pane.browser.engine

import android.content.Context
import app.pane.browser.BuildConfig
import app.pane.core.settings.BrowserSettings
import app.pane.core.settings.CookiePolicy
import app.pane.core.settings.DnsOverHttps
import app.pane.core.settings.HttpsMode
import app.pane.core.settings.ThemeMode
import app.pane.core.settings.TrackingProtection
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Builds and live-updates the single Gecko runtime. Everything privacy-relevant is decided here,
 * from settings, so there is one place to audit.
 *
 * Deliberately never configured: telemetry, crash reporting, remote debugging (debug builds only),
 * and enterprise roots.
 */
object EngineRuntime {

    fun create(context: Context, settings: BrowserSettings): GeckoRuntime {
        val builder = GeckoRuntimeSettings.Builder()
            .contentBlocking(contentBlocking(settings))
            .javaScriptEnabled(settings.javascriptEnabled)
            .globalPrivacyControlEnabled(settings.globalPrivacyControl)
            .allowInsecureConnections(httpsMode(settings.httpsMode))
            .trustedRecursiveResolverMode(trrMode(settings.dnsOverHttps))
            .trustedRecursiveResolverUri(settings.dohProvider.uri)
            .preferredColorScheme(colorScheme(settings.theme))
            .fontSizeFactor(settings.textScale)
            .automaticFontSizeAdjustment(false)
            .forceUserScalableEnabled(settings.forceZoom)
            .loginAutofillEnabled(true)
            .webManifest(true)
            .extensionsProcessEnabled(true)
            // Lets addons.mozilla.org offer "Add to Pane" directly in the page.
            .extensionsWebAPIEnabled(true)
            .enterpriseRootsEnabled(false)
            .remoteDebuggingEnabled(BuildConfig.DEBUG)
            .consoleOutput(BuildConfig.DEBUG)
            .aboutConfigEnabled(BuildConfig.DEBUG)
            .debugLogging(false)
            .inputAutoZoomEnabled(false)
            .translationsOfferPopup(false)
        val runtime = GeckoRuntime.create(context, builder.build())
        applyExtras(runtime, settings)
        return runtime
    }

    /** Applies a settings change to the running engine without a restart. */
    fun apply(runtime: GeckoRuntime, settings: BrowserSettings) {
        val s = runtime.settings
        s.setJavaScriptEnabled(settings.javascriptEnabled)
        s.setGlobalPrivacyControl(settings.globalPrivacyControl)
        s.setAllowInsecureConnections(httpsMode(settings.httpsMode))
        s.setTrustedRecursiveResolverMode(trrMode(settings.dnsOverHttps))
        s.setTrustedRecursiveResolverUri(settings.dohProvider.uri)
        s.setPreferredColorScheme(colorScheme(settings.theme))
        s.setFontSizeFactor(settings.textScale)
        s.setForceUserScalableEnabled(settings.forceZoom)
        val cb = s.contentBlocking
        cb.setAntiTracking(antiTracking(settings.trackingProtection))
        cb.setEnhancedTrackingProtectionLevel(etpLevel(settings.trackingProtection))
        cb.setStrictSocialTrackingProtection(settings.trackingProtection == TrackingProtection.Strict)
        cb.setCookieBehavior(cookieBehavior(settings.cookiePolicy))
        cb.setSafeBrowsing(if (settings.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
        cb.setQueryParameterStrippingEnabled(settings.stripTrackingParams)
        applyExtras(runtime, settings)
    }

    private fun applyExtras(runtime: GeckoRuntime, settings: BrowserSettings) {
        val s = runtime.settings
        s.setFingerprintingProtection(settings.fingerprintingProtection)
        s.setFingerprintingProtectionPrivateBrowsing(true)
        s.setCookieBehaviorOptInPartitioning(true)
        s.setCookieBehaviorOptInPartitioningPBM(true)
    }

    private fun contentBlocking(settings: BrowserSettings): ContentBlocking.Settings =
        ContentBlocking.Settings.Builder()
            .antiTracking(antiTracking(settings.trackingProtection))
            .enhancedTrackingProtectionLevel(etpLevel(settings.trackingProtection))
            .strictSocialTrackingProtection(settings.trackingProtection == TrackingProtection.Strict)
            .cookieBehavior(cookieBehavior(settings.cookiePolicy))
            // Private tabs always get the strongest isolation.
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookiePurging(true)
            .safeBrowsing(if (settings.safeBrowsing) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE)
            .queryParameterStrippingEnabled(settings.stripTrackingParams)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .emailTrackerBlockingPrivateMode(true)
            .bounceTrackingProtectionMode(ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED)
            .build()

    private fun antiTracking(level: TrackingProtection) = when (level) {
        TrackingProtection.Strict -> ContentBlocking.AntiTracking.STRICT
        TrackingProtection.Standard -> ContentBlocking.AntiTracking.DEFAULT
    }

    private fun etpLevel(level: TrackingProtection) = when (level) {
        TrackingProtection.Strict -> ContentBlocking.EtpLevel.STRICT
        TrackingProtection.Standard -> ContentBlocking.EtpLevel.DEFAULT
    }

    private fun cookieBehavior(policy: CookiePolicy) = when (policy) {
        CookiePolicy.IsolateAll -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
        CookiePolicy.BlockCrossSiteTrackers -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        CookiePolicy.BlockAllThirdParty -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
        CookiePolicy.BlockAll -> ContentBlocking.CookieBehavior.ACCEPT_NONE
    }

    private fun httpsMode(mode: HttpsMode) = when (mode) {
        HttpsMode.HttpsOnly -> GeckoRuntimeSettings.HTTPS_ONLY
        // Gecko upgrades opportunistically (HTTPS-First) on its own in these modes.
        HttpsMode.HttpsFirst, HttpsMode.Off -> GeckoRuntimeSettings.ALLOW_ALL
    }

    private fun trrMode(mode: DnsOverHttps) = when (mode) {
        DnsOverHttps.Off -> GeckoRuntimeSettings.TRR_MODE_DISABLED
        DnsOverHttps.Default -> GeckoRuntimeSettings.TRR_MODE_FIRST
        DnsOverHttps.Max -> GeckoRuntimeSettings.TRR_MODE_ONLY
    }

    private fun colorScheme(mode: ThemeMode) = when (mode) {
        ThemeMode.System -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        ThemeMode.Light -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        ThemeMode.Dark -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
    }
}
