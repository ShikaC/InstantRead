package com.personalai.instantread.settings

import android.content.Context
import com.personalai.instantread.preview.ReadingComfort
import com.personalai.instantread.preview.ReadingDisplayOptions

class PreviewSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSettings(): PreviewSettings {
        val legacyComfort = ReadingComfort.fromStoredValue(preferences.getString(KEY_READING_COMFORT, null))
        return PreviewSettings(
            isDarkTheme = preferences.getBoolean(KEY_DARK_THEME, false),
            readingComfort = legacyComfort,
            readingDisplayOptions = readReadingDisplayOptions(legacyComfort)
        )
    }

    fun setDarkTheme(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_DARK_THEME, enabled)
            .apply()
    }

    fun setReadingComfort(comfort: ReadingComfort) {
        preferences.edit()
            .putString(KEY_READING_COMFORT, comfort.name)
            .apply()
    }

    fun setReadingDisplayOptions(options: ReadingDisplayOptions) {
        val clamped = options.clamped()
        preferences.edit()
            .putInt(KEY_READING_FONT_SIZE_SP, clamped.fontSizeSp)
            .putFloat(KEY_READING_LETTER_SPACING_EM, clamped.letterSpacingEm)
            .putFloat(KEY_READING_LINE_HEIGHT, clamped.lineHeight)
            .putFloat(KEY_READING_PARAGRAPH_SPACING_EM, clamped.paragraphSpacingEm)
            .apply()
    }

    fun resetReadingDisplayOptions() {
        setReadingDisplayOptions(ReadingDisplayOptions.DEFAULT)
    }

    private fun readReadingDisplayOptions(legacyComfort: ReadingComfort): ReadingDisplayOptions {
        val fallback = ReadingDisplayOptions.fromLegacyComfort(legacyComfort)
        return ReadingDisplayOptions(
            fontSizeSp = preferences.getInt(KEY_READING_FONT_SIZE_SP, fallback.fontSizeSp),
            letterSpacingEm = preferences.getFloat(KEY_READING_LETTER_SPACING_EM, fallback.letterSpacingEm),
            lineHeight = preferences.getFloat(KEY_READING_LINE_HEIGHT, fallback.lineHeight),
            paragraphSpacingEm = preferences.getFloat(KEY_READING_PARAGRAPH_SPACING_EM, fallback.paragraphSpacingEm)
        ).clamped()
    }

    private companion object {
        const val PREFERENCES_NAME = "preview-settings"
        const val KEY_DARK_THEME = "dark-theme"
        const val KEY_READING_COMFORT = "reading-comfort"
        const val KEY_READING_FONT_SIZE_SP = "reading-font-size-sp"
        const val KEY_READING_LETTER_SPACING_EM = "reading-letter-spacing-em"
        const val KEY_READING_LINE_HEIGHT = "reading-line-height"
        const val KEY_READING_PARAGRAPH_SPACING_EM = "reading-paragraph-spacing-em"
    }
}
