package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 专注节律计算测试：时段聚合、黄金时段、峰值小时与工作日 / 周末对比。
 */
class RhythmTest {

    private fun hourList(vararg pairs: Pair<Int, Int>): List<Int> {
        val arr = IntArray(Rhythm.HOURS)
        pairs.forEach { (hour, minutes) -> arr[hour] = minutes }
        return arr.toList()
    }

    /** 取本周周一 0 点，作为星期相关断言的稳定基准 */
    private fun thisMonday(): Long = DateUtils.startOfWeek(System.currentTimeMillis())

    @Test
    fun normalize_补齐到24项且负值归零() {
        val norm = Rhythm.normalize(listOf(-5, 10, 3))
        assertEquals(24, norm.size)
        assertEquals(0, norm[0])
        assertEquals(10, norm[1])
        assertEquals(3, norm[2])
        assertEquals(0, norm[23])
    }

    @Test
    fun normalize_超出24小时的部分被截断() {
        val norm = Rhythm.normalize(List(30) { 1 })
        assertEquals(24, norm.size)
        assertEquals(24, norm.sum())
    }

    @Test
    fun slotOf_每4小时一档且越界夹取() {
        assertEquals(0, Rhythm.slotOf(0))
        assertEquals(0, Rhythm.slotOf(3))
        assertEquals(1, Rhythm.slotOf(4))
        assertEquals(2, Rhythm.slotOf(8))
        assertEquals(5, Rhythm.slotOf(23))
        assertEquals(0, Rhythm.slotOf(-1))
        assertEquals(5, Rhythm.slotOf(24))
    }

    @Test
    fun slotRange_起始与结束小时() {
        assertEquals(8, Rhythm.slotStartHour(2))
        assertEquals(12, Rhythm.slotEndHour(2))
        assertEquals(20, Rhythm.slotStartHour(5))
        assertEquals(24, Rhythm.slotEndHour(5))
        assertEquals("上午", Rhythm.slotName(2))
        assertEquals("晚间", Rhythm.slotName(5))
    }

    @Test
    fun sumBySlot_把小时聚合到6个时段() {
        val slots = Rhythm.sumBySlot(hourList(9 to 60, 14 to 20, 22 to 10))
        assertEquals(6, slots.size)
        assertEquals(60, slots[2])
        assertEquals(20, slots[3])
        assertEquals(10, slots[5])
        assertEquals(0, slots[0])
        assertEquals(90, slots.sum())
    }

    @Test
    fun total_等于全天累加() {
        assertEquals(90, Rhythm.total(hourList(9 to 60, 14 to 20, 22 to 10)))
        assertEquals(0, Rhythm.total(emptyList()))
    }

    @Test
    fun percent_边界与四舍五入() {
        assertEquals(0, Rhythm.percent(10, 0))
        assertEquals(0, Rhythm.percent(0, 10))
        assertEquals(25, Rhythm.percent(1, 4))
        assertEquals(75, Rhythm.percent(3, 4))
        assertEquals(33, Rhythm.percent(1, 3))
        assertEquals(67, Rhythm.percent(2, 3))
        assertEquals(100, Rhythm.percent(8, 8))
    }

    @Test
    fun share_各时段占比() {
        val share = Rhythm.share(hourList(9 to 60, 14 to 20))
        assertEquals(6, share.size)
        assertEquals(75, share[2])
        assertEquals(25, share[3])
        assertEquals(0, share[0])
        assertEquals(100, share.sum())
    }

    @Test
    fun bestSlot_全空返回负一() {
        assertEquals(-1, Rhythm.bestSlot(emptyList()))
        assertEquals(-1, Rhythm.bestSlot(List(24) { 0 }))
    }

    @Test
    fun bestSlot_取分钟数最高的时段() {
        assertEquals(2, Rhythm.bestSlot(hourList(9 to 60, 14 to 20)))
        assertEquals(5, Rhythm.bestSlot(hourList(9 to 10, 22 to 90)))
    }

    @Test
    fun peakHours_按分钟降序且平局取小时小的() {
        val peaks = Rhythm.peakHours(hourList(9 to 60, 20 to 20, 14 to 20))
        assertEquals(listOf(9, 14, 20), peaks)
    }

