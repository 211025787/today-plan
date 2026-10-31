package com.focuslist.app.util

import java.util.Calendar
import kotlin.math.ceil

/**
 * 热力图单个格子。
 *
 * @property dayStart 当天本地 0 点时间戳
 * @property minutes 当天专注分钟数
 * @property level 颜色等级 0（无）~ 4（最高）
 */
data class HeatCell(
    val dayStart: Long,
    val minutes: Int,
    val level: Int
)

/**
 * 热力图网格：**列 = 周**、**行 = 周一…周日**，与 GitHub 贡献图一致。
 *
 * @property columns 每一列是一周（最多 7 个格子），首列前几位可能为 null（该周起始早于统计区间）
 * @property monthLabels 月份标签：`列索引 -> 月份文案`（如 3 月）
 * @property maxMinutes 区间内单日最高分钟数，用于等级划分
 */
data class HeatGrid(
    val columns: List<List<HeatCell?>>,
    val monthLabels: List<Pair<Int, String>>,
    val maxMinutes: Int
)

/**
 * 年度总览指标。
 *
 * @property totalMinutes 区间内累计专注分钟
 * @property activeDays 有专注记录的天数
 * @property bestDayMinutes 最佳单日分钟数
 * @property longestStreak 最长连续天数
 * @property currentStreak 当前连续天数（今天或昨天有记录才算）
 * @property avgMinutesPerActiveDay 活跃日日均分钟
 */
data class YearOverview(
    val totalMinutes: Int = 0,
    val activeDays: Int = 0,
    val bestDayMinutes: Int = 0,
    val longestStreak: Int = 0,
    val currentStreak: Int = 0,
    val avgMinutesPerActiveDay: Int = 0
)

/**
 * 热力图纯计算工具：不依赖 Android / Room，可在 JVM 单元测试里直接跑。
 */
object Heatmap {

    /** 有效等级数量（1~4 级，0 表示无数据） */
    const val LEVEL_COUNT = 4

    /** 月份文案，索引 0 对应 1 月 */
    private val MONTH_NAMES = listOf("1月", "2月", "3月", "4月", "5月", "6月", "7月", "8月", "9月", "10月", "11月", "12月")

    /**
     * 计算某天的颜色等级：0 表示无数据，1~4 按占最高值的比例分档。
     *
     * @param minutes 当天专注分钟数
     * @param maxMinutes 区间内单日最高分钟数
     * @return 0~4 的等级
     */
    fun levelOf(minutes: Int, maxMinutes: Int): Int {
        if (minutes <= 0 || maxMinutes <= 0) {
            return 0
        }
        val ratio = minutes.toFloat() / maxMinutes.toFloat()
        val level = ceil(ratio * LEVEL_COUNT).toInt()
        return level.coerceIn(1, LEVEL_COUNT)
    }

    /**
     * 取某天在网格中的行号：周一 = 0 … 周日 = 6。
     *
     * @param dayStart 当天本地 0 点时间戳
     * @return 0~6 的行号
     */
    fun rowOf(dayStart: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
    }

    /**
     * 构建按周分列的热力图网格。
     *
     * @param rangeStart 统计区间首日（本地 0 点，含）
     * @param rangeEnd 统计区间末日（本地 0 点，含）
     * @param minutesByDay 每天专注分钟数（仅含有效日）
     * @return 网格；区间为空时返回空网格
     */
    fun buildGrid(rangeStart: Long, rangeEnd: Long, minutesByDay: Map<Long, Int>): HeatGrid {
        if (rangeEnd < rangeStart) {
            return HeatGrid(emptyList(), emptyList(), 0)
        }
        val maxMinutes = minutesByDay.values.maxOrNull() ?: 0
        val cells = ArrayList<HeatCell?>()

        // 首列前置占位：让第一列从区间首日的星期行开始，整体网格按周一对齐
        val startRow = rowOf(rangeStart)
        repeat(startRow) { cells.add(null) }

        var cursor = rangeStart
        while (cursor <= rangeEnd) {
            val minutes = minutesByDay[cursor] ?: 0
            cells.add(HeatCell(cursor, minutes, levelOf(minutes, maxMinutes)))
            cursor = DateUtils.addDays(cursor, 1)
        }

        val columns = cells.chunked(7)
        return HeatGrid(columns, monthLabelsOf(columns), maxMinutes)
    }

    /**
     * 生成月份标签：取每周第一个有效日所在月份，月份变化且距上个标签 ≥3 列时打标。
     *
     * @param columns 按周分列的网格
     * @return `列索引 -> 月份文案`
     */
    fun monthLabelsOf(columns: List<List<HeatCell?>>): List<Pair<Int, String>> {
        val labels = ArrayList<Pair<Int, String>>()
        var lastMonth = -1
        var lastIndex = -Int.MAX_VALUE
        columns.forEachIndexed { index, week ->
            val first = week.firstOrNull { it != null }
            val day = first ?: return@forEachIndexed
            val month = monthOf(day.dayStart)
            if (month != lastMonth && index - lastIndex >= 3) {
                labels.add(index to MONTH_NAMES[month - 1])
                lastMonth = month
                lastIndex = index
            }
        }
        return labels
    }

    /**
     * 汇总年度总览指标。
     *
     * @param minutesByDay 每天专注分钟数（仅含有效日）
     * @param todayStart 今天本地 0 点时间戳
     * @return 总览指标
     */
    fun overview(minutesByDay: Map<Long, Int>, todayStart: Long): YearOverview {
        val active = minutesByDay.filterValues { it > 0 }
        val total = active.values.sum()
        val days = active.size
        return YearOverview(
            totalMinutes = total,
            activeDays = days,
            bestDayMinutes = active.values.maxOrNull() ?: 0,
            longestStreak = longestStreak(active.keys),
            currentStreak = currentStreak(active.keys, todayStart),
            avgMinutesPerActiveDay = if (days > 0) total / days else 0
        )
    }

    /**
     * 最长连续天数：按日历天（而非固定毫秒差）判断相邻，跨夏令时也不会算错。
     *
     * @param daySet 有专注记录的本地 0 点时间戳集合
     * @return 最长连续天数
     */
    fun longestStreak(daySet: Set<Long>): Int {
        if (daySet.isEmpty()) {
            return 0
        }
        val sorted = daySet.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (DateUtils.addDays(sorted[i - 1], 1) == sorted[i]) run + 1 else 1
            if (run > best) {
                best = run
            }
        }
        return best
    }

    /**
     * 当前连续天数：今天有记录从今天起算，否则若昨天有记录从昨天起算，都没有则 0。
     *
     * @param daySet 有专注记录的本地 0 点时间戳集合
     * @param todayStart 今天本地 0 点时间戳
     * @return 当前连续天数
     */
    fun currentStreak(daySet: Set<Long>, todayStart: Long): Int {
        if (daySet.isEmpty()) {
            return 0
        }
        val yesterday = DateUtils.addDays(todayStart, -1)
        val cursorStart = when {
            daySet.contains(todayStart) -> todayStart
            daySet.contains(yesterday) -> yesterday
            else -> return 0
        }
        var streak = 0
        var cursor = cursorStart
        while (daySet.contains(cursor)) {
            streak++
            cursor = DateUtils.addDays(cursor, -1)
        }
        return streak
    }

    /**
     * 取某天所在月份（1~12）。
     *
     * @param dayStart 当天本地 0 点时间戳
     * @return 月份
     */
    private fun monthOf(dayStart: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        return cal.get(Calendar.MONTH) + 1
    }
}
