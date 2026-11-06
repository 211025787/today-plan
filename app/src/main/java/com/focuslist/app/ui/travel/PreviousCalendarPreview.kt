package com.focuslist.app.ui.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [已废弃] 早期「上一页」静态预览：旅游页改为叠在真实日历页上的覆盖层后不再被调用，
 * 底下直接就是真实的月历 / 卡片页，文件保留供参考。
 *
 * 原来的用途：旅游地图页侧滑返回时，从左边露出来的画面。
 *
 * 有两种形态，由进入旅游页之前停在哪一页决定：
 * - 月历主页：月份标题 / 建议条 / 星期表头 / 6×7 格子 / 底部 Dock；
 * - 日卡片页：日期标题 / 返回行 / 周条 / 2×2 渐变方卡。
 *
 * 都是**静态同构图**，不订阅任何数据仓库、不重组、不联网——手势进行中它只是一张底图。
 *
 * @param dayCardMode true 画日卡片页，false 画月历主页
 * @param modifier 外部修饰（由调用方挂 graphicsLayer 控制透明度与视差）
 */
@Composable
fun PreviousCalendarPreview(
    dayCardMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(scheme.background)
            .statusBarsPadding()
    ) {
        if (dayCardMode) {
            DayCardPreview()
        } else {
            MonthPreview()
        }
        // 底部 Dock 占位：让露出的左半边也有「上一页」的完整轮廓
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 190.dp, height = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.onSurfaceVariant.copy(alpha = 0.09f))
        )
    }
}

/** 月历主页的静态同构预览 */
@Composable
private fun MonthPreview() {
    val scheme = MaterialTheme.colorScheme
    val cell = scheme.onSurfaceVariant.copy(alpha = 0.09f)
    val strip = scheme.onSurfaceVariant.copy(alpha = 0.16f)
    val accent = scheme.primary
    val monthLabel = remember { SimpleDateFormat("M 月", Locale.CHINA).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 78.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cell)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        repeat(6) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cell)
                    ) {
                        if (index % 5 == 2 || index % 11 == 7) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(3.dp).padding(top = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(accent.copy(alpha = 0.55f))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.62f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(strip)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 日卡片页的静态同构预览：日期标题 + 返回行 + 周条 + 2×2 计划方卡 */
@Composable
private fun DayCardPreview() {
    val scheme = MaterialTheme.colorScheme
    val cell = scheme.onSurfaceVariant.copy(alpha = 0.09f)
    val dateLabel = remember { SimpleDateFormat("M 月 d 日", Locale.CHINA).format(Date()) }
    val cardColors = listOf(
        scheme.primary,
        Color(0xFF8E7BF0),
        Color(0xFF3FA796),
        Color(0xFFFB8C00)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 78.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶栏标题：卡片页显示的是选中的那一天
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onSurface
        )
        // 「‹ 返回日历 …… 收工」那一行
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.primary.copy(alpha = 0.55f))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cell)
            )
        }
        // 周条：7 天横排，中间那格高亮成选中日
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(7) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (index == 3) scheme.primary.copy(alpha = 0.38f) else cell
                        )
                )
            }
        }
        // 2×2 渐变方卡：图标占位 + 标题 + 底部进度条
        repeat(2) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(2) { col ->
                    val color = cardColors[row * 2 + col]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(color.copy(alpha = 0.85f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.White.copy(alpha = 0.75f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (col == 0) 0.62f else 0.35f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
