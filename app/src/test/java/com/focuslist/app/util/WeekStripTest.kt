package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 周条 [WeekStrip] 的单元测试：周一起点、四档完成度、本周小结与翻周。
 *
 * 用固定日期而不是「今天」来断言，保证用例不受运行时间影响。
 */
class WeekStripTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 2026-10-26 是周一，11-01 是周日 */
    private val monday = day("2026-10-26")
    private val sunday = day("2026-11-01")

    /** 用固定表提供每天分钟与目标 */
    private fun week(
        anchor: Long,
        today: Long = sunday,
        minutes: Map<Long, Int> = emptyMap(),
        goals: Map<Long, Int> = emptyMap()
    ): List<WeekDay> = WeekStrip.of(
        anchor = anchor,
        today = today,
        minutesOf = { minutes[it] ?: 0 },
        goalOf = { goals[it] ?: 0 }
    )

    @Test
    fun of_startsOnMonday() {
        val days = week(day("2026-10-28"))

        assertEquals(7, days.size)
        assertEquals(monday, days.first().start)
        assertEquals(sunday, days.last().start)
    }

    @Test
    fun of_listsSevenConsecutiveDays() {
        val days = week(monday)

        days.forEachIndexed { index, item ->
            assertEquals(DateUtils.addDays(monday, index), item.start)
        }
    }

    @Test
    fun of_labelsRunMondayToSunday() {
        val days = week(monday)

        assertEquals(listOf("一", "二", "三", "四", "五", "六", "日"), days.map { it.weekdayLabel })
    }

    @Test
    fun of_marksOnlyToday() {
        val days = week(monday, today = day("2026-10-29"))

        assertEquals(1, days.count { it.isToday })
        assertTrue(days[3].isToday)
        assertFalse(days[0].isToday)
    }

    @Test
    fun of_readsMinutesAndGoalsFromProviders() {
        val days = week(
            monday,
            minutes = mapOf(monday to 30, day("2026-10-27") to 10),
            goals = mapOf(monday to 30, day("2026-10-27") to 40)
        )

        assertEquals(30, days[0].minutes)
        assertEquals(30, days[0].goalMinutes)
        assertEquals(WeekStrip.LEVEL_DONE, days[0].level)
        assertEquals(10, days[1].minutes)
        assertEquals(WeekStrip.LEVEL_STARTED, days[1].level)
        assertEquals(0, days[2].minutes)
        assertEquals(WeekStrip.LEVEL_EMPTY, days[2].level)
    }

    @Test
    fun of_crossesMonthBoundary() {
        val days = week(sunday)

        assertEquals(26, days.first().dayOfMonth)
        assertEquals(1, days.last().dayOfMonth)
        assertEquals(sunday, days.last().start)
    }

    @Test
    fun level_splitsAtHalfAndGoal() {
        assertEquals(WeekStrip.LEVEL_EMPTY, WeekStrip.level(0, 30))
        assertEquals(WeekStrip.LEVEL_STARTED, WeekStrip.level(1, 30))
        assertEquals(WeekStrip.LEVEL_STARTED, WeekStrip.level(14, 30))
        assertEquals(WeekStrip.LEVEL_HALF, WeekStrip.level(15, 30))
        assertEquals(WeekStrip.LEVEL_HALF, WeekStrip.level(29, 30))
        assertEquals(WeekStrip.LEVEL_DONE, WeekStrip.level(30, 30))
        assertEquals(WeekStrip.LEVEL_DONE, WeekStrip.level(90, 30))
    }

    @Test
    fun level_withoutGoalOnlyTellsStarted() {
        assertEquals(WeekStrip.LEVEL_EMPTY, WeekStrip.level(0, 0))
        assertEquals(WeekStrip.LEVEL_STARTED, WeekStrip.level(5, 0))
        assertEquals(WeekStrip.LEVEL_STARTED, WeekStrip.level(5, -10))
    }

    @Test
    fun level_negativeMinutesCountAsEmpty() {
        assertEquals(WeekStrip.LEVEL_EMPTY, WeekStrip.level(-5, 30))
    }

    @Test
    fun summary_withoutPlans() {
        val days = week(monday)

        assertEquals("本周还没有计划，安排一个吧", WeekStrip.summary(days))
    }

    @Test
    fun summary_withPlansButNoMinutes() {
        val days = week(monday, goals = mapOf(monday to 30))

        assertEquals("本周还没有投入，从今天开始", WeekStrip.summary(days))
    }

    @Test
    fun summary_countsDoneDaysAndDuration() {
        val minutes = mapOf(
            monday to 30,
            day("2026-10-27") to 60,
            day("2026-10-28") to 30,
            day("2026-10-29") to 140
        )
        val goals = mapOf(
            monday to 30,
            day("2026-10-27") to 60,
            day("2026-10-28") to 30,
            day("2026-10-29") to 60
        )
        val days = week(monday, minutes = minutes, goals = goals)

        assertEquals("本周 4/7 天达标 · 4 小时 20 分", WeekStrip.summary(days))
    }

    @Test
    fun summary_usesMinutesWhenUnderAnHour() {
        val days = week(monday, minutes = mapOf(monday to 45), goals = mapOf(monday to 30))

        assertEquals("本周 1/7 天达标 · 45 分钟", WeekStrip.summary(days))
    }

    @Test
    fun summary_emptyListIsBlank() {
        assertEquals("", WeekStrip.summary(emptyList()))
    }

    @Test
    fun shift_movesByWholeWeeks() {
        val next = WeekStrip.shift(monday, 1)
        val prev = WeekStrip.shift(monday, -1)

        assertEquals(day("2026-11-02"), next)
        assertEquals(day("2026-10-19"), prev)
    }

    @Test
    fun shift_keepsSameWeekday() {
        val wednesday = day("2026-10-28")
        val nextWeek = WeekStrip.shift(wednesday, 1)
        val days = WeekStrip.of(nextWeek, sunday, { 0 }, { 0 })

        assertEquals("三", days[2].weekdayLabel)
        assertEquals(day("2026-11-04"), days[2].start)
    }

    @Test
    fun dayOfMonth_readsCalendarDay() {
        assertEquals(1, WeekStrip.dayOfMonth(sunday))
        assertEquals(31, WeekStrip.dayOfMonth(day("2026-10-31")))
    }

    @Test
    fun durationText_switchesUnits() {
        assertEquals("0 分钟", WeekStrip.durationText(0))
        assertEquals("45 分钟", WeekStrip.durationText(45))
        assertEquals("2 小时", WeekStrip.durationText(120))
        assertEquals("1 小时 20 分", WeekStrip.durationText(80))
    }

    @Test
    fun of_marksFutureDays() {
        val days = week(monday, today = day("2026-10-28"))

        assertEquals(3, days.count { !it.isFuture })
        assertEquals(4, days.count { it.isFuture })
        assertFalse(days[2].isFuture)
        assertTrue(days[3].isFuture)
    }

    @Test
    fun summary_denominatorCountsElapsedDaysOnly() {
        val days = week(
            monday,
            today = day("2026-10-28"),
            minutes = mapOf(monday to 30),
            goals = mapOf(monday to 30)
        )

        assertEquals("本周 1/3 天达标 · 30 分钟", WeekStrip.summary(days))
    }

    @Test
    fun summary_futureWeekSaysNotStarted() {
        val days = week(
            day("2026-11-02"),
            today = sunday,
            goals = mapOf(day("2026-11-02") to 30)
        )

        assertEquals("这一周还没有开始，已安排 1 天", WeekStrip.summary(days))
    }

    @Test
    fun summary_futureWeekWithoutPlans() {
        val days = week(day("2026-11-02"), today = sunday)

        assertEquals("这一周还没有安排计划", WeekStrip.summary(days))
    }
}
