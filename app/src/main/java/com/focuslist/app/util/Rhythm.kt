package com.focuslist.app.util

import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 专注节律：把一天的 24 小时切成若干时段，算出"你通常在什么时候专注"。
 *
 * 全部是纯 Kotlin 计算（只依赖 [DateUtils] 与 [Calendar]），不碰 Android / Room，
 * 因此可以直接在 JVM 单元测试里跑。
 */
object Rhythm {

    /** 一天的小时数 */
    const val HOURS = 24

    /** 每个时段包含的小时数 */
    const val SLOT_HOURS = 4

    /** 时段数量（24 / 4 = 6） */
    const val SLOT_COUNT = HOURS / SLOT_HOURS

    /** 时段名称：索引 0 对应 0–4 点 */
    val SLOT_NAMES = listOf("凌晨", "清晨", "上午", "午间", "下午", "晚间")

    /**
     * 把任意长度的小时数组补齐（或截断）成 24 项，并把负值归零。
     *
     * @param byHour 每小时专注分钟数
     * @return 长度恒为 24 且非负的列表
     */
    fun normalize(byHour: List<Int>): List<Int> {
        val out = ArrayList<Int>(HOURS)
        for (hour in 0 until HOURS) {
            val value = byHour.getOrElse(hour) { 0 }
            out.add(if (value > 0) value else 0)
        }
        return out
    }

    /**
     * 小时所属的时段下标（0~5）：0–3 点为 0，4–7 点为 1，以此类推。
     *
     * @param hour 小时 0~23
     * @return 时段下标；越界会被夹取
     */
    fun slotOf(hour: Int): Int {
        return (hour / SLOT_HOURS).coerceIn(0, SLOT_COUNT - 1)
    }

    /**
     * 时段起始小时（含）。
     *
     * @param slot 时段下标
     * @return 起始小时，例如时段 2 返回 8
     */
    fun slotStartHour(slot: Int): Int {
        return slot.coerceIn(0, SLOT_COUNT - 1) * SLOT_HOURS
    }

    /**
     * 时段结束小时（不含）。
     *
     * @param slot 时段下标
     * @return 结束小时，例如时段 2 返回 12（最后一个时段返回 24）
     */
    fun slotEndHour(slot: Int): Int {
        return slotStartHour(slot) + SLOT_HOURS
    }

    /**
     * 时段名称。
     *
     * @param slot 时段下标
     * @return 名称，例如 "上午"
     */
    fun slotName(slot: Int): String {
        return SLOT_NAMES[slot.coerceIn(0, SLOT_COUNT - 1)]
    }

    /**
     * 时段的小时区间文案。
     *
     * @param slot 时段下标
     * @return 文案，例如 "8–12 点"
     */
    fun slotRangeText(slot: Int): String {
        return "${slotStartHour(slot)}–${slotEndHour(slot)} 点"
    }

    /**
     * 小时文案。
     *
     * @param hour 小时 0~23
     * @return 文案，例如 "9 点"
     */
    fun hourText(hour: Int): String {
        return "${hour.coerceIn(0, HOURS - 1)} 点"
    }

    /**
     * 把 24 小时聚合成 6 个时段的总分钟数。
     *
     * @param byHour 每小时专注分钟数
     * @return 长度 6 的时段分钟数
     */
    fun sumBySlot(byHour: List<Int>): List<Int> {
        val norm = normalize(byHour)
        val out = IntArray(SLOT_COUNT)
        for (hour in 0 until HOURS) {
            out[slotOf(hour)] += norm[hour]
        }
        return out.toList()
    }

    /**
     * 全天总专注分钟数。
     *
     * @param byHour 每小时专注分钟数
     * @return 总分钟数
     */
    fun total(byHour: List<Int>): Int {
        return normalize(byHour).sum()
    }

    /**
     * 占比百分比（四舍五入）。
     *
     * @param part 部分值
     * @param total 总值；小于等于 0 时返回 0
     * @return 0~100 的整数
     */
    fun percent(part: Int, total: Int): Int {
        if (total <= 0 || part <= 0) {
            return 0
        }
        return ((part * 100f) / total).roundToInt()
    }

    /**
     * 各时段占全天的百分比。
     *
     * @param byHour 每小时专注分钟数
     * @return 长度 6 的百分比（可能因四舍五入总和不等于 100）
     */
    fun share(byHour: List<Int>): List<Int> {
        val slots = sumBySlot(byHour)
        val sum = slots.sum()
        return slots.map { percent(it, sum) }
    }

    /**
     * 专注最多的时段（"黄金时段"）。
     *
     * @param byHour 每小时专注分钟数
     * @return 时段下标；全为 0 时返回 -1
     */
    fun bestSlot(byHour: List<Int>): Int {
        val slots = sumBySlot(byHour)
        val max = slots.maxOrNull() ?: 0
        if (max <= 0) {
            return -1
        }
        return slots.indexOf(max)
    }

