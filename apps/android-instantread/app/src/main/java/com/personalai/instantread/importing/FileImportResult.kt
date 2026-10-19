package com.personalai.instantread.importing

sealed class FileImportResult {
    data class Success(val importedFile: ImportedFile) : FileImportResult()
    data class Failure(val error: ImportError) : FileImportResult()
}

sealed class ImportError {
    data class UnsupportedFileType(
        val displayName: String?,
        val mimeType: String?
    ) : ImportError()

    data object CannotReadSource : ImportError()
    data object EmptyFile : ImportError()
    data class FileTooLarge(
        val fileType: FileType,
        val actualBytes: Long,
        val limitBytes: Long
    ) : ImportError()
    data class CopyFailed(val message: String) : ImportError()
}
