package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.Graduation
import com.focuslist.app.util.GraduationPlan
import com.focuslist.app.util.PlanGraduations

/** 一张卡里最多列几个已结业的计划 */
private const val MAX_ROWS = 3

/**
 * 结业卡：最近 30 天结束的计划，给个交代。
 *
 * 之前计划到期后是**一声不响消失**的——首页看不到、也没有任何提示，
 * 用户很容易以为数据丢了。这里补上收尾：一张成绩单 + 两个选择。
 *
 * 没有已结束的计划时整张卡不渲染，统计页保持干净。
 *
 * @param plans 全部未归档计划
 * @param totals 每个计划的累计投入秒数（`planId -> seconds`）
 * @param onArchive 归档回调
 * @param onRenew 再来一版回调
 * @param modifier 外部布局修饰
 */
@Composable
fun GraduationCard(
    plans: List<PlanEntity>,
    totals: Map<Long, Long>,
    onArchive: (PlanEntity) -> Unit,
    onRenew: (PlanEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val ended = remember(plans, today) {
        PlanGraduations.endedRecently(
            plans = plans.map { it.toGraduationPlan() },
            today = today
        )
    }
    if (ended.isEmpty()) {
        return
    }
    val reports = remember(ended, totals) {
        ended.take(MAX_ROWS).map { plan ->
            PlanGraduations.report(plan, totals[plan.id] ?: 0L)
        }
    }
    val scheme = MaterialTheme.colorScheme

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("结业的计划", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "最近 30 天 · ${ended.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Text(
                text = "到期了，给它一个交代：留档，或者从今天再来一版",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )

            reports.forEachIndexed { index, report ->
                if (index > 0) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                }
                GraduationRow(
                    report = report,
                    onArchive = { plans.firstOrNull { it.id == report.plan.id }?.let(onArchive) },
                    onRenew = { plans.firstOrNull { it.id == report.plan.id }?.let(onRenew) }
                )
            }

            if (ended.size > MAX_ROWS) {
                Text(
                    text = "另有 ${ended.size - MAX_ROWS} 个已结束的计划未列出",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 一行结业成绩：图标 + 名称 + 评语 + 两个动作。
 *
 * @param report 成绩单
 * @param onArchive 归档回调
 * @param onRenew 再来一版回调
 */
@Composable
private fun GraduationRow(
    report: Graduation,
    onArchive: () -> Unit,
    onRenew: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val color = parseThemeColor(report.plan.colorHex)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(text = report.plan.icon, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = report.plan.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = report.verdict,
                style = MaterialTheme.typography.labelSmall,
                color = if (report.completionPercent >= 100) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = report.detail,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        MiniAction(text = "再来一版", filled = true, onClick = onRenew)
        Spacer(modifier = Modifier.width(6.dp))
        MiniAction(text = "归档", filled = false, onClick = onArchive)
    }
}

/**
 * 小动作胶囊：主操作填充计划色，次操作灰底。
 *
 * @param text 文案
 * @param filled 是否填充
 * @param onClick 点击回调
 */
@Composable
private fun MiniAction(text: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) scheme.onPrimary else scheme.onSurface
        )
    }
}

/**
 * 实体 → 结业计算用的纯数据。
 *
 * @return 计划信息
 */
private fun PlanEntity.toGraduationPlan(): GraduationPlan {
    return GraduationPlan(
        id = id,
        title = title,
        icon = icon,
        colorHex = colorHex,
        start = startDate,
        end = endDate,
        dailyGoalMinutes = dailyGoalMinutes
    )
}
