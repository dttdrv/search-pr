package app.pane.browser.downloads

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.pane.browser.data.DownloadRecord
import app.pane.browser.data.DownloadStatus
import app.pane.browser.data.DownloadsRepository
import app.pane.core.download.FileNames
import app.pane.core.library.UniqueNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * A file the page asked the browser to save: what WebView's `DownloadListener` reports, plus the
 * page it came from when that is known.
 */
data class DownloadRequest(
    val tabId: String?,
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    /** Bytes the page announced, or `<= 0` when unknown. */
    val contentLength: Long,
    val private: Boolean,
    /** The page the link was on; sent as the `Referer` when set. */
    val referrer: String? = null,
)

/**
 * Saves files the page hands over (responses WebView can't render) and direct "Download Link"
 * requests into the user's Downloads.
 *
 * The bytes come from a small in-process downloader (one `HttpURLConnection` per file, streamed
 * through a 64 KB buffer, with the page's cookies and user agent) so no engine is involved.
 *
 * Android 10+ writes through MediaStore, so files land in the shared Downloads folder without any
 * storage permission. Older versions use the app's own external Downloads folder and share files
 * through a FileProvider, which also needs no permission. Progress is kept in
 * [DownloadsRepository] so the list survives restarts; passing moments are announced on [events].
 *
 * Private downloads keep the file (the user asked for it) but not the address it came from.
 */
