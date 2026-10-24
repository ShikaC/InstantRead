package com.personalai.instantread.history

import android.app.Application
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentHistoryClearControllerTest {
    private lateinit var context: Application
    private lateinit var database: PreviewHistoryDatabase
    private lateinit var repository: PreviewHistoryRepository
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private var now = 1_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(PreviewHistoryDatabase.DATABASE_NAME)
        database = PreviewHistoryDatabase(context)
        repository = PreviewHistoryRepository(database) { now }
        filesDir = File(context.cacheDir, "clear-files").apply { mkdirs() }
        cacheDir = File(context.cacheDir, "clear-cache").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PreviewHistoryDatabase.DATABASE_NAME)
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    @Test
    fun clearNotFavoriteDeletesOnlyUnfavoritedRecords() {
        upsert("keep-a", favorite = true)
        upsert("drop-a")
        upsert("keep-b", favorite = true)
        upsert("drop-b")
        val cleanedIds = mutableListOf<String>()
        val controller = controller { id, _, _ -> cleanedIds += id; emptyList() }

        val result = controller.clearNotFavorite()

        assertEquals(2, result.clearedCount)
        assertEquals(2, result.cleanupScheduledCount)
        assertFalse(result.hasCleanupFailure)
        assertEquals(setOf("drop-a", "drop-b"), cleanedIds.toSet())
        assertEquals(listOf("keep-b", "keep-a"), repository.listRecent().map { it.id })
    }

    @Test
    fun clearNotFavoriteReportsPartialCleanupFailure() {
        upsert("drop-a")
        upsert("drop-b")
        val controller = controller { id, _, _ ->
            if (id == "drop-b") throw java.io.IOException("busy") else emptyList()
        }

        val result = controller.clearNotFavorite()

        assertEquals(2, result.clearedCount)
        assertEquals(1, result.cleanupScheduledCount)
        assertTrue(result.hasCleanupFailure)
        assertTrue(repository.listRecent().isEmpty())
    }

    @Test
    fun clearNotFavoriteLeavesEverythingWhenOnlyFavoritesExist() {
        upsert("keep-a", favorite = true)
        var cleanupCalls = 0
        val controller = controller { _, _, _ -> cleanupCalls += 1; emptyList() }

        val result = controller.clearNotFavorite()

        assertEquals(0, result.clearedCount)
        assertEquals(0, cleanupCalls)
        assertEquals(listOf("keep-a"), repository.listRecent().map { it.id })
    }

    @Test
    fun clearNotFavoriteRemovesImportCopiesAndCachedPreview() {
        val importId = "12345678-1234-1234-1234-1234567890ab"
        val importDirectory = File(File(filesDir, "imports"), importId).apply { mkdirs() }
        File(importDirectory, "source.zip").writeText("zip")
        val cachedPreview = File(File(cacheDir, "zip-preview"), importId).apply { mkdirs() }
        File(cachedPreview, "index.html").writeText("<html></html>")
        repository.upsertImported(
            ImportedFile(
                id = importId,
                displayName = "drop.zip",
                fileType = FileType.ZIP,
                sourceKind = SourceKind.APP_PICKER,
                localFile = File(importDirectory, "source.zip"),
                sizeBytes = 3L
            )
        )
        val controller = RecentHistoryClearController(repository, filesDir, cacheDir)

        val result = controller.clearNotFavorite()

        assertEquals(1, result.clearedCount)
        assertEquals(1, result.cleanupScheduledCount)
        assertFalse(importDirectory.exists())
        assertFalse(cachedPreview.exists())
        assertTrue(repository.listRecent().isEmpty())
    }

    private fun controller(deleteImportAndCache: (String, File, File) -> List<File>): RecentHistoryClearController {
        return RecentHistoryClearController(repository, filesDir, cacheDir, deleteImportAndCache)
    }

    private fun upsert(id: String, favorite: Boolean = false) {
        now += 1_000L
        repository.upsertImported(
            ImportedFile(
                id = id,
                displayName = "$id.html",
                fileType = FileType.HTML,
                sourceKind = SourceKind.APP_PICKER,
                localFile = File(File(filesDir, "imports/$id"), "source.html"),
                sizeBytes = 12L
            )
        )
        if (favorite) repository.toggleFavorite(id)
    }
}
