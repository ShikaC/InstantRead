package com.personalai.instantread.importing

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.LimitedInputCopier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class FileImportControllerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun importsSupportedFileUsingDetectedExtension() {
        val controller = controllerWithId("html-id")

        val result = controller.importFile(
            FileImportRequest(
                displayName = "report.html",
                mimeType = "application/octet-stream",
                sourceKind = SourceKind.ACTION_VIEW,
                openInputStream = { ByteArrayInputStream("<html></html>".toByteArray()) }
            )
        )

        val imported = (result as FileImportResult.Success).importedFile
        assertEquals("html-id", imported.id)
        assertEquals(FileType.HTML, imported.fileType)
        assertEquals(SourceKind.ACTION_VIEW, imported.sourceKind)
        assertEquals("source.html", imported.localFile.name)
    }

    @Test
    fun importsSupportedFileUsingMimeWhenNameIsMissing() {
        val controller = controllerWithId("txt-id")

        val result = controller.importFile(
            FileImportRequest(
                displayName = null,
                mimeType = "text/plain",
                sourceKind = SourceKind.ACTION_SEND,
                openInputStream = { ByteArrayInputStream("hello".toByteArray()) }
            )
        )

        val imported = (result as FileImportResult.Success).importedFile
        assertEquals(FileType.TEXT, imported.fileType)
        assertEquals("source.txt", imported.localFile.name)
    }

    @Test
    fun returnsUnsupportedFileTypeFailure() {
        val result = controllerWithId("unused").importFile(
            FileImportRequest(
                displayName = "photo.png",
                mimeType = "image/png",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = { ByteArrayInputStream(byteArrayOf(1)) }
            )
        )

        assertTrue(result is FileImportResult.Failure)
        assertTrue((result as FileImportResult.Failure).error is ImportError.UnsupportedFileType)
    }

    @Test
    fun returnsCannotReadSourceWhenInputStreamIsMissing() {
        val result = controllerWithId("missing").importFile(
            FileImportRequest(
                displayName = "notes.md",
                mimeType = "text/markdown",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = { null }
            )
        )

        assertEquals(ImportError.CannotReadSource, (result as FileImportResult.Failure).error)
    }

    @Test
    fun returnsEmptyFileFailure() {
        val result = controllerWithId("empty").importFile(
            FileImportRequest(
                displayName = "empty.txt",
                mimeType = "text/plain",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = { ByteArrayInputStream(ByteArray(0)) }
            )
        )

        assertEquals(ImportError.EmptyFile, (result as FileImportResult.Failure).error)
    }

    @Test
    fun returnsCopyFailedWhenSourceThrows() {
        val result = controllerWithId("broken").importFile(
            FileImportRequest(
                displayName = "broken.zip",
                mimeType = "application/zip",
                sourceKind = SourceKind.ACTION_SEND,
                openInputStream = { throw IOException("boom") }
            )
        )

        val error = (result as FileImportResult.Failure).error
        assertTrue(error is ImportError.CopyFailed)
        assertTrue((error as ImportError.CopyFailed).message.contains("boom"))
    }

    @Test
    fun rejectsDeclaredOversizeBeforeCancellationCheckOrOpeningStream() {
        var opened = false
        val controller = controllerWithId(
            id = "declared-oversized",
            policy = ImportResourcePolicy(textMaxBytes = 4L)
        )
        val result = controller.importFile(
            request = FileImportRequest(
                displayName = "large.txt",
                mimeType = "text/plain",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = {
                    opened = true
                    ByteArrayInputStream("12345".toByteArray())
                },
                declaredSizeBytes = 5L
            ),
            cancellationToken = FileTaskCancellationToken { throw FileTaskCancelledException() }
        )

        assertEquals(
            ImportError.FileTooLarge(FileType.TEXT, actualBytes = 5L, limitBytes = 4L),
            (result as FileImportResult.Failure).error
        )
        assertFalse(opened)
    }

    @Test
    fun allowsDeclaredSizeEqualToConfiguredLimit() {
        val controller = controllerWithId(
            id = "at-limit",
            policy = ImportResourcePolicy(textMaxBytes = 4L)
        )

        val result = controller.importFile(
            FileImportRequest(
                displayName = "limit.txt",
                mimeType = "text/plain",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = { ByteArrayInputStream("1234".toByteArray()) },
                declaredSizeBytes = 4L
            )
        )

        assertTrue(result is FileImportResult.Success)
        assertEquals(4L, (result as FileImportResult.Success).importedFile.sizeBytes)
    }

    @Test
    fun mapsStreamedOversizeToTypedImportError() {
        val controller = controllerWithId(
            id = "streamed-oversized",
            policy = ImportResourcePolicy(textMaxBytes = 4L)
        )

        val result = controller.importFile(
            FileImportRequest(
                displayName = "unknown-size.txt",
                mimeType = "text/plain",
                sourceKind = SourceKind.ACTION_SEND,
                openInputStream = { ByteArrayInputStream("12345".toByteArray()) }
            )
        )

        assertEquals(
            ImportError.FileTooLarge(FileType.TEXT, actualBytes = 5L, limitBytes = 4L),
            (result as FileImportResult.Failure).error
        )
    }

    @Test
    fun rethrowsCancellationBeforeOpeningStream() {
        val expected = FileTaskCancelledException()
        var opened = false
        val request = FileImportRequest(
            displayName = "cancelled.txt",
            mimeType = "text/plain",
            sourceKind = SourceKind.APP_PICKER,
            openInputStream = {
                opened = true
                ByteArrayInputStream("content".toByteArray())
            }
        )

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            controllerWithId("cancelled-before-open").importFile(
                request = request,
                cancellationToken = FileTaskCancellationToken { throw expected }
            )
        }

        assertSame(expected, actual)
        assertFalse(opened)
    }

    @Test
    fun rethrowsCancellationRaisedWhileOpeningStream() {
        val expected = FileTaskCancelledException()

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            controllerWithId("cancelled-opening").importFile(
                FileImportRequest(
                    displayName = "cancelled.md",
                    mimeType = "text/markdown",
                    sourceKind = SourceKind.ACTION_VIEW,
                    openInputStream = { throw expected }
                )
            )
        }

        assertSame(expected, actual)
    }

    @Test
    fun rethrowsCancellationRaisedDuringCopy() {
        val expected = FileTaskCancelledException()
        var checks = 0
        val cancellationToken = FileTaskCancellationToken {
            checks += 1
            if (checks == 2) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            controllerWithId("cancelled-copy").importFile(
                request = FileImportRequest(
                    displayName = "cancelled.html",
                    mimeType = "text/html",
                    sourceKind = SourceKind.ACTION_SEND,
                    openInputStream = { ByteArrayInputStream("content".toByteArray()) }
                ),
                cancellationToken = cancellationToken
            )
        }

        assertSame(expected, actual)
        assertEquals(2, checks)
    }

    @Test
    fun forwardsCumulativeCopyProgressToStore() {
        val progress = mutableListOf<Long>()
        val controller = controllerWithId(
            id = "progress",
            copier = LimitedInputCopier(bufferSize = 2)
        )

        val result = controller.importFile(
            request = FileImportRequest(
                displayName = "progress.txt",
                mimeType = "text/plain",
                sourceKind = SourceKind.APP_PICKER,
                openInputStream = { ByteArrayInputStream("12345".toByteArray()) }
            ),
            onBytesCopied = progress::add
        )

        assertTrue(result is FileImportResult.Success)
        assertEquals(listOf(2L, 4L, 5L), progress)
    }

    private fun controllerWithId(
        id: String,
        policy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT,
        copier: LimitedInputCopier = LimitedInputCopier()
    ): FileImportController {
        return FileImportController(
            store = ImportedFileStore(
                filesDir = temp.root,
                resourcePolicy = policy,
                copier = copier,
                idGenerator = { id }
            )
        )
    }
}
