package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划整体节奏 [PlanPaces] 的单元测试。
 *
 * 基准计划：2026-11-01 ~ 2026-11-30，共 30 天，每天 30 分钟 → 总目标 900 分钟。
 * 到 11-10 时已过 10 天，应完成 300 分钟（18000 秒）。
 */
class PlanPaceTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val start = day("2026-11-01")
    private val end = day("2026-11-30")

    /** 计算基准计划的节奏 */
    private fun pace(today: String, seconds: Long, goal: Int = 30): PlanPace =
        PlanPaces.of(start, end, goal, seconds, day(today))

    @Test
    fun beforeStart_saysNotStarted() {
        val result = pace("2026-10-30", 0)

        assertEquals(PaceStatus.NOT_STARTED, result.status)
        assertEquals(30, result.totalDays)
        assertEquals(0, result.elapsedDays)
        assertEquals(30, result.remainingDays)
        assertEquals(0, result.expectedPercent)
        assertTrue(result.headline.contains("还没开始"))
    }

    @Test
    fun earlyDays_areNotJudged() {
        val result = pace("2026-11-02", 0)

        assertEquals(PaceStatus.EARLY, result.status)
        assertEquals(2, result.elapsedDays)
        assertEquals("刚开始，先跑几天再看节奏", result.headline)
    }

    @Test
    fun shortPlansSkipEarlyBuffer() {
        val short = PlanPaces.of(day("2026-11-01"), day("2026-11-03"), 30, 0, day("2026-11-01"))

        assertEquals(3, short.totalDays)
        assertEquals(PaceStatus.BEHIND, short.status)
    }

    @Test
    fun onTrack_whenActualMatchesExpected() {
        val result = pace("2026-11-10", 18000)

        assertEquals(PaceStatus.ON_TRACK, result.status)
        assertEquals(10, result.elapsedDays)
        assertEquals(20, result.remainingDays)
        assertEquals(33, result.expectedPercent)
        assertEquals(33, result.actualPercent)
        assertEquals(0, result.behindDays)
        assertEquals(30, result.requiredPerDayMinutes)
    }

    @Test
    fun ahead_whenTenPercentOverExpected() {
        val result = pace("2026-11-10", 19800)

        assertEquals(PaceStatus.AHEAD, result.status)
        assertTrue(result.headline.contains("比时间进度快"))
    }

    @Test
    fun onTrack_atNinetyPercentBoundary() {
        assertEquals(PaceStatus.ON_TRACK, pace("2026-11-10", 16200).status)
    }

    @Test
    fun slightlyBehind_atSixtyFivePercent() {
        val result = pace("2026-11-10", 11700)

        assertEquals(PaceStatus.SLIGHTLY_BEHIND, result.status)
        assertEquals(4, result.behindDays)
    }

    @Test
    fun behind_belowSixtyFivePercent() {
        val result = pace("2026-11-10", 11600)

        assertEquals(PaceStatus.BEHIND, result.status)
        assertTrue(result.headline.contains("需要加把劲"))
    }

    @Test
    fun behindDays_roundsUpFromOneMinute() {
        // 只差 1 秒也算落后 1 天
        assertEquals(1, pace("2026-11-10", 17999).behindDays)
        // 差 30 分钟 → 1 天；差 30 分 1 秒 → 2 天
        assertEquals(1, pace("2026-11-10", 16200).behindDays)
        assertEquals(2, pace("2026-11-10", 16199).behindDays)
    }

    @Test
    fun requiredPerDay_growsWhenBehind() {
        val result = pace("2026-11-10", 6000)

        // 还剩 900 - 100 = 800 分钟，分摊到 20 天 = 40 分钟
        assertEquals(40, result.requiredPerDayMinutes)
        assertTrue(result.detail.contains("40 分钟"))
    }

    @Test
    fun requiredPerDay_isZeroWhenGoalReached() {
        val result = pace("2026-11-10", 60000)

        assertEquals(0, result.requiredPerDayMinutes)
        assertEquals(111, result.actualPercent)
    }

    @Test
    fun actualPercent_canExceedHundred() {
        val result = pace("2026-11-10", 54000)

        assertEquals(100, result.actualPercent)
        assertEquals(1f, result.actualFraction, 0.001f)
    }

    @Test
    fun fractionsAreClampedForBars() {
        val result = pace("2026-12-05", 54000)

        assertEquals(1f, result.timeFraction, 0.001f)
        assertEquals(1f, result.actualFraction, 0.001f)
    }

    @Test
    fun fractionsFollowPercentMidPlan() {
        val result = pace("2026-11-10", 18000)

        assertEquals(0.33f, result.timeFraction, 0.001f)
        assertEquals(0.33f, result.actualFraction, 0.001f)
    }

    @Test
    fun finishedDone_afterEndWithEnough() {
        val result = pace("2026-12-05", 54000)

        assertEquals(PaceStatus.FINISHED_DONE, result.status)
        assertEquals(100, result.expectedPercent)
        assertEquals(0, result.remainingDays)
        assertTrue(result.headline.contains("计划已结束"))
    }

    @Test
    fun finishedMissed_afterEndWithoutEnough() {
        val result = pace("2026-12-05", 40000)

        assertEquals(PaceStatus.FINISHED_MISSED, result.status)
        assertEquals(74, result.actualPercent)
        assertTrue(result.detail.contains("已结束"))
    }

    @Test
    fun elapsedDaysStopAtPlanEnd() {
        // 计划结束后再过很久，已过天数也不再增长
        val result = pace("2027-01-20", 54000)

        assertEquals(30, result.elapsedDays)
        assertEquals(30, result.totalDays)
    }

    @Test
    fun zeroGoal_neverJudgedAsBehind() {
        val result = PlanPaces.of(start, end, 0, 0, day("2026-11-10"))

        assertEquals(PaceStatus.ON_TRACK, result.status)
        assertEquals(0, result.behindDays)
        assertEquals(0, result.actualPercent)
    }

    @Test
    fun singleDayPlan_countsAsOneDay() {
        val oneDay = PlanPaces.of(start, start, 30, 0, start)

        assertEquals(1, oneDay.totalDays)
        assertEquals(1, oneDay.elapsedDays)
        assertEquals(100, oneDay.expectedPercent)
    }

    @Test
    fun progressText_showsBothPercentages() {
        assertEquals("时间 33% · 投入 33%", pace("2026-11-10", 18000).progressText)
    }

    @Test
    fun detail_forFinishedPlanDoesNotPromiseMore() {
        val result = pace("2026-12-05", 40000)

        assertEquals("共 30 天，已结束", result.detail)
    }

    @Test
    fun detail_forBehindMentionsDeadline() {
        val result = pace("2026-11-10", 6000)

        assertTrue(result.detail.startsWith("还剩 20 天"))
        assertTrue(result.detail.endsWith("才能按时完成"))
    }

    @Test
    fun daysBetween_countsWholeDays() {
        assertEquals(29, PlanPaces.daysBetween(start, end))
        assertEquals(0, PlanPaces.daysBetween(start, start))
        assertEquals(-7, PlanPaces.daysBetween(start, day("2026-10-25")))
    }

    @Test
    fun lastDay_pushesEverythingIntoToday() {
        val result = pace("2026-11-30", 0)

        assertEquals(0, result.remainingDays)
        assertEquals(PaceStatus.BEHIND, result.status)
        // 900 分钟全压在今天
        assertEquals(900, result.requiredPerDayMinutes)
        assertTrue(result.detail.startsWith("今天是最后一天"))
    }

    @Test
    fun lastDay_whenFinishedSaysKeepGoing() {
        val result = pace("2026-11-30", 54000)

        assertEquals(PaceStatus.ON_TRACK, result.status)
        assertEquals(0, result.requiredPerDayMinutes)
        assertEquals("整体目标已完成，保持就好", result.detail)
    }

    @Test
    fun chipText_onlyForBehind() {
        assertEquals("落后 7 天", PlanPaces.chipText(pace("2026-11-10", 6000)))
        assertEquals("落后 4 天", PlanPaces.chipText(pace("2026-11-10", 11700)))
        assertEquals(null, PlanPaces.chipText(pace("2026-11-10", 18000)))
    }

    @Test
    fun chipText_isSilentWhenAheadOrNotStarted() {
        assertEquals(null, PlanPaces.chipText(pace("2026-11-10", 19800)))
        assertEquals(null, PlanPaces.chipText(pace("2026-10-30", 0)))
        assertEquals(null, PlanPaces.chipText(pace("2026-11-02", 0)))
    }

    @Test
    fun chipText_isSilentAfterPlanEnds() {
        assertEquals(null, PlanPaces.chipText(pace("2026-12-05", 40000)))
        assertEquals(null, PlanPaces.chipText(pace("2026-12-05", 54000)))
    }
}
