package com.focuslist.app.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 计划模板：把一次建计划时配好的「图标 + 名称 + 每日时长 + 持续天数 + 颜色」沉淀下来，
 * 下次点一下就能复用，不必重填。
 *
 * 模板只保存**配置**，不保存具体日期：复用时一律从「今天」重新开始计算区间。
 *
 * @property id 唯一标识，由 [PlanTemplates.signature] 生成（同名同参数视为同一模板）
 * @property title 模板名称
 * @property icon 图标 emoji
 * @property dailyMinutes 每天目标分钟数
 * @property durationDays 持续天数（含首尾两天）
 * @property colorHex 主题色
 * @property useCount 被复用次数，用于排序
 * @property createdAt 首次创建时间（毫秒）
 */
data class PlanTemplate(
    val id: String = "",
    val title: String = "",
    val icon: String = "🎯",
    val dailyMinutes: Int = 30,
    val durationDays: Int = 30,
    val colorHex: String = "#4772FA",
    val useCount: Int = 0,
    val createdAt: Long = 0
)

/**
 * 计划模板工具：纯 Kotlin 实现（Gson 编解码 + java.time 日期推算），不依赖 Android，可直接单元测试。
 *
 * 排序规则固定为「用得多的在前，同次数下新的在前」，因此列表可以安全地截断：
 * 超出 [MAX_COUNT] 时丢掉的永远是最不常用的那几条。
 */
object PlanTemplates {

    /** 模板名称最大长度 */
    const val TITLE_MAX = 20

    /** 每日时长下限（分钟） */
    const val MIN_MINUTES = 5

    /** 每日时长上限（分钟） */
    const val MAX_MINUTES = 480

    /** 持续天数下限 */
    const val MIN_DAYS = 1

    /** 持续天数上限 */
    const val MAX_DAYS = 365

    /** 最多保留的模板条数 */
    const val MAX_COUNT = 24

    /** 常用持续天数（供 UI 快捷选择） */
    val DAY_PRESETS: List<Int> = listOf(7, 21, 30, 90)

    private val gson = Gson()
    private val listType = object : TypeToken<List<PlanTemplate>>() {}.type

    /**
     * 规范化标题：去首尾空白、压缩连续空白、截断到 [TITLE_MAX]。
     *
     * @param raw 原始输入
     * @return 规范化后的标题
     */
    fun cleanTitle(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ").take(TITLE_MAX)
    }

    /**
     * 把每日时长夹到合法区间。
     *
     * @param minutes 输入分钟数
     * @return [MIN_MINUTES]..[MAX_MINUTES] 内的值
     */
    fun coerceMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /**
     * 把持续天数夹到合法区间。
     *
     * @param days 输入天数
     * @return [MIN_DAYS]..[MAX_DAYS] 内的值
     */
    fun coerceDays(days: Int): Int = days.coerceIn(MIN_DAYS, MAX_DAYS)

    /**
     * 计算模板指纹：名称 + 时长 + 天数 + 图标完全相同即视为同一模板。
     *
     * @param title 标题（内部会先规范化）
     * @param dailyMinutes 每日分钟数
     * @param durationDays 持续天数
     * @param icon 图标
     * @return 指纹字符串，同时作为模板 id
     */
    fun signature(title: String, dailyMinutes: Int, durationDays: Int, icon: String): String {
        return "${cleanTitle(title)}#$dailyMinutes#$durationDays#$icon"
    }

    /**
     * 由表单数据构造模板。
     *
     * @param title 计划名称；空白时返回 null
     * @param icon 图标 emoji
     * @param dailyMinutes 每日目标分钟数
     * @param durationDays 持续天数
     * @param colorHex 主题色
     * @param now 当前时间戳（毫秒）
     * @return 模板；标题为空时返回 null
     */
    fun create(
        title: String,
        icon: String,
        dailyMinutes: Int,
        durationDays: Int,
        colorHex: String,
        now: Long = System.currentTimeMillis()
    ): PlanTemplate? {
        val clean = cleanTitle(title)
        if (clean.isBlank()) return null
        val minutes = coerceMinutes(dailyMinutes)
        val days = coerceDays(durationDays)
        return PlanTemplate(
            id = signature(clean, minutes, days, icon),
            title = clean,
            icon = icon,
            dailyMinutes = minutes,
            durationDays = days,
            colorHex = colorHex,
            useCount = 1,
            createdAt = now
        )
    }

