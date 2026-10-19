package com.personalai.instantread.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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
    fun settingsExplainThatCacheCleanupKeepsImportedSourceFiles() {
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
        assertTrue(text.contains("最近记录、收藏和导入源文件都会保留"))
        assertTrue(text.contains("下次打开 ZIP 会自动重新解包"))
        assertTrue(text.contains("卸载应用或清除应用数据会移除全部记录、应用内副本和缓存，但不会删除手机里的原始文件。"))
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

        assertTrue(text.contains("ZIP 解包缓存：2 个目录，3 个文件，128 B"))
        assertTrue(text.contains("已清理 ZIP 解包缓存：2 个目录，3 个文件，128 B。"))
        assertTrue(text.contains("清理只删除已解包资源，最近记录、收藏和导入源文件都会保留。"))
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
}
