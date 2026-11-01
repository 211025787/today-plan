package com.focuslist.app.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 一句话建计划的解析结果。
 *
 * @property title 解析出的计划标题；为空表示没写出有效名称（UI 应禁用保存）
 * @property icon 计划图标：输入里的 emoji 优先，否则按标题关键词猜一个
 * @property dailyGoalMinutes 每日目标时长（分钟），已夹到 [5, 480]
 * @property startDate 生效起始日 0 点时间戳
 * @property endDate 生效截止日 0 点时间戳（含）
 * @property days 区间天数（含首尾）
 */
data class QuickPlanDraft(
    val title: String,
    val icon: String,
    val dailyGoalMinutes: Int,
    val startDate: Long,
    val endDate: Long,
    val days: Int
) {
    /** 是否有可用标题 */
    fun hasTitle(): Boolean = title.isNotBlank()
}

/**
 * 一句话快速建计划解析器（纯 Kotlin，无 Android 依赖，可直接 JUnit）。
 *
 * 支持在一句话里同时写出：名称、emoji、每日时长、起止日期、持续天数。例如：
 * - `每天读 30 分钟英文原版书 📚 到年底`
 * - `11月1日-11月30日 每天健身 45 分钟 💪`
 * - `冥想 10 分钟 🧘 坚持 21 天`
 *
 * 解析策略：**先摘后清** —— 依次识别 emoji / 时长 / 日期 / 跨度，
 * 命中区间用等长空格"抹掉"（保持下标稳定），剩下的文字压缩空白后即为标题。
 * 这样即使输入顺序打乱（时长写在前面、日期写在中间）也能稳定解析。
 */
object QuickPlanParser {

    /** 未写时长时的默认每日目标（分钟） */
    const val DEFAULT_GOAL_MINUTES = 30

    /** 未写结束日期时的默认跨度（天，含首尾）：默认"今天起 30 天" */
    const val DEFAULT_SPAN_DAYS = 30

    /** 每日目标下限（分钟） */
    const val MIN_GOAL_MINUTES = 5

    /** 每日目标上限（分钟） */
    const val MAX_GOAL_MINUTES = 480

    /** 标题最大长度（与计划实体一致） */
    const val MAX_TITLE_LENGTH = 50

    /** 没写 emoji 也没命中关键词时的兜底图标 🎯 */
    const val FALLBACK_ICON = "\uD83C\uDFAF"

    /** 视为"结束日"的连接符（出现在日期前面时该日期作截止日） */
    private val END_CONNECTORS = charArrayOf('到', '至', '~', '～', '—', '–', '-')

