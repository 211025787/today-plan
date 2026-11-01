package com.focuslist.app.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.data.repo.achievementStatsOf
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.AchievementForecasts
import com.focuslist.app.util.AchievementState
import com.focuslist.app.util.AchievementStats
import com.focuslist.app.util.Achievements
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.Goal
import com.focuslist.app.util.GraduationPlan
import com.focuslist.app.util.Heatmap
import com.focuslist.app.util.PlanGraduations
import com.focuslist.app.util.MonthKey
import com.focuslist.app.util.MonthReview
import com.focuslist.app.util.WeekTrend
import com.focuslist.app.util.WeeklyReview
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 统计页：今日数据、周/月/累计汇总、连续天数与本周柱状图。
 *
 * @param container 依赖容器
 * @param onBack 返回上一页回调（从"我的"进入时由导航栈处理）
 * @param onOpenAchievements 打开成就殿堂回调
 * @param onOpenMonths 打开月度回顾回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    container: AppContainer,
    onBack: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenMonths: () -> Unit = {}
) {
    val viewModel: StatsViewModel = viewModel {
        StatsViewModel(container.focusRepository, container.settingsStore)
    }
    val summary by viewModel.summary.collectAsState()
    val goal by viewModel.goalMinutes.collectAsState()
    val goalStreak by viewModel.goalStreak.collectAsState()
    val unlocked by viewModel.achievements.collectAsState()
    val recentStats by viewModel.recentStats.collectAsState()
    // 计划时间轴用的计划列表：直接订阅仓库，避免为一张只读卡片扩 ViewModel 构造
    val allPlans by container.planRepository.observeAllPlans().collectAsState(initial = emptyList())
    // 结业卡需要每个计划的累计投入：一次分组查询，随计划列表变化重算
    val scope = rememberCoroutineScope()
    var planTotals by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(allPlans) {
        planTotals = container.planRepository.totalSecondsByPlan(
            start = 0L,
            end = DateUtils.startOfDay(System.currentTimeMillis())
        )
    }

    // 每次进入页面刷新一次，保证刚完成的番茄被统计
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // 成就快照：只在汇总或解锁记录变化时重算
    val achievementStates = remember(summary, unlocked) {
        Achievements.snapshot(achievementStatsOf(summary), unlocked)
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
                actions = {
                    IconButton(onClick = onOpenAchievements) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "成就殿堂",
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

            // 本周回顾：与上周同期对比 + 按当前节奏推算
            WeekReviewCard(summary = summary, goal = goal)

            // 月度回顾入口：整月逐日日历 + 与上月同期对比
            MonthReviewEntryCard(summary = summary, goal = goal, onClick = onOpenMonths)

            // 计划时间轴：未来 6 周的计划安排，看有没有堆在一起
            PlanTimelineCard(plans = allPlans)

            // 专注节律（一天里什么时候最专注）
            RhythmCard(summary = summary)

            // 年度总览
            YearOverviewCard(summary = summary)

            // 年度专注热力图
            YearHeatmapCard(summary = summary)

            // 成就殿堂入口
            AchievementsEntryCard(
                states = achievementStates,
                recentStats = recentStats,
                onClick = onOpenAchievements
            )

            // 结业的计划：到期后给它一个交代（归档 / 再来一版）
            GraduationCard(
                plans = allPlans,
                totals = planTotals,
                onArchive = { plan ->
                    scope.launch {
                        container.planRepository.save(plan.copy(archived = true))
                    }
                },
                onRenew = { plan ->
                    scope.launch {
                        // 旧的一版归档，新的从今天重新起算，避免结业列表里一直挂着它
                        container.planRepository.save(plan.copy(archived = true))
                        val renewed = PlanGraduations.renewed(
                            plan = GraduationPlan(
                                id = plan.id,
                                title = plan.title,
                                icon = plan.icon,
                                colorHex = plan.colorHex,
                                start = plan.startDate,
                                end = plan.endDate,
                                dailyGoalMinutes = plan.dailyGoalMinutes
                            ),
                            today = DateUtils.startOfDay(System.currentTimeMillis())
                        )
                        container.planRepository.save(
                            plan.copy(
                                id = 0L,
                                startDate = renewed.start,
                                endDate = renewed.end,
                                archived = false,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            )
        }
    }
}

/**
 * 成就殿堂入口卡：展示已解锁徽章与一句激励，点击进入完整徽章墙。
 *
 * 激励文案带上「按最近节奏还要多久」的估算，让人在统计页就知道下一枚徽章有多近。
 * 未解锁徽章为空时不重复同一句话，只留一行。
 *
 * @param states 全部成就状态
 * @param recentStats 近 14 天指标快照，用于估算点亮时间
 * @param onClick 点击回调
 */
@Composable
private fun AchievementsEntryCard(
    states: List<AchievementState>,
    recentStats: AchievementStats?,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("成就殿堂", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${Achievements.countText(states)} ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val unlockedList = states.filter { it.unlocked }
            if (unlockedList.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    unlockedList.take(8).forEach { state ->
                        Text(text = state.def.emoji, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text(
                text = remember(states, recentStats) {
                    val next = Achievements.nextUp(states, 1).firstOrNull()
                    if (next == null) {
                        Achievements.motivationText(states)
                    } else {
                        AchievementForecasts.nextLine(next, AchievementForecasts.of(next, recentStats))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

/**
 * 本周回顾卡片：本周同期时长、与上周同期的对比条、达标 / 最佳 / 日均指标与整周推算。
 *
 * "同期"只统计已经过去的天数，避免周一早上拿 1 天去比上周整周。
 *
 * @param summary 统计汇总
 * @param goal 每日目标分钟数
 */
@Composable
private fun WeekReviewCard(summary: StatsSummary, goal: Int) {
    // 今天是本周第几天（周一 = 0）：复用热力图区间末日，避免再算一次日期
    val todayIndex = remember(summary.heatRangeEnd) { Heatmap.rowOf(summary.heatRangeEnd) }
    val review = remember(summary.weekDaily, summary.lastWeekDaily, todayIndex, goal) {
        WeeklyReview.of(summary.weekDaily, summary.lastWeekDaily, todayIndex, goal)
    }
    val maxMinutes = maxOf(review.thisMinutes, review.lastMinutes, 1)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本周回顾", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                TrendBadge(text = WeeklyReview.badgeText(review), trend = review.trend)
            }
            if (review.isEmpty) {
                Text(
                    text = "本周还没有专注记录，完成第一个番茄后这里会出现与上周同期的对比",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${review.thisMinutes}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Text(
                    text = "${WeeklyReview.spanText(review)} · ${WeeklyReview.deltaText(review)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                CompareBar(label = "上周同期", minutes = review.lastMinutes, max = maxMinutes, dim = true)
                CompareBar(label = "本周", minutes = review.thisMinutes, max = maxMinutes, dim = false)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = WeeklyReview.metricText(review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = WeeklyReview.projectionText(review),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 月度回顾入口卡：本月累计、日均与环比一眼可见，点进去看整月逐日日历。
 *
 * 数据直接复用热力图的按天映射（滚动一年，够覆盖本月与上月），不额外查库。
 *
 * @param summary 统计汇总
 * @param goal 每日目标分钟数
 * @param onClick 点击回调
 */
@Composable
private fun MonthReviewEntryCard(summary: StatsSummary, goal: Int, onClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val month = remember(today) { MonthKey.of(today) }
    val byDay = remember(summary.heatByDay) { MonthReview.keyedByDay(summary.heatByDay) }
    val report = remember(month, byDay, goal) { MonthReview.build(month, byDay, goal, today) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("月度回顾", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${report.month.label} ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MonthReview.durationText(report.totalMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  ·  ${MonthReview.averageText(report)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = "${MonthReview.spanText(report)} · ${MonthReview.deltaText(report)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 趋势徽标：上涨用主题色，下降与持平均用次要文字色，避免把「少专注」渲染成告警。
 *
 * @param text 徽标文案
 * @param trend 趋势方向
 */
@Composable
private fun TrendBadge(text: String, trend: WeekTrend) {
    val color = if (trend == WeekTrend.UP) {
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

/**
 * 单行对比条：左侧标签、中间按最大值归一化的条形、右侧分钟数。
 *
 * @param label 左侧标签
 * @param minutes 该行分钟数
 * @param max 两行共用的最大值，保证条形长度可比
 * @param dim 是否为对照行（上周同期）
 */
@Composable
private fun CompareBar(label: String, minutes: Int, max: Int, dim: Boolean) {
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
            modifier = Modifier.width(56.dp)
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
            modifier = Modifier.width(38.dp)
        )
    }
}