package com.personalai.instantread.tasks

import android.net.Uri
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.provider.OpenableColumns
import com.personalai.instantread.importing.FileImportController
import com.personalai.instantread.importing.FileImportRequest
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportError
import com.personalai.instantread.importing.ImportRequestFactory
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.ImportedFileStore
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.importing.UriImportRequestFactory
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.LimitedInputCopier
import com.personalai.instantread.io.FileTaskCancellationToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportWorkflowControllerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun reportsMetadataThenKnownImportProgressAndReturnsImportedFile() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var capturedInput: ImportWorkflowInput? = null
        val workflow = ImportWorkflowController(
            requestFactory = requestFactory { uri, sourceKind, mimeType, action, uriSource ->
                capturedInput = ImportWorkflowInput(uri, sourceKind, mimeType, action, uriSource)
                request("note.txt", "text/plain", sourceKind, "test".toByteArray(), 4L)
            },
            importController = importController("import-1", bufferSize = 2),
            taskController = taskController
        )
        val states = mutableListOf<ForegroundTaskState>()
        val results = mutableListOf<ImportedFile>()
        val failures = mutableListOf<ImportError>()
        val input = ImportWorkflowInput(
            uri = Uri.parse("content://example/note.txt"),
            sourceKind = SourceKind.ACTION_SEND,
            intentMimeType = "text/plain",
            entryAction = "android.intent.action.SEND",
            entryUriSource = "EXTRA_STREAM"
        )

        workflow.start(input, states::add, results::add, failures::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(input, capturedInput)
        assertEquals(
            listOf(
                ForegroundTaskStage.READING_METADATA,
                ForegroundTaskStage.IMPORTING_FILE,
                ForegroundTaskStage.IMPORTING_FILE,
                ForegroundTaskStage.IMPORTING_FILE
            ),
            states.map { it.stage }
        )
        assertEquals(listOf(null, 0, 50, 100), states.map { it.percent })
        assertNull(states.first().displayName)
        assertEquals(listOf("note.txt", "note.txt", "note.txt"), states.drop(1).map { it.displayName })
        assertEquals(listOf(4L, 4L, 4L), states.drop(1).map { it.totalBytes })
        assertEquals("import-1", results.single().id)
        assertTrue(failures.isEmpty())
        taskController.close()
    }

    @Test
    fun unknownTotalStaysIndeterminateAndDoesNotReportDuplicateCopyProgress() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val workflow = ImportWorkflowController(
            requestFactory { _, sourceKind, _, _, _ ->
                request("unknown.txt", "text/plain", sourceKind, "12345".toByteArray(), 0L)
            },
            importController("unknown-id", bufferSize = 2),
            taskController
        )
        val states = mutableListOf<ForegroundTaskState>()
        val results = mutableListOf<ImportedFile>()

        workflow.start(input(), states::add, results::add) { throw AssertionError(it) }
        worker.runNext()
        callbacks.runAll()

        assertEquals(
            listOf(ForegroundTaskStage.READING_METADATA, ForegroundTaskStage.IMPORTING_FILE),
            states.map { it.stage }
        )
        assertNull(states.last().totalBytes)
        assertNull(states.last().percent)
        assertEquals("unknown-id", results.single().id)
        taskController.close()
    }

    @Test
    fun preservesTypedOversizeFailure() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val limit = ImportResourcePolicy.DEFAULT.maxSourceBytes(FileType.TEXT)
        var opened = false
        val expected = ImportError.FileTooLarge(FileType.TEXT, limit + 1L, limit)
        val workflow = ImportWorkflowController(
            requestFactory { _, sourceKind, _, _, _ ->
                FileImportRequest(
                    displayName = "large.txt",
                    mimeType = "text/plain",
                    sourceKind = sourceKind,
                    openInputStream = {
                        opened = true
                        ByteArrayInputStream(byteArrayOf(1))
                    },
                    declaredSizeBytes = limit + 1L
                )
            },
            importController("oversize-id"),
            taskController
        )
        val failures = mutableListOf<ImportError>()

        workflow.start(input(), {}, { throw AssertionError(it) }, failures::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf(expected), failures)
        assertFalse(opened)
        taskController.close()
    }

    @Test
    fun preservesUnsupportedFileTypeFailure() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val workflow = ImportWorkflowController(
            requestFactory { _, sourceKind, _, _, _ ->
                request("photo.png", "image/png", sourceKind, byteArrayOf(1), 1L)
            },
            importController("unsupported-id"),
            taskController
        )
        val failures = mutableListOf<ImportError>()

        workflow.start(input(), {}, { throw AssertionError(it) }, failures::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(
            listOf(ImportError.UnsupportedFileType("photo.png", "image/png")),
            failures
        )
        taskController.close()
    }

    @Test
    fun mapsUnexpectedRequestFactoryFailureToCopyFailed() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val workflow = ImportWorkflowController(
            requestFactory { _, _, _, _, _ -> throw IllegalStateException("metadata boom") },
            importController("factory-failure-id"),
            taskController
        )
        val failures = mutableListOf<ImportError>()

        workflow.start(input(), {}, { throw AssertionError(it) }, failures::add)
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf(ImportError.CopyFailed("metadata boom")), failures)
        taskController.close()
    }

    @Test
    fun cancellingBeforeWorkerExecutionProducesNoWorkOrCompletion() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var factoryCalled = false
        val workflow = ImportWorkflowController(
            requestFactory { _, sourceKind, _, _, _ ->
                factoryCalled = true
                request("note.txt", "text/plain", sourceKind, byteArrayOf(1), 1L)
            },
            importController("cancel-before-id"),
            taskController
        )
        val events = mutableListOf<String>()

        workflow.start(input(), { events += "progress" }, { events += "success" }, { events += "failure" })
        taskController.cancelCurrent()
        worker.runAll()
        callbacks.runAll()

        assertFalse(factoryCalled)
        assertTrue(events.isEmpty())
        taskController.close()
    }

    @Test
    fun cancellationAfterMetadataReportProducesNoCompletion() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        var factoryCalled = false
        val workflow = ImportWorkflowController(
            requestFactory { _, sourceKind, _, _, _ ->
                factoryCalled = true
                taskController.cancelCurrent()
                request("note.txt", "text/plain", sourceKind, byteArrayOf(1), 1L)
            },
            importController("cancel-metadata-id"),
            taskController
        )
        val events = mutableListOf<String>()

        workflow.start(input(), { events += "progress" }, { events += "success" }, { events += "failure" })
        worker.runNext()
        callbacks.runAll()

        assertTrue(factoryCalled)
        assertTrue(events.isEmpty())
        taskController.close()
    }

    @Test
    fun startingNewImportSuppressesQueuedCompletionFromOldImport() {
        val worker = ManualExecutorService()
        val callbacks = QueuedCallbackDispatcher()
        val taskController = ForegroundFileTaskController(worker, callbacks)
        val ids = ArrayDeque(listOf("first-id", "second-id"))
        val workflow = ImportWorkflowController(
            requestFactory { uri, sourceKind, _, _, _ ->
                request(uri.lastPathSegment, "text/plain", sourceKind, byteArrayOf(1), 1L)
            },
            importController(idGenerator = { ids.removeFirst() }),
            taskController
        )
        val results = mutableListOf<String>()
        val failures = mutableListOf<ImportError>()

        workflow.start(input("first.txt"), {}, { results += it.id }, failures::add)
        worker.runNext()
        val firstDirectory = File(temp.root, "imports/first-id")
        val secondDirectory = File(temp.root, "imports/second-id")
        assertTrue(firstDirectory.exists())
        workflow.start(input("second.txt"), {}, { results += it.id }, failures::add)
        assertFalse(firstDirectory.exists())
        worker.runNext()
        callbacks.runAll()

        assertEquals(listOf("second-id"), results)
        assertTrue(failures.isEmpty())
        assertFalse(firstDirectory.exists())
        assertTrue(secondDirectory.exists())
        taskController.close()
    }

    @Test
    fun cancellationSignalUnblocksMetadataQuerySoReplacementCanRun() {
        val authority = "com.personalai.instantread.test.blocking-metadata"
        val provider = BlockingCancellationProvider()
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val taskController = ForegroundFileTaskController(
            callbackDispatcher = FileTaskCallbackDispatcher { block -> block() }
        )
        val workflow = ImportWorkflowController(
            UriImportRequestFactory(RuntimeEnvironment.getApplication().contentResolver),
            importController("unused-id"),
            taskController
        )
        val firstCompletions = mutableListOf<String>()
        val secondCompleted = CountDownLatch(1)
        val secondFailures = mutableListOf<ImportError>()

        try {
            workflow.start(
                ImportWorkflowInput(Uri.parse("content://$authority/first"), SourceKind.APP_PICKER),
                {},
                { firstCompletions += "success" },
                { firstCompletions += "failure" }
            )
            assertTrue(
                "The provider should enter the cancellable query overload.",
                provider.queryStarted.await(5, TimeUnit.SECONDS)
            )

            workflow.start(
                ImportWorkflowInput(Uri.parse("content://$authority/second"), SourceKind.APP_PICKER),
                {},
                { throw AssertionError(it) },
                {
                    secondFailures += it
                    secondCompleted.countDown()
                }
            )

            assertTrue(provider.cancellationObserved.await(5, TimeUnit.SECONDS))
            assertTrue("The replacement task should execute on the same worker.", secondCompleted.await(5, TimeUnit.SECONDS))
            assertTrue(firstCompletions.isEmpty())
            assertTrue(secondFailures.single() is ImportError.UnsupportedFileType)
            assertEquals(2, provider.cancellableQueryCalls.get())
            assertEquals(0, provider.legacyQueryCalls.get())
        } finally {
            taskController.close()
        }
    }

    private fun requestFactory(
        create: (Uri, SourceKind, String?, String?, String?) -> FileImportRequest
    ): ImportRequestFactory = object : ImportRequestFactory {
        override fun fromUri(
            uri: Uri,
            sourceKind: SourceKind,
            intentMimeType: String?,
            entryAction: String?,
            entryUriSource: String?,
            cancellationToken: FileTaskCancellationToken
        ): FileImportRequest = create(uri, sourceKind, intentMimeType, entryAction, entryUriSource)
    }

    private fun request(
        displayName: String?,
        mimeType: String?,
        sourceKind: SourceKind,
        bytes: ByteArray,
        declaredSizeBytes: Long?
    ) = FileImportRequest(
        displayName = displayName,
        mimeType = mimeType,
        sourceKind = sourceKind,
        openInputStream = { ByteArrayInputStream(bytes) },
        declaredSizeBytes = declaredSizeBytes
    )

    private fun importController(
        id: String,
        bufferSize: Int = DEFAULT_TEST_BUFFER_SIZE
    ): FileImportController = importController({ id }, bufferSize)

    private fun importController(
        idGenerator: () -> String,
        bufferSize: Int = DEFAULT_TEST_BUFFER_SIZE
    ): FileImportController = FileImportController(
        ImportedFileStore(
            filesDir = temp.root,
            copier = LimitedInputCopier(bufferSize),
            idGenerator = idGenerator
        )
    )

    private fun input(name: String = "note.txt") = ImportWorkflowInput(
        Uri.parse("content://example/$name"),
        SourceKind.APP_PICKER
    )

    private companion object {
        const val DEFAULT_TEST_BUFFER_SIZE = 32 * 1024
    }

    private class BlockingCancellationProvider : ContentProvider() {
        val queryStarted = CountDownLatch(1)
        val cancellationObserved = CountDownLatch(1)
        val cancellableQueryCalls = AtomicInteger(0)
        val legacyQueryCalls = AtomicInteger(0)

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            legacyQueryCalls.incrementAndGet()
            return metadataCursor()
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
            cancellationSignal: CancellationSignal?
        ): Cursor {
            val invocation = cancellableQueryCalls.incrementAndGet()
            if (invocation == 1) {
                val signal = requireNotNull(cancellationSignal)
                signal.setOnCancelListener { cancellationObserved.countDown() }
                queryStarted.countDown()
                awaitIgnoringInterrupt(cancellationObserved)
                signal.throwIfCanceled()
            }
            return metadataCursor()
        }

        override fun getType(uri: Uri): String = "image/png"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

        private fun metadataCursor(): Cursor = MatrixCursor(
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        ).apply {
            addRow(arrayOf("photo.png", 1L))
        }

        private fun awaitIgnoringInterrupt(latch: CountDownLatch) {
            while (true) {
                try {
                    latch.await()
                    return
                } catch (_: InterruptedException) {
                    // This provider deliberately relies on CancellationSignal, not thread interruption.
                }
            }
        }
    }
}
