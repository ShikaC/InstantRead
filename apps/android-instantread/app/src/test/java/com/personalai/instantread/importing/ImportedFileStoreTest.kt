package com.personalai.instantread.importing

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.LimitedInputCopier
import com.personalai.instantread.io.SourceFileTooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class ImportedFileStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun savesImportedFileUnderStablePrivatePath() {
        val stalePartial = File(temp.root, "imports/import-1.partial")
        stalePartial.mkdirs()
        File(stalePartial, "stale.tmp").writeText("stale")
        val store = ImportedFileStore(
            filesDir = temp.root,
            idGenerator = { "import-1" }
        )

        val imported = store.save(
            fileType = FileType.HTML,
            displayName = "Report.HTML",
            sourceKind = SourceKind.APP_PICKER,
            inputStream = ByteArrayInputStream("<html></html>".toByteArray())
        )

        assertEquals("import-1", imported.id)
        assertEquals("Report.HTML", imported.displayName)
        assertEquals(FileType.HTML, imported.fileType)
        assertEquals(SourceKind.APP_PICKER, imported.sourceKind)
        assertEquals(13L, imported.sizeBytes)
        assertEquals(File(temp.root, "imports/import-1/source.html").canonicalFile, imported.localFile)
        assertEquals("<html></html>", imported.localFile.readText())
        assertFalse(stalePartial.exists())
        assertEquals(listOf("import-1"), File(temp.root, "imports").listFiles()!!.map(File::getName))
        assertEquals(listOf("source.html"), imported.localFile.parentFile!!.listFiles()!!.map(File::getName))
    }

    @Test
    fun discardsOwnedPromotedImportDirectory() {
        val store = ImportedFileStore(temp.root) { "discard-id" }
        val imported = store.save(
            FileType.TEXT,
            "discard.txt",
            SourceKind.APP_PICKER,
            ByteArrayInputStream("content".toByteArray())
        )

        store.discard(imported)

        assertFalse(File(temp.root, "imports/discard-id").exists())
        assertFalse(imported.localFile.exists())
    }

    @Test
    fun discardRefusesPathTraversalOutsideImportsRoot() {
        val outsideDirectory = File(temp.root, "outside-owned").apply { mkdirs() }
        val outsideFile = File(outsideDirectory, "source.txt").apply { writeText("keep") }
        val unowned = ImportedFile(
            id = "../outside-owned",
            displayName = "outside.txt",
            fileType = FileType.TEXT,
            sourceKind = SourceKind.APP_PICKER,
            localFile = outsideFile,
            sizeBytes = outsideFile.length()
        )
        val store = ImportedFileStore(temp.root)

        assertThrows(IOException::class.java) { store.discard(unowned) }

        assertTrue(outsideDirectory.exists())
        assertEquals("keep", outsideFile.readText())
    }

    @Test
    fun discardRefusesMismatchedLocalFileWithoutDeletingOwnedDirectory() {
        val store = ImportedFileStore(temp.root) { "owned-id" }
        val imported = store.save(
            FileType.TEXT,
            "owned.txt",
            SourceKind.APP_PICKER,
            ByteArrayInputStream("owned".toByteArray())
        )
        val outsideFile = temp.newFile("outside.txt").apply { writeText("outside") }

        assertThrows(IOException::class.java) {
            store.discard(imported.copy(localFile = outsideFile))
        }

        assertTrue(imported.localFile.exists())
        assertEquals("outside", outsideFile.readText())
    }

    @Test
    fun discardRefusesNormalizedAliasForAnotherOwnedImport() {
        val store = ImportedFileStore(temp.root) { "victim-id" }
        val victim = store.save(
            FileType.TEXT,
            "victim.txt",
            SourceKind.APP_PICKER,
            ByteArrayInputStream("keep".toByteArray())
        )
        val aliased = victim.copy(id = "nested/../victim-id")

        assertThrows(IOException::class.java) { store.discard(aliased) }

        assertTrue(victim.localFile.exists())
        assertEquals("keep", victim.localFile.readText())
    }

    @Test
    fun discardReportsDeletionFailureAndKeepsOwnedDirectory() {
        val store = ImportedFileStore(
            filesDir = temp.root,
            deleteStagingDirectory = { false },
            idGenerator = { "delete-failure-id" }
        )
        val imported = store.save(
            FileType.TEXT,
            "failure.txt",
            SourceKind.APP_PICKER,
            ByteArrayInputStream("content".toByteArray())
        )

        assertThrows(IOException::class.java) { store.discard(imported) }

        assertTrue(imported.localFile.exists())
    }

    @Test
    fun usesDefaultExtensionForEachSupportedType() {
        val ids = mutableListOf("md-id", "txt-id", "zip-id")
        val store = ImportedFileStore(temp.root) { ids.removeAt(0) }

        val markdown = store.save(FileType.MARKDOWN, null, SourceKind.ACTION_SEND, ByteArrayInputStream("# Title".toByteArray()))
        val text = store.save(FileType.TEXT, null, SourceKind.ACTION_VIEW, ByteArrayInputStream("hello".toByteArray()))
        val zip = store.save(FileType.ZIP, null, SourceKind.APP_PICKER, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        assertEquals("source.md", markdown.localFile.name)
        assertEquals("source.txt", text.localFile.name)
        assertEquals("source.zip", zip.localFile.name)
    }

    @Test
    fun deletesImportDirectoryWhenSourceIsEmpty() {
        val store = ImportedFileStore(temp.root) { "empty-id" }

        assertThrows(EmptyImportException::class.java) {
            store.save(FileType.TEXT, "empty.txt", SourceKind.APP_PICKER, ByteArrayInputStream(ByteArray(0)))
        }

        assertFalse(File(temp.root, "imports/empty-id.partial").exists())
        assertFalse(File(temp.root, "imports/empty-id").exists())
    }

    @Test
    fun rejectsSourceBeyondConfiguredLimitAndCleansStaging() {
        var inputClosed = false
        val input = object : ByteArrayInputStream("12345".toByteArray()) {
            override fun close() {
                inputClosed = true
                super.close()
            }
        }
        val store = ImportedFileStore(
            filesDir = temp.root,
            resourcePolicy = ImportResourcePolicy(textMaxBytes = 4L),
            idGenerator = { "oversized-id" }
        )

        val error = assertThrows(SourceFileTooLargeException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "oversized.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = input
            )
        }

        assertEquals(5L, error.actualBytes)
        assertEquals(4L, error.limitBytes)
        assertTrue(inputClosed)
        assertFalse(File(temp.root, "imports/oversized-id.partial").exists())
        assertFalse(File(temp.root, "imports/oversized-id").exists())
    }

    @Test
    fun propagatesCancellationBeforePromotionAndCleansStaging() {
        val expectedError = FileTaskCancelledException()
        var cancellationChecks = 0
        val cancellationToken = FileTaskCancellationToken {
            cancellationChecks += 1
            if (cancellationChecks == 3) throw expectedError
        }
        val store = ImportedFileStore(
            filesDir = temp.root,
            idGenerator = { "cancelled-id" }
        )

        val actualError = assertThrows(FileTaskCancelledException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "cancelled.txt",
                sourceKind = SourceKind.ACTION_SEND,
                inputStream = ByteArrayInputStream("abc".toByteArray()),
                cancellationToken = cancellationToken
            )
        }

        assertSame(expectedError, actualError)
        assertEquals(3, cancellationChecks)
        assertFalse(File(temp.root, "imports/cancelled-id.partial").exists())
        assertFalse(File(temp.root, "imports/cancelled-id").exists())
    }

    @Test
    fun reportsCumulativeCopyProgress() {
        val progress = mutableListOf<Long>()
        val store = ImportedFileStore(
            filesDir = temp.root,
            copier = LimitedInputCopier(bufferSize = 2),
            idGenerator = { "progress-id" }
        )

        val imported = store.save(
            fileType = FileType.TEXT,
            displayName = "progress.txt",
            sourceKind = SourceKind.APP_PICKER,
            inputStream = ByteArrayInputStream("12345".toByteArray()),
            onBytesCopied = progress::add
        )

        assertEquals(listOf(2L, 4L, 5L), progress)
        assertEquals(5L, imported.sizeBytes)
    }

    @Test
    fun cleansStagingWhenPromotionFailsWithoutReturningImportedFile() {
        var imported: ImportedFile? = null
        var promotionCalls = 0
        val store = ImportedFileStore(
            filesDir = temp.root,
            promoteDirectory = { staging, final ->
                promotionCalls += 1
                assertEquals(File(temp.root, "imports/promotion-id.partial").canonicalFile, staging.canonicalFile)
                assertEquals(File(temp.root, "imports/promotion-id").canonicalFile, final.canonicalFile)
                assertEquals("content", File(staging, "source.txt").readText())
                false
            },
            idGenerator = { "promotion-id" }
        )

        assertThrows(IOException::class.java) {
            imported = store.save(
                fileType = FileType.TEXT,
                displayName = "promotion.txt",
                sourceKind = SourceKind.ACTION_VIEW,
                inputStream = ByteArrayInputStream("content".toByteArray())
            )
        }

        assertNull(imported)
        assertEquals(1, promotionCalls)
        assertFalse(File(temp.root, "imports/promotion-id.partial").exists())
        assertFalse(File(temp.root, "imports/promotion-id").exists())
    }

    @Test
    fun doesNotOverwriteOrDeleteExistingFinalDirectory() {
        val finalDir = File(temp.root, "imports/existing-id")
        finalDir.mkdirs()
        val existingFile = File(finalDir, "keep.txt")
        existingFile.writeText("keep")
        var promotionCalled = false
        val store = ImportedFileStore(
            filesDir = temp.root,
            promoteDirectory = { _, _ ->
                promotionCalled = true
                true
            },
            idGenerator = { "existing-id" }
        )

        assertThrows(IOException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "new.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = ByteArrayInputStream("new".toByteArray())
            )
        }

        assertFalse(promotionCalled)
        assertTrue(finalDir.isDirectory)
        assertEquals("keep", existingFile.readText())
        assertEquals(listOf("keep.txt"), finalDir.listFiles()!!.map(File::getName))
        assertFalse(File(temp.root, "imports/existing-id.partial").exists())
    }

    @Test
    fun preservesFinalDirectoryCreatedDuringPromotionWindow() {
        val finalDir = File(temp.root, "imports/window-id")
        val existingFile = File(finalDir, "keep.txt")
        val store = ImportedFileStore(
            filesDir = temp.root,
            promoteDirectory = { staging, final ->
                assertTrue(final.mkdir())
                existingFile.writeText("keep")
                promoteImportDirectoryWithoutReplace(staging, final)
            },
            idGenerator = { "window-id" }
        )

        assertThrows(IOException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "new.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = ByteArrayInputStream("new".toByteArray())
            )
        }

        assertTrue(finalDir.isDirectory)
        assertEquals("keep", existingFile.readText())
        assertEquals(listOf("keep.txt"), finalDir.listFiles()!!.map(File::getName))
        assertFalse(File(temp.root, "imports/window-id.partial").exists())
    }

    @Test
    fun promotesWholeStagingDirectoryBeforeFinalPathExists() {
        val stagingDir = File(temp.root, "imports/atomic-id.partial").apply { mkdirs() }
        File(stagingDir, "source.txt").writeText("content")
        val finalDir = File(temp.root, "imports/atomic-id")
        var renameCalls = 0

        val promoted = promoteImportDirectoryWithoutReplace(stagingDir, finalDir) { source, target ->
            renameCalls += 1
            assertEquals(stagingDir.canonicalFile, source.canonicalFile)
            assertEquals(finalDir.canonicalFile, target.canonicalFile)
            assertFalse(finalDir.exists())
            source.renameTo(target)
        }

        assertTrue(promoted)
        assertEquals(1, renameCalls)
        assertFalse(stagingDir.exists())
        assertEquals("content", File(finalDir, "source.txt").readText())
    }

    @Test
    fun doesNotPromoteWhenInputCloseFailsAfterCopy() {
        val expectedError = IOException("close failed")
        val input = object : ByteArrayInputStream("content".toByteArray()) {
            override fun close() {
                super.close()
                throw expectedError
            }
        }
        val store = ImportedFileStore(
            filesDir = temp.root,
            idGenerator = { "close-failure-id" }
        )

        val actualError = assertThrows(IOException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "content.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = input
            )
        }

        assertSame(expectedError, actualError)
        assertFalse(File(temp.root, "imports/close-failure-id.partial").exists())
        assertFalse(File(temp.root, "imports/close-failure-id").exists())
    }

    @Test
    fun keepsResourceFailurePrimaryWhenStagingCleanupFails() {
        val store = ImportedFileStore(
            filesDir = temp.root,
            resourcePolicy = ImportResourcePolicy(textMaxBytes = 4L),
            deleteStagingDirectory = { false },
            idGenerator = { "cleanup-failure-id" }
        )

        val error = assertThrows(SourceFileTooLargeException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "oversized.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = ByteArrayInputStream("12345".toByteArray())
            )
        }

        assertEquals(1, error.suppressed.size)
        assertTrue(error.suppressed.single() is IOException)
        assertTrue(error.suppressed.single().message!!.contains("cleanup-failure-id.partial"))
        assertTrue(File(temp.root, "imports/cleanup-failure-id.partial").exists())
        assertFalse(File(temp.root, "imports/cleanup-failure-id").exists())
    }

    @Test
    fun doesNotPromoteWhenCanonicalPathResolutionFails() {
        val expectedError = IOException("canonical path failed")
        var promotionCalled = false
        val store = ImportedFileStore(
            filesDir = temp.root,
            promoteDirectory = { _, _ ->
                promotionCalled = true
                true
            },
            canonicalizeFile = { throw expectedError },
            idGenerator = { "canonical-failure-id" }
        )

        val actualError = assertThrows(IOException::class.java) {
            store.save(
                fileType = FileType.TEXT,
                displayName = "content.txt",
                sourceKind = SourceKind.APP_PICKER,
                inputStream = ByteArrayInputStream("content".toByteArray())
            )
        }

        assertSame(expectedError, actualError)
        assertFalse(promotionCalled)
        assertFalse(File(temp.root, "imports/canonical-failure-id.partial").exists())
        assertFalse(File(temp.root, "imports/canonical-failure-id").exists())
    }
}
