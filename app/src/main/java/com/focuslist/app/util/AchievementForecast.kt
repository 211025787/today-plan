package com.focuslist.app.util

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 达成预测的类型。
 *
 * 不同类型对应不同的解释方式，界面据此换配色与措辞，
 * 而不是把所有成就都硬套成「还需要 N 天」。
 */
enum class ForecastKind {
    /** 已点亮 */
    UNLOCKED,

    /** 进度已满但还没落库，下次结算就会点亮 */
    READY,

    /** 累积型指标：按近期节奏估出一个天数 */
    PACE,

    /** 累积型指标：近期没有新的积累 */
    STALLED,

    /** 峰值型指标（单日最高）：只能靠某一天冲高，无法用日均推算 */
    PEAK,

    /** 连续型指标：需要一天不落地连着来 */
    STREAK,

    /** 近期数据缺失，无法估算 */
    UNKNOWN
}

/**
 * 单个成就的达成预测结果。
 *
 * @property kind 预测类型
 * @property daysLeft 预估还需要几天（仅 [ForecastKind.PACE] 有意义，其余为 0）
 * @property headline 主文案，一句话给结论
 * @property detail 辅助文案，说明依据
 */
data class Forecast(
    val kind: ForecastKind,
    val daysLeft: Int = 0,
    val headline: String = "",
    val detail: String = ""
)

/**
 * 成就达成预测：不依赖 Android / Room / Compose，可在 JVM 单测里直接跑。
 *
 * 设计约定（都是刻意的取舍，改之前先读一遍）：
 * - **累积型指标**（累计分钟 / 番茄数 / 活跃天数 / 时段分钟 / 周末分钟）才做天数推算，
 *   用「近 [WINDOW_DAYS] 天的日均增量」外推；窗口**不含今天**，避免半天数据把节奏拉低。
 * - **峰值型**（单日最高）与**连续型**（最长连续）不给天数——前者取决于某一天的爆发，
 *   后者取决于不中断，用日均算出来一定是错的，只给「还差多少」的实话。
 * - 已解锁的成就不再预测，只回答「什么时候点亮的」。
 * - 预测只是参考：数据被删、记录被清空都会让节奏变化，文案里不承诺具体日期。
 */
object AchievementForecasts {

    /** 估算节奏用的默认窗口天数 */
    const val WINDOW_DAYS = 14

    /** 超过这个天数就不再给具体数字，改说「一年以上」 */
    const val LONG_ETA_DAYS = 365

    /**
     * 计算单个成就的达成预测。
     *
     * @param state 成就实时状态
     * @param recent 近 [windowDays] 天的指标快照（不含今天）；null 表示暂无近期数据
     * @param windowDays 窗口天数，非正数按 [WINDOW_DAYS] 处理
     * @param now 当前时刻（毫秒），用于「几天前点亮」的口径
     * @return 预测结果
     */
    fun of(
        state: AchievementState,
        recent: AchievementStats?,
        windowDays: Int = WINDOW_DAYS,
        now: Long = System.currentTimeMillis()
    ): Forecast {
        val def = state.def
        val unit = Achievements.unitOf(def.metric)

        if (state.unlocked) {
            val days = daysSince(state.unlockedAt, now)
            return Forecast(
                kind = ForecastKind.UNLOCKED,
                headline = "点亮于 ${Achievements.unlockDateText(state.unlockedAt)}",
                detail = if (days <= 0) "今天点亮" else "$days 天前点亮"
            )
        }

        val remaining = (def.target - state.current).coerceAtLeast(0)
        if (remaining == 0) {
            return Forecast(
                kind = ForecastKind.READY,
                headline = "条件已满足，等待结算",
                detail = "下次刷新统计时就会点亮"
            )
        }

        val days = if (windowDays > 0) windowDays else WINDOW_DAYS
        if (recent == null) {
            return Forecast(
                kind = ForecastKind.UNKNOWN,
                headline = "还差 $remaining $unit",
                detail = "多积累几天后再来看估算"
            )
        }

        return when (def.metric) {
            AchievementMetric.BEST_DAY -> Forecast(
                kind = ForecastKind.PEAK,
                headline = "需要有一天专注满 ${def.target} 分钟",
                detail = "近 $days 天最好的单日 ${recent.bestDayMinutes} 分钟，还差 $remaining 分钟"
            )

            AchievementMetric.LONGEST_STREAK -> Forecast(
                kind = ForecastKind.STREAK,
                headline = "需要连续 ${def.target} 天不中断",
                detail = "近 $days 天最长连续 ${recent.longestStreak} 天，还差 $remaining 天"
            )

            AchievementMetric.ACTIVE_DAYS -> {
                // 活跃天数是「有记录的天数」，用窗口内的出勤率外推，不能按分钟算
                val active = recent.activeDays
                if (active <= 0) {
                    stalled(days)
                } else {
                    val need = ceilDays(remaining.toLong() * days, active.toLong())
                    pace(need, "近 $days 天有 $active 天专注，还差 $remaining 天")
                }
            }

            else -> {
                val gained = Achievements.valueOf(recent, def.metric)
                if (gained <= 0) {
                    stalled(days)
                } else {
                    // 用整数运算做 ceil(remaining * days / gained)：
                    // 先算日均再除会因浮点误差把「正好 70 天」算成 71 天
                    val need = ceilDays(remaining.toLong() * days, gained.toLong())
                    val rate = gained.toFloat() / days
                    pace(need, "近 $days 天日均 ${rateText(rate)} $unit，还差 $remaining $unit")
                }
            }
        }
    }

