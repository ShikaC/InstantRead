package com.personalai.instantread.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import com.personalai.instantread.R
import kotlin.math.roundToInt

/**
 * 原型 SVG 对应的原生矢量图标。
 *
 * 图形路径保存在 res/drawable 的 VectorDrawable 中，各页面只负责选择图标、尺寸和颜色。
 */
internal data class InstantIcon(
    val drawableRes: Int,
    val activeDrawableRes: Int? = null
)

internal object InstantIcons {
    val Moon = InstantIcon(R.drawable.ic_instantread_moon)
    val Sun = InstantIcon(R.drawable.ic_instantread_sun)
    val Gear = InstantIcon(R.drawable.ic_instantread_gear)
    val Help = InstantIcon(R.drawable.ic_instantread_help)
    val Shield = InstantIcon(R.drawable.ic_instantread_shield)
    val Search = InstantIcon(R.drawable.ic_instantread_search)
    val PreviewSearch = InstantIcon(R.drawable.ic_instantread_search_preview)
    val Back = InstantIcon(R.drawable.ic_instantread_back)
    val Star = InstantIcon(
        drawableRes = R.drawable.ic_instantread_star_outline,
        activeDrawableRes = R.drawable.ic_instantread_star_filled
    )
    val Trash = InstantIcon(R.drawable.ic_instantread_trash)
    val FileHtml = InstantIcon(R.drawable.ic_instantread_file_html)
    val FileMarkdown = InstantIcon(R.drawable.ic_instantread_file_markdown)
    val FileZip = InstantIcon(R.drawable.ic_instantread_file_zip)
    val FileText = InstantIcon(R.drawable.ic_instantread_file_text)
    val ExternalLink = InstantIcon(R.drawable.ic_instantread_external_link)
    val Archive = InstantIcon(R.drawable.ic_instantread_archive)
    val ClearCache = InstantIcon(R.drawable.ic_instantread_clear_cache)
}

// 图标视图只在代码中构建，不需要 XML 的 (Context, AttributeSet) 构造器。
@SuppressLint("ViewConstructor")
internal class InstantIconView(
    context: Context,
    private val icon: InstantIcon,
    private val tint: Int,
    private val iconWidthDp: Float,
    private val active: Boolean = false,
    private val activeTint: Int = tint,
    private val pressedTint: Int? = null
) : View(context) {
    private val normalDrawable = requireNotNull(context.getDrawable(icon.drawableRes)).mutate()
    private val activeDrawable = icon.activeDrawableRes
        ?.let { requireNotNull(context.getDrawable(it)).mutate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val drawable = if (active && activeDrawable != null) activeDrawable else normalDrawable
        val color = when {
            pressedTint != null && isPressed -> pressedTint
            active -> activeTint
            else -> tint
        }
        drawable.setTint(color)

        val density = resources.displayMetrics.density
        val naturalWidth = iconWidthDp * density
        val naturalHeight = naturalWidth * drawable.intrinsicHeight / drawable.intrinsicWidth
        // 容器只提供触控区域和留白：图标始终按 iconWidthDp 绘制，容器更大时居中不放大。
        val fit = minOf(1f, width / naturalWidth, height / naturalHeight)
        val iconWidth = naturalWidth * fit
        val iconHeight = naturalHeight * fit
        val left = ((width - iconWidth) / 2f).roundToInt()
        val top = ((height - iconHeight) / 2f).roundToInt()
        drawable.setBounds(
            left,
            top,
            left + iconWidth.roundToInt(),
            top + iconHeight.roundToInt()
        )
        drawable.draw(canvas)
    }
}

internal class InstantIconDrawable(
    context: Context,
    icon: InstantIcon,
    color: Int,
    iconWidthDp: Float
) : Drawable() {
    private val vector = requireNotNull(context.getDrawable(icon.drawableRes)).mutate().apply {
        setTint(color)
    }
    private val intrinsicWidth =
        (iconWidthDp * context.resources.displayMetrics.density).roundToInt()
    private val intrinsicHeight =
        intrinsicWidth * vector.intrinsicHeight / vector.intrinsicWidth

    override fun getIntrinsicWidth(): Int = intrinsicWidth

    override fun getIntrinsicHeight(): Int = intrinsicHeight

    override fun draw(canvas: Canvas) {
        vector.setBounds(bounds)
        vector.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        vector.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        vector.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun instantIconDrawable(
    context: Context,
    icon: InstantIcon,
    color: Int,
    iconWidthDp: Float
): Drawable = InstantIconDrawable(context, icon, color, iconWidthDp)
