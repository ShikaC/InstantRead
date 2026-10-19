package com.personalai.instantread.history

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.ZipPreviewCacheCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportCleanupTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun deleteImportAndCacheRemovesOnlyMatchingImportId() {
        val importDir = File(temp.root, "files/imports/import-1").apply { mkdirs() }
        File(importDir, "source.html").writeText("html")
        val otherImport = File(temp.root, "files/imports/import-2").apply { mkdirs() }
        File(otherImport, "source.html").writeText("other")
        val zipCache = File(temp.root, "cache/zip-preview/import-1").apply { mkdirs() }
        File(zipCache, "index.html").writeText("zip")
        val partialCache = File(temp.root, "cache/zip-preview/import-1.partial-run-1").apply { mkdirs() }
        File(partialCache, "index.html").writeText("partial")
        val previousCache = File(temp.root, "cache/zip-preview/import-1.previous-run-1").apply { mkdirs() }
        File(previousCache, "index.html").writeText("previous")
        val otherCache = File(temp.root, "cache/zip-preview/import-2").apply { mkdirs() }
        File(otherCache, "index.html").writeText("other")
        val similarIdCache = File(temp.root, "cache/zip-preview/import-10.partial-run-1").apply { mkdirs() }
        File(similarIdCache, "index.html").writeText("similar")

        ImportCleanup.deleteImportAndCache(
            importId = "import-1",
            filesDir = File(temp.root, "files"),
            cacheDir = File(temp.root, "cache"),
            scheduleDeletion = { it.deleteRecursively() }
        )

        assertFalse(importDir.exists())
        assertFalse(zipCache.exists())
        assertFalse(partialCache.exists())
        assertFalse(previousCache.exists())
        assertTrue(otherImport.exists())
        assertTrue(otherCache.exists())
        assertTrue(similarIdCache.exists())
    }

    @Test
    fun deleteImportAndCacheDetachesOwnedPathsBeforeQueuedRecursiveDeletion() {
        val importDir = File(temp.root, "files-async/imports/import-async").apply { mkdirs() }
        File(importDir, "source.zip").writeText("source")
        val zipCache = File(temp.root, "cache-async/zip-preview/import-async").apply { mkdirs() }
        File(zipCache, "index.html").writeText("cache")
        val previous = File(
            temp.root,
            "cache-async/zip-preview/import-async.previous-run"
        ).apply {
            mkdirs()
            File(this, "index.html").writeText("previous")
        }
        val scheduled = mutableListOf<File>()

        ImportCleanup.deleteImportAndCache(
            importId = "import-async",
            filesDir = File(temp.root, "files-async"),
            cacheDir = File(temp.root, "cache-async"),
            scheduleDeletion = { scheduled += it }
        )

        assertFalse(importDir.exists())
        assertFalse(zipCache.exists())
        assertFalse(previous.exists())
        assertEquals(3, scheduled.size)
        assertTrue(scheduled.all(File::exists))
        scheduled.forEach { it.deleteRecursively() }
    }

    @Test
    fun deleteImportAndCacheRejectsUnsafeImportIdsBeforeTouchingFiles() {
        val filesDir = File(temp.root, "unsafe-files")
        val cacheDir = File(temp.root, "unsafe-cache")
        val victim = File(temp.root, "outside.txt").apply { writeText("keep") }
        val scheduled = mutableListOf<File>()

        assertThrows(java.io.IOException::class.java) {
            ImportCleanup.deleteImportAndCache(
                importId = "../outside.txt",
                filesDir = filesDir,
                cacheDir = cacheDir,
                scheduleDeletion = { scheduled += it }
            )
        }

        assertTrue(victim.exists())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun deleteImportAndCacheRejectsIdsBeyondPolicyLengthBeforeTouchingFiles() {
        val filesDir = File(temp.root, "long-files")
        val cacheDir = File(temp.root, "long-cache")
        val scheduled = mutableListOf<File>()

        assertThrows(java.io.IOException::class.java) {
            ImportCleanup.deleteImportAndCache(
                importId = "a".repeat(ImportResourcePolicy.DEFAULT.maxImportIdLength + 1),
                filesDir = filesDir,
                cacheDir = cacheDir,
                scheduleDeletion = { scheduled += it }
            )
        }

        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun clearZipPreviewCacheKeepsSourceImports() {
        val sourceImport = File(temp.root, "files/imports/import-1").apply { mkdirs() }
        File(sourceImport, "source.zip").writeText("zip")
        val zipCache = File(temp.root, "cache/zip-preview/import-1").apply { mkdirs() }
        File(zipCache, "index.html").writeText("html")

        ImportCleanup.clearZipPreviewCache(cacheDir = File(temp.root, "cache"))

        assertTrue(sourceImport.exists())
        assertFalse(File(temp.root, "cache/zip-preview").exists())
    }

    @Test
    fun zipPreviewCacheStatsCountsImportDirectoriesFilesAndBytes() {
        val first = File(temp.root, "cache/zip-preview/import-1/assets").apply { mkdirs() }
        File(first, "style.css").writeText("abcd")
        val second = File(temp.root, "cache/zip-preview/import-2").apply { mkdirs() }
        File(second, "index.html").writeText("123456")

        val stats = ImportCleanup.zipPreviewCacheStats(cacheDir = File(temp.root, "cache"))

        assertEquals(2, stats.importDirectories)
        assertEquals(2, stats.files)
        assertEquals(10L, stats.bytes)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun clearZipPreviewCacheReturnsClearedStats() {
        val first = File(temp.root, "cache/zip-preview/import-1").apply { mkdirs() }
        File(first, "index.html").writeText("12345")
        val second = File(temp.root, "cache/zip-preview/import-2").apply { mkdirs() }
        File(second, "main.css").writeText("abc")

        val result = ImportCleanup.clearZipPreviewCache(cacheDir = File(temp.root, "cache"))

        assertEquals(2, result.stats.importDirectories)
        assertEquals(2, result.stats.files)
        assertEquals(8L, result.stats.bytes)
        assertEquals(ZipCacheClearStatus.SCHEDULED, result.status)
        assertFalse(File(temp.root, "cache/zip-preview").exists())
    }

    @Test
    fun clearZipPreviewCacheDetachesRootBeforeQueuedDeletionAndUsesKnownStats() {
        val root = File(temp.root, "cache-detach/zip-preview/import-1").apply { mkdirs() }
        File(root, "index.html").writeText("content")
        val knownStats = ZipPreviewCacheStats(importDirectories = 7, files = 8, bytes = 9L)
        val scheduled = mutableListOf<File>()

        val result = ImportCleanup.clearZipPreviewCache(
            cacheDir = File(temp.root, "cache-detach"),
            knownStats = knownStats,
            scheduleDeletion = { scheduled += it }
        )

        assertEquals(knownStats, result.stats)
        assertEquals(ZipCacheClearStatus.SCHEDULED, result.status)
        assertFalse(File(temp.root, "cache-detach/zip-preview").exists())
        assertEquals(1, scheduled.size)
        assertTrue(scheduled.single().exists())
        scheduled.single().deleteRecursively()
    }

    @Test
    fun schedulesCrashLeftoversForBackgroundCleanupOnStartup() {
        val filesDir = File(temp.root, "startup-files")
        val cacheDir = File(temp.root, "startup-cache")
        val staleImport = File(filesDir, "imports/import-1.deleted-old").apply { mkdirs() }
        val staleCacheEntry = File(cacheDir, "zip-preview/import-1.discarded-old").apply { mkdirs() }
        val staleCacheRoot = File(cacheDir, "zip-preview.deleted-old").apply { mkdirs() }
        val scheduled = mutableListOf<File>()

        ImportCleanup.scheduleDetachedCleanup(filesDir, cacheDir) { scheduled += it }

        assertEquals(
            setOf(staleImport.canonicalFile, staleCacheEntry.canonicalFile, staleCacheRoot.canonicalFile),
            scheduled.map(File::getCanonicalFile).toSet()
        )
        scheduled.forEach { it.deleteRecursively() }
    }

    @Test
    fun startupCleanupQueuesOrphanedOfficialPathsButKeepsActiveImports() {
        val filesDir = File(temp.root, "orphan-files")
        val cacheDir = File(temp.root, "orphan-cache")
        val orphanImport = File(filesDir, "imports/orphan-id").apply { mkdirs() }
        val activeImport = File(filesDir, "imports/active-id").apply { mkdirs() }
        val orphanCache = File(cacheDir, "zip-preview/orphan-id").apply { mkdirs() }
        val activeCache = File(cacheDir, "zip-preview/active-id").apply { mkdirs() }
        val scheduled = mutableListOf<File>()

        ImportCleanup.scheduleDetachedCleanup(
            filesDir = filesDir,
            cacheDir = cacheDir,
            activeImportIds = setOf("active-id"),
            scheduleDeletion = { scheduled += it }
        )

        assertEquals(
            setOf(orphanImport.canonicalFile, orphanCache.canonicalFile),
            scheduled.map(File::getCanonicalFile).toSet()
        )
        assertTrue(activeImport.exists())
        assertTrue(activeCache.exists())
        scheduled.forEach { it.deleteRecursively() }
    }

    @Test
    fun clearRootRenameFailurePersistsDeletionIntentAcrossStartup() {
        val cacheDir = File(temp.root, "clear-rename-failure")
        val root = File(cacheDir, "zip-preview/active-id").apply { mkdirs() }
        File(root, "index.html").writeText("cache")
        val firstFinalizations = mutableListOf<() -> Unit>()

        ImportCleanup.clearZipPreviewCache(
            cacheDir = cacheDir,
            renamePath = { _, _ -> false },
            deleteDirectory = { false },
            scheduleFinalization = { firstFinalizations += it }
        )

        val marker = cacheDir.listFiles().orEmpty().single {
            it.name.startsWith("zip-preview.delete-pending-")
        }
        assertTrue(File(cacheDir, "zip-preview").exists())
        assertEquals(1, firstFinalizations.size)
        firstFinalizations.single().invoke()
        assertTrue(File(cacheDir, "zip-preview").exists())
        assertTrue(marker.exists())
        assertThrows(FileTaskCancelledException::class.java) {
            ZipPreviewCacheCoordinator.awaitPendingFinalization(
                File(cacheDir, "zip-preview"),
                "active-id"
            )
        }

        val startupFinalizations = mutableListOf<() -> Unit>()
        ImportCleanup.scheduleDetachedCleanup(
            filesDir = File(temp.root, "files"),
            cacheDir = cacheDir,
            activeImportIds = setOf("active-id"),
            scheduleFinalization = { startupFinalizations += it }
        )
        assertTrue(marker.exists())
        assertEquals(1, startupFinalizations.size)
        startupFinalizations.single().invoke()
        assertFalse(File(cacheDir, "zip-preview").exists())
        assertFalse(marker.exists())
        ZipPreviewCacheCoordinator.awaitPendingFinalization(
            File(cacheDir, "zip-preview"),
            "active-id"
        )
    }

    @Test
    fun clearConsumesPersistedMarkerWhenRootIsAlreadyMissing() {
        val cacheDir = File(temp.root, "clear-missing-root").apply { mkdirs() }
        val marker = File(cacheDir, "zip-preview.delete-pending-old").apply { mkdirs() }

        ImportCleanup.clearZipPreviewCache(cacheDir)

        assertFalse(marker.exists())
        ZipPreviewCacheCoordinator.awaitPendingFinalization(
            File(cacheDir, "zip-preview"),
            "active-id"
        )
    }

    @Test
    fun clearConsumesPersistedMarkerAfterRootRenameSucceeds() {
        val cacheDir = File(temp.root, "clear-renamed-root")
        File(cacheDir, "zip-preview/active-id").apply { mkdirs() }
        val marker = File(cacheDir, "zip-preview.delete-pending-old").apply { mkdirs() }
        val scheduled = mutableListOf<File>()

        ImportCleanup.clearZipPreviewCache(
            cacheDir = cacheDir,
            scheduleDeletion = { scheduled += it }
        )

        assertFalse(marker.exists())
        assertEquals(1, scheduled.size)
        ZipPreviewCacheCoordinator.awaitPendingFinalization(
            File(cacheDir, "zip-preview"),
            "active-id"
        )
        scheduled.forEach(File::deleteRecursively)
    }

    @Test
    fun persistedMarkerDeletionIsRetriedAndRemainsBlockingWhenFilesystemRefuses() {
        val cacheDir = File(temp.root, "clear-marker-refused").apply { mkdirs() }
        val marker = File(cacheDir, "zip-preview.delete-pending-old").apply { mkdirs() }
        val finalizations = mutableListOf<() -> Unit>()
        var deleteCalls = 0

        ImportCleanup.clearZipPreviewCache(
            cacheDir = cacheDir,
            deleteDirectory = {
                deleteCalls += 1
                false
            },
            scheduleFinalization = { finalizations += it }
        )

        assertEquals(3, deleteCalls)
        assertEquals(1, finalizations.size)
        finalizations.single().invoke()
        assertEquals(6, deleteCalls)
        assertTrue(marker.exists())
        assertThrows(FileTaskCancelledException::class.java) {
            ZipPreviewCacheCoordinator.awaitPendingFinalization(
                File(cacheDir, "zip-preview"),
                "active-id"
            )
        }
    }

    @Test
    fun cacheStatsTraversalChecksCancellationBetweenFiles() {
        val cacheDir = File(temp.root, "cancel-stats")
        val root = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        repeat(5) { index -> File(root, "$index.txt").writeText("content") }
        val expected = FileTaskCancelledException()
        var checks = 0
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == 5) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            ImportCleanup.zipPreviewCacheStats(cacheDir, token)
        }

        assertSame(expected, actual)
        assertEquals(5, checks)
    }

    @Test
    fun cacheStatsTraversalChecksCancellationBetweenDirectories() {
        val cacheDir = File(temp.root, "cancel-directory-stats")
        val root = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        repeat(5) { index -> File(root, "directory-$index").mkdirs() }
        val expected = FileTaskCancelledException()
        var checks = 0
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == 3) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            ImportCleanup.zipPreviewCacheStats(cacheDir, token)
        }

        assertSame(expected, actual)
        assertEquals(3, checks)
    }
}
