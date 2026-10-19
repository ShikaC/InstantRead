package com.personalai.instantread.preview

import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.io.FileTaskCancellationToken

interface ZipPreviewRenderer {
    fun render(
        importedFile: ImportedFile,
        options: PreviewOptions = PreviewOptions(),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): PreviewModel

    fun prepare(
        importedFile: ImportedFile,
        options: PreviewOptions = PreviewOptions(),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): PreviewModel = render(importedFile, options, cancellationToken)
}
