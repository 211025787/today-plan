package com.focuslist.app.util

import java.util.Calendar

/**
 * 一条计划投入记录（与 Room 实体解耦，纯 JVM 可测）。
 *
 * @property planId 计划 ID
 * @property date 当天 0 点时间戳
 * @property seconds 该计划当天累计投入秒数
 */
data class PlanLogValue(val planId: Long, val date: Long, val seconds: Long)

/**
 * 计划的展示信息。
 *
 * @property id 计划 ID
 * @property title 标题
 * @property icon emoji 图标
 * @property colorHex 主题色
 */
data class PlanInfoValue(val id: Long, val title: String, val icon: String, val colorHex: String)

/**
 * 某一天全部计划的合计投入分钟数。
 *
 * @property date 当天 0 点时间戳
 * @property minutes 合计分钟数
 */
data class PlanDayValue(val date: Long, val minutes: Int)

/**
 * 单个计划的累计投入（用于排行）。
 *
 * @property planId 计划 ID
 * @property title 标题
 * @property icon emoji 图标
 * @property colorHex 主题色
 * @property minutes 区间内累计分钟数
 */
data class PlanInvestValue(
    val planId: Long,
    val title: String,
    val icon: String,
    val colorHex: String,
    val minutes: Int
)

/**
 * 日历计划的统计汇总：只统计日历里计划的计时，不含番茄专注记录。
 *
 * @property todayMinutes 今日合计分钟数
 * @property weekMinutes 本周（周一至今）合计分钟数
 * @property monthMinutes 本月（1 号至今）合计分钟数
 * @property totalMinutes 传入区间内的累计分钟数
 * @property activeDays 有投入的天数
 * @property goalDays 达到每日目标的天数
 * @property bestDayMinutes 单日最高分钟数
 * @property streakDays 连续达标天数（今天还没达标时从昨天起算）
 * @property days 最近 windowDays 天的逐日数据，按日期升序，缺失补 0
 * @property perPlan 每个计划的累计投入，按分钟数降序
 */
data class PlanStatsSummary(
    val todayMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val monthMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val activeDays: Int = 0,
    val goalDays: Int = 0,
    val bestDayMinutes: Int = 0,
    val streakDays: Int = 0,
    val days: List<PlanDayValue> = emptyList(),
    val perPlan: List<PlanInvestValue> = emptyList()
)

/**
 * 日历计划统计的纯计算工具。
 *
 * 和 FocusRepository 那套番茄统计完全分开：这里的数据源是 `plan_logs`，
 * 也就是日历格子 / 日卡片上那些进度条背后的数据。
 */
object PlanStats {

