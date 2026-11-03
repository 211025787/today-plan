package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行程文案与汇总的单元测试：时长口径、距离分级、天数换算与停留 / 交通汇总。
 */
class TravelFormatTest {

    @Test
    fun duration_belowOneHour() {
        assertEquals("0 分钟", TravelFormat.duration(0))
        assertEquals("1 分钟", TravelFormat.duration(1))
        assertEquals("45 分钟", TravelFormat.duration(45))
        assertEquals("59 分钟", TravelFormat.duration(59))
    }

    @Test
    fun duration_hoursAndMinutes() {
        assertEquals("1 小时", TravelFormat.duration(60))
        assertEquals("1 小时 30 分", TravelFormat.duration(90))
        assertEquals("2 小时", TravelFormat.duration(120))
        assertEquals("12 小时", TravelFormat.duration(720))
    }

    @Test
    fun duration_daysAndHours() {
        assertEquals("1 天", TravelFormat.duration(1440))
        assertEquals("1 天 1 小时", TravelFormat.duration(1500))
        assertEquals("2 天", TravelFormat.duration(2880))
        assertEquals("1 天 6 小时", TravelFormat.duration(1800))
    }

    @Test
    fun duration_roundsUpNearFullDay() {
        assertEquals("2 天", TravelFormat.duration(2850))
    }

    @Test
    fun duration_negativeTreatedAsZero() {
        assertEquals("0 分钟", TravelFormat.duration(-30))
    }

    @Test
    fun stay_texts() {
        assertEquals("未安排停留", TravelFormat.stay(0))
        assertEquals("停留 2 天", TravelFormat.stay(2880))
        assertEquals("未安排", TravelFormat.stayShort(0))
        assertEquals("3 小时", TravelFormat.stayShort(180))
    }

    @Test
    fun distance_scales() {
        assertEquals("—", TravelFormat.distance(0))
        assertEquals("820 米", TravelFormat.distance(820))
        assertEquals("999 米", TravelFormat.distance(999))
        assertEquals("1.0 公里", TravelFormat.distance(1_000))
        assertEquals("12.4 公里", TravelFormat.distance(12_400))
        assertEquals("1,240 公里", TravelFormat.distance(1_240_000))
    }

    @Test
    fun days_roundsUpAndHandlesEmpty() {
        assertEquals("", TravelFormat.days(0, false))
        assertEquals("约 1 天", TravelFormat.days(0, true))
        assertEquals("约 1 天", TravelFormat.days(1440, true))
        assertEquals("约 2 天", TravelFormat.days(1441, true))
        assertEquals("约 9 天", TravelFormat.days(9 * 1440, true))
    }

    @Test
    fun grouped_insertsThousandsSeparators() {
        assertEquals("0", TravelFormat.grouped(0))
        assertEquals("999", TravelFormat.grouped(999))
        assertEquals("1,000", TravelFormat.grouped(1_000))
        assertEquals("1,240", TravelFormat.grouped(1_240))
        assertEquals("1,234,567", TravelFormat.grouped(1_234_567))
    }

    @Test
    fun transfer_joinsStationsAndService() {
        assertEquals(
            "北京南站 → 上海虹桥站 · G1",
            TravelFormat.transfer("北京南站", "上海虹桥站", "G1")
        )
        assertEquals("从 成都东站 出发", TravelFormat.transfer("成都东站", "", ""))
        assertEquals("到达 西湖", TravelFormat.transfer("", "西湖", ""))
        assertEquals("302路", TravelFormat.transfer("", "", "302路"))
        assertEquals("", TravelFormat.transfer("", "", ""))
    }

    @Test
    fun summary_sumsStayTravelAndDistance() {
        val summary = TravelSummaries.of(
            stayMinutes = listOf(1440, 2880),
            legMinutes = listOf(120, 0),
            distances = listOf(1_000L, 0L)
        )
        assertEquals(2, summary.stopCount)
        assertEquals(4320, summary.stayMinutes)
        assertEquals(120, summary.travelMinutes)
        assertEquals(1000L, summary.distanceMeters)
        assertEquals(3, summary.approxDays)
        assertEquals(4440, summary.totalMinutes)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun summary_daysTakeLongerOfStayAndTravel() {
        // 5 天停留 + 15 小时车程仍在 5 天内完成
        assertEquals(
            5,
            TravelSummaries.of(listOf(1440, 1440, 1440, 1440, 1440), listOf(900), emptyList()).approxDays
        )
        // 1 天停留 + 30 小时火车会跨到 2 天
        assertEquals(
            2,
            TravelSummaries.of(listOf(1440), listOf(1800), emptyList()).approxDays
        )
        // 停留很短但交通很久，也至少按交通天数算
        assertEquals(
            2,
            TravelSummaries.of(listOf(60), listOf(1500), emptyList()).approxDays
        )
    }

    @Test
    fun summary_ignoresNegativeValues() {
        val summary = TravelSummaries.of(
            stayMinutes = listOf(-10, 60),
            legMinutes = listOf(-5, 30),
            distances = listOf(-100L, 500L)
        )
        assertEquals(60, summary.stayMinutes)
        assertEquals(30, summary.travelMinutes)
        assertEquals(500L, summary.distanceMeters)
    }

    @Test
    fun summary_empty() {
        val summary = TravelSummaries.of(emptyList(), emptyList(), emptyList())
        assertEquals(0, summary.stopCount)
        assertEquals(0, summary.approxDays)
        assertTrue(summary.isEmpty)
    }
}
