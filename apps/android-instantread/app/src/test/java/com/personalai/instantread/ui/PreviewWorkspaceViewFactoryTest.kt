package com.personalai.instantread.ui

import android.app.Activity
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.personalai.instantread.PreviewDocumentMode
import com.personalai.instantread.PreviewSearchState
import com.personalai.instantread.preview.ReadingDisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewWorkspaceViewFactoryTest {
    @Test
    fun scriptBadgeTogglesAuthorizationOnlyWhenDocumentSupportsIt() {
        val context = RuntimeEnvironment.getApplication()
        val toggles = mutableListOf<String>()

        val html = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "sample.html",
                fileTypeLabel = "HTML",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 已禁用",
                supportsScriptAuthorization = true,
                navigationStatus = "安全沙箱已激活"
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onToggleDocumentJavaScript = { toggles += "html" }
            )
        )

        val badge = html.viewWithTag("preview-js-badge")
        assertTrue(badge.isClickable)
        assertEquals("为当前文档允许脚本", badge.contentDescription)

        badge.performClick()

        assertEquals(listOf("html"), toggles)

        val markdown = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "sample.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式"
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onToggleDocumentJavaScript = { toggles += "markdown" }
            )
        )

        val markdownBadge = markdown.viewWithTag("preview-js-badge")
        assertFalse(markdownBadge.isClickable)
        assertNull(markdownBadge.contentDescription)

        markdownBadge.performClick()

        assertEquals(listOf("html"), toggles)
    }

    @Test
    fun workspaceUsesReferenceStyleHeaderPolicyBarAndContent() {
        val context = RuntimeEnvironment.getApplication()
        val content = TextView(context).apply { text = "Preview body" }

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "sample.html",
                fileTypeLabel = "HTML",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 已禁用",
                navigationStatus = "安全沙箱已激活"
            ),
            contentView = content,
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {}
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("sample.html"))
        assertTrue(text.contains("HTML · 本地模式中"))
        assertTrue(text.contains("JS 已禁用"))
        assertTrue(text.contains("安全沙箱已激活"))
        assertTrue(text.contains("Preview body"))

        assertFalse(text.contains("返回"))
        assertFalse(text.contains("收藏"))
        assertFalse(text.contains("取消收藏"))
        assertFalse(text.contains("设置"))
        assertFalse(text.contains("JavaScript：已关闭"))
        assertFalse(text.contains("外部链接将交给系统浏览器打开"))

        assertEquals(dp(59), view.viewWithTag("preview-app-bar").layoutParams.height)
        assertEquals(dp(34), view.viewWithTag("preview-policy-bar").layoutParams.height)
        assertTrue(view.viewWithTag("preview-back-button").isClickable)
        assertTrue(view.viewWithTag("preview-favorite-button").isClickable)
        assertFalse(view.hasTaggedView("preview-theme-button"))
        assertFalse(view.hasTaggedView("preview-settings-button"))
    }

    @Test
    fun markdownSearchActionUsesTheFormerThemeButtonPosition() {
        val context = RuntimeEnvironment.getApplication()
        val view = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsSearch = true
            ),
            contentView = TextView(context),
            actions = emptyActions()
        )

        val appBar = view.viewWithTag("preview-app-bar")
        val searchParams = view
            .viewWithTag("preview-search-button")
            .layoutParams as LinearLayout.LayoutParams
        val favoriteParams = view
            .viewWithTag("preview-favorite-button")
            .layoutParams as LinearLayout.LayoutParams

        assertEquals(dp(59), appBar.layoutParams.height)
        assertEquals(dp(8), appBar.paddingRight)
        assertEquals(dp(0), searchParams.leftMargin)
        assertEquals(dp(8), favoriteParams.leftMargin)
        assertFalse(view.hasTaggedView("preview-theme-button"))
    }

    @Test
    fun workspaceUpdatesChromeWithoutReplacingPreviewContent() {
        val context = RuntimeEnvironment.getApplication()
        val factory = PreviewWorkspaceViewFactory(context)
        val content = TextView(context).apply {
            tag = "preview-content"
            text = "Loaded content"
        }
        val view = factory.create(
            state = previewState(isFavorite = false, isDarkTheme = false),
            contentView = content,
            actions = emptyActions()
        )

        val updated = factory.updateChrome(
            workspace = view,
            state = previewState(isFavorite = true, isDarkTheme = true),
            actions = emptyActions()
        )

        assertTrue(updated)
        assertEquals(content, view.getChildAt(3))
        assertEquals("取消收藏", view.viewWithTag("preview-favorite-button").contentDescription)
        assertFalse(view.hasTaggedView("preview-theme-button"))
    }

    @Test
    fun workspaceIconButtonsInvokeActions() {
        val context = RuntimeEnvironment.getApplication()
        val clicks = mutableListOf<String>()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = true,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式"
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = { clicks += "back" },
                onToggleFavorite = { clicks += "favorite" }
            )
        )

        view.viewWithTag("preview-back-button").performClick()
        view.viewWithTag("preview-favorite-button").performClick()

        assertEquals(listOf("back", "favorite"), clicks)
    }

    @Test
    fun markdownWorkspaceShowsReadingReviewSwitchAndInvokesModeChanges() {
        val context = RuntimeEnvironment.getApplication()
        val modeChanges = mutableListOf<PreviewDocumentMode>()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsDocumentReview = true,
                documentMode = PreviewDocumentMode.READING
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onDocumentModeChanged = { modeChanges += it }
            )
        )

        val text = view.visibleText().joinToString("\n")

        assertTrue(text.contains("阅读"))
        assertTrue(text.contains("审查"))
        assertEquals(dp(36), view.viewWithTag("preview-document-mode-switch").layoutParams.height)

        view.viewWithTag("preview-review-mode-button").performClick()
        view.viewWithTag("preview-reading-mode-button").performClick()

        assertEquals(listOf(PreviewDocumentMode.REVIEW, PreviewDocumentMode.READING), modeChanges)
    }

    @Test
    fun markdownAndTextWorkspacesShowSearchEntryWhileHtmlDoesNot() {
        val context = RuntimeEnvironment.getApplication()
        val clicks = mutableListOf<String>()

        val markdown = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsSearch = true
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onSearchOpened = { clicks += "open" }
            )
        )
        val text = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.txt",
                fileTypeLabel = "TEXT",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsSearch = true
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onSearchOpened = { clicks += "open" }
            )
        )
        val html = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false),
            contentView = TextView(context),
            actions = emptyActions()
        )

        assertTrue(markdown.hasTaggedView("preview-search-button"))
        assertFalse(markdown.hasTaggedView("preview-search-bar"))
        assertTrue(text.hasTaggedView("preview-search-button"))
        assertFalse(html.hasTaggedView("preview-search-button"))

        text.viewWithTag("preview-search-button").performClick()

        assertEquals(listOf("open"), clicks)
    }

    @Test
    fun openSearchCanBeClosedFromTheTopBarSearchButton() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsSearch = true,
                searchState = PreviewSearchState(isOpen = true)
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onSearchOpened = { events += "open" },
                onSearchClosed = { events += "close" }
            )
        )

        val searchButton = view.viewWithTag("preview-search-button")
        assertEquals("关闭搜索", searchButton.contentDescription)
        searchButton.performClick()

        assertEquals(listOf("close"), events)
    }

    @Test
    fun markdownAndTextWorkspaceShowReadingDisplayEntryAndHtmlDoesNot() {
        val context = RuntimeEnvironment.getApplication()
        val clicks = mutableListOf<String>()

        val markdown = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsReadingDisplayControls = true
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayOpened = { clicks += "open" }
            )
        )
        val text = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "TEXT",
                supportsReadingDisplayControls = true
            ),
            contentView = TextView(context),
            actions = emptyActions()
        )
        val html = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false),
            contentView = TextView(context),
            actions = emptyActions()
        )

        assertTrue(markdown.hasTaggedView("preview-reading-display-button"))
        assertTrue(text.hasTaggedView("preview-reading-display-button"))
        assertFalse(html.hasTaggedView("preview-reading-display-button"))

        markdown.viewWithTag("preview-reading-display-button").performClick()

        assertEquals(listOf("open"), clicks)
    }

    @Test
    fun openedReadingDisplayPanelShowsControlsAndInvokesChanges() {
        val context = RuntimeEnvironment.getApplication()
        val changes = mutableListOf<(ReadingDisplayOptions) -> ReadingDisplayOptions>()
        val events = mutableListOf<String>()
        val options = ReadingDisplayOptions(
            fontSizeSp = 18,
            letterSpacingEm = 0.01f,
            lineHeight = 1.8f,
            paragraphSpacingEm = 0.8f
        )

        val view = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsReadingDisplayControls = true,
                isReadingDisplayPanelOpen = true,
                readingDisplayOptions = options
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayClosed = { events += "close" },
                onReadingDisplayChanged = { changes += it },
                onReadingDisplayReset = { events += "reset" }
            )
        )

        val text = view.visibleText().joinToString("\n")
        assertTrue(text.contains("正文显示"))
        assertTrue(text.contains("字号"))
        assertTrue(text.contains("字距"))
        assertTrue(text.contains("行距"))
        assertTrue(text.contains("段距"))
        assertTrue(text.contains("18sp"))
        assertTrue(text.contains("0.01em"))
        assertTrue(text.contains("1.80"))
        assertTrue(text.contains("0.80em"))
        assertTrue(view.hasTaggedView("preview-reading-display-panel"))

        dragSliderToNextProgress(view, "preview-reading-font-size-slider")
        assertEquals(19, changes.last()(options).fontSizeSp)

        dragSliderToNextProgress(view, "preview-reading-letter-spacing-slider")
        assertEquals(0.02f, changes.last()(options).letterSpacingEm, 0.001f)

        dragSliderToNextProgress(view, "preview-reading-line-height-slider")
        assertEquals(1.85f, changes.last()(options).lineHeight, 0.001f)

        dragSliderToNextProgress(view, "preview-reading-paragraph-spacing-slider")
        assertEquals(0.84f, changes.last()(options).paragraphSpacingEm, 0.001f)

        view.viewWithTag("preview-reading-display-reset-button").performClick()
        view.viewWithTag("preview-reading-display-button").performClick()

        assertEquals(listOf("reset", "close"), events)
    }

    @Test
    fun readingDisplaySlidersDoNotApplyUntilUserStopsDragging() {
        val context = RuntimeEnvironment.getApplication()
        val changes = mutableListOf<(ReadingDisplayOptions) -> ReadingDisplayOptions>()
        val view = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsReadingDisplayControls = true,
                isReadingDisplayPanelOpen = true,
                readingDisplayOptions = ReadingDisplayOptions.DEFAULT
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayChanged = { changes += it }
            )
        )
        val slider = view.viewWithTag("preview-reading-font-size-slider") as SeekBar
        val listener = shadowOf(slider).onSeekBarChangeListener

        listener.onStartTrackingTouch(slider)
        listener.onStopTrackingTouch(slider)
        assertTrue(changes.isEmpty())

        listener.onStartTrackingTouch(slider)
        listener.onProgressChanged(slider, slider.progress + 1, true)

        assertTrue(changes.isEmpty())

        listener.onStopTrackingTouch(slider)

        assertEquals(1, changes.size)
        assertEquals(18, changes.single()(ReadingDisplayOptions.DEFAULT).fontSizeSp)
    }

    @Test
    fun outsideReadingDisplayTapClosesPanelAndContinuesContentClick() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val factory = PreviewWorkspaceViewFactory(context)
        val closedState = previewState(isFavorite = false, isDarkTheme = false).copy(
            fileTypeLabel = "MARKDOWN",
            supportsReadingDisplayControls = true,
            isReadingDisplayPanelOpen = false
        )
        val openState = closedState.copy(isReadingDisplayPanelOpen = true)
        val content = TextView(context).apply {
            tag = "preview-test-content"
            isClickable = true
            setOnClickListener { events += "content" }
        }
        val view = factory.create(
            state = closedState,
            contentView = content,
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayClosed = { events += "stale-close" }
            )
        )
        val closedActions = PreviewWorkspaceActions(
            onBack = {},
            onToggleFavorite = {},
            onReadingDisplayClosed = { events += "closed-close" }
        )
        val currentActions = PreviewWorkspaceActions(
            onBack = {},
            onToggleFavorite = {},
            onReadingDisplayClosed = {
                events += "close"
                assertTrue(factory.updateChrome(view, closedState, closedActions))
            }
        )
        assertTrue(factory.updateChrome(view, openState, currentActions))

        withLaidOutRoot(view) {
            tap(view, content)
        }

        assertEquals(listOf("close", "content"), events)
    }

    @Test
    fun outsideReadingDisplayTapClosesPanelAndContinuesTopBarAction() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val factory = PreviewWorkspaceViewFactory(context)
        val openState = previewState(isFavorite = false, isDarkTheme = false).copy(
            fileTypeLabel = "MARKDOWN",
            supportsSearch = true,
            supportsReadingDisplayControls = true,
            isReadingDisplayPanelOpen = true
        )
        val closedState = openState.copy(isReadingDisplayPanelOpen = false)
        lateinit var view: LinearLayout
        val closedActions = PreviewWorkspaceActions(
            onBack = {},
            onToggleFavorite = {},
            onSearchOpened = { events += "replacement-search" },
            onReadingDisplayOpened = { events += "open" },
            onReadingDisplayClosed = { events += "closed-close" }
        )
        view = factory.create(
            state = openState,
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onSearchOpened = { events += "search" },
                onReadingDisplayClosed = {
                    events += "close"
                    assertTrue(factory.updateChrome(view, closedState, closedActions))
                }
            )
        )
        withLaidOutRoot(view) {
            tap(view, view.viewWithTag("preview-search-button"))
        }

        assertEquals(listOf("close", "search"), events)
        assertEquals("正文显示", view.viewWithTag("preview-reading-display-button").contentDescription)

        view.viewWithTag("preview-reading-display-button").performClick()

        assertEquals(listOf("close", "search", "open"), events)
    }

    @Test
    fun readingDisplayPanelTapDoesNotClosePanel() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val view = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsReadingDisplayControls = true,
                isReadingDisplayPanelOpen = true
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayClosed = { events += "close" }
            )
        )
        withLaidOutRoot(view) {
            tap(view, view.viewWithTag("preview-reading-display-panel"))
        }

        assertTrue(events.isEmpty())
    }

    @Test
    fun readingDisplayButtonClosesPanelOnlyOnce() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val view = PreviewWorkspaceViewFactory(context).create(
            state = previewState(isFavorite = false, isDarkTheme = false).copy(
                fileTypeLabel = "MARKDOWN",
                supportsReadingDisplayControls = true,
                isReadingDisplayPanelOpen = true
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayClosed = { events += "close" }
            )
        )
        withLaidOutRoot(view) {
            tap(view, view.viewWithTag("preview-reading-display-button"))
        }

        assertEquals(listOf("close"), events)
    }

    @Test
    fun closedReadingDisplayPanelDoesNotEmitCloseOnContentTap() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()
        val factory = PreviewWorkspaceViewFactory(context)
        val openState = previewState(isFavorite = false, isDarkTheme = false).copy(
            fileTypeLabel = "MARKDOWN",
            supportsReadingDisplayControls = true,
            isReadingDisplayPanelOpen = true
        )
        val closedState = openState.copy(isReadingDisplayPanelOpen = false)
        val content = TextView(context).apply {
            tag = "preview-test-content"
            isClickable = true
            setOnClickListener { events += "content" }
        }
        val view = factory.create(
            state = openState,
            contentView = content,
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onReadingDisplayClosed = { events += "stale-close" }
            )
        )
        assertTrue(
            factory.updateChrome(
                workspace = view,
                state = closedState,
                actions = PreviewWorkspaceActions(
                    onBack = {},
                    onToggleFavorite = {},
                    onReadingDisplayClosed = { events += "current-close" }
                )
            )
        )

        withLaidOutRoot(view) {
            tap(view, content)
        }

        assertEquals(listOf("content"), events)
    }

    @Test
    fun openedSearchBarInvokesQueryNavigationClearAndCloseActions() {
        val context = RuntimeEnvironment.getApplication()
        val events = mutableListOf<String>()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsSearch = true,
                searchState = PreviewSearchState(
                    isOpen = true,
                    query = "alpha",
                    activeMatchOrdinal = 1,
                    totalMatches = 3
                )
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {},
                onSearchClosed = { events += "close" },
                onSearchQueryChanged = { events += "query:$it" },
                onSearchNext = { events += "next" },
                onSearchPrevious = { events += "previous" },
                onSearchCleared = { events += "clear" }
            )
        )

        val input = view.viewWithTag("preview-search-input") as EditText
        assertEquals("alpha", input.text.toString())
        assertTrue(view.visibleText().joinToString("\n").contains("2/3"))

        input.setText("beta")
        view.viewWithTag("preview-search-previous-button").performClick()
        view.viewWithTag("preview-search-next-button").performClick()
        view.viewWithTag("preview-search-clear-button").performClick()
        view.viewWithTag("preview-search-close-button").performClick()

        assertEquals(listOf("query:beta", "previous", "next", "clear", "close"), events)
    }

    @Test
    fun openedSearchBarHasAccessibleActionsAndEmptyResultCopy() {
        val context = RuntimeEnvironment.getApplication()
        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "notes.md",
                fileTypeLabel = "MARKDOWN",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 不执行",
                navigationStatus = "本地阅读模式",
                supportsSearch = true,
                searchState = PreviewSearchState(
                    isOpen = true,
                    query = "missing",
                    activeMatchOrdinal = 0,
                    totalMatches = 0
                )
            ),
            contentView = TextView(context),
            actions = emptyActions()
        )

        assertEquals("上一个匹配", view.viewWithTag("preview-search-previous-button").contentDescription)
        assertEquals("下一个匹配", view.viewWithTag("preview-search-next-button").contentDescription)
        assertEquals("清空搜索", view.viewWithTag("preview-search-clear-button").contentDescription)
        assertEquals("关闭搜索", view.viewWithTag("preview-search-close-button").contentDescription)
        assertTrue(view.visibleText().joinToString("\n").contains("无匹配"))
    }

    @Test
    fun workspaceKeepsActionsAsCustomIconViewsInsteadOfTextButtons() {
        val context = RuntimeEnvironment.getApplication()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "sample.html",
                fileTypeLabel = "HTML",
                isFavorite = false,
                isDarkTheme = false,
                javaScriptStatus = "JS 已启用",
                navigationStatus = "安全沙箱已激活"
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {}
            )
        )

        assertFalse(view.containsButtonText("返回"))
        assertFalse(view.containsButtonText("收藏"))
        assertFalse(view.containsButtonText("设置"))
        assertFalse(view.viewWithTag("preview-back-button") is Button)
        assertFalse(view.viewWithTag("preview-favorite-button") is Button)
        assertFalse(view.hasTaggedView("preview-theme-button"))
        assertFalse(view.hasTaggedView("preview-settings-button"))
    }

    @Test
    fun workspaceDoesNotExposeThemeToggleInPreviewChrome() {
        val context = RuntimeEnvironment.getApplication()

        val view = PreviewWorkspaceViewFactory(context).create(
            state = PreviewWorkspaceState(
                title = "sample.html",
                fileTypeLabel = "HTML",
                isFavorite = false,
                isDarkTheme = true,
                javaScriptStatus = "JS 已禁用",
                navigationStatus = "安全沙箱已激活"
            ),
            contentView = TextView(context),
            actions = PreviewWorkspaceActions(
                onBack = {},
                onToggleFavorite = {}
            )
        )

        assertFalse(view.hasTaggedView("preview-theme-button"))
    }

    @Test
    fun workspaceDarkModeAppliesDarkChromeColors() {
        val context = RuntimeEnvironment.getApplication()
        val factory = PreviewWorkspaceViewFactory(context)

        val light = factory.create(
            state = previewState(isFavorite = false, isDarkTheme = false),
            contentView = TextView(context),
            actions = emptyActions()
        )
        val dark = factory.create(
            state = previewState(isFavorite = false, isDarkTheme = true),
            contentView = TextView(context),
            actions = emptyActions()
        )

        assertNotEquals(light.backgroundColor(), dark.backgroundColor())
        assertNotEquals(
            light.viewWithTag("preview-app-bar").backgroundColor(),
            dark.viewWithTag("preview-app-bar").backgroundColor()
        )
        assertNotEquals(
            light.viewWithTag("preview-policy-bar").backgroundColor(),
            dark.viewWithTag("preview-policy-bar").backgroundColor()
        )
    }

    @Test
    fun workspaceFavoriteIconUsesPrototypeStarVector() {
        val source = java.io.File("src/main/java/com/personalai/instantread/ui/PreviewWorkspaceViewFactory.kt").readText()
        val favoriteButtonSource = source
            .substringAfter("private fun favoriteButton")
            .substringBefore("private fun divider")
        val filledStarVector =
            java.io.File("src/main/res/drawable/ic_instantread_star_filled.xml").readText()

        assertTrue(
            "Preview favorite action should reuse the shared prototype star vector.",
            favoriteButtonSource.contains("InstantIcons.Star") &&
                favoriteButtonSource.contains("active = state.isFavorite") &&
                favoriteButtonSource.contains("activeTint = colors(state).favoriteOn")
        )
        assertTrue(
            "Favorited preview star should switch to the filled vector instead of a translucent fill.",
            filledStarVector.contains("android:fillColor=\"#000000\"")
        )
    }

    private fun withLaidOutRoot(root: View, block: () -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        try {
            controller.setup()
            controller.get().setContentView(root)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(dp(400), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(900), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            block()
        } finally {
            try {
                controller.close()
            } finally {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    private fun tap(root: View, target: View) {
        val bounds = Rect()
        target.getDrawingRect(bounds)
        (root as ViewGroup).offsetDescendantRectToMyCoords(target, bounds)
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            0
        )
        val upEvent = MotionEvent.obtain(
            downTime,
            downTime + 10L,
            MotionEvent.ACTION_UP,
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            0
        )
        try {
            root.dispatchTouchEvent(downEvent)
            root.dispatchTouchEvent(upEvent)
        } finally {
            downEvent.recycle()
            upEvent.recycle()
        }
        shadowOf(Looper.getMainLooper()).idle()
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

    private fun dragSliderToNextProgress(root: View, tag: String) {
        val slider = root.viewWithTag(tag) as SeekBar
        val listener = shadowOf(slider).onSeekBarChangeListener
        listener.onStartTrackingTouch(slider)
        listener.onProgressChanged(slider, slider.progress + 1, true)
        listener.onStopTrackingTouch(slider)
    }

    private fun View.containsButtonText(expected: String): Boolean {
        return findButtonWithText(expected) != null
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

    private fun View.viewWithTag(expected: String): View {
        if (tag == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findTaggedView(expected)
                if (found != null) return found
            }
        }
        throw AssertionError("View not found with tag: $expected")
    }

    private fun View.hasTaggedView(expected: String): Boolean {
        return findTaggedView(expected) != null
    }

    private fun View.findTaggedView(expected: String): View? {
        if (tag == expected) {
            return this
        }
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findTaggedView(expected)
                if (found != null) return found
            }
        }
        return null
    }

    private fun dp(value: Int): Int {
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun View.backgroundColor(): Int {
        return (background as ColorDrawable).color
    }

    private fun previewState(
        isFavorite: Boolean,
        isDarkTheme: Boolean
    ): PreviewWorkspaceState {
        return PreviewWorkspaceState(
            title = "sample.html",
            fileTypeLabel = "HTML",
            isFavorite = isFavorite,
            isDarkTheme = isDarkTheme,
            javaScriptStatus = "JS 已禁用",
            navigationStatus = "安全沙箱已激活"
        )
    }

    private fun emptyActions(): PreviewWorkspaceActions {
        return PreviewWorkspaceActions(
            onBack = {},
            onToggleFavorite = {}
        )
    }
}
