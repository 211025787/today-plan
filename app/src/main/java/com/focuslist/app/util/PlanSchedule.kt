package com.focuslist.app.util

import com.focuslist.app.data.local.entity.PlanEntity
import java.util.Calendar

/**
 * 计划执行日类型：决定一天是否属于这个计划的「该做」日。
 *
 * 这是 [PlanEntity.dayRule] 的取值域。只有 [EVERY_DAY] 会继续看周几掩码，
 * 工作日 / 休息日两种规则直接由日历与法定节假日决定，与周几掩码无关。
 */
object DayRule {

    /** 按周几执行（掩码全选即「每天」） */
    const val EVERY_DAY = 0

    /** 只在工作日执行：周一至周五，且跳过法定节假日；调休上班日照常执行 */
    const val WORKDAY = 1

    /** 只在休息日执行：周六日 + 法定节假日；调休上班日不算休息 */
    const val REST_DAY = 2

    /**
     * 规则简称（用于设置项、卡片角标）。
     *
     * @param rule 规则编码
     * @return 中文简称
     */
    fun label(rule: Int): String = when (rule) {
        WORKDAY -> "工作日"
        REST_DAY -> "休息日"
        else -> "每天"
    }
}

/**
 * 周几位掩码：bit0=周一 … bit6=周日。
 *
 * 用一条 Int 表示「一周里选中的星期几」，比存 7 个布尔字段省事，
 * 也方便直接写进 Room 的单列。
 */
object WeekdayMask {

    /** 空集（等价于没选） */
    const val NONE = 0

    /** 周一 */
    const val MON = 1 shl 0

    /** 周二 */
    const val TUE = 1 shl 1

    /** 周三 */
    const val WED = 1 shl 2

    /** 周四 */
    const val THU = 1 shl 3

    /** 周五 */
    const val FRI = 1 shl 4

    /** 周六 */
    const val SAT = 1 shl 5

    /** 周日 */
    const val SUN = 1 shl 6

    /** 七天全选 */
    const val ALL = MON or TUE or WED or THU or FRI or SAT or SUN

    /** 周一至周五 */
    const val WEEKDAYS = MON or TUE or WED or THU or FRI

    /** 周六日 */
    const val WEEKEND = SAT or SUN

    /** 周一 → 周日的位顺序（界面按这个顺序排 chip） */
    val ORDER = intArrayOf(MON, TUE, WED, THU, FRI, SAT, SUN)

    /** 与 [ORDER] 一一对应的单字标签 */
    val LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")

    /**
     * 把 [Calendar.DAY_OF_WEEK] 换算成对应的位。
     *
     * Calendar 里周日是 1、周一是 2……周六是 7，减一后正好错开一天，
     * 加 5 取模 7 就能把周一映射到 bit0。
     *
     * @param dayOfWeek Calendar 的星期常量（1..7）
     * @return 单位掩码
     */
    fun bitOfDayOfWeek(dayOfWeek: Int): Int = 1 shl ((dayOfWeek + 5) % 7)

    /**
     * 判断某天是否落在掩码里。
     *
     * @param mask 位掩码；0 视为全选
     * @param dayStart 当天 0 点时间戳
     * @return true 表示选中
     */
    fun contains(mask: Int, dayStart: Long): Boolean {
        val safeMask = if (mask == NONE) ALL else mask
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        return (safeMask and bitOfDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))) != 0
    }

    /**
     * 掩码里选中了几天。
     *
     * @param mask 位掩码；0 视为全选
     * @return 1..7
     */
    fun count(mask: Int): Int {
        val safeMask = if (mask == NONE) ALL else mask
        return Integer.bitCount(safeMask and ALL)
    }

    /**
     * 掩码的中文描述，用于卡片与详情页。
     *
     * @param mask 位掩码；0 视为全选
     * @return 「每天」「周一至周五」「周末」「每周一、三、五」之类
     */
    fun summary(mask: Int): String {
        val safeMask = if (mask == NONE) ALL else mask
        return when (safeMask and ALL) {
            ALL -> "每天"
            WEEKDAYS -> "周一至周五"
            WEEKEND -> "周末"
            else -> {
                val picked = ORDER.filter { (safeMask and it) != 0 }
                if (picked.isEmpty()) {
                    "每天"
                } else {
                    // 「每周一、三、五」比「每周一、每周三、每周五」短，卡片上放得下
                    "每周" + picked.joinToString("、") { LABELS[ORDER.indexOf(it)] }
                }
            }
        }
    }
}

