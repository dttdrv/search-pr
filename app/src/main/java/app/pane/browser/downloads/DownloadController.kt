package app.pane.browser.downloads

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import app.pane.browser.data.DownloadRecord
import app.pane.browser.data.DownloadStatus
import app.pane.browser.data.DownloadsRepository
import app.pane.browser.engine.Profiles
import app.pane.core.download.BlobReader
import app.pane.core.download.BlobStream
import app.pane.core.download.FileNames
import app.pane.core.download.Referer
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
    /** the page the link was on; its origin is sent as the `Referer`. */
    val referrer: String? = null,
    /** reads `blob:` addresses out of the page that made them. */
    val blobs: BlobReader? = null,
    /** the form that was posted to get this file, to be sent again. */
    val form: FormPost? = null,
    /** what the page called the file: the `download` attribute of the link that led here. */
    val name: String? = null,
)

/** a posted form: the web view's download callback reports only the address, so the page remembers the body. */
class FormPost(val contentType: String, val body: ByteArray)

/**
 * Saves files the page hands over (responses WebView can't render) and direct "Download Link"
 * requests into the user's Downloads.
 *
 * The bytes come from a small in-process downloader (one `HttpURLConnection` per file, streamed
 * through a 64 KB buffer, with the page's cookies, user agent and referrer) so no engine is involved.
 * It stays in-process because the platform `DownloadManager` can't read a blob or a `data:` address out of
 * a page or send a form again, and records every address in a system database, private tabs' included.
 * [DownloadService] keeps the process in the foreground meanwhile.
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
    private val jobs = ConcurrentHashMap<Long, Job>()

    /** The connection a running download is blocked on, so [cancel] can break out of the read. */
    private val connections = ConcurrentHashMap<Long, Closeable>()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Short user-facing messages ("Downloading report.pdf…", "Saved report.pdf") for toasts. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _started = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** emits when a download begins, the moment to ask for the notification permission. */
    val started: SharedFlow<Unit> = _started.asSharedFlow()

    init {
        // downloads a dead process left half written: their hidden partial files go too
        val startedAt = System.currentTimeMillis()
        scope.launch {
            runCatching { repository.markInterrupted(beforeMillis = startedAt) }.getOrNull()?.forEach { uri ->
                withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) } }
            }
        }
    }

    /**
     * Starts saving [request]. `http(s):` addresses are fetched, `data:` addresses are decoded and `blob:`
     * addresses are read out of the page that made them. Anything else can't be saved, so it says so.
     */
    fun start(request: DownloadRequest) {
        val url = request.url.trim()
        val blob = request.blobs != null && url.startsWith("blob:", ignoreCase = true)
        if (isWeb(url) || blob || url.startsWith("data:", ignoreCase = true)) {
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

    fun cancelAll() = jobs.keys.forEach(::cancel)

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

    /** an intent that opens the finished file in another app, or null if there is no file (any more: other apps can delete it). */
    fun viewIntent(record: DownloadRecord): Intent? {
        val uri = existing(record) ?: return null
        return viewIntent(uri, record.mime)
    }

    /** a share sheet for the finished file, or null if there is no file (any more). */
    fun shareIntent(record: DownloadRecord): Intent? {
        val uri = existing(record) ?: return null
        val send = Intent(Intent.ACTION_SEND)
            .setType(record.mime ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, record.fileName)
    }

    private fun existing(record: DownloadRecord): Uri? =
        record.contentUri?.let(Uri::parse)?.takeIf { runCatching { context.contentResolver.openAssetFileDescriptor(it, "r")?.close() }.isSuccess }

    private fun viewIntent(uri: Uri, mime: String?): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: context.contentResolver.getType(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Adds the row to the list straight away (name and size are best guesses), then fetches in the background. */
    private suspend fun begin(request: DownloadRequest, url: String) {
        val name = FileNames.choose(request.contentDisposition, nameSource(url), request.mimeType ?: dataUriMime(url), request.name)
        val mime = mimeFor(name, request.mimeType ?: dataUriMime(url))
        val length = request.contentLength.takeIf { it > 0 } ?: -1L
        // data: and blob: addresses say nothing useful (a data: one can be megabytes); only web addresses are kept.
        val source = if (request.private || !isWeb(url)) "" else url
        val id = try {
            repository.insert(source, name, mime, length)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _events.tryEmit("Couldn't download $name")
            return
        }
        // the cookie jar is looked up here, on the main thread, where the profile api wants it
        val cookies = Profiles.cookies(request.private)
        val job = scope.launch(start = CoroutineStart.LAZY) { transfer(id, request, url, cookies, name, mime, length) }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            connections.remove(id)
        }
        _events.tryEmit("Downloading $name…")
        _started.tryEmit(Unit)
        DownloadService.start(context)
        job.start()
    }

    private suspend fun transfer(id: Long, request: DownloadRequest, url: String, cookies: CookieManager?, guessedName: String, guessedMime: String?, guessedLength: Long) {
        var name = guessedName
        var target: Target? = null
        try {
            repository.update(id, status = DownloadStatus.Running)
            val opened = withContext(Dispatchers.IO) { open(id, request, url, cookies) }
            // The response knows better than the page what it is called and how big it is.
            val headerName = FileNames.choose(
                opened.contentDisposition ?: request.contentDisposition,
                nameSource(opened.url),
                opened.contentType ?: request.mimeType,
                request.name,
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
            name = created.name
            repository.update(id, fileName = name, contentUri = created.uri.toString())
            val written = withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(created.uri) ?: throw IOException("Can't write ${created.uri}")
                out.use { sink -> opened.stream.use { copy(id, it, sink) } }
            }
            currentCoroutineContext().ensureActive()
            // a connection that ends early must not pass for a finished file
            if (opened.length >= 0 && written != opened.length) throw IOException("Got $written of ${opened.length} bytes")
            name = withContext(Dispatchers.IO) { publish(created) }
            repository.update(
                id,
                status = DownloadStatus.Completed,
                downloadedBytes = written,
                totalBytes = written,
                contentUri = created.uri.toString(),
                fileName = name,
            )
            _events.tryEmit("Saved $name")
            notifyFinished(id, name, created.uri, mime)
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
                DownloadNotifications.done(context, id, name, "Couldn't download", DownloadNotifications.list(context))
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

    /** blocking: connects (following redirects by hand so cookies can follow each hop), decodes a `data:` URL or reads a blob. */
    private fun open(id: Long, request: DownloadRequest, startUrl: String, cookies: CookieManager?): Opened {
        if (startUrl.startsWith("data:", ignoreCase = true)) return openDataUri(startUrl)
        if (startUrl.startsWith("blob:", ignoreCase = true)) return openBlob(request.blobs ?: throw IOException("No page to read $startUrl from"), startUrl)
        val userAgent = request.userAgent?.takeIf { it.isNotBlank() } ?: defaultUserAgent()
        var current = startUrl
        var form = request.form
        var redirects = 0
        while (true) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connections[id] = Closeable { connection.disconnect() }
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.requestMethod = if (form != null) "POST" else "GET"
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "*/*")
                // Files are stored as sent; a server's transfer compression must not be undone silently.
                connection.setRequestProperty("Accept-Encoding", "identity")
                Referer.origin(request.referrer, current)?.let {
                    connection.setRequestProperty("Referer", it)
                    if (form != null) connection.setRequestProperty("Origin", it.trimEnd('/'))
                }
                val cookie = runCatching { cookies?.getCookie(current) }.getOrNull()
                if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
                // the body goes last: writing it sends the headers
                if (form != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", form.contentType)
                    connection.setFixedLengthStreamingMode(form.body.size)
                    connection.outputStream.use { it.write(form.body) }
                }

                val code = connection.responseCode
                if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val location = connection.getHeaderField("Location")?.trim()
                    connection.disconnect()
                    if (location.isNullOrEmpty()) throw HttpStatusException(code)
                    val next = URL(URL(current), location).toString()
                    if (!isWeb(next) || ++redirects > MAX_REDIRECTS) throw IOException("Bad redirect")
                    // only 307 and 308 send the form again
                    if (code != 307 && code != 308) form = null
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

    private fun openBlob(reader: BlobReader, url: String): Opened {
        val blob = BlobStream.open(reader, url, BLOB_SLICE)
        return Opened(blob, url, blob.type.ifEmpty { null }, null, blob.size)
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
        return Target(uri, displayName(uri) ?: name, file = null)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun createFileTarget(name: String): Target {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
        val unique = UniqueNames.next(name) { File(dir, it).exists() }
        val file = File(dir, unique)
        if (!file.createNewFile()) throw IOException("Can't create $file")
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        return Target(uri, unique, file)
    }

    /** makes the finished file visible and returns its name: MediaStore settles a clash ("report (1).pdf") only now. */
    private fun publish(target: Target): String {
        if (target.file != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return target.name
        publishPending(target.uri)
        return displayName(target.uri) ?: target.name
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

    /** a touch opens the file in whichever app takes it, but a file that can run code goes through the list's warning first. */
    private fun notifyFinished(id: Long, name: String, uri: Uri, mime: String?) {
        val tap = if (FileNames.isPotentiallyDangerous(name)) {
            DownloadNotifications.list(context)
        } else {
            // A chooser rather than a bare VIEW intent, so a file no app can open says so instead of doing nothing.
            val open = Intent.createChooser(viewIntent(uri, mime), name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(context, id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        DownloadNotifications.done(context, id, name, "Download complete", tap)
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
        const val BLOB_SLICE = 1L shl 20
        const val PROGRESS_INTERVAL_MS = 250L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 8
        const val DATA_PREFIX = "data:"

        /** Must match the FileProvider `android:authorities` in the manifest. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