    /**
     * 由已有计划反推模板：持续天数按「起始日 ~ 截止日」含首尾换算。
     *
     * @param title 计划名称
     * @param icon 图标
     * @param dailyMinutes 每日目标分钟数
     * @param startAt 起始日 0 点时间戳
     * @param endAt 截止日 0 点时间戳
     * @param colorHex 主题色
     * @param now 当前时间戳（毫秒）
     * @return 模板；标题为空时返回 null
     */
    fun fromPlan(
        title: String,
        icon: String,
        dailyMinutes: Int,
        startAt: Long,
        endAt: Long,
        colorHex: String,
        now: Long = System.currentTimeMillis()
    ): PlanTemplate? {
        return create(title, icon, dailyMinutes, daysBetween(startAt, endAt), colorHex, now)
    }

    /**
     * 两个日期之间的天数（含首尾）。
     *
     * 走日期串而非毫秒整除，避免时区 / 夏令时带来的整日错位。
     *
     * @param startAt 起始时间戳
     * @param endAt 结束时间戳
     * @return 天数，至少 [MIN_DAYS]
     */
    fun daysBetween(startAt: Long, endAt: Long): Int {
        val from = LocalDate.parse(DayKeys.format(startAt))
        val to = LocalDate.parse(DayKeys.format(endAt))
        return coerceDays(ChronoUnit.DAYS.between(from, to).toInt() + 1)
    }

    /**
     * 复用模板时的日期区间：从「今天」起算，持续 [durationDays] 天。
     *
     * @param durationDays 持续天数
     * @param todayMillis 任意「今天」时刻的时间戳
     * @return 起始日 0 点到截止日 0 点
     */
    fun range(durationDays: Int, todayMillis: Long): Pair<Long, Long> {
        val startKey = DayKeys.format(todayMillis)
        val endKey = LocalDate.parse(startKey).plusDays((coerceDays(durationDays) - 1).toLong()).toString()
        return DayKeys.parse(startKey) to DayKeys.parse(endKey)
    }

    /**
     * 合并一条模板到列表：已存在则复用次数 +1，不存在则追加。
     *
     * @param list 已有模板
     * @param template 待合并模板
     * @param max 保留上限，默认 [MAX_COUNT]
     * @return 排序并截断后的新列表
     */
    fun merge(list: List<PlanTemplate>, template: PlanTemplate, max: Int = MAX_COUNT): List<PlanTemplate> {
        val existing = list.firstOrNull { it.id == template.id }
        val merged = if (existing == null) {
            list + template
        } else {
            list.map {
                if (it.id == template.id) {
                    it.copy(
                        useCount = it.useCount + 1,
                        colorHex = template.colorHex,
                        title = template.title
                    )
                } else {
                    it
                }
            }
        }
        return sorted(merged).take(max)
    }

    /**
     * 删除指定模板。
     *
     * @param list 已有模板
     * @param id 模板 id
     * @return 删除后的列表
     */
    fun remove(list: List<PlanTemplate>, id: String): List<PlanTemplate> {
        return list.filterNot { it.id == id }
    }

    /**
     * 排序：复用次数降序 → 创建时间降序 → 名称升序。
     *
     * @param list 待排序模板
     * @return 新列表
     */
    fun sorted(list: List<PlanTemplate>): List<PlanTemplate> {
        return list.sortedWith(
            compareByDescending<PlanTemplate> { it.useCount }
                .thenByDescending { it.createdAt }
                .thenBy { it.title }
        )
    }

    /**
     * 一句话描述模板配置。
     *
     * @param template 模板
     * @return 如「每天 30 分钟 · 共 21 天」
     */
    fun describe(template: PlanTemplate): String {
        return "每天 ${template.dailyMinutes} 分钟 · 共 ${template.durationDays} 天"
    }

    /**
     * 序列化为 JSON。
     *
     * @param list 模板列表
     * @return JSON 字符串；空列表返回 "[]"
     */
    fun encode(list: List<PlanTemplate>): String {
        return gson.toJson(list)
    }

    /**
     * 反序列化 JSON。
     *
     * @param json JSON 字符串
     * @return 模板列表；空白或非法输入返回空列表（不抛异常，避免脏数据让界面崩掉）
     */
    fun decode(json: String): List<PlanTemplate> {
        if (json.isBlank()) return emptyList()
        return try {
            val parsed: List<PlanTemplate> = gson.fromJson(json, listType)
            parsed ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
