package com.focuslist.app.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 成就稀有度：仅用于界面配色与排序展示，不影响解锁条件。
 */
enum class AchievementTier {
    /** 铜牌：入门即可拿到 */
    BRONZE,

    /** 银牌：需要一定积累 */
    SILVER,

    /** 金牌：长期坚持的证明 */
    GOLD
}

/**
 * 成就计量的指标类型。
 */
enum class AchievementMetric {
    /** 累计专注分钟数 */
    TOTAL_MINUTES,

    /** 累计完成的番茄个数 */
    TOTAL_POMOS,

    /** 有专注记录的自然日天数 */
    ACTIVE_DAYS,

    /** 最长连续专注天数 */
    LONGEST_STREAK,

    /** 单日最高专注分钟数 */
    BEST_DAY,

    /** 清晨时段（5:00-9:00）累计分钟数 */
    EARLY_MINUTES,

    /** 深夜时段（21:00-24:00）累计分钟数 */
    NIGHT_MINUTES,

    /** 周末累计分钟数 */
    WEEKEND_MINUTES
}

/**
 * 成就定义（静态描述，不含进度）。
 *
 * @property id 稳定标识，也是持久化存储的 key
 * @property emoji 成就图标
 * @property title 成就名称
 * @property desc 达成条件的文案说明
 * @property metric 计量指标
 * @property target 达成阈值
 * @property tier 稀有度
 */
data class AchievementDef(
    val id: String,
    val emoji: String,
    val title: String,
    val desc: String,
    val metric: AchievementMetric,
    val target: Int,
    val tier: AchievementTier
)

/**
 * 成就计算所需的统计快照，全部取全量口径（不受一年窗口截断）。
 *
 * @property totalMinutes 累计专注分钟数
 * @property totalPomos 累计完成的番茄个数
 * @property activeDays 有专注记录的自然日天数
 * @property longestStreak 最长连续专注天数
 * @property bestDayMinutes 单日最高专注分钟数
 * @property earlyMinutes 清晨（5:00-9:00）累计专注分钟数
 * @property nightMinutes 深夜（21:00-24:00）累计专注分钟数
 * @property weekendMinutes 周末累计专注分钟数
 */
data class AchievementStats(
    val totalMinutes: Int = 0,
    val totalPomos: Int = 0,
    val activeDays: Int = 0,
    val longestStreak: Int = 0,
    val bestDayMinutes: Int = 0,
    val earlyMinutes: Int = 0,
    val nightMinutes: Int = 0,
    val weekendMinutes: Int = 0
)

/**
 * 单个成就的实时状态。
 *
 * @property def 成就定义
 * @property current 当前完成值，已夹取到 [target] 区间内
 * @property target 达成阈值
 * @property unlocked 是否已解锁
 * @property unlockedAt 解锁时刻（毫秒），未解锁时为 0
 * @property percent 完成百分比 0-100
 */
data class AchievementState(
    val def: AchievementDef,
    val current: Int = 0,
    val target: Int = 0,
    val unlocked: Boolean = false,
    val unlockedAt: Long = 0L,
    val percent: Int = 0
)

/**
 * 成就体系纯计算工具：不依赖 Android / Room / Compose，可在 JVM 单元测试里直接跑。
 *
 * 设计约定：
 * - 成就「一旦解锁就永久保留」——进度可能因数据变化回落（例如历史记录被删），
 *   但解锁状态只由持久化记录决定，不随当前进度回退；
 * - 排序固定为「已解锁在前（按解锁时间倒序）→ 未解锁按完成度倒序 → 定义顺序」。
 */
object Achievements {

    /** 清晨时段起点（含） */
    const val EARLY_START_HOUR = 5

    /** 清晨时段终点（不含） */
    const val EARLY_END_HOUR = 9

    /** 深夜时段起点（含） */
    const val NIGHT_START_HOUR = 21

    /** 深夜时段终点（不含，即 24 点） */
    const val NIGHT_END_HOUR = 24

