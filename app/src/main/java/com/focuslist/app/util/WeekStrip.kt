package com.focuslist.app.util

import java.util.Calendar

/**
 * 周条上的一天。
 *
 * @property start 当天 0 点时间戳
 * @property dayOfMonth 几号（1-31）
 * @property weekdayLabel 星期文案（一…日）
 * @property isToday 是否今天
 * @property isFuture 是否还没到（含明天及以后），用于把「本周达标」的分母收窄到已过的天
 * @property minutes 当天累计分钟数
 * @property goalMinutes 当天生效计划的每日目标之和，0 表示当天没有计划
 * @property level 完成度档位 0-3，见 [WeekStrip.level]
 */
data class WeekDay(
    val start: Long,
    val dayOfMonth: Int,
    val weekdayLabel: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val minutes: Int,
    val goalMinutes: Int,
    val level: Int
)

/**
 * 周条：把「选中日所在周」摊成周一到周日 7 个格子。
 *
 * 放在当日卡片顶部，用来在不返回月历的情况下横向翻日子——
 * 也顺带把「这周练得怎么样」浓缩成 7 个圆点。
 *
 * 设计约定：
 * - **周一为一周起点**，与月历、热力图、周回顾完全一致；
 * - 完成度分四档，**先看目标再看绝对分钟**：没有目标的日子只区分「有没有动过」，
 *   避免让没设计划的日子看起来像失败；
 * - 文案只报**已达标天数**与本周总时长，不做百分比考核。
 */
object WeekStrip {

    /** 没动过 */
    const val LEVEL_EMPTY = 0

    /** 动过，但不到目标一半 */
    const val LEVEL_STARTED = 1

    /** 过半但未达标 */
    const val LEVEL_HALF = 2

    /** 达标 */
    const val LEVEL_DONE = 3

    /** 一周天数 */
    const val DAYS = 7

    /** 星期文案，索引 0 为周一 */
    val LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 生成某天所在周的 7 天数据。
     *
     * @param anchor 作为锚点的任意一天（0 点或当天任意时刻均可）
     * @param today 今天 0 点时间戳，用于标记 [WeekDay.isToday]
     * @param minutesOf 取某天累计分钟数
     * @param goalOf 取某天所有生效计划的每日目标之和
     * @return 周一到周日共 7 条
     */
    fun of(
        anchor: Long,
        today: Long,
        minutesOf: (Long) -> Int,
        goalOf: (Long) -> Int
    ): List<WeekDay> {
        val weekStart = DateUtils.startOfWeek(anchor)
        val todayStart = DateUtils.startOfDay(today)
        return (0 until DAYS).map { index ->
            val day = DateUtils.addDays(weekStart, index)
            val minutes = minutesOf(day)
            val goal = goalOf(day)
            WeekDay(
                start = day,
                dayOfMonth = dayOfMonth(day),
                weekdayLabel = LABELS[index],
                isToday = day == todayStart,
                isFuture = day > todayStart,
                minutes = minutes,
                goalMinutes = goal,
                level = level(minutes, goal)
            )
        }
    }

    /**
     * 完成度档位。
     *
     * 有目标时按比例分档（<50% / <100% / 达标）；没有目标时只区分动过没动过，
     * 这样「今天没设计划但练了 10 分钟」也有一个不刺眼的浅色提示。
     *
     * @param minutes 当天分钟数
     * @param goalMinutes 当天目标分钟数，非正数表示没有计划
     * @return 0-3
     */
    fun level(minutes: Int, goalMinutes: Int): Int {
        if (minutes <= 0) {
            return LEVEL_EMPTY
        }
        if (goalMinutes <= 0) {
            return LEVEL_STARTED
        }
        return when {
            minutes >= goalMinutes -> LEVEL_DONE
            minutes * 2 >= goalMinutes -> LEVEL_HALF
            else -> LEVEL_STARTED
        }
    }

    /**
     * 本周小结文案。
     *
     * 分母是**已经过去的天数**（含今天）而不是固定 7：
     * 周一就说「0/7 天达标」只会让人泄气，说「0/1」才是实话。
     * 整周都在未来时换成「已安排 N 天」，不做未开始就先判失败的表述。
     *
     * @param days 一周 7 天
     * @return 一行文案
     */
    fun summary(days: List<WeekDay>): String {
        if (days.isEmpty()) {
            return ""
        }
        val planned = days.count { it.goalMinutes > 0 }
        val elapsed = days.count { !it.isFuture }
        if (elapsed <= 0) {
            return if (planned > 0) {
                "这一周还没有开始，已安排 $planned 天"
            } else {
                "这一周还没有安排计划"
            }
        }
        if (planned == 0) {
            return "本周还没有计划，安排一个吧"
        }
        val totalMinutes = days.sumOf { it.minutes }
        if (totalMinutes <= 0) {
            return "本周还没有投入，从今天开始"
        }
        val done = days.count { it.level == LEVEL_DONE }
        return "本周 $done/$elapsed 天达标 · ${durationText(totalMinutes)}"
    }

    /**
     * 前后翻周。
     *
     * @param anchor 当前锚点（任意一天）
     * @param weeks 偏移周数，负数为上一周
     * @return 偏移后的同一天
     */
    fun shift(anchor: Long, weeks: Int): Long {
        return DateUtils.addDays(anchor, weeks * DAYS)
    }

    /**
     * 分钟数的中文时长文案，复用计划统计里同一套写法，避免两处措辞不一致。
     *
     * @param minutes 分钟数
     * @return 如「45 分钟」「2 小时」「1 小时 20 分」
     */
    fun durationText(minutes: Int): String {
        return PlanInsights.durationText(minutes.toLong() * 60L)
    }

    /**
     * 某天是几号。
     *
     * @param dayStart 当天 0 点（或当天任意时刻）
     * @return 1-31
     */
    fun dayOfMonth(dayStart: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        return cal.get(Calendar.DAY_OF_MONTH)
    }
}
