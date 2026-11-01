package com.focuslist.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.util.ChallengeProgress

/**
 * 本周挑战卡：三条本周小目标 + 剩余天数。
 *
 * 与下面的长期徽章分工：徽章是「这几年攒下的」，挑战是「这周还差多少」。
 * 周一自动换成一批新目标，周日晚上自然收尾——不需要任何存储来重置。
 *
 * @param progress 三条挑战的完成情况
 * @param daysLeft 本周剩余天数（含今天）
 * @param summary 一句话汇总
 * @param onCompleted 已完成挑战 id 的回调（写入成就存储，幂等）
 * @param modifier 外部布局修饰
 */
@Composable
fun WeeklyChallengeCard(
    progress: List<ChallengeProgress>,
    daysLeft: Int,
    summary: String,
    onCompleted: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val completed = progress.filter { it.done }.map { it.challenge.id }

    // 挑战完成即写入成就存储：同一周重复进入页面不会重复记录
    LaunchedEffect(completed) {
        if (completed.isNotEmpty()) {
            onCompleted(completed)
        }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本周挑战", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "剩 $daysLeft 天",
                    style = MaterialTheme.typography.labelSmall,
                    // 最后两天提醒一下，但不用红色告警
                    color = if (daysLeft <= 2) scheme.tertiary else scheme.onSurfaceVariant
                )
            }

            progress.forEach { item ->
                ChallengeRow(item = item)
            }

            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单条挑战：图标 + 标题 + 进度条 + 数值。
 *
 * @param item 挑战完成情况
 */
@Composable
private fun ChallengeRow(item: ChallengeProgress) {
    val scheme = MaterialTheme.colorScheme
    val highlight = if (item.done) scheme.primary else scheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = item.challenge.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.challenge.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = highlight,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (item.done) "✓ 已完成" else item.progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.done) scheme.primary else scheme.onSurfaceVariant
                )
            }
            AchievementProgressBar(
                fraction = item.percent / 100f,
                color = if (item.done) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
            )
        }
    }
}
