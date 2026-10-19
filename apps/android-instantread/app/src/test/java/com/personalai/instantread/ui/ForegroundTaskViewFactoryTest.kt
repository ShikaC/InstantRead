package com.personalai.instantread.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.personalai.instantread.tasks.ForegroundTaskStage
import com.personalai.instantread.tasks.ForegroundTaskState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundTaskViewFactoryTest {
    @Test
    fun lightDeterminateTaskShowsBrandProgressAndAccessibleCancelAction() {
        var cancelCount = 0

        val view: FrameLayout = factory().create(
            state = ForegroundTaskState(
                displayName = "report.zip",
                stage = ForegroundTaskStage.IMPORTING_FILE,
                completedBytes = 25,
                totalBytes = 100
            ),
            isDarkTheme = false,
            onCancel = { cancelCount += 1 }
        )

        val visibleText = view.visibleText()
        assertTrue(visibleText.contains("即阅"))
        assertTrue(visibleText.contains("LOCAL PREVIEW"))
        assertTrue(visibleText.contains("report.zip"))
        assertTrue(visibleText.contains("正在导入文件"))
        assertTrue(visibleText.contains("25%"))
        assertTrue(visibleText.contains("取消"))

        val progress = view.singleViewWithTag("foreground-task-progress") as ProgressBar
        assertEquals(100, progress.max)
        assertFalse(progress.isIndeterminate)
        assertEquals(25, progress.progress)
        assertEquals(
            "25%",
            (view.singleViewWithTag("foreground-task-percent") as TextView).text.toString()
        )

        val cancel = view.singleViewWithTag("foreground-task-cancel") as Button
        assertEquals("取消", cancel.text.toString())
        assertEquals("取消当前任务", cancel.contentDescription)
        assertNull("Cancel text must not use the platform all-caps transformation.", cancel.transformationMethod)
        assertTrue(cancel.minimumHeight >= dp(44))
        cancel.performClick()
        assertEquals(1, cancelCount)

        assertEquals(Color.WHITE, view.backgroundColor())
        val root = view.singleViewWithTag("foreground-task-root")
        assertEquals(Color.WHITE, root.backgroundColor())
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, root.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, root.layoutParams.height)

        val fileName = view.singleViewWithTag("foreground-task-file-name") as TextView
        val stage = view.singleViewWithTag("foreground-task-stage") as TextView
        assertEquals(Color.rgb(15, 23, 42), fileName.currentTextColor)
        assertEquals(Color.rgb(100, 116, 139), stage.currentTextColor)
        assertTrue(contrastRatio(fileName.currentTextColor, Color.WHITE) >= 4.5)
        assertTrue(contrastRatio(stage.currentTextColor, Color.WHITE) >= 4.5)
    }

    @Test
    fun darkUnknownTotalUsesIndeterminateProgressWithoutPercentText() {
        val view: FrameLayout = factory().create(
            state = ForegroundTaskState(
                displayName = "site.zip",
                stage = ForegroundTaskStage.EXTRACTING_ZIP
            ),
            isDarkTheme = true,
            onCancel = {}
        )

        val background = Color.rgb(28, 28, 29)
        val progress = view.singleViewWithTag("foreground-task-progress") as ProgressBar
        assertTrue(progress.isIndeterminate)
        assertEquals(100, progress.max)
        assertTrue(view.viewsWithTag("foreground-task-percent").isEmpty())
        assertFalse(view.visibleText().any { it.endsWith("%") })
        assertEquals(background, view.backgroundColor())
        assertEquals(background, view.singleViewWithTag("foreground-task-root").backgroundColor())

        val fileName = view.singleViewWithTag("foreground-task-file-name") as TextView
        val stage = view.singleViewWithTag("foreground-task-stage") as TextView
        assertEquals(Color.rgb(244, 247, 251), fileName.currentTextColor)
        assertEquals(Color.rgb(142, 162, 194), stage.currentTextColor)
        assertTrue(contrastRatio(fileName.currentTextColor, background) >= 4.5)
        assertTrue(contrastRatio(stage.currentTextColor, background) >= 4.5)
    }

    @Test
    fun nullOrBlankDisplayNameFallsBackToSelectedFile() {
        listOf<String?>(null, "", "   \t").forEach { displayName ->
            val view: FrameLayout = factory().create(
                state = ForegroundTaskState(
                    displayName = displayName,
                    stage = ForegroundTaskStage.READING_METADATA
                ),
                isDarkTheme = false,
                onCancel = {}
            )

            assertEquals(
                "所选文件",
                (view.singleViewWithTag("foreground-task-file-name") as TextView).text.toString()
            )
        }
    }

    @Test
    fun longFileNameStaysOnOneMiddleEllipsizedLine() {
        val longName = "very-long-".repeat(40) + "document.md"
        val view: FrameLayout = factory().create(
            state = ForegroundTaskState(
                displayName = longName,
                stage = ForegroundTaskStage.GENERATING_READING_PAGE,
                completedBytes = 1,
                totalBytes = 2
            ),
            isDarkTheme = false,
            onCancel = {}
        )

        val fileName = view.singleViewWithTag("foreground-task-file-name") as TextView
        assertEquals(longName, fileName.text.toString())
        assertEquals(1, fileName.maxLines)
        assertSame(TextUtils.TruncateAt.MIDDLE, fileName.ellipsize)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, fileName.layoutParams.width)
    }

    @Test
    fun taskHierarchyUsesUniqueTagsAndStableTouchSafeDimensions() {
        val view: FrameLayout = factory().create(
            state = ForegroundTaskState(
                displayName = "book.md",
                stage = ForegroundTaskStage.CHECKING_TEXT_ENCODING,
                completedBytes = 60,
                totalBytes = 100
            ),
            isDarkTheme = false,
            onCancel = {}
        )

        listOf(
            "foreground-task-root",
            "foreground-task-app-bar",
            "foreground-task-scroll",
            "foreground-task-content",
            "foreground-task-file-name",
            "foreground-task-stage",
            "foreground-task-progress",
            "foreground-task-percent",
            "foreground-task-cancel"
        ).forEach { tag ->
            assertEquals("Expected one view tagged $tag.", 1, view.viewsWithTag(tag).size)
        }

        assertHeightDp(59, view.singleViewWithTag("foreground-task-app-bar"))
        assertHeightDp(12, view.singleViewWithTag("foreground-task-progress"))
        val cancel = view.singleViewWithTag("foreground-task-cancel")
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, cancel.layoutParams.height)
        assertTrue(cancel.minimumHeight >= dp(44))

        val content = view.singleViewWithTag("foreground-task-content") as LinearLayout
        assertEquals(Gravity.CENTER, content.gravity)
        assertEquals(dp(24), content.paddingLeft)
        assertEquals(dp(24), content.paddingTop)
        assertEquals(dp(24), content.paddingRight)
        assertEquals(dp(24), content.paddingBottom)

        val root = view.singleViewWithTag("foreground-task-root") as LinearLayout
        assertEquals(2, root.childCount)
        val scroll = view.singleViewWithTag("foreground-task-scroll") as ScrollView
        assertTrue(scroll.isFillViewport)
        assertSame(scroll, root.getChildAt(1))
    }

    @Test
    fun largeFontsOnSmallScreenKeepContentScrollableAndCancelTouchSafe() {
        val baseContext = RuntimeEnvironment.getApplication() as Context
        val configuration = Configuration(baseContext.resources.configuration).apply {
            fontScale = 2f
        }
        val largeTextContext = baseContext.createConfigurationContext(configuration)
        val view = ForegroundTaskViewFactory(largeTextContext).create(
            state = ForegroundTaskState(
                displayName = "a-very-long-accessible-document-name.md",
                stage = ForegroundTaskStage.GENERATING_READING_PAGE,
                completedBytes = 25,
                totalBytes = 100
            ),
            isDarkTheme = false,
            onCancel = {}
        )
        val width = dp(largeTextContext, 320)
        val height = dp(largeTextContext, 240)

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val scroll = view.singleViewWithTag("foreground-task-scroll") as ScrollView
        val content = view.singleViewWithTag("foreground-task-content") as LinearLayout
        val cancel = view.singleViewWithTag("foreground-task-cancel") as Button
        assertEquals(width, view.measuredWidth)
        assertEquals(height, view.measuredHeight)
        assertTrue(scroll.isFillViewport)
        assertTrue(content.measuredHeight > scroll.measuredHeight)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, cancel.layoutParams.height)
        assertTrue(cancel.measuredHeight >= dp(largeTextContext, 44))

        assertTrue(scroll.fullScroll(View.FOCUS_DOWN))
        assertTrue(scroll.scrollY > 0)
        assertTrue(cancel.top >= scroll.scrollY)
        assertTrue(cancel.bottom <= scroll.scrollY + scroll.height)
    }

    @Test
    fun cancelUsesPressedFeedbackAndDispatchesAtMostOnce() {
        var cancelCount = 0
        val view = factory().create(
            state = ForegroundTaskState(
                displayName = "report.zip",
                stage = ForegroundTaskStage.IMPORTING_FILE
            ),
            isDarkTheme = false,
            onCancel = { cancelCount += 1 }
        )

        val cancel = view.singleViewWithTag("foreground-task-cancel") as Button
        cancel.performClick()
        cancel.performClick()

        assertEquals(1, cancelCount)
        assertFalse(cancel.isEnabled)
        assertTrue(cancel.background is RippleDrawable)
    }

    @Test
    fun progressExposesAccessibleDescriptionsForDeterminateAndUnknownTotals() {
        val determinate = factory().create(
            state = ForegroundTaskState(
                displayName = "report.zip",
                stage = ForegroundTaskStage.IMPORTING_FILE,
                completedBytes = 25,
                totalBytes = 100
            ),
            isDarkTheme = false,
            onCancel = {}
        ).singleViewWithTag("foreground-task-progress") as ProgressBar
        val indeterminate = factory().create(
            state = ForegroundTaskState(
                displayName = "site.zip",
                stage = ForegroundTaskStage.EXTRACTING_ZIP
            ),
            isDarkTheme = true,
            onCancel = {}
        ).singleViewWithTag("foreground-task-progress") as ProgressBar

        assertEquals("任务进度 25%", determinate.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, determinate.importantForAccessibility)
        assertEquals("任务正在进行", indeterminate.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, indeterminate.importantForAccessibility)
    }

    private fun factory(): ForegroundTaskViewFactory =
        ForegroundTaskViewFactory(RuntimeEnvironment.getApplication())

    private fun View.visibleText(): List<String> =
        findAll { it is TextView }
            .map { (it as TextView).text.toString() }

    private fun View.viewsWithTag(expected: String): List<View> =
        findAll { it.tag == expected }

    private fun View.singleViewWithTag(expected: String): View {
        val matches = viewsWithTag(expected)
        assertEquals("Expected one view tagged $expected.", 1, matches.size)
        return matches.single()
    }

    private fun View.findAll(predicate: (View) -> Boolean): List<View> {
        val result = mutableListOf<View>()
        fun collect(view: View) {
            if (predicate(view)) result += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    collect(view.getChildAt(index))
                }
            }
        }
        collect(this)
        return result
    }

    private fun View.backgroundColor(): Int =
        (background as ColorDrawable).color

    private fun assertHeightDp(expected: Int, view: View) {
        assertEquals("Unexpected height for ${view.tag}.", dp(expected), view.layoutParams.height)
    }

    private fun dp(value: Int): Int {
        return dp(RuntimeEnvironment.getApplication(), value)
    }

    private fun dp(context: Context, value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
