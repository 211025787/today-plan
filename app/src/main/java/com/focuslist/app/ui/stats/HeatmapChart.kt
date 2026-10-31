package com.focuslist.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.Heatmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 热力图格子边长 */
private val CELL_SIZE: Dp = 12.dp

/** 格子间距 */
private val CELL_GAP: Dp = 3.dp

/** 顶部月份标签高度 */
private val MONTH_LABEL_HEIGHT: Dp = 18.dp

/** 四个层级的透明度（由浅到深） */
private val LEVEL_ALPHAS = floatArrayOf(0.20f, 0.42f, 0.66f, 1.0f)

/**
 * 年度专注热力图卡片：GitHub 贡献图风格，列 = 周、行 = 周一…周日，
 * 颜色深浅表示当天专注时长，点击格子查看具体数值；默认滚动到最近一周。
 *
 * @param summary 统计汇总（提供热力图区间与按天数据）
 * @param modifier 布局修饰符
 */
@Composable
fun YearHeatmapCard(summary: StatsSummary, modifier: Modifier = Modifier) {
    if (summary.heatRangeEnd <= 0L || summary.heatRangeEnd < summary.heatRangeStart) {
        return
    }
    val grid = remember(summary.heatRangeStart, summary.heatRangeEnd, summary.heatByDay) {
        Heatmap.buildGrid(summary.heatRangeStart, summary.heatRangeEnd, summary.heatByDay)
    }
    if (grid.columns.isEmpty()) {
        return
    }

    var selectedDay by remember { mutableStateOf(-1L) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val cellPx = with(density) { CELL_SIZE.toPx() }
    val labelPx = with(density) { MONTH_LABEL_HEIGHT.toPx() }
    val step = cellPx + with(density) { CELL_GAP.toPx() }
    val gridWidth = with(density) { (grid.columns.size * step).toDp() }
    val gridHeight = with(density) { (labelPx + 7 * step).toDp() }

    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val ringColor = MaterialTheme.colorScheme.onSurface
    val todayStart = summary.heatRangeEnd
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("年度专注热力图", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (summary.year.activeDays == 0) {
                    "还没有专注记录"
                } else {
                    "过去一年 ${summary.year.activeDays} 天有专注"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                // 左侧星期标签（只在有内容的行标注）
                Column(modifier = Modifier.width(16.dp).padding(top = MONTH_LABEL_HEIGHT)) {
                    listOf("一", "", "三", "", "五", "", "日").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(CELL_SIZE + CELL_GAP)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))
                Canvas(
                    modifier = Modifier
                        .width(gridWidth)
                        .height(gridHeight)
                        .pointerInput(grid, selectedDay) {
                            detectTapGestures { tapOffset ->
                                val col = (tapOffset.x / step).toInt()
                                val row = ((tapOffset.y - labelPx) / step).toInt()
                                val cell = grid.columns.getOrNull(col)?.getOrNull(row)
                                if (cell != null) {
                                    selectedDay = if (selectedDay == cell.dayStart) -1L else cell.dayStart
                                }
                            }
                        }
                ) {
                    // 月份标签：该周首日所属月份变化时打标
                    grid.monthLabels.forEach { (index, label) ->
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            style = labelStyle,
                            topLeft = Offset(index * step, 0f)
                        )
                    }

                    // 格子：列 = 周，行 = 周一…周日
                    grid.columns.forEachIndexed { col, week ->
                        week.forEachIndexed { row, cell ->
                            if (cell == null) {
                                return@forEachIndexed
                            }
                            val x = col * step
                            val y = labelPx + row * step
                    val radius = CornerRadius(3f, 3f)
                    val cellSize = Size(cellPx, cellPx)
                            drawRoundRect(
                                color = heatColorOf(cell.level, primary, emptyColor),
                                topLeft = Offset(x, y),
                                size = cellSize,
                                cornerRadius = radius
                            )
                            // 今天 / 选中：加一圈描边，便于定位
                            if (cell.dayStart == todayStart || cell.dayStart == selectedDay) {
                                drawRoundRect(
                                    color = ringColor,
                                    topLeft = Offset(x, y),
                                    size = cellSize,
                                    cornerRadius = radius,
                                    style = Stroke(width = 1.2f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 图例：少 -> 多
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "少",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        LEVEL_ALPHAS.forEach { alpha ->
                            Canvas(modifier = Modifier.width(CELL_SIZE).height(CELL_SIZE)) {
                                drawRoundRect(
                                    color = primary.copy(alpha = alpha),
                                    topLeft = Offset.Zero,
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = CornerRadius(3f, 3f)
                            )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "多",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = selectedDayText(selectedDay, summary.heatByDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 生成选中日期的说明文案。
 *
 * @param dayStart 选中日（本地 0 点），-1 表示未选中
 * @param minutesByDay 每天专注分钟数
 * @return 文案，例如 "11月1日 · 45 分钟"
 */
internal fun selectedDayText(dayStart: Long, minutesByDay: Map<Long, Int>): String {
    if (dayStart <= 0L) {
        return "点击格子查看当天"
    }
    val formatter = SimpleDateFormat("M月d日", Locale.CHINA)
    val minutes = minutesByDay[dayStart] ?: 0
    val dateText = formatter.format(Date(dayStart))
    return if (minutes > 0) "$dateText · $minutes 分钟" else "$dateText · 未专注"
}

/**
 * 生成热力图某一等级对应的颜色。
 *
 * @param level 等级 0~4
 * @param primary 主题色
 * @param empty 空格颜色
 * @return 对应颜色
 */
internal fun heatColorOf(level: Int, primary: Color, empty: Color): Color {
    if (level <= 0) {
        return empty
    }
    return primary.copy(alpha = LEVEL_ALPHAS[(level - 1).coerceAtMost(LEVEL_ALPHAS.size - 1)])
}
