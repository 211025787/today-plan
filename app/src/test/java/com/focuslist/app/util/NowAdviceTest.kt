package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「现在该做什么」建议引擎 [NowAdvice] 的单元测试。
 *
 * 重点覆盖判定优先级：无计划 → 计时中 → 全部达成 → 深夜收工 → 全新开始 → 最接近达标，
 * 以及「挑哪一个计划」与各种文案边界。
 */
class NowAdviceTest {

    /** 构造一条计划快照 */
    private fun plan(
        id: Long,
        title: String = "写作",
        today: Int = 0,
        goal: Int = 30,
        running: Boolean = false
    ): NowPlan = NowPlan(
        id = id,
        title = title,
        icon = "📝",
        todayMinutes = today,
        goalMinutes = goal,
        running = running
    )

    /** 上午十点 */
    private val morning = 10 * 60

    @Test
    fun emptyPlans_asksForOne() {
        val tip = NowAdvice.of(emptyList(), morning)

        assertEquals(NowKind.NO_PLAN, tip.kind)
        assertEquals("添加", tip.actionText)
        assertNull(tip.planId)
        assertTrue(tip.headline.contains("计划"))
    }

    @Test
    fun running_winsOverPendingGoals() {
        val tip = NowAdvice.of(
            listOf(plan(1, today = 5), plan(2, title = "读书", today = 10, running = true)),
            morning
        )

        assertEquals(NowKind.RUNNING, tip.kind)
        assertEquals(2L, tip.planId)
        assertEquals("", tip.actionText)
        assertTrue(tip.headline.contains("读书"))
        assertTrue(tip.detail.contains("10 / 30"))
        assertEquals(10 / 30f, tip.progress, 0.001f)
    }

    @Test
    fun running_beatsLateNightRest() {
        val tip = NowAdvice.of(
            listOf(plan(1, today = 5, running = true)),
            23 * 60
        )

        assertEquals(NowKind.RUNNING, tip.kind)
    }

    @Test
    fun allDone_whenEveryPlanReached() {
        val tip = NowAdvice.of(
            listOf(plan(1, today = 30), plan(2, today = 60, goal = 60)),
            morning
        )

        assertEquals(NowKind.ALL_DONE, tip.kind)
        assertEquals("", tip.actionText)
        assertNull(tip.planId)
        assertEquals(1f, tip.progress, 0.001f)
    }

    @Test
    fun allDone_whenGoalIsZero() {
        val tip = NowAdvice.of(listOf(plan(1, today = 0, goal = 0)), morning)

        assertEquals(NowKind.ALL_DONE, tip.kind)
    }

    @Test
    fun allDone_beatsLateNight() {
        val tip = NowAdvice.of(listOf(plan(1, today = 30)), 23 * 60)

        assertEquals(NowKind.ALL_DONE, tip.kind)
    }

    @Test
    fun lateNight_suggestsResting() {
        val tip = NowAdvice.of(listOf(plan(1, today = 25)), 22 * 60 + 30)

        assertEquals(NowKind.REST, tip.kind)
        assertNull(tip.planId)
        assertTrue(tip.detail.contains("还差 5 分钟"))
    }

    @Test
    fun lateNight_boundaryIsHalfPastTen() {
        val justBefore = NowAdvice.of(listOf(plan(1, today = 0)), 22 * 60 + 29)
        val onTheLine = NowAdvice.of(listOf(plan(1, today = 0)), 22 * 60 + 30)

        assertEquals(NowKind.FRESH_START, justBefore.kind)
        assertEquals(NowKind.REST, onTheLine.kind)
    }

    @Test
    fun lateNight_stillRestsWhenNothingStarted() {
        val tip = NowAdvice.of(listOf(plan(1, today = 0)), 23 * 60 + 30)

        assertEquals(NowKind.REST, tip.kind)
    }

    @Test
    fun freshStart_whenNoMinutesToday() {
        val tip = NowAdvice.of(
            listOf(plan(1, goal = 45), plan(2, title = "读书", goal = 20)),
            morning
        )

        assertEquals(NowKind.FRESH_START, tip.kind)
        assertEquals(2L, tip.planId)
        assertEquals("开始", tip.actionText)
        assertTrue(tip.headline.contains("读书"))
        assertTrue(tip.detail.contains("20 分钟"))
    }

