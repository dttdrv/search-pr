package app.pane.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.pane.core.library.SiteOrigins
import app.pane.core.url.UrlInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlin.math.max

/** A site icon a page declares with `<link rel="icon">` or `rel="apple-touch-icon"`. */
data class IconCandidate(
    /** Absolute http(s) URL. */
    val href: String,
    /** The `sizes` attribute as written: `"32x32 64x64"`, `"any"` or null. */
    val sizes: String? = null,
    /** The `type` attribute (a MIME type), if any. */
    val type: String? = null,
    /** The `rel` attribute, lower-cased. */
    val rel: String? = null,
)

/**
 * Site icons for the history and bookmark lists, kept on the device only.
 *
 * Icons are learned from pages the user actually opened in normal tabs: the helper extension
 * reports what a page declares ([onPageIcons]), the best candidate is downloaded once through
 * Gecko's own network stack (anonymous, no cookies), shrunk to [ICON_PX] and remembered per site in
 * memory and in `cacheDir/favicons`. Private tabs never cause a request or leave anything behind.
 * Nothing here talks to the network on behalf of a list row: [get] and [peek] only read the cache.
 *
 * [clear] wipes everything and is part of "clear browsing data".
 */
class FaviconStore(context: Context, private val fetcher: WebFetcher) {
    private val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val lock = Any()

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private val inFlight = HashSet<String>()

    /** The last failed attempt per site, so a page that keeps reloading doesn't keep asking. */
    private val failures = HashMap<String, Failure>()

    /** [signature] is what was tried: a page that later declares other icons gets another go. */
    private class Failure(val signature: Int, val at: Long)

