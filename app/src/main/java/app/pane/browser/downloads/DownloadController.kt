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
import android.webkit.MimeTypeMap
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
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Saves files the engine hands over (responses it can't render) and direct "Download Link"
 * requests into the user's Downloads.
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
     * The engine runtime, used for direct downloads so they share Gecko's network stack, cookies
     * and DNS settings. Set by the app container right after the runtime is created.
     */
    var runtime: GeckoRuntime? = null

    private var executor: GeckoWebExecutor? = null
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val streams = ConcurrentHashMap<Long, InputStream>()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Short user-facing messages ("Downloading report.pdf…", "Saved report.pdf") for toasts. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        val startedAt = System.currentTimeMillis()
        scope.launch { runCatching { repository.markInterrupted(beforeMillis = startedAt) } }
    }

    fun onExternalResponse(tabId: String, response: WebResponse, private: Boolean) {
        scope.launch { start(response, private) }
    }

    /** Downloads [url] directly (context menu "Download Link" / "Save Image"). */
    fun download(url: String, private: Boolean, referrer: String? = null) {
        val fetcher = executor ?: runtime?.let { GeckoWebExecutor(it) }?.also { executor = it }
        if (fetcher == null) {
            _events.tryEmit("Couldn't start the download")
            return
        }
        val builder = WebRequest.Builder(url).header("Accept", "*/*")
        if (referrer != null) builder.referrer(referrer)
        val request = builder.build()
        val flags = if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        val fallbackName = FileNames.choose(null, nameSource(url), null)
        scope.launch {
            val response = try {
                fetcher.fetch(request, flags).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when {
                response == null -> _events.tryEmit("Couldn't download $fallbackName")
                response.statusCode !in 200..299 -> {
                    closeQuietly(response.body)
                    _events.tryEmit("Couldn't download $fallbackName (error ${response.statusCode})")
                }
                else -> start(response, private)
            }
        }
    }

    /** Stops a running download and discards the partial file. */
    fun cancel(id: Long) {
        val job = jobs[id]
        if (job == null) {
            scope.launch { repository.update(id, status = DownloadStatus.Cancelled) }
            return
        }
        job.cancel()
        // A read blocked on the network only notices cancellation once its stream is closed.
        streams.remove(id)?.let { stream -> scope.launch(Dispatchers.IO) { closeQuietly(stream) } }
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

    private suspend fun start(response: WebResponse, private: Boolean) {
        val body = response.body
        val contentType = header(response, "Content-Type")
        val name = FileNames.choose(header(response, "Content-Disposition"), nameSource(response.uri), contentType)
        if (body == null) {
            _events.tryEmit("Couldn't download $name")
            return
        }
        val length = header(response, "Content-Length")?.trim()?.toLongOrNull() ?: -1L
        val mime = mimeFor(name, contentType)
        // data: URLs can be megabytes long and say nothing useful; only web addresses are kept.
        val source = if (private) "" else response.uri.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
        val id = try {
            repository.insert(source, name, mime, length)
        } catch (e: CancellationException) {
            closeQuietly(body)
            throw e
        } catch (_: Exception) {
            closeQuietly(body)
            _events.tryEmit("Couldn't download $name")
            return
        }
        streams[id] = body
        val job = scope.launch(start = CoroutineStart.LAZY) { transfer(id, name, mime, body) }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            streams.remove(id)
        }
        _events.tryEmit("Downloading $name…")
        job.start()
    }

    private suspend fun transfer(id: Long, name: String, mime: String?, body: InputStream) {
        var target: Target? = null
        try {
            repository.update(id, status = DownloadStatus.Running)
            val created = withContext(Dispatchers.IO) { createTarget(name, mime) }
            target = created
            if (created.name != name) repository.update(id, fileName = created.name)
            val written = withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(created.uri) ?: throw IOException("Can't write ${created.uri}")
                out.use { copy(id, body, it) }
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
            // Closing the stream to cancel can surface as an IOException rather than a cancellation.
            val cancelled = e is CancellationException || !currentCoroutineContext().isActive
            val partial = target
            withContext(NonCancellable + Dispatchers.IO) {
                closeQuietly(body)
                if (partial != null) runCatching { discard(partial) }
                repository.update(id, status = if (cancelled) DownloadStatus.Cancelled else DownloadStatus.Failed)
            }
            if (e is CancellationException) throw e
            if (!cancelled) _events.tryEmit("Couldn't download $name")
        } finally {
            closeQuietly(body)
        }
    }

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
    private fun nameSource(url: String): String = if (url.startsWith("data:") || url.startsWith("blob:")) "" else url

    private fun header(response: WebResponse, name: String): String? =
        response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun closeQuietly(stream: InputStream?) {
        try {
            stream?.close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 250L
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_TAG = "download"

        /** Must match the FileProvider `android:authorities` in the manifest. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
