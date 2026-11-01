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
 */
data class IcsPlan(
    val id: Long,
    val title: String,
    val note: String = "",
    val start: Long,
    val end: Long,
    val dailyGoalMinutes: Int = 0
)

/**
 * 把计划导出成 iCalendar（.ics）文件。
 *
 * 这是应用与外部世界唯一的桥：本身不联网、不申请日历权限，只生成一个文件，
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
        lines += "DTSTART;VALUE=DATE:${dateOf(plan.start)}"
        // 整天事件的 DTEND 是排他的，所以取截止日的下一天
        lines += "DTEND;VALUE=DATE:${dateOf(DateUtils.addDays(plan.end, 1))}"
        lines += "RRULE:FREQ=DAILY;UNTIL=${dateOf(plan.end)}"
        lines += "SUMMARY:${escape(summaryOf(plan))}"
        lines += "DESCRIPTION:${escape(descriptionOf(plan))}"
        lines += "TRANSP:TRANSPARENT"
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"

        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /**
     * 事件标题：计划名 + 每日目标，日历格子窄，尽量短。
     *
     * @param plan 计划信息
     * @return 标题
     */
    fun summaryOf(plan: IcsPlan): String {
        val title = plan.title.ifBlank { "未命名计划" }
        return if (plan.dailyGoalMinutes > 0) {
            "$title · 每天 ${plan.dailyGoalMinutes} 分钟"
        } else {
            title
        }
    }

    /**
     * 事件描述：备注 + 区间与天数。
     *
     * @param plan 计划信息
     * @return 描述
     */
    fun descriptionOf(plan: IcsPlan): String {
        val days = (plan.end - plan.start) / DAY_MILLIS + 1
        val builder = StringBuilder()
        if (plan.note.isNotBlank()) {
            builder.append(plan.note.trim()).append('\n')
        }
        builder.append("共 ").append(days).append(" 天")
        if (plan.dailyGoalMinutes > 0) {
            builder.append("，每天目标 ").append(plan.dailyGoalMinutes).append(" 分钟")
        }
        builder.append("。由「今日规划」导出。")
        return builder.toString()
    }

    /**
     * 文件名：计划名 + `.ics`，去掉文件名不允许的字符。
     *
     * @param plan 计划信息
     * @return 文件名
     */
    fun fileNameOf(plan: IcsPlan): String {
        val raw = plan.title.ifBlank { "计划" }
        val safe = raw.map { ch ->
            if (ch in "\\/:*?\"<>|" || ch == '\n' || ch == '\r') '_' else ch
        }.joinToString("").trim().trim('.')
        return (safe.ifBlank { "计划" }) + ".ics"
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
        for (ch in line) {
            val size = ch.toString().toByteArray(Charsets.UTF_8).size
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
