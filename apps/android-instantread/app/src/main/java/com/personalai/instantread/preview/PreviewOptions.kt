package com.personalai.instantread.preview

import com.personalai.instantread.PreviewDocumentMode

data class PreviewOptions(
    val javaScriptEnabledForHtml: Boolean = false,
    val markdownDocumentMode: PreviewDocumentMode = PreviewDocumentMode.READING,
    val readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions.DEFAULT,
    val generatedContentDarkTheme: Boolean = false
)
