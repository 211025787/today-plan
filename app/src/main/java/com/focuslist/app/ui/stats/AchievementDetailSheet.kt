package com.focuslist.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.repo.ACHIEVEMENT_WINDOW_DAYS
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.AchievementForecasts
import com.focuslist.app.util.AchievementState
import com.focuslist.app.util.AchievementStats
import com.focuslist.app.util.Achievements
import com.focuslist.app.util.ForecastKind

/**
 * 单个徽章的详情弹层：点徽章卡打开。
 *
 * 内容分四段，都围绕一个问题——「这枚徽章怎么才能点亮」：
 * 1. 头图：图标 + 名称 + 稀有度 + 状态；
 * 2. 进度：已完成 / 目标，一条比例条；
 * 3. 预测：按近 14 天节奏估算还要多久；峰值型与连续型只给实话，不给假天数；
 * 4. 同类徽章：同一指标的其他档位，顺手看到下一档还差多少。
 *
 * @param state 当前成就状态（从列表里实时取，解锁后弹层会自动变亮）
 * @param siblings 同一指标的其他成就状态
 * @param recent 近 [windowDays] 天的指标快照；null 表示暂无数据
 * @param windowDays 预测窗口天数
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementDetailSheet(
    state: AchievementState,
    siblings: List<AchievementState>,
    recent: AchievementStats?,
    windowDays: Int = ACHIEVEMENT_WINDOW_DAYS,
    onDismiss: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val forecast = remember(state, recent, windowDays) {
        AchievementForecasts.of(state, recent, windowDays, now)
    }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(achievementTierColor(state.def.tier).copy(alpha = 0.16f))
                ) {
                    Text(text = state.def.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = state.def.title, style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = Achievements.tierText(state.def.tier),
                            style = MaterialTheme.typography.labelSmall,
                            color = achievementTierColor(state.def.tier)
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Text(
                            text = if (state.unlocked) "已点亮" else "进行中 ${state.percent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = state.def.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "进度", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = Achievements.progressText(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.unlocked) scheme.primary else scheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AchievementProgressBar(
                        fraction = state.percent / 100f,
                        color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = gapText(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = forecastTitle(forecast.kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = forecastColor(forecast.kind)
                )
                Text(
                    text = forecast.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = forecastColor(forecast.kind)
                )
                Text(
                    text = forecast.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

            Text(
                text = "口径：${AchievementForecasts.metricHint(state.def.metric)} · 按全量记录统计",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            if (siblings.isNotEmpty()) {
                Text(text = "同类徽章", style = MaterialTheme.typography.labelMedium)
                siblings.forEach { sibling ->
                    SiblingRow(state = sibling)
                }
            }
        }
    }
}

/**
 * 同类徽章的一行：图标 + 名称 + 进度 + 细进度条。
 *
 * @param state 另一档成就的状态
 */
@Composable
private fun SiblingRow(state: AchievementState) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(achievementTierColor(state.def.tier).copy(alpha = 0.12f))
        ) {
            Text(text = state.def.emoji, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.def.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(modifier = Modifier.padding(top = 5.dp)) {
                AchievementProgressBar(
                    fraction = state.percent / 100f,
                    color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (state.unlocked) "已点亮" else "${state.percent}%",
            style = MaterialTheme.typography.labelSmall,
            color = if (state.unlocked) scheme.primary else scheme.onSurfaceVariant
        )
    }
}

/**
 * 预测区块的小标题。
 *
 * @param kind 预测类型
 * @return 标题文案
 */
private fun forecastTitle(kind: ForecastKind): String {
    return when (kind) {
        ForecastKind.UNLOCKED -> "已达成"
        ForecastKind.READY -> "结算中"
        ForecastKind.PACE -> "预计"
        ForecastKind.STALLED -> "暂停中"
        ForecastKind.PEAK -> "怎么点亮"
        ForecastKind.STREAK -> "怎么点亮"
        ForecastKind.UNKNOWN -> "还差多少"
    }
}

/**
 * 预测区块的强调色：只有「按节奏推进」和「已点亮」用主色，
 * 其余用次要色 / 次要文字色，避免每张卡片都在抢注意力。
 *
 * @param kind 预测类型
 * @return 主题色
 */
@Composable
private fun forecastColor(kind: ForecastKind): Color {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        ForecastKind.UNLOCKED, ForecastKind.PACE -> scheme.primary
        ForecastKind.READY -> scheme.tertiary
        ForecastKind.PEAK, ForecastKind.STREAK -> scheme.secondary
        ForecastKind.STALLED, ForecastKind.UNKNOWN -> scheme.onSurfaceVariant
    }
}

/**
 * 进度条下方的说明：未点亮给「还差多少」，已点亮给「已达成」。
 *
 * @param state 成就状态
 * @return 一句说明
 */
private fun gapText(state: AchievementState): String {
    if (state.unlocked) {
        return "已经点亮，进度不会再回落"
    }
    val left = state.target - state.current
    if (left <= 0) {
        return "条件已满足，等待结算"
    }
    return Achievements.remainingText(state)
}
