package com.personalai.instantread

import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.preview.ReadingDisplayOptions
import com.personalai.instantread.settings.PreviewSettings
import com.personalai.instantread.ui.HomeListFilter
import com.personalai.instantread.ui.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreviewWorkspaceStateMapperTest {
    @Test
    fun htmlStateShowsTitleFavoriteAndJavaScriptPolicy() {
        val state = history(
            displayName = "sample.html",
            fileType = FileType.HTML,
            favorite = true
        ).toPreviewWorkspaceState(javaScriptEnabledForPreview = true, isDarkTheme = false)

        assertEquals("sample.html", state.title)
        assertEquals("HTML", state.fileTypeLabel)
        assertTrue(state.isFavorite)
        assertFalse(state.isDarkTheme)
        assertEquals("JS 已启用", state.javaScriptStatus)
        assertEquals("安全沙箱已激活", state.navigationStatus)
        assertTrue(state.supportsScriptAuthorization)
    }

    @Test
    fun zipStateUsesFilenameFallbackAndJavaScriptPolicy() {
        val state = history(
            displayName = null,
            fileType = FileType.ZIP,
            favorite = false,
            localFile = File("source.zip")
        ).toPreviewWorkspaceState(javaScriptEnabledForPreview = false, isDarkTheme = true)

        assertEquals("source.zip", state.title)
        assertEquals("ZIP", state.fileTypeLabel)
        assertFalse(state.isFavorite)
        assertTrue(state.isDarkTheme)
        assertEquals("JS 已禁用", state.javaScriptStatus)
        assertEquals("安全沙箱已激活", state.navigationStatus)
        assertTrue(state.supportsScriptAuthorization)
    }

    @Test
    fun markdownStateExplainsAppOwnedJavaScriptWhileTextDoesNotExecuteScripts() {
        val markdown = history(fileType = FileType.MARKDOWN)
            .toPreviewWorkspaceState(javaScriptEnabledForPreview = true, isDarkTheme = false)
        val text = history(fileType = FileType.TEXT)
            .toPreviewWorkspaceState(javaScriptEnabledForPreview = false, isDarkTheme = false)

        assertEquals("仅运行内置目录交互", markdown.javaScriptStatus)
        assertEquals("JS 不执行", text.javaScriptStatus)
        assertEquals("本地阅读模式", markdown.navigationStatus)
        assertEquals("本地阅读模式", text.navigationStatus)
        assertFalse(markdown.supportsScriptAuthorization)
        assertFalse(text.supportsScriptAuthorization)
    }

    @Test
    fun zipMarkdownPackageWithoutUserScriptsHidesAuthorizationEntry() {
        val state = history(fileType = FileType.ZIP)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = false,
                isDarkTheme = false,
                userScriptAuthorizationAvailable = false
            )

        assertEquals("JS 不执行", state.javaScriptStatus)
        assertFalse(state.supportsScriptAuthorization)
        assertEquals("安全沙箱已激活", state.navigationStatus)
    }

    @Test
    fun previewOptionsUsePerDocumentScriptAuthorization() {
        val authorized = history(fileType = FileType.HTML, jsAllowed = true)
            .toPreviewOptions(PreviewSettings(isDarkTheme = true), PreviewDocumentMode.REVIEW)
        val notAuthorized = history(fileType = FileType.HTML, jsAllowed = false)
            .toPreviewOptions(PreviewSettings(), PreviewDocumentMode.READING)

        assertTrue(authorized.javaScriptEnabledForHtml)
        assertTrue(authorized.generatedContentDarkTheme)
        assertEquals(PreviewDocumentMode.REVIEW, authorized.markdownDocumentMode)
        assertFalse(notAuthorized.javaScriptEnabledForHtml)
    }

    @Test
    fun onlyMarkdownStateSupportsReadingReviewModeSwitch() {
        val markdown = history(fileType = FileType.MARKDOWN)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                documentMode = PreviewDocumentMode.REVIEW
            )
        val html = history(fileType = FileType.HTML)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                documentMode = PreviewDocumentMode.REVIEW
            )

        assertTrue(markdown.supportsDocumentReview)
        assertEquals(PreviewDocumentMode.REVIEW, markdown.documentMode)
        assertFalse(html.supportsDocumentReview)
        assertEquals(PreviewDocumentMode.READING, html.documentMode)
    }

    @Test
    fun markdownAndTextStatesSupportInDocumentSearchWhileHtmlDoesNot() {
        val searchState = PreviewSearchState(
            isOpen = true,
            query = "Title",
            activeMatchOrdinal = 1,
            totalMatches = 3
        )
        val markdown = history(fileType = FileType.MARKDOWN)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                searchState = searchState
            )
        val text = history(fileType = FileType.TEXT)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                searchState = searchState
            )
        val html = history(fileType = FileType.HTML)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                searchState = searchState
            )

        assertTrue(markdown.supportsSearch)
        assertEquals(searchState, markdown.searchState)
        assertTrue(text.supportsSearch)
        assertEquals(searchState, text.searchState)
        assertFalse(html.supportsSearch)
        assertFalse(html.searchState.isOpen)
        assertEquals("", html.searchState.query)
    }

    @Test
    fun markdownAndTextStateSupportReadingDisplayControlsOnly() {
        val options = ReadingDisplayOptions(
            fontSizeSp = 20,
            letterSpacingEm = 0.03f,
            lineHeight = 1.9f,
            paragraphSpacingEm = 0.88f
        )
        val markdown = history(fileType = FileType.MARKDOWN)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                isReadingDisplayPanelOpen = true,
                readingDisplayOptions = options
            )
        val text = history(fileType = FileType.TEXT)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                isReadingDisplayPanelOpen = true,
                readingDisplayOptions = options
            )
        val html = history(fileType = FileType.HTML)
            .toPreviewWorkspaceState(
                javaScriptEnabledForPreview = true,
                isDarkTheme = false,
                isReadingDisplayPanelOpen = true,
                readingDisplayOptions = options
            )

        assertTrue(markdown.supportsReadingDisplayControls)
        assertTrue(markdown.isReadingDisplayPanelOpen)
        assertEquals(options, markdown.readingDisplayOptions)
        assertTrue(text.supportsReadingDisplayControls)
        assertTrue(text.isReadingDisplayPanelOpen)
        assertEquals(options, text.readingDisplayOptions)
        assertFalse(html.supportsReadingDisplayControls)
        assertFalse(html.isReadingDisplayPanelOpen)
        assertEquals(options, html.readingDisplayOptions)
    }

    @Test
    fun recentItemsCanBeFilteredToFavoritesOnly() {
        val favorite = RecentFileItem(
            id = "favorite",
            title = "favorite.html",
            fileTypeLabel = "HTML",
            isFavorite = true
        )
        val normal = RecentFileItem(
            id = "normal",
            title = "normal.md",
            fileTypeLabel = "MARKDOWN",
            isFavorite = false
        )

        assertEquals(listOf(favorite, normal), listOf(favorite, normal).filterFor(HomeListFilter.ALL))
        assertEquals(listOf(favorite), listOf(favorite, normal).filterFor(HomeListFilter.FAVORITES))
    }

    @Test
    fun recentItemsCanBeFilteredBySearchQuery() {
        val html = RecentFileItem(
            id = "html",
            title = "Report.html",
            fileTypeLabel = "HTML",
            isFavorite = false
        )
        val markdown = RecentFileItem(
            id = "markdown",
            title = "notes.md",
            fileTypeLabel = "MARKDOWN",
            isFavorite = false
        )
        val zip = RecentFileItem(
            id = "zip",
            title = "site.zip",
            fileTypeLabel = "ZIP",
            isFavorite = false
        )

        assertEquals(listOf(html, markdown, zip), listOf(html, markdown, zip).filterBySearch(""))
        assertEquals(listOf(html), listOf(html, markdown, zip).filterBySearch("report"))
        assertEquals(listOf(markdown), listOf(html, markdown, zip).filterBySearch("MARK"))
        assertEquals(emptyList<RecentFileItem>(), listOf(html, markdown, zip).filterBySearch("missing"))
    }

    private fun history(
        displayName: String? = "source.html",
        fileType: FileType = FileType.HTML,
        favorite: Boolean = false,
        localFile: File = File("source.html"),
        jsAllowed: Boolean = false
    ): PreviewHistory {
        return PreviewHistory(
            id = "id",
            displayName = displayName,
            fileType = fileType,
            sourceKind = SourceKind.APP_PICKER,
            localFile = localFile,
            createdAt = 1L,
            lastOpenedAt = 2L,
            favorite = favorite,
            jsAllowed = jsAllowed
        )
    }
}
