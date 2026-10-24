package com.personalai.instantread.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.personalai.instantread.tasks.ForegroundTaskState

class ForegroundTaskViewFactory(
    private val context: Context
) {
    fun create(
        state: ForegroundTaskState,
        isDarkTheme: Boolean,
        onCancel: () -> Unit
    ): FrameLayout {
        val theme = ForegroundTaskTheme.from(isDarkTheme)
        val root = LinearLayout(context).apply {
            tag = "foreground-task-root"
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            addView(createAppBar(theme), fullWidthHeightParams(APP_BAR_HEIGHT_DP))
            addView(
                createScroll(state, onCancel, theme),
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            )
        }

        return FrameLayout(context).apply {
            setBackgroundColor(theme.background)
            addView(root, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    private fun createScroll(
        state: ForegroundTaskState,
        onCancel: () -> Unit,
        theme: ForegroundTaskTheme
    ): ScrollView {
        return ScrollView(context).apply {
            tag = "foreground-task-scroll"
            isFillViewport = true
            setBackgroundColor(theme.background)
            addView(
                createContent(state, onCancel, theme),
                FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            )
        }
    }

    private fun createAppBar(theme: ForegroundTaskTheme): LinearLayout {
        return LinearLayout(context).apply {
            tag = "foreground-task-app-bar"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.background)
            setPadding(dp(13), 0, dp(13), 0)
            addView(
                TextView(context).apply {
                    text = "即阅"
                    setTextColor(theme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    includeFontPadding = false
                },
                wrapContentParams()
            )
            addView(
                TextView(context).apply {
                    text = "本地预览"
                    setTextColor(theme.muted)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    leftMargin = dp(6)
                }
            )
        }
    }

    private fun createContent(
        state: ForegroundTaskState,
        onCancel: () -> Unit,
        theme: ForegroundTaskTheme
    ): LinearLayout {
        val percent = state.percent
        return LinearLayout(context).apply {
            tag = "foreground-task-content"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(createFileName(state, theme), fullWidthWrapContentParams())
            addView(createStage(state, theme), spacedParams(top = 10))
            addView(
                createProgress(percent, theme),
                LinearLayout.LayoutParams(MATCH_PARENT, dp(PROGRESS_HEIGHT_DP)).apply {
                    topMargin = dp(22)
                }
            )
            if (percent != null) {
                addView(createPercent(percent, theme), spacedParams(top = 8))
            }
            addView(
                createCancelButton(onCancel, theme),
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = dp(24)
                }
            )
        }
    }

    private fun createFileName(
        state: ForegroundTaskState,
        theme: ForegroundTaskTheme
    ): TextView {
        return TextView(context).apply {
            tag = "foreground-task-file-name"
            text = state.displayName?.takeIf { it.isNotBlank() } ?: "所选文件"
            setTextColor(theme.text)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun createStage(
        state: ForegroundTaskState,
        theme: ForegroundTaskTheme
    ): TextView {
        return TextView(context).apply {
            tag = "foreground-task-stage"
            text = state.stage.label
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun createProgress(
        percent: Int?,
        theme: ForegroundTaskTheme
    ): ProgressBar {
        return ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "foreground-task-progress"
            max = 100
            isIndeterminate = percent == null
            progress = percent ?: 0
            progressTintList = ColorStateList.valueOf(theme.progress)
            progressBackgroundTintList = ColorStateList.valueOf(theme.progressTrack)
            indeterminateTintList = ColorStateList.valueOf(theme.progress)
            contentDescription = percent?.let { "任务进度 $it%" } ?: "任务正在进行"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    private fun createPercent(
        percent: Int,
        theme: ForegroundTaskTheme
    ): TextView {
        return TextView(context).apply {
            tag = "foreground-task-percent"
            text = "$percent%"
            setTextColor(theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun createCancelButton(
        onCancel: () -> Unit,
        theme: ForegroundTaskTheme
    ): Button {
        return Button(context).apply {
            tag = "foreground-task-cancel"
            text = "取消"
            contentDescription = "取消当前任务"
            setAllCaps(false)
            minHeight = dp(CANCEL_HEIGHT_DP)
            minimumHeight = dp(CANCEL_HEIGHT_DP)
            minWidth = 0
            minimumWidth = 0
            stateListAnimator = null
            elevation = 0f
            translationZ = 0f
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = RippleDrawable(
                ColorStateList.valueOf(theme.cancelRipple),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(theme.cancelBackground)
                    cornerRadius = dp(4).toFloat()
                },
                null
            )
            var handled = false
            setOnClickListener {
                if (!handled) {
                    handled = true
                    isEnabled = false
                    onCancel()
                }
            }
        }
    }

    private fun fullWidthHeightParams(heightDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH_PARENT, dp(heightDp))

    private fun fullWidthWrapContentParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    private fun wrapContentParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

    private fun spacedParams(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private data class ForegroundTaskTheme(
        val background: Int,
        val text: Int,
        val muted: Int,
        val progress: Int,
        val progressTrack: Int,
        val cancelBackground: Int,
        val cancelRipple: Int
    ) {
        companion object {
            fun from(isDarkTheme: Boolean): ForegroundTaskTheme {
                return if (isDarkTheme) {
                    ForegroundTaskTheme(
                        background = Color.rgb(28, 28, 29),
                        text = Color.rgb(244, 247, 251),
                        muted = Color.rgb(142, 162, 194),
                        progress = Color.rgb(124, 168, 232),
                        progressTrack = Color.rgb(58, 67, 79),
                        cancelBackground = Color.rgb(48, 55, 65),
                        cancelRipple = Color.argb(64, 244, 247, 251)
                    )
                } else {
                    ForegroundTaskTheme(
                        background = Color.WHITE,
                        text = Color.rgb(15, 23, 42),
                        muted = Color.rgb(100, 116, 139),
                        progress = Color.rgb(37, 99, 235),
                        progressTrack = Color.rgb(226, 232, 240),
                        cancelBackground = Color.rgb(226, 232, 240),
                        cancelRipple = Color.argb(48, 15, 23, 42)
                    )
                }
            }
        }
    }

    private companion object {
        const val APP_BAR_HEIGHT_DP = 59
        const val PROGRESS_HEIGHT_DP = 12
        const val CANCEL_HEIGHT_DP = 44
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
