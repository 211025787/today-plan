package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.repo.PlanRepository
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.settings.SettingsActionRow
import com.focuslist.app.ui.settings.SettingsDetailScreen
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.Goal
import com.focuslist.app.util.PlanDayValue
import com.focuslist.app.util.PlanInfoValue
import com.focuslist.app.util.PlanInvestValue
import com.focuslist.app.util.PlanLogValue
import com.focuslist.app.util.PlanStats
import com.focuslist.app.util.PlanStatsSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 日历计划统计页的数据源：只读 `plan_logs`，与番茄专注记录完全分开。
 *
 * @property planRepository 计划仓库
 */
class PlanStatsViewModel(
    private val planRepository: PlanRepository,
    settingsStore: com.focuslist.app.data.prefs.SettingsStore
) : ViewModel() {

    /** 今天 0 点：进页面时取一次，跨天重新进入即刷新 */
    val today: Long = DateUtils.startOfDay(System.currentTimeMillis())
    private val weekStart = DateUtils.startOfWeek(today)
    /** 本月 1 号 0 点，月历网格用 */
    val monthStart: Long = DateUtils.startOfMonth(today)
    private val windowStart = DateUtils.addDays(today, -400)

    /** 每日目标（分钟）：决定日历格着色档位与达标天数 */
    val goalMinutes: StateFlow<Int> = settingsStore.settings
        .map { it.dailyGoalMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Goal.DEFAULT_MINUTES)

    /** 汇总：一次订阅计划日志 + 全部计划，逐条映射成纯数据类后交给 [PlanStats] */
    val summary: StateFlow<PlanStatsSummary> = combine(
        planRepository.observeLogsBetween(windowStart, today),
        planRepository.observeAllPlans(),
        goalMinutes
    ) { logs, plans, goal ->
        PlanStats.of(
            logs = logs.map { PlanLogValue(it.planId, it.date, it.accumulatedSec) },
            plans = plans.map { PlanInfoValue(it.id, it.title, it.icon, it.colorHex) },
            today = today,
            weekStart = weekStart,
            monthStart = monthStart,
            goalMinutes = goal,
            windowDays = 42
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlanStatsSummary())
}

/**
 * 计划总览页：日 / 周 / 月 / 累计投入、本月计划日历、近 14 天柱状图、各计划投入排行。
 *
 * 统计口径是「日历里的计划计时」——也就是日历格子和日卡片进度条背后的数据，
 * 不含番茄专注记录；专注相关的统计在「专注（番茄）」分组里。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 * @param onOpenReview 打开「计划节奏」（时间轴 / 结业的计划）回调
 */
@Composable
fun PlanStatsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenReview: () -> Unit = {}
) {
    val viewModel: PlanStatsViewModel = viewModel {
        PlanStatsViewModel(container.planRepository, container.settingsStore)
    }
    val summary by viewModel.summary.collectAsState()
    val goal by viewModel.goalMinutes.collectAsState()
    val scheme = MaterialTheme.colorScheme

    SettingsDetailScreen(
        title = "计划总览",
        subtitle = "只统计日历里的计划投入",
        onBack = onBack
    ) {
        if (summary.totalMinutes <= 0) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("还没有计划投入记录", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "在日历里选中某一天，点计划卡右上角的计时按钮开始投入；" +
                            "这里会按每天的合计时长汇总，番茄专注不计入。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // 四个总量指标
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("投入总览", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    MetricCell("今日", summary.todayMinutes, Modifier.weight(1f))
                    MetricCell("本周", summary.weekMinutes, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    MetricCell("本月", summary.monthMinutes, Modifier.weight(1f))
                    MetricCell("累计", summary.totalMinutes, Modifier.weight(1f))
                }
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                Text(
                    text = "活跃 ${summary.activeDays} 天 · 达标 ${summary.goalDays} 天 · " +
                        "最长连续 ${summary.streakDays} 天 · 最佳单日 ${PlanStats.durationText(summary.bestDayMinutes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        // 本月日历：按当天全部计划的合计投入着色
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val monthLabel = remember(viewModel.monthStart) {
                    SimpleDateFormat("yyyy 年 M 月", Locale.CHINA).format(java.util.Date(viewModel.monthStart))
                }
                Text(monthLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "颜色越深，那天投在计划上的时间越多（对照每日目标 $goal 分钟）",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                val cells = remember(summary.days, viewModel.monthStart) {
                    PlanStats.monthGrid(summary.days, viewModel.monthStart)
                }
                val dayFormat = remember { SimpleDateFormat("d", Locale.CHINA) }
                cells.chunked(7).forEach { week ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        week.forEach { cell ->
                            DayCell(cell, viewModel.today, goal, dayFormat, Modifier.weight(1f))
                        }
                    }
                }
                // 图例
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("少", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    repeat(5) { level ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(levelColor(level, scheme.primary))
                        )
                    }
                    Text("多", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }

        // 近 14 天柱状图
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("近 14 天", style = MaterialTheme.typography.titleSmall)
                val recent = summary.days.takeLast(14)
                val maxMinutes = recent.maxOfOrNull { it.minutes }?.coerceAtLeast(goal) ?: goal
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp)
                ) {
                    recent.forEach { day ->
                        val ratio = if (maxMinutes <= 0) 0f else day.minutes.toFloat() / maxMinutes.toFloat()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(ratio.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (day.minutes >= goal && goal > 0) scheme.primary else scheme.primary.copy(alpha = 0.45f)
                                )
                        )
                    }
                }
                Text(
                    text = "实心柱 = 当天达到每日目标",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // 各计划投入排行
        if (summary.perPlan.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("各计划投入", style = MaterialTheme.typography.titleSmall)
                    val max = summary.perPlan.first().minutes.coerceAtLeast(1)
                    summary.perPlan.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = scheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        PlanInvestRow(item, max)
                    }
                }
            }
        }

        // 次级入口：设置页不再放统计分组，时间轴与结业从这里进，避免变成孤岛
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingsActionRow(
                    title = "计划节奏",
                    subtitle = "6 周时间轴、到期计划的结业与续期",
                    onClick = onOpenReview
                )
            }
        }
    }
}

/** 总量指标格 */
@Composable
private fun MetricCell(label: String, minutes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = PlanStats.durationText(minutes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** 月历里的单格 */
@Composable
private fun DayCell(
    cell: PlanDayValue?,
    today: Long,
    goal: Int,
    dayFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (cell == null) Color.Transparent else levelColor(PlanStats.level(cell.minutes, goal), scheme.primary))
            .then(
                if (cell?.date == today) {
                    Modifier.border(1.dp, scheme.primary, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (cell != null) {
            Text(
                text = dayFormat.format(java.util.Date(cell.date)),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface
            )
        }
    }
}

/** 单条计划投入排行 */
@Composable
private fun PlanInvestRow(item: PlanInvestValue, max: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(item.icon, style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((item.minutes.toFloat() / max.toFloat()).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(parseThemeColor(item.colorHex))
                )
            }
        }
        Text(
            text = PlanStats.durationText(item.minutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 0..4 档颜色：浅到深用主色透明度递进，空档是中性灰 */
@Composable
private fun levelColor(level: Int, primary: Color): Color {
    if (level <= 0) return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    return primary.copy(alpha = 0.18f + 0.18f * level)
}
