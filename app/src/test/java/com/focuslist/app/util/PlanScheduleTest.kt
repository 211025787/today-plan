package com.focuslist.app.util

import com.focuslist.app.data.local.entity.PlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划执行日规则 [PlanSchedule] 的单元测试。
 *
 * 覆盖三件事：周几掩码的位映射、法定节假日/调休的判定、以及「区间里到底有几天要做」。
 */
class PlanScheduleTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 造一个计划：只关心执行规则，其它字段随手给默认值 */
    private fun plan(
        start: String,
        end: String,
        mask: Int = WeekdayMask.NONE,
        rule: Int = DayRule.EVERY_DAY
    ): PlanEntity = PlanEntity(
        title = "写作",
        startDate = day(start),
        endDate = day(end),
        weekdaysMask = mask,
        dayRule = rule
    )

    // ── 周几掩码 ─────────────────────────────────────────────────────────────

    @Test
    fun weekdayBitsMatchCalendar() {
        // 2026-11-02 是周一，往后依次到周日
        val expected = listOf(
            "2026-11-02" to WeekdayMask.MON,
            "2026-11-03" to WeekdayMask.TUE,
            "2026-11-04" to WeekdayMask.WED,
            "2026-11-05" to WeekdayMask.THU,
            "2026-11-06" to WeekdayMask.FRI,
            "2026-11-07" to WeekdayMask.SAT,
            "2026-11-08" to WeekdayMask.SUN
        )
        expected.forEach { (date, bit) ->
            assertTrue("$date 应命中 $bit", WeekdayMask.contains(bit, day(date)))
        }
    }

    @Test
    fun emptyMaskMeansEveryDay() {
        // 老计划没有掩码字段，落库为 0，必须当成「每天」
        assertEquals(7, WeekdayMask.count(WeekdayMask.NONE))
        assertTrue(WeekdayMask.contains(WeekdayMask.NONE, day("2026-11-04")))
    }

    @Test
    fun maskSummaryText() {
        assertEquals("每天", WeekdayMask.summary(WeekdayMask.ALL))
        assertEquals("周一至周五", WeekdayMask.summary(WeekdayMask.WEEKDAYS))
        assertEquals("周末", WeekdayMask.summary(WeekdayMask.WEEKEND))
        assertEquals(
            "每周一、三、五",
            WeekdayMask.summary(WeekdayMask.MON or WeekdayMask.WED or WeekdayMask.FRI)
        )
    }

    // ── 法定节假日与调休 ─────────────────────────────────────────────────────

    @Test
    fun nationalDayIsHolidayNotWorkday() {
        assertTrue(ChinaHolidays.isHoliday(day("2026-10-02")))
        assertFalse(ChinaHolidays.isWorkday(day("2026-10-02")))
        assertTrue(ChinaHolidays.isRestDay(day("2026-10-02")))
    }

    @Test
    fun makeupSaturdayIsWorkday() {
        // 2026-10-10 周六，被调休成上班日
        assertTrue(ChinaHolidays.isWorkday(day("2026-10-10")))
        assertFalse(ChinaHolidays.isRestDay(day("2026-10-10")))
        // 相邻的普通周日仍是休息日
        assertTrue(ChinaHolidays.isRestDay(day("2026-10-11")))
    }

    @Test
    fun springFestivalCoversNineDays() {
        assertTrue(ChinaHolidays.isRestDay(day("2026-02-15")))
        assertTrue(ChinaHolidays.isRestDay(day("2026-02-23")))
        assertFalse(ChinaHolidays.isRestDay(day("2026-02-14")))
        assertFalse(ChinaHolidays.isRestDay(day("2026-02-24")))
    }

    // ── 区间内的执行日 ───────────────────────────────────────────────────────

    @Test
    fun everyDayCountsCalendarDays() {
        assertEquals(30, PlanSchedule.countActive(plan("2026-11-01", "2026-11-30")))
    }

    @Test
    fun weekendOnlySkipsWeekdays() {
        // 2026-11 的周末：1/7/8/14/15/21/22/28/29，共 9 天
        val weekend = plan("2026-11-01", "2026-11-30", WeekdayMask.WEEKEND)
        assertEquals(9, PlanSchedule.countActive(weekend))
        assertEquals(day("2026-11-01"), PlanSchedule.activeDays(weekend).first())
        assertFalse(PlanSchedule.isActive(weekend, day("2026-11-03")))
        assertTrue(PlanSchedule.isActive(weekend, day("2026-11-07")))
    }

    @Test
    fun workdayRuleSkipsHolidays() {
        // 2026-10-01 ~ 10-07 国庆放假，10-08 起才是工作日
        val workday = plan("2026-10-01", "2026-10-11", rule = DayRule.WORKDAY)
        assertFalse(PlanSchedule.isActive(workday, day("2026-10-05")))
        assertTrue(PlanSchedule.isActive(workday, day("2026-10-08")))
        // 10-08(四) 09(五) 10(六调休上班) 共 3 天；10-11 周日不上班
        assertEquals(3, PlanSchedule.countActive(workday))
    }

    @Test
    fun restDayRuleIncludesHolidays() {
        // 10-01 ~ 10-07 全是休息日，10-10 调休上班不算
        val rest = plan("2026-10-01", "2026-10-11", rule = DayRule.REST_DAY)
        assertTrue(PlanSchedule.isActive(rest, day("2026-10-05")))
        assertFalse(PlanSchedule.isActive(rest, day("2026-10-10")))
        // 10/1-10/7 国庆 7 天 + 10/11 周日 1 天 = 8 天
        assertEquals(8, PlanSchedule.countActive(rest))
    }

    @Test
    fun activeIndexCountsFromStart() {
        val weekend = plan("2026-11-01", "2026-11-30", WeekdayMask.WEEKEND)
        // 11-01 是第 1 个执行日，11-08 是第 3 个
        assertEquals(1, PlanSchedule.activeIndexUntil(weekend, day("2026-11-01")))
        assertEquals(3, PlanSchedule.activeIndexUntil(weekend, day("2026-11-08")))
        // 计划开始前是 0
        assertEquals(0, PlanSchedule.activeIndexUntil(weekend, day("2026-10-25")))
    }

    @Test
    fun nextActiveSkipsGap() {
        val weekend = plan("2026-11-01", "2026-11-30", WeekdayMask.WEEKEND)
        // 周三是空档，下一个执行日是周六
        assertEquals(day("2026-11-07"), PlanSchedule.nextActive(weekend, day("2026-11-04")))
        // 已经在执行日上时返回当天
        assertEquals(day("2026-11-01"), PlanSchedule.nextActive(weekend, day("2026-11-01")))
        // 区间结束之后没有了
        assertNull(PlanSchedule.nextActive(weekend, day("2026-12-01")))
    }

    @Test
    fun lastActiveSkipsBackward() {
        val weekend = plan("2026-11-01", "2026-11-30", WeekdayMask.WEEKEND)
        assertEquals(day("2026-11-01"), PlanSchedule.lastActive(weekend, day("2026-11-04")))
    }

    @Test
    fun outOfRangeIsNeverActive() {
        val everyDay = plan("2026-11-01", "2026-11-30")
        assertFalse(PlanSchedule.isActive(everyDay, day("2026-10-31")))
        assertFalse(PlanSchedule.isActive(everyDay, day("2026-12-01")))
        assertTrue(PlanSchedule.isActive(everyDay, day("2026-11-30")))
    }

    @Test
    fun ruleTextForPlan() {
        assertEquals("每天", PlanSchedule.ruleText(plan("2026-11-01", "2026-11-30")))
        assertEquals(
            "每周二、四",
            PlanSchedule.ruleText(
                plan("2026-11-01", "2026-11-30", WeekdayMask.TUE or WeekdayMask.THU)
            )
        )
        assertEquals(
            "工作日",
            PlanSchedule.ruleText(plan("2026-11-01", "2026-11-30", rule = DayRule.WORKDAY))
        )
    }
}
