package com.personalai.instantread.importing

import android.net.Uri
import com.personalai.instantread.io.FileTaskCancellationToken

interface ImportRequestFactory {
    fun fromUri(
        uri: Uri,
        sourceKind: SourceKind,
        intentMimeType: String? = null,
        entryAction: String? = null,
        entryUriSource: String? = null,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): FileImportRequest
}
