package com.personalai.instantread.preview

data class ReadingDisplayOptions(
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val letterSpacingEm: Float = DEFAULT_LETTER_SPACING_EM,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val paragraphSpacingEm: Float = DEFAULT_PARAGRAPH_SPACING_EM
) {
    fun clamped(): ReadingDisplayOptions {
        return copy(
            fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
            letterSpacingEm = letterSpacingEm.coerceIn(MIN_LETTER_SPACING_EM, MAX_LETTER_SPACING_EM),
            lineHeight = lineHeight.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT),
            paragraphSpacingEm = paragraphSpacingEm.coerceIn(MIN_PARAGRAPH_SPACING_EM, MAX_PARAGRAPH_SPACING_EM)
        )
    }

    companion object {
        const val MIN_FONT_SIZE_SP = 13
        const val MAX_FONT_SIZE_SP = 24
        const val DEFAULT_FONT_SIZE_SP = 17

        const val MIN_LETTER_SPACING_EM = -0.02f
        const val MAX_LETTER_SPACING_EM = 0.12f
        const val DEFAULT_LETTER_SPACING_EM = 0f

        const val MIN_LINE_HEIGHT = 1.3f
        const val MAX_LINE_HEIGHT = 2.3f
        const val DEFAULT_LINE_HEIGHT = 1.75f

        const val MIN_PARAGRAPH_SPACING_EM = 0.4f
        const val MAX_PARAGRAPH_SPACING_EM = 1.4f
        const val DEFAULT_PARAGRAPH_SPACING_EM = 0.72f

        val DEFAULT = ReadingDisplayOptions()

        fun fromLegacyComfort(comfort: ReadingComfort): ReadingDisplayOptions {
            return when (comfort) {
                ReadingComfort.COMPACT -> ReadingDisplayOptions(
                    fontSizeSp = 15,
                    letterSpacingEm = 0f,
                    lineHeight = 1.55f,
                    paragraphSpacingEm = 0.55f
                )
                ReadingComfort.STANDARD -> DEFAULT
                ReadingComfort.COMFORTABLE -> ReadingDisplayOptions(
                    fontSizeSp = 19,
                    letterSpacingEm = 0f,
                    lineHeight = 1.9f,
                    paragraphSpacingEm = 0.92f
                )
            }
        }
    }
}
