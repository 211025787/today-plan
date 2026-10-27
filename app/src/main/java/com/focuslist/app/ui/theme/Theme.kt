package com.focuslist.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 滴答蓝（默认主题色） */
val TickBlue = Color(0xFF4772FA)

/** 琥珀强调色（连续天数、提醒） */
val AmberAccent = Color(0xFFFFB300)

/** 逾期/红旗红 */
val FlagRed = Color(0xFFE94B3C)

/** 中优先级黄旗 */
val FlagYellow = Color(0xFFFFB300)

/** 低优先级蓝旗 */
val FlagBlue = Color(0xFF4772FA)

/** iOS 风浅色背景基底（光斑画在其上） */
val IosLightBackground = Color(0xFFF2F2F7)

/** iOS 风深色背景基底 */
val IosDarkBackground = Color(0xFF000000)

/** 玻璃卡片浅色填充：半透明白 */
val GlassLightFill = Color(0x99FFFFFF)

/** 玻璃卡片深色填充：半透明深灰 */
val GlassDarkFill = Color(0x662C2C2E)

/** 玻璃高光描边（浅色） */
val GlassLightBorder = Color(0x80FFFFFF)

/** 玻璃高光描边（深色） */
val GlassDarkBorder = Color(0x33FFFFFF)

/** 可选主题色列表（名称 + 色值），对齐滴答清单的多彩主题 */
val ThemeColors: List<Pair<String, String>> = listOf(
    "滴答蓝" to "#4772FA",
    "番茄红" to "#E94B3C",
    "青草绿" to "#43A047",
    "葡萄紫" to "#8E44AD",
    "活力橙" to "#FB8C00"
)

/**
 * 解析主题色字符串为 [Color]，非法值回退滴答蓝。
 *
 * @param hex 格式 "#RRGGBB"
 * @return 颜色对象
 */
fun parseThemeColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        TickBlue
    }
}

/**
 * 应用 Compose 主题：iOS 玻璃拟态配色，支持深浅色与主题色切换。
 *
 * @param themeMode SYSTEM 跟随系统、LIGHT 浅色、DARK 深色
 * @param themeColorHex 主题色 "#RRGGBB"
 * @param content 页面内容
 */
@Composable
fun FocusListTheme(themeMode: String = "SYSTEM", themeColorHex: String = "#4772FA", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val primary = parseThemeColor(themeColorHex)
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.25f),
            onPrimaryContainer = Color.White,
            secondary = AmberAccent,
            background = IosDarkBackground,
            surface = Color(0xFF1C1C1E),
            onBackground = Color(0xFFF2F2F7),
            onSurface = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFF98989F),
            surfaceVariant = Color(0xFF2C2C2E),
            outline = Color(0xFF48484A)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.10f),
            onPrimaryContainer = primary,
            secondary = AmberAccent,
            background = IosLightBackground,
            surface = Color.White,
            onBackground = Color(0xFF1C1C1E),
            onSurface = Color(0xFF1C1C1E),
            onSurfaceVariant = Color(0xFF8A8A8E),
            surfaceVariant = Color(0xFFE5E5EA),
            outline = Color(0xFFD1D1D6)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}