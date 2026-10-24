package com.personalai.instantread.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import com.personalai.instantread.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 图标必须真的能画出来。
 *
 * 原型图标全部由 VectorDrawable 路径组成，路径写错时不会编译失败，只会静默变成空白。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InstantReadIconsRenderTest {
    @Test
    fun everyPrototypeIconDrawsVisiblePixels() {
        val icons = mapOf(
            "moon" to R.drawable.ic_instantread_moon,
            "sun" to R.drawable.ic_instantread_sun,
            "gear" to R.drawable.ic_instantread_gear,
            "help" to R.drawable.ic_instantread_help,
            "shield" to R.drawable.ic_instantread_shield,
            "search" to R.drawable.ic_instantread_search,
            "search-preview" to R.drawable.ic_instantread_search_preview,
            "back" to R.drawable.ic_instantread_back,
            "star" to R.drawable.ic_instantread_star_outline,
            "star-filled" to R.drawable.ic_instantread_star_filled,
            "trash" to R.drawable.ic_instantread_trash,
            "file-html" to R.drawable.ic_instantread_file_html,
            "file-markdown" to R.drawable.ic_instantread_file_markdown,
            "file-zip" to R.drawable.ic_instantread_file_zip,
            "file-text" to R.drawable.ic_instantread_file_text,
            "external-link" to R.drawable.ic_instantread_external_link,
            "archive" to R.drawable.ic_instantread_archive,
            "clear-cache" to R.drawable.ic_instantread_clear_cache
        )

        icons.forEach { (name, resource) ->
            assertTrue("$name should draw visible pixels", drawnPixelCount(resource) > 0)
        }
    }

    @Test
    fun filledStarDrawsMorePixelsThanOutlineStar() {
        assertTrue(
            drawnPixelCount(R.drawable.ic_instantread_star_filled) >
                drawnPixelCount(R.drawable.ic_instantread_star_outline)
        )
    }

    @Test
    fun iconKeepsDeclaredSizeInsideLargerTouchTarget() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density

        // 顶部栏与列表行的按钮容器是 34x44dp，图标声明 17dp；容器只提供触控区，
        // 不能把图标放大去填满，否则按钮会明显比原型大。
        val natural = inkBounds(InstantIcons.Star, 17f, 17, 17, density)
        val inButton = inkBounds(InstantIcons.Star, 17f, 34, 44, density)

        assertTrue("star should draw inside the button container", inButton != null)
        assertEquals("icon must not be scaled up to fill a larger container", natural!!.width(), inButton!!.width())
        assertEquals(natural.height(), inButton.height())
    }

    private fun inkBounds(
        icon: InstantIcon,
        iconWidthDp: Float,
        containerWidthDp: Int,
        containerHeightDp: Int,
        density: Float
    ): Rect? {
        val widthPx = (containerWidthDp * density).roundToInt()
        val heightPx = (containerHeightDp * density).roundToInt()
        val view = InstantIconView(RuntimeEnvironment.getApplication(), icon, Color.BLACK, iconWidthDp)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        view.draw(Canvas(bitmap))

        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) == Color.WHITE) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x > right) right = x
                if (y > bottom) bottom = y
            }
        }
        return if (right < left) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun drawnPixelCount(resource: Int): Int {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val drawable = requireNotNull(context.getDrawable(resource)).mutate()
        drawable.setTint(Color.BLACK)
        drawable.setBounds(16, 16, 80, 80)
        drawable.draw(canvas)

        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (bitmap.getPixel(x, y) != Color.WHITE) count++
            }
        }
        return count
    }
}
