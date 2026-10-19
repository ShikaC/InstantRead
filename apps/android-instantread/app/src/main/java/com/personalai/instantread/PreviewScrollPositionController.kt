package com.personalai.instantread

import android.webkit.WebView

interface ScrollablePreviewDocument {
    val scrollY: Int
    fun post(action: () -> Unit)
    fun scrollTo(x: Int, y: Int)
}

class WebViewScrollablePreviewDocument(
    private val webView: WebView
) : ScrollablePreviewDocument {
    override val scrollY: Int
        get() = webView.scrollY

    override fun post(action: () -> Unit) {
        webView.post { action() }
    }

    override fun scrollTo(x: Int, y: Int) {
        webView.scrollTo(x, y)
    }
}

class PreviewScrollPositionController(
    private val store: PreviewReadingPositionStore
) {
    private var currentHistoryId: String? = null
    private var currentDocumentMode: PreviewDocumentMode = PreviewDocumentMode.READING
    private var document: ScrollablePreviewDocument? = null

    fun bind(
        historyId: String,
        documentMode: PreviewDocumentMode,
        document: ScrollablePreviewDocument
    ) {
        currentHistoryId = historyId
        currentDocumentMode = documentMode
        this.document = document
    }

    fun saveCurrent() {
        val historyId = currentHistoryId ?: return
        val document = document ?: return
        store.save(historyId, currentDocumentMode, document.scrollY)
    }

    fun restoreCurrent() {
        restoreCurrent(currentHistoryId ?: return)
    }

    fun restoreCurrent(historyId: String) {
        if (currentHistoryId != historyId) return
        val document = document ?: return
        val scrollY = store.load(historyId, currentDocumentMode)
        document.post {
            document.scrollTo(0, scrollY)
        }
    }

    fun clear() {
        currentHistoryId = null
        currentDocumentMode = PreviewDocumentMode.READING
        document = null
    }
}
