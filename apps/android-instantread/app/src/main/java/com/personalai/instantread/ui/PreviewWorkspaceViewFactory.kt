package com.personalai.instantread.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.personalai.instantread.PreviewDocumentMode
import com.personalai.instantread.PreviewSearchState
import com.personalai.instantread.preview.ReadingDisplayOptions
import java.util.Locale
import kotlin.math.roundToInt

data class PreviewWorkspaceState(
    val title: String,
    val fileTypeLabel: String,
    val isFavorite: Boolean,
    val isDarkTheme: Boolean,
    val javaScriptStatus: String,
    // HTML/ZIP 文档才允许用户按单个文档授权脚本；Markdown/TXT 不显示该入口。
    val supportsScriptAuthorization: Boolean = false,
    val navigationStatus: String,
    val supportsDocumentReview: Boolean = false,
    val documentMode: PreviewDocumentMode = PreviewDocumentMode.READING,
    val supportsSearch: Boolean = false,
    val searchState: PreviewSearchState = PreviewSearchState(),
    val supportsReadingDisplayControls: Boolean = false,
    val isReadingDisplayPanelOpen: Boolean = false,
    val readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions.DEFAULT
)

class PreviewWorkspaceActions(
    val onBack: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDocumentModeChanged: (PreviewDocumentMode) -> Unit = {},
    val onSearchOpened: () -> Unit = {},
    val onSearchClosed: () -> Unit = {},
    val onSearchQueryChanged: (String) -> Unit = {},
    val onSearchNext: () -> Unit = {},
    val onSearchPrevious: () -> Unit = {},
    val onSearchCleared: () -> Unit = {},
    val onToggleDocumentJavaScript: () -> Unit = {},
    val onReadingDisplayOpened: () -> Unit = {},
    val onReadingDisplayClosed: () -> Unit = {},
    val onReadingDisplayChanged: ((ReadingDisplayOptions) -> ReadingDisplayOptions) -> Unit = {},
    val onReadingDisplayReset: () -> Unit = {}
)

private class PreviewWorkspaceLayout(context: Context) : LinearLayout(context) {
    private var dismissReadingDisplayOnOutsideTap = false
    private var onReadingDisplayOutsideTap: () -> Unit = {}
    private var readingDisplayOutsideDismissalDispatchDepth = 0

    val isDispatchingReadingDisplayOutsideDismissal: Boolean
        get() = readingDisplayOutsideDismissalDispatchDepth > 0

