package com.focuslist.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focuslist.app.ui.theme.GlassDarkBorder
import com.focuslist.app.ui.theme.GlassDarkFill
import com.focuslist.app.ui.theme.GlassLightBorder
import com.focuslist.app.ui.theme.GlassLightFill

/**
 * iOS 玻璃拟态页面背景：基底灰 + 三团主题色柔光斑，玻璃卡片浮于其上产生通透感。
 *
 * @param modifier 布局修饰符
 * @param content 页面内容
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize().background(base)) {
        // 柔光斑层：三个低透明径向渐变圆，模拟 iOS 彩色光晕
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaMain = if (dark) 0.20f else 0.16f
            // 左上主色光斑
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = alphaMain), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.08f),
                    radius = w * 0.7f
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.15f, h * 0.08f)
            )
            // 右上琥珀补光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFB300).copy(alpha = alphaMain * 0.7f), Color.Transparent),
                    center = Offset(w * 0.9f, h * 0.25f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.9f, h * 0.25f)
            )
            // 底部紫色氛围光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8E44AD).copy(alpha = alphaMain * 0.6f), Color.Transparent),
                    center = Offset(w * 0.4f, h * 0.95f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.4f, h * 0.95f)
            )
        }
        content()
    }
}

/**
 * 玻璃容器修饰符：半透明填充 + 1px 高光描边 + 圆角。
 *
 * @param cornerRadius 圆角，默认 20dp
 * @param fillAlpha 填充透明度增量（基于默认玻璃色），默认 null 用标准玻璃色
 * @return 修饰符
 */
@Composable
fun Modifier.glass(cornerRadius: Dp = 20.dp, fillAlpha: Float? = null): Modifier {
    val dark = isSystemInDarkTheme()
    val fillBase = if (dark) GlassDarkFill else GlassLightFill
    val fill = if (fillAlpha != null) fillBase.copy(alpha = fillAlpha) else fillBase
    val stroke = if (dark) GlassDarkBorder else GlassLightBorder
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(fill)
        .border(width = 0.5.dp, color = stroke, shape = RoundedCornerShape(cornerRadius))
}

/**
 * 玻璃卡片：iOS 风格的通透内容容器。
 *
 * @param modifier 布局修饰符
 * @param cornerRadius 圆角，默认 20dp
 * @param content 卡片内容
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, cornerRadius: Dp = 20.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.glass(cornerRadius)) {
        content()
    }
}