/**
 * 中国大陆法定节假日与调休上班日（2025-2027 年，据国务院办公厅历年通知整理）。
 *
 * 只做两件事：判断某天是不是「休息日」、是不是「工作日」。
 * 调休上班的周六周日算工作日，法定假日（含调休放假）算休息日。
 *
 * 内置数据覆盖不到的年份（2024 及更早、2028 及以后）按「周一至周五上班、
 * 周六周日休息」兜底，等官方通知发布后再补表即可。
 */
object ChinaHolidays {

    /** 放假日（含调休出来的休息日） */
    private val restDays: Set<Long>

    /** 调休上班日（原本是周末，被调整为工作日） */
    private val workDays: Set<Long>

    init {
        val rest = HashSet<Long>()
        val work = HashSet<Long>()
        // 格式：单日 "yyyy-MM-dd"，连续区间 "yyyy-MM-dd:yyyy-MM-dd"（含两端）
        listOf(
            // ── 2025 ──
            "2025-01-01",
            "2025-01-28:2025-02-04",
            "2025-04-04:2025-04-06",
            "2025-05-01:2025-05-05",
            "2025-05-31:2025-06-02",
            "2025-10-01:2025-10-08",
            // ── 2026 ──
            "2026-01-01:2026-01-03",
            "2026-02-15:2026-02-23",
            "2026-04-04:2026-04-06",
            "2026-05-01:2026-05-05",
            "2026-06-19:2026-06-21",
            "2026-09-25:2026-09-27",
            "2026-10-01:2026-10-07",
            // ── 2027 ──
            "2027-01-01:2027-01-03",
            "2027-02-05:2027-02-13",
            "2027-04-03:2027-04-05",
            "2027-05-01:2027-05-05",
            "2027-06-09",
            "2027-09-15",
            "2027-10-01:2027-10-07"
        ).forEach { range -> rest += expandRange(range) }

        listOf(
            // ── 2025 ──
            "2025-01-26", "2025-02-08", "2025-04-27", "2025-09-28", "2025-10-11",
            // ── 2026 ──
            "2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10",
            // ── 2027 ──
            "2027-01-31", "2027-02-14", "2027-05-08", "2027-09-26", "2027-10-09"
        ).forEach { day -> work += parseDay(day) }

        restDays = rest
        workDays = work
    }

    /**
     * 是否是休息日：法定节假日（含调休放假）或普通周末，调休上班日除外。
     *
     * @param dayStart 当天 0 点时间戳
     * @return true 表示休息
     */
    fun isRestDay(dayStart: Long): Boolean {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStart
        return isRestDay(dayStart, cal.get(Calendar.DAY_OF_WEEK))
    }

