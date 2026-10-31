package com.focuslist.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.data.repo.StatsSummary
import com.focuslist.app.ui.common.GlassCard

/**
 * 统计页：今日数据、周/月/累计汇总、连续天数与本周柱状图。
 *
 * @param container 依赖容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer) {
    val viewModel: StatsViewModel = viewModel { StatsViewModel(container.focusRepository) }
    val summary by viewModel.summary.collectAsState()

    // 每次进入页面刷新一次，保证刚完成的番茄被统计
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("专注统计", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    WeekBarChart(daily = summary.weekDaily)
                }
            }

            // 年度总览
            YearOverviewCard(summary = summary)

            // 年度专注热力图
            YearHeatmapCard(summary = summary)
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
 * 本周 7 天柱状图（Canvas 绘制）。
 *
 * @param daily 从周一开始的每日分钟数（长度 7）
 */
@Composable
private fun WeekBarChart(daily: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.bodySmall
    val maxValue = (daily.maxOrNull() ?: 0).coerceAtLeast(1)
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val barWidth = size.width / (daily.size * 2)
            daily.forEachIndexed { index, value ->
                // 计算柱高（满高对应最大值）
                val ratio = value.toFloat() / maxValue.toFloat()
                val barHeight = size.height * ratio
                val left = index * (size.width / daily.size) + barWidth / 2
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            dayLabels.forEachIndexed { index, label ->
                Text(text = "$label\n${daily.getOrElse(index) { 0 }}m", style = labelStyle, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}