    /** 全部成就定义，顺序即同分时的兜底排序 */
    val ALL: List<AchievementDef> = listOf(
        AchievementDef("first_focus", "🌱", "初次专注", "完成第一次专注记录", AchievementMetric.TOTAL_MINUTES, 25, AchievementTier.BRONZE),
        AchievementDef("pomo_10", "🍅", "番茄新手", "累计完成 10 个番茄", AchievementMetric.TOTAL_POMOS, 10, AchievementTier.BRONZE),
        AchievementDef("hours_10", "⏳", "十小时", "累计专注满 10 小时", AchievementMetric.TOTAL_MINUTES, 600, AchievementTier.BRONZE),
        AchievementDef("streak_3", "🔥", "三日连续", "连续 3 天保持专注", AchievementMetric.LONGEST_STREAK, 3, AchievementTier.BRONZE),
        AchievementDef("deep_day", "🌊", "深度一日", "单日专注满 2 小时", AchievementMetric.BEST_DAY, 120, AchievementTier.BRONZE),
        AchievementDef("pomo_100", "🏅", "百颗番茄", "累计完成 100 个番茄", AchievementMetric.TOTAL_POMOS, 100, AchievementTier.SILVER),
        AchievementDef("hours_100", "💯", "百小时", "累计专注满 100 小时", AchievementMetric.TOTAL_MINUTES, 6000, AchievementTier.SILVER),
        AchievementDef("early_bird", "🌅", "早起鸟", "清晨 5-9 点累计专注 3 小时", AchievementMetric.EARLY_MINUTES, 180, AchievementTier.SILVER),
        AchievementDef("night_owl", "🌙", "夜猫子", "深夜 21-24 点累计专注 3 小时", AchievementMetric.NIGHT_MINUTES, 180, AchievementTier.SILVER),
        AchievementDef("weekend_go", "🎈", "周末不休", "周末累计专注 5 小时", AchievementMetric.WEEKEND_MINUTES, 300, AchievementTier.SILVER),
        AchievementDef("streak_30", "🏆", "月度坚持", "连续 30 天保持专注", AchievementMetric.LONGEST_STREAK, 30, AchievementTier.GOLD),
        AchievementDef("century", "📅", "百日足迹", "累计 100 天留下专注记录", AchievementMetric.ACTIVE_DAYS, 100, AchievementTier.GOLD)
    )

    /** 成就 id 在 [ALL] 中的下标，用于同分排序 */
    private val ORDERS: Map<String, Int> = ALL.withIndex().associate { it.value.id to it.index }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Long>>() {}.type

    /**
     * 从快照里取出某个指标的当前值。
     *
     * @param stats 统计快照
     * @param metric 指标类型
     * @return 当前值，非法指标返回 0
     */
    fun valueOf(stats: AchievementStats, metric: AchievementMetric): Int {
        return when (metric) {
            AchievementMetric.TOTAL_MINUTES -> stats.totalMinutes
            AchievementMetric.TOTAL_POMOS -> stats.totalPomos
            AchievementMetric.ACTIVE_DAYS -> stats.activeDays
            AchievementMetric.LONGEST_STREAK -> stats.longestStreak
            AchievementMetric.BEST_DAY -> stats.bestDayMinutes
            AchievementMetric.EARLY_MINUTES -> stats.earlyMinutes
            AchievementMetric.NIGHT_MINUTES -> stats.nightMinutes
            AchievementMetric.WEEKEND_MINUTES -> stats.weekendMinutes
        }
    }

    /**
     * 计算单个成就的实时状态。
     *
     * @param def 成就定义
     * @param stats 统计快照
     * @param unlockedAt 已记录的解锁时刻，0 表示尚未解锁
     * @return 成就状态；进度会夹到 0..target
     */
    fun stateOf(def: AchievementDef, stats: AchievementStats, unlockedAt: Long): AchievementState {
        val raw = valueOf(stats, def.metric)
        val done = raw >= def.target
        val at = if (unlockedAt > 0L) unlockedAt else 0L
        // 解锁后仍按阈值封顶，避免历史数据被删后又清理时进度条过冲
        val current = raw.coerceIn(0, def.target)
        return AchievementState(
            def = def,
            current = current,
            target = def.target,
            unlocked = at > 0L,
            unlockedAt = at,
            percent = percent(current, def.target)
        )
    }

