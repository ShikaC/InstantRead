package com.personalai.instantread.importing

object FileTypeDetector {
    fun detect(displayName: String?, mimeType: String?): FileType? {
        val extension = displayName
            ?.trim()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        return when (extension) {
            "html", "htm" -> FileType.HTML
            "md", "markdown" -> FileType.MARKDOWN
            "txt" -> FileType.TEXT
            "zip" -> FileType.ZIP
            else -> detectByMimeType(mimeType)
        }
    }

    private fun detectByMimeType(mimeType: String?): FileType? {
        val normalizedMimeType = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()

        return when (normalizedMimeType) {
            "text/html", "application/xhtml+xml" -> FileType.HTML
            "text/markdown", "text/x-markdown" -> FileType.MARKDOWN
            "text/plain" -> FileType.TEXT
            "application/zip", "application/x-zip", "application/x-zip-compressed" -> FileType.ZIP
            else -> null
        }
    }
}
