package com.focuslist.app.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 本周与上周同期的趋势方向。
 */
enum class WeekTrend {
    /** 比上周同期更专注 */
    UP,

    /** 比上周同期更少 */
    DOWN,

    /** 与上周同期持平 */
    FLAT
}

/**
 * 周回顾快照：本周（周一至今）与**上周同一区间段**的对比结果。
 *
 * 只统计已经过去的那些天，避免周一早上就拿本周 1 天去比上周完整 7 天，
 * 那样的「下降」没有意义，也会误导用户。
 *
 * @property thisMinutes 本周同期累计专注分钟
 * @property lastMinutes 上周同期累计专注分钟
 * @property elapsedDays 本周已经过去的天数（含今天），1~7
 * @property activeDays 本周同期有专注记录的天数
 * @property achievedDays 本周同期达标（达到每日目标）的天数
 * @property bestDayIndex 本周同期最佳单日的下标（周一 = 0），无记录时为 -1
 * @property bestDayMinutes 本周同期最佳单日的分钟数
 * @property averageMinutes 本周同期日均分钟数（按已过天数均摊）
 * @property goal 每日目标分钟数，非正数表示未设置
 */
data class WeekReview(
    val thisMinutes: Int = 0,
    val lastMinutes: Int = 0,
    val elapsedDays: Int = 0,
    val activeDays: Int = 0,
    val achievedDays: Int = 0,
    val bestDayIndex: Int = -1,
    val bestDayMinutes: Int = 0,
    val averageMinutes: Int = 0,
    val goal: Int = 0
) {

    /** 与上周同期的分钟差，正数表示更多 */
    val delta: Int get() = thisMinutes - lastMinutes

    /** 本周与上周同期都没有记录，用于展示更简洁的空状态 */
    val isEmpty: Boolean get() = thisMinutes <= 0 && lastMinutes <= 0

    /** 本周剩余天数（今天之后） */
    val remainingDays: Int get() = (WeeklyReview.DAYS - elapsedDays).coerceAtLeast(0)

    /** 趋势方向 */
    val trend: WeekTrend
        get() = when {
            delta > 0 -> WeekTrend.UP
            delta < 0 -> WeekTrend.DOWN
            else -> WeekTrend.FLAT
        }

    /** 按当前日均推算的整周分钟数；本周已结束或尚未开始时等于实际值 */
    val projectedMinutes: Int
        get() = if (elapsedDays in 1 until WeeklyReview.DAYS) {
            (thisMinutes.toFloat() / elapsedDays * WeeklyReview.DAYS).roundToInt()
        } else {
            thisMinutes
        }
}

/**
 * 周回顾纯计算工具：不依赖 Android / Room，可在 JVM 单元测试里直接跑。
 *
 * 输入是两个长度为 7 的数组（周一到周日），只取「已经过去的部分」做同期对比，
 * 因此周一早上不会出现「比上周少 83%」这种吓人的结论。
 */
object WeeklyReview {

    /** 一周天数 */
    const val DAYS = 7

    /** 星期文案，索引 0 对应周一 */
    private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /**
     * 生成本周回顾快照。
     *
     * @param thisWeek 本周 7 天的每日专注分钟（周一为下标 0）
     * @param lastWeek 上周 7 天的每日专注分钟（周一为下标 0）
     * @param todayIndex 今天是本周第几天（周一 = 0）；越界会被夹到 0~6
     * @param goal 每日目标分钟数；非正数时不做达标统计
     * @return 回顾快照
     */
    fun of(thisWeek: List<Int>, lastWeek: List<Int>, todayIndex: Int, goal: Int): WeekReview {
        val elapsed = (todayIndex + 1).coerceIn(1, DAYS)
        val thisSpan = (0 until elapsed).map { thisWeek.getOrElse(it) { 0 } }
        val lastSpan = (0 until elapsed).map { lastWeek.getOrElse(it) { 0 } }

        val thisTotal = thisSpan.sum()
        val best = thisSpan.maxOrNull() ?: 0
        return WeekReview(
            thisMinutes = thisTotal,
            lastMinutes = lastSpan.sum(),
            elapsedDays = elapsed,
            activeDays = thisSpan.count { it > 0 },
            achievedDays = if (goal > 0) thisSpan.count { it >= goal } else 0,
            bestDayIndex = if (best > 0) thisSpan.indexOf(best) else -1,
            bestDayMinutes = best,
            averageMinutes = thisTotal / elapsed,
            goal = goal
        )
    }

