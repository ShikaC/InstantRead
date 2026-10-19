package com.personalai.instantread

import com.personalai.instantread.preview.ReadingDisplayOptions

class PreviewReadingDisplayController(
    private val onPanelStateChanged: (historyId: String) -> Unit,
    private val onPreparationRequested: (request: ReadingDisplayPreparationRequest) -> Unit,
    private val onOptionsCommitted: (options: ReadingDisplayOptions) -> Unit
) {
    private var nextRequestId = 0L
    private var pendingRequest: ReadingDisplayPreparationRequest? = null

    var isPanelOpen: Boolean = false
        private set

    fun open(historyId: String) {
        isPanelOpen = true
        onPanelStateChanged(historyId)
    }

    fun close(historyId: String) {
        isPanelOpen = false
        onPanelStateChanged(historyId)
    }

    fun apply(
        historyId: String,
        committedOptions: ReadingDisplayOptions,
        edit: (ReadingDisplayOptions) -> ReadingDisplayOptions
    ): ReadingDisplayPreparationRequest {
        val desired = edit(optionsFor(historyId, committedOptions)).clamped()
        return stage(historyId, desired)
    }

    fun reset(historyId: String): ReadingDisplayPreparationRequest {
        return stage(historyId, ReadingDisplayOptions.DEFAULT)
    }

    fun optionsFor(
        historyId: String,
        committedOptions: ReadingDisplayOptions
    ): ReadingDisplayOptions {
        return pendingRequest
            ?.takeIf { it.historyId == historyId }
            ?.options
            ?: committedOptions.clamped()
    }

    fun complete(
        request: ReadingDisplayPreparationRequest,
        success: Boolean,
        beforeCommit: () -> Unit = {}
    ): Boolean {
        if (pendingRequest != request) return false
        if (success) beforeCommit()
        pendingRequest = null
        if (success) onOptionsCommitted(request.options)
        onPanelStateChanged(request.historyId)
        return true
    }

    fun cancelPending() {
        val request = pendingRequest ?: return
        pendingRequest = null
        onPanelStateChanged(request.historyId)
    }

    fun abandonPending() {
        pendingRequest = null
    }

    fun clear() {
        isPanelOpen = false
        abandonPending()
    }

    private fun stage(
        historyId: String,
        options: ReadingDisplayOptions
    ): ReadingDisplayPreparationRequest {
        val request = ReadingDisplayPreparationRequest(++nextRequestId, historyId, options.clamped())
        pendingRequest = request
        onPanelStateChanged(historyId)
        onPreparationRequested(request)
        return request
    }
}

data class ReadingDisplayPreparationRequest(
    val requestId: Long,
    val historyId: String,
    val options: ReadingDisplayOptions
)
