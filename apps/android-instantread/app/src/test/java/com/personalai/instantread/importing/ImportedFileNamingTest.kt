package com.personalai.instantread.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ImportedFileNamingTest {
    @Test
    fun uniqueNameKeepsOriginalWhenNoExistingNameMatches() {
        assertEquals("index.html", uniqueDisplayName("index.html", listOf("other.html")))
        assertEquals("index.html", uniqueDisplayName("index.html", emptyList()))
    }

    @Test
    fun uniqueNameAddsSuffixBeforeExtension() {
        assertEquals("index(1).html", uniqueDisplayName("index.html", listOf("index.html")))
    }

    @Test
    fun uniqueNameIncrementsUntilAFreeSuffixIsFound() {
        val existing = listOf("index.html", "index(1).html", "index(2).html")

        assertEquals("index(3).html", uniqueDisplayName("index.html", existing))
    }

    @Test
    fun uniqueNameReusesBaseFromAnExistingSuffix() {
        assertEquals("index(2).html", uniqueDisplayName("index(1).html", listOf("index.html", "index(1).html")))
    }

    @Test
    fun uniqueNameHandlesNamesWithoutExtension() {
        assertEquals("README(1)", uniqueDisplayName("README", listOf("README")))
    }

    @Test
    fun uniqueNameKeepsOnlyTheLastExtension() {
        assertEquals("archive.tar(1).gz", uniqueDisplayName("archive.tar.gz", listOf("archive.tar.gz")))
    }

    @Test
    fun uniqueNameMatchesExistingNamesIgnoringCase() {
        assertEquals("Index(1).HTML", uniqueDisplayName("Index.HTML", listOf("index.html")))
    }

    @Test
    fun withUniqueDisplayNameRewritesOnlyConflictingNames() {
        val file = importedFile("index.html")

        val renamed = file.withUniqueDisplayName(listOf("index.html", null))
        val unchanged = file.withUniqueDisplayName(listOf("other.html"))

        assertEquals("index(1).html", renamed.displayName)
        assertEquals("index.html", unchanged.displayName)
        assertEquals(file.id, renamed.id)
    }

    @Test
    fun withUniqueDisplayNameKeepsNullDisplayName() {
        val file = importedFile(displayName = null)

        val result = file.withUniqueDisplayName(listOf("index.html"))

        assertNull(result.displayName)
    }

    private fun importedFile(displayName: String?): ImportedFile {
        return ImportedFile(
            id = "import-1",
            displayName = displayName,
            fileType = FileType.HTML,
            sourceKind = SourceKind.APP_PICKER,
            localFile = File("/tmp/imports/import-1/source.html"),
            sizeBytes = 12L
        )
    }
}
