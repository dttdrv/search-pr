package app.pane.browser.engine

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Private tabs live in their own WebView profile: separate cookies, storage and cache that are
 * deleted when the last private tab closes. On a WebView too old to have profiles the tabs still
 * work but share storage with normal ones, and the browser says so.
 */
object Profiles {
    private const val PRIVATE = "pane-private"

    val supported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Must run before the view loads anything. */
    fun assign(webView: WebView, private: Boolean) {
        if (!private || !supported) return
        runCatching {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE)
            WebViewCompat.setProfile(webView, PRIVATE)
        }
    }

    /** the cookie jar a tab's pages use: the private profile's for private tabs, none if that profile is gone. */
    fun cookies(private: Boolean): CookieManager? = when {
        !private || !supported -> CookieManager.getInstance()
        else -> runCatching { ProfileStore.getInstance().getProfile(PRIVATE)?.cookieManager }.getOrNull()
    }

    /** Throws private data away; only valid once no private view is left. */
    fun dropPrivate() {
        if (!supported) return
        runCatching { ProfileStore.getInstance().deleteProfile(PRIVATE) }
    }
}
