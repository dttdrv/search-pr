package app.pane.browser.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What the system WebView keeps for every site, wiped through its global stores. None of it has
 * timestamps to filter by, so these always clear everything. The HTTP cache needs a live view and
 * lives in `SessionManager.clearCache()`; history, favicons and the rest are the app's own.
 *
 * The WebView APIs below must run on the main thread, so each function hops there itself, and
 * none of them fail when no WebView is installed: there is simply nothing to clear.
 */
object WebData {
    /** Removes every cookie and waits (briefly) until the cookie store has let go of them. */
    suspend fun clearCookies() {
        withContext(Dispatchers.Main.immediate) {
            val manager = try {
                CookieManager.getInstance()
            } catch (_: Exception) {
                null
            }
            if (manager != null) {
                withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        try {
                            manager.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
                        } catch (_: Exception) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
                try {
                    manager.flush()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * DOM storage (local, session, IndexedDB, WebSQL), saved HTTP logins, form data and the
     * location choices sites were given inside the web view.
     */
    @Suppress("DEPRECATION")
    suspend fun clearSiteData(context: Context) {
        withContext(Dispatchers.Main.immediate) {
            try {
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {
            }
            try {
                GeolocationPermissions.getInstance().clearAll()
            } catch (_: Exception) {
            }
            try {
                val database = WebViewDatabase.getInstance(context)
                database.clearHttpAuthUsernamePassword()
                database.clearFormData()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Cookies and web storage for [host] and its subdomains, so the site forgets who you are. Cookie
     * paths aren't exposed, so each cookie is expired at the root path, which is where logins live.
     */
    @Suppress("DEPRECATION")
    suspend fun clearSite(host: String) {
        withContext(Dispatchers.Main.immediate) {
            try {
                val cookies = CookieManager.getInstance()
                val labels = host.split('.')
                // The host and each parent down to two labels: a login cookie is often set on the parent.
                for (i in 0..(labels.size - 2).coerceAtLeast(0)) {
                    val domain = labels.drop(i).joinToString(".")
                    val url = "https://$domain/"
                    cookies.getCookie(url)?.split(';')?.forEach { pair ->
                        val name = pair.substringBefore('=').trim()
                        if (name.isEmpty()) return@forEach
                        val expired = "$name=; Max-Age=0; Path=/"
                        cookies.setCookie(url, expired)
                        cookies.setCookie(url, "$expired; Domain=$domain")
                        cookies.setCookie(url, "$expired; Domain=.$domain")
                    }
                }
                cookies.flush()
            } catch (_: Exception) {
            }
            try {
                val storage = WebStorage.getInstance()
                withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        storage.getOrigins { origins ->
                            origins?.keys?.filterIsInstance<String>()
                                ?.filter { origin ->
                                    val h = origin.substringAfter("://").substringBefore('/').substringBefore(':')
                                    h == host || h.endsWith(".$host") || host.endsWith(".$h")
                                }
                                ?.forEach { storage.deleteOrigin(it) }
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private const val CALLBACK_TIMEOUT_MS = 5_000L
}
