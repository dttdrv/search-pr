package app.pane.browser.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.pane.browser.settings.SettingsStore
import app.pane.core.adblock.FilterEngine
import app.pane.core.adblock.FilterEngineBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The filter lists the ad blocker runs on, and the one live [FilterEngine] built from them.
 *
 * The lists (uBlock Origin's and EasyList's, which are GPL/CC-BY-SA and so not bundled) are downloaded
 * on the device to `filesDir/filters/`. The engine built from the enabled ones is saved next to them as
 * a compact binary index keyed by which lists went into it and which versions, so a launch reads one
 * file instead of parsing megabytes of text. Until the real lists have been fetched, the small bundled
 * host list stands in.
 *
 * Nothing here runs on the main thread, and the engine is swapped atomically: a request already being
 * matched keeps the engine it started with.
 */
class FilterLists(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    /** One list of the catalogue. [id] is what settings store. */
    class Info(val id: String, val name: String, val url: String)

    /** What is known of a downloaded list. Times are epoch milliseconds; 0 means never. */
    data class Meta(
        val rules: Int = 0,
        val checkedAt: Long = 0,
        val etag: String? = null,
        val modified: String? = null,
        val bytes: Long = 0,
    )

    data class State(
        val updating: Boolean = false,
        val failed: Boolean = false,
        val lists: Map<String, Meta> = emptyMap(),
    )

    val catalogue: List<Info> = listOf(
        Info("ublock-filters", "uBlock filters", "https://ublockorigin.github.io/uAssets/filters/filters.min.txt"),
        Info("ublock-privacy", "uBlock privacy", "https://ublockorigin.github.io/uAssets/filters/privacy.min.txt"),
        Info("ublock-badware", "uBlock badware", "https://ublockorigin.github.io/uAssets/filters/badware.min.txt"),
        Info("ublock-unbreak", "uBlock unbreak", "https://ublockorigin.github.io/uAssets/filters/unbreak.min.txt"),
        // uAssets publishes this one only unminified; `resource-abuse.min.txt` is a 404.
        Info("ublock-resource-abuse", "uBlock resource abuse", "https://ublockorigin.github.io/uAssets/filters/resource-abuse.txt"),
        Info("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt"),
        Info("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        Info(
            "peter-lowe", "Peter Lowe's list",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
        ),
    )

    private val dir = File(context.filesDir, "filters")
    private val metaFile = File(dir, "meta.json")
    private val indexFile = File(dir, "engine.bin")
    private val mutex = Mutex()
    private val engineRef = AtomicReference<FilterEngine?>(null)
    private val ready = CountDownLatch(1)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var metas: Map<String, Meta> = emptyMap()
    @Volatile private var indexKey: String = ""

    /** The live engine, or null before the first one is ready (or while the blocker is off). Never waits. */
    fun engineOrNull(): FilterEngine? = engineRef.get()

    /**
     * The live engine. The first call after launch waits (up to two seconds) for the saved index to
     * load, so a request that starts with the app is still filtered; call it off the main thread.
     */
    fun engine(): FilterEngine {
        engineRef.get()?.let { return it }
        ready.await(2, TimeUnit.SECONDS)
        return engineRef.get() ?: FilterEngine.EMPTY
    }

    /** Loads the engine in the background and follows the settings from here on. */
    fun start() {
        scope.launch(Dispatchers.Default) {
            settings.state.map { it.blockAds to it.enabledFilterLists }.distinctUntilChanged().collect { (on, ids) ->
                try {
                    mutex.withLock { if (on) load(ids) else release() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't load filter lists", e)
                    ready.countDown()
                }
            }
        }
    }

    /** Checks the lists when they are older than three days, but only on a connection that isn't metered. */
    suspend fun updateIfStale() {
        if (!settings.current.blockAds || !isUnmetered()) return
        update(force = false)
    }

    /** Asks every enabled list whether it has changed, on any connection. */
    fun updateNow() {
        scope.launch(Dispatchers.Default) { update(force = true) }
    }

    /** Downloads the enabled lists that have never been fetched (a list the person just switched on), on any connection. */
    fun fetchMissing() {
        scope.launch(Dispatchers.Default) { update(force = false, onlyMissing = true) }
    }

    // ---- loading

    private fun release() {
        engineRef.set(FilterEngine.EMPTY)
        ready.countDown()
    }

    private fun readMeta() {
        val m = HashMap<String, Meta>()
        var key = ""
        runCatching {
            if (metaFile.isFile) {
                val root = JSONObject(metaFile.readText())
                key = root.optString("indexKey")
                val lists = root.optJSONObject("lists")
                lists?.keys()?.forEach { id ->
                    val o = lists.getJSONObject(id)
                    m[id] = Meta(
                        rules = o.optInt("rules"), checkedAt = o.optLong("checkedAt"),
                        etag = o.optString("etag").ifEmpty { null }, modified = o.optString("modified").ifEmpty { null },
                        bytes = o.optLong("bytes"),
                    )
                }
            }
        }
        metas = m
        indexKey = key
        _state.update { it.copy(lists = m) }
    }

    private fun writeMeta() {
        val lists = JSONObject()
        for ((id, m) in metas) {
            lists.put(
                id,
                JSONObject().put("rules", m.rules).put("checkedAt", m.checkedAt).put("etag", m.etag ?: "")
                    .put("modified", m.modified ?: "").put("bytes", m.bytes),
            )
        }
        val tmp = File(dir, "meta.json.tmp")
        runCatching {
            tmp.writeText(JSONObject().put("indexKey", indexKey).put("lists", lists).toString())
            tmp.renameTo(metaFile)
        }
        _state.update { it.copy(lists = metas) }
    }

    private fun listFile(info: Info) = File(dir, "${info.id}.txt")

    private fun enabledOnDisk(ids: List<String>): List<Info> =
        catalogue.filter { it.id in ids && listFile(it).isFile }

    private fun keyFor(lists: List<Info>): String =
        "v${FilterEngine.FORMAT_VERSION}:" + lists.joinToString(",") { "${it.id}@${metas[it.id]?.bytes ?: 0}/${metas[it.id]?.etag ?: metas[it.id]?.modified ?: ""}" }

    /** Reads the saved index when it matches the enabled lists, rebuilds from the downloaded text when it doesn't, and falls back to the bundled hosts. */
    private fun load(ids: List<String>) {
        val startedAt = System.nanoTime()
        dir.mkdirs()
        readMeta()
        val lists = enabledOnDisk(ids)
        if (lists.isEmpty()) {
            engineRef.set(bundled())
            ready.countDown()
            return
        }
        val key = keyFor(lists)
        if (key == indexKey && indexFile.isFile) {
            val loaded = runCatching { FileInputStream(indexFile).use { FilterEngine.load(it) } }
                .onFailure { Log.w(TAG, "Saved filter index unusable, rebuilding", it) }
                .getOrNull()
            if (loaded != null) {
                Log.i(TAG, "Loaded filter index: ${loaded.networkRuleCount} request rules, ${loaded.cosmeticRuleCount} cosmetic, ${System.nanoTime().let { (it - startedAt) / 1_000_000 }} ms")
                engineRef.set(loaded)
                ready.countDown()
                return
            }
        }
        // Until the rebuild finishes, whatever was live (or the bundled hosts) keeps filtering.
        if (engineRef.get() == null || engineRef.get() === FilterEngine.EMPTY) engineRef.set(bundled())
        ready.countDown()
        rebuild(lists)
    }

    /** Builds the engine from the downloaded text of [lists], saves it, and swaps it in. */
    private fun rebuild(lists: List<Info>) {
        val builtAt = System.nanoTime()
        val builder = FilterEngineBuilder()
        val counts = HashMap<String, Int>()
        for (info in lists) {
            counts[info.id] = runCatching { listFile(info).useLines { builder.addLines(it) } }.getOrDefault(0)
        }
        val engine = builder.build()
        Log.i(TAG, "Built filter index: ${engine.networkRuleCount} request rules, ${engine.cosmeticRuleCount} cosmetic, ${(System.nanoTime() - builtAt) / 1_000_000} ms")
        indexKey = keyFor(lists)
        metas = metas.toMutableMap().also { m -> for ((id, n) in counts) m[id] = (m[id] ?: Meta()).copy(rules = n) }
        val tmp = File(dir, "engine.bin.tmp")
        val saved = runCatching {
            FileOutputStream(tmp).use { engine.serialize(it) }
            tmp.renameTo(indexFile)
        }.getOrDefault(false)
        if (!saved) indexKey = ""
        engineRef.set(engine)
        writeMeta()
    }

    private fun bundled(): FilterEngine {
        val b = FilterEngineBuilder()
        runCatching {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                // The bundled hosts only ever applied to third parties.
                b.addLines(lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { "||$it^\$third-party" })
            }
        }
        return b.build()
    }

    // ---- downloading

    private sealed interface Fetched {
        data class Fresh(val etag: String?, val modified: String?, val bytes: Long) : Fetched
        data object Unchanged : Fetched
        data object Failed : Fetched
    }

    /**
     * Refreshes the enabled lists that are stale (all of them with [force], only the never-downloaded
     * ones with [onlyMissing]) and rebuilds the engine when any changed. One run at a time.
     */
    suspend fun update(force: Boolean, onlyMissing: Boolean = false) {
        mutex.withLock {
            dir.mkdirs()
            if (metas.isEmpty()) readMeta()
            _state.update { it.copy(updating = true, failed = false) }
            var failed = false
            try {
                val ids = settings.current.enabledFilterLists
                val now = System.currentTimeMillis()
                var changed = false
                val next = metas.toMutableMap()
                for (info in catalogue.filter { it.id in ids }) {
                    val file = listFile(info)
                    val prior = next[info.id]
                    if (onlyMissing && file.isFile) continue
                    if (!force && file.isFile && prior != null && now - prior.checkedAt < STALE_MS) continue
                    when (val r = withContext(Dispatchers.IO) { download(info, if (file.isFile) prior else null) }) {
                        is Fetched.Fresh -> {
                            next[info.id] = (prior ?: Meta()).copy(checkedAt = now, etag = r.etag, modified = r.modified, bytes = r.bytes)
                            changed = true
                        }
                        Fetched.Unchanged -> next[info.id] = (prior ?: Meta()).copy(checkedAt = now)
                        Fetched.Failed -> failed = true
                    }
                }
                metas = next
                if (changed && settings.current.blockAds) {
                    withContext(Dispatchers.Default) { rebuild(enabledOnDisk(ids)) }
                } else {
                    writeMeta()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Filter list update failed", e)
                failed = true
            } finally {
                _state.update { it.copy(updating = false, failed = failed) }
            }
        }
    }

    private fun download(info: Info, prior: Meta?): Fetched {
        val tmp = File(dir, "${info.id}.txt.tmp")
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(info.url)
            if (url.protocol != "https") return Fetched.Failed
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                prior?.etag?.let { setRequestProperty("If-None-Match", it) }
                prior?.modified?.let { setRequestProperty("If-Modified-Since", it) }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Fetched.Unchanged
            if (code != HttpURLConnection.HTTP_OK || conn.url.protocol != "https") return Fetched.Failed
            var total = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return Fetched.Failed
                        out.write(buf, 0, n)
                    }
                }
            }
            if (total < 64 || looksLikeHtml(tmp)) return Fetched.Failed
            if (!tmp.renameTo(listFile(info))) return Fetched.Failed
            Fetched.Fresh(conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"), total)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Couldn't fetch ${info.id}: ${e.javaClass.simpleName}")
            Fetched.Failed
        } finally {
            conn?.disconnect()
            tmp.delete()
        }
    }

    private fun looksLikeHtml(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val head = ByteArray(512)
            val n = input.read(head)
            val text = String(head, 0, maxOf(n, 0), Charsets.ISO_8859_1).trimStart().lowercase()
            text.startsWith("<!doctype") || text.startsWith("<html") || text.startsWith("<?xml")
        }
    }.getOrDefault(true)

    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "FilterLists"
        const val MAX_BYTES = 25L * 1024 * 1024
        const val STALE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
