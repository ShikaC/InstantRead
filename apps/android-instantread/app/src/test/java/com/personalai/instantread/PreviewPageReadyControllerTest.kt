package com.personalai.instantread

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPageReadyControllerTest {
    @Test
    fun pageFinishedRestoresScrollAndReplaysSearchOnTheReplacementDocument() {
        val store = FakeReadingPositionStore().apply {
            save("history-1", PreviewDocumentMode.READING, 480)
        }
        val scrollController = PreviewScrollPositionController(store)
        val replacementScrollDocument = FakeScrollablePreviewDocument()
        scrollController.bind("history-1", PreviewDocumentMode.READING, replacementScrollDocument)

        val searchController = PreviewSearchController {}
        val firstSearchDocument = FakeSearchablePreviewDocument()
        val replacementSearchDocument = FakeSearchablePreviewDocument()
        searchController.bind("history-1", firstSearchDocument)
        searchController.updateQuery("history-1", "Title")
        searchController.bind("history-1", replacementSearchDocument)

        val controller = PreviewPageReadyController(scrollController, searchController)
        val staleLoad = controller.beginLoad("history-1")
        val currentLoad = controller.beginLoad("history-1")

        assertEquals(null, replacementScrollDocument.lastScrollTo)
        assertEquals(null, replacementSearchDocument.lastFindAllQuery)

        controller.onPageFinished(staleLoad)

        assertEquals(null, replacementScrollDocument.lastScrollTo)
        assertEquals(null, replacementSearchDocument.lastFindAllQuery)

        controller.onPageFinished(currentLoad)

        assertEquals(0 to 480, replacementScrollDocument.lastScrollTo)
        assertEquals("Title", replacementSearchDocument.lastFindAllQuery)
    }

    private class FakeReadingPositionStore : PreviewReadingPositionStore {
        private val positions = mutableMapOf<Pair<String, PreviewDocumentMode>, Int>()

        override fun save(historyId: String, documentMode: PreviewDocumentMode, scrollY: Int) {
            positions[historyId to documentMode] = scrollY
        }

        override fun load(historyId: String, documentMode: PreviewDocumentMode): Int {
            return positions[historyId to documentMode] ?: 0
        }
    }

    private class FakeScrollablePreviewDocument : ScrollablePreviewDocument {
        override val scrollY: Int = 0
        var lastScrollTo: Pair<Int, Int>? = null

        override fun post(action: () -> Unit) = action()

        override fun scrollTo(x: Int, y: Int) {
            lastScrollTo = x to y
        }
    }

    private class FakeSearchablePreviewDocument : SearchablePreviewDocument {
        var lastFindAllQuery: String? = null
        var listener: PreviewFindListener? = null

        override fun findAllAsync(query: String) {
            lastFindAllQuery = query
            listener?.onFindResult(activeMatchOrdinal = 0, totalMatches = 1, isDoneCounting = true)
        }

        override fun findNext(forward: Boolean) = Unit
        override fun clearMatches() = Unit
        override fun setFindListener(listener: PreviewFindListener?) {
            this.listener = listener
        }
    }
}
