package com.personalai.instantread.history

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.SourceKind
import java.io.File

data class PreviewHistory(
    val id: String,
    val displayName: String?,
    val fileType: FileType,
    val sourceKind: SourceKind,
    val localFile: File,
    val createdAt: Long,
    val lastOpenedAt: Long,
    val favorite: Boolean,
    val jsAllowed: Boolean
)
