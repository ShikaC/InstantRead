package com.personalai.instantread

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewScrollPositionControllerTest {
    @Test
    fun savesAndRestoresTheBoundDocumentPositionForEachMode() {
        val store = FakeReadingPositionStore()
        val controller = PreviewScrollPositionController(store)
        val readingDocument = FakeScrollablePreviewDocument(scrollY = 320)

        controller.bind("history-1", PreviewDocumentMode.READING, readingDocument)
        controller.saveCurrent()

        assertEquals(320, store.load("history-1", PreviewDocumentMode.READING))
        assertEquals(0, store.load("history-1", PreviewDocumentMode.REVIEW))

        store.save("history-1", PreviewDocumentMode.REVIEW, 640)
        val reviewDocument = FakeScrollablePreviewDocument(scrollY = 0)

        controller.bind("history-1", PreviewDocumentMode.REVIEW, reviewDocument)
        controller.restoreCurrent()

        assertEquals(1, reviewDocument.postCalls)
        assertEquals(Pair(0, 640), reviewDocument.lastScrollTo)
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

    private class FakeScrollablePreviewDocument(
        override var scrollY: Int
    ) : ScrollablePreviewDocument {
        var postCalls = 0
        var lastScrollTo: Pair<Int, Int>? = null

        override fun post(action: () -> Unit) {
            postCalls += 1
            action()
        }

        override fun scrollTo(x: Int, y: Int) {
            lastScrollTo = x to y
            scrollY = y
        }
    }
}