    /**
     * 汇总计划投入。
     *
     * 先按「天」和「计划」分别累加**秒**再换算成分钟，避免每个计划都不足 1 分钟时
     * 逐个取整把投入抹成 0。
     *
     * @param logs 区间内的计划投入记录
     * @param plans 全部计划（用于给排行补标题 / 图标 / 颜色）
     * @param today 今天 0 点
     * @param weekStart 本周一 0 点
     * @param monthStart 本月 1 号 0 点
     * @param goalMinutes 每日目标分钟数，<=0 时达标相关指标按活跃天数计
     * @param windowDays 逐日数据的窗口天数（含今天）
     * @return 统计汇总
     */
    fun of(
        logs: List<PlanLogValue>,
        plans: List<PlanInfoValue>,
        today: Long,
        weekStart: Long,
        monthStart: Long,
        goalMinutes: Int,
        windowDays: Int = 42
    ): PlanStatsSummary {
        val secondsByDate = HashMap<Long, Long>()
        val secondsByPlan = HashMap<Long, Long>()
        logs.forEach { log ->
            val sec = log.seconds.coerceAtLeast(0L)
            if (sec == 0L) return@forEach
            secondsByDate[log.date] = (secondsByDate[log.date] ?: 0L) + sec
            secondsByPlan[log.planId] = (secondsByPlan[log.planId] ?: 0L) + sec
        }
        val minutesOf: (Long) -> Int = { ((secondsByDate[it] ?: 0L) / 60L).toInt() }

        val days = (windowDays - 1 downTo 0).map { offset ->
            val date = DateUtils.addDays(today, -offset)
            PlanDayValue(date, minutesOf(date))
        }
        val effectiveGoal = goalMinutes.coerceAtLeast(0)
        val target = if (effectiveGoal > 0) effectiveGoal else 1
        val goalDays = secondsByDate.keys.count { minutesOf(it) >= target }
        val perPlan = plans.mapNotNull { info ->
            val sec = secondsByPlan[info.id] ?: return@mapNotNull null
            val min = (sec / 60L).toInt()
            if (min <= 0) {
                null
            } else {
                PlanInvestValue(info.id, info.title, info.icon, info.colorHex, min)
            }
        }.sortedWith(compareByDescending<PlanInvestValue> { it.minutes }.thenBy { it.planId })

        return PlanStatsSummary(
            todayMinutes = minutesOf(today),
            weekMinutes = secondsByDate.entries.filter { it.key in weekStart..today }
                .sumOf { (it.value / 60L).toInt() },
            monthMinutes = secondsByDate.entries.filter { it.key in monthStart..today }
                .sumOf { (it.value / 60L).toInt() },
            totalMinutes = (secondsByDate.values.sum() / 60L).toInt(),
            activeDays = secondsByDate.keys.count { minutesOf(it) > 0 },
            goalDays = goalDays,
            bestDayMinutes = secondsByDate.keys.maxOfOrNull { minutesOf(it) } ?: 0,
            streakDays = streakOf(minutesOf, today, target),
            days = days,
            perPlan = perPlan
        )
    }

    /**
     * 连续达标天数：今天还没达标不算断档（从昨天起算），与每日目标的口径一致。
     */
    private fun streakOf(minutesOf: (Long) -> Int, today: Long, target: Int): Int {
        var cursor = if (minutesOf(today) >= target) today else DateUtils.addDays(today, -1)
        var streak = 0
        // 上限防止脏数据（未来日期 / 异常时间戳）导致死循环
        while (minutesOf(cursor) >= target && streak < 400) {
            streak++
            cursor = DateUtils.addDays(cursor, -1)
        }
        return streak
    }

    /**
     * 把逐日数据排成**周一起始**的月历网格，前置空格用 null 补齐到整周。
     *
     * @param days 逐日数据
     * @param monthStart 该月 1 号 0 点
     * @return 网格单元；null 表示非本月的占位格
     */
    fun monthGrid(days: List<PlanDayValue>, monthStart: Long): List<PlanDayValue?> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = monthStart
        val leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 周一 = 0
        val dayCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val byDate = days.associateBy { it.date }
        val cells = mutableListOf<PlanDayValue?>()
        repeat(leading) { cells.add(null) }
        repeat(dayCount) { index ->
            val date = DateUtils.addDays(monthStart, index)
            cells.add(byDate[date] ?: PlanDayValue(date, 0))
        }
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /**
     * 当天投入对应的着色档位（0 = 没投入，4 = 达标或超额）。
     *
     * @param minutes 当天合计分钟数
     * @param goalMinutes 每日目标；<=0 时按绝对时长分档
     * @return 0..4
     */
    fun level(minutes: Int, goalMinutes: Int): Int {
        if (minutes <= 0) return 0
        if (goalMinutes <= 0) {
            return when {
                minutes < 15 -> 1
                minutes < 45 -> 2
                minutes < 90 -> 3
                else -> 4
            }
        }
        val ratio = minutes.toFloat() / goalMinutes.toFloat()
        return when {
            ratio < 0.34f -> 1
            ratio < 0.67f -> 2
            ratio < 1f -> 3
            else -> 4
        }
    }

    /**
     * 分钟数文案：`1 小时 20 分` / `2 小时` / `45 分` / `0 分`。
     *
     * @param minutes 分钟数
     * @return 中文时长文案
     */
    fun durationText(minutes: Int): String {
        val value = minutes.coerceAtLeast(0)
        val hours = value / 60
        val rest = value % 60
        return when {
            hours > 0 && rest > 0 -> "$hours 小时 $rest 分"
            hours > 0 -> "$hours 小时"
            else -> "$rest 分"
        }
    }
}
