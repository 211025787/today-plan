package com.focuslist.app.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.settings.SettingsDetailScreen
import com.focuslist.app.ui.settings.SettingsSectionLabel
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.GraduationPlan
import com.focuslist.app.util.PlanGraduations
import com.focuslist.app.util.PlanStats
import kotlinx.coroutines.launch

/**
 * 计划节奏页：把「计划时间轴」和「结业的计划」两张只读卡片放到计划维度下。
 *
 * 这两张卡原本埋在专注统计页里，和番茄数据混在一起；这里单独成页，
 * 统计对象从头到尾都是日历里的计划。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun PlanReviewScreen(container: AppContainer, onBack: () -> Unit) {
    // 借用计划总览的 ViewModel 拿一句当前计划投入的摘要，避免再开一条订阅
    val viewModel: PlanStatsViewModel = viewModel {
        PlanStatsViewModel(container.planRepository, container.settingsStore)
    }
    val summary by viewModel.summary.collectAsState()
    val allPlans by container.planRepository.observeAllPlans().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    // GraduationCard 在没有到期计划时会整张不渲染，标签得跟着一起藏，避免只剩一个空标题
    val hasEnded = remember(allPlans, today) {
        PlanGraduations.endedRecently(
            plans = allPlans.map {
                GraduationPlan(
                    id = it.id, title = it.title, icon = it.icon, colorHex = it.colorHex,
                    start = it.startDate, end = it.endDate, dailyGoalMinutes = it.dailyGoalMinutes
                )
            },
            today = today
        ).isNotEmpty()
    }
    var planTotals by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    LaunchedEffect(allPlans) {
        planTotals = container.planRepository.totalSecondsByPlan(
            start = 0L,
            end = DateUtils.startOfDay(System.currentTimeMillis())
        )
    }

    SettingsDetailScreen(
        title = "计划节奏",
        subtitle = "这 6 周的排布，以及到期计划的交代",
        onBack = onBack
    ) {
        Text(
            text = "当前累计投入 ${PlanStats.durationText(summary.totalMinutes)} · " +
                "活跃 ${summary.activeDays} 天（只算日历里的计划计时）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )

        SettingsSectionLabel("计划时间轴")
        PlanTimelineCard(plans = allPlans, modifier = Modifier.fillMaxWidth())

        if (hasEnded) {
                SettingsSectionLabel("结业的计划")
                GraduationCard(
                plans = allPlans,
                totals = planTotals,
                onArchive = { plan ->
                    scope.launch { container.planRepository.save(plan.copy(archived = true)) }
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
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "最近没有到期的计划。到期后会在这里给你一张成绩单，可以归档或再来一版。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
