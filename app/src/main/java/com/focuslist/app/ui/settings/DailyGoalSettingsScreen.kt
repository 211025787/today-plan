package com.focuslist.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslist.app.AppContainer
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.Goal

/**
 * 每日目标设置页：预设、步进微调，以及按近两周表现给出的建议值。
 *
 * @param container 依赖容器
 * @param onBack 返回回调
 */
@Composable
fun DailyGoalSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.settingsStore, container.focusRepository)
    }
    val settings by viewModel.settings.collectAsState()
    val suggestedGoal by viewModel.suggestedGoal.collectAsState()

    SettingsDetailScreen(
        title = "每日目标",
        subtitle = "决定日历格里的完成率与达标统计",
        onBack = onBack
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("每天想专注多久", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        text = "${settings.dailyGoalMinutes}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "分钟 / 天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    StepButton(
                        label = "−",
                        enabled = settings.dailyGoalMinutes > Goal.MIN_MINUTES
                    ) { viewModel.saveDailyGoal(settings.dailyGoalMinutes - Goal.STEP_MINUTES) }
                    Spacer(modifier = Modifier.width(10.dp))
                    StepButton(
                        label = "+",
                        enabled = settings.dailyGoalMinutes < Goal.MAX_MINUTES
                    ) { viewModel.saveDailyGoal(settings.dailyGoalMinutes + Goal.STEP_MINUTES) }
                }
                // 预设：常见的几档，点一下就到
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Goal.PRESETS.forEach { minutes ->
                        FilterChip(
                            selected = settings.dailyGoalMinutes == minutes,
                            onClick = { viewModel.saveDailyGoal(minutes) },
                            label = { Text("$minutes") }
                        )
                    }
                }
            }
        }

        // 按最近两周的实际表现给一个建议值
        suggestedGoal?.let { suggestion ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("按你的近两周表现", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (suggestion == settings.dailyGoalMinutes) {
                            "现在设的 $suggestion 分钟和你最近的节奏正好合得上。"
                        } else {
                            "最近两周的日均接近 ${suggestion} 分钟，把目标调到这个数更容易连续达标。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (suggestion != settings.dailyGoalMinutes) {
                        TextButton(
                            onClick = { viewModel.saveDailyGoal(suggestion) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("采用 $suggestion 分钟")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 步进按钮：圆形底 + 加减号。
 *
 * @param label 显示字符（+ / −）
 * @param enabled 是否可点（到达上下限时置灰）
 * @param onClick 点击回调
 */
@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
    }
}
