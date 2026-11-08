package com.focuslist.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.repo.PlanRepository
import com.focuslist.app.ui.common.GlassCard
import com.focuslist.app.ui.theme.parseThemeColor
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.PaceStatus
import com.focuslist.app.util.PlanDayPoint
import com.focuslist.app.util.PlanInsight
import com.focuslist.app.util.PlanInsights
import com.focuslist.app.util.PlanPace
import com.focuslist.app.util.PlanPaces
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 计划有效期展示格式 */
private val sheetRangeFormat = SimpleDateFormat("M/d", Locale.CHINA)

/** 选中日期展示格式 */
private val sheetDayFormat = SimpleDateFormat("M月d日", Locale.CHINA)

/** 柱子下方只显示几号 */
private val sheetDayOfMonthFormat = SimpleDateFormat("d", Locale.CHINA)

/**
 * 统计弹层里的一个操作按钮。
 *
 * 原长按菜单的功能（编辑今日时长 / 存为模板 / 导出日历 / 删除计划）
 * 由调用方以这种描述传进来，弹层只负责统一排版，不碰具体业务状态。
 *
 * @property icon 图标
 * @property label 文案
 * @property destructive 是否危险操作（用 error 色）
 * @property onClick 点击回调
 */
data class PlanSheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 计划投入统计弹层：点计划卡即打开，看这个计划最近的投入节奏。
 *
 * 内容从上到下：
 * 1. 头图（图标 + 名称 + 有效期与每日目标）；
 * 2. 一排操作按钮（原来要长按卡片才出现的功能，现在点开统计就能用）；
 * 3. 整体节奏 / 今天的进度条（计划已结束时隐藏）；
 * 4. 连续达标 / 最长连续 / 达标率三个数字；
 * 5. 最近 14 天柱状图，点柱子看某天详情；
 * 6. 累计投入与活跃日日均。
 *
 * @param plan 计划实体
 * @param todaySeconds 今天的累计秒数（含未落库的运行段），由首页实时传入
 * @param repository 计划仓库（读逐日投入）
 * @param onDismiss 关闭回调
 * @param actions 统计页内的操作按钮（空列表则整块不显示）
 * @param hint 操作区下方的一行手势提示；null 则不显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanInsightSheet(
    plan: PlanEntity,
    todaySeconds: Long,
    repository: PlanRepository,
    onDismiss: () -> Unit,
    actions: List<PlanSheetAction> = emptyList(),
    hint: String? = null
) {
    val today = remember { DateUtils.startOfDay(System.currentTimeMillis()) }
    val planColor = parseThemeColor(plan.colorHex)
    var logs by remember(plan.id) { mutableStateOf<List<PlanLogEntity>>(emptyList()) }
    var selectedDate by remember(plan.id) { mutableStateOf<Long?>(null) }

    LaunchedEffect(plan.id) {
        // 取整个计划跨度：整体节奏要算总投入，14 天窗口只是其中一段
        logs = repository.logsOfPlan(plan.id, plan.startDate, today)
    }

    // 今天用调用方传入的实时秒数覆盖库中值（运行中的计时还没落库）
    val daily = remember(logs, plan.id, plan.startDate, plan.endDate, todaySeconds, today) {
        val map = logs.associate { it.date to it.accumulatedSec }.toMutableMap()
        if (today in plan.startDate..plan.endDate) {
            map[today] = todaySeconds.coerceAtLeast(0L)
        }
        map
    }
    val insight = remember(daily, plan.dailyGoalMinutes, plan.startDate, plan.endDate, today) {
        PlanInsights.of(
            planStart = plan.startDate,
            planEnd = plan.endDate,
            goalMinutes = plan.dailyGoalMinutes,
            dailySeconds = daily,
            today = today
        )
    }
    // 整体节奏：把「时间过了多少」与「目标完成多少」放在一起比
    val pace = remember(daily, plan.dailyGoalMinutes, plan.startDate, plan.endDate, today) {
        PlanPaces.of(
            planStart = plan.startDate,
            planEnd = plan.endDate,
            goalMinutes = plan.dailyGoalMinutes,
            totalSeconds = daily.values.sum(),
            today = today
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlanHeader(plan = plan, color = planColor)
            PlanActions(actions = actions, hint = hint)
            PaceBlock(pace = pace, color = planColor)
            insight.today?.let { point -> TodayProgress(point = point, insight = insight, color = planColor) }
            InsightCard(
                insight = insight,
                planColor = planColor,
                selectedDate = selectedDate,
                onSelectDay = { date -> selectedDate = if (selectedDate == date) null else date }
            )
            Text(
                text = "${insight.totalText} · ${insight.averageText}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 弹层头图：图标 + 名称 + 有效期与每日目标。
 *
 * @param plan 计划实体
 * @param color 计划主题色
 */
@Composable
private fun PlanHeader(plan: PlanEntity, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
        ) {
            Text(plan.icon, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.title.ifBlank { "未命名计划" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${sheetRangeFormat.format(Date(plan.startDate))} - " +
                    "${sheetRangeFormat.format(Date(plan.endDate))} · 每天 ${plan.dailyGoalMinutes} 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 统计弹层的操作区：把四个功能排成一行小按钮，放在头图正下方。
 *
 * 以前这些功能藏在「长按卡片」弹出的菜单里，用户得先知道有长按才行；
 * 现在单击卡片打开统计就能看到，长按只保留「抓起卡片拖动」这一件事。
 *
 * @param actions 操作列表；为空时整块不渲染
 * @param hint 下方的一行手势提示；null 不显示
 */
@Composable
private fun PlanActions(actions: List<PlanSheetAction>, hint: String?) {
    if (actions.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) scheme.error else scheme.primary
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(onClick = action.onClick)
                        .padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 整体进度：一条投入进度条 + 一个「时间进度」刻度 + 一句结论。
 *
 * 只画一条填充条（投入），再用一根竖线标出「按时间该到哪」——
 * 两条并排的条读起来要来回对照，一根刻度线更直接：柱子在线左边就是落后。
 *
 * @param pace 整体节奏
 * @param color 计划主题色
 */
@Composable
private fun PaceBlock(pace: PlanPace, color: Color) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (pace.status) {
        PaceStatus.AHEAD, PaceStatus.ON_TRACK, PaceStatus.FINISHED_DONE -> scheme.primary
        PaceStatus.SLIGHTLY_BEHIND -> scheme.secondary
        PaceStatus.BEHIND -> scheme.tertiary
        else -> scheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("整体进度", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = pace.progressText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(scheme.surfaceVariant)
        ) {
            // 投入进度
            if (pace.actualFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pace.actualFraction.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
            // 时间刻度：按时间该到的位置
            if (pace.timeFraction in 0.02f..0.98f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pace.timeFraction)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(scheme.onSurface.copy(alpha = 0.55f))
                    )
                }
            }
        }
        Text(
            text = pace.headline,
            style = MaterialTheme.typography.titleSmall,
            color = accent
        )
        Text(
            text = pace.detail,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}

/**
 * 今日进度：一行数字 + 一条比例条。
 *
 * @param point 今天的投入点
 * @param insight 计划画像（取今日文案）
 * @param color 计划主题色
 */
@Composable
private fun TodayProgress(point: PlanDayPoint, insight: PlanInsight, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今天",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${point.minutes} / ${point.goalMinutes} 分钟",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (point.barRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(point.barRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        }
        Text(
            text = insight.todayText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 统计卡：三个关键数字 + 最近窗口柱状图 + 逐日详情。
 *
 * @param insight 计划画像
 * @param planColor 计划主题色
 * @param selectedDate 当前选中的日期；null 表示未选中
 * @param onSelectDay 点击柱子回调
 */
@Composable
private fun InsightCard(
    insight: PlanInsight,
    planColor: Color,
    selectedDate: Long?,
    onSelectDay: (Long) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatCell(value = "${insight.streak} 天", label = "连续达标", color = planColor, modifier = Modifier.weight(1f))
                StatDivider()
                StatCell(value = "${insight.bestStreak} 天", label = "最长连续", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                StatDivider()
                StatCell(value = "${insight.ratePercent}%", label = "达标率", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (insight.isEmpty) "还没有可统计的日子" else "最近 ${insight.windowDays} 天",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "目标 ${insight.goalMinutes} 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (insight.isEmpty) {
                Text(
                    text = insight.todayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            } else {
                InsightBars(
                    insight = insight,
                    planColor = planColor,
                    selectedDate = selectedDate,
                    onSelectDay = onSelectDay
                )
                val selected = insight.days.firstOrNull { it.date == selectedDate }
                Text(
                    text = when {
                        selected != null ->
                            "${sheetDayFormat.format(Date(selected.date))} · ${selected.minutes} 分钟" +
                                if (selected.achieved) " · 达标" else ""
                        insight.today == null -> insight.todayText
                        else -> "点柱子查看某天详情"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/**
 * 最近窗口的柱状图：柱子按目标归一化，达标满格、未达标淡化。
 *
 * @param insight 计划画像
 * @param planColor 计划主题色
 * @param selectedDate 选中日期
 * @param onSelectDay 点击柱子回调
 */
@Composable
private fun InsightBars(
    insight: PlanInsight,
    planColor: Color,
    selectedDate: Long?,
    onSelectDay: (Long) -> Unit
) {
    val lastIndex = insight.days.lastIndex
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        // 目标参考线：柱子按目标归一化，顶线即「刚好达标」的高度
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            insight.days.forEachIndexed { index, point ->
                val selected = point.date == selectedDate
                val isToday = insight.activeToday && index == lastIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectDay(point.date) }
                ) {
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                    ) {
                        if (point.seconds > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.62f)
                                    .fillMaxHeight(point.barRatio.coerceAtLeast(0.06f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (point.achieved) planColor else planColor.copy(alpha = 0.4f))
                                    .then(
                                        if (selected) {
                                            Modifier.border(1.5.dp, planColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (index % 2 == 0 || isToday) {
                            sheetDayOfMonthFormat.format(Date(point.date))
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) planColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 单个统计数字（值 + 说明）。
 *
 * @param value 主数字
 * @param label 说明文案
 * @param color 数字颜色
 * @param modifier 布局修饰符
 */
@Composable
private fun StatCell(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 统计数字之间的细竖线分隔。
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
