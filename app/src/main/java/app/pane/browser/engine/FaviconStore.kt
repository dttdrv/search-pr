package app.pane.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.pane.core.url.UrlInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.max

/**
 * Site icons for the history and bookmark lists, kept on the device only.
 *
 * WebView hands over the icon of every page it loads (`WebChromeClient.onReceivedIcon`); the app
 * passes the ones from normal tabs to [put], which shrinks them to [ICON_PX] and remembers them per
 * site in memory and in `cacheDir/favicons`. Private tabs never reach this class, and nothing here
 * touches the network: [get] and [peek] only read the cache.
 *
 * [clear] wipes everything and is part of "clear browsing data".
 */
class FaviconStore(context: Context) {
    private val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private val inFlight = HashSet<String>()

    /** Bumped by [clear] so a write that started earlier doesn't put its icon back afterwards. */
    @Volatile
    private var generation = 0

    private val _version = MutableStateFlow(0)

    /** Increments whenever an icon becomes available (or all are cleared), so lists look them up again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The icon for the site of [url] if it is already in memory. Never touches the disk. */
    fun peek(url: String): Bitmap? = keyOf(url)?.let { memory.get(it) }

    /** The icon for the site of [url] from memory, else from disk (so call it off the main thread). */
    fun get(url: String): Bitmap? {
        val key = keyOf(url) ?: return null
        memory.get(key)?.let { return it }
        val file = fileFor(key)
        if (!file.isFile) return null
        val startedIn = generation
        val bitmap = try {
            BitmapFactory.decodeFile(file.path)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            file.delete()
            return null
        }
        if (startedIn == generation) memory.put(key, bitmap)
        return bitmap
    }

    /** Forgets every icon: memory and disk. */
    fun clear() {
        synchronized(lock) {
            generation++
            memory.evictAll()
            dir.listFiles()?.forEach { it.delete() }
        }
        _version.update { it + 1 }
    }

    /**
     * The page at [pageUrl] (a normal tab; the caller checks) reported [bitmap] as its icon. Shrinks
     * it right away, then saves it in the background unless the site already has a fresh icon at
     * least as large. [bitmap] is never kept or recycled. Pages that aren't http(s) are ignored.
     */
    fun put(pageUrl: String, bitmap: Bitmap) {
        val key = keyOf(pageUrl) ?: return
        val icon = runCatching { shrink(bitmap) }.getOrNull() ?: return
        val startedIn = generation
        scope.launch {
            if (!begin(key)) return@launch
            try {
                if (save(key, icon, startedIn)) _version.update { it + 1 }
            } finally {
                end(key)
            }
        }
    }

    // region Decoding and storage

    /**
     * A copy of [bitmap] no larger than [ICON_PX] on its longest edge, or null if it is too small
     * to be an icon (a 1x1 pixel is a tracker or a placeholder). The original is left alone.
     */
    private fun shrink(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null
        val longest = max(bitmap.width, bitmap.height)
        if (longest < MIN_ICON_PX) return null
        if (longest <= ICON_PX) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val ratio = ICON_PX.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun begin(key: String): Boolean = synchronized(lock) { inFlight.add(key) }

    private fun end(key: String) {
        synchronized(lock) { inFlight.remove(key) }
    }

    /** Puts [icon] in memory and on disk, then trims the disk cache. False if nothing was stored. */
    private fun save(key: String, icon: Bitmap, startedIn: Int): Boolean = synchronized(lock) {
        // A clear() since put() was called means the user wanted this site forgotten.
        if (startedIn != generation) return@synchronized false
        val target = fileFor(key)
        // Pages report their icon on every load; keep a fresh one unless this one is bigger.
        if (target.isFile && System.currentTimeMillis() - target.lastModified() < FRESH_MS) {
            val known = memory.get(key)
            if (known == null || max(known.width, known.height) >= max(icon.width, icon.height)) return@synchronized false
        }
        memory.put(key, icon)
        dir.mkdirs()
        val temp = File(dir, target.name + ".tmp")
        try {
            temp.outputStream().use { out -> icon.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (_: IOException) {
            temp.delete()
        }
        trim()
        true
    }

    /** Deletes the oldest files until the folder fits in [MAX_DISK_BYTES]. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DISK_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISK_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(dir, "$name.png")
    }

    // endregion

    private companion object {
        const val ICON_PX = 96
        const val MIN_ICON_PX = 8
        const val MAX_DISK_BYTES = 4L * 1024 * 1024
        const val MEMORY_BYTES = 2 * 1024 * 1024
        const val FRESH_MS = 7L * 24 * 60 * 60 * 1000

        /** Web pages only, keyed by host without a leading `www.`, `m.` or `mobile.`. */
        fun keyOf(url: String): String? {
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
            val host = UrlInput.hostOf(url)?.takeIf { it.isNotEmpty() } ?: return null
            val prefix = listOf("www.", "m.", "mobile.").firstOrNull { host.startsWith(it) && host.count { c -> c == '.' } >= 2 }
            return if (prefix != null) host.removePrefix(prefix) else host
        }
    }
}
