package com.personalai.instantread.io

import com.personalai.instantread.importing.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportResourcePolicyTest {
    @Test
    fun exposesConfirmedDefaultLimits() {
        val policy = ImportResourcePolicy.DEFAULT

        assertEquals(1024L * 1024L, ImportResourcePolicy.MIB)
        assertEquals(10L * 1024L * 1024L, policy.maxSourceBytes(FileType.MARKDOWN))
        assertEquals(10L * 1024L * 1024L, policy.maxSourceBytes(FileType.TEXT))
        assertEquals(50L * 1024L * 1024L, policy.maxSourceBytes(FileType.HTML))
        assertEquals(100L * 1024L * 1024L, policy.maxSourceBytes(FileType.ZIP))
        assertEquals(5_000, policy.maxZipEntryCount)
        assertEquals(50L * 1024L * 1024L, policy.maxZipEntryBytes)
        assertEquals(250L * 1024L * 1024L, policy.maxZipExpandedBytes)
        assertEquals(128, policy.maxImportIdLength)
    }

    @Test
    fun rejectsEveryNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(markdownMaxBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(textMaxBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(htmlMaxBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(zipSourceMaxBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(maxZipEntryCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(maxZipEntryBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(maxZipExpandedBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportResourcePolicy(maxImportIdLength = 0)
        }
    }

    @Test
    fun acceptsZeroLimits() {
        val policy = ImportResourcePolicy(
            markdownMaxBytes = 0L,
            textMaxBytes = 0L,
            htmlMaxBytes = 0L,
            zipSourceMaxBytes = 0L,
            maxZipEntryCount = 0,
            maxZipEntryBytes = 0L,
            maxZipExpandedBytes = 0L
        )

        assertEquals(0L, policy.maxSourceBytes(FileType.MARKDOWN))
        assertEquals(0L, policy.maxSourceBytes(FileType.TEXT))
        assertEquals(0L, policy.maxSourceBytes(FileType.HTML))
        assertEquals(0L, policy.maxSourceBytes(FileType.ZIP))
        assertEquals(0, policy.maxZipEntryCount)
        assertEquals(0L, policy.maxZipEntryBytes)
        assertEquals(0L, policy.maxZipExpandedBytes)
    }
}
