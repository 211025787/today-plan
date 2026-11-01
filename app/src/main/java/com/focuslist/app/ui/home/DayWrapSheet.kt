package com.focuslist.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslist.app.util.DayWrap

/** 小结里最多列出几个计划，避免长计划列表把弹层撑爆 */
private const val MAX_ROWS = 5

/**
 * 收工弹层：今天做了什么、还差什么、要不要现在就停表。
 *
 * 存在的理由很实际——并行的计划秒表没有「全部暂停」的地方，
 * 忘记停表会把明天的数据一起算进去；顺手也给一天一个结束的动作。
 *
 * @param wrap 今日小结
 * @param dateLabel 日期文案，如「11 月 1 日」
 * @param onPauseAll 暂停全部计时回调
 * @param onWriteNote 去写今日备注回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayWrapSheet(
    wrap: DayWrap,
    dateLabel: String,
    onPauseAll: () -> Unit,
    onWriteNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今天收工", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Text(
                text = wrap.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = if (wrap.allAchieved) scheme.primary else scheme.onSurface
            )
            Text(
                text = wrap.detail,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )

            if (wrap.rows.isNotEmpty()) {
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
                wrap.rows.take(MAX_ROWS).forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(text = row.icon, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (row.goalMinutes > 0) {
                                "${row.minutes} / ${row.goalMinutes} 分钟"
                            } else {
                                "${row.minutes} 分钟"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.achieved) scheme.primary else scheme.onSurfaceVariant
                        )
                        if (row.running) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "计时中", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
                        }
                    }
                }
                if (wrap.rows.size > MAX_ROWS) {
                    Text(
                        text = "另有 ${wrap.rows.size - MAX_ROWS} 个计划未列出",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = wrap.closing,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wrap.runningCount > 0) {
                    SheetAction(
                        text = "暂停全部 ${wrap.runningCount} 个计时",
                        filled = true,
                        onClick = onPauseAll
                    )
                }
                SheetAction(
                    text = "写一句今天",
                    filled = false,
                    onClick = onWriteNote
                )
            }
        }
    }
}

/**
 * 弹层底部动作：主操作填充主色，次操作只描边框。
 *
 * @param text 文案
 * @param filled 是否填充主色
 * @param onClick 点击回调
 */
@Composable
private fun SheetAction(text: String, filled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) scheme.primary else scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) scheme.onPrimary else scheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
