package com.focuslist.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 导出到日历所需的计划信息。
 *
 * 不直接复用 Room 实体，是为了让整个 ICS 生成器都能在 JVM 单测里跑。
 *
 * @property id 计划 ID，用于生成稳定的 UID
 * @property title 计划名称
 * @property note 备注，写进事件描述
 * @property start 起始日（0 点，含）
 * @property end 截止日（0 点，含）
 * @property dailyGoalMinutes 每日目标分钟数
 * @property weekdaysMask 周几执行掩码；0 表示每天
 * @property dayRule 执行日类型：0=按周几, 1=仅工作日, 2=仅休息日
 */
data class IcsPlan(
    val id: Long,
    val title: String,
    val note: String = "",
    val start: Long,
    val end: Long,
    val dailyGoalMinutes: Int = 0,
    val weekdaysMask: Int = 0,
    val dayRule: Int = 0
)

/**
 * 导出到日历所需的行程信息。
 *
 * @property id 行程 ID（用于稳定 UID）
 * @property title 行程名称
 * @property note 备注
 * @property start 起始日（0 点；<=0 表示未设置）
 * @property end 截止日（0 点，含；<=0 表示未设置）
 * @property stops 经过的地点名称，按顺序写进事件描述
 */
data class IcsTrip(
    val id: Long,
    val title: String,
    val note: String = "",
    val start: Long = 0L,
    val end: Long = 0L,
    val stops: List<String> = emptyList()
)

/**
 * 把计划导出成 iCalendar（.ics）文件。
 *
 * 这是应用主动生成的文件之一：本身不联网、不申请日历权限，只生成一个文件，
 * 用系统日历或 Google 日历打开它，计划就会以**每天重复的整天事件**出现。
 *
 * 几个刻意的选择：
 * - **整天事件**（`DTSTART;VALUE=DATE`）而不是固定时刻：计划是「每天做一点」，不该被钉在某个钟点；
 * - 重复用 `RRULE:FREQ=DAILY;UNTIL=截止日`，一个事件覆盖整段，日历里不会塞满 N 条；
 * - 一律 CRLF 换行、75 字节折行、特殊字符转义——这三条是各家日历客户端解析失败的常见原因。
 */
object Ics {

    /** 产品标识，日历客户端会显示在事件来源里 */
    private const val PRODID = "-//FocusList//Today Plan//CN"

    /** 单行最大字节数（RFC 5545） */
    private const val MAX_LINE_BYTES = 75

