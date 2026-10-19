package com.personalai.instantread.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.personalai.instantread.BuildConfig

data class RecentFileItem(
    val id: String,
    val title: String,
    val fileTypeLabel: String,
    val isFavorite: Boolean,
    val warningLabel: String? = null
)

enum class HomeListFilter {
    ALL,
    FAVORITES
}

class HomeActions(
    val onChooseFile: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onClearCache: () -> Unit,
    val onOpenHistory: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onDeleteHistory: (String) -> Unit,
    val onCancelDelete: () -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onFilterChanged: (HomeListFilter) -> Unit = {},
    val onSearchQueryChanged: (String) -> Unit = {}
)

class HomeViewFactory(
    private val context: Context
) {
    fun create(
        message: String?,
        recentFiles: List<RecentFileItem>,
        isDarkTheme: Boolean = false,
        selectedFilter: HomeListFilter = HomeListFilter.ALL,
        searchQuery: String = "",
        totalRecentCount: Int = recentFiles.size,
        favoriteRecentCount: Int = recentFiles.count { it.isFavorite },
        actions: HomeActions,
        pendingDeleteFile: RecentFileItem? = null
    ): FrameLayout {
        val theme = HomeTheme.from(isDarkTheme)
        val page = FrameLayout(context).apply {
            setBackgroundColor(theme.appBackground)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.surface)
            addView(createAppBar(actions, theme), fullWidthHeightParams(59))
            addView(
                createContent(
                    message,
                    recentFiles,
                    selectedFilter,
                    searchQuery,
                    totalRecentCount,
                    favoriteRecentCount,
                    actions,
                    theme
                ),
                weightedFullWidthParams()
            )
            addView(createFooter(theme), fullWidthHeightParams(64))
        }
        page.addView(root, centeredPhoneParams())

        if (pendingDeleteFile != null) {
            page.addView(createDeleteSheet(pendingDeleteFile, actions, theme), bottomSheetParams())
        }

        return page
    }

    private fun createAppBar(actions: HomeActions, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-app-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), 0, dp(8), 0)
            setBackgroundColor(theme.surface)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(context).apply {
                            text = "即阅"
                            setTextColor(theme.text)
                            setTypeface(typeface, Typeface.BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                            includeFontPadding = false
                        },
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        TextView(context).apply {
                            text = "LOCAL PREVIEW"
                            setTextColor(theme.muted)
                            setTypeface(typeface, Typeface.BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                            includeFontPadding = false
                        },
                        inlineParams(start = 6)
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(createThemeButton(actions, theme), iconButtonParams())
            addView(
                createSettingsButton(actions, theme),
                iconButtonParams(start = 8)
            )
        }
    }

    private fun createContent(
        message: String?,
        recentFiles: List<RecentFileItem>,
        selectedFilter: HomeListFilter,
        searchQuery: String,
        totalRecentCount: Int,
        favoriteRecentCount: Int,
        actions: HomeActions,
        theme: HomeTheme
    ): ScrollView {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(23), dp(13), dp(24))
            addView(createPrimaryAction(actions, theme), fixedSpacedParams(height = 52, bottom = 15))
            addView(createPrivacyRow(theme), centeredFixedSpacedParams(height = 21, bottom = 14))
            addView(createSearchRow(searchQuery, actions, theme), fixedSpacedParams(height = 36, bottom = 18))
            if (message != null) {
                addView(createMessage(message, theme), spacedParams(bottom = 20))
            }
            addRecentList(this, recentFiles, selectedFilter, searchQuery, totalRecentCount, favoriteRecentCount, actions, theme)
        }

        return ScrollView(context).apply {
            setFillViewport(true)
            setBackgroundColor(theme.surface)
            addView(content, fullWidthWrapContentParams())
        }
    }

    private fun createPrimaryAction(actions: HomeActions, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-primary-action"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(theme.primaryActionBackground, radiusDp = 13)
            setOnClickListener { actions.onChooseFile() }
            addView(
                TextView(context).apply {
                        text = "+  选择本地文件"
                        gravity = Gravity.CENTER
                    setTextColor(theme.primaryActionText)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    includeFontPadding = false
                },
                fullWidthWrapContentParams()
            )
        }
    }

    private fun createPrivacyRow(theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-privacy-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                        text = "⊙  PRIVACY PROTECTED"
                    setTextColor(theme.privacy)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    includeFontPadding = false
                    background = rounded(theme.privacySurface, radiusDp = 3)
                    setPadding(dp(7), 0, dp(7), 0)
                    gravity = Gravity.CENTER
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                TextView(context).apply {
                        text = "文件仅在本地解析"
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                },
                inlineParams(start = 8)
            )
        }
    }

    private fun createMessage(message: String, theme: HomeTheme): TextView {
        return TextView(context).apply {
            text = message
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(0f, 1.12f)
            setTextIsSelectable(true)
            background = rounded(theme.messageSurface, radiusDp = 12, strokeColor = theme.divider)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    private fun createSearchRow(searchQuery: String, actions: HomeActions, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-search-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(theme.searchSurface, radiusDp = 10, strokeColor = theme.divider)
            setPadding(dp(11), 0, dp(6), 0)
            addView(
                EditText(context).apply {
                    tag = "home-search-input"
                    hint = "搜索最近记录"
                    setText(searchQuery)
                    if (searchQuery.isNotBlank()) {
                        setSelection(searchQuery.length)
                        post { requestFocus() }
                    }
                    setSingleLine(true)
                    setTextColor(theme.text)
                    setHintTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                    background = null
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            val next = s?.toString().orEmpty()
                            if (next != searchQuery) actions.onSearchQueryChanged(next)
                        }
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
            if (searchQuery.isNotBlank()) {
                addView(
                    TextView(context).apply {
                        tag = "home-search-clear-button"
                        text = "×"
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(theme.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        contentDescription = "清空搜索"
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { actions.onSearchQueryChanged("") }
                    },
                    ViewGroup.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT)
                )
                addView(
                    TextView(context).apply {
                        tag = "home-search-cancel-button"
                        text = "取消"
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(theme.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        contentDescription = "退出搜索"
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { actions.onSearchQueryChanged("") }
                    },
                    ViewGroup.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        }
    }

    private fun addRecentList(
        root: LinearLayout,
        recentFiles: List<RecentFileItem>,
        selectedFilter: HomeListFilter,
        searchQuery: String,
        totalRecentCount: Int,
        favoriteRecentCount: Int,
        actions: HomeActions,
        theme: HomeTheme
    ) {
        root.addView(
            createRecentHeader(
                selectedFilter,
                recentFiles.size,
                searchQuery,
                totalRecentCount,
                favoriteRecentCount,
                actions,
                theme
            ),
            spacedParams(bottom = 14)
        )
        val list = LinearLayout(context).apply {
            tag = "home-recent-list"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.surface)
        }
        if (recentFiles.isEmpty()) {
            list.addView(
                TextView(context).apply {
                    text = when {
                        searchQuery.isNotBlank() && selectedFilter == HomeListFilter.FAVORITES -> "收藏中没有匹配记录"
                        searchQuery.isNotBlank() -> "没有匹配的记录"
                        selectedFilter == HomeListFilter.FAVORITES -> "暂无收藏文件"
                        else -> "暂无最近记录"
                    }
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(dp(1), 0, dp(1), 0)
                },
                fullWidthHeightParams(73)
            )
        } else {
            recentFiles.forEachIndexed { index, item ->
                list.addView(createRecentRow(item, searchQuery, actions, theme), fullWidthHeightParams(73))
                if (index < recentFiles.lastIndex) {
                    list.addView(divider(theme), fullWidthHeightParams(1))
                }
            }
        }
        root.addView(list, fullWidthWrapContentParams())
    }

    private fun createRecentHeader(
        selectedFilter: HomeListFilter,
        resultCount: Int,
        searchQuery: String,
        totalRecentCount: Int,
        favoriteRecentCount: Int,
        actions: HomeActions,
        theme: HomeTheme
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-recent-header"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    val base = if (selectedFilter == HomeListFilter.FAVORITES) "收藏文件" else "最近记录"
                    text = if (searchQuery.isBlank()) base else "$base · $resultCount"
                    setTextColor(theme.muted)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(createFilterButton("全部 $totalRecentCount", "home-filter-all", HomeListFilter.ALL, totalRecentCount, selectedFilter, actions, theme))
            addView(
                createFilterButton("收藏 $favoriteRecentCount", "home-filter-favorites", HomeListFilter.FAVORITES, favoriteRecentCount, selectedFilter, actions, theme),
                inlineParams(start = 4)
            )
        }
    }

    private fun createFilterButton(
        label: String,
        viewTag: String,
        filter: HomeListFilter,
        count: Int,
        selectedFilter: HomeListFilter,
        actions: HomeActions,
        theme: HomeTheme
    ): TextView {
        val isSelected = filter == selectedFilter
        return TextView(context).apply {
            tag = viewTag
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (isSelected) theme.primaryActionText else theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            minWidth = dp(52)
            minimumWidth = dp(52)
            minHeight = dp(24)
            minimumHeight = dp(24)
            background = rounded(
                color = if (isSelected) theme.primaryActionBackground else Color.TRANSPARENT,
                radiusDp = 7,
                strokeColor = if (isSelected) null else theme.divider
            )
            setPadding(dp(9), 0, dp(9), 0)
            isClickable = true
            isFocusable = true
            contentDescription = if (filter == HomeListFilter.FAVORITES) "显示收藏文件，$count 个" else "显示全部最近文件，$count 个"
            setOnClickListener { actions.onFilterChanged(filter) }
        }
    }

    private fun createRecentRow(
        item: RecentFileItem,
        searchQuery: String,
        actions: HomeActions,
        theme: HomeTheme
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "recent-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.surface)
            setPadding(0, 0, 0, 0)
            setOnClickListener { actions.onOpenHistory(item.id) }
            addView(
                fileIconView(item, theme),
                ViewGroup.LayoutParams(dp(40), dp(40))
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), 0, dp(8), 0)
                    addView(
                        createRecentTitleRow(item, searchQuery, theme),
                        fullWidthWrapContentParams()
                    )
                    addView(
                        createRecentMetaRow(item, searchQuery, theme),
                        spacedParams(top = 5)
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(
                favoriteIconButton(item, actions, theme),
                iconButtonParams()
            )
            addView(
                deleteIconButton(item, actions),
                iconButtonParams()
            )
        }
    }

    private fun fileIconView(item: RecentFileItem, theme: HomeTheme): View {
        return object : View(context) {
            private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fileTypeColor(item.fileTypeLabel)
                style = Paint.Style.STROKE
                strokeWidth = dp(1).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fileTypeSurface(item.fileTypeLabel)
                style = Paint.Style.FILL
            }

            init {
                tag = "recent-file-icon"
                background = rounded(theme.iconTile, radiusDp = 10, strokeColor = theme.divider)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = width / 2f - dp(6)
                val top = height / 2f - dp(8)
                val right = width / 2f + dp(6)
                val bottom = height / 2f + dp(8)
                val fold = dp(4).toFloat()
                val body = Path().apply {
                    moveTo(left, top)
                    lineTo(right - fold, top)
                    lineTo(right, top + fold)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.drawPath(body, fill)
                canvas.drawPath(body, line)
                canvas.drawLine(right - fold, top, right - fold, top + fold, line)
                canvas.drawLine(right - fold, top + fold, right, top + fold, line)
                drawTypeMark(canvas, item.fileTypeLabel, left, top, right, bottom)
            }

            private fun drawTypeMark(
                canvas: Canvas,
                label: String,
                left: Float,
                top: Float,
                right: Float,
                bottom: Float
            ) {
                when {
                    label.contains("HTML", ignoreCase = true) ->
                        canvas.drawCircle((left + right) / 2f, (top + bottom) / 2f + dp(2), dp(2).toFloat(), line)
                    label.contains("MARKDOWN", ignoreCase = true) || label.contains("Markdown", ignoreCase = true) -> {
                        canvas.drawLine(left + dp(3), bottom - dp(4), right - dp(3), bottom - dp(4), line)
                        canvas.drawLine(left + dp(3), bottom - dp(8), right - dp(3), bottom - dp(8), line)
                    }
                    label.contains("TEXT", ignoreCase = true) || label.contains("TXT", ignoreCase = true) -> {
                        canvas.drawLine(left + dp(3), top + dp(6), right - dp(3), top + dp(6), line)
                        canvas.drawLine(left + dp(3), top + dp(10), right - dp(3), top + dp(10), line)
                        canvas.drawLine(left + dp(3), top + dp(14), right - dp(5), top + dp(14), line)
                    }
                    label.contains("ZIP", ignoreCase = true) -> {
                        canvas.drawLine(left + dp(5), top + dp(5), left + dp(5), bottom - dp(4), line)
                        canvas.drawLine(left + dp(7), top + dp(7), left + dp(7), bottom - dp(6), line)
                    }
                }
            }
        }
    }

    private fun favoriteIconButton(item: RecentFileItem, actions: HomeActions, theme: HomeTheme): View {
        return object : View(context) {
            private val starStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.favoriteIcon
                style = Paint.Style.STROKE
                strokeWidth = dpFloat(1.7f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val starFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.favoriteIcon
                style = Paint.Style.FILL
            }
            private val starPath = Path()

            init {
                tag = "recent-favorite-button"
                contentDescription = if (item.isFavorite) "取消收藏" else "收藏"
                isClickable = true
                isFocusable = true
                setOnClickListener { actions.onToggleFavorite(item.id) }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val centerX = width / 2f
                val centerY = height / 2f
                val outer = minOf(width, height) * 0.25f
                val inner = outer * 0.47f
                starPath.reset()
                for (index in 0 until 10) {
                    val radius = if (index % 2 == 0) outer else inner
                    val angle = Math.toRadians((-90 + index * 36).toDouble())
                    val x = centerX + kotlin.math.cos(angle).toFloat() * radius
                    val y = centerY + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) {
                        starPath.moveTo(x, y)
                    } else {
                        starPath.lineTo(x, y)
                    }
                }
                starPath.close()
                starFill.alpha = if (item.isFavorite) 255 else 0
                canvas.drawPath(starPath, starFill)
                canvas.drawPath(starPath, starStroke)
            }
        }
    }

    private fun deleteIconButton(item: RecentFileItem, actions: HomeActions): View {
        return object : View(context) {
            private val trashStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DELETE_ICON
                style = Paint.Style.STROKE
                strokeWidth = dpFloat(1.55f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            init {
                tag = "recent-delete-button"
                isClickable = true
                setOnClickListener { actions.onDeleteHistory(item.id) }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val centerX = width / 2f
                val iconCenterY = height / 2f
                val bodyWidth = dpFloat(10.8f)
                val bodyHeight = dpFloat(14f)
                val left = centerX - bodyWidth / 2f
                val totalHeight = bodyHeight + dpFloat(3f)
                val top = iconCenterY - totalHeight / 2f + dpFloat(3f)
                val right = centerX + bodyWidth / 2f
                val bottom = top + bodyHeight
                canvas.drawRoundRect(
                    RectF(left, top, right, bottom),
                    dpFloat(1.7f),
                    dpFloat(1.7f),
                    trashStroke
                )
                val lidY = top
                val handleTop = lidY - dpFloat(3f)
                val handleLeft = centerX - dpFloat(2.8f)
                val handleRight = centerX + dpFloat(2.8f)
                canvas.drawLine(left - dpFloat(2.3f), lidY, right + dpFloat(2.3f), lidY, trashStroke)
                canvas.drawLine(handleLeft, lidY, handleLeft, handleTop, trashStroke)
                canvas.drawLine(handleLeft, handleTop, handleRight, handleTop, trashStroke)
                canvas.drawLine(handleRight, handleTop, handleRight, lidY, trashStroke)
                canvas.drawLine(centerX - dpFloat(2.8f), top + dpFloat(3.2f), centerX - dpFloat(2.8f), bottom - dpFloat(3f), trashStroke)
                canvas.drawLine(centerX + dpFloat(2.8f), top + dpFloat(3.2f), centerX + dpFloat(2.8f), bottom - dpFloat(3f), trashStroke)
            }
        }
    }

    private fun createRecentMetaRow(item: RecentFileItem, searchQuery: String, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = highlightedSearchText(item.fileTypeLabel, searchQuery, theme.searchHighlight)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun createRecentTitleRow(item: RecentFileItem, searchQuery: String, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = highlightedSearchText(item.title, searchQuery, theme.searchHighlight)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (item.warningLabel != null) {
                addView(
                    TextView(context).apply {
                        tag = "recent-warning-badge"
                        text = item.warningLabel
                        setTextColor(Color.WHITE)
                        setTypeface(typeface, Typeface.BOLD)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                        includeFontPadding = false
                        gravity = Gravity.CENTER
                        background = rounded(COLOR_DANGER, radiusDp = 2)
                        setPadding(dp(3), 0, dp(3), 0)
                    },
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(12)
                    )
                )
            }
        }
    }

    private fun highlightedSearchText(value: String, searchQuery: String, highlightColor: Int): CharSequence {
        val needle = searchQuery.trim()
        if (needle.isEmpty()) return value
        var index = value.indexOf(needle, ignoreCase = true)
        if (index < 0) return value
        val result = SpannableString(value)
        while (index >= 0) {
            result.setSpan(
                BackgroundColorSpan(highlightColor),
                index,
                index + needle.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = value.indexOf(needle, startIndex = index + needle.length, ignoreCase = true)
        }
        return result
    }

    private fun createDeleteSheet(item: RecentFileItem, actions: HomeActions, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-delete-sheet"
            orientation = LinearLayout.VERTICAL
            background = rounded(theme.surface, radiusDp = 24, strokeColor = theme.divider)
            setPadding(dp(20), dp(18), dp(20), dp(20))
            addView(
                TextView(context).apply {
                    text = "删除这条最近记录？"
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                },
                fullWidthWrapContentParams()
            )
            addView(
                TextView(context).apply {
                    text = "会移除这条记录、即阅保存的应用内副本和对应缓存；手机里的原始文件不会被删除。"
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                },
                spacedParams(top = 8, bottom = 16)
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        sheetButton("取消", theme.secondaryAction, theme.text).apply {
                            setOnClickListener { actions.onCancelDelete() }
                        },
                        weightedActionParams(endMargin = 6)
                    )
                    addView(
                        sheetButton("删除", COLOR_DANGER, Color.WHITE).apply {
                            setOnClickListener { actions.onDeleteHistory(item.id) }
                        },
                        weightedActionParams(startMargin = 6)
                    )
                },
                fullWidthWrapContentParams()
            )
        }
    }

    private fun createFooter(theme: HomeTheme): TextView {
        return TextView(context).apply {
            tag = "home-footer"
            text = "版本 ${BuildConfig.VERSION_NAME} • 系统安全解析已开启"
            gravity = Gravity.CENTER
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(8), 0, dp(8), dp(8))
        }
    }

    private fun createThemeButton(actions: HomeActions, theme: HomeTheme): View {
        return object : View(context) {
            private val backgroundFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.topIconSurface
                style = Paint.Style.FILL
            }
            private val moonFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.topIcon
                style = Paint.Style.FILL
            }
            private val moonCutout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.topIconSurface
                style = Paint.Style.FILL
            }
            private val sunStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_SUN_ICON
                style = Paint.Style.STROKE
                strokeWidth = dpFloat(1.35f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val sunFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_SUN_ICON
                style = Paint.Style.FILL
            }
            init {
                tag = "home-theme-button"
                contentDescription = if (theme.isDark) "日间模式" else "夜间模式"
                isClickable = true
                isFocusable = true
                setOnClickListener { actions.onToggleTheme() }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val centerX = width / 2f
                val centerY = height / 2f
                val buttonRadius = minOf(width, height) * 0.31f
                canvas.drawCircle(centerX, centerY, buttonRadius, backgroundFill)

                if (theme.isDark) {
                    drawSunIcon(canvas, centerX, centerY, minOf(width, height).toFloat(), sunFill, sunStroke)
                    return
                }

                val moonRadius = minOf(width, height) * 0.18f
                canvas.drawCircle(centerX - moonRadius * 0.12f, centerY, moonRadius, moonFill)
                canvas.drawCircle(
                    centerX + moonRadius * 0.42f,
                    centerY - moonRadius * 0.12f,
                    moonRadius * 0.84f,
                    moonCutout
                )
            }
        }
    }

    private fun createSettingsButton(actions: HomeActions, theme: HomeTheme): View {
        return object : View(context) {
            private val gearStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.topIcon
                style = Paint.Style.STROKE
                strokeWidth = dpFloat(1.35f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val gearPath = Path()

            init {
                tag = "home-settings-button"
                contentDescription = "设置"
                isClickable = true
                isFocusable = true
                setOnClickListener { actions.onOpenSettings() }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val centerX = width / 2f
                val centerY = height / 2f
                val size = minOf(width, height).toFloat()
                val outer = size * 0.19f
                val shoulder = size * 0.145f
                val toothHalfAngle = Math.toRadians(9.0)
                val valleyHalfAngle = Math.toRadians(22.5)
                gearPath.reset()
                for (index in 0 until 8) {
                    val centerAngle = Math.toRadians((-90 + index * 45).toDouble())
                    val valleyStart = centerAngle - valleyHalfAngle
                    val toothStart = centerAngle - toothHalfAngle
                    val toothEnd = centerAngle + toothHalfAngle
                    val valleyEnd = centerAngle + valleyHalfAngle
                    if (index == 0) {
                        gearPath.moveTo(
                            centerX + kotlin.math.cos(valleyStart).toFloat() * shoulder,
                            centerY + kotlin.math.sin(valleyStart).toFloat() * shoulder
                        )
                    }
                    gearPath.lineTo(
                        centerX + kotlin.math.cos(toothStart).toFloat() * outer,
                        centerY + kotlin.math.sin(toothStart).toFloat() * outer
                    )
                    gearPath.lineTo(
                        centerX + kotlin.math.cos(toothEnd).toFloat() * outer,
                        centerY + kotlin.math.sin(toothEnd).toFloat() * outer
                    )
                    gearPath.lineTo(
                        centerX + kotlin.math.cos(valleyEnd).toFloat() * shoulder,
                        centerY + kotlin.math.sin(valleyEnd).toFloat() * shoulder
                    )
                }
                gearPath.close()
                canvas.drawPath(gearPath, gearStroke)
                canvas.drawCircle(centerX, centerY, size * 0.075f, gearStroke)
            }
        }
    }

    private fun iconButton(label: String): Button {
        return Button(context).apply {
            text = label
            setAllCaps(false)
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = transparentBackground()
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun drawSunIcon(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
        sunFill: Paint,
        sunStroke: Paint
    ) {
        val radius = size * 0.105f
        canvas.drawCircle(centerX, centerY, radius, sunFill)
        val rayInner = size * 0.18f
        val rayOuter = size * 0.25f
        for (index in 0 until 8) {
            val angle = Math.toRadians((index * 45).toDouble())
            val startX = centerX + kotlin.math.cos(angle).toFloat() * rayInner
            val startY = centerY + kotlin.math.sin(angle).toFloat() * rayInner
            val endX = centerX + kotlin.math.cos(angle).toFloat() * rayOuter
            val endY = centerY + kotlin.math.sin(angle).toFloat() * rayOuter
            canvas.drawLine(startX, startY, endX, endY, sunStroke)
        }
    }

    private fun sheetButton(label: String, backgroundColor: Int, textColor: Int): Button {
        return Button(context).apply {
            text = label
            setAllCaps(false)
            minHeight = dp(48)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(backgroundColor, radiusDp = 14)
        }
    }

    private fun divider(): View {
        return View(context).apply {
            setBackgroundColor(COLOR_DIVIDER)
        }
    }

    private fun divider(theme: HomeTheme): View {
        return View(context).apply {
            setBackgroundColor(theme.divider)
        }
    }

    private fun fileTypeIcon(label: String): String {
        return when {
            label.contains("HTML", ignoreCase = true) -> "HTML"
            label.contains("Markdown", ignoreCase = true) -> "MD"
            label.contains("MARKDOWN", ignoreCase = true) -> "MD"
            label.contains("TEXT", ignoreCase = true) -> "TXT"
            label.contains("TXT", ignoreCase = true) -> "TXT"
            label.contains("ZIP", ignoreCase = true) -> "ZIP"
            else -> "FILE"
        }
    }

    private fun fileTypeColor(label: String): Int {
        return when {
            label.contains("HTML", ignoreCase = true) -> COLOR_HTML
            label.contains("Markdown", ignoreCase = true) -> COLOR_MARKDOWN
            label.contains("MARKDOWN", ignoreCase = true) -> COLOR_MARKDOWN
            label.contains("ZIP", ignoreCase = true) -> COLOR_ZIP
            label.contains("TEXT", ignoreCase = true) -> COLOR_MUTED
            else -> COLOR_TEXT
        }
    }

    private fun fileTypeSurface(label: String): Int {
        return when {
            label.contains("HTML", ignoreCase = true) -> COLOR_HTML_SURFACE
            label.contains("Markdown", ignoreCase = true) -> COLOR_MARKDOWN_SURFACE
            label.contains("MARKDOWN", ignoreCase = true) -> COLOR_MARKDOWN_SURFACE
            label.contains("ZIP", ignoreCase = true) -> COLOR_ZIP_SURFACE
            label.contains("TEXT", ignoreCase = true) -> COLOR_MESSAGE_SURFACE
            else -> COLOR_MESSAGE_SURFACE
        }
    }

    private fun fileTypeStroke(label: String): Int {
        return when {
            label.contains("HTML", ignoreCase = true) -> COLOR_HTML_STROKE
            label.contains("Markdown", ignoreCase = true) -> COLOR_MARKDOWN_STROKE
            label.contains("MARKDOWN", ignoreCase = true) -> COLOR_MARKDOWN_STROKE
            label.contains("ZIP", ignoreCase = true) -> COLOR_ZIP_STROKE
            else -> COLOR_DIVIDER
        }
    }

    private fun transparentBackground(): GradientDrawable {
        return rounded(Color.TRANSPARENT, radiusDp = 12)
    }

    private fun centeredPhoneParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun bottomSheetParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = dp(16)
            rightMargin = dp(16)
            bottomMargin = dp(16)
        }
    }

    private fun fullWidthHeightParams(heightDp: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(heightDp)
        )
    }

    private fun fullWidthWrapContentParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun weightedFullWidthParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }

    private fun iconButtonParams(start: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(34), dp(44)).apply {
            leftMargin = dp(start)
        }
    }

    private fun fixedSpacedParams(
        height: Int,
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(height)
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun centeredFixedSpacedParams(
        height: Int,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(height)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(bottom)
        }
    }

    private fun inlineParams(start: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(start)
        }
    }

    private fun spacedParams(
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return fullWidthWrapContentParams().apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun weightedActionParams(
        startMargin: Int = 0,
        endMargin: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            leftMargin = dp(startMargin)
            rightMargin = dp(endMargin)
        }
    }

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

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun dpFloat(value: Float): Float {
        return value * context.resources.displayMetrics.density
    }

    private data class HomeTheme(
        val isDark: Boolean,
        val appBackground: Int,
        val surface: Int,
        val text: Int,
        val muted: Int,
        val divider: Int,
        val topIcon: Int,
        val topIconSurface: Int,
        val messageSurface: Int,
        val searchSurface: Int,
        val searchHighlight: Int,
        val iconTile: Int,
        val privacy: Int,
        val privacySurface: Int,
        val favoriteIcon: Int,
        val primaryActionBackground: Int,
        val primaryActionText: Int,
        val secondaryAction: Int
    ) {
        companion object {
            fun from(isDark: Boolean): HomeTheme {
                return if (isDark) {
                    HomeTheme(
                        isDark = true,
                        appBackground = COLOR_DARK_BACKGROUND,
                        surface = COLOR_DARK_SURFACE,
                        text = COLOR_DARK_TEXT,
                        muted = COLOR_DARK_MUTED,
                        divider = COLOR_DARK_DIVIDER,
                        topIcon = COLOR_DARK_TOP_ICON,
                        topIconSurface = COLOR_DARK_TOP_ICON_SURFACE,
                        messageSurface = COLOR_DARK_MESSAGE_SURFACE,
                        searchSurface = COLOR_DARK_MESSAGE_SURFACE,
                        searchHighlight = COLOR_DARK_SEARCH_HIGHLIGHT,
                        iconTile = COLOR_DARK_ICON_TILE,
                        privacy = COLOR_PRIVACY,
                        privacySurface = COLOR_DARK_PRIVACY_SURFACE,
                        favoriteIcon = COLOR_DARK_FAVORITE_ICON,
                        primaryActionBackground = Color.WHITE,
                        primaryActionText = COLOR_TEXT,
                        secondaryAction = COLOR_DARK_ACTION
                    )
                } else {
                    HomeTheme(
                        isDark = false,
                        appBackground = COLOR_APP_BACKGROUND,
                        surface = COLOR_SURFACE,
                        text = COLOR_TEXT,
                        muted = COLOR_MUTED,
                        divider = COLOR_DIVIDER,
                        topIcon = COLOR_TOP_ICON,
                        topIconSurface = COLOR_TOP_ICON_SURFACE,
                        messageSurface = COLOR_MESSAGE_SURFACE,
                        searchSurface = COLOR_SEARCH_SURFACE,
                        searchHighlight = COLOR_SEARCH_HIGHLIGHT,
                        iconTile = COLOR_ICON_TILE,
                        privacy = COLOR_PRIVACY,
                        privacySurface = COLOR_PRIVACY_SURFACE,
                        favoriteIcon = COLOR_FAVORITE_ICON,
                        primaryActionBackground = COLOR_TEXT,
                        primaryActionText = Color.WHITE,
                        secondaryAction = COLOR_ACTION
                    )
                }
            }
        }
    }

    private companion object {
        val COLOR_APP_BACKGROUND: Int = Color.rgb(250, 250, 250)
        val COLOR_SURFACE: Int = Color.WHITE
        val COLOR_TEXT: Int = Color.rgb(15, 23, 42)
        val COLOR_MUTED: Int = Color.rgb(100, 116, 139)
        val COLOR_CTA_SECONDARY: Int = Color.rgb(203, 213, 225)
        val COLOR_DIVIDER: Int = Color.rgb(226, 232, 240)
        val COLOR_ACTION: Int = Color.rgb(241, 245, 249)
        val COLOR_TOP_ICON: Int = Color.rgb(142, 162, 194)
        val COLOR_TOP_ICON_SURFACE: Int = Color.rgb(248, 251, 255)
        val COLOR_MESSAGE_SURFACE: Int = Color.rgb(248, 250, 252)
        val COLOR_SEARCH_SURFACE: Int = Color.rgb(249, 251, 254)
        val COLOR_SEARCH_HIGHLIGHT: Int = Color.rgb(254, 243, 199)
        val COLOR_ICON_TILE: Int = Color.rgb(248, 250, 252)
        val COLOR_PRIVACY: Int = Color.rgb(5, 150, 105)
        val COLOR_PRIVACY_SURFACE: Int = Color.rgb(236, 253, 245)
        val COLOR_DANGER: Int = Color.rgb(220, 38, 38)
        val COLOR_FAVORITE_ICON: Int = Color.rgb(196, 210, 229)
        val COLOR_DELETE_ICON: Int = Color.rgb(255, 90, 103)
        val COLOR_HTML: Int = Color.rgb(234, 88, 12)
        val COLOR_HTML_SURFACE: Int = Color.rgb(255, 247, 237)
        val COLOR_HTML_STROKE: Int = Color.rgb(254, 215, 170)
        val COLOR_MARKDOWN: Int = Color.rgb(37, 99, 235)
        val COLOR_MARKDOWN_SURFACE: Int = Color.rgb(239, 246, 255)
        val COLOR_MARKDOWN_STROKE: Int = Color.rgb(191, 219, 254)
        val COLOR_ZIP: Int = Color.rgb(217, 119, 6)
        val COLOR_ZIP_SURFACE: Int = Color.rgb(255, 251, 235)
        val COLOR_ZIP_STROKE: Int = Color.rgb(253, 230, 138)
        val COLOR_DARK_BACKGROUND: Int = Color.rgb(12, 12, 14)
        val COLOR_DARK_SURFACE: Int = Color.rgb(28, 28, 29)
        val COLOR_DARK_TEXT: Int = Color.rgb(244, 247, 251)
        val COLOR_DARK_MUTED: Int = Color.rgb(142, 162, 194)
        val COLOR_DARK_DIVIDER: Int = Color.rgb(43, 49, 61)
        val COLOR_DARK_ACTION: Int = Color.rgb(38, 43, 52)
        val COLOR_DARK_TOP_ICON: Int = Color.rgb(156, 174, 204)
        val COLOR_DARK_TOP_ICON_SURFACE: Int = Color.rgb(32, 35, 42)
        val COLOR_DARK_MESSAGE_SURFACE: Int = Color.rgb(31, 35, 43)
        val COLOR_DARK_SEARCH_HIGHLIGHT: Int = Color.rgb(72, 58, 28)
        val COLOR_DARK_ICON_TILE: Int = Color.rgb(30, 36, 47)
        val COLOR_DARK_PRIVACY_SURFACE: Int = Color.rgb(12, 71, 57)
        val COLOR_DARK_FAVORITE_ICON: Int = Color.rgb(219, 231, 246)
        val COLOR_SUN_ICON: Int = Color.rgb(250, 204, 21)
    }
}
