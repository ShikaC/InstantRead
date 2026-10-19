package com.personalai.instantread.settings

import com.personalai.instantread.preview.ReadingComfort
import com.personalai.instantread.preview.ReadingDisplayOptions

data class PreviewSettings(
    val isDarkTheme: Boolean = false,
    val readingComfort: ReadingComfort = ReadingComfort.STANDARD,
    val readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions.DEFAULT
)
