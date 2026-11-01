package com.focuslist.app.util

/**
 * 计划单日投入点：某个计划在某一天的累计投入与当天目标。
 *
 * @property date 当天 0 点时间戳
 * @property seconds 当天累计投入秒数（多次计时累加后的结果）
 * @property goalSeconds 当天目标秒数
 */
data class PlanDayPoint(
    val date: Long,
    val seconds: Long,
    val goalSeconds: Long
) {

    /** 当天投入分钟数（向下取整，用于展示） */
    val minutes: Int
        get() = (seconds / 60L).toInt()

    /** 当天目标分钟数 */
    val goalMinutes: Int
        get() = (goalSeconds / 60L).toInt()

    /** 是否达标：达到目标即算，目标非正数时永不达标 */
    val achieved: Boolean
        get() = goalSeconds > 0 && seconds >= goalSeconds

    /** 完成比例：封顶 [PlanInsights.MAX_RATIO]，目标非正数时为 0 */
    val ratio: Float
        get() = if (goalSeconds <= 0) {
            0f
        } else {
            (seconds.toFloat() / goalSeconds.toFloat()).coerceIn(0f, PlanInsights.MAX_RATIO)
        }

    /** 柱状图用比例：达标即满格，超出部分不拉高柱子（避免图被一根超高柱压扁） */
    val barRatio: Float
        get() = ratio.coerceAtMost(1f)

    /** 超出目标的秒数；未超出为 0 */
    val overshootSeconds: Long
        get() = (seconds - goalSeconds).coerceAtLeast(0L)

    /** 距目标还差的秒数；已达标为 0 */
    val remainSeconds: Long
        get() = (goalSeconds - seconds).coerceAtLeast(0L)
}

/**
 * 单个计划的投入画像：最近一段时间的逐日投入、连续达标与累计。
 *
 * @property days 窗口内的逐日投入，按时间从旧到新；只包含计划有效期内的日子
 * @property goalMinutes 计划每日目标（分钟）
 * @property streak 连续达标天数：从今天（计划已结束时从最后一天）往回数
 * @property bestStreak 回溯窗口内的最长连续达标天数
 * @property totalSeconds 窗口内累计投入秒数
 * @property activeDays 窗口内有投入的天数
 * @property achievedDays 窗口内达标的天数
 * @property averageSeconds 活跃日日均秒数（无投入时为 0）
 * @property activeToday 今天是否仍在该计划有效期内
 */
data class PlanInsight(
    val days: List<PlanDayPoint> = emptyList(),
    val goalMinutes: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val totalSeconds: Long = 0L,
    val activeDays: Int = 0,
    val achievedDays: Int = 0,
    val averageSeconds: Long = 0L,
    val activeToday: Boolean = false
) {

    /** 窗口天数 */
    val windowDays: Int
        get() = days.size

    /** 没有可统计的日子（计划还没开始） */
    val isEmpty: Boolean
        get() = days.isEmpty()

    /** 窗口内的今天；计划已结束或还没开始时为 null */
    val today: PlanDayPoint?
        get() = if (activeToday) days.lastOrNull() else null

    /** 达标率百分比（0-100） */
    val ratePercent: Int
        get() = if (days.isEmpty()) 0 else achievedDays * 100 / days.size

    /** 标题文案：连续达标优先，其次今日达标，最后退到窗口统计 */
    val headline: String
        get() = when {
            days.isEmpty() -> "计划还没开始"
            streak >= 2 -> "连续达标 $streak 天"
            today?.achieved == true -> "今天已达标"
            activeDays == 0 -> "还没有投入记录"
            achievedDays == 0 -> "近 $windowDays 天还没达标"
            else -> "近 $windowDays 天达标 $achievedDays 天"
        }

    /** 今日文案：还差多少 / 超出多少 / 已结束 */
    val todayText: String
        get() {
            if (days.isEmpty()) {
                return "计划开始后，这里会显示每天的投入"
            }
            val point = today ?: return "计划已结束，共投入 ${PlanInsights.durationText(totalSeconds)}"
            return when {
                point.achieved -> {
                    val over = PlanInsights.ceilMinutes(point.overshootSeconds)
                    if (over <= 0) {
                        "今天刚好达标，${point.minutes} 分钟"
                    } else {
                        "今天超出目标 $over 分钟，共 ${point.minutes} 分钟"
                    }
                }
                point.seconds <= 0 -> "今天还差 ${point.goalMinutes} 分钟达标"
                else -> "今天还差 ${PlanInsights.ceilMinutes(point.remainSeconds)} 分钟达标"
            }
        }

    /** 累计投入文案 */
    val totalText: String
        get() = if (totalSeconds <= 0) {
            "还没有投入记录"
        } else {
            "累计投入 ${PlanInsights.durationText(totalSeconds)}"
        }

    /** 日均文案：按有投入的日子平均，避免没开始的日子稀释 */
    val averageText: String
        get() = if (activeDays <= 0) {
            "活跃日日均 —"
        } else {
            "活跃日日均 ${PlanInsights.durationText(averageSeconds)}"
        }

    /** 达标率文案 */
    val rateText: String
        get() = if (days.isEmpty()) {
            "达标率 —"
        } else {
            "达标率 $ratePercent%（$achievedDays/$windowDays 天）"
        }
}