    fun configureReadingDisplayOutsideDismissal(
        enabled: Boolean,
        onOutsideTap: () -> Unit
    ) {
        dismissReadingDisplayOnOutsideTap = enabled
        onReadingDisplayOutsideTap = onOutsideTap
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (
            event.actionMasked == MotionEvent.ACTION_DOWN &&
            dismissReadingDisplayOnOutsideTap &&
            isOutsideReadingDisplayControls(event)
        ) {
            readingDisplayOutsideDismissalDispatchDepth += 1
            try {
                onReadingDisplayOutsideTap()
            } finally {
                readingDisplayOutsideDismissalDispatchDepth -= 1
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun isOutsideReadingDisplayControls(event: MotionEvent): Boolean {
        val panel = findViewWithTag<View>("preview-reading-display-panel")
        val button = findViewWithTag<View>("preview-reading-display-button")
        return !contains(event, panel) && !contains(event, button)
    }

    private fun contains(event: MotionEvent, target: View?): Boolean {
        if (target == null || target.visibility != View.VISIBLE) return false
        val bounds = Rect()
        target.getDrawingRect(bounds)
        offsetDescendantRectToMyCoords(target, bounds)
        return bounds.contains(event.x.toInt(), event.y.toInt())
    }
}

class PreviewWorkspaceViewFactory(
    private val context: Context
) {
    fun create(
        state: PreviewWorkspaceState,
        contentView: View,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        return PreviewWorkspaceLayout(context).apply {
            tag = "preview-workspace-root"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors(state).surface)
            addView(appBar(state, actions), fullWidthHeightParams(59))
            addView(divider(state), fullWidthHeightParams(1))
            addView(policyBar(state, actions), fullWidthHeightParams(34))
            if (state.supportsDocumentReview) {
                addView(documentModeSwitch(state, actions), fullWidthHeightParams(36))
            }
            if (state.supportsSearch && state.searchState.isOpen) {
                addView(searchBar(state, actions), fullWidthHeightParams(46))
            }
            addView(contentView, fullWidthWeightedParams())
            if (state.supportsReadingDisplayControls && state.isReadingDisplayPanelOpen) {
                addView(readingDisplayPanel(state, actions), fullWidthHeightParams(266))
            }
            configureReadingDisplayOutsideDismissal(
                enabled = state.supportsReadingDisplayControls && state.isReadingDisplayPanelOpen,
                onOutsideTap = actions.onReadingDisplayClosed
            )
        }
    }

    fun updateChrome(
        workspace: View,
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): Boolean {
        val root = workspace as? PreviewWorkspaceLayout ?: return false
        if (root.tag != "preview-workspace-root") return false
        if (root.getChildAt(0) !is LinearLayout) return false
        if (root.isDispatchingReadingDisplayOutsideDismissal) {
            updateReadingDisplayPanel(root, state, actions)
            updateReadingDisplayButton(root, state, actions)
            root.configureReadingDisplayOutsideDismissal(
                enabled = state.supportsReadingDisplayControls && state.isReadingDisplayPanelOpen,
                onOutsideTap = actions.onReadingDisplayClosed
            )
            return true
        }
        val divider = root.getChildAt(1)
        val policyBar = root.getChildAt(2) as? LinearLayout ?: return false
        root.setBackgroundColor(colors(state).surface)
        divider.setBackgroundColor(colors(state).divider)
        replaceChild(root, 0, appBar(state, actions), fullWidthHeightParams(59))
        policyBar.setBackgroundColor(colors(state).policyBand)
        replaceChild(policyBar, 0, javaScriptBadge(state, actions), ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)))
        replaceChild(policyBar, 1, sandboxLabel(state), sandboxLabelParams())
        updateDocumentModeSwitch(root, state, actions)
        updateSearchBar(root, state, actions)
        updateReadingDisplayPanel(root, state, actions)
        root.configureReadingDisplayOutsideDismissal(
            enabled = state.supportsReadingDisplayControls && state.isReadingDisplayPanelOpen,
            onOutsideTap = actions.onReadingDisplayClosed
        )
        return true
    }

    private fun appBar(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "preview-app-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            setBackgroundColor(colors(state).surface)
            addView(backButton(actions), iconButtonParams())
            addView(titleBlock(state), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (state.supportsReadingDisplayControls) {
                addView(readingDisplayButton(state, actions), iconButtonParams())
            }
            if (state.supportsSearch) {
                addView(searchButton(state, actions), iconButtonParams(start = if (state.supportsReadingDisplayControls) 2 else 0))
            }
            addView(favoriteButton(state, actions), iconButtonParams(start = 8))
        }
    }

