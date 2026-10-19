package com.personalai.instantread.history

import android.app.Application
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewHistoryRepositoryTest {
    private lateinit var context: Application
    private lateinit var database: PreviewHistoryDatabase
    private lateinit var repository: PreviewHistoryRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DATABASE_NAME)
        database = PreviewHistoryDatabase(context)
        repository = PreviewHistoryRepository(database) { now }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun upsertImportedPersistsRecordsForNewRepositoryInstance() {
        val source = importedFile(
            id = "import-1",
            displayName = null,
            fileType = FileType.HTML,
            sourceKind = SourceKind.ACTION_VIEW
        )

        repository.upsertImported(source)
        database.close()

        database = PreviewHistoryDatabase(context)
        repository = PreviewHistoryRepository(database) { now }
        val persisted = repository.findById("import-1")

        requireNotNull(persisted)
        assertEquals("import-1", persisted.id)
        assertNull(persisted.displayName)
        assertEquals(FileType.HTML, persisted.fileType)
        assertEquals(SourceKind.ACTION_VIEW, persisted.sourceKind)
        assertEquals(source.localFile.path, persisted.localFile.path)
        assertEquals(1_000L, persisted.createdAt)
        assertEquals(1_000L, persisted.lastOpenedAt)
        assertFalse(persisted.favorite)
        assertFalse(persisted.jsAllowed)
    }

    @Test
    fun reopeningSameSchemaPreservesHistoryFavoriteAndSourceFile() {
        val source = importedFile(
            id = "restart-id",
            displayName = "Restart.md",
            fileType = FileType.MARKDOWN
        )
        repository.upsertImported(source)
        repository.toggleFavorite("restart-id")
        now = 2_000L
        repository.markOpened("restart-id")
        database.close()

        database = PreviewHistoryDatabase(context)
        repository = PreviewHistoryRepository(database) { now }
        val reopened = requireNotNull(repository.findById("restart-id"))

        assertEquals("Restart.md", reopened.displayName)
        assertEquals(1_000L, reopened.createdAt)
        assertEquals(2_000L, reopened.lastOpenedAt)
        assertTrue(reopened.favorite)
        assertTrue(reopened.localFile.exists())
        assertEquals("content", reopened.localFile.readText())
    }

    @Test
    fun upsertImportedPreservesFavoriteAndCreatedAt() {
        val first = importedFile(
            id = "import-1",
            displayName = "First.html",
            fileType = FileType.HTML
        )
        repository.upsertImported(first)
        repository.toggleFavorite("import-1")

        now = 2_000L
        val second = importedFile(
            id = "import-1",
            displayName = "Renamed.html",
            fileType = FileType.HTML
        )
        val updated = repository.upsertImported(second)

        assertEquals("Renamed.html", updated.displayName)
        assertEquals(1_000L, updated.createdAt)
        assertEquals(2_000L, updated.lastOpenedAt)
        assertTrue(updated.favorite)
    }

    @Test
    fun listRecentOrdersFavoritesFirstThenLastOpened() {
        now = 1_000L
        repository.upsertImported(importedFile(id = "old", displayName = "Old.txt", fileType = FileType.TEXT))
        now = 2_000L
        repository.upsertImported(importedFile(id = "favorite", displayName = "Favorite.md", fileType = FileType.MARKDOWN))
        repository.toggleFavorite("favorite")
        now = 3_000L
        repository.upsertImported(importedFile(id = "new", displayName = "New.zip", fileType = FileType.ZIP))

        val recentIds = repository.listRecent().map { it.id }

        assertEquals(listOf("favorite", "new", "old"), recentIds)
    }

    @Test
    fun markOpenedUpdatesLastOpenedAt() {
        repository.upsertImported(importedFile(id = "import-1"))

        now = 5_000L
        val opened = repository.markOpened("import-1")

        requireNotNull(opened)
        assertEquals(1_000L, opened.createdAt)
        assertEquals(5_000L, opened.lastOpenedAt)
    }

    @Test
    fun deleteRemovesRecordAndReturnsDeletedHistory() {
        repository.upsertImported(importedFile(id = "import-1"))

        val deleted = repository.delete("import-1")

        requireNotNull(deleted)
        assertEquals("import-1", deleted.id)
        assertNull(repository.findById("import-1"))
        assertNull(repository.delete("import-1"))
    }

    @Test
    fun setJavaScriptAllowedOnlyChangesTheTargetDocument() {
        repository.upsertImported(importedFile(id = "doc-a"))
        repository.upsertImported(importedFile(id = "doc-b"))

        val updated = requireNotNull(repository.setJavaScriptAllowed("doc-a", true))

        assertTrue(updated.jsAllowed)
        assertTrue(requireNotNull(repository.findById("doc-a")).jsAllowed)
        assertFalse(requireNotNull(repository.findById("doc-b")).jsAllowed)
        assertNull(repository.setJavaScriptAllowed("missing", true))
    }

    @Test
    fun reimportedCopyDoesNotInheritScriptAuthorization() {
        repository.upsertImported(importedFile(id = "first-import"))
        repository.setJavaScriptAllowed("first-import", true)

        repository.upsertImported(importedFile(id = "second-import"))

        assertTrue(requireNotNull(repository.findById("first-import")).jsAllowed)
        assertFalse(requireNotNull(repository.findById("second-import")).jsAllowed)
    }

    @Test
    fun openingFutureDatabaseVersionFailsWithoutReplacingRecords() {
        val source = importedFile(id = "import-1")
        repository.upsertImported(source)
        database.close()

        val futureVersion = PreviewHistoryDatabase.DATABASE_VERSION + 1
        val file = context.getDatabasePath(DATABASE_NAME)
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            db.version = futureVersion
        }

        val error = assertThrows(FutureSchemaVersionException::class.java) {
            PreviewHistoryDatabase(context).readableDatabase
        }
        assertEquals(futureVersion, error.storedVersion)
        assertEquals(PreviewHistoryDatabase.DATABASE_VERSION, error.supportedVersion)

        val reopened = android.database.sqlite.SQLiteDatabase.openDatabase(
            file.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        try {
            assertEquals(futureVersion, reopened.version)
            reopened.rawQuery("SELECT COUNT(*) FROM preview_history", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            reopened.close()
        }
    }

    private fun importedFile(
        id: String,
        displayName: String? = "Source.html",
        fileType: FileType = FileType.HTML,
        sourceKind: SourceKind = SourceKind.APP_PICKER
    ): ImportedFile {
        val extension = when (fileType) {
            FileType.HTML -> "html"
            FileType.MARKDOWN -> "md"
            FileType.TEXT -> "txt"
            FileType.ZIP -> "zip"
        }
        val localFile = File(context.filesDir, "imports/$id/source.$extension").apply {
            parentFile?.mkdirs()
            writeText("content")
        }
        return ImportedFile(
            id = id,
            displayName = displayName,
            fileType = fileType,
            sourceKind = sourceKind,
            localFile = localFile,
            sizeBytes = localFile.length()
        )
    }

    private companion object {
        const val DATABASE_NAME = "preview-history.db"
    }
}