/**
 * 计划投入画像的纯计算工具：不依赖 Android / Room，可直接在 JVM 单测里跑。
 *
 * 口径约定：
 * - 窗口默认取「最近 14 天」，右端锚定 `min(今天, 计划结束日)`，所以已结束的计划看到的是
 *   它最后 14 天，而不是一片空白；
 * - 窗口只保留计划有效期内的日子，计划开始前的空白不参与达标率，避免「刚开始就 0%」；
 * - 连续达标沿用应用其它地方的约定：**今天还没达标时从昨天往回数**，今天一旦达标立刻计入；
 *   但计划已经结束时不再享受这个宽容——最后一天没达标就是 0；
 * - 最长连续回溯 90 天，可能长于展示窗口。
 */
object PlanInsights {

    /** 展示窗口天数 */
    const val WINDOW_DAYS = 14

    /** 最长连续的回溯天数 */
    const val LOOKBACK_DAYS = 90

    /** 完成比例上限（超出目标的部分只用于文案，不拉高比例） */
    const val MAX_RATIO = 1.5f

    /** 窗口天数上限（防御异常入参） */
    private const val MAX_WINDOW_DAYS = 366

    /** 回溯天数上限 */
    private const val MAX_LOOKBACK_DAYS = 730

    /**
     * 计算单个计划的投入画像。
     *
     * @param planStart 计划起始日 0 点时间戳
     * @param planEnd 计划截止日 0 点时间戳
     * @param goalMinutes 每日目标分钟数
     * @param dailySeconds 逐日累计秒数，key 为当天 0 点时间戳
     * @param today 今天 0 点时间戳
     * @param windowDays 展示窗口天数，默认 [WINDOW_DAYS]
     * @param lookbackDays 最长连续的回溯天数，默认 [LOOKBACK_DAYS]
     * @return 计划投入画像；计划还没开始时各字段为零值
     */
    fun of(
        planStart: Long,
        planEnd: Long,
        goalMinutes: Int,
        dailySeconds: Map<Long, Long>,
        today: Long,
        windowDays: Int = WINDOW_DAYS,
        lookbackDays: Int = LOOKBACK_DAYS
    ): PlanInsight {
        val safeGoal = goalMinutes.coerceAtLeast(0)
        val goalSeconds = safeGoal * 60L
        val window = windowDays.coerceIn(1, MAX_WINDOW_DAYS)
        val lookback = lookbackDays.coerceIn(window, MAX_LOOKBACK_DAYS)
        val todayStart = DateUtils.startOfDay(today)
        // 计划已结束时把窗口右端锚到最后一天，仍能看到它最后的积累
        val anchor = minOf(todayStart, planEnd)
        if (planStart > anchor) {
            return PlanInsight(goalMinutes = safeGoal, activeToday = false)
        }

        val windowStart = maxOf(planStart, DateUtils.addDays(anchor, -(window - 1)))
        val days = ArrayList<PlanDayPoint>(window)
        var cursor = windowStart
        while (cursor <= anchor) {
            days += PlanDayPoint(
                date = cursor,
                seconds = (dailySeconds[cursor] ?: 0L).coerceAtLeast(0L),
                goalSeconds = goalSeconds
            )
            cursor = DateUtils.addDays(cursor, 1)
        }

        /** 某天是否达标 */
        fun achievedOn(day: Long): Boolean {
            if (goalSeconds <= 0) return false
            return (dailySeconds[day] ?: 0L) >= goalSeconds
        }

        // 连续达标：今天没达标时从昨天数（今天还有机会），计划结束后不宽容
        var streak = 0
        if (goalSeconds > 0) {
            var start = anchor
            if (!achievedOn(anchor) && anchor == todayStart) {
                start = DateUtils.addDays(anchor, -1)
            }
            var day = start
            while (day >= planStart) {
                if (!achievedOn(day)) break
                streak++
                day = DateUtils.addDays(day, -1)
            }
        }

        // 最长连续：回溯窗口可能比展示窗口更长
        var best = 0
        var run = 0
        var day = maxOf(planStart, DateUtils.addDays(anchor, -(lookback - 1)))
        while (day <= anchor) {
            if (achievedOn(day)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            day = DateUtils.addDays(day, 1)
        }

        val total = days.sumOf { it.seconds }
        val active = days.count { it.seconds > 0 }
        val achieved = days.count { it.achieved }
        val activeToday = todayStart in planStart..planEnd
        return PlanInsight(
            days = days,
            goalMinutes = safeGoal,
            streak = streak,
            bestStreak = best,
            totalSeconds = total,
            activeDays = active,
            achievedDays = achieved,
            averageSeconds = if (active > 0) total / active else 0L,
            activeToday = activeToday
        )
    }

    /**
     * 把秒数格式化成「x 小时 y 分 / y 分钟」的短文案。
     *
     * @param seconds 秒数
     * @return 展示文案
     */
    fun durationText(seconds: Long): String {
        if (seconds <= 0) return "0 分钟"
        val totalMinutes = seconds / 60L
        if (totalMinutes <= 0) return "不足 1 分钟"
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours <= 0 -> "$minutes 分钟"
            minutes == 0L -> "$hours 小时"
            else -> "$hours 小时 $minutes 分"
        }
    }

    /**
     * 秒数向上取整为分钟：还差 30 秒也算「还差 1 分钟」，避免显示成 0。
     *
     * @param seconds 秒数
     * @return 分钟数
     */
    fun ceilMinutes(seconds: Long): Int {
        if (seconds <= 0) return 0
        return ((seconds + 59L) / 60L).toInt()
    }
}
