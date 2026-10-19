package com.personalai.instantread.ui

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
import org.robolectric.annotation.Config

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
        assertTrue(text.contains("LOCAL PREVIEW"))
        assertFalse(text.contains("☾"))
        assertFalse(text.contains("⚙"))
        assertTrue(text.contains("+  选择本地文件"))
        assertTrue(text.contains("PRIVACY PROTECTED"))
        assertTrue(text.contains("文件仅在本地解析"))
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
        assertHeightDp(21, view.singleViewWithTag("home-privacy-row"))
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
        assertTextSizeSp(8f, view.textViewWithText("LOCAL PREVIEW"))
        assertTextSizeSp(15f, view.textViewWithText("+  选择本地文件"))
        assertTextSizeSp(8f, view.textViewWithText("⊙  PRIVACY PROTECTED"))
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
    fun homeThemeButtonUsesCleanCrescentWithoutAccentDot() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val themeButtonSource = source
            .substringAfter("private fun createThemeButton")
            .substringBefore("private fun createSettingsButton")

        assertTrue(source.contains("tag = \"home-theme-button\""))
        assertFalse(
            "Moon icon should follow the reference image as a clean crescent, without the previous decorative dot.",
            themeButtonSource.contains("dotRadius") || themeButtonSource.contains("private val dot = Paint")
        )
        assertFalse(
            "Moon icon should be drawn as filled negative space, not stitched together from arc segments.",
            themeButtonSource.contains("addArc(") || themeButtonSource.contains("drawPath(crescent")
        )
        assertTrue(
            "Moon icon should use a cutout circle to form a recognizable crescent.",
            themeButtonSource.contains("moonCutout")
        )
        assertTrue(
            "Moon crescent should be wide enough to read as a moon at toolbar size.",
            themeButtonSource.contains("moonRadius * 0.42f") &&
                themeButtonSource.contains("moonRadius * 0.84f")
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
        assertTrue(themeButtonSource.contains("drawSunIcon"))
        assertTrue(themeButtonSource.contains("COLOR_SUN_ICON"))

        view.singleViewWithTag("home-theme-button").performClick()

        assertEquals(listOf("theme"), clicks)
    }

    @Test
    fun homeSettingsButtonUsesGearSilhouetteInsteadOfSunRays() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val settingsButtonSource = source
            .substringAfter("private fun createSettingsButton")
            .substringBefore("private fun iconButton")

        assertTrue(source.contains("tag = \"home-settings-button\""))
        assertFalse(
            "Settings icon should not use radial drawLine teeth because it reads like a sun.",
            settingsButtonSource.contains("drawLine(")
        )
        assertTrue(
            "Settings icon should use a light stroked gear outline path.",
            settingsButtonSource.contains("gearPath") &&
                settingsButtonSource.contains("canvas.drawPath(gearPath, gearStroke)")
        )
        assertTrue(
            "Settings icon should keep a center cutout so it reads as a gear.",
            settingsButtonSource.contains("canvas.drawCircle(centerX, centerY, size * 0.075f, gearStroke)")
        )
        assertTrue(
            "Settings icon should share the moon icon visual bounds instead of looking taller or wider.",
            settingsButtonSource.contains("val outer = size * 0.19f") &&
                settingsButtonSource.contains("val shoulder = size * 0.145f")
        )
    }

    @Test
    fun homeRecentActionsUseReferenceStyleSelfDrawnIcons() {
        val source = File("src/main/java/com/personalai/instantread/ui/HomeViewFactory.kt").readText()
        val rowSource = source
            .substringAfter("private fun createRecentRow")
            .substringBefore("private fun fileIconView")
        val favoriteButtonSource = source
            .substringAfter("private fun favoriteIconButton")
            .substringBefore("private fun deleteIconButton")
        val deleteButtonSource = source
            .substringAfter("private fun deleteIconButton")
            .substringBefore("private fun createDeleteSheet")

        assertFalse(
            "Recent rows should not render favorite state with system text glyphs.",
            rowSource.contains("\"★\"") || rowSource.contains("\"☆\"")
        )
        assertTrue(
            "Favorite action should be a custom drawn outlined star.",
            favoriteButtonSource.contains("tag = \"recent-favorite-button\"") &&
                favoriteButtonSource.contains("starPath") &&
                favoriteButtonSource.contains("canvas.drawPath(starPath, starStroke)") &&
                favoriteButtonSource.contains("val centerY = height / 2f")
        )
        assertTrue(
            "Delete action should be a custom drawn bright outline trash can.",
            deleteButtonSource.contains("tag = \"recent-delete-button\"") &&
                deleteButtonSource.contains("trashStroke") &&
                deleteButtonSource.contains("val iconCenterY = height / 2f") &&
                deleteButtonSource.contains("bodyWidth = dpFloat(10.8f)") &&
                deleteButtonSource.contains("bodyHeight = dpFloat(14f)") &&
                deleteButtonSource.contains("totalHeight = bodyHeight + dpFloat(3f)") &&
                deleteButtonSource.contains("val lidY = top") &&
                deleteButtonSource.contains("handleTop = lidY - dpFloat(3f)") &&
                deleteButtonSource.contains("canvas.drawRoundRect")
        )
        assertTrue(
            "Recent action icon colors should match the reference image family.",
            source.contains("COLOR_FAVORITE_ICON") &&
                source.contains("COLOR_DELETE_ICON")
        )
        assertFalse(
            "Favorited star should be solid in the same color as the outline, not semi-transparent.",
            favoriteButtonSource.contains("alpha = if (item.isFavorite) 54")
        )
        assertTrue(
            "Favorited star should fill with the same color family as its outline.",
            favoriteButtonSource.contains("canvas.drawPath(starPath, starFill)") &&
                favoriteButtonSource.contains("starFill.alpha = if (item.isFavorite) 255 else 0")
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
        assertTrue(text.contains("HTML 与 ZIP 网页包中的脚本默认不执行。"))
        assertTrue(text.contains("在预览页点击“JS 已禁用”可为当前文档单独允许脚本，只对这一次导入的副本生效。"))
        assertTrue(text.contains("Markdown 与 TXT 预览不受影响。"))
        assertTrue(text.contains("ZIP 解包缓存"))
        assertTrue(text.contains("阅读设置"))
        assertTrue(text.contains("正文显示"))
        assertTrue(text.contains("在 Markdown 或 TXT 文件右上角点击 Aa 调整字号、字距、行距和段距。"))
        assertFalse(text.contains("紧凑"))
        assertFalse(text.contains("标准"))
        assertFalse(text.contains("舒适"))
        assertTrue(text.contains("当前缓存"))
        assertTrue(text.contains("可用"))
        assertTrue(text.contains("清理 ZIP 缓存"))
        assertTrue(text.contains("清理只删除已解包资源，最近记录、收藏和导入源文件都会保留。"))
        assertTrue(text.contains("外部链接"))
        assertTrue(text.contains("外部链接将交给系统浏览器打开，App 内不会直接跳转到外部网页。"))
        assertTrue(text.contains("本地隐私"))
        assertTrue(text.contains("文件仅在本机处理，不上传到云端。"))
        assertTrue(text.contains("最近记录和收藏保存在本机。"))
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
        assertEquals(5, view.viewsWithTag("settings-section-header").size)
        assertEquals(5, view.viewsWithTag("settings-section-block").size)
        assertEquals(0, view.viewsWithTag("settings-reading-compact").size)
        assertEquals(0, view.viewsWithTag("settings-reading-standard").size)
        assertEquals(0, view.viewsWithTag("settings-reading-comfortable").size)
        assertEquals(3, view.viewsWithTag("settings-info-row").size)
        assertEquals(3, view.viewsWithTag("settings-info-icon").size)
        assertEquals(1, view.viewsWithTag("settings-note-card").size)
        assertEquals(1, view.viewsWithTag("settings-clear-cache-button").size)
        assertEquals(1, view.viewsWithTag("settings-page-footer").size)
        assertNotNull("Settings should use an intentional page background.", view.background)
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
    fun settingsInfoIconsUseLargerDrawingBoundsInsideTiles() {
        val source = File("src/main/java/com/personalai/instantread/ui/SettingsViewFactory.kt").readText()
        val iconSource = source
            .substringAfter("private fun settingsInfoIcon")
            .substringBefore("private fun transparentBackground")
        val externalSource = iconSource
            .substringAfter("private fun drawExternalLink")
            .substringBefore("private fun drawShield")
        val shieldSource = iconSource
            .substringAfter("private fun drawShield")
            .substringBefore("private fun drawSavedLocal")
        val savedSource = iconSource
            .substringAfter("private fun drawSavedLocal")
            .substringBefore("private fun transparentBackground")

        assertTrue(
            "Info icons should use a lighter reference-style stroke.",
            iconSource.contains("strokeWidth = dpFloat(1.15f)")
        )
        assertTrue(
            "External-link icon should use a slightly smaller square frame instead of a wide rectangle.",
            externalSource.contains("val iconBox = minOf(width, height) * 0.58f") &&
                externalSource.contains("iconLeft + iconBox * 0.10f") &&
                externalSource.contains("iconTop + iconBox * 0.28f") &&
                externalSource.contains("iconLeft + iconBox * 0.74f") &&
                externalSource.contains("iconTop + iconBox * 0.92f") &&
                externalSource.contains("canvas.drawLine(left, top, left, bottom, iconStroke)") &&
                externalSource.contains("canvas.drawLine(left, bottom, right, bottom, iconStroke)") &&
                externalSource.contains("iconLeft + iconBox * 0.48f") &&
                externalSource.contains("iconTop + iconBox * 0.52f") &&
                externalSource.contains("iconLeft + iconBox * 0.84f") &&
                externalSource.contains("iconTop + iconBox * 0.16f") &&
                externalSource.contains("iconTop + iconBox * 0.40f")
        )
        assertTrue(
            "Shield icons should stay on the previous version while only the external-link icon changes.",
            shieldSource.contains("val shieldBox = minOf(width, height) * 0.60f") &&
                shieldSource.contains("iconLeft + shieldBox * 0.82f") &&
                shieldSource.contains("iconLeft + shieldBox * 0.18f") &&
                shieldSource.contains("iconTop + shieldBox * 0.88f") &&
                savedSource.contains("val shieldBox = minOf(width, height) * 0.60f") &&
                savedSource.contains("iconLeft + shieldBox * 0.82f") &&
                savedSource.contains("iconLeft + shieldBox * 0.18f") &&
                savedSource.contains("iconTop + shieldBox * 0.88f")
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
        val rowTitleSource = source.substringAfter("private fun rowTitle").substringBefore("private fun bodyText")
        val sectionTitleSource = source.substringAfter("private fun sectionTitle").substringBefore("private fun sectionBlock")
        val footerSource = source.substringAfter("private fun createFooter").substringBefore("private fun createMessage")
        val successSource = source.substringAfter("private fun successText").substringBefore("private fun statusBadge")

        assertFalse("Row titles should not all be forced bold.", rowTitleSource.contains("Typeface.BOLD"))
        assertFalse("Section labels should rely on size, color, and spacing instead of bold weight.", sectionTitleSource.contains("Typeface.BOLD"))
        assertFalse("Footer should be quiet metadata, not bold display text.", footerSource.contains("Typeface.BOLD"))
        assertFalse("Cache availability should not need bold weight.", successSource.contains("Typeface.BOLD"))
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
