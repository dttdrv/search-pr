package app.pane.browser.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import kotlin.coroutines.resume

/**
 * Small fetches (search suggestions, the add-on store) through Gecko's own network stack, so they
 * obey the same DNS-over-HTTPS, proxy and TLS settings as browsing, and never send cookies.
 */
class WebFetcher(runtime: GeckoRuntime) {
    private val executor = GeckoWebExecutor(runtime)

    suspend fun text(url: String, private: Boolean = false, maxBytes: Int = 2 shl 20): String? {
        val request = WebRequest.Builder(url)
            .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.1")
            .cacheMode(WebRequest.CACHE_MODE_DEFAULT)
            .build()
        val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else 0)
        return suspendCancellableCoroutine { cont ->
            executor.fetch(request, flags).accept({ response ->
                val body = try {
                    if (response == null || response.statusCode !in 200..299) {
                        null
                    } else {
                        response.body?.use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (out.size() < maxBytes) {
                                val n = stream.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                            }
                            out.toString(Charsets.UTF_8.name())
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (cont.isActive) cont.resume(body)
            }, { _ ->
                if (cont.isActive) cont.resume(null)
            })
        }
    }

    fun warmUp(url: String) = runCatching { executor.speculativeConnect(url) }
}
