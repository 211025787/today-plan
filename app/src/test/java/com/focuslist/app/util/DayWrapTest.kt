package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收工小结 [DayWraps] 的单元测试。
 *
 * 重点：总量与达标数的口径、逐计划排序、三种结语分支、以及「什么时候值得露出收工入口」。
 */
class DayWrapTest {

    /** 构造一条计划 */
    private fun plan(
        id: Long,
        title: String = "计划$id",
        minutes: Int = 0,
        goal: Int = 30,
        running: Boolean = false
    ): WrapPlan = WrapPlan(
        id = id,
        title = title,
        icon = "📝",
        minutes = minutes,
        goalMinutes = goal,
        running = running
    )

    /** 中午 */
    private val noon = 12 * 60

    /** 晚上九点半 */
    private val evening = 21 * 60 + 30

    @Test
    fun emptyPlans_hasNoSuggestion() {
        val wrap = DayWraps.of(emptyList(), evening)

        assertEquals("今天没有计划", wrap.headline)
        assertEquals(0, wrap.planCount)
        assertFalse(wrap.suggest)
        assertTrue(wrap.empty)
    }

    @Test
    fun zeroMinutes_saysNotStarted() {
        val wrap = DayWraps.of(listOf(plan(1), plan(2)), noon)

        assertEquals("今天还没有投入", wrap.headline)
        assertTrue(wrap.empty)
        assertEquals("今天先到这里，明天再来", wrap.closing)
    }

    @Test
    fun headline_sumsDuration() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 60), plan(2, minutes = 70)), noon)

        assertEquals("今天 2 小时 10 分", wrap.headline)
        assertEquals(130, wrap.totalMinutes)
    }

    @Test
    fun detail_countsAchieved() {
        val wrap = DayWraps.of(
            listOf(plan(1, minutes = 30), plan(2, minutes = 10), plan(3, minutes = 40)),
            noon
        )

        assertEquals(2, wrap.achievedCount)
        assertEquals(3, wrap.planCount)
        assertEquals("达标 2/3 个计划", wrap.detail)
    }

    @Test
    fun detail_mentionsRunningTimers() {
        val wrap = DayWraps.of(
            listOf(plan(1, minutes = 30, running = true), plan(2, minutes = 10)),
            noon
        )

        assertEquals(1, wrap.runningCount)
        assertEquals("达标 1/2 个计划 · 1 个还在计时", wrap.detail)
    }

    @Test
    fun rows_areSortedByMinutesDesc() {
        val wrap = DayWraps.of(
            listOf(plan(1, minutes = 10), plan(2, "读书", minutes = 50), plan(3, minutes = 30)),
            noon
        )

        assertEquals(listOf(2L, 3L, 1L), wrap.rows.map { it.id })
    }

    @Test
    fun rows_breakTiesByTitle() {
        val wrap = DayWraps.of(
            listOf(plan(1, "B", minutes = 10), plan(2, "A", minutes = 10)),
            noon
        )

        assertEquals(listOf("A", "B"), wrap.rows.map { it.title })
    }

    @Test
    fun achieved_boundaryIsInclusive() {
        assertTrue(plan(1, minutes = 30, goal = 30).achieved)
        assertFalse(plan(1, minutes = 29, goal = 30).achieved)
        assertFalse(plan(1, minutes = 99, goal = 0).achieved)
    }

    @Test
    fun remain_neverNegative() {
        assertEquals(0, plan(1, minutes = 40, goal = 30).remainMinutes)
        assertEquals(20, plan(1, minutes = 10, goal = 30).remainMinutes)
    }

    @Test
    fun allAchieved_encouragesClosing() {
        val wrap = DayWraps.of(
            listOf(plan(1, minutes = 30), plan(2, minutes = 40)),
            noon
        )

        assertTrue(wrap.allAchieved)
        assertEquals("全部达标，可以安心收工了", wrap.closing)
    }

    @Test
    fun lateEvening_closesGently() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 10)), evening)

        assertEquals("今天先到这里，明天继续", wrap.closing)
        assertTrue(wrap.suggest)
    }

    @Test
    fun daytime_pointsAtTheClosestGoal() {
        val wrap = DayWraps.of(
            listOf(plan(1, "写作", minutes = 20), plan(2, "读书", minutes = 10)),
            noon
        )

        // 写作还差 10 分钟，比读书的 20 分钟更近
        assertEquals("「写作」还差 10 分钟，或者就到这儿", wrap.closing)
        assertFalse(wrap.suggest)
    }

    @Test
    fun zeroGoalPlansAreIgnoredInClosing() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 10, goal = 0)), noon)

        assertEquals("今天到这里就好", wrap.closing)
    }

    @Test
    fun suggest_whenTimersRunning() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 5, running = true)), noon)

        assertTrue(wrap.suggest)
    }

    @Test
    fun suggest_startsAtNinePm() {
        assertFalse(DayWraps.of(listOf(plan(1, minutes = 10)), 21 * 60 - 1).suggest)
        assertTrue(DayWraps.of(listOf(plan(1, minutes = 10)), 21 * 60).suggest)
    }

    @Test
    fun suggest_falseWhenNothingDoneAtNight() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 0)), evening)

        assertFalse(wrap.suggest)
    }

    @Test
    fun suggest_falseWhenAllDoneAtNoon() {
        val wrap = DayWraps.of(listOf(plan(1, minutes = 30)), noon)

        assertFalse(wrap.suggest)
    }

    @Test
    fun nowMinutes_clampsOutOfRange() {
        assertEquals("今天先到这里，明天继续", DayWraps.of(listOf(plan(1, minutes = 10)), 5000).closing)
        assertFalse(DayWraps.of(listOf(plan(1, minutes = 10)), -30).suggest)
    }

    @Test
    fun emptyFlag_onlyWhenNoMinutes() {
        assertTrue(DayWraps.of(listOf(plan(1)), noon).empty)
        assertFalse(DayWraps.of(listOf(plan(1, minutes = 1)), noon).empty)
    }

    @Test
    fun singlePlan_countedCorrectly() {
        val wrap = DayWraps.of(listOf(plan(1, "写作", minutes = 45, goal = 30)), evening)

        assertEquals(1, wrap.planCount)
        assertEquals(1, wrap.achievedCount)
        assertEquals("全部达标，可以安心收工了", wrap.closing)
    }
}
