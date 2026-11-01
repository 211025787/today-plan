package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划时间轴 [PlanTimeline] 的单元测试。
 *
 * 固定窗口：2026-10-12（周一）起 6 周，覆盖 10-12 ~ 11-22，
 * 列下标 0..5 分别对应 10/12、10/19、10/26、11/02、11/09、11/16。
 */
class PlanTimelineTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 固定窗口 */
    private val window = TimelineWindow(start = day("2026-10-12"), weeks = 6)

    /** 构造一个计划区间 */
    private fun span(
        id: Long,
        start: String,
        end: String,
        title: String = "计划$id"
    ): PlanSpan = PlanSpan(id = id, title = title, start = day(start), end = day(end))

    @Test
    fun window_anchorsTodayInThirdColumn() {
        val w = PlanTimeline.window(day("2026-11-01"))

        assertEquals(day("2026-10-12"), w.start)
        assertEquals(6, w.weeks)
        assertEquals(2, PlanTimeline.todayColumn(w, day("2026-11-01")))
    }

    @Test
    fun window_startsOnMondayForMidWeekDay() {
        val w = PlanTimeline.window(day("2026-10-28"))

        assertEquals(day("2026-10-12"), w.start)
    }

    @Test
    fun window_coercesWeeksToAtLeastOne() {
        val w = PlanTimeline.window(day("2026-11-01"), weeks = 0)

        assertEquals(1, w.weeks)
        assertEquals(day("2026-10-26"), w.start)
    }

    @Test
    fun window_columnStartsAreWeekly() {
        val columns = window.columnStarts()

        assertEquals(6, columns.size)
        assertEquals(day("2026-10-12"), columns.first())
        assertEquals(day("2026-11-16"), columns.last())
        assertEquals(day("2026-10-19"), columns[1])
    }

    @Test
    fun window_endIsLastSunday() {
        assertEquals(day("2026-11-22"), window.end)
    }

    @Test
    fun layout_emptyInput() {
        assertEquals(emptyList<TimelineBar>(), PlanTimeline.layout(emptyList(), window))
        assertEquals(0, PlanTimeline.rowCount(emptyList()))
    }

    @Test
    fun layout_dropsSpansOutsideWindow() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-09-01", "2026-10-05"),
                span(2, "2026-11-25", "2026-12-31")
            ),
            window
        )

        assertEquals(emptyList<TimelineBar>(), bars)
    }

    @Test
    fun layout_clipsStartBeforeWindow() {
        val bars = PlanTimeline.layout(listOf(span(1, "2026-09-20", "2026-10-20")), window)

        assertEquals(0, bars[0].startCol)
        assertEquals(2, bars[0].spanCols)
    }

    @Test
    fun layout_clipsEndAfterWindow() {
        val bars = PlanTimeline.layout(listOf(span(1, "2026-11-18", "2026-12-30")), window)

        assertEquals(5, bars[0].startCol)
        assertEquals(1, bars[0].spanCols)
    }

    @Test
    fun layout_singleDayOccupiesOneColumn() {
        val bars = PlanTimeline.layout(listOf(span(1, "2026-10-14", "2026-10-14")), window)

        assertEquals(0, bars[0].startCol)
        assertEquals(1, bars[0].spanCols)
    }

    @Test
    fun layout_fullWindowOccupiesEveryColumn() {
        val bars = PlanTimeline.layout(listOf(span(1, "2025-01-01", "2027-01-01")), window)

        assertEquals(0, bars[0].startCol)
        assertEquals(6, bars[0].spanCols)
    }

    @Test
    fun layout_roundsMidWeekToWholeColumn() {
        // 周三到周五：仍算在 10/12 那一列
        val bars = PlanTimeline.layout(listOf(span(1, "2026-10-14", "2026-10-16")), window)

        assertEquals(0, bars[0].startCol)
        assertEquals(1, bars[0].spanCols)
    }

    @Test
    fun layout_overlappingSpansGoToDifferentRows() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-10-26", "2026-11-08", title = "写作"),
                span(2, "2026-11-02", "2026-11-10", title = "读书")
            ),
            window
        )

        assertEquals(2, bars[0].startCol)
        assertEquals(3, bars[0].startCol + bars[0].spanCols - 1)
        assertEquals(0, bars[0].row)
        assertEquals(1, bars[1].row)
    }

    @Test
    fun layout_adjacentSpansShareFirstRow() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-10-26", "2026-11-01"),
                span(2, "2026-11-02", "2026-11-08")
            ),
            window
        )

        assertEquals(0, bars[0].row)
        assertEquals(0, bars[1].row)
    }

    @Test
    fun layout_threeWayOverlapUsesThreeRows() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-10-26", "2026-11-08"),
                span(2, "2026-10-28", "2026-11-05"),
                span(3, "2026-11-01", "2026-11-03")
            ),
            window
        )

        assertEquals(listOf(0, 1, 2), bars.map { it.row })
        assertEquals(3, PlanTimeline.rowCount(bars))
    }

    @Test
    fun layout_orderedByStartColumn() {
        val bars = PlanTimeline.layout(
            listOf(
                span(3, "2026-11-09", "2026-11-15"),
                span(1, "2026-10-12", "2026-10-18"),
                span(2, "2026-10-26", "2026-10-27")
            ),
            window
        )

        assertEquals(listOf(1L, 2L, 3L), bars.map { it.plan.id })
        assertEquals(listOf(0, 2, 4), bars.map { it.startCol })
    }

    @Test
    fun layout_reusesRowAfterGap() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-10-12", "2026-10-25"),   // 列 0-1
                span(2, "2026-10-19", "2026-10-19"),   // 列 1，与 1 重叠 → 第 2 行
                span(3, "2026-11-02", "2026-11-08")    // 列 3，第 1 行空出来了
            ),
            window
        )

        assertEquals(1, bars.first { it.plan.id == 2L }.row)
        assertEquals(0, bars.first { it.plan.id == 3L }.row)
    }

    @Test
    fun layout_keepsEdgeColumns() {
        val bars = PlanTimeline.layout(
            listOf(
                span(1, "2026-10-12", "2026-10-12"),
                span(2, "2026-11-22", "2026-11-22")
            ),
            window
        )

        assertEquals(0, bars.first { it.plan.id == 1L }.startCol)
        assertEquals(5, bars.first { it.plan.id == 2L }.startCol)
    }

    @Test
    fun columnOf_clampsToWindow() {
        assertEquals(0, PlanTimeline.columnOf(window, day("2026-09-01")))
        assertEquals(5, PlanTimeline.columnOf(window, day("2027-01-01")))
        assertEquals(2, PlanTimeline.columnOf(window, day("2026-10-28")))
    }

    @Test
    fun todayColumn_isMinusOneOutsideWindow() {
        assertEquals(-1, PlanTimeline.todayColumn(window, day("2026-12-01")))
        assertTrue(PlanTimeline.contains(window, day("2026-10-12")))
        assertFalse(PlanTimeline.contains(window, day("2026-11-23")))
    }

    @Test
    fun text_helpersFormatChineseDates() {
        assertEquals("10月12日 - 11月22日", PlanTimeline.rangeText(window))
        assertEquals("10/12", PlanTimeline.label(day("2026-10-12")))

        val bar = PlanTimeline.layout(listOf(span(1, "2026-09-20", "2026-10-20")), window).first()
        assertEquals("10月12日 - 10月20日", PlanTimeline.barRangeText(window, bar))
    }
}
