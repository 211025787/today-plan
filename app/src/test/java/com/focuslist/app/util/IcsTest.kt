package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日历导出 [Ics] 的单元测试。
 *
 * 重点在「各家日历客户端会不会解析失败」这件事上：CRLF、75 字节折行、转义。
 */
class IcsTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 基准计划：11/1 到 11/30，每天 30 分钟 */
    private val plan = IcsPlan(
        id = 7,
        title = "写作",
        note = "每天写一点",
        start = day("2026-11-01"),
        end = day("2026-11-30"),
        dailyGoalMinutes = 30
    )

    /** 固定生成时刻：2026-11-01 09:15 UTC */
    private val now = Instant.parse("2026-11-01T09:15:00Z").toEpochMilli()

    /** 生成文本并按行拆开（去掉 CR） */
    private fun lines(plan: IcsPlan = this.plan): List<String> =
        Ics.forPlan(plan, now).replace("\r\n", "\n").split("\n").filter { it.isNotEmpty() }

    @Test
    fun wrapperIsComplete() {
        val text = Ics.forPlan(plan, now)

        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.contains("VERSION:2.0\r\n"))
        assertTrue(text.contains("PRODID:-//FocusList//Today Plan//CN\r\n"))
        assertTrue(text.contains("BEGIN:VEVENT\r\n"))
        assertTrue(text.contains("END:VEVENT\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun usesCrlfLineEndings() {
        val text = Ics.forPlan(plan, now)
        val bareLf = text.replace("\r\n", "").contains("\n")

        assertFalse("存在裸 LF", bareLf)
    }

    @Test
    fun uidIsStable() {
        val a = Ics.forPlan(plan, now)
        val b = Ics.forPlan(plan, now + 3_600_000L)

        assertTrue(a.contains("UID:focuslist-plan-7@focuslist.app"))
        assertEquals(
            a.lines().first { it.startsWith("UID:") },
            b.lines().first { it.startsWith("UID:") }
        )
    }

    @Test
    fun dtStampIsUtc() {
        assertTrue(Ics.forPlan(plan, now).contains("DTSTAMP:20261101T091500Z"))
    }

    @Test
    fun allDayDatesAreInclusiveStartExclusiveEnd() {
        val list = lines()

        assertTrue(list.contains("DTSTART;VALUE=DATE:20261101"))
        // DTEND 是排他的，取截止日下一天
        assertTrue(list.contains("DTEND;VALUE=DATE:20261201"))
    }

    @Test
    fun repeatsDailyUntilEndDate() {
        assertTrue(lines().contains("RRULE:FREQ=DAILY;UNTIL=20261130"))
    }

    @Test
    fun singleDayPlanEndsNextDay() {
        val oneDay = plan.copy(start = day("2026-11-05"), end = day("2026-11-05"))
        val list = lines(oneDay)

        assertTrue(list.contains("DTSTART;VALUE=DATE:20261105"))
        assertTrue(list.contains("DTEND;VALUE=DATE:20261106"))
        assertTrue(list.contains("RRULE:FREQ=DAILY;UNTIL=20261105"))
    }

    @Test
    fun summaryMentionsDailyGoal() {
        assertTrue(lines().contains("SUMMARY:写作 · 每天 30 分钟"))
    }

    @Test
    fun summaryFallsBackToPlaceholder() {
        val blank = plan.copy(title = "   ", dailyGoalMinutes = 0)

        assertTrue(lines(blank).contains("SUMMARY:未命名计划"))
    }

    @Test
    fun summaryWithoutGoalOmitsMinutes() {
        val noGoal = plan.copy(dailyGoalMinutes = 0)

        assertTrue(lines(noGoal).contains("SUMMARY:写作"))
    }

    @Test
    fun escapesCommaSemicolonAndBackslash() {
        assertEquals("a\\,b\\;c\\\\d", Ics.escape("a,b;c\\d"))
    }

    @Test
    fun escapesNewlines() {
        assertEquals("第一行\\n第二行", Ics.escape("第一行\n第二行"))
        assertEquals("第一行\\n第二行", Ics.escape("第一行\r\n第二行"))
    }

    @Test
    fun descriptionIncludesNoteAndRange() {
        // 描述偏长会被折行，先展开再断言
        val unfolded = Ics.forPlan(plan, now).replace("\r\n ", "").replace("\r\n", "\n")
        val description = unfolded.lines().first { it.startsWith("DESCRIPTION:") }

        assertTrue(description.contains("每天写一点"))
        assertTrue(description.contains("共 30 天"))
        assertTrue(description.contains("每天目标 30 分钟"))
        assertTrue(description.contains("今日规划"))
    }

    @Test
    fun foldingKeepsLinesUnder75Bytes() {
        val longTitle = "这是一个特别长的计划名称用来测试折行是否正确处理中文字符的字节长度".repeat(2)
        val text = Ics.forPlan(plan.copy(title = longTitle), now)

        text.split("\r\n").forEach { line ->
            assertTrue("超长行：${line.length} 字符", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
        // 续行以空格开头
        assertTrue(text.contains("\r\n "))
    }

    @Test
    fun foldingKeepsContentLossless() {
        val longTitle = "写作".repeat(30)
        val text = Ics.forPlan(plan.copy(title = longTitle), now)
        val unfolded = text.replace("\r\n ", "")

        assertTrue(unfolded.contains(longTitle))
    }

    @Test
    fun fileNameSanitizesIllegalChars() {
        assertEquals("写作.ics", Ics.fileNameOf(plan))
        assertEquals("a_b_c.ics", Ics.fileNameOf(plan.copy(title = "a/b:c")))
        assertEquals("计划.ics", Ics.fileNameOf(plan.copy(title = "   ")))
    }

    @Test
    fun fileNameTrimsTrailingDots() {
        assertEquals("写作.ics", Ics.fileNameOf(plan.copy(title = "写作...")))
    }

    @Test
    fun everyLineIsNonBlank() {
        Ics.forPlan(plan, now).replace("\r\n", "\n").split("\n").forEach { line ->
            if (line.isNotEmpty()) {
                assertFalse("存在尾随空格", line.endsWith(" ") && line.length > 1)
            }
        }
    }

    @Test
    fun transparentSoItDoesNotMarkBusy() {
        assertTrue(lines().contains("TRANSP:TRANSPARENT"))
    }
}