    /** Bumped by [clear] so a download that started earlier doesn't put its icon back afterwards. */
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
            failures.clear()
            dir.listFiles()?.forEach { it.delete() }
        }
        _version.update { it + 1 }
    }

    /**
     * A normal-tab page finished loading and declared [icons] (possibly none). Downloads the site's
     * icon unless a fresh one is already stored. Never does anything for private tabs or non-web pages.
     */
    suspend fun onPageIcons(pageUrl: String, icons: List<IconCandidate>, private: Boolean) {
        if (private) return
        val key = keyOf(pageUrl) ?: return
        val origin = SiteOrigins.originOf(pageUrl) ?: return
        if (!begin(key)) return
        try {
            val startedIn = generation
            val file = fileFor(key)
            val (cached, fresh) = withContext(Dispatchers.IO) {
                val exists = file.isFile
                exists to (exists && System.currentTimeMillis() - file.lastModified() < FRESH_MS)
            }
            if (fresh) return
            // An icon we already have stays when the page declares nothing new; only a site we
            // know nothing about gets the conventional /favicon.ico.
            val candidates = plan(pageUrl, origin, icons, includeDefault = !cached)
            if (candidates.isEmpty()) return
            val signature = candidates.hashCode()
            if (recentlyFailed(key, signature)) return
            for (candidate in candidates) {
                val bytes = download(candidate) ?: continue
                val bitmap = withContext(Dispatchers.Default) { decode(bytes) } ?: continue
                val stored = withContext(Dispatchers.IO) { store(key, bitmap, startedIn) }
                if (stored) _version.update { it + 1 }
                return
            }
            synchronized(lock) { failures[key] = Failure(signature, System.currentTimeMillis()) }
        } finally {
            end(key)
        }
    }

    // region Downloading

    private suspend fun download(url: String): ByteArray? = try {
        withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetcher.bytes(url, private = false, maxBytes = MAX_DOWNLOAD_BYTES) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun begin(key: String): Boolean = synchronized(lock) { inFlight.add(key) }

    private fun recentlyFailed(key: String, signature: Int): Boolean = synchronized(lock) {
        val failure = failures[key] ?: return@synchronized false
        failure.signature == signature && System.currentTimeMillis() - failure.at < RETRY_AFTER_FAILURE_MS
    }

    private fun end(key: String) {
        synchronized(lock) { inFlight.remove(key) }
    }

    /** The URLs to try, best first: up to [MAX_CANDIDATES] declared icons, then the site's default. */
    private fun plan(pageUrl: String, origin: String, icons: List<IconCandidate>, includeDefault: Boolean): List<String> {
        val secure = pageUrl.startsWith("https://", ignoreCase = true)
        val declared = icons
            .mapNotNull { candidate -> absolute(pageUrl, candidate.href)?.let { candidate to it } }
            // An https page's icon over plain http would be a downgrade; skip it.
            .filter { (_, url) -> !secure || url.startsWith("https://", ignoreCase = true) }
            .filter { (candidate, url) -> !isVector(candidate, url) }
            .sortedBy { (candidate, url) -> penalty(candidate, url) }
            .map { it.second }
            .distinct()
            .take(MAX_CANDIDATES)
        val fallback = "$origin/favicon.ico"
        return if (includeDefault && fallback !in declared) declared + fallback else declared
    }

    private fun absolute(pageUrl: String, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty()) return null
        // The helper script already resolves against the page; only odd inputs need the URI dance.
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return trimmed
        val resolved = try {
            URI(pageUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            return null
        }
        return resolved.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    /** SVG can't be decoded by [BitmapFactory]. */
    private fun isVector(candidate: IconCandidate, url: String): Boolean =
        candidate.type?.contains("svg", ignoreCase = true) == true ||
            url.substringBefore('#').substringBefore('?').endsWith(".svg", ignoreCase = true)

    /**
     * Lower is better: closeness of the declared size to the 96–192 px sweet spot, with a little
     * preference for apple-touch-icons (opaque, square, made to be shown large) over .ico files.
     */
    private fun penalty(candidate: IconCandidate, url: String): Int {
        val apple = candidate.rel?.contains("apple-touch-icon") == true
        val ico = candidate.type?.contains("icon", ignoreCase = true) == true ||
            url.substringBefore('#').substringBefore('?').endsWith(".ico", ignoreCase = true)
        val size = declaredSize(candidate.sizes) ?: if (apple) 180 else if (ico) 32 else 64
        var cost = when {
            size < SWEET_MIN -> SWEET_MIN - size
            size > SWEET_MAX -> (size - SWEET_MAX) / 2
            else -> 0
        }
        if (!apple) cost += 8
        if (ico) cost += 12
        return cost
    }

    /** The largest edge in a `sizes` attribute (`"16x16 32x32"` → 32); null for `any` or nothing. */
    private fun declaredSize(sizes: String?): Int? {
        if (sizes.isNullOrBlank()) return null
        return SIZE.findAll(sizes).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()
    }

    // endregion

    // region Decoding and storage

    /** Decodes [bytes] (PNG, ICO, WebP, JPEG, GIF) and shrinks the result to at most [ICON_PX]. */
    private fun decode(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > MAX_SOURCE_PX || h > MAX_SOURCE_PX) {
            null
        } else {
            var sample = 1
            while (w / (sample * 2) >= ICON_PX && h / (sample * 2) >= ICON_PX) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            // A 1x1 pixel is a tracker or a placeholder, not an icon.
            if (decoded == null || max(decoded.width, decoded.height) < MIN_ICON_PX) {
                null
            } else {
                shrink(decoded)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun shrink(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= ICON_PX) return bitmap
        val ratio = ICON_PX.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** Puts [bitmap] in memory and on disk, then trims the disk cache. False if [clear] ran since [startedIn]. */
    private fun store(key: String, bitmap: Bitmap, startedIn: Int): Boolean = synchronized(lock) {
        if (startedIn != generation) return@synchronized false
        failures.remove(key)
        memory.put(key, bitmap)
        dir.mkdirs()
        val target = fileFor(key)
        val temp = File(dir, target.name + ".tmp")
        try {
            temp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
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
        const val MAX_SOURCE_PX = 4096
        const val SWEET_MIN = 96
        const val SWEET_MAX = 192
        const val MAX_CANDIDATES = 3
        const val MAX_DOWNLOAD_BYTES = 512 * 1024
        const val MAX_DISK_BYTES = 4L * 1024 * 1024
        const val MEMORY_BYTES = 2 * 1024 * 1024
        const val FETCH_TIMEOUT_MS = 12_000L
        const val FRESH_MS = 7L * 24 * 60 * 60 * 1000
        const val RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000
        val SIZE = Regex("(\\d+)\\s*[xX]\\s*\\d+")

        /** Web pages only, keyed by host without a leading `www.`, `m.` or `mobile.`. */
        fun keyOf(url: String): String? {
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
            val host = UrlInput.hostOf(url)?.takeIf { it.isNotEmpty() } ?: return null
            val prefix = listOf("www.", "m.", "mobile.").firstOrNull { host.startsWith(it) && host.count { c -> c == '.' } >= 2 }
            return if (prefix != null) host.removePrefix(prefix) else host
        }
    }
}
