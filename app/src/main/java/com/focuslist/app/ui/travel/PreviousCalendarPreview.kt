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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「上一页」预览：旅游地图页侧滑返回时，从左边露出来的日历主页。
 *
 * 刻意画成**静态的同构图**（月份标题 / 建议条 / 星期表头 / 6×7 格子 / 几根计划条 / 底部 Dock），
 * 不订阅任何数据仓库、不重组、不联网——手势进行中它只是一张底图，跟着手指向左轻微归位即可。
 * 用真实主题色，所以浅色 / 深色下都和真正的日历页一致。
 *
 * @param modifier 外部修饰（由调用方挂 graphicsLayer 控制透明度与视差）
 */
@Composable
fun PreviousCalendarPreview(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val cell = scheme.onSurfaceVariant.copy(alpha = 0.09f)
    val strip = scheme.onSurfaceVariant.copy(alpha = 0.16f)
    val accent = scheme.primary
    val monthLabel = remember {
        SimpleDateFormat("M 月", Locale.CHINA).format(Date())
    }

    Box(
        modifier = modifier
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 78.dp, bottom = 96.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 顶栏标题：和日历页一样显示当前月份
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface
            )
            // 「现在」建议条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cell)
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 星期表头
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
            // 6 行 × 7 列月历，零星几格放上计划条，看着像真的日历
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

        // 底部 Dock 占位：让露出的左半边也有「上一页」的完整轮廓
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 190.dp, height = 44.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cell)
        )
    }
}
