package com.personalai.instantread.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.personalai.instantread.BuildConfig
import com.personalai.instantread.preview.ReadingComfort

data class SettingsViewState(
    val message: String?,
    val zipCacheSummary: String,
    val readingComfort: ReadingComfort = ReadingComfort.STANDARD,
    val isDarkTheme: Boolean = false
)

class SettingsActions(
    val onClearCache: () -> Unit,
    val onBack: () -> Unit,
    val onReadingComfortChanged: (ReadingComfort) -> Unit = {}
)

internal object SettingsHelpCopy {
    const val ACKNOWLEDGE = "知道了"
    const val JAVASCRIPT_TITLE = "JavaScript 说明"
    const val JAVASCRIPT_MESSAGE =
        "HTML 与 ZIP 网页包中的脚本默认不执行。\n" +
            "在预览页点击“JS 已禁用”可为当前文档单独允许脚本，只对这一次导入的副本生效。\n" +
            "Markdown 与 TXT 预览不受影响。"
    const val CACHE_TITLE = "ZIP 解包缓存说明"
    const val CACHE_MESSAGE =
        "ZIP 缓存用于保存已解包的网页包资源。\n" +
            "清理只删除已解包资源，最近记录、收藏和导入源文件都会保留；下次打开 ZIP 会自动重新解包。\n" +
            "卸载应用或清除应用数据会移除全部记录、应用内副本和缓存，但不会删除手机里的原始文件。"
}

