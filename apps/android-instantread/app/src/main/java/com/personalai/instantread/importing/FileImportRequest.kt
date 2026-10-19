package com.personalai.instantread.importing

import java.io.InputStream

data class FileImportRequest(
    val displayName: String?,
    val mimeType: String?,
    val sourceKind: SourceKind,
    val openInputStream: () -> InputStream?,
    val providerMimeType: String? = mimeType,
    val intentMimeType: String? = null,
    val entryAction: String? = null,
    val entryUriSource: String? = null,
    val declaredSizeBytes: Long? = null
)