    @Test
    fun freshStart_tieKeepsListOrder() {
        val tip = NowAdvice.of(listOf(plan(7, goal = 30), plan(8, goal = 30)), morning)

        assertEquals(7L, tip.planId)
    }

    @Test
    fun closeToGoal_picksSmallestRemaining() {
        val tip = NowAdvice.of(
            listOf(
                plan(1, title = "写作", today = 10, goal = 30),
                plan(2, title = "读书", today = 40, goal = 45)
            ),
            morning
        )

        assertEquals(NowKind.CLOSE_TO_GOAL, tip.kind)
        assertEquals(2L, tip.planId)
        assertEquals("再 5 分钟达成「读书」", tip.headline)
        assertTrue(tip.detail.contains("40 / 45"))
    }

    @Test
    fun closeToGoal_skipsAchievedPlans() {
        val tip = NowAdvice.of(
            listOf(plan(1, today = 30, goal = 30), plan(2, title = "读书", today = 10, goal = 40)),
            morning
        )

        assertEquals(NowKind.CLOSE_TO_GOAL, tip.kind)
        assertEquals(2L, tip.planId)
    }

    @Test
    fun of_clampsOutOfRangeMinute() {
        val early = NowAdvice.of(listOf(plan(1, today = 0)), -600)
        val late = NowAdvice.of(listOf(plan(1, today = 0)), 5000)

        assertEquals(NowKind.FRESH_START, early.kind)
        assertEquals(NowKind.REST, late.kind)
    }

    @Test
    fun actionText_onlyForStartableTips() {
        val running = NowAdvice.of(listOf(plan(1, today = 5, running = true)), morning)
        val done = NowAdvice.of(listOf(plan(1, today = 30)), morning)
        val rest = NowAdvice.of(listOf(plan(1, today = 5)), 23 * 60)

        assertEquals("", running.actionText)
        assertEquals("", done.actionText)
        assertEquals("", rest.actionText)
    }

    @Test
    fun progress_clampsToUnitRange() {
        assertEquals(1f, NowAdvice.progressOf(plan(1, today = 99, goal = 30)), 0.001f)
        assertEquals(0f, NowAdvice.progressOf(plan(1, today = 0, goal = 30)), 0.001f)
        assertEquals(1f, NowAdvice.progressOf(plan(1, today = 0, goal = 0)), 0.001f)
    }

    @Test
    fun remaining_neverNegative() {
        assertEquals(0, NowAdvice.remainingOf(plan(1, today = 40, goal = 30)))
        assertEquals(30, NowAdvice.remainingOf(plan(1, today = 0, goal = 30)))
    }

    @Test
    fun achieved_boundaryCountsAsDone() {
        assertTrue(NowAdvice.achieved(plan(1, today = 30, goal = 30)))
        assertTrue(!NowAdvice.achieved(plan(1, today = 29, goal = 30)))
    }

    @Test
    fun allPlanning_returnsNonBlankCopy() {
        val samples = listOf(
            NowAdvice.of(emptyList(), morning),
            NowAdvice.of(listOf(plan(1, today = 5, running = true)), morning),
            NowAdvice.of(listOf(plan(1, today = 5)), morning),
            NowAdvice.of(listOf(plan(1, today = 30)), morning),
            NowAdvice.of(listOf(plan(1, today = 5)), 23 * 60)
        )

        samples.forEach { tip ->
            assertTrue(tip.headline.isNotBlank())
            assertTrue(tip.detail.isNotBlank())
            assertTrue(tip.emoji.isNotBlank())
            assertTrue(tip.progress in 0f..1f)
        }
    }

    @Test
    fun minuteOfDay_readsClock() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals(9 * 60 + 30, DateUtils.minuteOfDay(cal.timeInMillis))

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        assertEquals(23 * 60 + 59, DateUtils.minuteOfDay(cal.timeInMillis))
    }

    @Test
    fun minuteOfDay_midnightIsZero() {
        val start = DateUtils.startOfDay(System.currentTimeMillis())
        assertEquals(0, DateUtils.minuteOfDay(start))
    }
}