    private fun titleBlock(state: PreviewWorkspaceState): LinearLayout {
        val colors = colors(state)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(8), 0)
            addView(
                TextView(context).apply {
                    tag = "preview-title"
                    text = state.title
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(colors.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    includeFontPadding = false
                },
                fullWidthWrapContentParams()
            )
            addView(
                TextView(context).apply {
                    tag = "preview-subtitle"
                    text = "${state.fileTypeLabel} · 本地模式中"
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(colors.muted)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    includeFontPadding = false
                },
                spacedParams(top = 3)
            )
        }
    }

    private fun policyBar(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "preview-policy-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(colors(state).policyBand)
            addView(javaScriptBadge(state, actions), ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(18)))
            addView(sandboxLabel(state), sandboxLabelParams())
        }
    }

    private fun documentModeSwitch(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        val colors = colors(state)
        return LinearLayout(context).apply {
            tag = "preview-document-mode-switch"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(4))
            setBackgroundColor(colors.surface)
            addView(
                documentModeButton(
                    state = state,
                    mode = PreviewDocumentMode.READING,
                    label = "阅读",
                    actions = actions
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(
                documentModeButton(
                    state = state,
                    mode = PreviewDocumentMode.REVIEW,
                    label = "审查",
                    actions = actions
                ),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    leftMargin = dp(6)
                }
            )
        }
    }

    private fun documentModeButton(
        state: PreviewWorkspaceState,
        mode: PreviewDocumentMode,
        label: String,
        actions: PreviewWorkspaceActions
    ): TextView {
        val colors = colors(state)
        val selected = state.documentMode == mode
        return TextView(context).apply {
            tag = if (mode == PreviewDocumentMode.READING) {
                "preview-reading-mode-button"
            } else {
                "preview-review-mode-button"
            }
            text = label
            setTextColor(if (selected) colors.segmentSelectedText else colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = rounded(
                color = if (selected) colors.segmentSelected else colors.segmentSurface,
                radiusDp = 8,
                strokeColor = colors.segmentStroke
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onDocumentModeChanged(mode) }
        }
    }

    private fun updateDocumentModeSwitch(
        workspace: LinearLayout,
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ) {
        val existing = workspace.findChildIndexByTag("preview-document-mode-switch")
        when {
            state.supportsDocumentReview && existing >= 0 -> {
                replaceChild(workspace, existing, documentModeSwitch(state, actions), fullWidthHeightParams(36))
            }
            state.supportsDocumentReview -> {
                val searchIndex = workspace.findChildIndexByTag("preview-search-bar")
                val insertIndex = if (searchIndex >= 0) searchIndex else contentIndex(workspace)
                workspace.addView(documentModeSwitch(state, actions), insertIndex, fullWidthHeightParams(36))
            }
            existing >= 0 -> workspace.removeViewAt(existing)
        }
    }

    private fun updateSearchBar(
        workspace: LinearLayout,
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ) {
        val existing = workspace.findChildIndexByTag("preview-search-bar")
        val shouldShow = state.supportsSearch && state.searchState.isOpen
        when {
            shouldShow && existing >= 0 -> {
                replaceChild(workspace, existing, searchBar(state, actions), fullWidthHeightParams(46))
            }
            shouldShow -> {
                workspace.addView(searchBar(state, actions), contentIndex(workspace), fullWidthHeightParams(42))
            }
            existing >= 0 -> workspace.removeViewAt(existing)
        }
    }

    private fun updateReadingDisplayPanel(
        workspace: LinearLayout,
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ) {
        val existing = workspace.findChildIndexByTag("preview-reading-display-panel")
        val shouldShow = state.supportsReadingDisplayControls && state.isReadingDisplayPanelOpen
        when {
            shouldShow && existing >= 0 -> {
                replaceChild(workspace, existing, readingDisplayPanel(state, actions), fullWidthHeightParams(266))
            }
            shouldShow -> {
                workspace.addView(readingDisplayPanel(state, actions), fullWidthHeightParams(266))
            }
            existing >= 0 -> workspace.removeViewAt(existing)
        }
    }

    private fun updateReadingDisplayButton(
        workspace: LinearLayout,
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ) {
        val button = workspace.findViewWithTag<TextView>("preview-reading-display-button") ?: return
        button.contentDescription = if (state.isReadingDisplayPanelOpen) "关闭正文显示" else "正文显示"
        button.setOnClickListener {
            if (state.isReadingDisplayPanelOpen) {
                actions.onReadingDisplayClosed()
            } else {
                actions.onReadingDisplayOpened()
            }
        }
    }

    private fun contentIndex(workspace: LinearLayout): Int {
        val panelIndex = workspace.findChildIndexByTag("preview-reading-display-panel")
        return if (panelIndex >= 0) {
            (panelIndex - 1).coerceAtLeast(0)
        } else {
            (workspace.childCount - 1).coerceAtLeast(0)
        }
    }

    private fun LinearLayout.findChildIndexByTag(tag: String): Int {
        for (index in 0 until childCount) {
            if (getChildAt(index).tag == tag) {
                return index
            }
        }
        return -1
    }

    private fun searchButton(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.PreviewSearch,
            tint = colors(state).topIcon,
            iconWidthDp = 18f
        ).apply {
            tag = "preview-search-button"
            contentDescription = if (state.searchState.isOpen) "关闭搜索" else "搜索"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (state.searchState.isOpen) {
                    actions.onSearchClosed()
                } else {
                    actions.onSearchOpened()
                }
            }
        }
    }

    private fun readingDisplayButton(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): TextView {
        val colors = colors(state)
        return TextView(context).apply {
            tag = "preview-reading-display-button"
            text = "Aa"
            contentDescription = if (state.isReadingDisplayPanelOpen) "关闭正文显示" else "正文显示"
            setTextColor(colors.topIcon)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            includeFontPadding = false
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (state.isReadingDisplayPanelOpen) {
                    actions.onReadingDisplayClosed()
                } else {
                    actions.onReadingDisplayOpened()
                }
            }
        }
    }

    private fun readingDisplayPanel(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        val colors = colors(state)
        val options = state.readingDisplayOptions.clamped()
        return LinearLayout(context).apply {
            tag = "preview-reading-display-panel"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(9), dp(16), dp(12))
            setBackgroundColor(colors.surface)
            addView(panelHandle(colors), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)))
            addView(readingDisplayTitleRow(colors, actions), fullWidthHeightParams(32))
            addView(
                readingDisplaySliderRow(
                    tag = "preview-reading-font-size-slider",
                    label = "字号",
                    valueLabel = "${options.fontSizeSp}sp",
                    progress = options.fontSizeSp - ReadingDisplayOptions.MIN_FONT_SIZE_SP,
                    max = ReadingDisplayOptions.MAX_FONT_SIZE_SP - ReadingDisplayOptions.MIN_FONT_SIZE_SP,
                    colors = colors,
                    onChanged = { progress ->
                        actions.onReadingDisplayChanged { current ->
                            current.copy(fontSizeSp = ReadingDisplayOptions.MIN_FONT_SIZE_SP + progress).clamped()
                        }
                    }
                ),
                sliderRowParams()
            )
            addView(
                readingDisplaySliderRow(
                    tag = "preview-reading-letter-spacing-slider",
                    label = "字距",
                    valueLabel = "${formatEm(options.letterSpacingEm)}em",
                    progress = steppedProgress(
                        options.letterSpacingEm,
                        ReadingDisplayOptions.MIN_LETTER_SPACING_EM,
                        LETTER_SPACING_STEP
                    ),
                    max = steppedProgress(
                        ReadingDisplayOptions.MAX_LETTER_SPACING_EM,
                        ReadingDisplayOptions.MIN_LETTER_SPACING_EM,
                        LETTER_SPACING_STEP
                    ),
                    colors = colors,
                    onChanged = { progress ->
                        actions.onReadingDisplayChanged { current ->
                            current.copy(
                                letterSpacingEm = steppedValue(
                                    ReadingDisplayOptions.MIN_LETTER_SPACING_EM,
                                    LETTER_SPACING_STEP,
                                    progress
                                )
                            ).clamped()
                        }
                    }
                ),
                sliderRowParams()
            )
            addView(
                readingDisplaySliderRow(
                    tag = "preview-reading-line-height-slider",
                    label = "行距",
                    valueLabel = formatNumber(options.lineHeight),
                    progress = steppedProgress(
                        options.lineHeight,
                        ReadingDisplayOptions.MIN_LINE_HEIGHT,
                        LINE_HEIGHT_STEP
                    ),
                    max = steppedProgress(
                        ReadingDisplayOptions.MAX_LINE_HEIGHT,
                        ReadingDisplayOptions.MIN_LINE_HEIGHT,
                        LINE_HEIGHT_STEP
                    ),
                    colors = colors,
                    onChanged = { progress ->
                        actions.onReadingDisplayChanged { current ->
                            current.copy(
                                lineHeight = steppedValue(
                                    ReadingDisplayOptions.MIN_LINE_HEIGHT,
                                    LINE_HEIGHT_STEP,
                                    progress
                                )
                            ).clamped()
                        }
                    }
                ),
                sliderRowParams()
            )
            addView(
                readingDisplaySliderRow(
                    tag = "preview-reading-paragraph-spacing-slider",
                    label = "段距",
                    valueLabel = "${formatEm(options.paragraphSpacingEm)}em",
                    progress = steppedProgress(
                        options.paragraphSpacingEm,
                        ReadingDisplayOptions.MIN_PARAGRAPH_SPACING_EM,
                        PARAGRAPH_SPACING_STEP
                    ),
                    max = steppedProgress(
                        ReadingDisplayOptions.MAX_PARAGRAPH_SPACING_EM,
                        ReadingDisplayOptions.MIN_PARAGRAPH_SPACING_EM,
                        PARAGRAPH_SPACING_STEP
                    ),
                    colors = colors,
                    onChanged = { progress ->
                        actions.onReadingDisplayChanged { current ->
                            current.copy(
                                paragraphSpacingEm = steppedValue(
                                    ReadingDisplayOptions.MIN_PARAGRAPH_SPACING_EM,
                                    PARAGRAPH_SPACING_STEP,
                                    progress
                                )
                            ).clamped()
                        }
                    }
                ),
                sliderRowParams()
            )
            addView(readingDisplayHint(colors), spacedParams(top = 7))
        }
    }

    private fun panelHandle(colors: WorkspaceColors): LinearLayout {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(
                View(context).apply {
                    tag = "preview-reading-display-handle"
                    background = rounded(colors.segmentStroke, radiusDp = 3)
                },
                LinearLayout.LayoutParams(dp(34), dp(4))
            )
        }
    }

    private fun readingDisplayTitleRow(
        colors: WorkspaceColors,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = "正文显示"
                    setTextColor(colors.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                TextView(context).apply {
                    tag = "preview-reading-display-reset-button"
                    text = "重置默认"
                    contentDescription = "重置正文显示默认值"
                    setTextColor(colors.topIcon)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, dp(10), 0)
                    background = rounded(colors.segmentSurface, radiusDp = 8, strokeColor = colors.segmentStroke)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { actions.onReadingDisplayReset() }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
            )
        }
    }

    private fun readingDisplaySliderRow(
        tag: String,
        label: String,
        valueLabel: String,
        progress: Int,
        max: Int,
        colors: WorkspaceColors,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = label
                    setTextColor(colors.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT)
            )
            addView(
                SeekBar(context).apply {
                    this.tag = tag
                    this.max = max
                    this.progress = progress.coerceIn(0, max)
                    var tracking = false
                    var pendingProgress = this.progress
                    var appliedProgress = this.progress
                    progressTintList = ColorStateList.valueOf(colors.topIcon)
                    progressBackgroundTintList = ColorStateList.valueOf(colors.segmentStroke)
                    thumb = oval(colors.topIcon, 10)
                    splitTrack = false
                    setPadding(0, 0, 0, 0)
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                pendingProgress = progress
                                if (fromUser && !tracking) {
                                    onChanged(progress)
                                    appliedProgress = progress
                                }
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                                tracking = true
                                pendingProgress = seekBar?.progress ?: pendingProgress
                            }

                            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                                tracking = false
                                if (pendingProgress != appliedProgress) {
                                    onChanged(pendingProgress)
                                    appliedProgress = pendingProgress
                                }
                            }
                        }
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                TextView(context).apply {
                    text = valueLabel
                    setTextColor(colors.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun readingDisplayHint(colors: WorkspaceColors): TextView {
        return TextView(context).apply {
            tag = "preview-reading-display-hint"
            text = "松开滑杆后应用到 Markdown 与 TXT；HTML 与 ZIP 保留原文件样式。"
            setTextColor(colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            includeFontPadding = false
            maxLines = 2
        }
    }

    private fun searchBar(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): LinearLayout {
        val colors = colors(state)
        return LinearLayout(context).apply {
            tag = "preview-search-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setBackgroundColor(colors.surface)
            addView(searchInput(state, actions), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(searchCount(state), LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(searchActionButton("preview-search-previous-button", "↑", "上一个匹配", colors) { actions.onSearchPrevious() }, searchActionParams())
            addView(searchActionButton("preview-search-next-button", "↓", "下一个匹配", colors) { actions.onSearchNext() }, searchActionParams())
            addView(searchActionButton("preview-search-clear-button", "×", "清空搜索", colors) { actions.onSearchCleared() }, searchActionParams())
            addView(searchActionButton("preview-search-close-button", "关闭", "关闭搜索", colors) { actions.onSearchClosed() }, closeSearchParams())
        }
    }

    private fun searchInput(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): EditText {
        val colors = colors(state)
        return EditText(context).apply {
            tag = "preview-search-input"
            setText(state.searchState.query)
            setTextColor(colors.text)
            setHintTextColor(colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            hint = "搜索文档"
            setSingleLine(true)
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(colors.segmentSurface, radiusDp = 8, strokeColor = colors.segmentStroke)
            setPadding(dp(10), 0, dp(10), 0)
            setSelection(text.length)
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        actions.onSearchQueryChanged(s?.toString().orEmpty())
                    }
                }
            )
        }
    }

    private fun searchCount(state: PreviewWorkspaceState): TextView {
        val colors = colors(state)
        return TextView(context).apply {
            tag = "preview-search-count"
            text = state.searchState.displayLabel()
            setTextColor(colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
    }

    private fun searchActionButton(
        tag: String,
        label: String,
        contentDescription: String,
        colors: WorkspaceColors,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            this.tag = tag
            text = label
            this.contentDescription = contentDescription
            setTextColor(colors.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label == "关闭") 11f else 15f)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = rounded(colors.segmentSurface, radiusDp = 8, strokeColor = colors.segmentStroke)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun javaScriptBadge(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): TextView {
        val colors = colors(state)
        return TextView(context).apply {
            tag = "preview-js-badge"
            text = state.javaScriptStatus
            setTextColor(colors.policyText)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            includeFontPadding = false
            gravity = Gravity.CENTER
            background = rounded(colors.policyBadge, radiusDp = 6, strokeColor = colors.policyStroke)
            setPadding(dp(9), 0, dp(9), 0)
            setCompoundDrawablesWithIntrinsicBounds(
                instantIconDrawable(context, InstantIcons.Shield, colors.policyText, 11f),
                null,
                null,
                null
            )
            compoundDrawablePadding = dp(5)
            if (state.supportsScriptAuthorization) {
                isClickable = true
                isFocusable = true
                contentDescription = if (state.javaScriptStatus == "JS 已启用") {
                    "撤销当前文档的脚本授权"
                } else {
                    "为当前文档允许脚本"
                }
                setOnClickListener { actions.onToggleDocumentJavaScript() }
            }
        }
    }

    private fun sandboxLabel(state: PreviewWorkspaceState): TextView {
        val colors = colors(state)
        return TextView(context).apply {
            tag = "preview-sandbox-label"
            text = state.navigationStatus
            setTextColor(colors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun backButton(actions: PreviewWorkspaceActions): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.Back,
            tint = COLOR_TOP_ICON,
            iconWidthDp = 18f
        ).apply {
            tag = "preview-back-button"
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onBack() }
        }
    }

    private fun favoriteButton(
        state: PreviewWorkspaceState,
        actions: PreviewWorkspaceActions
    ): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.Star,
            tint = colors(state).topIcon,
            iconWidthDp = 17f,
            active = state.isFavorite,
            activeTint = colors(state).favoriteOn
        ).apply {
            tag = "preview-favorite-button"
            contentDescription = if (state.isFavorite) "取消收藏" else "收藏"
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onToggleFavorite() }
        }
    }

    private fun divider(state: PreviewWorkspaceState): View {
        return View(context).apply {
            setBackgroundColor(colors(state).divider)
        }
    }

    private fun fullWidthHeightParams(heightDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun fullWidthWrapContentParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullWidthWeightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private fun iconButtonParams(): LinearLayout.LayoutParams {
        return iconButtonParams(start = 0)
    }

    private fun iconButtonParams(start: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(34), dp(44)).apply {
            leftMargin = dp(start)
        }
    }

    private fun spacedParams(top: Int = 0): LinearLayout.LayoutParams {
        return fullWidthWrapContentParams().apply {
            topMargin = dp(top)
        }
    }

    private fun sandboxLabelParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = dp(8)
        }
    }

    private fun searchActionParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(5)
        }
    }

    private fun closeSearchParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(5)
        }
    }

    private fun sliderRowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
    }

    private fun replaceChild(parent: ViewGroup, index: Int, child: View, params: ViewGroup.LayoutParams) {
        parent.removeViewAt(index)
        parent.addView(child, index, params)
    }

    private fun colors(state: PreviewWorkspaceState): WorkspaceColors {
        return if (state.isDarkTheme) {
            WorkspaceColors(
                surface = COLOR_DARK_SURFACE,
                text = COLOR_DARK_TEXT,
                muted = COLOR_DARK_MUTED,
                divider = COLOR_DARK_DIVIDER,
                topIcon = COLOR_DARK_TOP_ICON,
                topIconSurface = COLOR_DARK_TOP_ICON_SURFACE,
                policyBand = COLOR_DARK_POLICY_BAND,
                policyText = COLOR_DARK_POLICY_TEXT,
                policyBadge = COLOR_DARK_POLICY_BADGE,
                policyStroke = COLOR_DARK_POLICY_STROKE,
                segmentSurface = COLOR_DARK_SEGMENT_SURFACE,
                segmentSelected = COLOR_DARK_SEGMENT_SELECTED,
                segmentStroke = COLOR_DARK_SEGMENT_STROKE,
                segmentSelectedText = COLOR_DARK_TEXT,
                favoriteOn = COLOR_FAVORITE_ON
            )
        } else {
            WorkspaceColors(
                surface = COLOR_SURFACE,
                text = COLOR_TEXT,
                muted = COLOR_MUTED,
                divider = COLOR_DIVIDER,
                topIcon = COLOR_TOP_ICON,
                topIconSurface = COLOR_TOP_ICON_SURFACE,
                policyBand = COLOR_POLICY_BAND,
                policyText = COLOR_POLICY_TEXT,
                policyBadge = COLOR_POLICY_BADGE,
                policyStroke = COLOR_POLICY_STROKE,
                segmentSurface = COLOR_SEGMENT_SURFACE,
                segmentSelected = COLOR_TEXT,
                segmentStroke = COLOR_SEGMENT_STROKE,
                segmentSelectedText = Color.WHITE,
                favoriteOn = COLOR_FAVORITE_ON
            )
        }
    }

    private data class WorkspaceColors(
        val surface: Int,
        val text: Int,
        val muted: Int,
        val divider: Int,
        val topIcon: Int,
        val topIconSurface: Int,
        val policyBand: Int,
        val policyText: Int,
        val policyBadge: Int,
        val policyStroke: Int,
        val segmentSurface: Int,
        val segmentSelected: Int,
        val segmentStroke: Int,
        val segmentSelectedText: Int,
        val favoriteOn: Int
    )

    private fun rounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun oval(color: Int, sizeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(sizeDp), dp(sizeDp))
        }
    }

    private fun steppedProgress(value: Float, min: Float, step: Float): Int {
        return ((value - min) / step).roundToInt()
    }

    private fun steppedValue(min: Float, step: Float, progress: Int): Float {
        return ((min + step * progress) * 100f).roundToInt() / 100f
    }

    private fun formatNumber(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun formatEm(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        val COLOR_SURFACE: Int = Color.WHITE
        val COLOR_TEXT: Int = Color.rgb(15, 23, 42)
        val COLOR_MUTED: Int = Color.rgb(134, 153, 184)
        val COLOR_DIVIDER: Int = Color.rgb(235, 240, 247)
        val COLOR_TOP_ICON: Int = Color.rgb(142, 162, 194)
        val COLOR_TOP_ICON_SURFACE: Int = Color.rgb(248, 251, 255)
        val COLOR_FAVORITE_ON: Int = Color.rgb(37, 99, 235)
        val COLOR_POLICY_BAND: Int = Color.rgb(249, 251, 254)
        val COLOR_POLICY_TEXT: Int = Color.rgb(30, 64, 115)
        val COLOR_POLICY_BADGE: Int = Color.rgb(239, 246, 255)
        val COLOR_POLICY_STROKE: Int = Color.rgb(216, 230, 249)
        val COLOR_SEGMENT_SURFACE: Int = Color.rgb(248, 251, 255)
        val COLOR_SEGMENT_STROKE: Int = Color.rgb(226, 234, 244)
        val COLOR_DARK_SURFACE: Int = Color.rgb(28, 28, 29)
        val COLOR_DARK_TEXT: Int = Color.rgb(244, 247, 251)
        val COLOR_DARK_MUTED: Int = Color.rgb(142, 162, 194)
        val COLOR_DARK_DIVIDER: Int = Color.rgb(43, 49, 61)
        val COLOR_DARK_TOP_ICON: Int = Color.rgb(156, 174, 204)
        val COLOR_DARK_TOP_ICON_SURFACE: Int = Color.rgb(32, 35, 42)
        val COLOR_DARK_POLICY_BAND: Int = Color.rgb(24, 26, 31)
        val COLOR_DARK_POLICY_TEXT: Int = Color.rgb(170, 196, 238)
        val COLOR_DARK_POLICY_BADGE: Int = Color.rgb(34, 42, 55)
        val COLOR_DARK_POLICY_STROKE: Int = Color.rgb(53, 66, 86)
        val COLOR_DARK_SEGMENT_SURFACE: Int = Color.rgb(24, 26, 31)
        val COLOR_DARK_SEGMENT_SELECTED: Int = Color.rgb(52, 63, 82)
        val COLOR_DARK_SEGMENT_STROKE: Int = Color.rgb(53, 66, 86)
        const val LETTER_SPACING_STEP = 0.01f
        const val LINE_HEIGHT_STEP = 0.05f
        const val PARAGRAPH_SPACING_STEP = 0.04f
    }
}
