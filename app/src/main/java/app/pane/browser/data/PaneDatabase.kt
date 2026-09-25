package app.pane.browser.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite: three small tables don't justify an ORM, and fewer dependencies means less
 * attack surface. All access goes through the repositories on [kotlinx.coroutines.Dispatchers.IO].
 */
class PaneDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE history (
                url TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                visit_count INTEGER NOT NULL DEFAULT 0,
                last_visited INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX history_last_visited ON history(last_visited DESC)")
        db.execSQL(
            """CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                created INTEGER NOT NULL,
                position INTEGER NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL(
            """CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                file_name TEXT NOT NULL,
                mime TEXT,
                content_uri TEXT,
                total_bytes INTEGER NOT NULL DEFAULT -1,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                status INTEGER NOT NULL,
                created INTEGER NOT NULL
            )""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val NAME = "pane.db"
        const val VERSION = 1
    }
}
