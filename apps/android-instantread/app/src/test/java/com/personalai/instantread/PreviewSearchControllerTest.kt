package com.personalai.instantread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSearchControllerTest {
    @Test
    fun nonEmptyQueryUsesFindApiAndPublishesMatchCounts() {
        val changedIds = mutableListOf<String>()
        val document = FakeSearchablePreviewDocument()
        val controller = PreviewSearchController { changedIds += it }

        controller.bind("history-1", document)
        controller.open("history-1")
        controller.updateQuery("history-1", "Title")

        assertEquals("Title", document.lastFindAllQuery)

        document.listener?.onFindResult(activeMatchOrdinal = 1, totalMatches = 4, isDoneCounting = true)

        assertEquals(
            PreviewSearchState(
                isOpen = true,
                query = "Title",
                activeMatchOrdinal = 1,
                totalMatches = 4
            ),
            controller.stateFor("history-1")
        )

        controller.next("history-1")
        controller.previous("history-1")

        assertEquals(listOf(true, false), document.findNextCalls)
        assertTrue(changedIds.contains("history-1"))
    }

    @Test
    fun emptyQueryClearsMatchesAndDoesNotKeepStaleCounts() {
        val document = FakeSearchablePreviewDocument()
        val controller = PreviewSearchController {}

        controller.bind("history-1", document)
        controller.open("history-1")
        controller.updateQuery("history-1", "Title")
        document.listener?.onFindResult(activeMatchOrdinal = 2, totalMatches = 5, isDoneCounting = true)

        controller.updateQuery("history-1", "")

        assertEquals(1, document.clearCalls)
        assertEquals(
            PreviewSearchState(isOpen = true),
            controller.stateFor("history-1")
        )
    }

    @Test
    fun reboundDocumentReplaysExistingQueryWhenTheNewPageIsReady() {
        val firstDocument = FakeSearchablePreviewDocument()
        val replacementDocument = FakeSearchablePreviewDocument()
        val controller = PreviewSearchController {}
        var restored = false

        controller.bind("history-1", firstDocument)
        controller.open("history-1")
        controller.updateQuery("history-1", "Title")
        firstDocument.listener?.onFindResult(activeMatchOrdinal = 2, totalMatches = 5, isDoneCounting = true)

        controller.bind("history-1", replacementDocument)

        assertEquals(null, replacementDocument.lastFindAllQuery)

        controller.restoreCurrentQuery("history-1") { restored = true }

        assertEquals("Title", replacementDocument.lastFindAllQuery)
        assertEquals(
            PreviewSearchState(isOpen = true, query = "Title", activeMatchOrdinal = 2, totalMatches = 5),
            controller.stateFor("history-1")
        )

        replacementDocument.listener?.onFindResult(activeMatchOrdinal = 0, totalMatches = 5, isDoneCounting = true)
        assertEquals(listOf(true), replacementDocument.findNextCalls)
        assertEquals(false, restored)

        replacementDocument.listener?.onFindResult(activeMatchOrdinal = 1, totalMatches = 5, isDoneCounting = true)
        assertEquals(listOf(true, true), replacementDocument.findNextCalls)
        assertEquals(false, restored)

        replacementDocument.listener?.onFindResult(activeMatchOrdinal = 2, totalMatches = 5, isDoneCounting = true)

        assertEquals(true, restored)
        assertEquals(
            PreviewSearchState(isOpen = true, query = "Title", activeMatchOrdinal = 2, totalMatches = 5),
            controller.stateFor("history-1")
        )
    }

    private class FakeSearchablePreviewDocument : SearchablePreviewDocument {
        var lastFindAllQuery: String? = null
        val findNextCalls = mutableListOf<Boolean>()
        var clearCalls = 0
        var listener: PreviewFindListener? = null

        override fun findAllAsync(query: String) {
            lastFindAllQuery = query
        }

        override fun findNext(forward: Boolean) {
            findNextCalls += forward
        }

        override fun clearMatches() {
            clearCalls += 1
        }

        override fun setFindListener(listener: PreviewFindListener?) {
            this.listener = listener
        }
    }
}