class SettingsViewFactory(
    private val context: Context
) {
    fun updateZipCacheSummary(root: View, summary: String): Boolean {
        val summaryView = root.findViewWithTag<TextView>("settings-zip-cache-summary") ?: return false
        summaryView.text = summaryAvailability(summary)
        return true
    }

    fun create(
        state: SettingsViewState,
        actions: SettingsActions
    ): LinearLayout {
        val theme = SettingsTheme.from(state.isDarkTheme)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.appBackground)
            addView(createAppBar(actions, theme), fullWidthHeightParams(49))
            addView(createBody(state, actions, theme), weightedFullWidthParams())
            addView(createFooter(theme), fullWidthHeightParams(92))
        }
    }

    private fun createAppBar(actions: SettingsActions, theme: SettingsTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-app-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.surface)
            setPadding(dp(7), 0, dp(12), 0)
            addView(
                Button(context).apply {
                    tag = "settings-back-button"
                    text = ""
                    setAllCaps(false)
                    background = transparentBackground()
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                    setCompoundDrawablesWithIntrinsicBounds(
                        instantIconDrawable(context, InstantIcons.Back, theme.topIcon, 18f),
                        null,
                        null,
                        null
                    )
                    contentDescription = "返回"
                    setOnClickListener { actions.onBack() }
                },
                ViewGroup.LayoutParams(dp(33), dp(38))
            )
            addView(
                TextView(context).apply {
                    text = "设置与隐私"
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun createBody(
        state: SettingsViewState,
        actions: SettingsActions,
        theme: SettingsTheme
    ): ScrollView {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (state.message != null) {
                addView(createMessage(state.message, theme), compactSectionParams())
            }
            addJavaScriptSection(this, theme)
            addReadingSection(this, theme)
            addCacheSection(this, state, actions, theme)
            addPrivacySection(this, theme)
        }

        return ScrollView(context).apply {
            setFillViewport(true)
            setBackgroundColor(theme.appBackground)
            addView(content, fullWidthWrapContentParams())
        }
    }

    private fun addJavaScriptSection(root: LinearLayout, theme: SettingsTheme) {
        root.addView(sectionTitle("JAVASCRIPT 设置", theme), sectionHeaderParams(top = 17))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13, theme = theme).apply {
                addView(
                    settingHeading(
                        title = "JavaScript",
                        badge = statusBadge("按文档授权", theme),
                        helpTitle = SettingsHelpCopy.JAVASCRIPT_TITLE,
                        helpMessage = SettingsHelpCopy.JAVASCRIPT_MESSAGE,
                        helpTag = "settings-help-javascript",
                        theme = theme
                    ),
                    fullWidthWrapContentParams()
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun addCacheSection(
        root: LinearLayout,
        state: SettingsViewState,
        actions: SettingsActions,
        theme: SettingsTheme
    ) {
        root.addView(sectionTitle("ZIP 解包缓存", theme), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13, theme = theme).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            settingHeading(
                                title = "当前缓存",
                                badge = statusBadge(summaryAvailability(state.zipCacheSummary), theme).apply {
                                    tag = "settings-zip-cache-summary"
                                },
                                helpTitle = SettingsHelpCopy.CACHE_TITLE,
                                helpMessage = SettingsHelpCopy.CACHE_MESSAGE,
                                helpTag = "settings-help-cache",
                                theme = theme
                            ),
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        )
                        addView(createClearCacheButton(actions, theme), clearCacheButtonParams())
                    },
                    fullWidthWrapContentParams()
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun createClearCacheButton(actions: SettingsActions, theme: SettingsTheme): Button {
        return Button(context).apply {
            tag = "settings-clear-cache-button"
            text = "清理 ZIP 缓存"
            setAllCaps(false)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            stateListAnimator = null
            elevation = 0f
            translationZ = 0f
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = rounded(theme.action, radiusDp = 12, strokeColor = theme.actionStroke)
            setPadding(dp(10), 0, dp(12), 0)
            setCompoundDrawablesWithIntrinsicBounds(
                instantIconDrawable(
                    context,
                    InstantIcons.ClearCache,
                    theme.text,
                    13f
                ),
                null,
                null,
                null
            )
            compoundDrawablePadding = dp(6)
            setOnClickListener { actions.onClearCache() }
        }
    }

    private fun addReadingSection(root: LinearLayout, theme: SettingsTheme) {
        root.addView(sectionTitle("阅读显示", theme), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13, theme = theme).apply {
                addView(rowTitle("正文显示", theme), fullWidthWrapContentParams())
                addView(
                    bodyText(
                        "在 Markdown 或 TXT 文件右上角点击 Aa，调整字号、字距、行距和段距。\n" +
                            "调整会全局保存；HTML 与 ZIP 会保留文件自身样式。",
                        theme
                    ),
                    fullWidthWrapContentParams().apply { topMargin = dp(7) }
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun addPrivacySection(root: LinearLayout, theme: SettingsTheme) {
        root.addView(sectionTitle("隐私与安全", theme), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13, theme = theme).apply {
                addView(
                    infoRow(InstantIcons.Shield, "文件仅在本机处理，不上传到云端。", theme),
                    fullWidthWrapContentParams()
                )
                addView(View(context), fullWidthHeightParams(10))
                addView(
                    infoRow(InstantIcons.Archive, "最近记录和收藏保存在本机。", theme),
                    fullWidthWrapContentParams()
                )
                addView(View(context), fullWidthHeightParams(10))
                addView(
                    infoRow(
                        InstantIcons.ExternalLink,
                        "外部链接将交给系统浏览器打开，App 内不会直接跳转到外部网页。",
                        theme
                    ),
                    fullWidthWrapContentParams()
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun infoRow(icon: InstantIcon, text: String, theme: SettingsTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-info-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(
                InstantIconView(context, icon, theme.topIcon, 16f).apply {
                    tag = "settings-info-icon"
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    topMargin = dp(2)
                }
            )
            addView(
                bodyText(text, theme),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(12)
                }
            )
        }
    }

    private fun settingHeading(
        title: String,
        badge: TextView,
        helpTitle: String,
        helpMessage: String,
        helpTag: String,
        theme: SettingsTheme
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-primary-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rowTitle(title, theme), wrapContentParams())
            addView(badge, inlineParams(start = 7))
            addView(View(context), LinearLayout.LayoutParams(0, dp(1), 1f))
            addView(
                helpButton(helpTitle, helpMessage, helpTag, theme),
                LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    leftMargin = dp(4)
                }
            )
        }
    }

    private fun helpButton(
        helpTitle: String,
        helpMessage: String,
        helpTag: String,
        theme: SettingsTheme
    ): View {
        return InstantIconView(context, InstantIcons.Help, theme.topIcon, 17f).apply {
            tag = helpTag
            contentDescription = "$helpTitle"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(helpTitle)
                    .setMessage(helpMessage)
                    .setPositiveButton(SettingsHelpCopy.ACKNOWLEDGE, null)
                    .show()
            }
        }
    }

    private fun createFooter(theme: SettingsTheme): TextView {
        return TextView(context).apply {
            tag = "settings-page-footer"
            text = "版本 ${BuildConfig.VERSION_NAME}"
            gravity = Gravity.CENTER
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setLineSpacing(0f, 1.22f)
            setPadding(dp(8), 0, dp(8), dp(18))
        }
    }

    private fun createMessage(message: String, theme: SettingsTheme): TextView {
        return TextView(context).apply {
            text = message
            setTextColor(theme.success)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            background = rounded(theme.successSurface, radiusDp = 8)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
    }

    private fun sectionTitle(value: String, theme: SettingsTheme): TextView {
        return TextView(context).apply {
            tag = "settings-section-header"
            text = value
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.12f
            includeFontPadding = false
        }
    }

    private fun sectionBlock(
        horizontalPadding: Int,
        verticalPadding: Int,
        theme: SettingsTheme
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-section-block"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.surface)
            setPadding(dp(horizontalPadding), dp(verticalPadding), dp(horizontalPadding), dp(verticalPadding))
        }
    }

    private fun rowTitle(value: String, theme: SettingsTheme): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            includeFontPadding = false
        }
    }

    private fun bodyText(value: String, theme: SettingsTheme): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(0f, 1.18f)
        }
    }

    private fun statusBadge(value: String, theme: SettingsTheme): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            includeFontPadding = false
            background = rounded(theme.badge, radiusDp = 3)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
    }

    private fun transparentBackground(): GradientDrawable {
        return rounded(Color.TRANSPARENT, radiusDp = 10)
    }

    private fun summaryAvailability(summary: String): String {
        return when {
            summary.contains("统计") -> "正在统计"
            summary.contains("暂无") || summary.contains("0 个") -> "暂无"
            else -> "可用"
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

    private fun wrapContentParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
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

    private fun compactSectionParams(): LinearLayout.LayoutParams {
        return fullWidthWrapContentParams().apply {
            topMargin = dp(10)
            leftMargin = dp(13)
            rightMargin = dp(13)
            bottomMargin = dp(2)
        }
    }

    private fun sectionHeaderParams(top: Int): LinearLayout.LayoutParams {
        return fullWidthWrapContentParams().apply {
            topMargin = dp(top)
            bottomMargin = dp(9)
            leftMargin = dp(13)
            rightMargin = dp(13)
        }
    }

    private fun inlineParams(start: Int = 0): LinearLayout.LayoutParams {
        return wrapContentParams().apply {
            leftMargin = dp(start)
        }
    }

    private fun clearCacheButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(36)
        ).apply {
            leftMargin = dp(8)
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

    private companion object {
        val COLOR_APP_BACKGROUND: Int = Color.rgb(248, 250, 252)
        val COLOR_SURFACE: Int = Color.WHITE
        val COLOR_TEXT: Int = Color.rgb(15, 23, 42)
        val COLOR_MUTED: Int = Color.rgb(114, 132, 164)
        val COLOR_TOP_ICON: Int = Color.rgb(142, 162, 194)
        val COLOR_ACTION: Int = Color.rgb(249, 251, 254)
        val COLOR_ACTION_STROKE: Int = Color.rgb(215, 225, 240)
        val COLOR_BADGE: Int = Color.rgb(230, 236, 246)
        val COLOR_SUCCESS: Int = Color.rgb(0, 153, 102)
        val COLOR_SUCCESS_SURFACE: Int = Color.rgb(236, 253, 245)

        val COLOR_DARK_APP_BACKGROUND: Int = Color.rgb(12, 12, 14)
        val COLOR_DARK_SURFACE: Int = Color.rgb(28, 28, 29)
        val COLOR_DARK_TEXT: Int = Color.rgb(244, 247, 251)
        val COLOR_DARK_MUTED: Int = Color.rgb(142, 162, 194)
        val COLOR_DARK_TOP_ICON: Int = Color.rgb(156, 174, 204)
        val COLOR_DARK_ACTION: Int = Color.rgb(38, 43, 52)
        val COLOR_DARK_ACTION_STROKE: Int = Color.rgb(43, 49, 61)
        val COLOR_DARK_BADGE: Int = Color.rgb(38, 43, 52)
        val COLOR_DARK_SUCCESS: Int = Color.rgb(52, 211, 153)
        val COLOR_DARK_SUCCESS_SURFACE: Int = Color.rgb(12, 71, 57)
    }

    private data class SettingsTheme(
        val appBackground: Int,
        val surface: Int,
        val text: Int,
        val muted: Int,
        val topIcon: Int,
        val action: Int,
        val actionStroke: Int,
        val badge: Int,
        val success: Int,
        val successSurface: Int
    ) {
        companion object {
            fun from(isDark: Boolean): SettingsTheme {
                return if (isDark) {
                    SettingsTheme(
                        appBackground = COLOR_DARK_APP_BACKGROUND,
                        surface = COLOR_DARK_SURFACE,
                        text = COLOR_DARK_TEXT,
                        muted = COLOR_DARK_MUTED,
                        topIcon = COLOR_DARK_TOP_ICON,
                        action = COLOR_DARK_ACTION,
                        actionStroke = COLOR_DARK_ACTION_STROKE,
                        badge = COLOR_DARK_BADGE,
                        success = COLOR_DARK_SUCCESS,
                        successSurface = COLOR_DARK_SUCCESS_SURFACE
                    )
                } else {
                    SettingsTheme(
                        appBackground = COLOR_APP_BACKGROUND,
                        surface = COLOR_SURFACE,
                        text = COLOR_TEXT,
                        muted = COLOR_MUTED,
                        topIcon = COLOR_TOP_ICON,
                        action = COLOR_ACTION,
                        actionStroke = COLOR_ACTION_STROKE,
                        badge = COLOR_BADGE,
                        success = COLOR_SUCCESS,
                        successSurface = COLOR_SUCCESS_SURFACE
                    )
                }
            }
        }
    }
}
