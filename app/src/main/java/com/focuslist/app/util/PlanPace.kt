package com.focuslist.app.util

/**
 * 计划整体节奏的状态。
 *
 * 只用于决定文案口径，不参与任何计算；界面据此换配色。
 */
enum class PaceStatus {
    /** 今天早于计划开始日 */
    NOT_STARTED,

    /** 刚开始没几天，样本太少，不评价 */
    EARLY,

    /** 投入进度明显超前 */
    AHEAD,

    /** 按节奏推进 */
    ON_TRACK,

    /** 略微落后 */
    SLIGHTLY_BEHIND,

    /** 明显落后 */
    BEHIND,

    /** 计划已结束且达成 */
    FINISHED_DONE,

    /** 计划已结束但未达成 */
    FINISHED_MISSED
}

/**
 * 计划整体节奏：把「时间过去了多少」与「目标完成了多少」放在一起比。
 *
 * 这是 14 天窗口之外的另一个视角——近两周很努力，但整个计划可能仍然落后；
 * 「落后 N 天」比一个百分比更接近直觉，所以它是这组数据的核心输出。
 *
 * @property totalDays 计划总天数（含首尾）
 * @property elapsedDays 已经过去的天数（含今天，最多等于总天数）
 * @property remainingDays 今天之后还剩的天数
 * @property expectedPercent 时间进度 0-100
 * @property actualPercent 投入进度 0-100+（超额完成会大于 100，不夹取，方便文案说「超出」）
 * @property behindDays 落后天数，按当前每日目标折算；不落后为 0
 * @property requiredPerDayMinutes 想在剩余天数内完成目标，每天需要投入的分钟数
 * @property status 状态
 */
data class PlanPace(
    val totalDays: Int,
    val elapsedDays: Int,
    val remainingDays: Int,
    val expectedPercent: Int,
    val actualPercent: Int,
    val behindDays: Int,
    val requiredPerDayMinutes: Int,
    val status: PaceStatus
) {

    /** 时间进度条比例 0-1 */
    val timeFraction: Float get() = (expectedPercent / 100f).coerceIn(0f, 1f)

    /** 投入进度条比例 0-1（超额封顶，条不会画出去） */
    val actualFraction: Float get() = (actualPercent / 100f).coerceIn(0f, 1f)

    /** 一句话结论 */
    val headline: String
        get() = when (status) {
            PaceStatus.NOT_STARTED -> "计划还没开始"
            PaceStatus.EARLY -> "刚开始，先跑几天再看节奏"
            PaceStatus.AHEAD -> "比时间进度快 ${(actualPercent - expectedPercent).coerceAtLeast(1)}%"
            PaceStatus.ON_TRACK -> "按节奏推进中"
            PaceStatus.SLIGHTLY_BEHIND -> "落后约 $behindDays 天"
            PaceStatus.BEHIND -> "落后约 $behindDays 天，需要加把劲"
            PaceStatus.FINISHED_DONE -> "计划已结束，完成度 $actualPercent%"
            PaceStatus.FINISHED_MISSED -> "计划已结束，完成度 $actualPercent%"
        }

    /** 一行进度说明，如「时间 68% · 投入 41%」 */
    val progressText: String get() = "时间 $expectedPercent% · 投入 $actualPercent%"

    /** 补充说明：还剩多少天、每天需要多少；最后一天不再说「还剩 0 天」 */
    val detail: String
        get() = when (status) {
            PaceStatus.NOT_STARTED -> "共 $totalDays 天，开始后这里会显示整体节奏"
            PaceStatus.FINISHED_DONE, PaceStatus.FINISHED_MISSED -> "共 $totalDays 天，已结束"
            PaceStatus.AHEAD, PaceStatus.ON_TRACK -> when {
                requiredPerDayMinutes <= 0 -> "整体目标已完成，保持就好"
                remainingDays > 0 -> "还剩 $remainingDays 天，每天需 $requiredPerDayMinutes 分钟"
                else -> "今天是最后一天，还需 $requiredPerDayMinutes 分钟"
            }

            else -> if (remainingDays > 0) {
                "还剩 $remainingDays 天，每天需 $requiredPerDayMinutes 分钟才能按时完成"
            } else {
                "今天是最后一天，还需 $requiredPerDayMinutes 分钟才能完成"
            }
        }
}

/**
 * 计划整体节奏的计算工具：纯 Kotlin，不依赖 Android，可直接在 JVM 单测里跑。
 *
 * 口径说明（改之前先读）：
 * - **总天数按首尾都算**：11/1 到 11/30 是 30 天；
 * - **已过天数含今天**：今天还有机会补，所以不把今天算成「已经失败的一天」；
 * - **目标总量 = 总天数 × 每日目标**，不额外存字段——计划本来就是「每天做一点」；
 * - **落后天数向上取整**：还差 1 分钟也算落后 1 天，避免出现「落后 0 天」这种自我安慰；
 * - 计划只有 3 天以内时不设「刚开始」的缓冲期，否则永远看不到评价。
 */
object PlanPaces {

    /** 一天的毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** 需要缓冲期的最短计划天数：短于此的计划第一天就开始评价 */
    private const val EARLY_MIN_TOTAL_DAYS = 7

    /** 缓冲期的已过天数上限 */
    private const val EARLY_MAX_ELAPSED_DAYS = 3

