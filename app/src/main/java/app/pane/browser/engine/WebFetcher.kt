package app.pane.browser.engine

import android.annotation.SuppressLint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import kotlin.coroutines.resume

/**
 * Small fetches (search suggestions, the add-on store) through Gecko's own network stack, so they
 * obey the same DNS-over-HTTPS, proxy and TLS settings as browsing, and never send cookies.
 */
class WebFetcher(runtime: GeckoRuntime) {
    private val executor = GeckoWebExecutor(runtime)

    // The flags parameter is a bit field; GeckoView's annotation just doesn't say so.
    @SuppressLint("WrongConstant")
    suspend fun text(url: String, private: Boolean = false, maxBytes: Int = 2 shl 20): String? {
        val request = WebRequest.Builder(url)
            .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.1")
            .cacheMode(WebRequest.CACHE_MODE_DEFAULT)
            .build()
        val flags = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else 0)
        val response = suspendCancellableCoroutine<WebResponse?> { cont ->
            executor.fetch(request, flags).accept({ result ->
                if (cont.isActive) cont.resume(result) else runCatching { result?.body?.close() }
            }, { _ ->
                if (cont.isActive) cont.resume(null)
            })
        } ?: return null
        val body = response.body ?: return null
        return try {
            // Gecko answers on the main thread; reads block (up to the response's read timeout each).
            withContext(Dispatchers.IO) {
                if (response.statusCode !in 200..299) return@withContext null
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() < maxBytes) {
                    ensureActive()
                    val n = body.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            // Also when cancelled; an unclosed body keeps the connection open until it's collected.
            runCatching { body.close() }
        }
    }

    fun warmUp(url: String) = runCatching { executor.speculativeConnect(url) }
}