    /**
     * 总览里的一行激励文案：用预测替换「还差多少」，把节奏一并说清楚。
     *
     * 例如「距离「十小时」还差 300 分钟 · 约 5 天」。
     * 没有节奏信息时不硬塞后缀，只保留原本的差值文案。
     *
     * @param state 目标成就
     * @param forecast 该成就的预测
     * @return 一行文案
     */
    fun nextLine(state: AchievementState, forecast: Forecast): String {
        val base = "距离「${state.def.title}」${Achievements.remainingText(state)}"
        return when (forecast.kind) {
            ForecastKind.PACE -> "$base · 约 ${forecast.daysLeft} 天"
            // 差值已经是 0，再说「距离…0 分钟」很别扭，直接报即将点亮
            ForecastKind.READY -> "「${state.def.title}」即将点亮"
            else -> base
        }
    }

    /**
     * 指标口径说明：详情弹层里回答「这个数字怎么算出来的」。
     *
     * @param metric 指标类型
     * @return 一句说明
     */
    fun metricHint(metric: AchievementMetric): String {
        return when (metric) {
            AchievementMetric.TOTAL_MINUTES -> "累计完整番茄与秒表的专注时长"
            AchievementMetric.TOTAL_POMOS -> "每完成一个番茄记 1 个"
            AchievementMetric.ACTIVE_DAYS -> "当天有专注记录就算一天"
            AchievementMetric.LONGEST_STREAK -> "连续的自然日，中断一天重新计数"
            AchievementMetric.BEST_DAY -> "历史上单日专注最多的一天"
            AchievementMetric.EARLY_MINUTES -> "清晨 5:00-9:00 的专注时长"
            AchievementMetric.NIGHT_MINUTES -> "深夜 21:00-24:00 的专注时长"
            AchievementMetric.WEEKEND_MINUTES -> "周六与周日的专注时长"
        }
    }

    /**
     * 日均增量文案：小于 1 时保留两位小数，避免「日均 0 个」这种抹平。
     *
     * @param rate 每天增量
     * @return 数字文案，如「42」「0.7」「0.07」
     */
    fun rateText(rate: Float): String {
        if (rate <= 0f) {
            return "0"
        }
        if (rate >= 10f) {
            return rate.roundToInt().toString()
        }
        val scaled = (rate * 100f).roundToInt()
        return when {
            scaled % 100 == 0 -> (scaled / 100).toString()
            scaled % 10 == 0 -> String.format(Locale.CHINA, "%.1f", scaled / 100f)
            else -> String.format(Locale.CHINA, "%.2f", scaled / 100f)
        }
    }

    /**
     * 从解锁时刻到现在的自然日差。
     *
     * 用毫秒差取整即可：「3 天前点亮」只需要量级正确，
     * 不必为了跨时区的边界去做日历换算。
     *
     * @param at 解锁时刻（毫秒）
     * @param now 当前时刻（毫秒）
     * @return 天数；时刻非法或未来时间返回 0
     */
    fun daysSince(at: Long, now: Long): Int {
        if (at <= 0L || now <= at) {
            return 0
        }
        return ((now - at) / DAY_MILLIS).toInt()
    }

    /** 一天毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * 组装「按节奏推进」的文案。
     *
     * @param days 预估天数
     * @param detail 依据说明
     * @return 预测结果
     */
    private fun pace(days: Int, detail: String): Forecast {
        val headline = when {
            days <= 1 -> "按最近的节奏，明天就能点亮"
            days <= LONG_ETA_DAYS -> "按最近的节奏，约 $days 天后点亮"
            else -> "按最近的节奏，还要一年以上"
        }
        return Forecast(kind = ForecastKind.PACE, daysLeft = days, headline = headline, detail = detail)
    }

    /**
     * 近期没有新积累时的文案。
     *
     * @param days 窗口天数
     * @return 预测结果
     */
    private fun stalled(days: Int): Forecast {
        return Forecast(
            kind = ForecastKind.STALLED,
            headline = "近 $days 天没有新的积累",
            detail = "恢复专注就会继续往前走"
        )
    }

    /**
     * 整数版向上取整除法：`ceil(numerator / denominator)`，全程不碰浮点。
     *
     * 结果夹到 1..9999，避免脏数据算出天文数字。
     *
     * @param numerator 被除数（非负）
     * @param denominator 除数（正数）
     * @return 至少 1 的整数天数
     */
    private fun ceilDays(numerator: Long, denominator: Long): Int {
        if (denominator <= 0L || numerator <= 0L) {
            return 1
        }
        val days = (numerator + denominator - 1L) / denominator
        return days.coerceIn(1L, 9999L).toInt()
    }
}