    /**
     * 计算整体节奏。
     *
     * @param planStart 计划起始日（0 点，含）
     * @param planEnd 计划截止日（0 点，含）
     * @param goalMinutes 每日目标分钟数，非正数表示不设目标
     * @param totalSeconds 计划开始至今的累计投入秒数
     * @param today 今天（任意时刻，内部归一到 0 点）
     * @return 节奏快照
     */
    fun of(
        planStart: Long,
        planEnd: Long,
        goalMinutes: Int,
        totalSeconds: Long,
        today: Long
    ): PlanPace {
        val start = DateUtils.startOfDay(planStart)
        val end = DateUtils.startOfDay(planEnd)
        val todayStart = DateUtils.startOfDay(today)
        val totalDays = (daysBetween(start, end) + 1).coerceAtLeast(1)
        val goal = goalMinutes.coerceAtLeast(0)
        val actualSeconds = totalSeconds.coerceAtLeast(0L)

        if (todayStart < start) {
            return PlanPace(
                totalDays = totalDays,
                elapsedDays = 0,
                remainingDays = totalDays,
                expectedPercent = 0,
                actualPercent = 0,
                behindDays = 0,
                requiredPerDayMinutes = goal,
                status = PaceStatus.NOT_STARTED
            )
        }

        val lastActiveDay = minOf(todayStart, end)
        val elapsedDays = (daysBetween(start, lastActiveDay) + 1).coerceIn(1, totalDays)
        val remainingDays = totalDays - elapsedDays
        val totalGoalSeconds = totalDays.toLong() * goal * 60L
        val expectedSeconds = elapsedDays.toLong() * goal * 60L
        val actualPercent = percentOf(actualSeconds, totalGoalSeconds)
        // 时间进度只看日子，与目标大小无关：不设目标也能看出「过了多久」
        val expectedPercent = elapsedDays * 100 / totalDays
        val behindSeconds = (expectedSeconds - actualSeconds).coerceAtLeast(0L)
        val behindDays = if (goal > 0) {
            ceilDays(behindSeconds, goal * 60L)
        } else {
            0
        }
        val remainingGoalSeconds = (totalGoalSeconds - actualSeconds).coerceAtLeast(0L)
        // 最后一天（含今天的已过天数正好等于总天数）时，剩余目标全部压在今天
        val requiredPerDay = when {
            remainingGoalSeconds <= 0L -> 0
            remainingDays > 0 -> ceilMinutes(remainingGoalSeconds, remainingDays)
            else -> ceilMinutes(remainingGoalSeconds, 1)
        }

        val status = when {
            todayStart > end -> if (actualSeconds >= totalGoalSeconds && totalGoalSeconds > 0L) {
                PaceStatus.FINISHED_DONE
            } else {
                PaceStatus.FINISHED_MISSED
            }

            goal <= 0 -> PaceStatus.ON_TRACK

            elapsedDays < EARLY_MAX_ELAPSED_DAYS && totalDays >= EARLY_MIN_TOTAL_DAYS -> PaceStatus.EARLY

            expectedSeconds <= 0L -> PaceStatus.ON_TRACK

            actualSeconds * 10 >= expectedSeconds * 11 -> PaceStatus.AHEAD

            actualSeconds * 10 >= expectedSeconds * 9 -> PaceStatus.ON_TRACK

            actualSeconds * 100 >= expectedSeconds * 65 -> PaceStatus.SLIGHTLY_BEHIND

            else -> PaceStatus.BEHIND
        }

        return PlanPace(
            totalDays = totalDays,
            elapsedDays = elapsedDays,
            remainingDays = remainingDays,
            expectedPercent = expectedPercent,
            actualPercent = actualPercent,
            behindDays = behindDays,
            requiredPerDayMinutes = requiredPerDay,
            status = status
        )
    }

    /**
     * 日卡片角标文案：**只有落后才给**，其余情况返回 null。
     *
     * 卡片本来就小，按节奏推进是常态，不需要每张卡都挂一句话；
     * 角标一旦出现就说明「这个计划欠账了」，反而更有信息量。
     *
     * @param pace 节奏快照
     * @return 角标文案，如「落后 2 天」；不需要提示时为 null
     */
    fun chipText(pace: PlanPace): String? {
        return when (pace.status) {
            PaceStatus.BEHIND, PaceStatus.SLIGHTLY_BEHIND -> "落后 ${pace.behindDays} 天"
            else -> null
        }
    }

    /**
     * 两个 0 点时间戳之间的天数差，恒非负。
     *
     * @param from 起始 0 点
     * @param to 结束 0 点
     * @return 天数差
     */
    fun daysBetween(from: Long, to: Long): Int {
        return ((to - from) / DAY_MILLIS).toInt()
    }

    /**
     * 百分比：分母非正数时返回 0，结果不封顶（超额完成会大于 100）。
     *
     * @param value 分子
     * @param total 分母
     * @return 整数百分比
     */
    private fun percentOf(value: Long, total: Long): Int {
        if (total <= 0L) {
            return 0
        }
        return ((value.toDouble() / total.toDouble()) * 100.0).toInt()
    }

    /**
     * 向上取整为天数：差 1 分钟也算 1 天。
     *
     * @param seconds 差额秒数
     * @param perDaySeconds 每日目标秒数
     * @return 天数，至少 1
     */
    private fun ceilDays(seconds: Long, perDaySeconds: Long): Int {
        if (seconds <= 0L || perDaySeconds <= 0L) {
            return 0
        }
        return ((seconds + perDaySeconds - 1L) / perDaySeconds).toInt()
    }

    /**
     * 剩余每天需要多少分钟（向上取整），至少 1 分钟。
     *
     * @param remainingSeconds 剩余目标秒数
     * @param remainingDays 剩余天数
     * @return 分钟数
     */
    private fun ceilMinutes(remainingSeconds: Long, remainingDays: Int): Int {
        if (remainingSeconds <= 0L || remainingDays <= 0) {
            return 0
        }
        val perDaySeconds = (remainingSeconds + remainingDays - 1L) / remainingDays
        val minutes = ((perDaySeconds + 59L) / 60L).toInt()
        return minutes.coerceAtLeast(1)
    }
}