    /**
     * 汇总所有成就的状态并排序。
     *
     * @param stats 统计快照
     * @param unlocked 已持久化的解锁记录（`id -> 解锁时刻`）
     * @return 排序后的状态列表
     */
    fun snapshot(stats: AchievementStats, unlocked: Map<String, Long>): List<AchievementState> {
        val states = ALL.map { stateOf(it, stats, unlocked[it.id] ?: 0L) }
        return states.sortedWith(
            compareByDescending<AchievementState> { it.unlocked }
                .thenByDescending { if (it.unlocked) it.unlockedAt else 0L }
                .thenByDescending { it.percent }
                .thenBy { ORDERS[it.def.id] ?: 0 }
        )
    }

    /**
     * 已解锁的成就数量。
     *
     * @param states 状态列表
     * @return 已解锁数量
     */
    fun unlockedCount(states: List<AchievementState>): Int {
        return states.count { it.unlocked }
    }

    /**
     * 整体完成度：所有成就完成百分比的平均，用于总进度条。
     *
     * @param states 状态列表
     * @return 0-100
     */
    fun overallPercent(states: List<AchievementState>): Int {
        if (states.isEmpty()) {
            return 0
        }
        return states.sumOf { it.percent } / states.size
    }

    /**
     * 取最接近达成、且还能继续推进的若干条。
     *
     * 完成度已满（100%）的未解锁成就不进榜——它们已经在等下次结算落库，
     * 列出来只会让人以为还差临门一脚。
     *
     * @param states 状态列表
     * @param limit 最多返回条数
     * @return 未解锁且未满进度的成就，最多 limit 条
     */
    fun nextUp(states: List<AchievementState>, limit: Int = 3): List<AchievementState> {
        return states.filter { !it.unlocked && it.percent < 100 }
            .sortedWith(compareByDescending<AchievementState> { it.percent }.thenBy { ORDERS[it.def.id] ?: 0 })
            .take(limit)
    }