    /**
     * 把下标换算成星期文案。
     *
     * @param index 周一 = 0 … 周日 = 6
     * @return 星期文案；越界返回破折号
     */
    fun dayName(index: Int): String {
        return DAY_NAMES.getOrElse(index) { "—" }
    }

    /**
     * 计算占比百分比（四舍五入到整数）。
     *
     * @param part 部分值
     * @param base 基准值
     * @return 百分比；基准非正数时返回 0
     */
    fun percentOf(part: Int, base: Int): Int {
        if (base <= 0) {
            return 0
        }
        return (abs(part).toFloat() / base * 100f).roundToInt()
    }

    /**
     * 趋势徽标文案，例如 `↑ 33%`、`↓ 12%`、`持平`。
     *
     * @param review 回顾快照
     * @return 简短徽标
     */
    fun badgeText(review: WeekReview): String {
        if (review.lastMinutes <= 0) {
            return if (review.thisMinutes > 0) "新起点" else "未开始"
        }
        val percent = percentOf(review.delta, review.lastMinutes)
        return when (review.trend) {
            WeekTrend.UP -> "↑ $percent%"
            WeekTrend.DOWN -> "↓ $percent%"
            WeekTrend.FLAT -> "持平"
        }
    }

    /**
     * 对比文案，例如 `较上周同期多 62 分钟`。
     *
     * @param review 回顾快照
     * @return 一句话对比
     */
    fun deltaText(review: WeekReview): String {
        if (review.lastMinutes <= 0) {
            return if (review.thisMinutes > 0) "上周同期还没有记录" else "这周还没开始"
        }
        return when (review.trend) {
            WeekTrend.UP -> "较上周同期多 ${review.delta} 分钟"
            WeekTrend.DOWN -> "较上周同期少 ${-review.delta} 分钟"
            WeekTrend.FLAT -> "与上周同期持平"
        }
    }

    /**
     * 区间文案，例如 `周一至今 4 天` 或 `整周 7 天`。
     *
     * @param review 回顾快照
     * @return 统计区间说明
     */
    fun spanText(review: WeekReview): String {
        return if (review.elapsedDays >= DAYS) "整周 $DAYS 天" else "周一至今 ${review.elapsedDays} 天"
    }

    /**
     * 指标行文案：达标天数 + 最佳单日 + 日均。
     *
     * 未设置每日目标时，达标天数换成「活跃天数」，避免出现「达标 0/4 天」的假失败。
     *
     * @param review 回顾快照
     * @return 用 ` · ` 连接的一行指标
     */
    fun metricText(review: WeekReview): String {
        val parts = ArrayList<String>(3)
        parts.add(
            if (review.goal > 0) {
                "达标 ${review.achievedDays}/${review.elapsedDays} 天"
            } else {
                "活跃 ${review.activeDays}/${review.elapsedDays} 天"
            }
        )
        if (review.bestDayIndex >= 0) {
            parts.add("最佳 ${dayName(review.bestDayIndex)} ${review.bestDayMinutes} 分钟")
        }
        parts.add("日均 ${review.averageMinutes} 分钟")
        return parts.joinToString(" · ")
    }

    /**
     * 底部结论：按当前节奏推算本周总量。
     *
     * @param review 回顾快照
     * @return 一句话总结
     */
    fun projectionText(review: WeekReview): String {
        if (review.elapsedDays >= DAYS) {
            return "本周已完整记录，共 ${review.thisMinutes} 分钟"
        }
        if (review.thisMinutes <= 0) {
            return "本周还没有开始，随时可以启动第一个番茄"
        }
        val tail = if (review.remainingDays > 0) "（还剩 ${review.remainingDays} 天）" else ""
        return "按当前节奏，本周预计 ${review.projectedMinutes} 分钟$tail"
    }
}