    /**
     * 峰值小时：专注分钟数最高的若干个小时（同分钟数时小时小的在前）。
     *
     * @param byHour 每小时专注分钟数
     * @param topN 取前几个，至少 1
     * @return 按分钟数降序排列的小时列表（只含有记录的小时）
     */
    fun peakHours(byHour: List<Int>, topN: Int = 3): List<Int> {
        val norm = normalize(byHour)
        return norm
            .mapIndexed { hour, minutes -> hour to minutes }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .take(topN.coerceAtLeast(1))
            .map { it.first }
    }

    /**
     * 峰值小时文案。
     *
     * @param byHour 每小时专注分钟数
     * @param topN 取前几个
     * @return 文案，例如 "9 点、14 点、20 点"；无数据时为空串
     */
    fun peakText(byHour: List<Int>, topN: Int = 3): String {
        val peaks = peakHours(byHour, topN)
        if (peaks.isEmpty()) {
            return ""
        }
        return peaks.joinToString("、") { hourText(it) }
    }

    /**
     * 一句话节律总结。
     *
     * @param byHour 每小时专注分钟数
     * @return 文案；无数据时给出引导语
     */
    fun summaryText(byHour: List<Int>): String {
        if (total(byHour) <= 0) {
            return "还没有专注记录，完成一个番茄后这里就会出现你的作息节律"
        }
        val slot = bestSlot(byHour)
        val slotMinutes = sumBySlot(byHour)[slot]
        val pct = percent(slotMinutes, total(byHour))
        val peaks = peakText(byHour)
        return "黄金时段是「${slotName(slot)}」${slotRangeText(slot)}，占 ${pct}%；峰值出现在 $peaks"
    }

    /**
     * 判断某天是否周末（周六 / 周日）。
     *
     * @param dayStart 当天本地 0 点时间戳
     * @return 是周末返回 true
     */
    fun isWeekend(dayStart: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    /**
     * 统计区间内工作日 / 周末各有多少天。
     *
     * @param rangeStart 区间首日（本地 0 点，含）
     * @param rangeEnd 区间末日（本地 0 点，含）
     * @return `工作日天数 to 周末天数`；区间非法时返回 `0 to 0`
     */
    fun countByKind(rangeStart: Long, rangeEnd: Long): Pair<Int, Int> {
        if (rangeEnd < rangeStart) {
            return 0 to 0
        }
        var weekday = 0
        var weekend = 0
        var cursor = rangeStart
        // 防御性上限：区间异常大时也不会死循环
        var guard = 0
        while (cursor <= rangeEnd && guard < 5000) {
            if (isWeekend(cursor)) {
                weekend++
            } else {
                weekday++
            }
            cursor = DateUtils.addDays(cursor, 1)
            guard++
        }
        return weekday to weekend
    }

    /**
     * 把按天数据拆成工作日 / 周末的分钟数总和。
     *
     * @param minutesByDay 本地 0 点时间戳 -> 当天分钟数
     * @return `工作日总分钟 to 周末总分钟`
     */
    fun splitByKind(minutesByDay: Map<Long, Int>): Pair<Int, Int> {
        var weekday = 0
        var weekend = 0
        minutesByDay.forEach { (dayStart, minutes) ->
            if (minutes <= 0) {
                return@forEach
            }
            if (isWeekend(dayStart)) {
                weekend += minutes
            } else {
                weekday += minutes
            }
        }
        return weekday to weekend
    }

    /**
     * 工作日 / 周末的**日均**专注分钟数。
     *
     * @param minutesByDay 本地 0 点时间戳 -> 当天分钟数
     * @param rangeStart 区间首日（本地 0 点，含）
     * @param rangeEnd 区间末日（本地 0 点，含）
     * @return `工作日日均 to 周末日均`
     */
    fun kindAverages(minutesByDay: Map<Long, Int>, rangeStart: Long, rangeEnd: Long): Pair<Int, Int> {
        val (weekdayCount, weekendCount) = countByKind(rangeStart, rangeEnd)
        val (weekdaySum, weekendSum) = splitByKind(minutesByDay)
        val weekdayAvg = if (weekdayCount > 0) weekdaySum / weekdayCount else 0
        val weekendAvg = if (weekendCount > 0) weekendSum / weekendCount else 0
        return weekdayAvg to weekendAvg
    }

    /**
     * 工作日 / 周末对比文案。
     *
     * @param weekdayAvg 工作日日均分钟
     * @param weekendAvg 周末日均分钟
     * @return 对比文案
     */
    fun balanceText(weekdayAvg: Int, weekendAvg: Int): String {
        if (weekdayAvg <= 0 && weekendAvg <= 0) {
            return "工作日与周末都还没有数据"
        }
        if (weekendAvg <= 0) {
            return "周末还没有专注记录，工作日日均 $weekdayAvg 分钟"
        }
        if (weekdayAvg <= 0) {
            return "工作日还没有专注记录，周末日均 $weekendAvg 分钟"
        }
        val diff = weekdayAvg - weekendAvg
        return when {
            diff > 5 -> "工作日更投入：日均 $weekdayAvg 分钟，比周末多 $diff 分钟"
            diff < -5 -> "周末更投入：日均 $weekendAvg 分钟，比工作日多 ${-diff} 分钟"
            else -> "工作日与周末差不多：日均 $weekdayAvg / $weekendAvg 分钟"
        }
    }
}
