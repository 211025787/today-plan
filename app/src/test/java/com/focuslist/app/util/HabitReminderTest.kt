package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 习惯提醒时间 [HabitReminders] 的单元测试。
 *
 * 全部用固定时刻断言，不依赖运行时的「今天」。
 */
class HabitReminderTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 日期时间串 → 时间戳 */
    private fun at(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 21:30 的提醒时间戳（日期部分无关紧要） */
    private val evening = at("2026-11-01T21:30:00")

    @Test
    fun nextTrigger_todayWhenStillAhead() {
        val now = at("2026-11-05T08:00:00")
        val next = HabitReminders.nextTriggerAt(evening, now)

        assertEquals(at("2026-11-05T21:30:00"), next)
    }

    @Test
    fun nextTrigger_tomorrowWhenAlreadyPassed() {
        val now = at("2026-11-05T22:00:00")
        val next = HabitReminders.nextTriggerAt(evening, now)

        assertEquals(at("2026-11-06T21:30:00"), next)
    }

    @Test
    fun nextTrigger_sameMinuteCountsAsPassed() {
        val now = at("2026-11-05T21:30:00")
        val next = HabitReminders.nextTriggerAt(evening, now)

        assertEquals(at("2026-11-06T21:30:00"), next)
    }

    @Test
    fun nextTrigger_oneSecondBeforeStillToday() {
        val now = at("2026-11-05T21:29:59")
        val next = HabitReminders.nextTriggerAt(evening, now)

        assertEquals(at("2026-11-05T21:30:00"), next)
    }

    @Test
    fun nextTrigger_handlesMidnightReminder() {
        val midnight = at("2026-11-01T00:00:00")
        val now = at("2026-11-05T23:59:00")

        assertEquals(at("2026-11-06T00:00:00"), HabitReminders.nextTriggerAt(midnight, now))
    }

    @Test
    fun nextTrigger_crossesMonthBoundary() {
        val now = at("2026-11-30T22:00:00")

        assertEquals(at("2026-12-01T21:30:00"), HabitReminders.nextTriggerAt(evening, now))
    }

    @Test
    fun nextTrigger_alwaysInFuture() {
        val now = at("2026-11-05T09:15:00")

        assertTrue(HabitReminders.nextTriggerAt(evening, now) > now)
    }

    @Test
    fun timeText_formatsHoursAndMinutes() {
        assertEquals("21:30", HabitReminders.timeText(evening))
        assertEquals("09:05", HabitReminders.timeText(at("2026-11-01T09:05:00")))
    }

    @Test
    fun timeText_blankWhenUnset() {
        assertEquals("", HabitReminders.timeText(null))
        assertEquals("", HabitReminders.timeText(0L))
        assertEquals("", HabitReminders.timeText(-1L))
    }

    @Test
    fun at_combinesDayAndTime() {
        val combined = HabitReminders.at(day("2026-11-05"), 7, 5)

        assertEquals(at("2026-11-05T07:05:00"), combined)
    }

    @Test
    fun at_clampsOutOfRangeTime() {
        assertEquals(at("2026-11-05T23:59:00"), HabitReminders.at(day("2026-11-05"), 99, 99))
    }

    @Test
    fun hourAndMinuteRoundTrip() {
        assertEquals(21, HabitReminders.hourOf(evening))
        assertEquals(30, HabitReminders.minuteOf(evening))
    }
}