    @Test
    fun peakHours_只含有记录的小时且受topN限制() {
        assertEquals(listOf(9), Rhythm.peakHours(hourList(9 to 60), 3))
        assertEquals(listOf(9, 14), Rhythm.peakHours(hourList(9 to 60, 14 to 20, 20 to 20), 2))
        assertTrue(Rhythm.peakHours(emptyList()).isEmpty())
    }

    @Test
    fun peakText_文案与空态() {
        assertEquals("", Rhythm.peakText(emptyList()))
        assertEquals("9 点、14 点", Rhythm.peakText(hourList(9 to 60, 14 to 20), 2))
        assertEquals("9 点", Rhythm.hourText(9))
    }

    @Test
    fun summaryText_无数据时给引导语() {
        val text = Rhythm.summaryText(emptyList())
        assertTrue(text.contains("还没有专注记录"))
    }

    @Test
    fun summaryText_有数据时含黄金时段与占比() {
        val text = Rhythm.summaryText(hourList(9 to 60, 14 to 20))
        assertTrue(text.contains("上午"))
        assertTrue(text.contains("75%"))
        assertTrue(text.contains("9 点"))
    }

    @Test
    fun isWeekend_周一到周日判断正确() {
        val monday = thisMonday()
        assertFalse(Rhythm.isWeekend(monday))
        assertFalse(Rhythm.isWeekend(DateUtils.addDays(monday, 4)))
        assertTrue(Rhythm.isWeekend(DateUtils.addDays(monday, 5)))
        assertTrue(Rhythm.isWeekend(DateUtils.addDays(monday, 6)))
    }

    @Test
    fun countByKind_整周为5个工作日加2个周末() {
        val monday = thisMonday()
        assertEquals(5 to 2, Rhythm.countByKind(monday, DateUtils.addDays(monday, 6)))
    }

    @Test
    fun countByKind_单日与非法区间() {
        val monday = thisMonday()
        assertEquals(1 to 0, Rhythm.countByKind(monday, monday))
        assertEquals(0 to 0, Rhythm.countByKind(monday, DateUtils.addDays(monday, -1)))
    }

    @Test
    fun splitByKind_按周末与否累加分钟() {
        val monday = thisMonday()
        val byDay = mapOf(
            monday to 30,
            DateUtils.addDays(monday, 1) to 40,
            DateUtils.addDays(monday, 5) to 50
        )
        assertEquals(70 to 50, Rhythm.splitByKind(byDay))
    }

    @Test
    fun splitByKind_忽略无记录的日期() {
        val monday = thisMonday()
        val byDay = mapOf(monday to 30, DateUtils.addDays(monday, 6) to 0)
        assertEquals(30 to 0, Rhythm.splitByKind(byDay))
    }

    @Test
    fun kindAverages_按天数取日均() {
        val monday = thisMonday()
        val byDay = mapOf(
            monday to 30,
            DateUtils.addDays(monday, 1) to 40,
            DateUtils.addDays(monday, 5) to 50
        )
        // 5 个工作日共 70 分钟 → 14；2 个周末日共 50 分钟 → 25
        assertEquals(14 to 25, Rhythm.kindAverages(byDay, monday, DateUtils.addDays(monday, 6)))
    }

    @Test
    fun kindAverages_区间无天数时返回0() {
        val monday = thisMonday()
        assertEquals(0 to 0, Rhythm.kindAverages(mapOf(monday to 60), monday, DateUtils.addDays(monday, -1)))
    }

    @Test
    fun balanceText_周末更投入() {
        val text = Rhythm.balanceText(14, 25)
        assertTrue(text.contains("周末更投入"))
        assertTrue(text.contains("11"))
    }

    @Test
    fun balanceText_工作日更投入() {
        val text = Rhythm.balanceText(30, 20)
        assertTrue(text.contains("工作日更投入"))
        assertTrue(text.contains("10"))
    }

    @Test
    fun balanceText_差距不大时视为持平() {
        val text = Rhythm.balanceText(30, 28)
        assertTrue(text.contains("差不多"))
        assertTrue(text.contains("30 / 28"))
    }

    @Test
    fun balanceText_缺一侧数据时的文案() {
        assertTrue(Rhythm.balanceText(0, 0).contains("都还没有数据"))
        assertTrue(Rhythm.balanceText(30, 0).contains("周末还没有"))
        assertTrue(Rhythm.balanceText(0, 30).contains("工作日还没有"))
    }
}
