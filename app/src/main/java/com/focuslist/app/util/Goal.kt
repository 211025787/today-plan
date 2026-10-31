package com.focuslist.app.util

import kotlin.math.ceil
import kotlin.math.round

/**
 * 每日专注目标的达成状态。
 */
enum class GoalStatus {
    /** 今天还没有任何专注记录 */
    IDLE,

    /** 已开始但进度不足 60% */
    STARTED,

    /** 进度达到 60% 但尚未达标 */
    NEAR,

    /** 已达到当日目标 */
    DONE
}

/**
 * 每日专注目标纯计算工具：不依赖 Android / Room，可在 JVM 单元测试里直接跑。
 *
 * 目标只影响展示（进度环、柱状图参考线、达标天数），不参与计时逻辑，
 * 因此所有函数都是无副作用的纯函数。
 */
object Goal {

    /** 默认每日目标（分钟） */
    const val DEFAULT_MINUTES = 30

    /** 目标下限（分钟） */
    const val MIN_MINUTES = 5

    /** 目标上限（分钟） */
    const val MAX_MINUTES = 480

    /** 目标调整步长（分钟）：所有取值都是 5 的倍数 */
    const val STEP_MINUTES = 5

    /** 判定「接近目标」的进度比例 */
    const val NEAR_RATIO = 0.6f

    /** 给出建议目标所需的最少有效样本天数 */
    const val MIN_SAMPLE_DAYS = 3

    /** 建议目标的下限（分钟） */
    const val SUGGEST_MIN_MINUTES = 15

    /** 建议目标的上限（分钟） */
    const val SUGGEST_MAX_MINUTES = 240

    /** 设置页提供的快捷目标档位（分钟） */
    val PRESETS = listOf(15, 30, 45, 60, 90, 120)

    /**
     * 把目标值约束到合法范围并取整到步长倍数。
     *
     * 小于步长的输入（如 1 分钟）向上取到步长，避免目标过小导致进度环失真。
     *
     * @param minutes 输入分钟数
     * @return 落在 [MIN_MINUTES, MAX_MINUTES] 且为 [STEP_MINUTES] 倍数的目标值
     */
    fun coerce(minutes: Int): Int {
        val stepped = roundUpToStep(minutes)
        return stepped.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }

    /**
     * 计算目标完成进度。
     *
     * @param done 已完成分钟数
     * @param goal 目标分钟数
     * @return 0~1 的进度；目标非正数时返回 0
     */
    fun progress(done: Int, goal: Int): Float {
        if (goal <= 0) {
            return 0f
        }
        return (done.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 计算目标完成百分比（用于圆环上的文案）。
     *
     * @param done 已完成分钟数
     * @param goal 目标分钟数
     * @return 0~100 的整数百分比
     */
    fun percent(done: Int, goal: Int): Int {
        return round(progress(done, goal) * 100f).toInt()
    }

    /**
     * 距离达标还差多少分钟。
     *
     * @param done 已完成分钟数
     * @param goal 目标分钟数
     * @return 剩余分钟数；已达标时为 0
     */
    fun remaining(done: Int, goal: Int): Int {
        return (goal - done).coerceAtLeast(0)
    }

    /**
     * 判断当日目标达成状态。
     *
     * @param done 已完成分钟数
     * @param goal 目标分钟数
     * @return 达成状态；目标非正数时视为 [GoalStatus.IDLE]
     */
    fun status(done: Int, goal: Int): GoalStatus {
        if (goal <= 0) {
            return GoalStatus.IDLE
        }
        if (done <= 0) {
            return GoalStatus.IDLE
        }
        if (done >= goal) {
            return GoalStatus.DONE
        }
        return if (progress(done, goal) >= NEAR_RATIO) GoalStatus.NEAR else GoalStatus.STARTED
    }

    /**
     * 统计一组日数据里达标的天数。
     *
     * @param daily 每日专注分钟数（任意长度，通常为一周的 7 天）
     * @param goal 目标分钟数
     * @return 达标天数
     */
    fun achievedDays(daily: List<Int>, goal: Int): Int {
        if (goal <= 0) {
            return 0
        }
        return daily.count { it >= goal }
    }

    /**
     * 计算一周的目标总量。
     *
     * @param goal 每日目标分钟数
     * @return 一周（7 天）目标分钟数
     */
    fun weeklyGoal(goal: Int): Int {
        return goal * 7
    }

    /**
     * 计算一周的完成进度。
     *
     * @param daily 本周每日专注分钟数
     * @param goal 每日目标分钟数
     * @return 0~1 的进度
     */
    fun weekProgress(daily: List<Int>, goal: Int): Float {
        val target = weeklyGoal(goal)
        if (target <= 0) {
            return 0f
        }
        return (daily.sum().toFloat() / target.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 计算「连续达标」天数：末位是今天，今天还没达标不算断（从昨天起算）。
     *
     * @param daysAscending 按日期升序排列的每日专注分钟数，最后一个元素代表今天
     * @param goal 目标分钟数
     * @return 连续达标天数
     */
    fun goalStreak(daysAscending: List<Int>, goal: Int): Int {
        if (daysAscending.isEmpty() || goal <= 0) {
            return 0
        }
        var cursor = daysAscending.size - 1
        if (daysAscending[cursor] < goal) {
            cursor--
        }
        var streak = 0
        while (cursor >= 0 && daysAscending[cursor] >= goal) {
            streak++
            cursor--
        }
        return streak
    }

    /**
     * 按近期表现建议一个每日目标：取有效日分钟数的中位数，向上取整到步长倍数。
     *
     * 样本不足（有效日少于 [MIN_SAMPLE_DAYS] 天）时返回默认值，避免用零星数据误导用户。
     *
     * @param recentMinutes 近期每日专注分钟数（通常不含今天）
     * @return 建议的目标分钟数
     */
    fun suggest(recentMinutes: List<Int>): Int {
        val active = recentMinutes.filter { it > 0 }.sorted()
        if (active.size < MIN_SAMPLE_DAYS) {
            return DEFAULT_MINUTES
        }
        val mid = active.size / 2
        val median = if (active.size % 2 == 1) {
            active[mid]
        } else {
            (active[mid - 1] + active[mid]) / 2
        }
        return roundUpToStep(median).coerceIn(SUGGEST_MIN_MINUTES, SUGGEST_MAX_MINUTES)
    }

    /**
     * 生成进度文案（中文）。
     *
     * @param done 已完成分钟数
     * @param goal 目标分钟数
     * @return 直接展示给用户的一句话
     */
    fun statusText(done: Int, goal: Int): String {
        return when (status(done, goal)) {
            GoalStatus.IDLE -> "今天还没开始，目标 $goal 分钟"
            GoalStatus.STARTED, GoalStatus.NEAR -> "已专注 $done 分钟，还差 ${remaining(done, goal)} 分钟达标"
            GoalStatus.DONE -> {
                val extra = done - goal
                if (extra > 0) "已达标：今日 $done 分钟，超出 $extra 分钟" else "已达标：今日 $done 分钟"
            }
        }
    }

    /**
     * 向上取整到步长倍数。
     *
     * @param minutes 分钟数
     * @return 不小于输入值且为 [STEP_MINUTES] 倍数的值
     */
    private fun roundUpToStep(minutes: Int): Int {
        return ceil(minutes.toFloat() / STEP_MINUTES).toInt() * STEP_MINUTES
    }
}
