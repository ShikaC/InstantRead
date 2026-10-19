package com.personalai.instantread.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    val readingComfort: ReadingComfort = ReadingComfort.STANDARD
)

class SettingsActions(
    val onClearCache: () -> Unit,
    val onBack: () -> Unit,
    val onReadingComfortChanged: (ReadingComfort) -> Unit = {}
)

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
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_APP_BACKGROUND)
            addView(createAppBar(actions), fullWidthHeightParams(49))
            addView(createBody(state, actions), weightedFullWidthParams())
            addView(createFooter(), fullWidthHeightParams(92))
        }
    }

    private fun createAppBar(actions: SettingsActions): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-app-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(7), 0, dp(12), 0)
            addView(
                Button(context).apply {
                    tag = "settings-back-button"
                    text = "‹"
                    setAllCaps(false)
                    setTextColor(COLOR_TEXT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                    background = transparentBackground()
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, dp(2))
                    setOnClickListener { actions.onBack() }
                },
                ViewGroup.LayoutParams(dp(33), dp(38))
            )
            addView(
                TextView(context).apply {
                    text = "设置与隐私"
                    setTextColor(COLOR_TEXT)
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
        actions: SettingsActions
    ): ScrollView {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (state.message != null) {
                addView(createMessage(state.message), compactSectionParams())
            }
            addJavaScriptSection(this)
            addReadingSection(this, state, actions)
            addCacheSection(this, state, actions)
            addInfoSection(
                root = this,
                title = "外部链接",
                rows = listOf(
                    InfoRow(
                        icon = SettingsInfoIcon.EXTERNAL_LINK,
                        text = "外部链接将交给系统浏览器打开，App 内不会直接跳转到外部网页。"
                    )
                )
            )
            addInfoSection(
                root = this,
                title = "本地隐私",
                rows = listOf(
                    InfoRow(SettingsInfoIcon.SHIELD, "文件仅在本机处理，不上传到云端。"),
                    InfoRow(SettingsInfoIcon.SAVED_LOCAL, "最近记录和收藏保存在本机。")
                )
            )
        }

        return ScrollView(context).apply {
            setFillViewport(true)
            setBackgroundColor(COLOR_APP_BACKGROUND)
            addView(content, fullWidthWrapContentParams())
        }
    }

    private fun addJavaScriptSection(root: LinearLayout) {
        root.addView(sectionTitle("JAVASCRIPT 设置"), sectionHeaderParams(top = 17))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(
                                    LinearLayout(context).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        gravity = Gravity.CENTER_VERTICAL
                                        addView(rowTitle("JavaScript"), wrapContentParams())
                                        addView(statusBadge("按文档授权"), inlineParams(start = 6))
                                    },
                                    fullWidthWrapContentParams()
                                )
                                addView(
                                    bodyText("HTML 与 ZIP 网页包中的脚本默认不执行。\n在预览页点击“JS 已禁用”可为当前文档单独允许脚本，只对这一次导入的副本生效。\nMarkdown 与 TXT 预览不受影响。"),
                                    spacedParams(top = 5)
                                )
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        )
                    },
                    fullWidthWrapContentParams()
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun addCacheSection(
        root: LinearLayout,
        state: SettingsViewState,
        actions: SettingsActions
    ) {
        root.addView(sectionTitle("ZIP 解包缓存"), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13).apply {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(rowTitle("当前缓存"), fullWidthWrapContentParams())
                                addView(
                                    successText(summaryAvailability(state.zipCacheSummary)).apply {
                                        tag = "settings-zip-cache-summary"
                                    },
                                    spacedParams(top = 4)
                                )
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        )
                        addView(
                            Button(context).apply {
                                tag = "settings-clear-cache-button"
                                text = "⌫  清理 ZIP 缓存"
                                setAllCaps(false)
                                minHeight = 0
                                minimumHeight = 0
                                minWidth = 0
                                minimumWidth = 0
                                stateListAnimator = null
                                elevation = 0f
                                translationZ = 0f
                                setTextColor(COLOR_TEXT)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                                background = rounded(COLOR_ACTION, radiusDp = 12, strokeColor = COLOR_ACTION_STROKE)
                                setPadding(dp(12), 0, dp(12), 0)
                                setOnClickListener { actions.onClearCache() }
                            },
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dp(36)
                            )
                        )
                    },
                    fullWidthWrapContentParams()
                )
                addView(
                    TextView(context).apply {
                        tag = "settings-note-card"
                        text = "ZIP 缓存用于保存已解包的网页包资源。清理只删除已解包资源，最近记录、收藏和导入源文件都会保留。下次打开 ZIP 会自动重新解包。卸载应用或清除应用数据会移除全部记录、应用内副本和缓存，但不会删除手机里的原始文件。"
                        setTextColor(COLOR_MUTED)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setLineSpacing(0f, 1.25f)
                        background = rounded(COLOR_NOTE, radiusDp = 8, strokeColor = COLOR_DIVIDER)
                        setPadding(dp(11), dp(9), dp(11), dp(9))
                    },
                    spacedParams(top = 10)
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun addReadingSection(
        root: LinearLayout,
        state: SettingsViewState,
        actions: SettingsActions
    ) {
        root.addView(sectionTitle("阅读设置"), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13).apply {
                addView(rowTitle("正文显示"), fullWidthWrapContentParams())
                addView(
                    bodyText("在 Markdown 或 TXT 文件右上角点击 Aa 调整字号、字距、行距和段距。"),
                    spacedParams(top = 7)
                )
                addView(
                    bodyText("调整会全局保存；HTML 与 ZIP 会保留文件自身样式。"),
                    spacedParams(top = 8)
                )
            },
            fullWidthWrapContentParams()
        )
    }

    private fun addInfoSection(
        root: LinearLayout,
        title: String,
        rows: List<InfoRow>
    ) {
        root.addView(sectionTitle(title), sectionHeaderParams(top = 16))
        root.addView(
            sectionBlock(horizontalPadding = 13, verticalPadding = 13).apply {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        addView(divider(), fullWidthHeightParams(10))
                    }
                    addView(infoRow(row), fullWidthWrapContentParams())
                }
            },
            fullWidthWrapContentParams()
        )
    }

    private fun infoRow(row: InfoRow): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-info-row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                settingsInfoIcon(row.icon),
                ViewGroup.LayoutParams(dp(22), dp(22))
            )
            addView(bodyText(row.text), inlineWeightedParams(start = 12))
        }
    }

    private fun createFooter(): TextView {
        return TextView(context).apply {
            tag = "settings-page-footer"
            text = "版本 ${BuildConfig.VERSION_NAME}"
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setLineSpacing(0f, 1.22f)
            setPadding(dp(8), 0, dp(8), dp(18))
        }
    }

    private fun createMessage(message: String): TextView {
        return TextView(context).apply {
            text = message
            setTextColor(COLOR_SUCCESS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = rounded(COLOR_SUCCESS_SURFACE, radiusDp = 8)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
    }

    private fun sectionTitle(value: String): TextView {
        return TextView(context).apply {
            tag = "settings-section-header"
            text = value
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            letterSpacing = 0.12f
            includeFontPadding = false
        }
    }

    private fun sectionBlock(
        horizontalPadding: Int,
        verticalPadding: Int
    ): LinearLayout {
        return LinearLayout(context).apply {
            tag = "settings-section-block"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_SURFACE)
            setPadding(dp(horizontalPadding), dp(verticalPadding), dp(horizontalPadding), dp(verticalPadding))
        }
    }

    private fun rowTitle(value: String): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            includeFontPadding = false
        }
    }

    private fun bodyText(value: String): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setLineSpacing(0f, 1.18f)
        }
    }

    private fun successText(value: String): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(COLOR_SUCCESS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            includeFontPadding = false
        }
    }

    private fun statusBadge(value: String): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(COLOR_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            includeFontPadding = false
            background = rounded(COLOR_BADGE, radiusDp = 3)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
    }

    private fun divider(): View {
        return View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun settingsInfoIcon(icon: SettingsInfoIcon): View {
        return object : View(context) {
            private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_MUTED
                style = Paint.Style.STROKE
                strokeWidth = dpFloat(1.15f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val iconPath = Path()

            init {
                tag = "settings-info-icon"
                background = rounded(COLOR_ICON_TILE, radiusDp = 8, strokeColor = COLOR_DIVIDER)
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                when (icon) {
                    SettingsInfoIcon.EXTERNAL_LINK -> drawExternalLink(canvas)
                    SettingsInfoIcon.SHIELD -> drawShield(canvas)
                    SettingsInfoIcon.SAVED_LOCAL -> drawSavedLocal(canvas)
                }
            }

            private fun drawExternalLink(canvas: Canvas) {
                val centerX = width / 2f
                val centerY = height / 2f
                val iconBox = minOf(width, height) * 0.58f
                val iconLeft = centerX - iconBox / 2f
                val iconTop = centerY - iconBox / 2f
                val left = iconLeft + iconBox * 0.10f
                val top = iconTop + iconBox * 0.28f
                val right = iconLeft + iconBox * 0.74f
                val bottom = iconTop + iconBox * 0.92f
                canvas.drawLine(left, top, left, bottom, iconStroke)
                canvas.drawLine(left, bottom, right, bottom, iconStroke)
                canvas.drawLine(right, bottom, right, iconTop + iconBox * 0.58f, iconStroke)
                canvas.drawLine(left, top, iconLeft + iconBox * 0.42f, top, iconStroke)
                canvas.drawLine(
                    iconLeft + iconBox * 0.48f,
                    iconTop + iconBox * 0.52f,
                    iconLeft + iconBox * 0.84f,
                    iconTop + iconBox * 0.16f,
                    iconStroke
                )
                canvas.drawLine(
                    iconLeft + iconBox * 0.62f,
                    iconTop + iconBox * 0.16f,
                    iconLeft + iconBox * 0.84f,
                    iconTop + iconBox * 0.16f,
                    iconStroke
                )
                canvas.drawLine(
                    iconLeft + iconBox * 0.84f,
                    iconTop + iconBox * 0.16f,
                    iconLeft + iconBox * 0.84f,
                    iconTop + iconBox * 0.40f,
                    iconStroke
                )
            }

            private fun drawShield(canvas: Canvas) {
                val centerX = width / 2f
                val centerY = height / 2f
                val shieldBox = minOf(width, height) * 0.60f
                val iconLeft = centerX - shieldBox / 2f
                val iconTop = centerY - shieldBox / 2f
                iconPath.reset()
                iconPath.moveTo(centerX, iconTop + shieldBox * 0.06f)
                iconPath.lineTo(iconLeft + shieldBox * 0.82f, iconTop + shieldBox * 0.24f)
                iconPath.lineTo(iconLeft + shieldBox * 0.76f, iconTop + shieldBox * 0.60f)
                iconPath.quadTo(iconLeft + shieldBox * 0.70f, iconTop + shieldBox * 0.80f, centerX, iconTop + shieldBox * 0.88f)
                iconPath.quadTo(iconLeft + shieldBox * 0.30f, iconTop + shieldBox * 0.80f, iconLeft + shieldBox * 0.24f, iconTop + shieldBox * 0.60f)
                iconPath.lineTo(iconLeft + shieldBox * 0.18f, iconTop + shieldBox * 0.24f)
                iconPath.close()
                canvas.drawPath(iconPath, iconStroke)
            }

            private fun drawSavedLocal(canvas: Canvas) {
                val centerX = width / 2f
                val centerY = height / 2f
                val shieldBox = minOf(width, height) * 0.60f
                val iconLeft = centerX - shieldBox / 2f
                val iconTop = centerY - shieldBox / 2f
                iconPath.reset()
                iconPath.moveTo(centerX, iconTop + shieldBox * 0.06f)
                iconPath.lineTo(iconLeft + shieldBox * 0.82f, iconTop + shieldBox * 0.24f)
                iconPath.lineTo(iconLeft + shieldBox * 0.76f, iconTop + shieldBox * 0.60f)
                iconPath.quadTo(iconLeft + shieldBox * 0.70f, iconTop + shieldBox * 0.80f, centerX, iconTop + shieldBox * 0.88f)
                iconPath.quadTo(iconLeft + shieldBox * 0.30f, iconTop + shieldBox * 0.80f, iconLeft + shieldBox * 0.24f, iconTop + shieldBox * 0.60f)
                iconPath.lineTo(iconLeft + shieldBox * 0.18f, iconTop + shieldBox * 0.24f)
                iconPath.close()
                canvas.drawPath(iconPath, iconStroke)
                canvas.drawLine(
                    iconLeft + shieldBox * 0.32f,
                    iconTop + shieldBox * 0.52f,
                    iconLeft + shieldBox * 0.45f,
                    iconTop + shieldBox * 0.65f,
                    iconStroke
                )
                canvas.drawLine(
                    iconLeft + shieldBox * 0.45f,
                    iconTop + shieldBox * 0.65f,
                    iconLeft + shieldBox * 0.70f,
                    iconTop + shieldBox * 0.38f,
                    iconStroke
                )
            }
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

    private fun spacedParams(
        top: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        return fullWidthWrapContentParams().apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun inlineParams(start: Int = 0): LinearLayout.LayoutParams {
        return wrapContentParams().apply {
            leftMargin = dp(start)
        }
    }

    private fun inlineWeightedParams(start: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(start)
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

    private enum class SettingsInfoIcon {
        EXTERNAL_LINK,
        SHIELD,
        SAVED_LOCAL
    }

    private data class InfoRow(
        val icon: SettingsInfoIcon,
        val text: String
    )

    private companion object {
        val COLOR_APP_BACKGROUND: Int = Color.rgb(248, 250, 252)
        val COLOR_SURFACE: Int = Color.WHITE
        val COLOR_TEXT: Int = Color.rgb(15, 23, 42)
        val COLOR_MUTED: Int = Color.rgb(114, 132, 164)
        val COLOR_DIVIDER: Int = Color.rgb(229, 234, 243)
        val COLOR_ACTION: Int = Color.rgb(249, 251, 254)
        val COLOR_ACTION_STROKE: Int = Color.rgb(215, 225, 240)
        val COLOR_NOTE: Int = Color.rgb(246, 249, 253)
        val COLOR_ICON_TILE: Int = Color.rgb(248, 251, 254)
        val COLOR_BADGE: Int = Color.rgb(230, 236, 246)
        val COLOR_SUCCESS: Int = Color.rgb(0, 153, 102)
        val COLOR_SUCCESS_SURFACE: Int = Color.rgb(236, 253, 245)
    }
}
