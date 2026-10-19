package com.personalai.instantread.settings

import android.app.Application
import com.personalai.instantread.preview.ReadingDisplayOptions
import com.personalai.instantread.preview.ReadingComfort
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewSettingsRepositoryTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("preview-settings", 0).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("preview-settings", 0).edit().clear().commit()
    }

    @Test
    fun retiredGlobalJavaScriptPreferenceIsIgnored() {
        // 旧版本用全局开关控制 HTML/ZIP 脚本；现在脚本改为按文档授权，
        // 遗留的偏好值不再被读取，也不会让任何已导入文档自动获得脚本权限。
        context.getSharedPreferences("preview-settings", 0)
            .edit()
            .putBoolean("javascript-for-html", true)
            .commit()

        assertEquals(PreviewSettings(), PreviewSettingsRepository(context).getSettings())
    }

    @Test
    fun darkThemeIsDisabledByDefault() {
        val repository = PreviewSettingsRepository(context)

        assertFalse(repository.getSettings().isDarkTheme)
        assertTrue(repository.getSettings().readingComfort == ReadingComfort.STANDARD)
        assertEquals(ReadingDisplayOptions.DEFAULT, repository.getSettings().readingDisplayOptions)
    }

    @Test
    fun darkThemeCanBePersisted() {
        PreviewSettingsRepository(context).setDarkTheme(true)

        assertTrue(PreviewSettingsRepository(context).getSettings().isDarkTheme)

        PreviewSettingsRepository(context).setDarkTheme(false)

        assertFalse(PreviewSettingsRepository(context).getSettings().isDarkTheme)
    }

    @Test
    fun readingComfortCanBePersisted() {
        PreviewSettingsRepository(context).setReadingComfort(ReadingComfort.COMFORTABLE)

        assertTrue(PreviewSettingsRepository(context).getSettings().readingComfort == ReadingComfort.COMFORTABLE)

        PreviewSettingsRepository(context).setReadingComfort(ReadingComfort.COMPACT)

        assertTrue(PreviewSettingsRepository(context).getSettings().readingComfort == ReadingComfort.COMPACT)
    }

    @Test
    fun readingDisplayOptionsCanBePersisted() {
        val options = ReadingDisplayOptions(
            fontSizeSp = 21,
            letterSpacingEm = 0.04f,
            lineHeight = 2.05f,
            paragraphSpacingEm = 1.08f
        )

        PreviewSettingsRepository(context).setReadingDisplayOptions(options)

        assertEquals(options, PreviewSettingsRepository(context).getSettings().readingDisplayOptions)
    }

    @Test
    fun readingDisplayOptionsAreClampedBeforePersisting() {
        PreviewSettingsRepository(context).setReadingDisplayOptions(
            ReadingDisplayOptions(
                fontSizeSp = 99,
                letterSpacingEm = 1f,
                lineHeight = 9f,
                paragraphSpacingEm = 9f
            )
        )

        assertEquals(
            ReadingDisplayOptions(
                fontSizeSp = 24,
                letterSpacingEm = 0.12f,
                lineHeight = 2.3f,
                paragraphSpacingEm = 1.4f
            ),
            PreviewSettingsRepository(context).getSettings().readingDisplayOptions
        )

        PreviewSettingsRepository(context).setReadingDisplayOptions(
            ReadingDisplayOptions(
                fontSizeSp = 1,
                letterSpacingEm = -1f,
                lineHeight = 0.1f,
                paragraphSpacingEm = 0.1f
            )
        )

        assertEquals(
            ReadingDisplayOptions(
                fontSizeSp = 13,
                letterSpacingEm = -0.02f,
                lineHeight = 1.3f,
                paragraphSpacingEm = 0.4f
            ),
            PreviewSettingsRepository(context).getSettings().readingDisplayOptions
        )
    }

    @Test
    fun legacyReadingComfortMigratesToReadingDisplayOptionsWhenCustomFieldsAreMissing() {
        context.getSharedPreferences("preview-settings", 0)
            .edit()
            .putString("reading-comfort", ReadingComfort.COMFORTABLE.name)
            .commit()

        assertEquals(
            ReadingDisplayOptions(
                fontSizeSp = 19,
                letterSpacingEm = 0f,
                lineHeight = 1.9f,
                paragraphSpacingEm = 0.92f
            ),
            PreviewSettingsRepository(context).getSettings().readingDisplayOptions
        )
    }

    @Test
    fun readingDisplayOptionsCanBeResetToDefault() {
        val repository = PreviewSettingsRepository(context)
        repository.setReadingDisplayOptions(
            ReadingDisplayOptions(
                fontSizeSp = 21,
                letterSpacingEm = 0.04f,
                lineHeight = 2.05f,
                paragraphSpacingEm = 1.08f
            )
        )

        repository.resetReadingDisplayOptions()

        assertEquals(ReadingDisplayOptions.DEFAULT, repository.getSettings().readingDisplayOptions)
    }
}
