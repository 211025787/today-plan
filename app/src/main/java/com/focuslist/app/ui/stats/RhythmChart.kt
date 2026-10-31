package com.focuslist.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.Rhythm

/** 24 小时柱状图高度 */
private val RHYTHM_CHART_HEIGHT = 84.dp

/** 时段占比条高度 */
private val SLOT_BAR_HEIGHT = 8.dp

/**
 * 专注节律卡片：24 小时分布柱状图 + 六大时段占比 + 黄金时段时间文案 + 工作日 / 周末对比。
 *
 * @param summary 统计汇总（提供按小时分布与热力图按天数据）
 * @param modifier 布局修饰符
 */
@Composable
fun RhythmCard(summary: StatsSummary, modifier: Modifier = Modifier) {
    // 这些聚合只依赖 summary，缓存后避免每次重组都重算（区间内有一天循环）
    val byHour = remember(summary.rhythmByHour) { Rhythm.normalize(summary.rhythmByHour) }
    val slotMinutes = remember(byHour) { Rhythm.sumBySlot(byHour) }
    val shares = remember(byHour) { Rhythm.share(byHour) }
    val peaks = remember(byHour) { Rhythm.peakHours(byHour).toSet() }
    val balance = remember(summary.heatByDay, summary.heatRangeStart, summary.heatRangeEnd) {
        Rhythm.kindAverages(summary.heatByDay, summary.heatRangeStart, summary.heatRangeEnd)
    }
    val balanceText = remember(balance) { Rhythm.balanceText(balance.first, balance.second) }
    val summaryText = remember(byHour) { Rhythm.summaryText(byHour) }
    val bestSlot = remember(byHour) { Rhythm.bestSlot(byHour) }
    val hasData = Rhythm.total(byHour) > 0

    val primary = MaterialTheme.colorScheme.primary
    val peakColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("专注节律", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!hasData) {
                return@GlassCard
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 24 小时分布：每根柱子一小时，峰值小时换高亮色
            val maxValue = byHour.maxOrNull()?.coerceAtLeast(1) ?: 1
            Canvas(modifier = Modifier.fillMaxWidth().height(RHYTHM_CHART_HEIGHT)) {
                val slotWidth = size.width / Rhythm.HOURS
                val barWidth = slotWidth * 0.62f
                byHour.forEachIndexed { hour, minutes ->
                    val left = hour * slotWidth + (slotWidth - barWidth) / 2f
                    if (minutes <= 0) {
                        // 无数据的小时画一小段底衬，保持 24 格的节奏感
                        drawRoundRect(
                            color = emptyColor,
                            topLeft = Offset(left, size.height - 2f),
                            size = Size(barWidth, 2f),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                        return@forEachIndexed
                    }
                    val ratio = minutes.toFloat() / maxValue.toFloat()
                    val barHeight = (size.height * ratio).coerceAtLeast(3f)
                    val color = if (hour in peaks) {
                        peakColor
                    } else {
                        primary.copy(alpha = 0.35f + 0.65f * ratio)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0点", "6点", "12点", "18点", "24点").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 六大时段占比
            slotMinutes.forEachIndexed { slot, minutes ->
                val percent = shares.getOrElse(slot) { 0 }
                SlotShareRow(
                    name = Rhythm.slotName(slot),
                    range = Rhythm.slotRangeText(slot),
                    percent = percent,
                    minutes = minutes,
                    fillColor = if (slot == bestSlot) peakColor else primary,
                    trackColor = trackColor
                )
                if (slot != slotMinutes.lastIndex) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = balanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个时段的占比条：名称 + 区间 + 进度条 + 百分比。
 *
 * @param name 时段名称
 * @param range 时段的小时区间文案
 * @param percent 占全天百分比
 * @param minutes 该时段累计分钟数
 * @param fillColor 进度条填充色
 * @param trackColor 进度条底色
 */
@Composable
private fun SlotShareRow(
    name: String,
    range: String,
    percent: Int,
    minutes: Int,
    fillColor: Color,
    trackColor: Color
) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(34.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodySmall)
            Text(
                text = range.replace(" ", ""),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SLOT_BAR_HEIGHT)
                .background(trackColor, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fillColor, RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (minutes > 0) "$percent%" else "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
    }
}
