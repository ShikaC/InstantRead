package com.personalai.instantread

import android.app.Application
import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.history.PreviewHistoryDatabase
import com.personalai.instantread.history.PreviewHistoryRepository
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewScriptAuthorizationControllerTest {
    private lateinit var context: Application
    private lateinit var database: PreviewHistoryDatabase
    private lateinit var repository: PreviewHistoryRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(PreviewHistoryDatabase.DATABASE_NAME)
        database = PreviewHistoryDatabase(context)
        repository = PreviewHistoryRepository(database) { 1_000L }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PreviewHistoryDatabase.DATABASE_NAME)
    }

    @Test
    fun enablingRequiresConsentBeforePersistingAuthorization() {
        repository.upsertImported(importedFile("doc-a"))
        val consented = mutableListOf<String>()
        val updated = mutableListOf<PreviewHistory>()
        val controller = PreviewScriptAuthorizationController(
            repository,
            { history, confirm -> consented += history.id; confirm() },
            { updated += it }
        )

        controller.toggle("doc-a")

        assertEquals(listOf("doc-a"), consented)
        assertTrue(requireNotNull(repository.findById("doc-a")).jsAllowed)
        assertEquals(listOf("doc-a"), updated.map { it.id })
    }

    @Test
    fun cancellingConsentLeavesDocumentUnauthorized() {
        repository.upsertImported(importedFile("doc-a"))
        val updated = mutableListOf<PreviewHistory>()
        val controller = PreviewScriptAuthorizationController(
            repository,
            { _, _ -> },
            { updated += it }
        )

        controller.toggle("doc-a")

        assertFalse(requireNotNull(repository.findById("doc-a")).jsAllowed)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun revokingIsImmediateAndDoesNotAffectOtherDocuments() {
        repository.upsertImported(importedFile("doc-a"))
        repository.upsertImported(importedFile("doc-b"))
        repository.setJavaScriptAllowed("doc-a", true)
        repository.setJavaScriptAllowed("doc-b", true)
        val consented = mutableListOf<String>()
        val updated = mutableListOf<PreviewHistory>()
        val controller = PreviewScriptAuthorizationController(
            repository,
            { history, confirm -> consented += history.id; confirm() },
            { updated += it }
        )

        controller.toggle("doc-a")

        assertTrue(consented.isEmpty())
        assertFalse(requireNotNull(repository.findById("doc-a")).jsAllowed)
        assertTrue(requireNotNull(repository.findById("doc-b")).jsAllowed)
        assertEquals(listOf("doc-a"), updated.map { it.id })
    }

    @Test
    fun togglingUnknownDocumentDoesNothing() {
        val controller = PreviewScriptAuthorizationController(repository, { _, _ -> }, {})

        controller.toggle("missing")

        assertNull(repository.findById("missing"))
    }

    private fun importedFile(id: String): ImportedFile {
        val localFile = File(context.filesDir, "imports/$id/source.html").apply {
            parentFile?.mkdirs()
            writeText("<html></html>")
        }
        return ImportedFile(
            id = id,
            displayName = "$id.html",
            fileType = FileType.HTML,
            sourceKind = SourceKind.APP_PICKER,
            localFile = localFile,
            sizeBytes = localFile.length()
        )
    }
}
