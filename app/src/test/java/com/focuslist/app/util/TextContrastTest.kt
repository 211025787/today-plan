package com.focuslist.app.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 文字对比度 [TextContrast] 的单元测试：合成色与黑/白字选择。
 */
class TextContrastTest {

    private val white = Color.White
    private val black = Color.Black

    @Test
    fun composite_whiteBaseWithBlackOverlay() {
        // 白底 + 40% 黑 = 60% 灰
        val result = TextContrast.composite(white, black.copy(alpha = 0.4f))

        assertEquals(0.6f, result.red, 0.001f)
        assertEquals(0.6f, result.green, 0.001f)
        assertEquals(0.6f, result.blue, 0.001f)
        assertEquals(1f, result.alpha, 0.001f)
    }

    @Test
    fun composite_fullOverlayIsOverlayItself() {
        val result = TextContrast.composite(white, Color(0xFF3366CC))

        assertEquals(0x33 / 255f, result.red, 0.001f)
        assertEquals(0x66 / 255f, result.green, 0.001f)
        assertEquals(0xCC / 255f, result.blue, 0.001f)
    }

    @Test
    fun composite_zeroOverlayIsBase() {
        val base = Color(0xFF123456)
        val result = TextContrast.composite(base, black.copy(alpha = 0f))

        assertEquals(base.red, result.red, 0.001f)
        assertEquals(base.green, result.green, 0.001f)
        assertEquals(base.blue, result.blue, 0.001f)
    }

    @Test
    fun readableOn_lightBackgroundPicksDark() {
        assertEquals(black, TextContrast.readableOn(Color.White))
        assertEquals(black, TextContrast.readableOn(Color(0xFFF2F2F2)))
    }

    @Test
    fun readableOn_darkBackgroundPicksLight() {
        assertEquals(Color.White, TextContrast.readableOn(Color.Black))
        assertEquals(Color.White, TextContrast.readableOn(Color(0xFF202124)))
    }

    @Test
    fun readableOn_usesProvidedColors() {
        val dark = Color(0xFF111111)
        val light = Color(0xFFEEEEEE)
        assertEquals(dark, TextContrast.readableOn(Color.White, darkText = dark, lightText = light))
        assertEquals(light, TextContrast.readableOn(Color.Black, darkText = dark, lightText = light))
    }

    @Test
    fun colorFor_switchesWhenFillCoversText() {
        val tinted = TextContrast.composite(Color.White, Color(0xFF2244CC).copy(alpha = 0.18f))
        val filled = TextContrast.composite(tinted, Color(0xFF2244CC).copy(alpha = 0.4f))

        // 填充没盖到文字区域 → 按淡底取深色字
        val before = TextContrast.colorFor(
            fraction = 0.2f,
            textStartFraction = 0.7f,
            tintedBackground = tinted,
            filledBackground = filled,
            darkText = black,
            lightText = white
        )
        // 填充盖到了 → 按深底取浅色字
        val covered = TextContrast.colorFor(
            fraction = 0.9f,
            textStartFraction = 0.7f,
            tintedBackground = tinted,
            filledBackground = filled,
            darkText = black,
            lightText = white
        )

        assertEquals(black, before)
        assertEquals(Color.White, covered)
    }

    @Test
    fun colorFor_fractionClamped() {
        val tinted = Color.White
        val filled = Color.Black
        assertEquals(black, TextContrast.colorFor(-1f, 0.7f, tinted, filled, black, white))
        assertEquals(Color.White, TextContrast.colorFor(2f, 0.7f, tinted, filled, black, white))
    }

    @Test
    fun lightPlanColorStaysDarkText() {
        // 浅色计划（比如黄绿色）即使填满，底色仍然偏亮 → 继续用深色字
        val tinted = TextContrast.composite(Color.White, Color(0xFFCCDD66).copy(alpha = 0.18f))
        val filled = TextContrast.composite(tinted, Color(0xFFCCDD66).copy(alpha = 0.4f))

        assertEquals(black, TextContrast.readableOn(filled))
    }
}
