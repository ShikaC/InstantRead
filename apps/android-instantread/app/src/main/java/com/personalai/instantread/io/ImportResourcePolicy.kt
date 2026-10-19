package com.personalai.instantread.io

import com.personalai.instantread.importing.FileType

data class ImportResourcePolicy(
    val markdownMaxBytes: Long = 10L * MIB,
    val textMaxBytes: Long = 10L * MIB,
    val htmlMaxBytes: Long = 50L * MIB,
    val zipSourceMaxBytes: Long = 100L * MIB,
    val maxZipEntryCount: Int = 5_000,
    val maxZipEntryBytes: Long = 50L * MIB,
    val maxZipExpandedBytes: Long = 250L * MIB,
    val maxImportIdLength: Int = 128
) {
    init {
        require(markdownMaxBytes >= 0L) { "markdownMaxBytes must be non-negative" }
        require(textMaxBytes >= 0L) { "textMaxBytes must be non-negative" }
        require(htmlMaxBytes >= 0L) { "htmlMaxBytes must be non-negative" }
        require(zipSourceMaxBytes >= 0L) { "zipSourceMaxBytes must be non-negative" }
        require(maxZipEntryCount >= 0) { "maxZipEntryCount must be non-negative" }
        require(maxZipEntryBytes >= 0L) { "maxZipEntryBytes must be non-negative" }
        require(maxZipExpandedBytes >= 0L) { "maxZipExpandedBytes must be non-negative" }
        require(maxImportIdLength > 0) { "maxImportIdLength must be positive" }
    }

    fun maxSourceBytes(fileType: FileType): Long = when (fileType) {
        FileType.MARKDOWN -> markdownMaxBytes
        FileType.TEXT -> textMaxBytes
        FileType.HTML -> htmlMaxBytes
        FileType.ZIP -> zipSourceMaxBytes
    }

    companion object {
        const val MIB: Long = 1024L * 1024L
        val DEFAULT = ImportResourcePolicy()
    }
}
