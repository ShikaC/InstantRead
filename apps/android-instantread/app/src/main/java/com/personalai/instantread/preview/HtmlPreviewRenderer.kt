package com.personalai.instantread.preview

import com.personalai.instantread.importing.ImportedFile

class HtmlPreviewRenderer {
    fun render(
        importedFile: ImportedFile,
        options: PreviewOptions = PreviewOptions()
    ): PreviewModel.WebFile {
        val documentFile = importedFile.localFile.canonicalFile
        return PreviewModel.WebFile(
            file = documentFile,
            title = importedFile.displayName ?: importedFile.localFile.name,
            javaScriptEnabled = options.javaScriptEnabledForHtml,
            // 单 HTML 只导入自身副本，兄弟资源不会一起复制；授权根就是该副本所在目录。
            resourceRoot = documentFile.parentFile
        )
    }
}
