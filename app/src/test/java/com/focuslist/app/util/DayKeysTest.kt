package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DayKeys] 单元测试：日期串与本地 0 点时间戳互转，重点验证与 [DateUtils.startOfDay] 口径一致。
 */
class DayKeysTest {

    @Test
    fun format_thenParse_roundTripsToSameDayStart() {
        val now = System.currentTimeMillis()
        val startOfDay = DateUtils.startOfDay(now)
        val key = DayKeys.format(startOfDay)
        assertEquals(startOfDay, DayKeys.parse(key))
    }

    @Test
    fun format_matchesIsoDateOfLocalDay() {
        val startOfDay = DateUtils.startOfDay(System.currentTimeMillis())
        val key = DayKeys.format(startOfDay)
        assertTrue("日期串应为 yyyy-MM-dd，实际 $key", Regex("\\d{4}-\\d{2}-\\d{2}").matches(key))
    }

    @Test
    fun parse_midnightOfSameDay_mapsToSameKey() {
        val startOfDay = DateUtils.startOfDay(System.currentTimeMillis())
        // 同一天的 23:59 与 00:00 应落到同一天
        val lateSameDay = startOfDay + 23L * 3600 * 1000 + 59 * 60 * 1000
        assertEquals(DayKeys.format(startOfDay), DayKeys.format(lateSameDay))
    }

    @Test
    fun parse_adjacentDays_differByOneDay() {
        val startOfDay = DateUtils.startOfDay(System.currentTimeMillis())
        val yesterday = DateUtils.addDays(startOfDay, -1)
        val diff = DayKeys.parse(DayKeys.format(startOfDay)) - DayKeys.parse(DayKeys.format(yesterday))
        assertTrue("相邻两天应相差一天毫秒数，实际 $diff", diff > 0 && diff <= 2 * DateUtils.DAY_MILLIS && diff >= DateUtils.DAY_MILLIS - 3600_000)
    }

    @Test
    fun parse_invalidInput_returnsZero() {
        assertEquals(0L, DayKeys.parse("not-a-date"))
        assertEquals(0L, DayKeys.parse(""))
    }
}
