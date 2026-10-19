package com.personalai.instantread.tasks

import com.personalai.instantread.history.ZipCacheClearResult
import com.personalai.instantread.history.ZipCacheClearStatus
import com.personalai.instantread.history.ZipPreviewCacheStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipCacheSettingsControllerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun loadsCacheStatsOnWorkerBeforeDeliveringCallback() {
        val cacheDir = temp.newFolder("stats-cache")
        val entry = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        File(entry, "index.html").writeText("1234")
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val controller = ZipCacheSettingsController(taskController, cacheDir)
        val completed = mutableListOf<ZipPreviewCacheStats>()

        controller.load(completed::add)

        assertEquals(1, worker.pending)
        assertEquals(0, callbacks.pending)
        worker.runNext()
        assertTrue(completed.isEmpty())
        callbacks.runAll()

        assertEquals(ZipPreviewCacheStats(1, 1, 4L), completed.single())
        taskController.close()
    }

    @Test
    fun clearsCacheOnWorkerAndReturnsPreClearStats() {
        val cacheDir = temp.newFolder("clear-cache")
        val entry = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        File(entry, "index.html").writeText("12345")
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val controller = ZipCacheSettingsController(
            taskController,
            cacheDir,
            scheduleDeletion = { it.deleteRecursively() }
        )
        val completed = mutableListOf<ZipCacheClearResult>()

        controller.clear(completed::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(ZipCacheClearResult(
            ZipPreviewCacheStats(1, 1, 5L),
            ZipCacheClearStatus.SCHEDULED
        ), completed.single())
        assertFalse(File(cacheDir, "zip-preview").exists())
        taskController.close()
    }

    @Test
    fun clearChecksWorkerCancellationBeforeMutatingCache() {
        val cacheDir = temp.newFolder("cancel-clear-cache")
        val entry = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        File(entry, "index.html").writeText("12345")
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(
            InlineExecutorService(beforeExecute = { Thread.currentThread().interrupt() }),
            callbacks
        )
        val controller = ZipCacheSettingsController(taskController, cacheDir)

        try {
            controller.clear(
                onCompleted = { error("Cancelled clear must not complete") },
                onFailure = { error("Cancelled clear must not report failure") }
            )
        } finally {
            Thread.interrupted()
        }

        assertTrue(File(cacheDir, "zip-preview").exists())
        assertEquals(0, callbacks.pending)
        taskController.close()
    }

    @Test
    fun clearReportsFailureInsteadOfSuccessStatsWhenDeletionCannotBePersisted() {
        val cacheDir = temp.newFolder("clear-failure")
        val entry = File(cacheDir, "zip-preview/import-1").apply { mkdirs() }
        File(entry, "index.html").writeText("cache")
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(InlineExecutorService(), callbacks)
        val controller = ZipCacheSettingsController(
            taskController,
            cacheDir,
            scheduleDeletion = { throw IllegalStateException("scripted deletion failure") }
        )
        val completed = mutableListOf<ZipCacheClearResult>()
        val failures = mutableListOf<Throwable>()

        controller.clear(
            onCompleted = completed::add,
            onFailure = failures::add
        )
        callbacks.runAll()

        assertTrue("success=$completed failure=$failures", completed.isEmpty())
        assertEquals("success=$completed failure=$failures", 1, failures.size)
        assertTrue(
            "The cache root must be detached before cleanup is queued.",
            !File(cacheDir, "zip-preview").exists()
        )
        taskController.close()
    }
}
