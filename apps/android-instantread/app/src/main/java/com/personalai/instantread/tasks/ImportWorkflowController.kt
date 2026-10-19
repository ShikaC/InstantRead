package com.personalai.instantread.tasks

import android.net.Uri
import com.personalai.instantread.importing.FileImportController
import com.personalai.instantread.importing.FileImportResult
import com.personalai.instantread.importing.ImportError
import com.personalai.instantread.importing.ImportRequestFactory
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind

data class ImportWorkflowInput(
    val uri: Uri,
    val sourceKind: SourceKind,
    val intentMimeType: String? = null,
    val entryAction: String? = null,
    val entryUriSource: String? = null
)

class ImportWorkflowController(
    private val requestFactory: ImportRequestFactory,
    private val importController: FileImportController,
    private val taskController: ForegroundFileTaskController
) {
    fun start(
        input: ImportWorkflowInput,
        onProgress: (ForegroundTaskState) -> Unit,
        onSuccess: (ImportedFile) -> Unit,
        onFailure: (ImportError) -> Unit
    ) {
        taskController.start<ImportedFile, ForegroundTaskState>(
            work = { token, report ->
                report(ForegroundTaskState(null, ForegroundTaskStage.READING_METADATA))
                val request = requestFactory.fromUri(
                    input.uri,
                    input.sourceKind,
                    input.intentMimeType,
                    input.entryAction,
                    input.entryUriSource,
                    cancellationToken = token
                )
                val totalBytes = request.declaredSizeBytes?.takeIf { it > 0L }
                val initialState = ForegroundTaskState(
                    request.displayName,
                    ForegroundTaskStage.IMPORTING_FILE,
                    completedBytes = 0L,
                    totalBytes = totalBytes
                )
                report(initialState)
                var lastPercent = initialState.percent
                when (val result = importController.importFile(request, token) { copiedBytes ->
                    if (totalBytes != null) {
                        val state = ForegroundTaskState(
                            request.displayName,
                            ForegroundTaskStage.IMPORTING_FILE,
                            completedBytes = copiedBytes,
                            totalBytes = totalBytes
                        )
                        if (state.percent != lastPercent) {
                            lastPercent = state.percent
                            report(state)
                        }
                    }
                }) {
                    is FileImportResult.Success -> result.importedFile
                    is FileImportResult.Failure -> throw ImportWorkflowFailure(result.error)
                }
            },
            onProgress = onProgress,
            onSuccess = onSuccess,
            onFailure = { error ->
                onFailure(
                    (error as? ImportWorkflowFailure)?.error
                        ?: ImportError.CopyFailed(error.message.orEmpty())
                )
            },
            onDiscardedResult = importController::discardImportedFile
        )
    }

    private class ImportWorkflowFailure(val error: ImportError) : RuntimeException()
}
