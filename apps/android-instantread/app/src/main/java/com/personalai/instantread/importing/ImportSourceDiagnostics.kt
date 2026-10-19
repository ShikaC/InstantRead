package com.personalai.instantread.importing

import android.net.Uri

object ImportSourceDiagnostics {
    fun format(
        uri: Uri,
        request: FileImportRequest,
        result: FileImportResult
    ): String {
        return listOf(
            "sourceKind=${request.sourceKind}",
            "entryAction=${request.entryAction.orNone()}",
            "entryUriSource=${request.entryUriSource.orNone()}",
            "scheme=${uri.scheme.orNone()}",
            "authority=${uri.authority.orNone()}",
            "pathSegments=${uri.pathSegments.size}",
            "lastPathExtension=${uri.lastPathSegment.extensionOrNone()}",
            "displayName=${request.displayName.orNone()}",
            "displayExtension=${request.displayName.extensionOrNone()}",
            "intentMime=${request.intentMimeType.orNone()}",
            "providerMime=${request.providerMimeType.orNone()}",
            "resolvedMime=${request.mimeType.orNone()}",
            "result=${result.label()}"
        ).joinToString("\n")
    }

    private fun String?.extensionOrNone(): String {
        return this
            ?.substringAfterLast('/', missingDelimiterValue = this)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: "<none>"
    }

    private fun String?.orNone(): String = this?.takeIf { it.isNotBlank() } ?: "<none>"

    private fun FileImportResult.label(): String {
        return when (this) {
            is FileImportResult.Success -> "success:${importedFile.fileType}"
            is FileImportResult.Failure -> "failure:${error.label()}"
        }
    }

    private fun ImportError.label(): String {
        return when (this) {
            ImportError.CannotReadSource -> "CannotReadSource"
            ImportError.EmptyFile -> "EmptyFile"
            is ImportError.CopyFailed -> "CopyFailed"
            is ImportError.FileTooLarge -> "FileTooLarge"
            is ImportError.UnsupportedFileType -> "UnsupportedFileType"
        }
    }
}
