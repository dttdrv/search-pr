package app.pane.browser.engine.prompts

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import app.pane.core.download.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies documents picked for an `<input type=file>` into app-private storage.
 *
 * The system picker hands out `content:` URIs that Gecko can't reliably open by path; a private
 * copy with the original name works everywhere and is pruned after a day.
 */
internal object Uploads {
    private const val DIR = "uploads"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** Returns `file:` URIs for the copies, skipping anything that couldn't be read. */
    suspend fun stage(context: Context, uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, DIR)
        prune(root)
        val batch = File(root, UUID.randomUUID().toString())
        uris.mapIndexedNotNull { index, uri -> copy(context, uri, File(batch, index.toString())) }
    }

    private fun copy(context: Context, uri: Uri, dir: File): Uri? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return try {
            val name = FileNames.sanitize(displayName(context, uri) ?: "").ifEmpty { "file" }
            if (!dir.mkdirs() && !dir.isDirectory) return null
            val target = File(dir, name)
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target.toUri()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun prune(root: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        root.listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
    }
}
