package app.pane.browser.extensions

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.pane.browser.engine.WebFetcher
import app.pane.core.extensions.AddonMatching
import app.pane.core.extensions.Amo
import app.pane.core.extensions.AmoAddon
import app.pane.core.extensions.AmoPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The add-on store (addons.mozilla.org) over Gecko's network stack, so it follows the same DoH,
 * proxy and TLS settings as browsing and never sends cookies. Listing icons are kept in a small
 * in-memory LRU so scrolling back through results doesn't refetch them.
 *
 * Call from the main thread; bodies are read on the IO dispatcher.
 */
class AmoClient(runtime: GeckoRuntime, private val fetcher: WebFetcher) {
    private val executor = GeckoWebExecutor(runtime)
    private val icons = object : LruCache<String, ImageBitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private var recommended: Map<String, AmoAddon>? = null

    /** A page of results for [url] (`Amo.searchUrl(…)` or a previous page's `next`); null on failure. */
    suspend fun page(url: String): AmoPage? {
        val body = fetcher.text(url) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                Amo.parsePage(body)
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected add-on store response", e)
                null
            }
        }
    }

    /** Store listings of the curated recommendations, by slug. Cached for the life of the process. */
    suspend fun recommendedListings(): Map<String, AmoAddon> {
        recommended?.let { return it }
        val page = page(AddonMatching.listingsByIdUrl(AddonMatching.knownIds.values)) ?: return emptyMap()
        return page.addons.associateBy { it.slug }.also { recommended = it }
    }

    fun cachedIcon(url: String): ImageBitmap? = icons.get(url)

    /** The icon at [url], decoded no larger than needed for [sizePx]. Null when unavailable. */
    suspend fun icon(url: String, sizePx: Int): ImageBitmap? {
        icons.get(url)?.let { return it }
        val request = WebRequest.Builder(url)
            .header("Accept", "image/webp,image/png,image/*;q=0.8")
            .cacheMode(WebRequest.CACHE_MODE_DEFAULT)
            .build()
        val response = try {
            executor.fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val bitmap = withContext(Dispatchers.IO) { decode(response, sizePx) } ?: return null
        icons.put(url, bitmap)
        return bitmap
    }

    private fun decode(response: WebResponse, sizePx: Int): ImageBitmap? {
        val body: InputStream = response.body ?: return null
        return try {
            if (response.statusCode !in 200..299) return null
            val bytes = body.readCapped(MAX_ICON_BYTES) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { body.close() }
        }
    }

    private fun InputStream.readCapped(max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > max) return null
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "AmoClient"
        const val ICON_CACHE_BYTES = 6 * 1024 * 1024
        const val MAX_ICON_BYTES = 1024 * 1024
    }
}
