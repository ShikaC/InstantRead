package com.personalai.instantread.ui

import android.content.Context
import android.graphics.Color
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
    val warningLabel: String? = null,
    val lastOpenedAt: Long? = null
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
    val onClearHistory: () -> Unit = {},
    val onCancelClearHistory: () -> Unit = {},
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
        clearableRecentCount: Int = 0,
        actions: HomeActions,
        pendingDeleteFile: RecentFileItem? = null,
        pendingClearHistory: Boolean = false
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
                    clearableRecentCount,
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
        if (pendingClearHistory) {
            page.addView(createClearSheet(clearableRecentCount, actions, theme), bottomSheetParams())
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
                            text = "本地预览"
                            setTextColor(theme.muted)
                            setTypeface(typeface, Typeface.BOLD)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                            includeFontPadding = false
                        },
                        inlineParams(start = 6)
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(createThemeButton(actions, theme), appBarIconButtonParams())
            addView(
                createSettingsButton(actions, theme),
                appBarIconButtonParams(start = 8)
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
        clearableRecentCount: Int,
        actions: HomeActions,
        theme: HomeTheme
    ): ScrollView {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(23), dp(13), dp(24))
            addView(createPrimaryAction(actions, theme), fixedSpacedParams(height = 52, bottom = 15))
            addView(createPrivacyRow(theme), centeredFixedSpacedParams(height = 26, bottom = 14))
            addView(createSearchRow(searchQuery, actions, theme), fixedSpacedParams(height = 42, bottom = 18))
            if (message != null) {
                addView(createMessage(message, theme), spacedParams(bottom = 20))
            }
            addRecentList(this, recentFiles, selectedFilter, searchQuery, totalRecentCount, favoriteRecentCount, clearableRecentCount, actions, theme)
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
                    text = "仅在本机解析"
                    setTextColor(theme.privacy)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    includeFontPadding = false
                    background = rounded(theme.privacySurface, radiusDp = 13)
                    setPadding(dp(10), 0, dp(11), 0)
                    gravity = Gravity.CENTER
                    setCompoundDrawablesWithIntrinsicBounds(
                        instantIconDrawable(context, InstantIcons.Shield, theme.privacy, 12f),
                        null,
                        null,
                        null
                    )
                    compoundDrawablePadding = dp(6)
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                TextView(context).apply {
                    text = "不上传 · 无网络权限"
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                },
                inlineParams(start = 10)
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
            background = rounded(theme.searchSurface, radiusDp = 13, strokeColor = theme.divider)
            setPadding(dp(12), 0, dp(6), 0)
            addView(
                InstantIconView(context, InstantIcons.Search, theme.muted, 15f),
                LinearLayout.LayoutParams(dp(15), dp(15)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    rightMargin = dp(9)
                }
            )
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
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
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
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
        clearableRecentCount: Int,
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
                clearableRecentCount,
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
                createEmptyState(selectedFilter, searchQuery, theme),
                fullWidthWrapContentParams()
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

    private fun createEmptyState(
        selectedFilter: HomeListFilter,
        searchQuery: String,
        theme: HomeTheme
    ): LinearLayout {
        val searching = searchQuery.isNotBlank()
        val favorites = selectedFilter == HomeListFilter.FAVORITES
        val title = when {
            searching && favorites -> "收藏中没有匹配记录"
            searching -> "没有匹配的记录"
            favorites -> "暂无收藏文件"
            else -> "暂无最近记录"
        }
        val hint = when {
            searching && favorites -> "换个关键词试试，或者清空搜索查看全部收藏。"
            searching -> "换个关键词试试，或者清空搜索查看全部记录。"
            favorites -> "在最近记录里点击星标即可收藏文件。"
            else -> "选择本地文件后，这里会保留打开过的 HTML、Markdown、TXT 与 ZIP 网页包。"
        }
        return LinearLayout(context).apply {
            tag = "home-empty-state"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(40), dp(26), dp(40))
            addView(
                createEmptyBadge(searching, theme),
                ViewGroup.LayoutParams(dp(56), dp(56))
            )
            addView(
                TextView(context).apply {
                    text = title
                    gravity = Gravity.CENTER
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    includeFontPadding = false
                },
                spacedParams(top = 14)
            )
            addView(
                TextView(context).apply {
                    text = hint
                    gravity = Gravity.CENTER
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setLineSpacing(0f, 1.3f)
                },
                spacedParams(top = 7)
            )
        }
    }

    private fun createEmptyBadge(searching: Boolean, theme: HomeTheme): FrameLayout {
        return FrameLayout(context).apply {
            background = rounded(theme.iconTile, radiusDp = 18, strokeColor = theme.divider)
            addView(
                InstantIconView(
                    context,
                    if (searching) InstantIcons.FileText else InstantIcons.FileMarkdown,
                    theme.topIcon,
                    19f
                ),
                FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
            )
        }
    }

    private fun createRecentHeader(
        selectedFilter: HomeListFilter,
        resultCount: Int,
        searchQuery: String,
        totalRecentCount: Int,
        favoriteRecentCount: Int,
        clearableRecentCount: Int,
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
            addView(
                createFilterGroup(
                    totalRecentCount = totalRecentCount,
                    favoriteRecentCount = favoriteRecentCount,
                    selectedFilter = selectedFilter,
                    actions = actions,
                    theme = theme
                ),
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            if (clearableRecentCount > 0) {
                addView(
                    TextView(context).apply {
                        tag = "home-clear-history-button"
                        text = "清空"
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(theme.muted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        minWidth = dp(40)
                        minimumWidth = dp(40)
                        minHeight = dp(24)
                        minimumHeight = dp(24)
                        background = rounded(theme.searchSurface, radiusDp = 8, strokeColor = theme.divider)
                        isClickable = true
                        isFocusable = true
                        contentDescription = "清空 $clearableRecentCount 条未收藏记录，收藏会保留"
                        setOnClickListener { actions.onClearHistory() }
                    },
                    inlineParams(start = 8)
                )
            }
        }
    }

    private fun createFilterGroup(
        totalRecentCount: Int,
        favoriteRecentCount: Int,
        selectedFilter: HomeListFilter,
        actions: HomeActions,
        theme: HomeTheme
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-filter-group"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(theme.searchSurface, radiusDp = 11, strokeColor = theme.divider)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            addView(
                createFilterButton(
                    "全部 $totalRecentCount",
                    "home-filter-all",
                    HomeListFilter.ALL,
                    totalRecentCount,
                    selectedFilter,
                    actions,
                    theme
                )
            )
            addView(
                createFilterButton(
                    "收藏 $favoriteRecentCount",
                    "home-filter-favorites",
                    HomeListFilter.FAVORITES,
                    favoriteRecentCount,
                    selectedFilter,
                    actions,
                    theme
                ),
                inlineParams(start = 3)
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
                radiusDp = 8,
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
                ViewGroup.LayoutParams(dp(42), dp(42))
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
                deleteIconButton(item, actions, theme),
                iconButtonParams()
            )
        }
    }

    private fun fileIconView(item: RecentFileItem, theme: HomeTheme): FrameLayout {
        return FrameLayout(context).apply {
            tag = "recent-file-icon"
            background = rounded(
                fileTypeSurface(item.fileTypeLabel, theme),
                radiusDp = 13,
                strokeColor = fileTypeStroke(item.fileTypeLabel, theme)
            )
            addView(
                InstantIconView(
                    context,
                    fileTypeIcon(item.fileTypeLabel),
                    fileTypeColor(item.fileTypeLabel),
                    32f
                ),
                FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
            )
        }
    }

    private fun favoriteIconButton(item: RecentFileItem, actions: HomeActions, theme: HomeTheme): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.Star,
            tint = theme.topIcon,
            iconWidthDp = 17f,
            active = item.isFavorite,
            activeTint = theme.favoriteOn
        ).apply {
            tag = "recent-favorite-button"
            contentDescription = if (item.isFavorite) "取消收藏" else "收藏"
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onToggleFavorite(item.id) }
        }
    }

    private fun deleteIconButton(item: RecentFileItem, actions: HomeActions, theme: HomeTheme): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.Trash,
            tint = theme.topIcon,
            iconWidthDp = 16f,
            pressedTint = theme.deleteIcon
        ).apply {
            tag = "recent-delete-button"
            contentDescription = "删除"
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onDeleteHistory(item.id) }
        }
    }

    private fun createRecentMetaRow(item: RecentFileItem, searchQuery: String, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = highlightedSearchText(recentMetaLabel(item), searchQuery, theme.searchHighlight)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(theme.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
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
            setPadding(dp(20), dp(12), dp(20), dp(20))
            addView(
                View(context).apply {
                    tag = "home-delete-handle"
                    background = rounded(theme.divider, radiusDp = 2)
                },
                LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(14)
                }
            )
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(dp(8), 0, dp(8), dp(8))
        }
    }

    private fun createClearSheet(clearableRecentCount: Int, actions: HomeActions, theme: HomeTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "home-clear-sheet"
            orientation = LinearLayout.VERTICAL
            background = rounded(theme.surface, radiusDp = 24, strokeColor = theme.divider)
            setPadding(dp(20), dp(12), dp(20), dp(20))
            addView(
                View(context).apply {
                    tag = "home-clear-handle"
                    background = rounded(theme.divider, radiusDp = 2)
                },
                LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(14)
                }
            )
            addView(
                TextView(context).apply {
                    text = "清空未收藏的记录？"
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                },
                fullWidthWrapContentParams()
            )
            addView(
                TextView(context).apply {
                    text = "会移除 $clearableRecentCount 条未收藏记录，以及即阅保存的应用内副本和对应缓存；" +
                        "已收藏的文件和手机里的原始文件不会被删除。"
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
                            tag = "home-clear-cancel"
                            setOnClickListener { actions.onCancelClearHistory() }
                        },
                        weightedActionParams(endMargin = 6)
                    )
                    addView(
                        sheetButton("清空", COLOR_DANGER, Color.WHITE).apply {
                            tag = "home-clear-confirm"
                            setOnClickListener { actions.onClearHistory() }
                        },
                        weightedActionParams(startMargin = 6)
                    )
                },
                fullWidthWrapContentParams()
            )
        }
    }

    private fun createThemeButton(actions: HomeActions, theme: HomeTheme): View {
        return InstantIconView(
            context = context,
            icon = if (theme.isDark) InstantIcons.Sun else InstantIcons.Moon,
            tint = theme.topIcon,
            iconWidthDp = 18f
        ).apply {
            tag = "home-theme-button"
            contentDescription = if (theme.isDark) "日间模式" else "夜间模式"
            background = rounded(theme.topIconSurface, radiusDp = 19)
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onToggleTheme() }
        }
    }

    private fun createSettingsButton(actions: HomeActions, theme: HomeTheme): View {
        return InstantIconView(
            context = context,
            icon = InstantIcons.Gear,
            tint = theme.topIcon,
            iconWidthDp = 19f
        ).apply {
            tag = "home-settings-button"
            contentDescription = "设置"
            background = rounded(theme.topIconSurface, radiusDp = 19)
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onOpenSettings() }
        }
    }

    private fun sheetButton(label: String, backgroundColor: Int, textColor: Int): Button {
        return Button(context).apply {
            text = label
            setAllCaps(false)
            minHeight = dp(48)
            // 自定义圆角背景已经表达层次，平台默认的按下抬升动画会在抽屉边缘留下多余阴影。
            stateListAnimator = null
            elevation = 0f
            translationZ = 0f
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

    private fun recentMetaLabel(item: RecentFileItem): String {
        val openedAt = item.lastOpenedAt ?: return item.fileTypeLabel
        return "${item.fileTypeLabel} · ${recentTimeLabel(openedAt)}"
    }

    private fun fileTypeIcon(label: String): InstantIcon {
        return when {
            label.contains("HTML", ignoreCase = true) -> InstantIcons.FileHtml
            label.contains("Markdown", ignoreCase = true) -> InstantIcons.FileMarkdown
            label.contains("MARKDOWN", ignoreCase = true) -> InstantIcons.FileMarkdown
            label.contains("ZIP", ignoreCase = true) -> InstantIcons.FileZip
            else -> InstantIcons.FileText
        }
    }

    private fun fileTypeColor(label: String): Int {
        return when {
            label.contains("HTML", ignoreCase = true) -> COLOR_HTML
            label.contains("Markdown", ignoreCase = true) -> COLOR_MARKDOWN
            label.contains("MARKDOWN", ignoreCase = true) -> COLOR_MARKDOWN
            label.contains("ZIP", ignoreCase = true) -> COLOR_ZIP
            else -> COLOR_TXT
        }
    }

    private fun fileTypeSurface(label: String, theme: HomeTheme): Int {
        return when {
            label.contains("HTML", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_HTML_SURFACE else COLOR_HTML_SURFACE
            label.contains("Markdown", ignoreCase = true) ||
                label.contains("MARKDOWN", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_MARKDOWN_SURFACE else COLOR_MARKDOWN_SURFACE
            label.contains("ZIP", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_ZIP_SURFACE else COLOR_ZIP_SURFACE
            else ->
                if (theme.isDark) COLOR_DARK_TXT_SURFACE else COLOR_TXT_SURFACE
        }
    }

    private fun fileTypeStroke(label: String, theme: HomeTheme): Int {
        return when {
            label.contains("HTML", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_HTML_STROKE else COLOR_HTML_STROKE
            label.contains("Markdown", ignoreCase = true) ||
                label.contains("MARKDOWN", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_MARKDOWN_STROKE else COLOR_MARKDOWN_STROKE
            label.contains("ZIP", ignoreCase = true) ->
                if (theme.isDark) COLOR_DARK_ZIP_STROKE else COLOR_ZIP_STROKE
            else ->
                if (theme.isDark) COLOR_DARK_TXT_STROKE else COLOR_TXT_STROKE
        }
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

    // 顶部栏按钮按原型做 38dp 圆形，列表行按钮仍用 34x44 的触控区域。
    private fun appBarIconButtonParams(start: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(38), dp(38)).apply {
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
        val favoriteOn: Int,
        val deleteIcon: Int,
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
                        favoriteOn = COLOR_FAVORITE_ON,
                        deleteIcon = COLOR_DELETE_ICON,
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
                        favoriteOn = COLOR_FAVORITE_ON,
                        deleteIcon = COLOR_DELETE_ICON,
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
        val COLOR_FAVORITE_ON: Int = Color.rgb(37, 99, 235)
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
        val COLOR_TXT: Int = Color.rgb(100, 116, 139)
        val COLOR_TXT_SURFACE: Int = Color.rgb(248, 250, 252)
        val COLOR_TXT_STROKE: Int = Color.rgb(226, 232, 240)
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
        val COLOR_DARK_HTML_SURFACE: Int = Color.rgb(44, 30, 20)
        val COLOR_DARK_HTML_STROKE: Int = Color.rgb(122, 67, 24)
        val COLOR_DARK_MARKDOWN_SURFACE: Int = Color.rgb(22, 35, 60)
        val COLOR_DARK_MARKDOWN_STROKE: Int = Color.rgb(47, 76, 126)
        val COLOR_DARK_ZIP_SURFACE: Int = Color.rgb(44, 36, 19)
        val COLOR_DARK_ZIP_STROKE: Int = Color.rgb(107, 84, 23)
        val COLOR_DARK_TXT_SURFACE: Int = Color.rgb(30, 36, 47)
        val COLOR_DARK_TXT_STROKE: Int = Color.rgb(43, 49, 61)
    }
}
