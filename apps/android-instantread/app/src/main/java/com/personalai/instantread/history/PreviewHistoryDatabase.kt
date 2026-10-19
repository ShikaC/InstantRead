package com.personalai.instantread.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PreviewHistoryDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        val storedVersion = db.version
        if (storedVersion > DATABASE_VERSION) {
            throw FutureSchemaVersionException(storedVersion, DATABASE_VERSION)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE preview_history(
              id TEXT PRIMARY KEY,
              display_name TEXT,
              file_type TEXT NOT NULL,
              source_kind TEXT NOT NULL,
              local_path TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              last_opened_at INTEGER NOT NULL,
              favorite INTEGER NOT NULL,
              js_allowed INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw UnsupportedSchemaUpgradeException(oldVersion, newVersion)
    }

    companion object {
        const val TABLE = "preview_history"
        const val DATABASE_NAME = "preview-history.db"
        const val DATABASE_VERSION = 1
    }
}

class FutureSchemaVersionException(
    val storedVersion: Int,
    val supportedVersion: Int
) : IllegalStateException(
    "Database version $storedVersion is newer than supported version $supportedVersion"
)

class UnsupportedSchemaUpgradeException(
    val oldVersion: Int,
    val newVersion: Int
) : IllegalStateException(
    "No migration from database version $oldVersion to $newVersion"
)
