package com.focuslist.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.QuickPlanDraft
import com.focuslist.app.util.QuickPlanParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一句话示例：点击即可填入输入框 */
private val QUICK_SAMPLES = listOf(
    "每天读书 30 分钟 📚 到年底",
    "11月1日-11月30日 每天健身 45 分钟 💪",
    "冥想 10 分钟 🧘 坚持 21 天"
)

/** 月日格式 */
private val quickDayFormat = SimpleDateFormat("M月d日", Locale.CHINA)

/**
 * 一句话建计划弹层：一行输入 + 实时预览，回车/保存直接落库。
 *
 * 交互约定：输入即时解析（名称 / 图标 / 每日时长 / 起止日期），
 * 预览区所见即所得，标题为空时不可保存。
 *
 * @param onDismiss 关闭回调
 * @param onSave 保存回调（产出新计划实体）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPlanSheet(onDismiss: () -> Unit, onSave: (PlanEntity) -> Unit) {
    var input by remember { mutableStateOf("") }
    val today = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val draft = remember(input) { QuickPlanParser.parse(input, today) }
    val colorHex = remember(draft.title) {
        PlanColorPool[QuickPlanParser.colorIndex(draft.title, PlanColorPool.size)]
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("一句话建计划", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("如：每天读书 30 分钟 📚 到年底") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (draft.hasTitle()) onSave(draft.toPlan(colorHex)) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 实时预览：所见即所得
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(parseThemeColor(colorHex).copy(alpha = 0.18f))
                    ) {
                        Text(draft.icon, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (draft.hasTitle()) draft.title else "还没写计划名称",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (draft.hasTitle()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                        Text(
                            text = "每天 ${draft.dailyGoalMinutes} 分钟 · " +
                                "${quickDayFormat.format(Date(draft.startDate))} - " +
                                "${quickDayFormat.format(Date(draft.endDate))} · " +
                                "共 ${draft.days} 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Text("试试这些", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_SAMPLES.forEach { sample ->
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { input = sample }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Text(
                text = "支持：30 分钟 / 1小时30分 / 今天起 / 到年底 / 11月1日-11月30日 / 坚持 21 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSave(draft.toPlan(colorHex)) },
                    enabled = draft.hasTitle(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}

/**
 * 解析结果落成计划实体。
 *
 * @receiver 解析出的草稿
 * @param colorHex 主题色
 * @return 可直接入库的计划实体
 */
private fun QuickPlanDraft.toPlan(colorHex: String): PlanEntity {
    return PlanEntity(
        title = title,
        icon = icon,
        colorHex = colorHex,
        dailyGoalMinutes = dailyGoalMinutes,
        startDate = startDate,
        endDate = endDate
    )
}
