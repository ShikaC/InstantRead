package com.personalai.instantread.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheCleanupCopyTest {
    @Test
    fun homeKeepsZipCacheCleanupOutOfTheFigmaHomeScreen() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = emptyList(),
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {}
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("选择本地文件"))
        assertTrue(!text.contains("清理 ZIP 解包缓存"))
        assertTrue(!text.contains("清理 ZIP 缓存"))
    }

    @Test
    fun homeMessageTextIsSelectableForDiagnosticsCopying() {
        val diagnostics = "sourceKind=ACTION_SEND\nscheme=content\nresult=success:HTML"
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = diagnostics,
            recentFiles = emptyList(),
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {}
            )
        )

        val messageView = view.textViewWithText(diagnostics)

        assertEquals(diagnostics, messageView.text.toString())
        assertTrue(messageView.isTextSelectable)
    }

    @Test
    fun homeShowsImportErrorMessageWithRecoveryActions() {
        val errorMessage = "无法读取文件。请重新选择文件，并确认来源应用仍允许访问该文件。"
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = errorMessage,
            recentFiles = emptyList(),
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {}
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains(errorMessage))
        assertTrue(text.contains("选择文件"))
        assertEquals(1, view.viewsWithTag("home-settings-button").size)
    }

    @Test
    fun settingsHelpExplainsThatCacheCleanupKeepsImportedSourceFiles() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = SettingsActions(
                onClearCache = {},
                onBack = {}
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("清理 ZIP 缓存"))
        assertTrue(text.contains("当前缓存"))
        assertFalse(text.contains("最近记录、收藏和导入源文件都会保留"))
        view.assertHelpDialog(
            tag = "settings-help-cache",
            expectedTitle = SettingsHelpCopy.CACHE_TITLE,
            expectedMessage = SettingsHelpCopy.CACHE_MESSAGE
        )
    }

    @Test
    fun settingsShowZipCacheSummaryAndCleanupResult() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = "已清理 ZIP 解包缓存：2 个目录，3 个文件，128 B。",
                zipCacheSummary = "ZIP 解包缓存：2 个目录，3 个文件，128 B"
            ),
            actions = SettingsActions(
                onClearCache = {},
                onBack = {}
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("可用"))
        assertTrue(text.contains("已清理 ZIP 解包缓存：2 个目录，3 个文件，128 B。"))
        assertFalse(text.contains("清理只删除已解包资源"))
        view.assertHelpDialog(
            tag = "settings-help-cache",
            expectedTitle = SettingsHelpCopy.CACHE_TITLE,
            expectedMessage = SettingsHelpCopy.CACHE_MESSAGE
        )
    }

    private fun View.visibleText(): List<String> {
        val result = mutableListOf<String>()
        fun collect(view: View) {
            if (view is TextView) {
                result.add(view.text.toString())
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }
        collect(this)
        return result
    }

    private fun View.textViewWithText(expected: String): TextView {
        if (this is TextView && text.toString() == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findTextViewWithText(expected)
                if (found != null) return found
            }
        }
        throw AssertionError("TextView not found: $expected")
    }

    private fun View.findTextViewWithText(expected: String): TextView? {
        if (this is TextView && text.toString() == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findTextViewWithText(expected)
                if (found != null) return found
            }
        }
        return null
    }

    private fun View.viewsWithTag(expected: String): List<View> {
        val result = mutableListOf<View>()
        fun collect(view: View) {
            if (view.tag == expected) {
                result.add(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }
        collect(this)
        return result
    }

    private fun View.assertHelpDialog(
        tag: String,
        expectedTitle: String,
        expectedMessage: String
    ) {
        ShadowAlertDialog.reset()
        val helpButton = viewsWithTag(tag).single()
        helpButton.performClick()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Expected a help dialog for $tag.", dialog)
        val shadowDialog = shadowOf(dialog)
        assertEquals(expectedTitle, shadowDialog.title.toString())
        assertEquals(expectedMessage, shadowDialog.message.toString())
        dialog.dismiss()
    }
}
