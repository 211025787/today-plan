package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 行程导出到日历 [Ics.forTrip] 的单元测试。
 *
 * 行程是「一个覆盖整段日期的整天事件」，与计划的「每天重复」不同，重点验证日期边界与描述。
 */
class IcsTripTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val trip = IcsTrip(
        id = 9,
        title = "西安三日",
        note = "高铁往返",
        start = day("2026-11-01"),
        end = day("2026-11-03"),
        stops = listOf("西安北", "兵马俑", "回民街")
    )

    /** 固定生成时刻：2026-11-01 09:15 UTC */
    private val now = Instant.parse("2026-11-01T09:15:00Z").toEpochMilli()

    private fun lines(trip: IcsTrip = this.trip, now: Long = this.now): List<String> =
        Ics.forTrip(trip, now).replace("\r\n", "\n").split("\n").filter { it.isNotEmpty() }

    @Test
    fun wrapperHasSingleEvent() {
        val list = lines()

        assertEquals("BEGIN:VCALENDAR", list.first())
        assertEquals("END:VCALENDAR", list.last())
        assertEquals(1, list.count { it == "BEGIN:VEVENT" })
        assertEquals(1, list.count { it == "END:VEVENT" })
    }

    @Test
    fun onlyCrlfLineEndings() {
        val text = Ics.forTrip(trip, now)

        assertFalse("存在裸 LF", text.replace("\r\n", "").contains("\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
    }

    @Test
    fun allDayDatesAreInclusiveStartExclusiveEnd() {
        val list = lines()

        assertTrue(list.contains("DTSTART;VALUE=DATE:20261101"))
        // 11/3 是行程最后一天，DTEND 取 11/4
        assertTrue(list.contains("DTEND;VALUE=DATE:20261104"))
    }

    @Test
    fun tripNeverRepeatsDaily() {
        assertFalse(lines().any { it.startsWith("RRULE:") })
    }

    @Test
    fun uidIsStableAndDistinctFromPlan() {
        val a = Ics.forTrip(trip, now)
        val b = Ics.forTrip(trip, now + 3_600_000L)

        assertTrue(a.contains("UID:focuslist-trip-9@focuslist.app"))
        assertTrue(b.contains("UID:focuslist-trip-9@focuslist.app"))
    }

    @Test
    fun dtStampIsUtc() {
        assertTrue(lines().contains("DTSTAMP:20261101T091500Z"))
    }

    @Test
    fun descriptionListsRouteAndNote() {
        val description = lines().first { it.startsWith("DESCRIPTION:") }

        assertTrue(description.contains("高铁往返"))
        assertTrue(description.contains("西安北 → 兵马俑 → 回民街"))
        assertTrue(description.contains("\\n"))
    }

    @Test
    fun summaryCarriesTripTitle() {
        assertTrue(lines().contains("SUMMARY:🗺 西安三日"))
    }

    @Test
    fun noDatesFallsBackToSingleDay() {
        val undated = trip.copy(start = 0L, end = 0L)
        val list = lines(undated, now = day("2026-05-06") + 9 * 3600_000L)

        assertTrue(list.contains("DTSTART;VALUE=DATE:20260506"))
        assertTrue(list.contains("DTEND;VALUE=DATE:20260507"))
    }

    @Test
    fun endBeforeStartIsClamped() {
        val reversed = trip.copy(end = day("2026-10-20"))

        assertTrue(lines(reversed).contains("DTEND;VALUE=DATE:20261102"))
    }

    @Test
    fun escapesSpecialCharacters() {
        val tricky = trip.copy(title = "西安,三日;回程", note = "备注\\换行")

        val summary = lines(tricky).first { it.startsWith("SUMMARY:") }
        assertTrue(summary.contains("西安\\,三日\\;回程"))
    }

    @Test
    fun longChineseTitleStillFoldsWithin75Bytes() {
        val long = trip.copy(title = "西安三日游".repeat(20))
        val text = Ics.forTrip(long, now)

        text.split("\r\n").forEach { line ->
            assertTrue("超长行", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun fileNameIsSafe() {
        assertEquals("西安三日.ics", Ics.tripFileNameOf(trip))
        assertEquals("a_b_c.ics", Ics.tripFileNameOf(trip.copy(title = "a/b:c")))
        assertEquals("行程.ics", Ics.tripFileNameOf(trip.copy(title = "   ")))
    }
}
