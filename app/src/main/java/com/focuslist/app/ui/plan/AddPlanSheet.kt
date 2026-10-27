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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.DatePickerDialogWrapper
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 月日格式 */
private val dayFormat = SimpleDateFormat("M月d日", Locale.getDefault())

/**
 * 添加计划底部弹层：名称、图标、颜色、每日目标时长、生效日期范围。
 *
 * @param onDismiss 关闭回调
 * @param onSave 保存回调（产出新计划实体）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlanSheet(onDismiss: () -> Unit, onSave: (PlanEntity) -> Unit) {
    var title by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(PlanIconPool.first()) }
    var colorHex by remember { mutableStateOf(PlanColorPool.first()) }
    var goalMinutes by remember { mutableIntStateOf(30) }
    // 默认生效范围：今天起 30 天
    var startDate by remember { mutableStateOf(DateUtils.startOfDay(System.currentTimeMillis())) }
    var endDate by remember { mutableStateOf(DateUtils.startOfDay(System.currentTimeMillis()) + 30 * DateUtils.DAY_MILLIS) }

    // 日期选择器目标：true 改开始，false 改结束
    var pickingStart by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("添加计划", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("计划名称，如：健身 / 学习 Java") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 图标选择
            Text("图标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlanIconPool.forEach { emoji ->
                    Text(
                        text = emoji,
                        style = if (emoji == icon) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (emoji == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .clickable { icon = emoji }
                            .padding(4.dp)
                    )
                }
            }

            // 颜色选择
            Text("颜色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanColorPool.forEach { hex ->
                    val color = parseThemeColor(hex)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { colorHex = hex }
                    ) {
                        if (colorHex == hex) {
                            Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 每日目标时长
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每日目标时长", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("$goalMinutes 分钟", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = goalMinutes.toFloat(),
                onValueChange = { goalMinutes = (it / 5f).roundToInt() * 5 },
                valueRange = 5f..180f
            )

            // 生效日期范围
            Text("执行日期范围", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateChip(label = "开始：${dayFormat.format(Date(startDate))}") {
                    pickingStart = true
                    showDatePicker = true
                }
                DateChip(label = "结束：${dayFormat.format(Date(endDate))}") {
                    pickingStart = false
                    showDatePicker = true
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        // 保证开始不晚于结束
                        val (start, end) = if (startDate <= endDate) startDate to endDate else endDate to startDate
                        onSave(
                            PlanEntity(
                                title = title.trim(),
                                icon = icon,
                                colorHex = colorHex,
                                dailyGoalMinutes = goalMinutes,
                                startDate = start,
                                endDate = end
                            )
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialogWrapper(
            initialMillis = if (pickingStart) startDate else endDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                val day = DateUtils.startOfDay(picked)
                if (pickingStart) {
                    startDate = day
                } else {
                    endDate = day
                }
                showDatePicker = false
            }
        )
    }
}

/**
 * 日期选择 chip 按钮。
 *
 * @param label 文案
 * @param onClick 点击回调
 */
@Composable
private fun DateChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}