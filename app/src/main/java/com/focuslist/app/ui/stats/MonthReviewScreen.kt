package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.MonthDay
import com.focuslist.app.util.MonthReport
import com.focuslist.app.util.MonthReview
import com.focuslist.app.util.MonthTrend

/**
 * 月度回顾页：整月逐日日历 + 与上月同期的对比。
 *
 * 交互只有一处：左右翻月、点某天看当天分钟数，其余信息一眼可见。
 *
 * @param container 依赖容器
 * @param onBack 返回上一页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthReviewScreen(
    container: AppContainer,
    onBack: () -> Unit = {}
) {
    val viewModel: MonthReviewViewModel = viewModel {
        MonthReviewViewModel(container.focusRepository, container.settingsStore)
    }
    val state by viewModel.state.collectAsState()
    val report = state.report

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("月度回顾", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonthSwitcher(
                label = report?.month?.label ?: "",
                isCurrent = report?.isCurrent != false,
                canPrev = state.canPrev,
                canNext = state.canNext,
                onPrev = viewModel::selectPrev,
                onNext = viewModel::selectNext,
                onSelectCurrent = viewModel::selectCurrent
            )
            if (report != null) {
                MonthSummaryCard(report = report)
                MonthCalendarCard(report = report)
            }
        }
    }
}

/**
 * 月份切换条：左右箭头翻月，中间点一下回到本月。
 *
 * @param label 月份文案
 * @param isCurrent 是否是本月
 * @param canPrev 是否可以往前翻
 * @param canNext 是否可以往后翻
 * @param onPrev 往前翻回调
 * @param onNext 往后翻回调
 * @param onSelectCurrent 回到本月回调
 */
@Composable
private fun MonthSwitcher(
    label: String,
    isCurrent: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectCurrent: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = canPrev) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "上一个月",
                    tint = if (canPrev) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isCurrent, onClick = onSelectCurrent)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (isCurrent) "本月" else "回到本月",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "下一个月",
                    tint = if (canNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

/**
 * 月度汇总卡：总时长 + 趋势徽标 + 与上月同期的双条对比 + 指标与整月推算。
 *
 * @param report 月度快照
 */
@Composable
private fun MonthSummaryCard(report: MonthReport) {
    val maxMinutes = maxOf(report.totalMinutes, report.prevMinutes, 1)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (report.isCurrent) "本月专注" else "整月专注",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                TrendBadge(text = MonthReview.badgeText(report), trend = report.trend)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MonthReview.durationText(report.totalMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  ·  ${MonthReview.averageText(report)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Text(
                text = "${MonthReview.spanText(report)} · ${MonthReview.deltaText(report)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))
            MonthCompareBar(label = "上月同期", minutes = report.prevMinutes, max = maxMinutes, dim = true)
            MonthCompareBar(label = "本月", minutes = report.totalMinutes, max = maxMinutes, dim = false)

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = MonthReview.metricText(report),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MonthReview.projectionText(report).takeIf { it.isNotEmpty() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = MonthReview.summaryText(report),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 月度日历卡：7 列网格按专注时长着色，今天描边，未来日期压暗；点某天看当天详情。
 *
 * @param report 月度快照
 */
@Composable
private fun MonthCalendarCard(report: MonthReport) {
    var selectedDay by remember(report.month) { mutableStateOf<Int?>(null) }
    val cells = remember(report) {
        buildList<MonthDay?> {
            repeat(report.month.firstWeekday) { add(null) }
            report.days.forEach { add(it) }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                MonthReview.WEEKDAY_LABELS.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            MonthDayCell(
                                day = day,
                                selected = selectedDay == day.day,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedDay = if (selectedDay == day.day) null else day.day
                                }
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            MonthLegend()
            val picked = report.days.firstOrNull { it.day == selectedDay }
            Text(
                text = if (picked == null) {
                    "点按任意日期，查看当天的专注时长"
                } else {
                    val base = "${report.month.month}月${picked.day}日 · ${MonthReview.durationText(picked.minutes)}"
                    when {
                        picked.achieved -> "$base · 已达标"
                        report.goal > 0 && picked.hasRecord -> "$base · 目标 ${report.goal} 分钟"
                        else -> base
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (picked == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * 单个日期格：背景色按等级加深，今天用主题色描边，选中用主文字色描边。
 *
 * @param day 当天数据
 * @param selected 是否被选中
 * @param modifier 布局修饰符（外部传入 weight）
 * @param onClick 点击回调
 */
@Composable
private fun MonthDayCell(
    day: MonthDay,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = when {
        day.isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        day.level == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> primary.copy(alpha = 0.16f + 0.17f * day.level)
    }
    val contentColor = when {
        day.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        day.level >= 4 -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        day.isToday -> primary
        else -> Color.Transparent
    }
    val borderWidth = if (selected) 1.8.dp else 1.2.dp
    val shape = RoundedCornerShape(9.dp)

    Box(modifier = modifier.aspectRatio(1f).padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background)
                .border(width = borderWidth, color = borderColor, shape = shape)
                .clickable(enabled = !day.isFuture, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${day.day}",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * 图例：少 → 多 的五档色块。
 */
@Composable
private fun MonthLegend() {
    val primary = MaterialTheme.colorScheme.primary
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        primary.copy(alpha = 0.33f),
        primary.copy(alpha = 0.50f),
        primary.copy(alpha = 0.67f),
        primary.copy(alpha = 0.84f)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 单行对比条：上月同期与本月共用同一最大值，长度可直接比较。
 *
 * @param label 左侧标签
 * @param minutes 分钟数
 * @param max 两行共用的最大值
 * @param dim 是否为对照行
 */
@Composable
private fun MonthCompareBar(label: String, minutes: Int, max: Int, dim: Boolean) {
    val barColor = if (dim) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val fraction = if (max <= 0) 0f else (minutes.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(trackColor)
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }
        }
        Text(
            text = "$minutes",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}

/**
 * 趋势徽标：上涨用主题色，下降与持平均用次要文字色，不把「少专注」渲染成告警。
 *
 * @param text 徽标文案
 * @param trend 趋势方向
 */
@Composable
private fun TrendBadge(text: String, trend: MonthTrend) {
    val color = if (trend == MonthTrend.UP) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