    /**
     * 是否是休息日（已知星期，批量遍历时省一次 Calendar 取值）。
     *
     * @param dayStart 当天 0 点时间戳
     * @param dayOfWeek Calendar 的星期常量（1..7）
     * @return true 表示休息
     */
    fun isRestDay(dayStart: Long, dayOfWeek: Int): Boolean {
        if (dayStart in workDays) {
            return false
        }
        if (dayStart in restDays) {
            return true
        }
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    /**
     * 是否是工作日（休息日的反面）。
     *
     * @param dayStart 当天 0 点时间戳
     * @return true 表示上班
     */
    fun isWorkday(dayStart: Long): Boolean = !isRestDay(dayStart)

    /**
     * 是否是内置表里明确标注的法定假日（不含普通周末）。
     *
     * @param dayStart 当天 0 点时间戳
     * @return true 表示是法定假日
     */
    fun isHoliday(dayStart: Long): Boolean = dayStart in restDays

    /** 把单日或区间展开成 0 点时间戳集合 */
    private fun expandRange(range: String): List<Long> {
        val parts = range.split(':')
        val from = parseDay(parts[0])
        val to = if (parts.size > 1) parseDay(parts[1]) else from
        val days = ArrayList<Long>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        while (cal.timeInMillis <= to) {
            days += cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    /** "yyyy-MM-dd" → 当天 0 点时间戳 */
    private fun parseDay(text: String): Long {
        val nums = text.split('-')
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(nums[0].toInt(), nums[1].toInt() - 1, nums[2].toInt(), 0, 0, 0)
        return cal.timeInMillis
    }
}

/**
 * 计划执行日计算：判断某天做不做、一段区间共做几天、下一次是哪天。
 *
 * 计划本身仍然是「一个日期区间 + 一套每日目标」，执行日规则只负责在这个区间里
 * 筛出真正要做的那些天。区间勾上「只在周末」就只在周六日出现，其余日期连卡片都不显示。
 *
 * 口径：区间两端都含；掩码为 0 视为每天（兼容升级前的老计划）。
 */
object PlanSchedule {

    /**
     * 某天是否是这个计划的执行日。
     *
     * @param plan 计划实体
     * @param dayStart 当天 0 点时间戳
     * @return true 表示这天要做
     */
    fun isActive(plan: PlanEntity, dayStart: Long): Boolean {
        return isActive(plan.startDate, plan.endDate, plan.weekdaysMask, plan.dayRule, dayStart)
    }

    /**
     * 某天是否落在 [start, end] 区间内且符合执行规则。
     *
     * @param start 起始日 0 点（含）
     * @param end 截止日 0 点（含）
     * @param weekdaysMask 周几掩码；0 视为每天
     * @param dayRule 执行日类型，见 [DayRule]
     * @param dayStart 待判断的日期 0 点
     * @return true 表示这天要做
     */
    fun isActive(
        start: Long,
        end: Long,
        weekdaysMask: Int,
        dayRule: Int,
        dayStart: Long
    ): Boolean {
        if (dayStart < start || dayStart > end) {
            return false
        }
        return matchesRule(weekdaysMask, dayRule, dayStart)
    }

    /**
     * 只看规则、不看区间的判断（日历外部已经做过区间筛选时用）。
     *
     * @param weekdaysMask 周几掩码；0 视为每天
     * @param dayRule 执行日类型，见 [DayRule]
     * @param dayStart 待判断的日期 0 点
     * @return true 表示符合规则
     */
    fun matchesRule(weekdaysMask: Int, dayRule: Int, dayStart: Long): Boolean {
        return when (dayRule) {
            DayRule.WORKDAY -> ChinaHolidays.isWorkday(dayStart)
            DayRule.REST_DAY -> ChinaHolidays.isRestDay(dayStart)
            else -> WeekdayMask.contains(weekdaysMask, dayStart)
        }
    }

    /**
     * 计划内的全部执行日。
     *
     * @param plan 计划实体
     * @param limit 最多返回多少个，超出即截断（界面预览用，避免超长区间卡顿）
     * @return 升序执行日 0 点时间戳
     */
    fun activeDays(plan: PlanEntity, limit: Int = 400): List<Long> {
        return activeDays(plan.startDate, plan.endDate, plan.weekdaysMask, plan.dayRule, limit)
    }

    /**
     * 区间内符合规则的前 [limit] 个执行日。
     *
     * @param start 起始日 0 点（含）
     * @param end 截止日 0 点（含）
     * @param weekdaysMask 周几掩码；0 视为每天
     * @param dayRule 执行日类型
     * @param limit 最多返回多少个
     * @return 升序执行日
     */
    fun activeDays(
        start: Long,
        end: Long,
        weekdaysMask: Int,
        dayRule: Int,
        limit: Int = 400
    ): List<Long> {
        val result = ArrayList<Long>()
        if (end < start || limit <= 0) {
            return result
        }
        val safeMask = if (weekdaysMask == WeekdayMask.NONE) WeekdayMask.ALL else weekdaysMask
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        while (cal.timeInMillis <= end && result.size < limit) {
            val day = cal.timeInMillis
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val hit = when (dayRule) {
                DayRule.WORKDAY -> ChinaHolidays.isWorkday(day)
                DayRule.REST_DAY -> ChinaHolidays.isRestDay(day, dow)
                else -> (safeMask and WeekdayMask.bitOfDayOfWeek(dow)) != 0
            }
            if (hit) {
                result += day
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /**
     * 计划内一共有多少个执行日（用于「共 N 天」与进度分母）。
     *
     * @param plan 计划实体
     * @return 执行日总数；区间非法时为 0
     */
    fun countActive(plan: PlanEntity): Int {
        return countActive(plan.startDate, plan.endDate, plan.weekdaysMask, plan.dayRule)
    }

    /**
     * 区间内共有多少个执行日。
     *
     * @param start 起始日 0 点（含）
     * @param end 截止日 0 点（含）
     * @param weekdaysMask 周几掩码；0 视为每天
     * @param dayRule 执行日类型
     * @return 执行日总数
     */
    fun countActive(start: Long, end: Long, weekdaysMask: Int, dayRule: Int): Int {
        if (end < start) {
            return 0
        }
        // 每天都做时不用逐日遍历，直接按日历天数算，跨十年的长计划也不卡
        if (dayRule == DayRule.EVERY_DAY &&
            (weekdaysMask == WeekdayMask.NONE || weekdaysMask == WeekdayMask.ALL)
        ) {
            return (((end - start) / DateUtils.DAY_MILLIS) + 1).toInt()
        }
        // 同一份计划在一次重组里会被问好几遍「共几天」，逐日遍历结果又是稳定的，
        // 直接缓存；节假日表是编译期常量，不用担心过期
        val key = CountKey(start, end, weekdaysMask, dayRule)
        countCache[key]?.let { return it }
        val safeMask = if (weekdaysMask == WeekdayMask.NONE) WeekdayMask.ALL else weekdaysMask
        var count = 0
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        while (cal.timeInMillis <= end) {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val hit = when (dayRule) {
                DayRule.WORKDAY -> ChinaHolidays.isWorkday(cal.timeInMillis)
                DayRule.REST_DAY -> ChinaHolidays.isRestDay(cal.timeInMillis, dow)
                else -> (safeMask and WeekdayMask.bitOfDayOfWeek(dow)) != 0
            }
            if (hit) {
                count++
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (countCache.size > 256) {
            countCache.clear()
        }
        countCache[key] = count
        return count
    }

    /** [countActive] 的缓存键 */
    private data class CountKey(val start: Long, val end: Long, val mask: Int, val rule: Int)

    /** 执行日总数缓存：计划数量有限，256 条足够覆盖一遍日历渲染 */
    private val countCache = java.util.concurrent.ConcurrentHashMap<CountKey, Int>()

    /**
     * 从计划开始到 [day]（含）已经有几个执行日——即「今天是第几个执行日」。
     *
     * 计划还没开始时返回 0；[day] 超过截止日时返回总执行日数。
     *
     * @param plan 计划实体
     * @param day 目标日期 0 点
     * @return 执行日序号（含目标当天，若当天是执行日）
     */
    fun activeIndexUntil(plan: PlanEntity, day: Long): Int {
        if (day < plan.startDate) {
            return 0
        }
        val end = minOf(day, plan.endDate)
        return countActive(plan.startDate, end, plan.weekdaysMask, plan.dayRule)
    }

    /**
     * 从 [from] 起（含）的第一个执行日。
     *
     * @param plan 计划实体
     * @param from 起始查询日 0 点
     * @return 执行日 0 点；计划剩余区间内没有执行日时为 null
     */
    fun nextActive(plan: PlanEntity, from: Long): Long? {
        val start = maxOf(from, plan.startDate)
        if (start > plan.endDate) {
            return null
        }
        return activeDays(start, plan.endDate, plan.weekdaysMask, plan.dayRule, 1).firstOrNull()
    }

    /**
     * 从 [from] 起（含）向前的最后一个执行日。
     *
     * @param plan 计划实体
     * @param from 起始查询日 0 点
     * @return 执行日 0 点；没有则返回 null
     */
    fun lastActive(plan: PlanEntity, from: Long): Long? {
        val end = minOf(from, plan.endDate)
        if (end < plan.startDate) {
            return null
        }
        var cursor = end
        while (cursor >= plan.startDate) {
            if (isActive(plan, cursor)) {
                return cursor
            }
            cursor = DateUtils.addDays(cursor, -1)
        }
        return null
    }

    /**
     * 规则文案，例如「每天」「工作日」「每周一、三、五」。
     *
     * @param plan 计划实体
     * @return 中文描述
     */
    fun ruleText(plan: PlanEntity): String {
        return describe(plan.weekdaysMask, plan.dayRule)
    }

    /**
     * 规则文案（只给字段值，尚未落库的草稿也能用）。
     *
     * @param weekdaysMask 周几掩码；0 视为每天
     * @param dayRule 执行日类型
     * @return 「每天」「工作日」「休息日」「每周一、三、五」之类
     */
    fun describe(weekdaysMask: Int, dayRule: Int): String {
        return when (dayRule) {
            DayRule.WORKDAY -> "工作日"
            DayRule.REST_DAY -> "休息日"
            else -> WeekdayMask.summary(weekdaysMask)
        }
    }
}
