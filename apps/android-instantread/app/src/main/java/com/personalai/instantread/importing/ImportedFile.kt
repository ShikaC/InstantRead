package com.personalai.instantread.importing

import java.io.File

data class ImportedFile(
    val id: String,
    val displayName: String?,
    val fileType: FileType,
    val sourceKind: SourceKind,
    val localFile: File,
    val sizeBytes: Long
)
