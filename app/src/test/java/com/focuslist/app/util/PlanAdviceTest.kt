package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlanAdvice] 的类别识别与推荐规则测试。
 */
class PlanAdviceTest {

    @Test
    fun `fitness suggests rest days`() {
        val advice = PlanAdvice.suggest("每天健身 45 分钟")!!
        assertEquals("健身", advice.category)
        assertEquals(DayRule.EVERY_DAY, advice.rule)
        assertEquals(4, WeekdayMask.count(advice.mask))
        assertTrue(advice.mask and WeekdayMask.TUE == 0)
        assertTrue(advice.reason.contains("恢复"))
    }

    @Test
    fun `running counts as fitness`() {
        val advice = PlanAdvice.suggest("晨跑 5 公里")!!
        assertEquals("健身", advice.category)
    }

    @Test
    fun `english suggests every day`() {
        val advice = PlanAdvice.suggest("背单词 20 分钟")!!
        assertEquals("学习", advice.category)
        assertEquals(WeekdayMask.ALL, advice.mask)
        assertEquals(30, advice.minutes)
    }

    @Test
    fun `reading stays daily`() {
        val advice = PlanAdvice.suggest("读书 30 分钟")!!
        assertEquals("学习", advice.category)
        assertEquals(DayRule.EVERY_DAY, advice.rule)
    }

    @Test
    fun `mindful and writing are daily`() {
        assertEquals("冥想", PlanAdvice.suggest("冥想 10 分钟")!!.category)
        assertEquals("写作", PlanAdvice.suggest("写日记")!!.category)
    }

    @Test
    fun `instrument uses weekdays`() {
        val advice = PlanAdvice.suggest("练吉他")!!
        assertEquals("练习", advice.category)
        assertEquals(WeekdayMask.WEEKDAYS, advice.mask)
    }

    @Test
    fun `unknown or blank returns null`() {
        assertNull(PlanAdvice.suggest(""))
        assertNull(PlanAdvice.suggest("整理房间"))
    }
}
