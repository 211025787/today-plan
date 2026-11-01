package com.focuslist.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 文字对比度：把「半透明色块叠在底色上之后到底有多深」算出来，再据此选黑字或白字。
 *
 * 使用场景是日历格子的迷你进度条——进度填充会盖到文字，
 * 深色计划色 + 深色文字叠在一起就看不清了；这里用合成底色的**相对亮度**做判断。
 *
 * 阈值取 0.5（亮度高于一半用深色字，否则用浅色字）：
 * 不是 WCAG 的精确对比度公式，但对「选黑还是选白」这件事足够稳。
 */
object TextContrast {

    /**
     * 把半透明的前景色叠到底色上，得到不透明的合成色。
     *
     * @param base 底色（不透明）
     * @param overlay 前景色（其 alpha 即叠加浓度）
     * @return 合成后的不透明色
     */
    fun composite(base: Color, overlay: Color): Color {
        val a = overlay.alpha.coerceIn(0f, 1f)
        fun mix(baseCh: Float, overlayCh: Float): Float = baseCh * (1f - a) + overlayCh * a
        return Color(
            red = mix(base.red, overlay.red),
            green = mix(base.green, overlay.green),
            blue = mix(base.blue, overlay.blue),
            alpha = 1f
        )
    }

    /**
     * 按底色亮度选择可读的文字颜色。
     *
     * @param background 文字背后的合成底色
     * @param darkText 亮底上用的深色字
     * @param lightText 暗底上用的浅色字
     * @return 二者之一
     */
    fun readableOn(
        background: Color,
        darkText: Color = Color.Black,
        lightText: Color = Color.White
    ): Color {
        return if (background.luminance() > 0.5f) darkText else lightText
    }

    /**
     * 迷你进度条里文字的取色：填充盖到哪里，就按哪段底色算。
     *
     * @param fraction 进度填充比例 0-1
     * @param textStartFraction 文字区域左边缘在整行中的位置（0-1）
     * @param tintedBackground 只有淡色底时的合成底色
     * @param filledBackground 被深色填充盖住时的合成底色
     * @param darkText 亮底用的文字色
     * @param lightText 暗底用的文字色
     * @return 文字颜色
     */
    fun colorFor(
        fraction: Float,
        textStartFraction: Float,
        tintedBackground: Color,
        filledBackground: Color,
        darkText: Color,
        lightText: Color
    ): Color {
        val safeFraction = fraction.coerceIn(0f, 1f)
        return if (safeFraction >= textStartFraction) {
            readableOn(filledBackground, darkText, lightText)
        } else {
            readableOn(tintedBackground, darkText, lightText)
        }
    }
}