    /**
     * 计算完成百分比。
     *
     * @param current 当前值
     * @param target 阈值
     * @return 0-100；target 非正数时视为 0
     */
    fun percent(current: Int, target: Int): Int {
        if (target <= 0) {
            return 0
        }
        return ((current.toFloat() / target.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    /**
     * 某指标的单位文案。
     *
     * @param metric 指标类型
     * @return 单位，如「分钟」「个」「天」
     */
    fun unitOf(metric: AchievementMetric): String {
        return when (metric) {
            AchievementMetric.TOTAL_MINUTES,
            AchievementMetric.BEST_DAY,
            AchievementMetric.EARLY_MINUTES,
            AchievementMetric.NIGHT_MINUTES,
            AchievementMetric.WEEKEND_MINUTES -> "分钟"

            AchievementMetric.TOTAL_POMOS -> "个"

            AchievementMetric.ACTIVE_DAYS,
            AchievementMetric.LONGEST_STREAK -> "天"
        }
    }

    /**
     * 进度文案，如「300 / 600 分钟」。
     *
     * @param state 成就状态
     * @return 进度文案
     */
    fun progressText(state: AchievementState): String {
        return "${state.current} / ${state.target} ${unitOf(state.def.metric)}"
    }

    /**
     * 还差多少的文案，如「还差 300 分钟」。
     *
     * @param state 成就状态
     * @return 差值文案；已达成时为空串
     */
    fun remainingText(state: AchievementState): String {
        val left = state.target - state.current
        if (left <= 0) {
            return ""
        }
        return "还差 $left ${unitOf(state.def.metric)}"
    }

    /**
     * 成就总览文案，如「已解锁 3 / 12」。
     *
     * @param states 状态列表
     * @return 总览文案
     */
    fun countText(states: List<AchievementState>): String {
        return "已解锁 ${unlockedCount(states)} / ${states.size}"
    }

    /**
     * 顶部一句话激励文案：没有进度时引导第一步，全解锁时祝贺，否则指向最接近的目标。
     *
     * @param states 状态列表
     * @return 激励文案
     */
    fun motivationText(states: List<AchievementState>): String {
        val unlocked = unlockedCount(states)
        if (states.isEmpty()) {
            return ""
        }
        if (unlocked == 0) {
            return "完成第一次专注，点亮第一枚徽章"
        }
        if (unlocked == states.size) {
            return "全部成就已解锁，你是真正的专注大师"
        }
        // 剩下的都已满进度（只是还没落库），提示「即将点亮」而不是空文案
        val next = nextUp(states, 1).firstOrNull()
            ?: return "继续保持，新的徽章即将点亮"
        return "距离「${next.def.title}」${remainingText(next)}"
    }

    /**
     * 稀有度名称。
     *
     * @param tier 稀有度
     * @return 名称文案
     */
    fun tierText(tier: AchievementTier): String {
        return when (tier) {
            AchievementTier.BRONZE -> "铜牌"
            AchievementTier.SILVER -> "银牌"
            AchievementTier.GOLD -> "金牌"
        }
    }

    /**
     * 解锁日期文案，如「2026.11.01」。
     *
     * @param at 解锁时刻（毫秒）
     * @return 日期文案；未解锁时为空串
     */
    fun unlockDateText(at: Long): String {
        if (at <= 0L) {
            return ""
        }
        return SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(at))
    }

    /**
     * 取 24 小时分布里某个时段（左闭右开）的累计分钟数。
     *
     * @param byHour 24 小时分钟数，索引即小时
     * @param from 起始小时（含）
     * @param to 结束小时（不含）
     * @return 时段内分钟数；越界小时自动忽略
     */
    fun rangeMinutes(byHour: List<Int>, from: Int, to: Int): Int {
        var sum = 0
        for (hour in from until to) {
            if (hour in byHour.indices) {
                sum += byHour[hour]
            }
        }
        return sum
    }

    /**
     * 清晨（5:00-9:00）累计专注分钟数。
     *
     * @param byHour 24 小时分钟数
     * @return 清晨分钟数
     */
    fun earlyMinutes(byHour: List<Int>): Int {
        return rangeMinutes(byHour, EARLY_START_HOUR, EARLY_END_HOUR)
    }

    /**
     * 深夜（21:00-24:00）累计专注分钟数。
     *
     * @param byHour 24 小时分钟数
     * @return 深夜分钟数
     */
    fun nightMinutes(byHour: List<Int>): Int {
        return rangeMinutes(byHour, NIGHT_START_HOUR, NIGHT_END_HOUR)
    }

    /**
     * 周末累计专注分钟数。
     *
     * @param minutesByDay 每天专注分钟数（键为本地 0 点时间戳）
     * @return 周末分钟数
     */
    fun weekendMinutes(minutesByDay: Map<Long, Int>): Int {
        var sum = 0
        minutesByDay.forEach { (dayStart, minutes) ->
            if (Rhythm.isWeekend(dayStart)) {
                sum += minutes
            }
        }
        return sum
    }

    /**
     * 找出本次新达成、但还没写进存储的成就 id。
     *
     * @param stats 统计快照
     * @param unlocked 已持久化的解锁记录
     * @return 新达成的 id 列表，按定义顺序
     */
    fun freshIds(stats: AchievementStats, unlocked: Map<String, Long>): List<String> {
        return ALL.filter { def ->
            unlocked[def.id] ?: 0L <= 0L && valueOf(stats, def.metric) >= def.target
        }.map { it.id }
    }

    /**
     * 把新解锁的 id 合并进已有记录：只补缺失的，已有解锁时间保持不变。
     *
     * @param unlocked 已有记录
     * @param ids 新解锁的 id 列表
     * @param at 解锁时刻（毫秒）
     * @return 合并后的记录
     */
    fun merge(unlocked: Map<String, Long>, ids: List<String>, at: Long): Map<String, Long> {
        if (ids.isEmpty()) {
            return unlocked
        }
        val out = LinkedHashMap(unlocked)
        ids.forEach { id ->
            if (out[id] ?: 0L <= 0L) {
                out[id] = at
            }
        }
        return out
    }

    /**
     * 序列化解锁记录。
     *
     * @param unlocked 解锁记录
     * @return JSON 串
     */
    fun encode(unlocked: Map<String, Long>): String {
        return gson.toJson(unlocked, mapType)
    }

    /**
     * 反序列化解锁记录：脏数据一律降级为空记录，不让坏 JSON 影响界面。
     *
     * @param json JSON 串
     * @return 解锁记录
     */
    fun decode(json: String): Map<String, Long> {
        if (json.isBlank()) {
            return emptyMap()
        }
        return try {
            val parsed: Map<String, Long>? = gson.fromJson(json, mapType)
            parsed?.filterValues { it > 0L } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
