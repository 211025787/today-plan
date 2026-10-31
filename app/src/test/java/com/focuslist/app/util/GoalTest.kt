package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每日目标纯逻辑单元测试：覆盖取值约束、进度、达标判定、连续达标与建议目标。
 */
class GoalTest {

    @Test
    fun coerce_keepsStepAndBounds() {
        assertEquals(30, Goal.coerce(30))
        assertEquals(35, Goal.coerce(33))
        assertEquals(5, Goal.coerce(1))
        assertEquals(5, Goal.coerce(0))
        assertEquals(5, Goal.coerce(-20))
        assertEquals(480, Goal.coerce(1000))
    }

    @Test
    fun progress_capsAtOne() {
        assertEquals(0f, Goal.progress(0, 30), 0.0001f)
        assertEquals(0.5f, Goal.progress(15, 30), 0.0001f)
        assertEquals(1f, Goal.progress(30, 30), 0.0001f)
        assertEquals(1f, Goal.progress(45, 30), 0.0001f)
    }

    @Test
    fun progress_returnsZeroWhenGoalInvalid() {
        assertEquals(0f, Goal.progress(20, 0), 0.0001f)
        assertEquals(0f, Goal.progress(20, -10), 0.0001f)
    }

    @Test
    fun percent_roundsToNearestInt() {
        assertEquals(33, Goal.percent(1, 3))
        assertEquals(67, Goal.percent(2, 3))
        assertEquals(100, Goal.percent(30, 30))
        assertEquals(0, Goal.percent(0, 30))
    }

    @Test
    fun remaining_neverNegative() {
        assertEquals(20, Goal.remaining(10, 30))
        assertEquals(0, Goal.remaining(30, 30))
        assertEquals(0, Goal.remaining(45, 30))
    }

    @Test
    fun status_idleWhenNothingDone() {
        assertEquals(GoalStatus.IDLE, Goal.status(0, 30))
        assertEquals(GoalStatus.IDLE, Goal.status(20, 0))
    }

    @Test
    fun status_startedBeforeNearThreshold() {
        assertEquals(GoalStatus.STARTED, Goal.status(1, 30))
        assertEquals(GoalStatus.STARTED, Goal.status(17, 30))
    }

    @Test
    fun status_nearFromSixtyPercent() {
        assertEquals(GoalStatus.NEAR, Goal.status(18, 30))
        assertEquals(GoalStatus.NEAR, Goal.status(29, 30))
    }

    @Test
    fun status_doneAtOrAboveGoal() {
        assertEquals(GoalStatus.DONE, Goal.status(30, 30))
        assertEquals(GoalStatus.DONE, Goal.status(90, 30))
    }

    @Test
    fun achievedDays_countsOnlyReachedDays() {
        val weekly = listOf(30, 10, 45, 0, 60, 30, 29)
        assertEquals(4, Goal.achievedDays(weekly, 30))
        assertEquals(0, Goal.achievedDays(weekly, 0))
        assertEquals(0, Goal.achievedDays(emptyList(), 30))
    }

    @Test
    fun weeklyGoal_isSevenTimesDaily() {
        assertEquals(210, Goal.weeklyGoal(30))
        assertEquals(7, Goal.weeklyGoal(1))
    }

    @Test
    fun weekProgress_usesWeeklyTotal() {
        val full = listOf(30, 30, 30, 30, 30, 30, 30)
        assertEquals(1f, Goal.weekProgress(full, 30), 0.0001f)
        assertEquals(0.5f, Goal.weekProgress(List(7) { 15 }, 30), 0.0001f)
        assertEquals(0f, Goal.weekProgress(emptyList(), 30), 0.0001f)
        assertEquals(0f, Goal.weekProgress(full, 0), 0.0001f)
    }

    @Test
    fun goalStreak_countsFromToday() {
        assertEquals(3, Goal.goalStreak(listOf(30, 30, 30), 30))
        assertEquals(1, Goal.goalStreak(listOf(0, 0, 30), 30))
    }

    @Test
    fun goalStreak_todayNotReachedFallsBackToYesterday() {
        assertEquals(2, Goal.goalStreak(listOf(30, 30, 10), 30))
        assertEquals(2, Goal.goalStreak(listOf(30, 30, 0), 30))
    }

    @Test
    fun goalStreak_zeroOnEmptyOrInvalidGoal() {
        assertEquals(0, Goal.goalStreak(emptyList(), 30))
        assertEquals(0, Goal.goalStreak(listOf(30, 30), 0))
        assertEquals(0, Goal.goalStreak(listOf(10), 30))
    }

    @Test
    fun suggest_defaultsWhenSamplesTooFew() {
        assertEquals(Goal.DEFAULT_MINUTES, Goal.suggest(emptyList()))
        assertEquals(Goal.DEFAULT_MINUTES, Goal.suggest(listOf(25, 25)))
        assertEquals(Goal.DEFAULT_MINUTES, Goal.suggest(listOf(0, 0, 0, 0, 0)))
    }

    @Test
    fun suggest_usesMedianOfActiveDays() {
        assertEquals(30, Goal.suggest(listOf(20, 30, 40)))
        assertEquals(25, Goal.suggest(listOf(10, 20, 30, 40)))
        assertEquals(20, Goal.suggest(listOf(0, 0, 10, 20, 30)))
    }

    @Test
    fun suggest_roundsUpToStep() {
        // 中位数 23 → 向上取整到 5 的倍数 = 25
        assertEquals(25, Goal.suggest(listOf(21, 23, 25)))
        assertEquals(45, Goal.suggest(listOf(41, 45, 50)))
    }

    @Test
    fun suggest_clampedToReasonableRange() {
        // 中位数 1 → 取整 5 → 仍低于建议下限，抬到 15
        assertEquals(Goal.SUGGEST_MIN_MINUTES, Goal.suggest(listOf(1, 1, 1, 1)))
        assertEquals(Goal.SUGGEST_MAX_MINUTES, Goal.suggest(listOf(300, 400, 500)))
    }

    @Test
    fun statusText_describesEachState() {
        assertEquals("今天还没开始，目标 30 分钟", Goal.statusText(0, 30))
        assertEquals("已专注 10 分钟，还差 20 分钟达标", Goal.statusText(10, 30))
        assertEquals("已专注 20 分钟，还差 10 分钟达标", Goal.statusText(20, 30))
        assertEquals("已达标：今日 30 分钟", Goal.statusText(30, 30))
        assertEquals("已达标：今日 45 分钟，超出 15 分钟", Goal.statusText(45, 30))
    }

    @Test
    fun presets_containDefaultGoal() {
        assertTrue(Goal.PRESETS.contains(Goal.DEFAULT_MINUTES))
        assertTrue(Goal.PRESETS.all { it % Goal.STEP_MINUTES == 0 })
    }
}
