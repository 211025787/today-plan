package com.focuslist.app.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.Goal

/**
 * 统计页：今日数据、周/月/累计汇总、连续天数与本周柱状图。
 *
 * @param container 依赖容器
 * @param onBack 返回上一页回调（从"我的"进入时由导航栈处理）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit = {}) {
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.focusRepository, container.settingsStore)
    }
    val summary by viewModel.summary.collectAsState()
    val goal by viewModel.goalMinutes.collectAsState()
    val goalStreak by viewModel.goalStreak.collectAsState()

    // 每次进入页面刷新一次，保证刚完成的番茄被统计
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("专注统计", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 今日目标进度
            TodayGoalCard(summary = summary, goal = goal, streak = goalStreak)

            // 今日卡片行
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "今日专注", value = "${summary.todayMinutes}", unit = "分钟", modifier = Modifier.weight(1f))
                StatCard(title = "今日番茄", value = "${summary.todayPomos}", unit = "个", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "连续专注", value = "${summary.streakDays}", unit = "天", modifier = Modifier.weight(1f))
                StatCard(title = "累计专注", value = "${summary.totalMinutes}", unit = "分钟", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "本周", value = "${summary.weekMinutes}", unit = "分钟", modifier = Modifier.weight(1f))
                StatCard(title = "本月", value = "${summary.monthMinutes}", unit = "分钟", modifier = Modifier.weight(1f))
            }

            // 本周每日分布柱状图
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("本周每日分布", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    WeekBarChart(daily = summary.weekDaily, goal = goal)
                }
            }

            // 专注节律（一天里什么时候最专注）
            RhythmCard(summary = summary)

            // 年度总览
            YearOverviewCard(summary = summary)

            // 年度专注热力图
            YearHeatmapCard(summary = summary)
        }
    }
}

/**
 * 今日目标卡片：左侧圆环展示完成度，右侧展示进度文案与连续达标天数。
 *
 * @param summary 统计汇总
 * @param goal 每日目标分钟数
 * @param streak 连续达标天数
 */
@Composable
private fun TodayGoalCard(summary: StatsSummary, goal: Int, streak: Int) {
    val target = Goal.progress(summary.todayMinutes, goal)
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "goalProgress"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    if (animatedProgress > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = stroke
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${Goal.percent(summary.todayMinutes, goal)}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${summary.todayMinutes}/${goal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("今日目标", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Goal.statusText(summary.todayMinutes, goal),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "连续达标 $streak 天 · 本周达标 ${Goal.achievedDays(summary.weekDaily, goal)}/7 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 年度总览卡片：展示过去一年的累计、活跃天数、最佳单日与最长连续。
 *
 * @param summary 统计汇总
 */
@Composable
private fun YearOverviewCard(summary: StatsSummary) {
    val year = summary.year
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("年度总览", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "年度专注",
                    value = "${year.totalMinutes / 60}时${year.totalMinutes % 60}分",
                    unit = "",
                    modifier = Modifier.weight(1f)
                )
                StatCard(title = "活跃天数", value = "${year.activeDays}", unit = "天", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "最佳单日", value = "${year.bestDayMinutes}", unit = "分钟", modifier = Modifier.weight(1f))
                StatCard(title = "最长连续", value = "${year.longestStreak}", unit = "天", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "活跃日日均 ${year.avgMinutesPerActiveDay} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个统计卡片。
 *
 * @param title 标题
 * @param value 数值文本
 * @param unit 单位文案
 * @param modifier 布局修饰符
 */
@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Text(text = unit, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * 本周 7 天柱状图（Canvas 绘制）：达标日用实心色，未达标日淡化，并叠加每日目标参考线。
 *
 * @param daily 从周一开始的每日分钟数（长度 7）
 * @param goal 每日目标分钟数；小于等于 0 时不画参考线
 */
@Composable
private fun WeekBarChart(daily: List<Int>, goal: Int) {
    val barColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.bodySmall
    // 纵轴上限取「最高值」与「目标值」的较大者，保证目标线始终可见
    val maxValue = maxOf(daily.maxOrNull() ?: 0, goal).coerceAtLeast(1)
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val barWidth = size.width / (daily.size * 2)
            daily.forEachIndexed { index, value ->
                // 计算柱高（满高对应上限）
                val ratio = value.toFloat() / maxValue.toFloat()
                val barHeight = size.height * ratio
                val left = index * (size.width / daily.size) + barWidth / 2
                drawRect(
                    color = if (goal > 0 && value >= goal) barColor else barColor.copy(alpha = 0.32f),
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
            // 每日目标参考线（虚线）
            if (goal > 0 && goal <= maxValue) {
                val y = size.height * (1f - goal.toFloat() / maxValue.toFloat())
                drawLine(
                    color = goalLineColor.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            dayLabels.forEachIndexed { index, label ->
                Text(text = "$label\n${daily.getOrElse(index) { 0 }}m", style = labelStyle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (goal > 0) {
            Text(
                text = "--- 每日目标 $goal 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}