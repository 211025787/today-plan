package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计划提前提醒时间计算的边界测试。
 */
class PlanLeadTest {

    @Test
    fun `coerce keeps legal range`() {
        assertEquals(0, PlanLead.coerce(-3))
        assertEquals(5, PlanLead.coerce(5))
        assertEquals(60, PlanLead.coerce(61))
        assertEquals(60, PlanLead.coerce(999))
    }

    @Test
    fun `zero lead disables pre reminder`() {
        assertNull(PlanLead.triggerAt(nowMs = 1_000L, remainSec = 600L, leadMinutes = 0))
    }

    @Test
    fun `trigger subtracts lead minutes from remaining`() {
        val at = PlanLead.triggerAt(nowMs = 1_000L, remainSec = 600L, leadMinutes = 5)
        assertEquals(1_000L + 300L * 1000L, at)
    }

    @Test
    fun `remaining inside lead window skips pre reminder`() {
        assertNull(PlanLead.triggerAt(nowMs = 0L, remainSec = 300L, leadMinutes = 5))
        assertNull(PlanLead.triggerAt(nowMs = 0L, remainSec = 120L, leadMinutes = 5))
    }

    @Test
    fun `one second outside window still schedules`() {
        val at = PlanLead.triggerAt(nowMs = 0L, remainSec = 301L, leadMinutes = 5)
        assertEquals(1000L, at)
    }

    @Test
    fun `reached or negative remaining never schedules`() {
        assertNull(PlanLead.triggerAt(nowMs = 0L, remainSec = 0L, leadMinutes = 5))
        assertNull(PlanLead.triggerAt(nowMs = 0L, remainSec = -10L, leadMinutes = 5))
        assertNull(PlanLead.goalAt(nowMs = 0L, remainSec = 0L))
    }

    @Test
    fun `goal time is remaining seconds from now`() {
        assertEquals(601_000L, PlanLead.goalAt(nowMs = 1_000L, remainSec = 600L))
    }

    @Test
    fun `reminder title uses coerced minutes`() {
        assertEquals("计划还差 5 分钟达标", PlanLead.reminderTitle(5))
        assertEquals("计划还差 60 分钟达标", PlanLead.reminderTitle(999))
    }

    @Test
    fun `catch up fires when starting already inside lead window`() {
        assertTrue(
            PlanLead.shouldCatchUp(
                remainSec = 1740L,
                leadMinutes = 29,
                sessionSec = 0L,
                alerted = false
            )
        )
    }

    @Test
    fun `catch up ignores late session`() {
        assertFalse(
            PlanLead.shouldCatchUp(
                remainSec = 600L,
                leadMinutes = 29,
                sessionSec = PlanLead.CATCH_UP_SESSION_SEC + 1,
                alerted = false
            )
        )
    }

    @Test
    fun `catch up skips when already alerted or outside window`() {
        assertFalse(PlanLead.shouldCatchUp(1740L, 29, 0L, alerted = true))
        assertFalse(PlanLead.shouldCatchUp(1800L, 29, 0L, alerted = false))
        assertFalse(PlanLead.shouldCatchUp(1740L, 0, 0L, alerted = false))
        assertFalse(PlanLead.shouldCatchUp(0L, 29, 0L, alerted = false))
    }
}
