package app.pane.browser.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.pane.core.suggest.Frecency
import app.pane.core.suggest.PlaceCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class HistoryItem(val url: String, val title: String, val visitCount: Int, val lastVisited: Long)

data class Bookmark(val id: Long, val url: String, val title: String, val created: Long, val position: Int, val favorite: Boolean)

enum class DownloadStatus { Pending, Running, Paused, Completed, Failed, Cancelled }

data class DownloadRecord(
    val id: Long,
    val url: String,
    val fileName: String,
    val mime: String?,
    val contentUri: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val created: Long,
)

/** Base for repositories: a change counter drives cold flows that re-query on every write. */
abstract class Repository(protected val database: PaneDatabase) {
    private val version = MutableStateFlow(0)

    protected fun changed() {
        version.value++
    }

    protected fun <T> observe(query: (SQLiteDatabase) -> T): Flow<T> =
        version.map { query(database.readableDatabase) }.distinctUntilChanged().flowOn(Dispatchers.IO)

    protected suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) { block(database.readableDatabase) }

    protected suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        block(database.writableDatabase).also { changed() }
    }
}

private inline fun <T> Cursor.mapAll(transform: (Cursor) -> T): List<T> = use {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += transform(this)
    out
}

private fun likeEscape(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

class HistoryRepository(database: PaneDatabase, private val clock: () -> Long = System::currentTimeMillis) : Repository(database) {

    private fun item(c: Cursor) = HistoryItem(c.getString(0), c.getString(1), c.getInt(2), c.getLong(3))

    // No UPSERT: SQLite on API 26–29 predates it.
    suspend fun recordVisit(url: String, title: String?) = write { db ->
        val now = clock()
        db.beginTransaction()
        try {
            val updated = db.compileStatement(
                "UPDATE history SET visit_count = visit_count + 1, last_visited = ?, title = CASE WHEN ? != '' THEN ? ELSE title END WHERE url = ?",
            ).use { st ->
                st.bindLong(1, now)
                st.bindString(2, title.orEmpty())
                st.bindString(3, title.orEmpty())
                st.bindString(4, url)
                st.executeUpdateDelete()
            }
            if (updated == 0) {
                db.insert(
                    "history",
                    null,
                    ContentValues().apply {
                        put("url", url)
                        put("title", title.orEmpty())
                        put("visit_count", 1)
                        put("last_visited", now)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateTitle(url: String, title: String) = write { db ->
        db.update("history", ContentValues().apply { put("title", title) }, "url = ?", arrayOf(url))
    }

    suspend fun visited(urls: Array<String>): BooleanArray = read { db ->
        if (urls.isEmpty()) return@read BooleanArray(0)
        val found = HashSet<String>()
        urls.toList().chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.rawQuery("SELECT url FROM history WHERE url IN ($placeholders)", chunk.toTypedArray()).mapAll { found += it.getString(0) }
        }
        BooleanArray(urls.size) { urls[it] in found }
    }

    fun observeRecent(limit: Int = 500): Flow<List<HistoryItem>> = observe { db ->
        db.rawQuery("SELECT url, title, visit_count, last_visited FROM history ORDER BY last_visited DESC LIMIT ?", arrayOf(limit.toString()))
            .mapAll(::item)
    }

    suspend fun search(query: String, limit: Int = 200): List<HistoryItem> = read { db -> searchQuery(db, query, limit) }

    /** Like [search], but re-runs after every change so a filtered list stays current. */
    fun observeSearch(query: String, limit: Int = 200): Flow<List<HistoryItem>> = observe { db -> searchQuery(db, query, limit) }

    private fun searchQuery(db: SQLiteDatabase, query: String, limit: Int): List<HistoryItem> {
        val like = "%${likeEscape(query.trim())}%"
        return db.rawQuery(
            "SELECT url, title, visit_count, last_visited FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY last_visited DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).mapAll(::item)
    }

    /** Candidates for address bar suggestions and inline autocomplete. */
    suspend fun candidates(query: String, limit: Int = 60): List<PlaceCandidate> = read { db ->
        val first = query.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        val like = "%${likeEscape(first)}%"
        db.rawQuery(
            "SELECT url, title, visit_count, last_visited FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY visit_count DESC, last_visited DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).mapAll { PlaceCandidate(it.getString(0), it.getString(1), it.getInt(2), it.getLong(3)) }
    }

    /** Most "frecent" pages, for the start page when there are no favourites yet. */
    suspend fun topSites(limit: Int = 8): List<HistoryItem> = read { db ->
        val now = clock()
        db.rawQuery("SELECT url, title, visit_count, last_visited FROM history ORDER BY visit_count DESC LIMIT 200", null)
            .mapAll(::item)
            .sortedByDescending { Frecency.score(it.visitCount, it.lastVisited, false, now) }
            .distinctBy { app.pane.core.url.UrlInput.hostOf(it.url) }
            .take(limit)
    }

    suspend fun delete(url: String) = write { db -> db.delete("history", "url = ?", arrayOf(url)) }

    suspend fun deleteSince(sinceMillis: Long) = write { db -> db.delete("history", "last_visited >= ?", arrayOf(sinceMillis.toString())) }

    suspend fun clear() = write { db -> db.delete("history", null, null) }
}

class BookmarksRepository(database: PaneDatabase, private val clock: () -> Long = System::currentTimeMillis) : Repository(database) {

    private fun item(c: Cursor) = Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4), c.getInt(5) != 0)
    private val columns = "id, url, title, created, position, favorite"

    fun observeAll(): Flow<List<Bookmark>> = observe { db ->
        db.rawQuery("SELECT $columns FROM bookmarks ORDER BY position ASC", null).mapAll(::item)
    }

    fun observeFavorites(): Flow<List<Bookmark>> = observe { db ->
        db.rawQuery("SELECT $columns FROM bookmarks WHERE favorite = 1 ORDER BY position ASC", null).mapAll(::item)
    }

    fun observeIsBookmarked(url: String): Flow<Boolean> = observe { db ->
        db.rawQuery("SELECT 1 FROM bookmarks WHERE url = ? LIMIT 1", arrayOf(url)).use { it.moveToFirst() }
    }

    suspend fun all(): List<Bookmark> = read { db -> db.rawQuery("SELECT $columns FROM bookmarks ORDER BY position ASC", null).mapAll(::item) }

    suspend fun candidates(query: String, limit: Int = 30): List<PlaceCandidate> = read { db ->
        val like = "%${likeEscape(query.trim())}%"
        db.rawQuery("SELECT $columns FROM bookmarks WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' LIMIT ?", arrayOf(like, like, limit.toString()))
            .mapAll(::item)
            .map { PlaceCandidate(it.url, it.title, visitCount = 1, lastVisited = it.created, bookmarked = true) }
    }

    suspend fun add(url: String, title: String, favorite: Boolean = false): Long = write { db ->
        val position = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks", null).use { it.moveToFirst(); it.getInt(0) }
        db.insertWithOnConflict(
            "bookmarks",
            null,
            ContentValues().apply {
                put("url", url)
                put("title", title.ifBlank { url })
                put("created", clock())
                put("position", position)
                put("favorite", if (favorite) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    suspend fun update(id: Long, title: String, url: String) = write { db ->
        db.update("bookmarks", ContentValues().apply { put("title", title); put("url", url) }, "id = ?", arrayOf(id.toString()))
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = write { db ->
        db.update("bookmarks", ContentValues().apply { put("favorite", if (favorite) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    suspend fun removeUrl(url: String) = write { db -> db.delete("bookmarks", "url = ?", arrayOf(url)) }

    suspend fun remove(id: Long) = write { db -> db.delete("bookmarks", "id = ?", arrayOf(id.toString())) }

    /** Puts a removed bookmark back exactly as it was (id, position, date), for "Undo". */
    suspend fun restore(bookmark: Bookmark) = write { db ->
        db.insertWithOnConflict(
            "bookmarks",
            null,
            ContentValues().apply {
                put("id", bookmark.id)
                put("url", bookmark.url)
                put("title", bookmark.title)
                put("created", bookmark.created)
                put("position", bookmark.position)
                put("favorite", if (bookmark.favorite) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    /** Rewrites positions so [orderedIds] appear in that order. */
    suspend fun reorder(orderedIds: List<Long>) = write { db ->
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { index, id ->
                db.update("bookmarks", ContentValues().apply { put("position", index) }, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

class DownloadsRepository(database: PaneDatabase, private val clock: () -> Long = System::currentTimeMillis) : Repository(database) {

    private fun item(c: Cursor) = DownloadRecord(
        id = c.getLong(0), url = c.getString(1), fileName = c.getString(2), mime = c.getString(3), contentUri = c.getString(4),
        totalBytes = c.getLong(5), downloadedBytes = c.getLong(6), status = DownloadStatus.entries[c.getInt(7)], created = c.getLong(8),
    )
    private val columns = "id, url, file_name, mime, content_uri, total_bytes, downloaded_bytes, status, created"

    fun observeAll(): Flow<List<DownloadRecord>> = observe { db ->
        db.rawQuery("SELECT $columns FROM downloads ORDER BY created DESC", null).mapAll(::item)
    }

    suspend fun get(id: Long): DownloadRecord? = read { db ->
        db.rawQuery("SELECT $columns FROM downloads WHERE id = ?", arrayOf(id.toString())).mapAll(::item).firstOrNull()
    }

    suspend fun insert(url: String, fileName: String, mime: String?, totalBytes: Long): Long = write { db ->
        db.insert(
            "downloads",
            null,
            ContentValues().apply {
                put("url", url)
                put("file_name", fileName)
                put("mime", mime)
                put("total_bytes", totalBytes)
                put("status", DownloadStatus.Pending.ordinal)
                put("created", clock())
            },
        )
    }

    suspend fun update(
        id: Long,
        status: DownloadStatus? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        contentUri: String? = null,
        fileName: String? = null,
    ) = write { db ->
        val values = ContentValues().apply {
            status?.let { put("status", it.ordinal) }
            downloadedBytes?.let { put("downloaded_bytes", it) }
            totalBytes?.let { put("total_bytes", it) }
            contentUri?.let { put("content_uri", it) }
            fileName?.let { put("file_name", it) }
        }
        if (values.size() > 0) db.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun delete(id: Long) = write { db -> db.delete("downloads", "id = ?", arrayOf(id.toString())) }

    suspend fun clearFinished() = clearFinishedSince(0L)

    /** Removes finished records created at or after [sinceMillis]; running downloads stay. */
    suspend fun clearFinishedSince(sinceMillis: Long) = write { db ->
        db.delete(
            "downloads",
            "status IN (?, ?, ?) AND created >= ?",
            arrayOf(
                DownloadStatus.Completed.ordinal.toString(),
                DownloadStatus.Failed.ordinal.toString(),
                DownloadStatus.Cancelled.ordinal.toString(),
                sinceMillis.toString(),
            ),
        )
    }

    /**
     * Downloads created before [beforeMillis] (app start) and still marked as in progress were cut
     * off when the process died.
     */
    suspend fun markInterrupted(beforeMillis: Long) = write { db ->
        db.update(
            "downloads",
            ContentValues().apply { put("status", DownloadStatus.Failed.ordinal) },
            "status IN (?, ?, ?) AND created < ?",
            arrayOf(
                DownloadStatus.Pending.ordinal.toString(),
                DownloadStatus.Running.ordinal.toString(),
                DownloadStatus.Paused.ordinal.toString(),
                beforeMillis.toString(),
            ),
        )
    }
}
