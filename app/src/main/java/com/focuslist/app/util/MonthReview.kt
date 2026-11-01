package com.focuslist.app.util

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 月度趋势方向。
 */
enum class MonthTrend {
    /** 比上月同期更专注 */
    UP,

    /** 比上月同期更少 */
    DOWN,

    /** 与上月同期持平 */
    FLAT
}

/**
 * 月份键：只描述「哪一年哪一月」，不携带任何时区信息。
 *
 * 用 [java.time.YearMonth] 做加减与天数计算，跨年、闰年都交给 JDK，
 * 避免自己写 `month - 1 == 0` 之类的分支出错。
 *
 * @property year 年份，例如 2026
 * @property month 月份，1~12
 */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {

    /** 存库 / 传参用的紧凑键，例如 `2026-11` */
    val key: String get() = String.format(Locale.ROOT, "%04d-%02d", year, month)

    /** 完整展示文案，例如 `2026年11月` */
    val label: String get() = "${year}年${month}月"

    /** 紧凑展示文案，例如 `11月` */
    val shortLabel: String get() = "${month}月"

    /** 当月天数（28~31） */
    val length: Int get() = yearMonth().lengthOfMonth()

    /** 1 号是本周第几天（周一 = 0，周日 = 6），用于日历网格的起始留白 */
    val firstWeekday: Int get() = LocalDate.of(year, month, 1).dayOfWeek.value - 1

    /** 转为 YearMonth，供 JDK 处理越界加减 */
    private fun yearMonth(): YearMonth = YearMonth.of(year, month)

    /**
     * 生成当月某一天的日期串。
     *
     * @param day 当月第几天，1~[length]
     * @return 格式 `yyyy-MM-dd`
     */
    fun dayKey(day: Int): String {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
    }

    /** 上一个月 */
    fun prev(): MonthKey = plusMonths(-1)

    /** 下一个月 */
    fun next(): MonthKey = plusMonths(1)

    /**
     * 偏移若干个月，正数向后、负数向前，自动跨年。
     *
     * @param count 偏移月数
     * @return 偏移后的月份
     */
    fun plusMonths(count: Int): MonthKey {
        val shifted = yearMonth().plusMonths(count.toLong())
        return MonthKey(shifted.year, shifted.monthValue)
    }

    /** 相对本月向后回溯若干个月（等价于 [plusMonths] 传负数） */
    fun minusMonths(count: Int): MonthKey = plusMonths(-count)

    /** 该月 1 号的本地日期 */
    fun firstDate(): LocalDate = LocalDate.of(year, month, 1)

    override fun compareTo(other: MonthKey): Int {
        val byYear = year.compareTo(other.year)
        return if (byYear != 0) byYear else month.compareTo(other.month)
    }

    companion object {

        /**
         * 从年、月构造，月份越界会被规整（例如 month = 13 → 次年 1 月）。
         *
         * @param year 年份
         * @param month 月份，允许越界
         * @return 月份键
         */
        fun of(year: Int, month: Int): MonthKey {
            // 先按「年 * 12 + 月」线性化再取整，month 越界（如 13、0）也能正确进位
            val linear = year * 12 + (month - 1)
            val normalizedYear = Math.floorDiv(linear, 12)
            val normalizedMonth = Math.floorMod(linear, 12) + 1
            return MonthKey(normalizedYear, normalizedMonth)
        }

        /**
         * 取指定日期所在月份。
         *
         * @param date 本地日期
         * @return 月份键
         */
        fun of(date: LocalDate): MonthKey = MonthKey(date.year, date.monthValue)

        /**
         * 取指定日期串（`yyyy-MM-dd`）所在月份。
         *
         * @param dayKey 日期串
         * @return 月份键；格式非法返回 null
         */
        fun ofDayKey(dayKey: String): MonthKey? {
            val date = MonthReview.parseDate(dayKey) ?: return null
            return of(date)
        }

        /**
         * 解析 `yyyy-MM` 形式的月份键。
         *
         * @param key 月份键
         * @return 月份键；格式非法返回 null
         */
        fun parse(key: String): MonthKey? {
            val parts = key.split('-')
            if (parts.size != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            if (month !in 1..12) return null
            return MonthKey(year, month)
        }
    }
}

/**
 * 月度日历里的一天。
 *
 * @property day 当月第几天
 * @property minutes 当天专注分钟数
 * @property level 着色等级 0~4，0 表示没有记录
 * @property achieved 是否达到当日目标
 * @property isToday 是否是今天
 * @property isFuture 是否属于「还没到」的日子
 */
data class MonthDay(
    val day: Int,
    val minutes: Int = 0,
    val level: Int = 0,
    val achieved: Boolean = false,
    val isToday: Boolean = false,
    val isFuture: Boolean = false
) {

    /** 是否有专注记录 */
    val hasRecord: Boolean get() = minutes > 0
}

/**
 * 月度回顾快照：整月逐日数据 + 与**上月同期**的对比结果。
 *
 * 「同期」的口径与周回顾一致：只统计已经过去的日子。本月 17 号就只和上月 1~17 号比，
 * 否则月初永远会得出「暴跌 90%」的假结论。
 *
 * @property month 所属月份
 * @property days 整月逐日数据（含未来日，UI 直接按格子渲染）
 * @property elapsedDays 已经过去的天数：当月为今天几号，过去月为整月天数
 * @property totalMinutes 已过天数的累计专注分钟
 * @property prevMinutes 上月同期累计专注分钟
 * @property activeDays 已过天数里有记录的天数
 * @property achievedDays 已过天数里达标的天数
 * @property bestDay 已过天数里的最佳单日，没有记录时为 null
 * @property goal 每日目标分钟数，非正数表示未设置
 * @property isCurrent 是否是本月
 */
data class MonthReport(
    val month: MonthKey,
    val days: List<MonthDay> = emptyList(),
    val elapsedDays: Int = 0,
    val totalMinutes: Int = 0,
    val prevMinutes: Int = 0,
    val activeDays: Int = 0,
    val achievedDays: Int = 0,
    val bestDay: MonthDay? = null,
    val goal: Int = 0,
    val isCurrent: Boolean = false
) {

    /** 与上月同期的分钟差，正数表示更多 */
    val delta: Int get() = totalMinutes - prevMinutes

    /** 已过天数里完全没有记录 */
    val isEmpty: Boolean get() = totalMinutes <= 0

    /** 趋势方向 */
    val trend: MonthTrend
        get() = when {
            delta > 0 -> MonthTrend.UP
            delta < 0 -> MonthTrend.DOWN
            else -> MonthTrend.FLAT
        }

    /** 本月剩余天数 */
    val remainingDays: Int get() = (month.length - elapsedDays).coerceAtLeast(0)

    /**
     * 按已过天数均摊的日均分钟数。
     *
     * 用「已过天数」而不是「活跃天数」，否则一个月只练一天也会显示日均 180 分钟。
     */
    val dailyAverage: Int
        get() = if (elapsedDays <= 0) 0 else (totalMinutes.toFloat() / elapsedDays).roundToInt()

    /** 按当前节奏推算的整月分钟数；本月已结束或未开始时等于实际值 */
    val projectedMinutes: Int
        get() = if (isCurrent && elapsedDays in 1 until month.length) {
            (totalMinutes.toFloat() / elapsedDays * month.length).roundToInt()
        } else {
            totalMinutes
        }

    /** 已过天数里最长的连续专注天数 */
    val longestStreak: Int
        get() {
            var best = 0
            var current = 0
            for (index in 0 until elapsedDays.coerceAtMost(days.size)) {
                if (days[index].hasRecord) {
                    current++
                    if (current > best) best = current
                } else {
                    current = 0
                }
            }
            return best
        }
}

/**
 * 月度回顾纯计算工具：不依赖 Android / Room，可在 JVM 单元测试里直接跑。
 *
 * 输入只有「日期串 → 分钟数」的扁平映射，输出是可直接渲染的整月快照，
 * 因此 UI 层不需要再做任何日期运算。
 */
object MonthReview {

    /** 日历表头：周一到周日 */
    val WEEKDAY_LABELS: List<String> = listOf("一", "二", "三", "四", "五", "六", "日")

    /** 着色等级的最大值 */
    const val MAX_LEVEL: Int = 4

    /**
     * 把 `yyyy-MM-dd` 解析成本地日期。
     *
     * @param dayKey 日期串
     * @return 日期；格式非法返回 null
     */
    fun parseDate(dayKey: String): LocalDate? {
        return try {
            LocalDate.parse(dayKey)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把「本地 0 点时间戳 → 分钟数」换算成「日期串 → 分钟数」。
     *
     * 热力图数据用时间戳做键，月度回顾用日期串做键，这里统一一次即可。
     *
     * @param byDay 时间戳映射
     * @return 日期串映射
     */
    fun keyedByDay(byDay: Map<Long, Int>): Map<String, Int> {
        if (byDay.isEmpty()) return emptyMap()
        return byDay.entries.associate { DayKeys.format(it.key) to it.value }
    }

    /**
     * 计算某天的着色等级：有目标时按目标占比，无目标时按绝对分钟数。
     *
     * @param minutes 当天分钟数
     * @param goal 每日目标，非正数表示未设置
     * @return 0~4
     */
    fun level(minutes: Int, goal: Int): Int {
        if (minutes <= 0) return 0
        if (goal > 0) {
            val ratio = minutes.toFloat() / goal.toFloat()
            return when {
                ratio >= 1f -> 4
                ratio >= 0.67f -> 3
                ratio >= 0.34f -> 2
                else -> 1
            }
        }
        return when {
            minutes >= 60 -> 4
            minutes >= 30 -> 3
            minutes >= 15 -> 2
            else -> 1
        }
    }

    /**
     * 构造某个月的回顾快照。
     *
     * @param month 目标月份
     * @param dayMinutes 日期串（`yyyy-MM-dd`）到分钟数的映射，缺失视为 0
     * @param goal 每日目标分钟数
     * @param today 今天（用于判定同日、未来日与已过天数）
     * @return 月度回顾快照
     */
    fun build(
        month: MonthKey,
        dayMinutes: Map<String, Int>,
        goal: Int,
        today: LocalDate = LocalDate.now()
    ): MonthReport {
        val todayMonth = MonthKey.of(today)
        val isCurrent = month == todayMonth
        val isFuture = month > todayMonth
        val elapsed = when {
            isFuture -> 0
            isCurrent -> today.dayOfMonth.coerceIn(0, month.length)
            else -> month.length
        }

        val days = (1..month.length).map { day ->
            val date = LocalDate.of(month.year, month.month, day)
            val minutes = if (day <= elapsed) (dayMinutes[month.dayKey(day)] ?: 0) else 0
            MonthDay(
                day = day,
                minutes = minutes,
                level = level(minutes, goal),
                achieved = goal > 0 && minutes >= goal,
                isToday = isCurrent && day == today.dayOfMonth,
                isFuture = date.isAfter(today)
            )
        }

        val elapsedDays = days.take(elapsed)
        val total = elapsedDays.sumOf { it.minutes }
        val prev = month.prev()
        val prevElapsed = elapsed.coerceAtMost(prev.length)
        val prevMinutes = if (prevElapsed <= 0) {
            0
        } else {
            (1..prevElapsed).sumOf { day -> dayMinutes[prev.dayKey(day)] ?: 0 }
        }

        return MonthReport(
            month = month,
            days = days,
            elapsedDays = elapsed,
            totalMinutes = total,
            prevMinutes = prevMinutes,
            activeDays = elapsedDays.count { it.hasRecord },
            achievedDays = elapsedDays.count { it.achieved },
            bestDay = elapsedDays.filter { it.hasRecord }.maxByOrNull { it.minutes },
            goal = goal,
            isCurrent = isCurrent
        )
    }

    /**
     * 两个月份之间的连续月份列表（含两端、升序）。
     *
     * @param from 起始月
     * @param to 结束月
     * @return 月份列表；起点晚于终点时为空
     */
    fun monthsBetween(from: MonthKey, to: MonthKey): List<MonthKey> {
        if (from > to) return emptyList()
        val result = mutableListOf<MonthKey>()
        var cursor = from
        while (cursor <= to) {
            result += cursor
            cursor = cursor.next()
        }
        return result
    }

    /**
     * 把分钟数写成「x 小时 y 分」这种口语文案。
     *
     * @param minutes 分钟数
     * @return 例如 `0 分钟`、`45 分钟`、`1 小时`、`2 小时 15 分`
     */
    fun durationText(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        if (safe < 60) return "$safe 分钟"
        val hours = safe / 60
        val rest = safe % 60
        return if (rest == 0) "$hours 小时" else "$hours 小时 $rest 分"
    }

    /**
     * 趋势徽标文案：只给百分比，绝对值放在下一行，避免同一信息重复两遍。
     *
     * @param report 月度快照
     * @return 徽标文案
     */
    fun badgeText(report: MonthReport): String {
        if (report.isEmpty) return "暂无记录"
        if (report.prevMinutes <= 0) return "首次记录"
        val percent = ((report.delta.toFloat() / report.prevMinutes) * 100f).roundToInt()
        return when (report.trend) {
            MonthTrend.UP -> "+$percent%"
            MonthTrend.DOWN -> "$percent%"
            MonthTrend.FLAT -> "持平"
        }
    }

    /**
     * 与上月同期的差值文案。
     *
     * @param report 月度快照
     * @return 一行说明
     */
    fun deltaText(report: MonthReport): String {
        if (report.prevMinutes <= 0) {
            return if (report.isEmpty) "这个月还没有专注记录" else "上月同期还没有专注记录"
        }
        return when {
            report.delta > 0 -> "比上月同期多 ${report.delta} 分钟"
            report.delta < 0 -> "比上月同期少 ${-report.delta} 分钟"
            else -> "与上月同期持平"
        }
    }

    /**
     * 统计区间文案，例如 `11月1日 – 11月17日（17/30 天）`。
     *
     * @param report 月度快照
     * @return 区间说明
     */
    fun spanText(report: MonthReport): String {
        val endDay = report.elapsedDays.coerceAtLeast(0)
        if (endDay <= 0) return "${report.month.shortLabel}还没有开始"
        val span = "${report.month.month}月1日 – ${report.month.month}月${endDay}日"
        return if (report.isCurrent) {
            "${span}（${endDay}/${report.month.length} 天）"
        } else {
            "${span}（整月 ${report.month.length} 天）"
        }
    }

    /**
     * 指标行文案：有目标时报「活跃 / 达标 / 最佳」，无目标时用「最长连续」替换达标。
     *
     * @param report 月度快照
     * @return 指标文案
     */
    fun metricText(report: MonthReport): String {
        if (report.isEmpty) return "完成第一个番茄后，这里的格子会亮起来"
        val best = report.bestDay
        val bestText = if (best == null) {
            "暂无最佳单日"
        } else {
            "最佳 ${report.month.month}月${best.day}日 ${best.minutes} 分钟"
        }
        val middle = if (report.goal > 0) {
            "达标 ${report.achievedDays} 天"
        } else {
            "最长连续 ${report.longestStreak} 天"
        }
        return "活跃 ${report.activeDays} 天 · $middle · $bestText"
    }

    /**
     * 日均文案（按已过天数均摊）。
     *
     * @param report 月度快照
     * @return 例如 `日均 44 分钟`
     */
    fun averageText(report: MonthReport): String = "日均 ${report.dailyAverage} 分钟"

    /**
     * 整月推算 / 收尾文案。
     *
     * @param report 月度快照
     * @return 本月给推算，过去月给整月合计；空月份返回空串
     */
    fun projectionText(report: MonthReport): String {
        if (report.isEmpty) return ""
        if (!report.isCurrent) return "整月合计 ${durationText(report.totalMinutes)}"
        if (report.remainingDays <= 0) return "本月已结束，合计 ${durationText(report.totalMinutes)}"
        return "按当前节奏，整月约 ${durationText(report.projectedMinutes)}"
    }

    /**
     * 一句话结论：按达标率（无目标时按活跃率）给出鼓励或提醒，不做指责。
     *
     * @param report 月度快照
     * @return 结论文案
     */
    fun summaryText(report: MonthReport): String {
        if (report.isEmpty) return "这个月还没有专注记录，先完成一个番茄试试"
        val elapsed = report.elapsedDays.coerceAtLeast(1)
        val ratio = if (report.goal > 0) {
            report.achievedDays.toFloat() / elapsed
        } else {
            report.activeDays.toFloat() / elapsed
        }
        val label = if (report.goal > 0) "达标" else "专注"
        return when {
            ratio >= 0.8f -> "几乎每天都${label}，这个月的节奏很稳"
            ratio >= 0.5f -> "一半以上的日子都${label}了，保持住"
            ratio >= 0.2f -> "有 ${if (report.goal > 0) report.achievedDays else report.activeDays} 天${label}，节奏正在形成"
            else -> "这个月${label}的日子还不多，从一个番茄开始就好"
        }
    }
}