class DownloadController(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val scope: CoroutineScope,
) {
    /**
     * Where the `Cookie` header comes from, given the URL and whether the download is private.
     * Normal downloads use WebView's own cookie jar. Private tabs have a jar of their own, so the
     * app should point this at it; until it does, private downloads go out without cookies rather
     * than with the normal profile's. Called on a background thread.
     */
    var cookieSource: (url: String, isPrivate: Boolean) -> String? = { url, isPrivate ->
        if (isPrivate) null else CookieManager.getInstance().getCookie(url)
    }

    private val jobs = ConcurrentHashMap<Long, Job>()

    /** The connection a running download is blocked on, so [cancel] can break out of the read. */
    private val connections = ConcurrentHashMap<Long, Closeable>()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Short user-facing messages ("Downloading report.pdf…", "Saved report.pdf") for toasts. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        val startedAt = System.currentTimeMillis()
        scope.launch { runCatching { repository.markInterrupted(beforeMillis = startedAt) } }
    }

    /**
     * Starts saving [request]. `http(s):` addresses are fetched, `data:` addresses are decoded.
     * `blob:` (and anything else) can't be fetched outside the page that owns it, so it says so.
     */
    fun start(request: DownloadRequest) {
        val url = request.url.trim()
        if (isWeb(url) || url.startsWith("data:", ignoreCase = true)) {
            scope.launch { begin(request, url) }
        } else {
            _events.tryEmit("Couldn't download this file")
        }
    }

    /** Downloads [url] directly (context menu "Download Link" / "Save Image", and retry). */
    fun download(url: String, private: Boolean, referrer: String? = null) {
        start(
            DownloadRequest(
                tabId = null,
                url = url,
                userAgent = null,
                contentDisposition = null,
                mimeType = null,
                contentLength = -1L,
                private = private,
                referrer = referrer,
            ),
        )
    }

    /** Stops a running download and discards the partial file. */
    fun cancel(id: Long) {
        val job = jobs[id]
        if (job == null) {
            scope.launch {
                val record = repository.get(id)
                if (record?.status == DownloadStatus.Pending || record?.status == DownloadStatus.Running) {
                    repository.update(id, status = DownloadStatus.Cancelled)
                }
            }
            return
        }
        job.cancel()
        // A read blocked on the network only notices cancellation once its connection is closed.
        connections.remove(id)?.let { connection -> scope.launch(Dispatchers.IO) { closeQuietly(connection) } }
    }

    /** Removes [record] from the list; with [deleteFile] the file goes too, if Pane still owns it. */
    fun remove(record: DownloadRecord, deleteFile: Boolean) {
        if (record.status == DownloadStatus.Running || record.status == DownloadStatus.Pending) cancel(record.id)
        scope.launch {
            val uri = record.contentUri
            if (deleteFile && uri != null) {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
                }
            }
            repository.delete(record.id)
        }
    }

    /** An intent that opens the finished file in another app, or null if there is no file yet. */
    fun viewIntent(record: DownloadRecord): Intent? {
        val uri = record.contentUri?.let { Uri.parse(it) } ?: return null
        return viewIntent(uri, record.mime)
    }

    /** A share sheet for the finished file, or null if there is no file yet. */
    fun shareIntent(record: DownloadRecord): Intent? {
        val uri = record.contentUri?.let { Uri.parse(it) } ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(record.mime ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, record.fileName)
    }

    private fun viewIntent(uri: Uri, mime: String?): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: context.contentResolver.getType(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Adds the row to the list straight away (name and size are best guesses), then fetches in the background. */
    private suspend fun begin(request: DownloadRequest, url: String) {
        val name = FileNames.choose(request.contentDisposition, nameSource(url), request.mimeType ?: dataUriMime(url))
        val mime = mimeFor(name, request.mimeType ?: dataUriMime(url))
        val length = request.contentLength.takeIf { it > 0 } ?: -1L
        // data: URLs can be megabytes long and say nothing useful; only web addresses are kept.
        val source = if (request.private || !isWeb(url)) "" else url
        val id = try {
            repository.insert(source, name, mime, length)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _events.tryEmit("Couldn't download $name")
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) { transfer(id, request, url, name, mime, length) }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            connections.remove(id)
        }
        _events.tryEmit("Downloading $name…")
        job.start()
    }

    private suspend fun transfer(id: Long, request: DownloadRequest, url: String, guessedName: String, guessedMime: String?, guessedLength: Long) {
        var name = guessedName
        var target: Target? = null
        try {
            repository.update(id, status = DownloadStatus.Running)
            val opened = withContext(Dispatchers.IO) { open(id, request, url) }
            // The response knows better than the page what it is called and how big it is.
            val headerName = FileNames.choose(
                opened.contentDisposition ?: request.contentDisposition,
                nameSource(opened.url),
                opened.contentType ?: request.mimeType,
            )
            val mime = mimeFor(headerName, opened.contentType ?: request.mimeType) ?: guessedMime
            if (headerName != name || mime != guessedMime || (opened.length >= 0 && opened.length != guessedLength)) {
                name = headerName
                repository.update(id, fileName = name, mime = mime, totalBytes = opened.length.takeIf { it >= 0 })
            }
            // Record ownership inside IO: cancellation at the dispatcher hand-off must still
            // leave a target for the cleanup below to discard.
            withContext(Dispatchers.IO) { target = createTarget(name, mime) }
            val created = checkNotNull(target)
            if (created.name != name) {
                name = created.name
                repository.update(id, fileName = name)
            }
            val written = withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(created.uri) ?: throw IOException("Can't write ${created.uri}")
                out.use { sink -> opened.stream.use { copy(id, it, sink) } }
            }
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) { publish(created) }
            repository.update(
                id,
                status = DownloadStatus.Completed,
                downloadedBytes = written,
                totalBytes = written,
                contentUri = created.uri.toString(),
            )
            _events.tryEmit("Saved ${created.name}")
            notifyFinished(id, created.name, created.uri, mime)
        } catch (e: Exception) {
            // Closing the connection to cancel can surface as an IOException rather than a cancellation.
            val cancelled = e is CancellationException || !currentCoroutineContext().isActive
            val partial = target
            withContext(NonCancellable + Dispatchers.IO) {
                connections.remove(id)?.let { closeQuietly(it) }
                if (partial != null) runCatching { discard(partial) }
                repository.update(id, status = if (cancelled) DownloadStatus.Cancelled else DownloadStatus.Failed)
            }
            if (e is CancellationException) throw e
            if (!cancelled) {
                _events.tryEmit(if (e is HttpStatusException) "Couldn't download $name (error ${e.code})" else "Couldn't download $name")
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { connections.remove(id)?.let { closeQuietly(it) } }
        }
    }

    // region Network

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    /** The response head of an opened download and the body to read it from. */
    private class Opened(
        val stream: InputStream,
        /** Where redirects ended up; the file name is taken from here. */
        val url: String,
        val contentType: String?,
        val contentDisposition: String?,
        /** `-1` when the server didn't say. */
        val length: Long,
    )

    /** Blocking: connects (following redirects by hand so cookies can follow each hop) or decodes a `data:` URL. */
    private fun open(id: Long, request: DownloadRequest, startUrl: String): Opened {
        if (startUrl.startsWith("data:", ignoreCase = true)) return openDataUri(startUrl)
        val userAgent = request.userAgent?.takeIf { it.isNotBlank() } ?: defaultUserAgent()
        var current = startUrl
        var redirects = 0
        while (true) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connections[id] = Closeable { connection.disconnect() }
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "*/*")
                // Files are stored as sent; a server's transfer compression must not be undone silently.
                connection.setRequestProperty("Accept-Encoding", "identity")
                request.referrer?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Referer", it) }
                val cookie = runCatching { cookieSource(current, request.private) }.getOrNull()
                if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)

                val code = connection.responseCode
                if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val location = connection.getHeaderField("Location")?.trim()
                    connection.disconnect()
                    if (location.isNullOrEmpty()) throw HttpStatusException(code)
                    val next = URL(URL(current), location).toString()
                    if (!isWeb(next) || ++redirects > MAX_REDIRECTS) throw IOException("Bad redirect")
                    current = next
                    continue
                }
                if (code !in 200..299) throw HttpStatusException(code)
                return Opened(
                    stream = connection.inputStream,
                    url = current,
                    contentType = connection.contentType,
                    contentDisposition = connection.getHeaderField("Content-Disposition"),
                    length = connection.contentLengthLong,
                )
            } catch (e: Exception) {
                runCatching { connection.disconnect() }
                throw e
            }
        }
    }

    private fun defaultUserAgent(): String =
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull() ?: "Mozilla/5.0 (Linux; Android) Pane"

    /** `data:[<mime>][;base64],<payload>`. The whole payload is decoded in memory; these are small in practice. */
    private fun openDataUri(uri: String): Opened {
        val comma = uri.indexOf(',')
        if (comma < 0) throw IOException("Malformed data: URL")
        val meta = uri.substring(DATA_PREFIX.length, comma)
        val payload = uri.substring(comma + 1)
        val bytes = if (meta.endsWith(";base64", ignoreCase = true)) {
            if ('%' in payload) Base64.decode(percentDecode(payload), Base64.DEFAULT) else Base64.decode(payload, Base64.DEFAULT)
        } else {
            percentDecode(payload)
        }
        val mime = meta.substringBefore(';').trim().ifEmpty { "text/plain" }
        return Opened(ByteArrayInputStream(bytes), uri, mime, null, bytes.size.toLong())
    }

    private fun dataUriMime(url: String): String? {
        if (!url.startsWith("data:", ignoreCase = true)) return null
        val end = url.indexOfAny(charArrayOf(',', ';'), DATA_PREFIX.length).takeIf { it >= 0 } ?: return null
        return url.substring(DATA_PREFIX.length, end).trim().ifEmpty { "text/plain" }
    }

    /** Percent-decoding that keeps raw bytes (a `data:` payload isn't necessarily text). */
    private fun percentDecode(text: String): ByteArray {
        val out = ByteArrayOutputStream(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hex = if (c == '%' && i + 2 < text.length) text.substring(i + 1, i + 3).toIntOrNull(16) else null
            when {
                hex != null -> {
                    out.write(hex)
                    i += 3
                }
                c.code < 0x80 -> {
                    out.write(c.code)
                    i++
                }
                else -> {
                    val codePoint = text.codePointAt(i)
                    out.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                    i += Character.charCount(codePoint)
                }
            }
        }
        return out.toByteArray()
    }

    // endregion

    private suspend fun copy(id: Long, input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastReport = SystemClock.elapsedRealtime()
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            total += n
            val now = SystemClock.elapsedRealtime()
            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now
                repository.update(id, downloadedBytes = total)
            }
        }
        output.flush()
        return total
    }

    /** Where a download is being written. [file] is set only for the pre-Android 10 fallback. */
    private class Target(val uri: Uri, val name: String, val file: File?)

    private fun createTarget(name: String, mime: String?): Target =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) createMediaStoreTarget(name, mime) else createFileTarget(name)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createMediaStoreTarget(name: String, mime: String?): Target {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            if (mime != null) put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // Hidden from other apps until the last byte is written.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore refused $name")
        // MediaStore resolves name clashes itself ("report (1).pdf"); record the name it chose.
        val actual = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return Target(uri, actual ?: name, file = null)
    }

    private fun createFileTarget(name: String): Target {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
        val unique = UniqueNames.next(name) { File(dir, it).exists() }
        val file = File(dir, unique)
        if (!file.createNewFile()) throw IOException("Can't create $file")
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        return Target(uri, unique, file)
    }

    private fun publish(target: Target) {
        if (target.file == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishPending(target.uri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishPending(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    private fun discard(target: Target) {
        val file = target.file
        if (file != null) {
            file.delete()
        } else {
            context.contentResolver.delete(target.uri, null, null)
        }
    }

    // The runtime check below is what matters; lint can't follow the SDK-gated permission check.
    @SuppressLint("MissingPermission")
    private fun notifyFinished(id: Long, name: String, uri: Uri, mime: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Downloads")
                .setDescription("Finished downloads")
                .build(),
        )
        // A chooser rather than a bare VIEW intent, so a file no app can open says so instead of doing nothing.
        val open = Intent.createChooser(viewIntent(uri, mime), name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(name)
            .setContentText("Download complete")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_TAG, id.toInt(), notification) }
    }

    private fun mimeFor(name: String, contentType: String?): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        val fromExtension = if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
        val fromHeader = contentType?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { '/' in it && it != "application/octet-stream" }
        // The extension wins: MediaStore renames files whose MIME type disagrees with it.
        return fromExtension ?: fromHeader
    }

    /** data: and blob: URLs carry no file name, only payload; let the MIME type name the file. */
    private fun nameSource(url: String): String = if (url.startsWith("data:", ignoreCase = true) || url.startsWith("blob:", ignoreCase = true)) "" else url

    private fun isWeb(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 250L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 8
        const val DATA_PREFIX = "data:"
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_TAG = "download"

        /** Must match the FileProvider `android:authorities` in the manifest. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
