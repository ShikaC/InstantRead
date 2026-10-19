package com.personalai.instantread.importing

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.SourceFileTooLargeException

class FileImportController(
    private val store: ImportedFileStore
) {
    fun discardImportedFile(importedFile: ImportedFile) {
        store.discard(importedFile)
    }

    fun importFile(
        request: FileImportRequest,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        onBytesCopied: (Long) -> Unit = {}
    ): FileImportResult {
        val fileType = FileTypeDetector.detect(request.displayName, request.mimeType)
            ?: return FileImportResult.Failure(
                ImportError.UnsupportedFileType(request.displayName, request.mimeType)
            )

        val limitBytes = store.resourcePolicy.maxSourceBytes(fileType)
        val declaredSizeBytes = request.declaredSizeBytes
        if (declaredSizeBytes != null && declaredSizeBytes > limitBytes) {
            return FileImportResult.Failure(
                ImportError.FileTooLarge(fileType, declaredSizeBytes, limitBytes)
            )
        }

        cancellationToken.throwIfCancelled()

        val inputStream = try {
            request.openInputStream()
        } catch (error: FileTaskCancelledException) {
            throw error
        } catch (error: Exception) {
            return FileImportResult.Failure(ImportError.CopyFailed(error.message.orEmpty()))
        } ?: return FileImportResult.Failure(ImportError.CannotReadSource)

        return try {
            FileImportResult.Success(
                store.save(
                    fileType = fileType,
                    displayName = request.displayName,
                    sourceKind = request.sourceKind,
                    inputStream = inputStream,
                    cancellationToken = cancellationToken,
                    onBytesCopied = onBytesCopied
                )
            )
        } catch (error: FileTaskCancelledException) {
            throw error
        } catch (error: SourceFileTooLargeException) {
            FileImportResult.Failure(
                ImportError.FileTooLarge(fileType, error.actualBytes, error.limitBytes)
            )
        } catch (error: EmptyImportException) {
            FileImportResult.Failure(ImportError.EmptyFile)
        } catch (error: Exception) {
            FileImportResult.Failure(ImportError.CopyFailed(error.message.orEmpty()))
        }
    }
}
