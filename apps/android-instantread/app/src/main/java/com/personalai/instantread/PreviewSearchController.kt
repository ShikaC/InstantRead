package com.personalai.instantread

import android.webkit.WebView

interface PreviewFindListener {
    fun onFindResult(activeMatchOrdinal: Int, totalMatches: Int, isDoneCounting: Boolean)
}

interface SearchablePreviewDocument {
    fun findAllAsync(query: String)
    fun findNext(forward: Boolean)
    fun clearMatches()
    fun setFindListener(listener: PreviewFindListener?)
}

class WebViewSearchablePreviewDocument(
    private val webView: WebView
) : SearchablePreviewDocument {
    override fun findAllAsync(query: String) {
        webView.findAllAsync(query)
    }

    override fun findNext(forward: Boolean) {
        webView.findNext(forward)
    }

    override fun clearMatches() {
        webView.clearMatches()
    }

    override fun setFindListener(listener: PreviewFindListener?) {
        webView.setFindListener { activeMatchOrdinal, totalMatches, isDoneCounting ->
            listener?.onFindResult(activeMatchOrdinal, totalMatches, isDoneCounting)
        }
    }
}

class PreviewSearchController(
    private val onStateChanged: (historyId: String) -> Unit
) {
    private data class PendingRestore(
        val historyId: String,
        val targetOrdinal: Int,
        val onRestored: () -> Unit
    )

    private var currentHistoryId: String? = null
    private var document: SearchablePreviewDocument? = null
    private var state: PreviewSearchState = PreviewSearchState()
    private var pendingRestore: PendingRestore? = null

    fun bind(historyId: String, document: SearchablePreviewDocument) {
        this.document?.setFindListener(null)
        if (currentHistoryId != historyId) state = PreviewSearchState()
        currentHistoryId = historyId
        this.document = document
        pendingRestore = null
        document.setFindListener(
            object : PreviewFindListener {
                override fun onFindResult(activeMatchOrdinal: Int, totalMatches: Int, isDoneCounting: Boolean) {
                    if (!isDoneCounting || currentHistoryId != historyId) return
                    pendingRestore?.takeIf { it.historyId == historyId }?.let { pending ->
                        val targetOrdinal = if (totalMatches > 0) {
                            pending.targetOrdinal.coerceIn(0, totalMatches - 1)
                        } else {
                            0
                        }
                        if (totalMatches > 0 && activeMatchOrdinal != targetOrdinal) {
                            this@PreviewSearchController.document?.findNext(activeMatchOrdinal < targetOrdinal)
                            return
                        }
                        pendingRestore = null
                        state = state.copy(
                            activeMatchOrdinal = targetOrdinal,
                            totalMatches = totalMatches.coerceAtLeast(0)
                        )
                        onStateChanged(historyId)
                        pending.onRestored()
                        return
                    }
                    state = state.copy(
                        activeMatchOrdinal = activeMatchOrdinal.coerceAtLeast(0),
                        totalMatches = totalMatches.coerceAtLeast(0)
                    )
                    onStateChanged(historyId)
                }
            }
        )
    }

    fun stateFor(historyId: String): PreviewSearchState {
        return if (currentHistoryId == historyId) state else PreviewSearchState()
    }

    fun open(historyId: String) {
        if (!matches(historyId)) return
        state = state.copy(isOpen = true)
        onStateChanged(historyId)
    }

    fun close(historyId: String) {
        if (!matches(historyId)) return
        pendingRestore = null
        document?.clearMatches()
        state = PreviewSearchState()
        onStateChanged(historyId)
    }

    fun updateQuery(historyId: String, query: String) {
        if (!matches(historyId)) return
        pendingRestore = null
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearQuery(historyId)
            return
        }
        state = state.copy(isOpen = true, query = query, activeMatchOrdinal = 0, totalMatches = 0)
        document?.findAllAsync(query)
        onStateChanged(historyId)
    }

    fun next(historyId: String) {
        if (matches(historyId) && state.query.isNotBlank()) {
            document?.findNext(true)
        }
    }

    fun previous(historyId: String) {
        if (matches(historyId) && state.query.isNotBlank()) {
            document?.findNext(false)
        }
    }

    fun restoreCurrentQuery(historyId: String, onRestored: () -> Unit = {}) {
        if (!matches(historyId) || state.query.isBlank()) {
            onRestored()
            return
        }
        pendingRestore = PendingRestore(historyId, state.activeMatchOrdinal, onRestored)
        document?.findAllAsync(state.query)
    }

    fun clearQuery(historyId: String) {
        if (!matches(historyId)) return
        pendingRestore = null
        document?.clearMatches()
        state = state.copy(query = "", activeMatchOrdinal = 0, totalMatches = 0)
        onStateChanged(historyId)
    }

    fun clear() {
        pendingRestore = null
        document?.clearMatches()
        document?.setFindListener(null)
        currentHistoryId = null
        document = null
        state = PreviewSearchState()
    }

    private fun matches(historyId: String): Boolean {
        return currentHistoryId == historyId && document != null
    }
}
