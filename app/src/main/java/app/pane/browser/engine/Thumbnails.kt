package app.pane.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Page snapshots for the tab switcher and gesture transitions. Private tabs stay in memory only
 * and are dropped when private browsing ends.
 */
class Thumbnails(context: Context) {
    private val dir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    private val privateIds = HashSet<String>()
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun get(tabId: String): Bitmap? = memory.get(tabId)

    suspend fun load(tabId: String): Bitmap? {
        memory.get(tabId)?.let { return it }
        if (tabId in privateIds) return null
        return withContext(Dispatchers.IO) {
            val file = File(dir, "$tabId.webp")
            if (!file.exists()) null else BitmapFactory.decodeFile(file.path)?.also { memory.put(tabId, it) }
        }
    }

    /** Stores a snapshot, downscaled to at most [MAX_WIDTH] wide. */
    suspend fun put(tabId: String, bitmap: Bitmap, private: Boolean) {
        val scaled = withContext(Dispatchers.Default) {
            // a snapshot the gpu drew is read back once, to be scaled and written
            val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
            if (readable.width > MAX_WIDTH) {
                readable.scale(MAX_WIDTH, (readable.height * (MAX_WIDTH.toFloat() / readable.width)).toInt().coerceAtLeast(1))
            } else {
                readable
            }
        }
        memory.put(tabId, scaled)
        if (private) {
            privateIds += tabId
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                File(dir, "$tabId.webp").outputStream().use { out ->
                    @Suppress("DEPRECATION")
                    val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    scaled.compress(format, 70, out)
                }
            }
        }
    }

    fun remove(tabId: String) {
        memory.remove(tabId)
        privateIds -= tabId
        File(dir, "$tabId.webp").delete()
    }

    fun clearPrivate() {
        privateIds.forEach { memory.remove(it) }
        privateIds.clear()
    }

    fun clearAll() {
        memory.evictAll()
        privateIds.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_WIDTH = 720
    }
}
