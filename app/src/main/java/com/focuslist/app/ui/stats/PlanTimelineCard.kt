package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.PlanSpan
import com.focuslist.app.util.PlanTimeline
import com.focuslist.app.util.TimelineBar
import com.focuslist.app.util.TimelineWindow

/** 左侧计划名称列宽度 */
private val LABEL_WIDTH = 96.dp

/** 单行高度 */
private val ROW_HEIGHT = 26.dp

/** 最多画几行，超出折叠成一行说明，避免计划多时把统计页撑长 */
private const val MAX_ROWS = 6

/**
 * 计划时间轴卡：以「周」为单位把未来 6 周的计划画成泳道条带。
 *
 * 应用里其它图表都在看「已经发生的事」，这张看的是**安排**——
 * 这段时间有几个计划在跑、有没有堆在一起、空档在哪。
 *
 * 每个计划独占一行（窄屏上比甘特的行堆叠更易读），条带按周取整；
 * 今天所在的那一列有淡色底，点任意一行看它的具体区间。
 *
 * @param plans 全部未归档计划
 * @param modifier 外部布局修饰
 */
@Composable
fun PlanTimelineCard(plans: List<PlanEntity>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val today = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val window = remember(today) { PlanTimeline.window(today) }
    val spans = remember(plans) {
        plans.map {
            PlanSpan(
                id = it.id,
                title = it.title,
                icon = it.icon,
                colorHex = it.colorHex,
                start = it.startDate,
                end = it.endDate
            )
        }
    }
    val bars = remember(spans, window) { PlanTimeline.layout(spans, window) }
    val todayCol = remember(window, today) { PlanTimeline.todayColumn(window, today) }
    var pickedId by remember { mutableStateOf<Long?>(null) }
    val picked = bars.firstOrNull { it.plan.id == pickedId }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("计划时间轴", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = PlanTimeline.rangeText(window),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }

            if (bars.isEmpty()) {
                Text(
                    text = "这 6 周里没有生效中的计划，安排一个看看",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                return@Column
            }

            WeekHeader(window = window, todayCol = todayCol)

            bars.take(MAX_ROWS).forEach { bar ->
                TimelineRow(
                    bar = bar,
                    window = window,
                    todayCol = todayCol,
                    selected = bar.plan.id == pickedId,
                    onClick = { pickedId = if (pickedId == bar.plan.id) null else bar.plan.id }
                )
            }

            if (bars.size > MAX_ROWS) {
                Text(
                    text = "另有 ${bars.size - MAX_ROWS} 个计划未列出",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }

            val rows = PlanTimeline.rowCount(bars)
            Text(
                text = buildString {
                    append("${bars.size} 个计划")
                    append(" · 最多同时 $rows 个")
                    if (rows >= 3) {
                        append(" · 排得有点满")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (rows >= 3) scheme.tertiary else scheme.onSurfaceVariant
            )

            picked?.let { bar ->
                val plan = plans.firstOrNull { it.id == bar.plan.id }
                Text(
                    text = "${bar.plan.title} · ${PlanTimeline.barRangeText(window, bar)}" +
                        if (plan != null) " · 每天 ${plan.dailyGoalMinutes} 分钟" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary
                )
            }
        }
    }
}

/**
 * 周刻度表头：6 个周一日期，今天所在列用主色。
 *
 * @param window 时间轴窗口
 * @param todayCol 今天所在列，-1 表示不在窗口内
 */
@Composable
private fun WeekHeader(window: TimelineWindow, todayCol: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(LABEL_WIDTH))
        window.columnStarts().forEachIndexed { index, day ->
            Text(
                text = PlanTimeline.label(day),
                style = MaterialTheme.typography.labelSmall,
                color = if (index == todayCol) scheme.primary else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个计划的一行：左侧名称 + 右侧 6 周泳道。
 *
 * @param bar 布局结果
 * @param window 时间轴窗口
 * @param todayCol 今天所在列
 * @param selected 是否被点选
 * @param onClick 点击回调
 */
@Composable
private fun TimelineRow(
    bar: TimelineBar,
    window: TimelineWindow,
    todayCol: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val color = parseThemeColor(bar.plan.colorHex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(LABEL_WIDTH).padding(end = 6.dp)
        ) {
            Text(text = bar.plan.icon, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = bar.plan.title,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(ROW_HEIGHT)
        ) {
            val cellWidth = maxWidth / window.weeks
            // 泳道底
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.06f))
            )
            // 今天所在列的淡色底
            if (todayCol >= 0) {
                Box(
                    modifier = Modifier
                        .offset(x = cellWidth * todayCol)
                        .width(cellWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(scheme.primary.copy(alpha = 0.07f))
                )
            }
            // 计划条带
            Box(
                modifier = Modifier
                    .offset(x = cellWidth * bar.startCol + 2.dp)
                    .width((cellWidth * bar.spanCols - 4.dp).coerceAtLeast(4.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) color else color.copy(alpha = 0.72f))
            )
        }
    }
}
