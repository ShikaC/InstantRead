package com.personalai.instantread.history

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import java.io.File

class PreviewHistoryRepository(
    private val database: PreviewHistoryDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun upsertImported(importedFile: ImportedFile): PreviewHistory {
        val now = clock()
        val existing = findById(importedFile.id)
        val history = PreviewHistory(
            id = importedFile.id,
            displayName = importedFile.displayName,
            fileType = importedFile.fileType,
            sourceKind = importedFile.sourceKind,
            localFile = importedFile.localFile,
            createdAt = existing?.createdAt ?: now,
            lastOpenedAt = now,
            favorite = existing?.favorite ?: false,
            jsAllowed = existing?.jsAllowed ?: false
        )
        database.writableDatabase.insertWithOnConflict(
            PreviewHistoryDatabase.TABLE,
            null,
            history.toValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return history
    }

    fun listRecent(): List<PreviewHistory> {
        val cursor = database.readableDatabase.query(
            PreviewHistoryDatabase.TABLE,
            null,
            null,
            null,
            null,
            null,
            "favorite DESC, last_opened_at DESC"
        )
        return cursor.use { it.readAll() }
    }

    fun findById(id: String): PreviewHistory? {
        val cursor = database.readableDatabase.query(
            PreviewHistoryDatabase.TABLE,
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) it.toHistory() else null
        }
    }

    fun markOpened(id: String): PreviewHistory? {
        val existing = findById(id) ?: return null
        val updated = existing.copy(lastOpenedAt = clock())
        database.writableDatabase.update(
            PreviewHistoryDatabase.TABLE,
            updated.toValues(),
            "id = ?",
            arrayOf(id)
        )
        return updated
    }

    fun toggleFavorite(id: String): PreviewHistory? {
        val existing = findById(id) ?: return null
        val updated = existing.copy(favorite = !existing.favorite)
        database.writableDatabase.update(
            PreviewHistoryDatabase.TABLE,
            updated.toValues(),
            "id = ?",
            arrayOf(id)
        )
        return updated
    }

    /**
     * 设置单个文档的脚本授权。
     *
     * 授权只作用于这一次导入副本：重新导入会生成新的 id，旧授权不会自动继承。
     */
    fun setJavaScriptAllowed(id: String, allowed: Boolean): PreviewHistory? {
        val existing = findById(id) ?: return null
        val updated = existing.copy(jsAllowed = allowed)
        database.writableDatabase.update(
            PreviewHistoryDatabase.TABLE,
            updated.toValues(),
            "id = ?",
            arrayOf(id)
        )
        return updated
    }

    fun delete(id: String): PreviewHistory? {
        val existing = findById(id) ?: return null
        database.writableDatabase.delete(
            PreviewHistoryDatabase.TABLE,
            "id = ?",
            arrayOf(id)
        )
        return existing
    }

    private fun PreviewHistory.toValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("display_name", displayName)
            put("file_type", fileType.name)
            put("source_kind", sourceKind.name)
            put("local_path", localFile.path)
            put("created_at", createdAt)
            put("last_opened_at", lastOpenedAt)
            put("favorite", if (favorite) 1 else 0)
            put("js_allowed", if (jsAllowed) 1 else 0)
        }
    }

    private fun Cursor.readAll(): List<PreviewHistory> {
        val result = mutableListOf<PreviewHistory>()
        while (moveToNext()) {
            result += toHistory()
        }
        return result
    }

    private fun Cursor.toHistory(): PreviewHistory {
        return PreviewHistory(
            id = getString(getColumnIndexOrThrow("id")),
            displayName = getString(getColumnIndexOrThrow("display_name")),
            fileType = FileType.valueOf(getString(getColumnIndexOrThrow("file_type"))),
            sourceKind = SourceKind.valueOf(getString(getColumnIndexOrThrow("source_kind"))),
            localFile = File(getString(getColumnIndexOrThrow("local_path"))),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            lastOpenedAt = getLong(getColumnIndexOrThrow("last_opened_at")),
            favorite = getInt(getColumnIndexOrThrow("favorite")) == 1,
            jsAllowed = getInt(getColumnIndexOrThrow("js_allowed")) == 1
        )
    }
}
