package com.personalai.instantread.zip

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.TooManyZipEntriesException
import com.personalai.instantread.io.ZipEntryTooLargeException
import com.personalai.instantread.io.ZipExpandedTooLargeException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SafeZipExtractorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun extractsSafeFilesIntoTargetDirectoryAndReturnsStats() {
        val index = byteArrayOf(0x01, 0x02, 0x03)
        val style = byteArrayOf(0x7f, 0x00)
        val zip = zipFile(
            "index.html" to index,
            "assets/style.css" to style
        )
        val target = temp.newFolder("out")

        val stats = SafeZipExtractor.extract(zip, target)

        assertArrayEquals(index, File(target, "index.html").readBytes())
        assertArrayEquals(style, File(target, "assets/style.css").readBytes())
        assertEquals(ZipExtractionStats(entryCount = 2, fileCount = 2, expandedBytes = 5L), stats)
    }

    @Test
    fun rejectsTargetDirectoryThatIsAnExistingFileEvenForEmptyZip() {
        val zip = zipFile()
        val target = temp.newFile("out")

        assertThrows<IOException> {
            SafeZipExtractor.extract(zip, target)
        }
    }

    @Test
    fun rejectsDirectoryEntryWhenItsPathIsOccupiedByAFile() {
        val zip = zipFile("assets/" to byteArrayOf())
        val target = temp.newFolder("out")
        val occupiedPath = File(target, "assets").apply {
            writeBytes(byteArrayOf(0x01))
        }

        assertThrows<IOException> {
            SafeZipExtractor.extract(zip, target)
        }

        assertTrue(occupiedPath.isFile)
    }

    @Test
    fun blocksParentDirectoryTraversal() {
        val zip = zipFile("../escape.txt" to byteArrayOf(0x01))
        val target = temp.newFolder("out")
        val outside = File(target.parentFile, "escape.txt")

        assertThrowsZipSlip {
            SafeZipExtractor.extract(zip, target)
        }

        assertFalse(outside.exists())
    }

    @Test
    fun blocksNestedTraversal() {
        val zip = zipFile("safe/../../escape.txt" to byteArrayOf(0x01))
        val target = temp.newFolder("out")
        val outside = File(target.parentFile, "escape.txt")

        assertThrowsZipSlip {
            SafeZipExtractor.extract(zip, target)
        }

        assertFalse(outside.exists())
    }

    @Test
    fun countsDirectoryEntriesWithoutAddingToFileCountOrExpandedBytes() {
        val script = byteArrayOf(0x10, 0x20)
        val zip = zipFile("assets/" to byteArrayOf(), "assets/app.js" to script)
        val target = temp.newFolder("out")

        val stats = SafeZipExtractor.extract(zip, target)

        assertTrue(File(target, "assets").isDirectory)
        assertArrayEquals(script, File(target, "assets/app.js").readBytes())
        assertEquals(ZipExtractionStats(entryCount = 2, fileCount = 1, expandedBytes = 2L), stats)
    }

    @Test
    fun countsDirectoryEntryPayloadTowardExpandedBytesWithoutAddingToFileCount() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val zip = zipFile("assets/" to payload)
        val target = temp.newFolder("out")

        val stats = SafeZipExtractor.extract(zip, target)

        assertTrue(File(target, "assets").isDirectory)
        assertEquals(ZipExtractionStats(entryCount = 1, fileCount = 0, expandedBytes = 3L), stats)
    }

    @Test
    fun rejectsDirectoryEntryPayloadAboveEntryLimit() {
        val zip = zipFile("assets/" to byteArrayOf(0x01, 0x02, 0x03))
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(maxZipEntryBytes = 2L)

        val error = assertThrows<ZipEntryTooLargeException> {
            SafeZipExtractor.extract(zip, target, policy)
        }

        assertEquals("assets/", error.entryName)
        assertEquals(3L, error.actualBytes)
        assertEquals(2L, error.limitBytes)
        assertTrue(File(target, "assets").isDirectory)
    }

    @Test
    fun countsDirectoryEntryPayloadTowardExpandedLimitAcrossEntries() {
        val zip = zipFile(
            "assets/" to byteArrayOf(0x01, 0x02),
            "index.html" to byteArrayOf(0x03, 0x04)
        )
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(maxZipExpandedBytes = 3L)

        val error = assertThrows<ZipExpandedTooLargeException> {
            SafeZipExtractor.extract(zip, target, policy)
        }

        assertEquals(4L, error.actualBytes)
        assertEquals(3L, error.limitBytes)
        assertEquals(0L, File(target, "index.html").length())
    }

    @Test
    fun cancellationCanInterruptDirectoryEntryPayloadDrain() {
        val zip = zipFile("assets/" to ByteArray(32) { it.toByte() })
        val target = temp.newFolder("out")
        val expected = FileTaskCancelledException()
        var cancellationChecks = 0
        val cancellationToken = FileTaskCancellationToken {
            cancellationChecks += 1
            if (cancellationChecks == 2) {
                throw expected
            }
        }

        val actual = assertThrows<FileTaskCancelledException> {
            SafeZipExtractor.extract(
                zipFile = zip,
                targetDirectory = target,
                cancellationToken = cancellationToken
            )
        }

        assertSame(expected, actual)
        assertEquals(2, cancellationChecks)
    }

    @Test
    fun rejectsEntryCountAboveLimitIncludingDirectories() {
        val zip = zipFile("assets/" to byteArrayOf(), "index.html" to byteArrayOf(0x01))
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(maxZipEntryCount = 1)

        val error = assertThrows<TooManyZipEntriesException> {
            SafeZipExtractor.extract(zip, target, policy)
        }

        assertEquals(2, error.actualCount)
        assertEquals(1, error.limitCount)
        assertFalse(File(target, "index.html").exists())
    }

    @Test
    fun rejectsEntryBytesAboveLimitWithoutWritingOverflowingChunk() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val zip = zipFile("payload.bin" to payload)
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(maxZipEntryBytes = 4L)

        val error = assertThrows<ZipEntryTooLargeException> {
            SafeZipExtractor.extract(zip, target, policy)
        }

        assertEquals("payload.bin", error.entryName)
        assertEquals(5L, error.actualBytes)
        assertEquals(4L, error.limitBytes)
        assertEquals(0L, File(target, "payload.bin").length())
    }

    @Test
    fun rejectsExpandedBytesAboveLimitAcrossEntriesWithoutWritingOverflowingChunk() {
        val zip = zipFile(
            "first.bin" to byteArrayOf(0x01, 0x02, 0x03),
            "second.bin" to byteArrayOf(0x04, 0x05, 0x06, 0x07)
        )
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(maxZipExpandedBytes = 5L)

        val error = assertThrows<ZipExpandedTooLargeException> {
            SafeZipExtractor.extract(zip, target, policy)
        }

        assertEquals(7L, error.actualBytes)
        assertEquals(5L, error.limitBytes)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), File(target, "first.bin").readBytes())
        assertEquals(0L, File(target, "second.bin").length())
    }

    @Test
    fun allowsCountsAndBytesExactlyAtLimits() {
        val zip = zipFile(
            "first.bin" to byteArrayOf(0x01, 0x02, 0x03),
            "second.bin" to byteArrayOf(0x04, 0x05)
        )
        val target = temp.newFolder("out")
        val policy = ImportResourcePolicy.DEFAULT.copy(
            maxZipEntryCount = 2,
            maxZipEntryBytes = 3L,
            maxZipExpandedBytes = 5L
        )

        val stats = SafeZipExtractor.extract(zip, target, policy)

        assertEquals(ZipExtractionStats(entryCount = 2, fileCount = 2, expandedBytes = 5L), stats)
    }

    @Test
    fun cancellationAfterReadPreventsThePendingChunkFromBeingWritten() {
        val bufferSize = 16 * 1024
        val payload = ByteArray(bufferSize + 32) { it.toByte() }
        val zip = zipFile("payload.bin" to payload)
        val target = temp.newFolder("out")
        val expected = FileTaskCancelledException()
        var cancellationChecks = 0
        val cancellationToken = FileTaskCancellationToken {
            cancellationChecks += 1
            if (cancellationChecks == 5) {
                throw expected
            }
        }

        val actual = assertThrows<FileTaskCancelledException> {
            SafeZipExtractor.extract(
                zipFile = zip,
                targetDirectory = target,
                cancellationToken = cancellationToken
            )
        }

        assertSame(expected, actual)
        assertEquals(5, cancellationChecks)
        assertEquals(bufferSize.toLong(), File(target, "payload.bin").length())
        assertArrayEquals(payload.copyOf(bufferSize), File(target, "payload.bin").readBytes())
    }

    @Test
    fun rejectsBytesWithoutZipCentralDirectoryBeforeCreatingTarget() {
        val corrupt = temp.newFile("corrupt.zip").apply {
            writeBytes(ByteArray(4096) { index -> (index * 31).toByte() })
        }
        val target = File(temp.root, "not-created")

        assertThrows<ZipException> {
            SafeZipExtractor.extract(corrupt, target)
        }

        assertFalse(target.exists())
    }

    private fun zipFile(vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile("sample.zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = content.size.toLong()
                    crc = CRC32().apply { update(content) }.value
                }
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return file
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) {
                throw error
            }
            error
        }
    }

    private fun assertThrowsZipSlip(block: () -> Unit) {
        val error = assertThrows<ZipSlipException>(block)
        assertTrue(error.message.orEmpty().contains("Unsafe ZIP entry"))
    }
}
