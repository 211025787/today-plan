package com.focuslist.app.util

/**
 * 收工小结里的单个计划。
 *
 * @property id 计划 ID
 * @property title 计划名称
 * @property icon 图标 emoji
 * @property minutes 今天投入分钟数
 * @property goalMinutes 每日目标分钟数，非正数表示没设目标
 * @property running 是否仍在计时
 */
data class WrapPlan(
    val id: Long,
    val title: String,
    val icon: String = "🎯",
    val minutes: Int = 0,
    val goalMinutes: Int = 0,
    val running: Boolean = false
) {

    /** 是否达标 */
    val achieved: Boolean get() = goalMinutes > 0 && minutes >= goalMinutes

    /** 还差多少分钟达标 */
    val remainMinutes: Int get() = (goalMinutes - minutes).coerceAtLeast(0)
}

/**
 * 今天的收工小结。
 *
 * @property rows 逐计划投入，按分钟数从多到少
 * @property totalMinutes 今天总投入分钟数
 * @property achievedCount 达标计划数
 * @property planCount 今天的计划数
 * @property runningCount 仍在计时的计划数
 * @property headline 主文案
 * @property detail 副文案
 * @property closing 结语
 * @property suggest 是否值得在界面上给出「收工」入口
 */
data class DayWrap(
    val rows: List<WrapPlan> = emptyList(),
    val totalMinutes: Int = 0,
    val achievedCount: Int = 0,
    val planCount: Int = 0,
    val runningCount: Int = 0,
    val headline: String = "",
    val detail: String = "",
    val closing: String = "",
    val suggest: Boolean = false
) {

    /** 是否所有计划都达标 */
    val allAchieved: Boolean get() = planCount > 0 && achievedCount == planCount

    /** 今天是否完全没有投入 */
    val empty: Boolean get() = totalMinutes <= 0
}

/**
 * 收工小结：把今天的投入收成一张可以「合上」的卡片。
 *
 * 存在的理由有两件事：
 * 1. **忘记暂停**——多个并行秒表跑了一整天，没有一键停下来的地方，数据会被算虚；
 * 2. **缺一个结束动作**——大多数时间管理工具只负责催你开始，不负责告诉你「今天可以了」。
 *
 * 设计约定：
 * - 21:00 之后，或还有计时在跑，才在界面上露出「收工」入口，白天不打扰；
 * - 结语只在全部达标时鼓励收工，否则给一句「还差多少」的实话，最晚的一句不催；
 * - 逐计划按投入从多到少排，让「今天主要花在哪」一眼可见。
 */
object DayWraps {

    /** 收工建议的时间线：21:00 */
    const val LATE_MINUTE = 21 * 60

    /**
     * 生成小结。
     *
     * @param plans 今天的计划（含各自的投入与计时状态）
     * @param nowMinutes 当前时刻从 0 点起的分钟数，越界会夹到 0..1439
     * @return 收工小结
     */
    fun of(plans: List<WrapPlan>, nowMinutes: Int): DayWrap {
        val minute = nowMinutes.coerceIn(0, 24 * 60 - 1)
        val rows = plans.sortedWith(
            compareByDescending<WrapPlan> { it.minutes }.thenBy { it.title }
        )
        val total = plans.sumOf { it.minutes }
        val achieved = plans.count { it.achieved }
        val running = plans.count { it.running }

        val headline = when {
            plans.isEmpty() -> "今天没有计划"
            total <= 0 -> "今天还没有投入"
            else -> "今天 ${WeekStrip.durationText(total)}"
        }

        val detail = when {
            plans.isEmpty() -> "明天可以再安排"
            running > 0 -> "达标 $achieved/${plans.size} 个计划 · $running 个还在计时"
            else -> "达标 $achieved/${plans.size} 个计划"
        }

        // 没设目标的计划不参与「还差多少」，否则会出现「还差 0 分钟」这种废话
        val pending = plans.filter { it.goalMinutes > 0 && !it.achieved }
            .minByOrNull { it.remainMinutes }
        val closing = when {
            plans.isEmpty() -> "明天再安排"
            plans.all { it.achieved } && total > 0 -> "全部达标，可以安心收工了"
            total <= 0 -> "今天先到这里，明天再来"
            minute >= LATE_MINUTE -> "今天先到这里，明天继续"
            pending != null -> "「${pending.title}」还差 ${pending.remainMinutes} 分钟，或者就到这儿"
            else -> "今天到这里就好"
        }

        val suggest = running > 0 || (minute >= LATE_MINUTE && total > 0)
        return DayWrap(
            rows = rows,
            totalMinutes = total,
            achievedCount = achieved,
            planCount = plans.size,
            runningCount = running,
            headline = headline,
            detail = detail,
            closing = closing,
            suggest = suggest
        )
    }
}
