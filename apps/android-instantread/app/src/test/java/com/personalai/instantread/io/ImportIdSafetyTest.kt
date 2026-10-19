package com.personalai.instantread.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportIdSafetyTest {
    @Test
    fun acceptsBoundedSingleSegmentIds() {
        assertTrue("import-1".isSafeImportId())
        assertTrue("a".repeat(ImportResourcePolicy.DEFAULT.maxImportIdLength).isSafeImportId())
    }

    @Test
    fun rejectsTraversalAbsoluteAndBlankIds() {
        assertFalse("../outside".isSafeImportId())
        assertFalse("nested/child".isSafeImportId())
        assertFalse("nested\\child".isSafeImportId())
        assertFalse("/absolute".isSafeImportId())
        assertFalse(".".isSafeImportId())
        assertFalse("..".isSafeImportId())
        assertFalse(" ".isSafeImportId())
    }

    @Test
    fun rejectsIdsBeyondConfiguredLength() {
        val policy = ImportResourcePolicy(maxImportIdLength = 4)

        assertFalse("12345".isSafeImportId(policy))
        assertTrue("1234".isSafeImportId(policy))
    }
}
