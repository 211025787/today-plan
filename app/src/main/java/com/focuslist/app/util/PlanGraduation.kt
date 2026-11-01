package com.focuslist.app.util

/**
 * 结业结算所需的计划信息（不含 Room 依赖，便于单测）。
 *
 * @property id 计划 ID
 * @property title 计划名称
 * @property icon 图标 emoji
 * @property colorHex 主题色
 * @property start 起始日（0 点）
 * @property end 截止日（0 点，含）
 * @property dailyGoalMinutes 每日目标分钟数
 */
data class GraduationPlan(
    val id: Long,
    val title: String,
    val icon: String = "🎯",
    val colorHex: String = "#4772FA",
    val start: Long,
    val end: Long,
    val dailyGoalMinutes: Int = 0
)

/**
 * 一个已结束计划的成绩单。
 *
 * @property plan 计划信息
 * @property totalDays 计划总天数（含首尾）
 * @property totalMinutes 累计投入分钟数
 * @property completionPercent 完成度百分比（累计 / 目标总量，可超 100）
 * @property verdict 一句评语
 * @property detail 一行补充说明
 */
data class Graduation(
    val plan: GraduationPlan,
    val totalDays: Int,
    val totalMinutes: Int,
    val completionPercent: Int,
    val verdict: String,
    val detail: String
)

/**
 * 计划结业：计划到期后给它一个交代。
 *
 * 之前计划是一声不响地消失的——首页看不到它，也没有任何「结束了」的提示，
 * 用户很容易以为是数据丢了。这里补上收尾：一张成绩单 + 两个选择（归档 / 再来一版）。
 *
 * 口径：
 * - **只看最近 30 天结束的计划**，再久的就让它安静待着，不在统计页堆历史；
 * - 完成度 = 累计投入 / （总天数 × 每日目标），可以超过 100%，超额完成也照实说；
 * - 「再来一版」= 同配置从今天重新起算，并**把旧计划归档**，否则旧计划会一直挂在结业列表里。
 */
object PlanGraduations {

    /** 结业列表的回看天数 */
    const val RECENT_DAYS = 30

    /** 一天毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * 挑出最近结束、还没归档的计划，按结束日从近到远。
     *
     * @param plans 全部未归档计划
     * @param today 今天（任意时刻）
     * @param days 回看天数
     * @return 已结束的计划，最多 [days] 天内
     */
    fun endedRecently(
        plans: List<GraduationPlan>,
        today: Long,
        days: Int = RECENT_DAYS
    ): List<GraduationPlan> {
        val todayStart = DateUtils.startOfDay(today)
        val safeDays = if (days > 0) days else RECENT_DAYS
        return plans
            .filter { plan ->
                val end = DateUtils.startOfDay(plan.end)
                end < todayStart && todayStart - end <= safeDays * DAY_MILLIS
            }
            .sortedByDescending { DateUtils.startOfDay(it.end) }
    }

    /**
     * 生成成绩单。
     *
     * @param plan 已结束的计划
     * @param totalSeconds 该计划的累计投入秒数
     * @return 成绩单
     */
    fun report(plan: GraduationPlan, totalSeconds: Long): Graduation {
        val totalDays = (PlanPaces.daysBetween(plan.start, plan.end) + 1).coerceAtLeast(1)
        val targetSeconds = totalDays.toLong() * plan.dailyGoalMinutes.coerceAtLeast(0) * 60L
        val seconds = totalSeconds.coerceAtLeast(0L)
        val percent = if (targetSeconds <= 0L) {
            0
        } else {
            ((seconds.toDouble() / targetSeconds.toDouble()) * 100.0).toInt()
        }
        val minutes = (seconds / 60L).toInt()
        return Graduation(
            plan = plan,
            totalDays = totalDays,
            totalMinutes = minutes,
            completionPercent = percent,
            verdict = verdictOf(percent),
            detail = "共 $totalDays 天 · 累计 ${WeekStrip.durationText(minutes)}"
        )
    }

    /**
     * 评语：只按完成度分档，不评判努力程度。
     *
     * @param percent 完成度百分比
     * @return 一句评语
     */
    fun verdictOf(percent: Int): String {
        return when {
            percent <= 0 -> "没有留下记录，但计划曾经被你认真安排过"
            percent >= 100 -> "完成度 $percent%，说到做到"
            percent >= 80 -> "完成度 $percent%，只差一点点"
            percent >= 50 -> "完成度 $percent%，坚持过就是收获"
            else -> "完成度 $percent%，至少你开始过"
        }
    }

    /**
     * 再来一版：同配置、从今天重新起算、天数不变；返回的计划 ID 为 0（等待插入）。
     *
     * @param plan 已结束的计划
     * @param today 今天（任意时刻）
     * @return 新计划信息
     */
    fun renewed(plan: GraduationPlan, today: Long): GraduationPlan {
        val totalDays = (PlanPaces.daysBetween(plan.start, plan.end) + 1).coerceAtLeast(1)
        val start = DateUtils.startOfDay(today)
        return plan.copy(
            id = 0L,
            start = start,
            end = DateUtils.addDays(start, totalDays - 1)
        )
    }
}
