package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 日期与时间工具的单元测试：覆盖"当天 0 点"、跨月加减、周起点与倒计时格式。
 */
class DateUtilsTest {

    /** 用本地时区构造一个确定的时间戳：2026-10-31 15:26:31.400 */
    private fun sampleTime(): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 31, 15, 26, 31)
        set(Calendar.MILLISECOND, 400)
    }.timeInMillis

    @Test
    fun startOfDay_zeroesTimeFields() {
        val dayStart = DateUtils.startOfDay(sampleTime())
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
        assertTrue(dayStart <= sampleTime())
    }

    @Test
    fun endOfDay_isLastMillisecondOfDay() {
        val start = DateUtils.startOfDay(sampleTime())
        assertEquals(start + DateUtils.DAY_MILLIS - 1, DateUtils.endOfDay(sampleTime()))
    }

    @Test
    fun addDays_crossesMonthBoundary() {
        val oct31 = DateUtils.startOfDay(sampleTime())
        val nov1 = DateUtils.addDays(oct31, 1)
        val cal = Calendar.getInstance().apply { timeInMillis = nov1 }
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        // 负向偏移回到同一天
        assertEquals(oct31, DateUtils.addDays(nov1, -1))
    }

    @Test
    fun startOfWeek_isMondayAndNotAfterToday() {
        val weekStart = DateUtils.startOfWeek(sampleTime())
        val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertTrue(weekStart <= DateUtils.startOfDay(sampleTime()))
    }

    @Test
    fun startOfMonth_isFirstDay() {
        val monthStart = DateUtils.startOfMonth(sampleTime())
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun formatCountdown_handlesZeroNormalAndNegative() {
        assertEquals("00:00", DateUtils.formatCountdown(0))
        assertEquals("01:05", DateUtils.formatCountdown(65))
        assertEquals("25:00", DateUtils.formatCountdown(1500))
        assertEquals("60:00", DateUtils.formatCountdown(3600))
        // 超出目标后继续计时显示为负值
        assertEquals("-03:20", DateUtils.formatCountdown(-200))
    }
}
