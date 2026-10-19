package com.personalai.instantread.tasks

import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.preview.DecodedText
import com.personalai.instantread.preview.LocalTextEncoding
import com.personalai.instantread.preview.PreviewController
import com.personalai.instantread.preview.PreviewModel
import com.personalai.instantread.preview.PreviewOptions
import com.personalai.instantread.preview.TextDecoder
import com.personalai.instantread.preview.ZipWebPackageRenderer
import com.personalai.instantread.preview.ZipPreviewRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PreviewPreparationControllerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun initialStateMatchesTheFirstWorkerStageForGeneratedPreviews() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val controller = PreviewPreparationController(
            taskController,
            previewController { importedFile, _, _ -> PreviewModel.WebFile(importedFile.localFile, "unused") }
        )
        val markdownState = controller.initialState(
            textHistory("readme.md", "markdown-initial", FileType.MARKDOWN)
        )
        assertEquals(ForegroundTaskStage.CHECKING_TEXT_ENCODING, markdownState.stage)
        assertEquals("readme.md", markdownState.displayName)
        assertEquals(
            ForegroundTaskStage.CHECKING_TEXT_ENCODING,
            controller.initialState(textHistory("notes.txt", "text-initial", FileType.TEXT)).stage
        )
        assertEquals(
            ForegroundTaskStage.EXTRACTING_ZIP,
            controller.initialState(zipHistory("site.zip", "zip-initial")).stage
        )
        val htmlFailure = runCatching {
            controller.initialState(htmlHistory("page.html", "html-initial"))
        }.exceptionOrNull()
        assertTrue(htmlFailure is IllegalArgumentException)
        taskController.close()
    }

    @Test
    fun markdownReportsDecodeThenRenderStages() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var receivedToken: FileTaskCancellationToken? = null
        val controller = PreviewPreparationController(
            taskController,
            PreviewController(
                textDecoder = object : TextDecoder {
                    override fun decode(
                        file: java.io.File,
                        fileType: FileType,
                        cancellationToken: FileTaskCancellationToken
                    ): DecodedText {
                        receivedToken = cancellationToken
                        return DecodedText("# Title", LocalTextEncoding.UTF8)
                    }
                }
            )
        )
        val history = textHistory("stages.md", "markdown-id", FileType.MARKDOWN)
        val states = mutableListOf<ForegroundTaskState>()
        val completed = mutableListOf<PreviewModel>()

        controller.start(history, PreviewOptions(), states::add, completed::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(
            listOf(
                ForegroundTaskStage.CHECKING_TEXT_ENCODING,
                ForegroundTaskStage.GENERATING_READING_PAGE
            ),
            states.map { it.stage }
        )
        assertEquals(listOf("stages.md", "stages.md"), states.map { it.displayName })
        assertNotSame(FileTaskCancellationToken.NONE, receivedToken)
        assertTrue(completed.single() is PreviewModel.WebHtml)
        taskController.close()
    }

    @Test
    fun reportsZipStagePassesWorkerTokenAndReturnsWebFile() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var receivedFile: ImportedFile? = null
        var receivedOptions: PreviewOptions? = null
        var receivedToken: FileTaskCancellationToken? = null
        val options = PreviewOptions(javaScriptEnabledForHtml = true)
        val controller = PreviewPreparationController(
            taskController,
            previewController { importedFile, actualOptions, token ->
                receivedFile = importedFile
                receivedOptions = actualOptions
                receivedToken = token
                PreviewModel.WebFile(importedFile.localFile, "prepared-site", true)
            }
        )
        val history = zipHistory("site.zip", "zip-id")
        val states = mutableListOf<ForegroundTaskState>()
        val completed = mutableListOf<PreviewModel>()

        controller.start(history, options, states::add, completed::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf(ForegroundTaskStage.EXTRACTING_ZIP), states.map { it.stage })
        assertEquals("site.zip", states.single().displayName)
        assertEquals(history.id, receivedFile?.id)
        assertEquals(history.localFile, receivedFile?.localFile)
        assertSame(options, receivedOptions)
        assertNotSame(FileTaskCancellationToken.NONE, receivedToken)
        assertEquals("prepared-site", (completed.single() as PreviewModel.WebFile).title)
        taskController.close()
    }

    @Test
    fun preservesPreviewModelErrorAsCompletedResult() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val expected = PreviewModel.Error("ZIP 不可用", "资源超出安全上限")
        val controller = PreviewPreparationController(
            taskController,
            previewController { _, _, _ -> expected }
        )
        val completed = mutableListOf<PreviewModel>()

        controller.start(zipHistory("error.zip", "error-id"), PreviewOptions(), {}, completed::add)
        worker.runNext()
        callbacks.runAll()

        assertSame(expected, completed.single())
        taskController.close()
    }

    @Test
    fun mapsUnexpectedThrowableToStablePreviewError() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val controller = PreviewPreparationController(
            taskController,
            previewController { _, _, _ -> throw IllegalStateException("boom") }
        )
        val completed = mutableListOf<PreviewModel>()

        controller.start(zipHistory("broken.zip", "broken-id"), PreviewOptions(), {}, completed::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(
            PreviewModel.Error(
                title = "预览准备失败",
                message = "无法完成本地预览准备。请返回首页后重新打开文件。"
            ),
            completed.single()
        )
        taskController.close()
    }

    @Test
    fun cancellingBeforeWorkerExecutionProducesNoRenderOrCompletion() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var rendered = false
        val controller = PreviewPreparationController(
            taskController,
            previewController { importedFile, _, _ ->
                rendered = true
                PreviewModel.WebFile(importedFile.localFile, "unexpected")
            }
        )
        val events = mutableListOf<String>()

        controller.start(
            zipHistory("cancel.zip", "cancel-id"),
            PreviewOptions(),
            { events += "progress" },
            { events += "completed" }
        )
        taskController.cancelCurrent()
        worker.runAll()
        callbacks.runAll()

        assertFalse(rendered)
        assertTrue(events.isEmpty())
        taskController.close()
    }

    @Test
    fun startingNewPreparationSuppressesQueuedCompletionFromOldTask() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val controller = PreviewPreparationController(
            taskController,
            previewController { importedFile, _, _ ->
                PreviewModel.WebFile(importedFile.localFile, importedFile.displayName.orEmpty())
            }
        )
        val completed = mutableListOf<String>()

        controller.start(zipHistory("first.zip", "first-id"), PreviewOptions(), {}) {
            completed += (it as PreviewModel.WebFile).title
        }
        worker.runNext()
        controller.start(zipHistory("second.zip", "second-id"), PreviewOptions(), {}) {
            completed += (it as PreviewModel.WebFile).title
        }
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf("second.zip"), completed)
        taskController.close()
    }

    @Test
    fun cancellingQueuedZipCompletionRollsBackPreparedCache() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val cacheRoot = temp.newFolder("queued-cancel-cache")
        File(cacheRoot, "queued-cancel-id").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val source = temp.newFile("queued-cancel.zip").apply {
            ZipOutputStream(FileOutputStream(this)).use { zip ->
                zip.putNextEntry(ZipEntry("index.html"))
                zip.write("new".toByteArray())
                zip.closeEntry()
            }
        }
        val controller = PreviewPreparationController(
            taskController,
            PreviewController(
                zipRenderer = ZipWebPackageRenderer(
                    cacheRoot = cacheRoot,
                    stagingIdGenerator = { "queued-cancel-run" }
                )
            )
        )
        val completed = mutableListOf<PreviewModel>()

        controller.start(
            history("queued-cancel.zip", "queued-cancel-id", FileType.ZIP, source),
            PreviewOptions(),
            {},
            completed::add
        )
        worker.runNext()

        assertEquals("new", File(cacheRoot, "queued-cancel-id/index.html").readText())
        assertTrue(File(cacheRoot, "queued-cancel-id.previous-queued-cancel-run").exists())

        taskController.cancelCurrent()
        callbacks.runAll()

        assertEquals("old", File(cacheRoot, "queued-cancel-id/index.html").readText())
        assertFalse(File(cacheRoot, "queued-cancel-id.previous-queued-cancel-run").exists())
        assertTrue(completed.isEmpty())
        taskController.close()
    }

    @Test
    fun publishingQueuedZipCompletionSchedulesRecursiveBackupDeletionOffCallbackThread() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val cacheRoot = temp.newFolder("queued-publish-cache")
        File(cacheRoot, "queued-publish-id").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val source = temp.newFile("queued-publish.zip").apply {
            ZipOutputStream(FileOutputStream(this)).use { zip ->
                zip.putNextEntry(ZipEntry("index.html"))
                zip.write("new".toByteArray())
                zip.closeEntry()
            }
        }
        val callbackThread = Thread.currentThread()
        var recursiveDeleteOnCallbackThread = false
        val scheduled = mutableListOf<File>()
        val controller = PreviewPreparationController(
            taskController,
            PreviewController(
                zipRenderer = ZipWebPackageRenderer(
                    cacheRoot = cacheRoot,
                    stagingIdGenerator = { "queued-publish-run" },
                    deleteDirectory = { directory ->
                        if (Thread.currentThread() === callbackThread) {
                            recursiveDeleteOnCallbackThread = true
                        }
                        directory.deleteRecursively()
                    },
                    scheduleDeletion = { scheduled += it }
                )
            )
        )

        controller.start(
            history("queued-publish.zip", "queued-publish-id", FileType.ZIP, source),
            PreviewOptions(),
            {},
            {}
        )
        worker.runNext()
        callbacks.runAll()

        assertFalse(recursiveDeleteOnCallbackThread)
        assertEquals(1, scheduled.size)
        assertFalse(File(cacheRoot, "queued-publish-id.previous-queued-publish-run").exists())
        scheduled.forEach { it.deleteRecursively() }
        taskController.close()
    }

    private fun previewController(
        render: (ImportedFile, PreviewOptions, FileTaskCancellationToken) -> PreviewModel
    ) = PreviewController(
        zipRenderer = object : ZipPreviewRenderer {
            override fun render(
                importedFile: ImportedFile,
                options: PreviewOptions,
                cancellationToken: FileTaskCancellationToken
            ): PreviewModel = render(importedFile, options, cancellationToken)
        }
    )

    private fun zipHistory(name: String, id: String): PreviewHistory {
        val file = temp.newFile("$id.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        return history(name, id, FileType.ZIP, file)
    }

    private fun textHistory(name: String, id: String, fileType: FileType): PreviewHistory {
        val extension = if (fileType == FileType.MARKDOWN) "md" else "txt"
        val file = temp.newFile("$id.$extension").apply { writeText("ignored") }
        return history(name, id, fileType, file)
    }

    private fun htmlHistory(name: String, id: String): PreviewHistory {
        val file = temp.newFile("$id.html").apply { writeText("<p>ignored</p>") }
        return history(name, id, FileType.HTML, file)
    }

    private fun history(
        name: String,
        id: String,
        fileType: FileType,
        file: java.io.File
    ): PreviewHistory {
        return PreviewHistory(
            id = id,
            displayName = name,
            fileType = fileType,
            sourceKind = SourceKind.APP_PICKER,
            localFile = file,
            createdAt = 1L,
            lastOpenedAt = 1L,
            favorite = false,
            jsAllowed = false
        )
    }
}
