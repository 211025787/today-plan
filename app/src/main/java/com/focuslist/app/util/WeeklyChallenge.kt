package com.focuslist.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 周挑战的类型。
 *
 * 三类分别对应「坚持」「强度」「爆发」，覆盖面固定，只有目标值每周轮换。
 */
enum class ChallengeKind {
    /** 本周有专注的天数 */
    ACTIVE_DAYS,

    /** 本周累计专注分钟数 */
    TOTAL_MINUTES,

    /** 本周单日最高分钟数 */
    BEST_DAY
}

/**
 * 一条周挑战定义。
 *
 * @property id 稳定标识（含所在周的日期），用于把完成记录写进成就存储
 * @property kind 类型
 * @property emoji 图标
 * @property title 标题文案，如「本周专注 5 天」
 * @property target 目标值
 * @property unit 单位，「天」或「分钟」
 */
data class WeeklyChallenge(
    val id: String,
    val kind: ChallengeKind,
    val emoji: String,
    val title: String,
    val target: Int,
    val unit: String
)

/**
 * 一条周挑战的完成情况。
 *
 * @property challenge 挑战定义
 * @property current 当前值（未夹取，便于文案说「超出」）
 */
data class ChallengeProgress(
    val challenge: WeeklyChallenge,
    val current: Int
) {

    /** 是否已完成 */
    val done: Boolean get() = current >= challenge.target

    /** 完成百分比 0-100 */
    val percent: Int
        get() = if (challenge.target <= 0) {
            100
        } else {
            ((current.toFloat() / challenge.target.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }

    /** 进度文案，如「3 / 5 天」 */
    val progressText: String get() = "$current / ${challenge.target} ${challenge.unit}"

    /** 还差多少的文案；已完成时给「已达成」 */
    val remainingText: String
        get() = when {
            done -> "已达成"
            challenge.kind == ChallengeKind.BEST_DAY -> "还差 ${challenge.target - current} 分钟"
            else -> "还差 ${challenge.target - current} ${challenge.unit}"
        }
}

/**
 * 每周挑战：把「这周还差一点」变成三个具体的小目标。
 *
 * 与长期成就的分工很清楚——成就看的是「你这几年做了什么」，
 * 周挑战看的是「这周还有救吗」，周一自动换一批、周日晚上自然收尾。
 *
 * 设计约定：
 * - 三类目标固定（天数 / 累计 / 单日爆发），只有**目标值按周轮换**，
 *   否则每周随机换类型会让人看不懂规则；
 * - 轮换只依赖「周起点距 1970 年有多少周」，同一周在任何设备上算出来都一样；
 * - 进度全部来自本周 7 天的分钟数（[com.focuslist.app.data.repo.StatsSummary.weekDaily]），
 *   不额外查库，也不持久化进度——周一自然会归零。
 */
object WeeklyChallenges {

    /** 一天毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** 周起点：与全应用一致，周一 */
    fun weekStart(today: Long): Long {
        return DateUtils.startOfWeek(today)
    }

    /**
     * 周标识（用周一的日期），如「2026-10-26」。
     *
     * 用日期而不是 ISO 周号，是为了跨年也不会重号，调试时也能直接看懂。
     *
     * @param weekStart 周一 0 点
     * @return 周标识
     */
    fun weekKey(weekStart: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(weekStart))
    }

    /**
     * 周序号（自 1970 年起的第几周），只用于轮换目标值。
     *
     * @param weekStart 周一 0 点
     * @return 非负序号
     */
    fun weekIndex(weekStart: Long): Int {
        val weeks = weekStart / DAY_MILLIS / 7L
        return weeks.toInt()
    }

    /**
     * 本周的三条挑战（含目标轮换）。
     *
     * @param weekStart 周一 0 点
     * @return 固定顺序：坚持 → 强度 → 爆发
     */
    fun ofWeek(weekStart: Long): List<WeeklyChallenge> {
        val key = weekKey(weekStart)
        val variant = Math.floorMod(weekIndex(weekStart), 3)
        val activeDays = listOf(4, 5, 3)[variant]
        val totalMinutes = listOf(180, 240, 300)[variant]
        val bestDay = listOf(45, 60, 90)[variant]
        return listOf(
            WeeklyChallenge(
                id = "challenge_${key}_active_days",
                kind = ChallengeKind.ACTIVE_DAYS,
                emoji = "📅",
                title = "本周专注 $activeDays 天",
                target = activeDays,
                unit = "天"
            ),
            WeeklyChallenge(
                id = "challenge_${key}_total_minutes",
                kind = ChallengeKind.TOTAL_MINUTES,
                emoji = "⏳",
                title = "本周累计 ${WeekStrip.durationText(totalMinutes)}",
                target = totalMinutes,
                unit = "分钟"
            ),
            WeeklyChallenge(
                id = "challenge_${key}_best_day",
                kind = ChallengeKind.BEST_DAY,
                emoji = "🔥",
                title = "单日专注 $bestDay 分钟",
                target = bestDay,
                unit = "分钟"
            )
        )
    }

    /**
     * 计算本周三条挑战的进度。
     *
     * @param weekStart 周一 0 点
     * @param weekDaily 本周 7 天的分钟数（周一在前），长度不足按 0 补齐
     * @return 与 [ofWeek] 同序的进度列表
     */
    fun progress(weekStart: Long, weekDaily: List<Int>): List<ChallengeProgress> {
        val days = List(7) { index -> weekDaily.getOrElse(index) { 0 }.coerceAtLeast(0) }
        return ofWeek(weekStart).map { challenge ->
            val current = when (challenge.kind) {
                ChallengeKind.ACTIVE_DAYS -> days.count { it > 0 }
                ChallengeKind.TOTAL_MINUTES -> days.sum()
                ChallengeKind.BEST_DAY -> days.maxOrNull() ?: 0
            }
            ChallengeProgress(challenge = challenge, current = current)
        }
    }

    /**
     * 本周还剩几天（含今天）。
     *
     * @param today 今天 0 点
     * @param weekStart 周一 0 点
     * @return 1-7
     */
    fun daysLeft(today: Long, weekStart: Long): Int {
        val index = ((DateUtils.startOfDay(today) - weekStart) / DAY_MILLIS).toInt()
        return (7 - index.coerceIn(0, 6))
    }

    /**
     * 一句话汇总，如「本周 2/3 个挑战完成」。
     *
     * @param list 进度列表
     * @return 汇总文案
     */
    fun summary(list: List<ChallengeProgress>): String {
        if (list.isEmpty()) {
            return ""
        }
        val done = list.count { it.done }
        return if (done == list.size) {
            "本周挑战全部完成，漂亮"
        } else {
            "本周 $done/${list.size} 个挑战完成"
        }
    }

    /**
     * 已完成的挑战 id，用于写入成就存储（幂等，重复调用不会重复记录）。
     *
     * @param list 进度列表
     * @return 已完成的 id
     */
    fun completedIds(list: List<ChallengeProgress>): List<String> {
        return list.filter { it.done }.map { it.challenge.id }
    }
}