    private val HOUR_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:个)?\s*(?:小时|时|h|hr|hrs|hours?)""", RegexOption.IGNORE_CASE)
    private val MINUTE_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:分钟|分|min|mins|minutes?)""", RegexOption.IGNORE_CASE)
    private val SPAN_REGEX = Regex("""(\d+|[一二三四五六七八九十两]+)\s*(?:天|周|星期|个月|月)""")

    // 日期规则：自左到右按优先级依次扫描，命中即抹除，避免同一串被多个规则重复识别
    private val DATE_RULES: List<Pair<Regex, (MatchResult, LocalDate) -> LocalDate?>> = listOf(
        // 2026-11-01 / 2026年11月1日 / 2026.11.1
        Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*[日号]?""") to { m, _ ->
            safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        },
        // 11月1日 / 11月1
        Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]?""") to { m, today ->
            val day = safeDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            // 今年已过完的月日顺延到明年（"1月5日"在 11 月说，指的是明年）
            if (day != null && day.isBefore(today)) day.plusYears(1) else day
        },
        // 11/1 / 11.1（纯数字月日）
        Regex("""(\d{1,2})\s*[-/.]\s*(\d{1,2})(?!\d)""") to { m, today ->
            val day = safeDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            if (day != null && day.isBefore(today)) day.plusYears(1) else day
        },
        // 下周一 / 周三 / 礼拜天
        Regex("""(下)?\s*(?:周|星期|礼拜)\s*([一二三四五六日天1-7])""") to { m, today ->
            val dow = weekdayOf(m.groupValues[2]) ?: return@to null
            val base = today.with(DayOfWeek.of(dow))
            if (m.groupValues[1] == "下" || base.isBefore(today)) base.plusWeeks(1) else base
        },
        // 周末 → 本周六（已过则下周六）
        Regex("""(下)?\s*周末""") to { m, today ->
            val base = today.with(DayOfWeek.SATURDAY)
            if (m.groupValues[1] == "下" || base.isBefore(today)) base.plusWeeks(1) else base
        },
        // 今天 / 明天 / 后天 / 大后天
        Regex("""今天|今日|明天|明日|大后天|后天""") to { m, today ->
            today.plusDays(
                when (m.value) {
                    "明天", "明日" -> 1L
                    "后天" -> 2L
                    "大后天" -> 3L
                    else -> 0L
                }
            )
        },
        // 月底 / 月末 / 年底 / 年末
        Regex("""(?:本|这|今)?\s*(月|年)\s*(?:底|末)""") to { m, today ->
            if (m.groupValues[1] == "年") {
                LocalDate.of(today.year, 12, 31)
            } else {
                today.withDayOfMonth(today.lengthOfMonth())
            }
        }
    )

    /** 标题关键词 → 图标（只在输入里没有 emoji 时兜底猜测） */
    private val ICON_KEYWORDS: List<Pair<String, String>> = listOf(
        "冥想" to "\uD83E\uDDD8", // 🧘
        "健身" to "\uD83D\uDCAA", // 💪
        "运动" to "\uD83D\uDCAA",
        "跑" to "\uD83C\uDFC3", // 🏃
        "步" to "\uD83C\uDFC3",
        "读" to "\uD83D\uDCDA", // 📚
        "书" to "\uD83D\uDCDA",
        "学" to "\uD83D\uDCDA",
        "英" to "\uD83C\uDFA7", // 🎧
        "听" to "\uD83C\uDFA7",
        "单词" to "\uD83C\uDFA7",
        "写" to "✍️",
        "日记" to "✍️",
        "代码" to "\uD83D\uDCBB", // 💻
        "编程" to "\uD83D\uDCBB",
        "开发" to "\uD83D\uDCBB",
        "画" to "\uD83C\uDFA8", // 🎨
        "设计" to "\uD83C\uDFA8",
        "语" to "\uD83C\uDF0D" // 🌍
    )

    /**
     * 解析一句话。
     *
     * @param input 用户输入，如 `每天读 30 分钟书 📚 到年底`
     * @param todayStart "今天"的 0 点时间戳，用于相对日期换算（测试可传固定值）
     * @return 解析结果；任何异常输入都退化成默认值而不是崩溃
     */
    fun parse(input: String, todayStart: Long = DateUtils.startOfDay(System.currentTimeMillis())): QuickPlanDraft {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(todayStart), zone)
        // 口语时长先归一成"N分钟"，后续统一走数字规则
        val normalized = input.trim()
            .replace("一个半小时", "90分钟")
            .replace("半小时", "30分钟")
            .replace("一刻钟", "15分钟")
        val work = StringBuilder(normalized)
        if (work.isBlank()) {
            return defaultDraft(today, zone)
        }

        // 1) emoji：优先作为图标，全部 emoji 从标题里抹掉
        val typedIcon = extractEmoji(work)

        // 2) 时长：1小时30分 / 30分钟 / 半小时 / 45min
        val goalMinutes = extractGoal(work) ?: DEFAULT_GOAL_MINUTES

        // 3) 日期：命中顺序保留，前面带"到/至/-"的算截止日
        val hits = extractDates(work, today)

        // 4) 跨度：坚持 21 天 / 3个月
        val spanDays = extractSpan(work)

        // 5) 剩下的就是标题
        val title = cleanTitle(work.toString())
        val icon = typedIcon ?: suggestIcon(title)

        val startHit = hits.firstOrNull { !it.asEnd }?.date
        val endHit = hits.firstOrNull { it.asEnd }?.date
        var start = startHit ?: today
        var end = endHit ?: start.plusDays((spanDays - 1).coerceAtLeast(0).toLong())
        if (endHit != null && end.isBefore(start)) {
            // "到 3月1日" 这类跨年说法：先顺延一年，仍然更早才交换首尾
            val rolled = end.plusYears(1)
            if (!rolled.isBefore(start)) {
                end = rolled
            } else {
                val swap = start
                start = end
                end = swap
            }
        }

        return QuickPlanDraft(
            title = title,
            icon = icon,
            dailyGoalMinutes = goalMinutes,
            startDate = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = end.atStartOfDay(zone).toInstant().toEpochMilli(),
            days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        )
    }

    /**
     * 按标题稳定映射到调色板下标（同一标题永远同一颜色，纯函数便于测试）。
     *
     * @param seed 种子文本（一般用标题）
     * @param size 调色板长度
     * @return [0, size) 范围内的下标；size <= 0 时返回 0
     */
    fun colorIndex(seed: String, size: Int): Int {
        if (size <= 0) return 0
        return Math.floorMod(seed.hashCode(), size)
    }

    /**
     * 按标题关键词猜图标。
     *
     * @param title 计划标题
     * @return 命中关键词的 emoji，否则 [FALLBACK_ICON]
     */
    fun suggestIcon(title: String): String {
        ICON_KEYWORDS.forEach { (keyword, emoji) ->
            if (title.contains(keyword)) return emoji
        }
        return FALLBACK_ICON
    }

    /** 空输入 / 纯空白时的默认结果：今天起 30 天，每天 30 分钟 */
    private fun defaultDraft(today: LocalDate, zone: ZoneId): QuickPlanDraft {
        val end = today.plusDays((DEFAULT_SPAN_DAYS - 1).toLong())
        return QuickPlanDraft(
            title = "",
            icon = FALLBACK_ICON,
            dailyGoalMinutes = DEFAULT_GOAL_MINUTES,
            startDate = today.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = end.atStartOfDay(zone).toInstant().toEpochMilli(),
            days = ChronoUnit.DAYS.between(today, end).toInt() + 1
        )
    }

    /**
     * 摘出第一个 emoji 作为图标，并把全部 emoji 字符抹成空格。
     *
     * @param work 工作串（原地修改）
     * @return 用户输入的 emoji；没有则返回 null
     */
    private fun extractEmoji(work: StringBuilder): String? {
        var icon: String? = null
        var i = 0
        while (i < work.length) {
            val cp = work.codePointAt(i)
            val width = Character.charCount(cp)
            if (isEmojiCodePoint(cp)) {
                if (icon == null && cp != 0xFE0F) {
                    // 带上紧随的变体选择符，保证 ✍️ 这类 emoji 完整
                    var end = i + width
                    if (end < work.length && work.codePointAt(end) == 0xFE0F) {
                        end += Character.charCount(0xFE0F)
                    }
                    icon = work.substring(i, end)
                }
                for (k in 0 until width) {
                    work.setCharAt(i + k, ' ')
                }
            }
            i += width
        }
        return icon
    }

    /** emoji 码点判定（含箭头/符号/表情三段与变体选择符） */
    private fun isEmojiCodePoint(cp: Int): Boolean {
        return cp == 0xFE0F || cp == 0x20E3 ||
            cp in 0x2190..0x21FF ||
            cp in 0x2300..0x23FF ||
            cp in 0x2600..0x27BF ||
            cp in 0x2B00..0x2BFF ||
            cp in 0x1F000..0x1FAFF
    }

    /**
     * 摘出时长（小时 + 分钟可叠加），抹掉命中片段。
     *
     * @param work 工作串（原地修改）
     * @return 分钟数；未写时长返回 null
     */
    private fun extractGoal(work: StringBuilder): Int? {
        var total: Double? = null
        val hour = HOUR_REGEX.find(work.toString())
        if (hour != null) {
            total = (total ?: 0.0) + (hour.groupValues[1].toDoubleOrNull() ?: 0.0) * 60.0
            mask(work, hour.range)
        }
        val minute = MINUTE_REGEX.find(work.toString())
        if (minute != null) {
            total = (total ?: 0.0) + (minute.groupValues[1].toDoubleOrNull() ?: 0.0)
            mask(work, minute.range)
        }
        return total?.roundToInt()?.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES)
    }

    /** 日期命中项 */
    private class DateHit(val date: LocalDate, val asEnd: Boolean)

    /**
     * 按规则扫描全部日期，抹掉命中片段。
     *
     * @param work 工作串（原地修改）
     * @param today 今天
     * @return 按出现顺序排列的命中项
     */
    private fun extractDates(work: StringBuilder, today: LocalDate): List<DateHit> {
        val hits = mutableListOf<Pair<Int, DateHit>>()
        DATE_RULES.forEach { (regex, resolve) ->
            // 先快照再改，避免边遍历边修改
            regex.findAll(work.toString()).toList().forEach { m ->
                val date = resolve(m, today) ?: return@forEach
                // 注意：必须在抹除前判断，才能看到紧邻的"到/至/-"
                hits.add(m.range.first to DateHit(date, isEndConnector(work.toString(), m.range.first)))
                mask(work, m.range)
            }
        }
        return hits.sortedBy { it.first }.map { it.second }
    }

    /**
     * 判断日期前面是不是"到/至/-"这类连接符（是则该日期作截止日）。
     *
     * @param text 当前工作串
     * @param start 命中起始下标
     * @return true 表示应视为截止日
     */
    private fun isEndConnector(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return false
        return END_CONNECTORS.contains(text[i])
    }

    /**
     * 摘出持续跨度（天），抹掉命中片段。
     *
     * @param work 工作串（原地修改）
     * @return 跨度天数；未写则返回 [DEFAULT_SPAN_DAYS]
     */
    private fun extractSpan(work: StringBuilder): Int {
        val m = SPAN_REGEX.find(work.toString()) ?: return DEFAULT_SPAN_DAYS
        val raw = m.groupValues[1]
        val n = raw.toIntOrNull() ?: chineseNumber(raw) ?: return DEFAULT_SPAN_DAYS
        val unit = when {
            m.value.contains("天") -> 1
            m.value.contains("周") || m.value.contains("星期") -> 7
            else -> 30
        }
        val days = n * unit
        if (days <= 0 || days > 365) return DEFAULT_SPAN_DAYS
        mask(work, m.range)
        return days
    }

    /** 中文数字转阿拉伯数字，支持 一~二十 与 十一~十九 */
    private fun chineseNumber(raw: String): Int? {
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (raw.contains('十')) {
            val parts = raw.split('十')
            val tens = if (parts[0].isEmpty()) 1 else digits[parts[0].singleOrNull()] ?: return null
            val ones = if (parts.size > 1 && parts[1].isNotEmpty()) {
                digits[parts[1].singleOrNull()] ?: return null
            } else {
                0
            }
            return tens * 10 + ones
        }
        return digits[raw.singleOrNull() ?: return null]
    }

    /** 星期中文/数字 → DayOfWeek 序号（1=周一 … 7=周日） */
    private fun weekdayOf(token: String): Int? {
        return when (token) {
            "一", "1" -> 1
            "二", "2" -> 2
            "三", "3" -> 3
            "四", "4" -> 4
            "五", "5" -> 5
            "六", "6" -> 6
            "日", "天", "7" -> 7
            else -> null
        }
    }

    /** 安全构造日期：非法月日返回 null */
    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** 把命中区间替换成等长空格，保持后续下标稳定 */
    private fun mask(work: StringBuilder, range: IntRange) {
        for (i in range) {
            if (i < work.length) work.setCharAt(i, ' ')
        }
    }

    /** 口语命令前缀：客气话 + 动词 + 量词，三者都可选，但单字动词必须带量词才剥 */
    private val LEADING_COMMAND = Regex(
        "^(?:请帮我|请帮忙|帮我|帮忙|麻烦|请)?" +
            "(?:创建|新建|建立|制定|添加|安排)?" +
            // 单字动词后面必须跟量词 / "的" / 数字（"建3个月""建个""建的"才算命令，"做菜"不算）
            "(?:(?:建|加|来|做|订)(?=(?:一个|一条|一份|个|条|份|\\d|的)))?" +
            "(?:一个|一条|一份|个|条|份)?"
    )

    /** 计划常见的收尾词：留着会让标题冗余（"健身规划" → "健身"） */
    private val TRAILING_PLAN_WORD = Regex("(?:规划|计划|安排)$")

    /** 清理残留：去掉命令前缀、"的"与"规划/计划"等收尾词，压缩空白，裁掉首尾标点，最后限长 */
    private fun cleanTitle(text: String): String {
        val trimmed = text
            .replace(Regex("""^\s*(?:每天|每日|天天)\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            // 抹除 token 后夹在中文之间的残留空格一并去掉（"读 📚 书" → "读书"），
            // 但"学 Java"这类中英混排的空格保留
            .replace(Regex("""(?<=[\u4e00-\u9fa5]) (?=[\u4e00-\u9fa5])"""), "")
            .trim()
            .trim(' ', '，', ',', '。', '、', '；', ';', '：', ':', '~', '～', '-', '—', '到', '至', '起', '从', '始')
        // 口语里的「帮我建三个月的健身规划」不该成为计划名：
        // 摘掉命令前缀与收尾词，只留真正的主题
        val stripped = trimmed
            .replace(LEADING_COMMAND, "")
            .replace(Regex("^的+"), "")
            .replace(TRAILING_PLAN_WORD, "")
            .trim()
        val candidate = stripped.ifBlank { trimmed }
        val final = candidate.trim('，', '。', '、', '；', ';', '：', ':', '~', '～')
        return if (final.length > MAX_TITLE_LENGTH) final.substring(0, MAX_TITLE_LENGTH) else final
    }
}
