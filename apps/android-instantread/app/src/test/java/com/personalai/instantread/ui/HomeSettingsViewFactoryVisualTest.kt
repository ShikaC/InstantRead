package com.personalai.instantread.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.personalai.instantread.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class HomeSettingsViewFactoryVisualTest {
    @Test
    fun homeMatchesFigmaTopBarActionPrivacyRecentAndFooter() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("即阅"))
        assertTrue(text.contains("本地预览"))
        assertFalse(text.contains("☾"))
        assertFalse(text.contains("⚙"))
        assertTrue(text.contains("+  选择本地文件"))
        assertTrue(text.contains("仅在本机解析"))
        assertTrue(text.contains("不上传 · 无网络权限"))
        assertTrue(text.contains("最近记录"))
        assertTrue(text.contains("全部"))
        assertTrue(text.contains("收藏"))
        assertTrue(text.contains("sample.html"))
        assertTrue(text.contains("HTML"))
        assertFalse(text.contains("HTML · Ready for preview"))
        assertTrue(text.contains("sample.md"))
        assertTrue(text.contains("MARKDOWN"))
        assertFalse(text.contains("MARKDOWN · Ready for preview"))
        assertTrue(text.contains("sample.txt"))
        assertTrue(text.contains("TEXT"))
        assertFalse(text.contains("TEXT · Ready for preview"))
        assertTrue(text.contains("sample-site.zip"))
        assertTrue(text.contains("unsafe-traversal.zip"))
        assertTrue(text.contains("UNSAFE"))
        assertTrue(text.contains("版本 ${BuildConfig.VERSION_NAME} • 系统安全解析已开启"))
        assertFalse(text.contains("设置与隐私"))
        assertFalse(text.contains("选择要预览的文件"))
        assertFalse(text.contains("Privacy Protected"))
        assertFalse(text.contains("☀"))
        assertEquals("夜间模式", view.singleViewWithTag("home-theme-button").contentDescription)
        assertHeightDp(59, view.singleViewWithTag("home-app-bar"))
        assertHeightDp(52, view.singleViewWithTag("home-primary-action"))
        assertHeightDp(26, view.singleViewWithTag("home-privacy-row"))
        assertEquals(1, view.viewsWithTag("home-recent-list").size)
        assertEquals(1, view.viewsWithTag("home-theme-button").size)
        assertEquals(1, view.viewsWithTag("home-settings-button").size)
        assertEquals(0, view.viewsWithTag("home-footer-spacer").size)
        assertEquals(1, view.viewsWithTag("home-footer").size)
        assertEquals(5, view.viewsWithTag("recent-row").size)
        assertEquals(5, view.viewsWithTag("recent-file-icon").size)
        view.viewsWithTag("recent-row").forEach { row ->
            assertHeightDp(73, row)
        }
        assertEquals(1, view.viewsWithTag("recent-warning-badge").size)
        assertTextSizeSp(20f, view.textViewWithText("即阅"))
        assertTextSizeSp(10.5f, view.textViewWithText("本地预览"))
        assertTextSizeSp(15f, view.textViewWithText("+  选择本地文件"))
        assertTextSizeSp(11.5f, view.textViewWithText("仅在本机解析"))
        assertTextSizeSp(12f, view.textViewWithText("最近记录"))
        assertEquals(1, view.viewsWithTag("home-filter-all").size)
        assertEquals(1, view.viewsWithTag("home-filter-favorites").size)
        assertFalse("Figma home does not use format chips as primary structure.", text.contains("本地优先的 HTML"))
        assertFalse("ZIP cache cleanup belongs on settings in the Figma design.", text.contains("清理 ZIP 解包缓存"))
        assertNotNull("Home should use an intentional page background.", view.background)
    }

    @Test
    fun homeTopActionButtonsKeepTapSpacing() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            actions = emptyHomeActions()
        )

        val settingsParams = view
            .singleViewWithTag("home-settings-button")
            .layoutParams as LinearLayout.LayoutParams

        assertEquals(
            "Settings should sit a little away from the theme toggle to reduce accidental taps.",
            dp(8),
            settingsParams.leftMargin
        )
    }

    @Test
    fun homeRendersRecentFilesAsDividerRowsWithTrailingActions() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(
                RecentFileItem(
                    id = "zip-1",
                    title = "sample-site.zip",
                    fileTypeLabel = "ZIP",
                    isFavorite = true,
                    warningLabel = null
                )
            ),
            isDarkTheme = false,
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")
        val rows = view.viewsWithTag("recent-row")

        assertTrue(text.contains("最近记录"))
        assertTrue(text.contains("sample-site.zip"))
        assertTrue(text.contains("ZIP"))
        assertFalse(text.contains("ZIP · Ready for preview"))
        assertFalse(text.contains("★"))
        assertFalse(text.contains("☆"))
        assertFalse(text.contains("🗑︎"))
        assertEquals(1, view.viewsWithTag("recent-file-icon").size)
        assertEquals(1, view.viewsWithTag("recent-favorite-button").size)
        assertEquals(1, view.viewsWithTag("recent-delete-button").size)
        assertEquals("Each recent file should render as one Figma-style divider row.", 1, rows.size)
        assertHeightDp(73, rows.single())
        assertEquals("The previous rounded-card treatment should be removed.", 0, view.viewsWithTag("recent-card").size)
        assertNotNull(rows.single().background)
    }

    @Test
    fun homeEmptyRecentStateDoesNotShowPreviewStatus() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = emptyList(),
            isDarkTheme = false,
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("暂无最近记录"))
        assertFalse(text.contains("Ready for preview"))
    }

    @Test
    fun homeRecentFilterControlsInvokeActions() {
        val selected = mutableListOf<HomeListFilter>()
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            selectedFilter = HomeListFilter.ALL,
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {},
                onFilterChanged = { selected += it }
            )
        )

        view.singleViewWithTag("home-filter-favorites").performClick()
        view.singleViewWithTag("home-filter-all").performClick()

        assertEquals(listOf(HomeListFilter.FAVORITES, HomeListFilter.ALL), selected)
        assertEquals(dp(52), view.singleViewWithTag("home-filter-all").minimumWidth)
        assertEquals(dp(24), view.singleViewWithTag("home-filter-all").minimumHeight)
        assertEquals(dp(52), view.singleViewWithTag("home-filter-favorites").minimumWidth)
        assertEquals(dp(24), view.singleViewWithTag("home-filter-favorites").minimumHeight)
    }

    @Test
    fun homeRecentFilterButtonsShowAvailableCounts() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            totalRecentCount = 5,
            favoriteRecentCount = 1,
            actions = emptyHomeActions()
        )

        val all = view.singleViewWithTag("home-filter-all") as TextView
        val favorites = view.singleViewWithTag("home-filter-favorites") as TextView

        assertEquals("全部 5", all.text.toString())
        assertEquals("收藏 1", favorites.text.toString())
        assertEquals("显示全部最近文件，5 个", all.contentDescription)
        assertEquals("显示收藏文件，1 个", favorites.contentDescription)
    }

    @Test
    fun homeShowsClearHistoryButtonOnlyWhenUnfavoritedRecordsExist() {
        val cleared = mutableListOf<String>()
        val actions = HomeActions(
            onChooseFile = {},
            onOpenSettings = {},
            onClearCache = {},
            onOpenHistory = {},
            onToggleFavorite = {},
            onDeleteHistory = {},
            onClearHistory = { cleared += "clear" }
        )
        val withClearable = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            clearableRecentCount = 4,
            actions = actions
        )
        val withoutClearable = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            clearableRecentCount = 0,
            actions = actions
        )

        assertEquals(1, withClearable.viewsWithTag("home-clear-history-button").size)
        assertEquals(0, withoutClearable.viewsWithTag("home-clear-history-button").size)
        assertEquals(
            "清空 4 条未收藏记录，收藏会保留",
            withClearable.singleViewWithTag("home-clear-history-button").contentDescription
        )
        withClearable.singleViewWithTag("home-clear-history-button").performClick()
        assertEquals(listOf("clear"), cleared)
    }

    @Test
    fun homeClearHistorySheetExplainsFavoritesAndOriginalFilesSurvive() {
        val confirmations = mutableListOf<String>()
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            clearableRecentCount = 4,
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {},
                onClearHistory = { confirmations += "clear" },
                onCancelClearHistory = { confirmations += "cancel" }
            ),
            pendingClearHistory = true
        )

        val sheet = view.singleViewWithTag("home-clear-sheet")
        val sheetText = sheet.visibleText().joinToString("\n")

        assertTrue(sheetText.contains("清空未收藏的记录？"))
        assertTrue(sheetText.contains("已收藏的文件和手机里的原始文件不会被删除"))
        assertEquals(0, view.viewsWithTag("home-delete-sheet").size)
        (sheet.viewsWithTag("home-clear-cancel").single() as Button).performClick()
        (sheet.viewsWithTag("home-clear-confirm").single() as Button).performClick()
        assertEquals(listOf("cancel", "clear"), confirmations)
    }

    @Test
    fun homeRecentSearchShowsQueryAndInvokesActions() {
        val queries = mutableListOf<String>()
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            searchQuery = "sample",
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {},
                onSearchQueryChanged = { queries += it }
            )
        )

        val input = view.singleViewWithTag("home-search-input") as EditText
        val text = view.visibleText().joinToString("\n")
        assertEquals("sample", input.text.toString())
        assertEquals("搜索最近记录", input.hint.toString())
        assertTrue(text.contains("最近记录 · 5"))
        assertEquals(1, view.viewsWithTag("home-search-clear-button").size)
        assertEquals("清空搜索", view.singleViewWithTag("home-search-clear-button").contentDescription)

        input.setText("zip")
        view.singleViewWithTag("home-search-clear-button").performClick()

        assertEquals(listOf("zip", ""), queries)
    }

    @Test
    fun homeRecentSearchHighlightsMatchingTitleAndType() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(
                RecentFileItem(
                    id = "zip",
                    title = "sample-site.zip",
                    fileTypeLabel = "ZIP",
                    isFavorite = false
                )
            ),
            isDarkTheme = false,
            searchQuery = "zip",
            actions = emptyHomeActions()
        )

        assertHasSearchHighlight(view.textViewWithText("sample-site.zip"))
        assertHasSearchHighlight(view.textViewWithText("ZIP"))
    }

    @Test
    fun homeRecentSearchCanBeCancelledExplicitly() {
        val queries = mutableListOf<String>()
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = false,
            searchQuery = "zip",
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {},
                onSearchQueryChanged = { queries += it }
            )
        )

        val cancel = view.singleViewWithTag("home-search-cancel-button") as TextView

        assertEquals("取消", cancel.text.toString())
        assertEquals("退出搜索", cancel.contentDescription)

        cancel.performClick()

        assertEquals(listOf(""), queries)
    }

    @Test
    fun homeRecentSearchEmptyStateUsesSpecificCopy() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = emptyList(),
            isDarkTheme = false,
            searchQuery = "missing",
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("没有匹配的记录"))
        assertFalse(text.contains("暂无最近记录"))
        assertFalse(text.contains("暂无收藏文件"))
    }

    @Test
    fun homeFavoritesSearchEmptyStateUsesSpecificCopyAndCount() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = emptyList(),
            isDarkTheme = false,
            selectedFilter = HomeListFilter.FAVORITES,
            searchQuery = "missing",
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("收藏文件 · 0"))
        assertTrue(text.contains("收藏中没有匹配记录"))
        assertFalse(text.contains("没有匹配的记录"))
        assertFalse(text.contains("暂无收藏文件"))
    }

    @Test
    fun homeFavoritesFilterOnlyRendersFavoritedRows() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(
                RecentFileItem(
                    id = "favorite",
                    title = "favorite.html",
                    fileTypeLabel = "HTML",
                    isFavorite = true
                )
            ),
            isDarkTheme = false,
            selectedFilter = HomeListFilter.FAVORITES,
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("收藏文件"))
        assertTrue(text.contains("favorite.html"))
        assertFalse(text.contains("sample.md"))
        assertEquals(1, view.viewsWithTag("recent-row").size)
    }

    @Test
    fun homeFavoritesFilterEmptyStateUsesSpecificCopy() {
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = emptyList(),
            isDarkTheme = false,
            selectedFilter = HomeListFilter.FAVORITES,
            actions = emptyHomeActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("收藏文件"))
        assertTrue(text.contains("暂无收藏文件"))
        assertFalse(text.contains("暂无最近记录"))
    }

    @Test
    fun homeThemeButtonUsesPrototypeVectorIcons() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val themeButtonSource = source
            .substringAfter("private fun createThemeButton")
            .substringBefore("private fun createSettingsButton")
        val moonVector = File("src/main/res/drawable/ic_instantread_moon.xml").readText()
        val sunVector = File("src/main/res/drawable/ic_instantread_sun.xml").readText()

        assertTrue(source.contains("tag = \"home-theme-button\""))
        assertTrue(
            "Theme button should use the shared prototype moon and sun vectors.",
            themeButtonSource.contains("InstantIcons.Sun") &&
                themeButtonSource.contains("InstantIcons.Moon")
        )
        assertFalse(
            "Theme icon should not fall back to a page-local Canvas approximation.",
            themeButtonSource.contains("moonCutout") || themeButtonSource.contains("drawSunIcon")
        )
        assertTrue(
            "Moon path should match the interface prototype exactly.",
            moonVector.contains("M15.2 11.4A6.5 6.5 0 0 1 6.6 2.8a6.5 6.5 0 1 0 8.6 8.6Z")
        )
        assertTrue(
            "Sun rays should match the interface prototype exactly.",
            sunVector.contains("M9 1.6v1.8M9 14.6v1.8M16.4 9h-1.8M3.4 9H1.6")
        )
    }

    @Test
    fun homeThemeButtonTogglesDarkModeAndShowsSunInDarkMode() {
        val clicks = mutableListOf<String>()
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = figmaRecentFiles(),
            isDarkTheme = true,
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = {},
                onToggleTheme = { clicks += "theme" }
            )
        )
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val themeButtonSource = source
            .substringAfter("private fun createThemeButton")
            .substringBefore("private fun createSettingsButton")

        assertEquals("日间模式", view.singleViewWithTag("home-theme-button").contentDescription)
        assertNotNull("Dark home should keep an intentional page background.", view.background)
        assertTrue(themeButtonSource.contains("InstantIcons.Sun"))

        view.singleViewWithTag("home-theme-button").performClick()

        assertEquals(listOf("theme"), clicks)
    }

    @Test
    fun homeSettingsButtonUsesGearSilhouetteInsteadOfSunRays() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val settingsButtonSource = source
            .substringAfter("private fun createSettingsButton")
            .substringBefore("private fun sheetButton")
        val gearVector = File("src/main/res/drawable/ic_instantread_gear.xml").readText()

        assertTrue(source.contains("tag = \"home-settings-button\""))
        assertFalse(
            "Settings icon should not use a page-local radial tooth approximation.",
            settingsButtonSource.contains("drawLine(") ||
                settingsButtonSource.contains("val outer = size * 0.19f")
        )
        assertTrue(
            "Settings button should use the shared prototype gear vector.",
            settingsButtonSource.contains("InstantIcons.Gear")
        )
        assertTrue(
            "Gear vector should keep the prototype center circle and outline path.",
            gearVector.contains("M8.9 12a3.1 3.1 0 1 0 6.2 0a3.1 3.1 0 1 0-6.2 0") &&
                gearVector.contains("M19.4 15a1.65 1.65 0 0 0 .33 1.82")
        )
    }

    @Test
    fun homeRecentActionsUsePrototypeVectorPaths() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val favoriteButtonSource = source
            .substringAfter("private fun favoriteIconButton")
            .substringBefore("private fun deleteIconButton")
        val deleteButtonSource = source
            .substringAfter("private fun deleteIconButton")
            .substringBefore("private fun createDeleteSheet")
        val starVector = File("src/main/res/drawable/ic_instantread_star_outline.xml").readText()
        val filledStarVector = File("src/main/res/drawable/ic_instantread_star_filled.xml").readText()
        val trashVector = File("src/main/res/drawable/ic_instantread_trash.xml").readText()

        assertTrue(
            "Favorite action should reuse the shared prototype star vector and active fill.",
            favoriteButtonSource.contains("tag = \"recent-favorite-button\"") &&
                favoriteButtonSource.contains("InstantIcons.Star") &&
                favoriteButtonSource.contains("activeTint = theme.favoriteOn")
        )
        assertTrue(
            "Delete action should reuse the shared prototype trash vector and press color.",
            deleteButtonSource.contains("tag = \"recent-delete-button\"") &&
                deleteButtonSource.contains("InstantIcons.Trash") &&
                deleteButtonSource.contains("pressedTint = theme.deleteIcon")
        )
        assertTrue(
            "Star and trash paths should match the interface prototype exactly.",
            starVector.contains("M9 2.6l1.9 4 4.4.6-3.2 3 .8 4.3L9 12.5l-3.9 2 .8-4.3-3.2-3 4.4-.6L9 2.6Z") &&
                trashVector.contains("M3.2 4.6h11.6M7 4.6V3.1h4v1.5M5.2 4.6l.7 10.3h6.2l.7-10.3")
        )
        assertTrue(
            "Favorited star should use a filled vector rather than a semi-transparent approximation.",
            filledStarVector.contains("android:fillColor=\"#000000\"")
        )
    }

    @Test
    fun homeCanRenderFigmaDeleteConfirmationSheet() {
        val clicks = mutableListOf<String>()
        val pendingDelete = RecentFileItem(
            id = "zip-1",
            title = "sample-site.zip",
            fileTypeLabel = "ZIP",
            isFavorite = false,
            warningLabel = null
        )
        val view = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(pendingDelete),
            isDarkTheme = false,
            actions = HomeActions(
                onChooseFile = {},
                onOpenSettings = {},
                onClearCache = {},
                onOpenHistory = {},
                onToggleFavorite = {},
                onDeleteHistory = { clicks += "delete=$it" },
                onCancelDelete = { clicks += "cancel" },
                onToggleTheme = {}
            ),
            pendingDeleteFile = pendingDelete
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("删除这条最近记录？"))
        assertTrue(text.contains("会移除这条记录、即阅保存的应用内副本和对应缓存；手机里的原始文件不会被删除。"))
        assertEquals(1, view.viewsWithTag("home-delete-sheet").size)

        view.buttonWithText("取消").performClick()
        view.buttonWithText("删除").performClick()

        assertEquals(listOf("cancel", "delete=zip-1"), clicks)
    }

    @Test
    fun homeSheetButtonsAvoidPlatformShadow() {
        val pendingDelete = RecentFileItem(
            id = "zip-1",
            title = "sample-site.zip",
            fileTypeLabel = "ZIP",
            isFavorite = false,
            warningLabel = null
        )
        val deleteSheet = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(pendingDelete),
            isDarkTheme = false,
            actions = emptyHomeActions(),
            pendingDeleteFile = pendingDelete
        )
        val clearSheet = HomeViewFactory(RuntimeEnvironment.getApplication()).create(
            message = null,
            recentFiles = listOf(pendingDelete),
            isDarkTheme = false,
            clearableRecentCount = 1,
            actions = emptyHomeActions(),
            pendingClearHistory = true
        )
        val sheetButtons = listOf(
            deleteSheet.buttonWithText("取消"),
            deleteSheet.buttonWithText("删除"),
            clearSheet.singleViewWithTag("home-clear-cancel") as Button,
            clearSheet.singleViewWithTag("home-clear-confirm") as Button
        )

        sheetButtons.forEach { button ->
            assertNull(
                "确认抽屉按钮使用自定义圆角背景，不应保留会在抽屉边缘被裁切的平台阴影动画。",
                button.stateListAnimator
            )
            assertEquals(0f, button.elevation, 0f)
            assertEquals(0f, button.translationZ, 0f)
        }
    }

    @Test
    fun settingsMatchesReferenceCompactGroupedLayout() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = "已保存设置。",
                zipCacheSummary = "ZIP 解包缓存：2 个目录，3 个文件，128 B"
            ),
            actions = emptySettingsActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("设置与隐私"))
        assertTrue(text.contains("JAVASCRIPT 设置"))
        assertTrue(text.contains("JavaScript"))
        assertTrue(text.contains("按文档授权"))
        assertFalse(text.contains("HTML 与 ZIP 网页包中的脚本默认不执行。"))
        assertTrue(text.contains("ZIP 解包缓存"))
        assertTrue(text.contains("阅读显示"))
        assertTrue(text.contains("正文显示"))
        assertTrue(text.contains("在 Markdown 或 TXT 文件右上角点击 Aa"))
        assertTrue(text.contains("调整会全局保存"))
        assertFalse(text.contains("阅读设置"))
        assertFalse(text.contains("紧凑"))
        assertFalse(text.contains("标准"))
        assertFalse(text.contains("舒适"))
        assertTrue(text.contains("当前缓存"))
        assertTrue(text.contains("可用"))
        assertTrue(text.contains("清理 ZIP 缓存"))
        assertFalse(text.contains("清理只删除已解包资源"))
        assertTrue(text.contains("隐私与安全"))
        assertTrue(text.contains("文件仅在本机处理，不上传到云端。"))
        assertTrue(text.contains("最近记录和收藏保存在本机。"))
        assertTrue(text.contains("外部链接将交给系统浏览器打开"))
        assertFalse(text.contains("↗"))
        assertFalse(text.contains("盾"))
        assertFalse(text.contains("锁"))
        assertTrue(text.contains("版本 ${BuildConfig.VERSION_NAME}"))
        assertFalse(text.contains("GLIMPSE"))
        assertFalse(text.contains("LOCAL ENGINE"))
        assertFalse(text.contains("Build 20260617"))
        assertTrue(text.contains("已保存设置。"))
        assertFalse(text.contains("ZIP 解包缓存：2 个目录，3 个文件，128 B"))
        assertFalse(text.contains("本地预览引擎"))
        assertFalse(text.contains("安全默认"))
        assertFalse(text.contains("预览安全"))
        assertFalse(text.contains("存储与缓存"))
        assertFalse(text.contains("返回首页"))
        assertFalse(text.contains("清理缓存不会删除最近、收藏或导入的源 ZIP。"))
        assertEquals(1, view.viewsWithTag("settings-app-bar").size)
        assertHeightDp(49, view.singleViewWithTag("settings-app-bar"))
        assertEquals(0, view.viewsWithTag("settings-status-summary").size)
        assertEquals(4, view.viewsWithTag("settings-section-header").size)
        assertEquals(4, view.viewsWithTag("settings-section-block").size)
        assertEquals(0, view.viewsWithTag("settings-reading-compact").size)
        assertEquals(0, view.viewsWithTag("settings-reading-standard").size)
        assertEquals(0, view.viewsWithTag("settings-reading-comfortable").size)
        assertEquals(3, view.viewsWithTag("settings-info-row").size)
        assertEquals(3, view.viewsWithTag("settings-info-icon").size)
        assertEquals(0, view.viewsWithTag("settings-note-card").size)
        assertEquals(2, view.viewsWithTag("settings-primary-row").size)
        assertEquals(1, view.viewsWithTag("settings-help-javascript").size)
        assertEquals(0, view.viewsWithTag("settings-help-reading").size)
        assertEquals(1, view.viewsWithTag("settings-help-cache").size)
        assertEquals(0, view.viewsWithTag("settings-help-privacy").size)
        assertEquals(1, view.viewsWithTag("settings-clear-cache-button").size)
        assertEquals(1, view.viewsWithTag("settings-page-footer").size)
        assertNotNull("Settings should use an intentional page background.", view.background)
    }

    @Test
    fun settingsDarkThemeUsesDarkPageAndSurfaceColors() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = "已保存设置。",
                zipCacheSummary = "ZIP 解包缓存：2 个目录，3 个文件，128 B",
                isDarkTheme = true
            ),
            actions = emptySettingsActions()
        )

        assertEquals(
            Color.rgb(12, 12, 14),
            (view.background as ColorDrawable).color
        )
        assertEquals(
            Color.rgb(28, 28, 29),
            (view.singleViewWithTag("settings-app-bar").background as ColorDrawable).color
        )
        assertTrue(
            view.viewsWithTag("settings-section-block").all { section ->
                (section.background as ColorDrawable).color == Color.rgb(28, 28, 29)
            }
        )
        assertEquals(
            Color.rgb(244, 247, 251),
            view.textViewWithText("设置与隐私").currentTextColor
        )
    }

    @Test
    fun settingsQuestionMarkButtonsExposeSupportingCopyInDialogs() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = emptySettingsActions()
        )

        view.assertHelpDialog(
            tag = "settings-help-javascript",
            expectedTitle = SettingsHelpCopy.JAVASCRIPT_TITLE,
            expectedMessage = SettingsHelpCopy.JAVASCRIPT_MESSAGE
        )
        view.assertHelpDialog(
            tag = "settings-help-cache",
            expectedTitle = SettingsHelpCopy.CACHE_TITLE,
            expectedMessage = SettingsHelpCopy.CACHE_MESSAGE
        )
    }

    @Test
    fun settingsCacheSummaryCanUpdateAfterBackgroundStatsLoad() {
        val factory = SettingsViewFactory(RuntimeEnvironment.getApplication())
        val view = factory.create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "正在统计"
            ),
            actions = emptySettingsActions()
        )

        assertEquals(
            "正在统计",
            (view.singleViewWithTag("settings-zip-cache-summary") as TextView).text
        )

        assertTrue(
            factory.updateZipCacheSummary(
                view,
                "ZIP 解包缓存：2 个目录，3 个文件，128 B"
            )
        )

        assertEquals(
            "可用",
            (view.singleViewWithTag("settings-zip-cache-summary") as TextView).text
        )
    }

    @Test
    fun settingsHelpIconsUseCircleQuestionMarkVector() {
        val source = File("src/main/java/com/personalai/instantread/ui/SettingsViewFactory.kt").readText()
        val helpVector = File("src/main/res/drawable/ic_instantread_help.xml").readText()
        val linkVector = File("src/main/res/drawable/ic_instantread_external_link.xml").readText()
        val shieldVector = File("src/main/res/drawable/ic_instantread_shield.xml").readText()
        val archiveVector = File("src/main/res/drawable/ic_instantread_archive.xml").readText()

        assertTrue(
            "Settings should reuse the shared question-mark and privacy vectors.",
            source.contains("InstantIcons.Help") &&
                source.contains("InstantIcons.Shield") &&
                source.contains("InstantIcons.Archive") &&
                source.contains("InstantIcons.ExternalLink")
        )
        assertFalse(
            "Help icons should not fall back to page-local Canvas drawing.",
            source.contains("drawCircle(") || source.contains("drawText(\"?\")")
        )
        assertTrue(
            "The help icon should be a circle containing a question mark.",
            helpVector.contains("M9 2.5a6.5 6.5 0 1 0 0 13") &&
                helpVector.contains("M6.9 6.7a2.25 2.25 0 0 1 4.35.75")
        )
        assertTrue(
            "Privacy icons should keep the existing prototype vector paths.",
            linkVector.contains("M7.4 10.6a3 3 0 0 0 4.2 0l2.4-2.4a3 3 0 0 0-4.2-4.2l-1 1") &&
                shieldVector.contains("M6 1.2 10.2 2.6v3.6c0 2.6-1.7 4.6-4.2 5.6-2.5-1-4.2-3-4.2-5.6V2.6L6 1.2Z") &&
                archiveVector.contains("M3.4 6.2c0-.9.7-1.6 1.6-1.6h8c.9 0 1.6.7 1.6 1.6v6.4")
        )
    }

    @Test
    fun settingsZipCacheButtonAvoidsClippedBorderShadow() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = emptySettingsActions()
        )

        val button = view.singleViewWithTag("settings-clear-cache-button") as Button

        assertNull(
            "Custom bordered cleanup button should not keep the platform shadow animator that gets clipped in the compact row.",
            button.stateListAnimator
        )
        assertEquals(0f, button.elevation, 0f)
        assertEquals(dp(36), button.layoutParams.height)
    }

    @Test
    fun settingsExplainsReadingDisplayIsAdjustedInsideMarkdownAndTextFiles() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = emptySettingsActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("阅读显示"))
        assertTrue(text.contains("正文显示"))
        assertTrue(text.contains("右上角点击 Aa"))
        assertTrue(text.contains("字号"))
        assertTrue(text.contains("字距"))
        assertTrue(text.contains("行距"))
        assertTrue(text.contains("段距"))
        assertFalse(text.contains("影响 HTML"))
        assertFalse(text.contains("影响 ZIP"))
        assertFalse(text.contains("紧凑"))
        assertFalse(text.contains("标准"))
        assertFalse(text.contains("舒适"))
        assertEquals(0, view.viewsWithTag("settings-help-reading").size)
    }

    @Test
    fun settingsFooterOnlyShowsVersionNumber() {
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = emptySettingsActions()
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("版本 ${BuildConfig.VERSION_NAME}"))
        assertFalse(text.contains("GLIMPSE"))
        assertFalse(text.contains("LOCAL ENGINE"))
        assertFalse(text.contains("Build"))
    }

    @Test
    fun settingsAvoidsOverusingBoldText() {
        val source = File("src/main/java/com/personalai/instantread/ui/SettingsViewFactory.kt").readText()
        val rowTitleSource = source.substringAfter("private fun rowTitle").substringBefore("private fun statusBadge")
        val sectionTitleSource = source.substringAfter("private fun sectionTitle").substringBefore("private fun sectionBlock")
        val footerSource = source.substringAfter("private fun createFooter").substringBefore("private fun createMessage")
        val statusBadgeSource = source.substringAfter("private fun statusBadge").substringBefore("private fun transparentBackground")

        assertFalse("Row titles should not all be forced bold.", rowTitleSource.contains("Typeface.BOLD"))
        assertFalse("Section labels should rely on size, color, and spacing instead of bold weight.", sectionTitleSource.contains("Typeface.BOLD"))
        assertFalse("Footer should be quiet metadata, not bold display text.", footerSource.contains("Typeface.BOLD"))
        assertFalse("Status badges should not need bold weight.", statusBadgeSource.contains("Typeface.BOLD"))
    }

    @Test
    fun settingsControlsInvokeActions() {
        val clicks = mutableListOf<String>()
        val view = SettingsViewFactory(RuntimeEnvironment.getApplication()).create(
            state = SettingsViewState(
                message = null,
                zipCacheSummary = "ZIP 解包缓存：暂无"
            ),
            actions = SettingsActions(
                onClearCache = { clicks += "clear" },
                onBack = { clicks += "back" },
                onReadingComfortChanged = { clicks += "reading=${it.name}" }
            )
        )

        view.singleViewWithTag("settings-clear-cache-button").performClick()
        view.singleViewWithTag("settings-back-button").performClick()

        assertTrue(view.viewsWithTag("settings-reading-comfortable").isEmpty())
        assertEquals(listOf("clear", "back"), clicks)
    }

    private fun emptyHomeActions(): HomeActions {
        return HomeActions(
            onChooseFile = {},
            onOpenSettings = {},
            onClearCache = {},
            onOpenHistory = {},
            onToggleFavorite = {},
            onDeleteHistory = {},
            onCancelDelete = {},
            onToggleTheme = {}
        )
    }

    private fun emptySettingsActions(): SettingsActions {
        return SettingsActions(
            onClearCache = {},
            onBack = {}
        )
    }

    private fun figmaRecentFiles(): List<RecentFileItem> {
        return listOf(
            RecentFileItem(
                id = "html",
                title = "sample.html",
                fileTypeLabel = "HTML",
                isFavorite = false,
                warningLabel = null
            ),
            RecentFileItem(
                id = "md",
                title = "sample.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                warningLabel = null
            ),
            RecentFileItem(
                id = "txt",
                title = "sample.txt",
                fileTypeLabel = "TEXT",
                isFavorite = false,
                warningLabel = null
            ),
            RecentFileItem(
                id = "site",
                title = "sample-site.zip",
                fileTypeLabel = "ZIP",
                isFavorite = false,
                warningLabel = null
            ),
            RecentFileItem(
                id = "unsafe",
                title = "unsafe-traversal.zip",
                fileTypeLabel = "ZIP",
                isFavorite = false,
                warningLabel = "UNSAFE"
            )
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

    private fun View.singleViewWithTag(expected: String): View {
        val matches = viewsWithTag(expected)
        assertEquals("Expected one view tagged $expected.", 1, matches.size)
        return matches.single()
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

    private fun assertHeightDp(expected: Int, view: View) {
        val density = view.resources.displayMetrics.density
        val actual = (view.layoutParams.height / density).toInt()
        assertEquals("Unexpected height for ${view.tag}.", expected, actual)
    }

    private fun assertTextSizeSp(expected: Float, textView: TextView) {
        val actual = textView.textSize / textView.resources.displayMetrics.scaledDensity
        assertEquals("Unexpected text size for ${textView.text}.", expected, actual, 0.5f)
    }

    private fun assertHasSearchHighlight(textView: TextView) {
        val text = textView.text
        assertTrue("Expected highlighted text for ${textView.text}.", text is Spanned)
        val spanned = text as Spanned
        assertTrue(
            "Expected a search highlight span for ${textView.text}.",
            spanned.getSpans(0, spanned.length, BackgroundColorSpan::class.java).isNotEmpty()
        )
    }

    private fun dp(value: Int): Int {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun View.assertHelpDialog(
        tag: String,
        expectedTitle: String,
        expectedMessage: String
    ) {
        ShadowAlertDialog.reset()
        singleViewWithTag(tag).performClick()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Expected a help dialog for $tag.", dialog)
        val shadowDialog = shadowOf(dialog)
        assertEquals(expectedTitle, shadowDialog.title.toString())
        assertEquals(expectedMessage, shadowDialog.message.toString())
        dialog.dismiss()
    }

    private fun View.buttonWithText(expected: String): Button {
        if (this is Button && text.toString() == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findButtonWithText(expected)
                if (found != null) return found
            }
        }
        throw AssertionError("Button not found: $expected")
    }

    private fun View.findButtonWithText(expected: String): Button? {
        if (this is Button && text.toString() == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findButtonWithText(expected)
                if (found != null) return found
            }
        }
        return null
    }

}
