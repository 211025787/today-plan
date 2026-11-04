package com.focuslist.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 顶部渐变遮罩：从顶部背景色渐变到底部透明，让内容滚动到顶部时淡出融入背景。
 * 不拦截触摸事件，点击穿透到下层内容。
 *
 * @param modifier 布局修饰符（通常带 align 定位到屏幕顶部）
 * @param height 渐变区域高度，越大淡出过渡越柔和
 */
@Composable
fun TopFadeEdge(
    modifier: Modifier = Modifier,
    height: Dp = 128.dp
) {
    // 渐变起点用页面背景色，内容顶部淡入背景而非突兀遮挡
    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(bgColor, Color.Transparent)
                )
            )
    )
}