    /**
     * 生成单个计划的日历文本。
     *
     * @param plan 计划信息
     * @param now 生成时刻（毫秒），用于 DTSTAMP
     * @return ICS 文本，行尾为 CRLF
     */
    fun forPlan(plan: IcsPlan, now: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:$PRODID"
        lines += "CALSCALE:GREGORIAN"
        lines += "METHOD:PUBLISH"
        lines += "BEGIN:VEVENT"
        lines += "UID:${uidOf(plan)}"
        lines += "DTSTAMP:${stampOf(now)}"
        // DTSTART 必须是第一个执行日：只在周末执行的计划如果从周一起，
        // 部分客户端会把这个不存在的日期当成第一个实例
        val firstDay = PlanSchedule.activeDays(
            plan.start, plan.end, plan.weekdaysMask, plan.dayRule, 1
        ).firstOrNull() ?: plan.start
        lines += "DTSTART;VALUE=DATE:${dateOf(firstDay)}"
        // 整天事件的 DTEND 是排他的，所以取截止日的下一天
        lines += "DTEND;VALUE=DATE:${dateOf(DateUtils.addDays(plan.end, 1))}"
        lines += rruleOf(plan)
        lines += "SUMMARY:${escape(summaryOf(plan))}"
        lines += "DESCRIPTION:${escape(descriptionOf(plan))}"
        lines += "TRANSP:TRANSPARENT"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"

        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /**
     * 生成单个行程的日历文本：一个覆盖整段日期的**整天事件**，不按天重复。
     *
     * 行程是「这几天去哪」而不是「每天做一点」，用 RRULE 会在日历里塞满重复条目；
     * 没有设置日期时退化成「今天一天」，避免生成无法导入的非法事件。
     *
     * @param trip 行程信息
     * @param now 生成时刻（毫秒）
     * @return ICS 文本，行尾为 CRLF
     */
    fun forTrip(trip: IcsTrip, now: Long = System.currentTimeMillis()): String {
        val today = DateUtils.startOfDay(now)
        val start = if (trip.start > 0L) DateUtils.startOfDay(trip.start) else today
        val last = if (trip.end > start) DateUtils.startOfDay(trip.end) else start
        val title = trip.title.ifBlank { "未命名行程" }
        val description = buildString {
            if (trip.note.isNotBlank()) {
                append(trip.note.trim()).append("\n")
            }
            if (trip.stops.isNotEmpty()) {
                append("路线：").append(trip.stops.joinToString(" → ")).append("\n")
            }
            append("由「暖历」导出。")
        }
        val lines = mutableListOf<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:$PRODID"
        lines += "CALSCALE:GREGORIAN"
        lines += "METHOD:PUBLISH"
        lines += "BEGIN:VEVENT"
        lines += "UID:focuslist-trip-${trip.id}@focuslist.app"
        lines += "DTSTAMP:${stampOf(now)}"
        lines += "DTSTART;VALUE=DATE:${dateOf(start)}"
        lines += "DTEND;VALUE=DATE:${dateOf(DateUtils.addDays(last, 1))}"
        lines += "SUMMARY:${escape("🗺 $title")}"
        lines += "DESCRIPTION:${escape(description)}"
        lines += "TRANSP:TRANSPARENT"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /**
     * 行程 .ics 文件名。
     *
     * @param trip 行程信息
     * @return 文件名
     */
    fun tripFileNameOf(trip: IcsTrip): String {
        return sanitizeFileName(trip.title.ifBlank { "行程" }) + ".ics"
    }

    /**
     * 重复规则：每天都执行时沿用 `FREQ=DAILY`，只在部分日子执行时改用 `FREQ=WEEKLY;BYDAY=…`。
     *
     * 工作日 / 休息日没法用 RRULE 表达「跳过法定节假日」，这里退化成「周一至周五」
     * 与「周六日」——日历客户端拿到的仍是可用的事件，细节差异写进描述里。
     *
     * @param plan 计划信息
     * @return 一行 RRULE
     */
    private fun rruleOf(plan: IcsPlan): String {
        val until = dateOf(plan.end)
        return when (plan.dayRule) {
            DayRule.WORKDAY -> "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=$until"
            DayRule.REST_DAY -> "RRULE:FREQ=WEEKLY;BYDAY=SA,SU;UNTIL=$until"
            else -> {
                val mask = if (plan.weekdaysMask == WeekdayMask.NONE) WeekdayMask.ALL else plan.weekdaysMask
                if (mask == WeekdayMask.ALL) {
                    "RRULE:FREQ=DAILY;UNTIL=$until"
                } else {
                    val byDay = WeekdayMask.ORDER
                        .filter { (mask and it) != 0 }
                        .joinToString(",") { ICS_WEEKDAYS[WeekdayMask.ORDER.indexOf(it)] }
                    "RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=$until"
                }
            }
        }
    }

    /** iCalendar 的星期缩写，顺序对应 [WeekdayMask.ORDER]（周一起） */
    private val ICS_WEEKDAYS = arrayOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /**
     * 事件标题：计划名 + 执行规则 + 每日目标，日历格子窄，尽量短。
     *
     * @param plan 计划信息
     * @return 标题
     */
    fun summaryOf(plan: IcsPlan): String {
        val title = plan.title.ifBlank { "未命名计划" }
        val rule = PlanSchedule.describe(plan.weekdaysMask, plan.dayRule)
        val everyDay = rule == "每天"
        return when {
            plan.dailyGoalMinutes > 0 && everyDay -> "$title · 每天 ${plan.dailyGoalMinutes} 分钟"
            plan.dailyGoalMinutes > 0 -> "$title · $rule · ${plan.dailyGoalMinutes} 分钟"
            everyDay -> title
            else -> "$title · $rule"
        }
    }

    /**
     * 事件描述：备注 + 区间与天数 + 执行规则。
     *
     * @param plan 计划信息
     * @return 描述
     */
    fun descriptionOf(plan: IcsPlan): String {
        // 天数按真实执行日算：只在周末的计划不该写「共 30 天」
        val days = PlanSchedule.countActive(plan.start, plan.end, plan.weekdaysMask, plan.dayRule)
        val builder = StringBuilder()
        if (plan.note.isNotBlank()) {
            builder.append(plan.note.trim()).append('\n')
        }
        builder.append("共 ").append(days).append(" 天")
        if (plan.dailyGoalMinutes > 0) {
            builder.append("，每天目标 ").append(plan.dailyGoalMinutes).append(" 分钟")
        }
        val rule = PlanSchedule.describe(plan.weekdaysMask, plan.dayRule)
        if (rule != "每天") {
            builder.append("，执行日：").append(rule)
        }
        builder.append("。由「暖历」导出。")
        return builder.toString()
    }

    /**
     * 文件名：计划名 + `.ics`，去掉文件名不允许的字符。
     *
     * @param plan 计划信息
     * @return 文件名
     */
    fun fileNameOf(plan: IcsPlan): String {
        return sanitizeFileName(plan.title.ifBlank { "计划" }) + ".ics"
    }

    /**
     * 文件名清洗：去掉路径分隔符等非法字符，并避免空名。
     *
     * @param raw 原始名称
     * @return 安全文件名（不含扩展名）
     */
    private fun sanitizeFileName(raw: String): String {
        val safe = raw.map { ch ->
            if (ch in "\\/:*?\"<>|" || ch == '\n' || ch == '\r') '_' else ch
        }.joinToString("").trim().trim('.')
        return safe.ifBlank { "计划" }
    }

    /**
     * 转义 iCalendar 文本：反斜杠、分号、逗号与换行。
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    fun escape(text: String): String {
        val builder = StringBuilder(text.length + 8)
        text.forEach { ch ->
            when (ch) {
                '\\' -> builder.append("\\\\")
                ';' -> builder.append("\\;")
                ',' -> builder.append("\\,")
                '\n' -> builder.append("\\n")
                '\r' -> Unit
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    /**
     * 按 75 字节折行：续行以一个空格开头，中文按 UTF-8 三字节计算，
     * 不能按字符数切，否则中文长标题会被切成非法字节序列。
     *
     * @param line 单行内容（不含换行）
     * @return 折行后的内容，中间用 CRLF + 空格连接
     */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_BYTES) {
            return line
        }
        val segments = mutableListOf<String>()
        val builder = StringBuilder()
        var bytes = 0
        // 首行 75 字节，续行开头要留一个空格，因此可用 74 字节
        var limit = MAX_LINE_BYTES
        // 按「码点」而不是 Char 遍历：emoji 是代理对，按 Char 会把 4 字节算成 1+1，
        // 既会算错行宽，也可能把 emoji 从中间切断产生非法 UTF-8
        for (cp in line.codePoints().toArray()) {
            val ch = String(Character.toChars(cp))
            val size = ch.toByteArray(Charsets.UTF_8).size
            if (bytes + size > limit) {
                segments += builder.toString()
                builder.clear()
                bytes = 0
                limit = MAX_LINE_BYTES - 1
            }
            builder.append(ch)
            bytes += size
        }
        if (builder.isNotEmpty()) {
            segments += builder.toString()
        }
        return segments.joinToString("\r\n ")
    }

    /** 一天毫秒数 */
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * 稳定的 UID：同一个计划每次导出都一样，重复导入不会产生两条。
     *
     * @param plan 计划信息
     * @return UID 文本
     */
    private fun uidOf(plan: IcsPlan): String {
        return "focuslist-plan-${plan.id}@focuslist.app"
    }

    /**
     * 日期格式化为 `yyyyMMdd`（整天事件不带时区）。
     *
     * @param day 当天任意时刻
     * @return 日期串
     */
    private fun dateOf(day: Long): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(day))
    }

    /**
     * 生成时刻格式化为 UTC 的 `yyyyMMdd'T'HHmmss'Z'`。
     *
     * @param now 毫秒时间戳
     * @return UTC 时间串
     */
    private fun stampOf(now: Long): String {
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(now))
    }
}
