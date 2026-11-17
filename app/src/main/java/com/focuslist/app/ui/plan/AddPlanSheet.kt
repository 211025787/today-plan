package com.focuslist.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.ui.common.DatePickerDialogWrapper
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.DayRule
import com.focuslist.app.util.PlanSchedule
import com.focuslist.app.util.PlanTemplate
import com.focuslist.app.util.PlanTemplates
import com.focuslist.app.util.WeekdayMask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 月日格式 */
private val dayFormat = SimpleDateFormat("M月d日", Locale.CHINA)

/** 预览里的短日期格式 */
private val shortDayFormat = SimpleDateFormat("M/d", Locale.CHINA)

/** 周几名称，按 Calendar.DAY_OF_WEEK - 1 取用 */
private val weekdayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

/**
 * 某天是周几。
 *
 * @param dayStart 当天 0 点时间戳
 * @return 「周一」…「周日」
 */
private fun weekdayName(dayStart: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = dayStart
    return weekdayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

/** 执行规则选项：每天 */
private const val MODE_EVERY = 0

/** 执行规则选项：自定义周几（多选） */
private const val MODE_PICK = 1

/** 执行规则选项：仅工作日（跳过法定节假日） */
private const val MODE_WORKDAY = 2

/** 执行规则选项：仅休息日（含法定节假日） */
private const val MODE_REST = 3

/**
 * 添加计划底部弹层：名称、图标、颜色、每日目标时长、生效日期范围、重复规则。
 *
 * 顶部是模板条：点一下把名称 / 图标 / 配色 / 时长 / 区间一次性填好；
 * 保存后这套配置会被自动记住，下次直接复用。
 *
 * 「重复」区决定这个计划落在范围里的哪些日子：每天、自选周几（可多选）、
 * 工作日（不含法定节假日）、休息日（含法定节假日）。选完会实时算出共几天、
 * 首次是哪天，并把日期横向列出来——所见即最终写进日历的那些天。
 *
 * @param templates 已保存的计划模板（按复用次数排序）
 * @param onDismiss 关闭回调
 * @param onSave 保存回调（产出新计划实体）
 * @param onRemember 记住本次配置的回调
 * @param onDeleteTemplate 删除模板回调（长按 chip）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPlanSheet(
    templates: List<PlanTemplate> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (PlanEntity) -> Unit,
    onRemember: (PlanTemplate) -> Unit = {},
    onDeleteTemplate: (PlanTemplate) -> Unit = {}
) {
    var title by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(PlanIconPool.first()) }
    var colorHex by remember { mutableStateOf(PlanColorPool.first()) }
    var goalMinutes by remember { mutableIntStateOf(30) }
    // 默认生效范围：今天起 30 天
    var startDate by remember { mutableStateOf(DateUtils.startOfDay(System.currentTimeMillis())) }
    var endDate by remember { mutableStateOf(DateUtils.startOfDay(System.currentTimeMillis()) + 30 * DateUtils.DAY_MILLIS) }
    // 重复规则：默认每天都做，与升级前的老计划一致
    var ruleMode by remember { mutableIntStateOf(MODE_EVERY) }
    var weekdaysMask by remember { mutableIntStateOf(WeekdayMask.NONE) }

    // 日期选择器目标：true 改开始，false 改结束
    var pickingStart by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 区间保证正序，用户把结束选到开始之前也能正常保存
    val rangeStart = minOf(startDate, endDate)
    val rangeEnd = maxOf(startDate, endDate)
    val effectiveRule = when (ruleMode) {
        MODE_WORKDAY -> DayRule.WORKDAY
        MODE_REST -> DayRule.REST_DAY
        else -> DayRule.EVERY_DAY
    }
    // 自定义周几但一个都没选时按「每天」处理，避免存出一个永远不生效的计划
    val effectiveMask = if (ruleMode == MODE_PICK && weekdaysMask != WeekdayMask.NONE) {
        weekdaysMask
    } else {
        WeekdayMask.ALL
    }
    val activeDays = remember(rangeStart, rangeEnd, effectiveMask, effectiveRule) {
        PlanSchedule.activeDays(rangeStart, rangeEnd, effectiveMask, effectiveRule, 60)
    }
    val activeCount = remember(rangeStart, rangeEnd, effectiveMask, effectiveRule) {
        PlanSchedule.countActive(rangeStart, rangeEnd, effectiveMask, effectiveRule)
    }

    val sheetState = rememberModalBottomSheetState(
        // 表单较高，直接完全展开，不要停在半屏还要再滑一下
        skipPartiallyExpanded = true
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("添加计划", style = MaterialTheme.typography.titleMedium)

            PlanTemplateBar(
                templates = templates,
                onPick = { template ->
                    title = template.title
                    icon = if (template.icon in PlanIconPool) template.icon else PlanIconPool.first()
                    colorHex = template.colorHex
                    goalMinutes = template.dailyMinutes
                    val (start, end) = PlanTemplates.range(template.durationDays, System.currentTimeMillis())
                    startDate = start
                    endDate = end
                },
                onDelete = onDeleteTemplate
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("计划名称，如：健身 / 学习英语") },
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
                valueRange = PlanTemplates.MIN_MINUTES.toFloat()..PlanTemplates.MAX_MINUTES.toFloat()
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

            // 重复规则：决定范围里哪些天真的要执行
            Text("重复", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleChip(
                    label = "每天",
                    selected = ruleMode == MODE_EVERY,
                    onClick = { ruleMode = MODE_EVERY }
                )
                RuleChip(
                    label = "自选周几",
                    selected = ruleMode == MODE_PICK,
                    onClick = {
                        ruleMode = MODE_PICK
                        // 首次切到自定义时给个合理默认：周一到周五
                        if (weekdaysMask == WeekdayMask.NONE) {
                            weekdaysMask = WeekdayMask.WEEKDAYS
                        }
                    }
                )
                RuleChip(
                    label = "工作日",
                    selected = ruleMode == MODE_WORKDAY,
                    onClick = { ruleMode = MODE_WORKDAY }
                )
                RuleChip(
                    label = "休息日",
                    selected = ruleMode == MODE_REST,
                    onClick = { ruleMode = MODE_REST }
                )
            }

            if (ruleMode == MODE_PICK) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    WeekdayMask.ORDER.forEachIndexed { index, bit ->
                        val on = (weekdaysMask and bit) != 0
                        Text(
                            text = WeekdayMask.LABELS[index],
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    weekdaysMask = if (on) {
                                        weekdaysMask and bit.inv()
                                    } else {
                                        weekdaysMask or bit
                                    }
                                }
                                .padding(vertical = 9.dp)
                        )
                    }
                }
                if (weekdaysMask == WeekdayMask.NONE) {
                    // 全都不选时按每天处理（与老计划一致），先把规则说清楚
                    Text(
                        text = "一个都不选时按「每天」执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 结果预览：共几天 + 具体落到哪些日期，避免「选了范围却不知道落在哪天」
            RulePreview(
                summary = "${PlanSchedule.describe(effectiveMask, effectiveRule)} · 共 $activeCount 天",
                days = activeDays,
                moreCount = (activeCount - activeDays.size).coerceAtLeast(0)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        onSave(
                            PlanEntity(
                                title = title.trim(),
                                icon = icon,
                                colorHex = colorHex,
                                dailyGoalMinutes = goalMinutes,
                                startDate = rangeStart,
                                endDate = rangeEnd,
                                weekdaysMask = effectiveMask,
                                dayRule = effectiveRule
                            )
                        )
                        // 自动沉淀为模板，下次一键复用
                        PlanTemplates.create(
                            title = title,
                            icon = icon,
                            dailyMinutes = goalMinutes,
                            durationDays = PlanTemplates.daysBetween(rangeStart, rangeEnd),
                            colorHex = colorHex
                        )?.let(onRemember)
                    },
                    enabled = title.isNotBlank() && activeCount > 0,
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
 * 重复规则的单个选项 chip。
 *
 * @param label 文案
 * @param selected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun RuleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/**
 * 规则结果预览：一行结论 + 横向滚动的日期列表。
 *
 * 日期只是一个可读性辅助，真正的落库依据是规则字段本身；
 * 所以这里最多列 60 个，剩下的用「还有 N 天」概括。
 *
 * @param summary 结论文案，如「每周一、三、五 · 共 12 天」
 * @param days 前若干个执行日（0 点时间戳）
 * @param moreCount 未列出的执行日数量
 */
@Composable
private fun RulePreview(summary: String, days: List<Long>, moreCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            if (days.isEmpty()) {
                Text(
                    text = "范围内没有符合的日期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "首次 ${dayFormat.format(Date(days.first()))} " + weekdayName(days.first()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (days.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                days.forEach { day ->
                    Text(
                        text = shortDayFormat.format(Date(day)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (moreCount > 0) {
                    Text(
                        text = "还有 $moreCount 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }
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
