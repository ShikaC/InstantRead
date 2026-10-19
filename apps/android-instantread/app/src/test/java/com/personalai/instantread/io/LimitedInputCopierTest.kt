package com.personalai.instantread.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class LimitedInputCopierTest {
    @Test
    fun rejectsZeroBufferSizeAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            LimitedInputCopier(bufferSize = 0)
        }
    }

    @Test
    fun rejectsNegativeBufferSizeAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            LimitedInputCopier(bufferSize = -1)
        }
    }

    @Test
    fun rejectsNegativeMaxBytesBeforeReadingInput() {
        var reads = 0
        val input = object : InputStream() {
            override fun read(): Int {
                reads += 1
                return -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads += 1
                return -1
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            LimitedInputCopier().copy(
                input = input,
                output = ByteArrayOutputStream(),
                maxBytes = -1L
            )
        }
        assertEquals(0, reads)
    }

    @Test
    fun copiesExactlyTheLimit() {
        val output = ByteArrayOutputStream()

        val copied = LimitedInputCopier().copy(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            output = output,
            maxBytes = 4
        )

        assertEquals(4L, copied)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun copiesEmptyInputWithZeroLimit() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val copied = LimitedInputCopier().copy(
            input = ByteArrayInputStream(ByteArray(0)),
            output = output,
            maxBytes = 0L,
            onBytesCopied = progress::add
        )

        assertEquals(0L, copied)
        assertArrayEquals(ByteArray(0), output.toByteArray())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun rejectsNonEmptyInputWithZeroLimitBeforeWriting() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val error = assertThrows(SourceFileTooLargeException::class.java) {
            LimitedInputCopier().copy(
                input = ByteArrayInputStream(byteArrayOf(1)),
                output = output,
                maxBytes = 0L,
                onBytesCopied = progress::add
            )
        }

        assertEquals(1L, error.actualBytes)
        assertEquals(0L, error.limitBytes)
        assertArrayEquals(ByteArray(0), output.toByteArray())
        assertTrue(progress.isEmpty())
    }

    @Test
    fun rejectsOneByteBeyondTheLimitBeforeWritingIt() {
        val output = ByteArrayOutputStream()

        val error = assertThrows(SourceFileTooLargeException::class.java) {
            LimitedInputCopier(bufferSize = 2).copy(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                output = output,
                maxBytes = 4
            )
        }

        assertEquals(5L, error.actualBytes)
        assertEquals(4L, error.limitBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), output.toByteArray())
    }

    @Test
    fun checksCancellationBeforeEveryBufferAndPropagatesTheException() {
        var checks = 0
        val events = mutableListOf<String>()
        val expectedError = FileTaskCancelledException()
        val output = ByteArrayOutputStream()
        val delegate = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        val input = object : InputStream() {
            override fun read(): Int {
                events += "read"
                return delegate.read()
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                events += "read"
                return delegate.read(buffer, offset, length)
            }
        }
        val cancellation = FileTaskCancellationToken {
            events += "cancel"
            checks += 1
            if (checks == 2) throw expectedError
        }

        val actualError = assertThrows(FileTaskCancelledException::class.java) {
            LimitedInputCopier(bufferSize = 2).copy(
                input = input,
                output = output,
                maxBytes = 4,
                cancellationToken = cancellation
            )
        }

        assertSame(expectedError, actualError)
        assertEquals(2, checks)
        assertEquals(listOf("cancel", "read", "cancel"), events)
        assertArrayEquals(byteArrayOf(1, 2), output.toByteArray())
    }

    @Test
    fun fallsBackToSingleByteReadWhenBulkReadReturnsZero() {
        val source = byteArrayOf(1, 2, 3)
        var index = 0
        var bulkReads = 0
        var singleByteReads = 0
        var firstBulkRead = true
        val input = object : InputStream() {
            override fun read(): Int {
                singleByteReads += 1
                if (index >= source.size) return -1
                return source[index++].toInt() and 0xff
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                bulkReads += 1
                if (firstBulkRead) {
                    firstBulkRead = false
                    return 0
                }
                if (index >= source.size) return -1
                val count = minOf(length, source.size - index)
                source.copyInto(buffer, offset, index, index + count)
                index += count
                return count
            }
        }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()

        val copied = LimitedInputCopier(bufferSize = 2).copy(
            input = input,
            output = output,
            maxBytes = source.size.toLong(),
            onBytesCopied = progress::add
        )

        assertEquals(3L, copied)
        assertArrayEquals(source, output.toByteArray())
        assertEquals(listOf(1L, 3L), progress)
        assertEquals(3, bulkReads)
        assertEquals(1, singleByteReads)
    }

    @Test
    fun reportsCumulativeCopiedBytesAfterEachBuffer() {
        val progress = mutableListOf<Long>()

        val copied = LimitedInputCopier(bufferSize = 2).copy(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
            output = ByteArrayOutputStream(),
            maxBytes = 5,
            onBytesCopied = progress::add
        )

        assertEquals(5L, copied)
        assertEquals(listOf(2L, 4L, 5L), progress)
    }

    @Test
    fun tooManyZipEntriesExceptionExposesStructuredFieldsAndHierarchy() {
        val error = TooManyZipEntriesException(actualCount = 5_001, limitCount = 5_000)

        assertEquals(5_001, error.actualCount)
        assertEquals(5_000, error.limitCount)
        assertFileResourceExceptionHierarchy(error)
    }

    @Test
    fun zipEntryTooLargeExceptionExposesStructuredFieldsAndHierarchy() {
        val error = ZipEntryTooLargeException(
            entryName = "assets/video.mp4",
            actualBytes = 50L * ImportResourcePolicy.MIB + 1L,
            limitBytes = 50L * ImportResourcePolicy.MIB
        )

        assertEquals("assets/video.mp4", error.entryName)
        assertEquals(50L * ImportResourcePolicy.MIB + 1L, error.actualBytes)
        assertEquals(50L * ImportResourcePolicy.MIB, error.limitBytes)
        assertFileResourceExceptionHierarchy(error)
    }

    @Test
    fun zipExpandedTooLargeExceptionExposesStructuredFieldsAndHierarchy() {
        val error = ZipExpandedTooLargeException(
            actualBytes = 250L * ImportResourcePolicy.MIB + 1L,
            limitBytes = 250L * ImportResourcePolicy.MIB
        )

        assertEquals(250L * ImportResourcePolicy.MIB + 1L, error.actualBytes)
        assertEquals(250L * ImportResourcePolicy.MIB, error.limitBytes)
        assertFileResourceExceptionHierarchy(error)
    }

    private fun assertFileResourceExceptionHierarchy(error: Throwable) {
        assertTrue(error is FileResourceException)
        assertTrue(error is IOException)
    }
}
