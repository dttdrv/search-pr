package app.pane.browser.engine

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Recent full-page snapshots keyed by tab and URL, so the back gesture can reveal the previous
 * page the way iOS does instead of a blank. Memory only; never written to disk.
 */
class PageSnapshots {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun put(tabId: String, url: String, bitmap: Bitmap) {
        cache.put(key(tabId, url), bitmap)
    }

    fun get(tabId: String, url: String): Bitmap? = cache.get(key(tabId, url))

    fun clear() = cache.evictAll()

    private fun key(tabId: String, url: String) = "$tabId|${url.substringBefore('#')}"
}
