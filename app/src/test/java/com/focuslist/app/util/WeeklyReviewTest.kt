package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周回顾纯逻辑单元测试：覆盖同期对比口径、趋势判定、最佳单日、推算值与各类文案分支。
 */
class WeeklyReviewTest {

    /** 本周 7 天：周一到周三有记录，周四周日为空 */
    private val thisWeek = listOf(30, 60, 90, 0, 0, 0, 0)

    /** 上周 7 天：整周都有记录 */
    private val lastWeek = listOf(10, 20, 30, 40, 50, 60, 70)

    @Test
    fun of_onlyCountsElapsedDays() {
        val review = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 2, goal = 60)
        assertEquals(180, review.thisMinutes)
        // 上周只取周一到周三，而不是整周 280 分钟
        assertEquals(60, review.lastMinutes)
        assertEquals(3, review.elapsedDays)
        assertEquals(4, review.remainingDays)
    }

    @Test
    fun of_ignoresFutureDaysEvenIfTheyHaveData() {
        val future = listOf(0, 0, 0, 500, 500, 500, 500)
        val review = WeeklyReview.of(future, lastWeek, todayIndex = 0, goal = 60)
        assertEquals(0, review.thisMinutes)
        // 上周同样只取周一那一天
        assertEquals(10, review.lastMinutes)
        assertEquals(1, review.elapsedDays)
    }

    @Test
    fun of_countsActiveAndAchievedDays() {
        val review = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 3, goal = 60)
        assertEquals(3, review.activeDays)
        // 60 与 90 达标，30 不达标，周四为 0
        assertEquals(2, review.achievedDays)
    }

    @Test
    fun of_skipsAchievedDaysWhenGoalInvalid() {
        val review = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 2, goal = 0)
        assertEquals(0, review.achievedDays)
        assertEquals(60, review.averageMinutes)
        assertEquals("活跃 3/3 天 · 最佳 周三 90 分钟 · 日均 60 分钟", WeeklyReview.metricText(review))
    }

    @Test
    fun of_picksEarliestDayOnTie() {
        val review = WeeklyReview.of(listOf(50, 50, 50, 0, 0, 0, 0), lastWeek, todayIndex = 2, goal = 30)
        assertEquals(0, review.bestDayIndex)
        assertEquals(50, review.bestDayMinutes)
    }

    @Test
    fun of_reportsNoBestDayWhenEmpty() {
        val review = WeeklyReview.of(List(7) { 0 }, List(7) { 0 }, todayIndex = 2, goal = 30)
        assertEquals(-1, review.bestDayIndex)
        assertEquals(0, review.bestDayMinutes)
        assertEquals(0, review.averageMinutes)
    }

    @Test
    fun of_averageTruncatesToWholeMinutes() {
        val review = WeeklyReview.of(listOf(33, 33, 34, 0, 0, 0, 0), lastWeek, todayIndex = 2, goal = 30)
        assertEquals(100, review.thisMinutes)
        assertEquals(33, review.averageMinutes)
    }

    @Test
    fun of_clampsTodayIndex() {
        val late = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 99, goal = 60)
        assertEquals(7, late.elapsedDays)
        assertEquals(180, late.thisMinutes)

        val early = WeeklyReview.of(thisWeek, lastWeek, todayIndex = -5, goal = 60)
        assertEquals(1, early.elapsedDays)
        assertEquals(30, early.thisMinutes)
    }

    @Test
    fun of_toleratesShortOrEmptyLists() {
        val review = WeeklyReview.of(emptyList(), listOf(30), todayIndex = 2, goal = 30)
        assertEquals(0, review.thisMinutes)
        assertEquals(30, review.lastMinutes)
        assertEquals(3, review.elapsedDays)
    }

    @Test
    fun deltaAndTrend_followSign() {
        val up = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 2, goal = 60)
        assertEquals(120, up.delta)
        assertEquals(WeekTrend.UP, up.trend)

        val down = WeeklyReview.of(listOf(10, 10, 10, 0, 0, 0, 0), lastWeek, todayIndex = 2, goal = 60)
        assertEquals(-30, down.delta)
        assertEquals(WeekTrend.DOWN, down.trend)

        val flat = WeeklyReview.of(listOf(10, 20, 30, 0, 0, 0, 0), lastWeek, todayIndex = 2, goal = 60)
        assertEquals(0, flat.delta)
        assertEquals(WeekTrend.FLAT, flat.trend)
    }

    @Test
    fun projectedMinutes_scalesToWholeWeek() {
        val review = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 2, goal = 60)
        // 180 / 3 * 7
        assertEquals(420, review.projectedMinutes)
    }

    @Test
    fun projectedMinutes_equalsActualOnLastDay() {
        val review = WeeklyReview.of(thisWeek, lastWeek, todayIndex = 6, goal = 60)
        assertEquals(180, review.projectedMinutes)
        assertEquals(0, review.remainingDays)
    }

    @Test
    fun percentOf_roundsAndGuardsZeroBase() {
        assertEquals(33, WeeklyReview.percentOf(62, 186))
        assertEquals(33, WeeklyReview.percentOf(1, 3))
        assertEquals(67, WeeklyReview.percentOf(2, 3))
        assertEquals(0, WeeklyReview.percentOf(10, 0))
        // 传入负数时按绝对值计算，避免出现负百分比
        assertEquals(50, WeeklyReview.percentOf(-15, 30))
    }

    @Test
    fun badgeText_coversEachTrend() {
        assertEquals("↑ 200%", WeeklyReview.badgeText(WeeklyReview.of(thisWeek, lastWeek, 2, 60)))
        assertEquals("↓ 50%", WeeklyReview.badgeText(WeeklyReview.of(listOf(10, 10, 10, 0, 0, 0, 0), lastWeek, 2, 60)))
        assertEquals("持平", WeeklyReview.badgeText(WeeklyReview.of(listOf(10, 20, 30, 0, 0, 0, 0), lastWeek, 2, 60)))
    }

    @Test
    fun badgeText_handlesMissingLastWeek() {
        assertEquals("新起点", WeeklyReview.badgeText(WeeklyReview.of(thisWeek, emptyList(), 2, 60)))
        assertEquals("未开始", WeeklyReview.badgeText(WeeklyReview.of(emptyList(), emptyList(), 2, 60)))
    }

    @Test
    fun deltaText_coversEachTrend() {
        assertEquals("较上周同期多 120 分钟", WeeklyReview.deltaText(WeeklyReview.of(thisWeek, lastWeek, 2, 60)))
        assertEquals("较上周同期少 30 分钟", WeeklyReview.deltaText(WeeklyReview.of(listOf(10, 10, 10, 0, 0, 0, 0), lastWeek, 2, 60)))
        assertEquals("与上周同期持平", WeeklyReview.deltaText(WeeklyReview.of(listOf(10, 20, 30, 0, 0, 0, 0), lastWeek, 2, 60)))
    }

    @Test
    fun deltaText_handlesMissingLastWeek() {
        assertEquals("上周同期还没有记录", WeeklyReview.deltaText(WeeklyReview.of(thisWeek, emptyList(), 2, 60)))
        assertEquals("这周还没开始", WeeklyReview.deltaText(WeeklyReview.of(emptyList(), emptyList(), 2, 60)))
    }

    @Test
    fun spanText_describesElapsedRange() {
        assertEquals("周一至今 1 天", WeeklyReview.spanText(WeeklyReview.of(thisWeek, lastWeek, 0, 60)))
        assertEquals("周一至今 3 天", WeeklyReview.spanText(WeeklyReview.of(thisWeek, lastWeek, 2, 60)))
        assertEquals("整周 7 天", WeeklyReview.spanText(WeeklyReview.of(thisWeek, lastWeek, 6, 60)))
    }

    @Test
    fun dayName_mapsIndexAndFallsBack() {
        assertEquals("周一", WeeklyReview.dayName(0))
        assertEquals("周三", WeeklyReview.dayName(2))
        assertEquals("周日", WeeklyReview.dayName(6))
        assertEquals("—", WeeklyReview.dayName(-1))
        assertEquals("—", WeeklyReview.dayName(7))
    }

    @Test
    fun metricText_listsGoalBestAndAverage() {
        val review = WeeklyReview.of(thisWeek, lastWeek, 2, 60)
        assertEquals("达标 2/3 天 · 最佳 周三 90 分钟 · 日均 60 分钟", WeeklyReview.metricText(review))
    }

    @Test
    fun metricText_omitsBestDayWhenNoRecord() {
        val review = WeeklyReview.of(List(7) { 0 }, List(7) { 0 }, 2, 30)
        assertEquals("达标 0/3 天 · 日均 0 分钟", WeeklyReview.metricText(review))
    }

    @Test
    fun projectionText_coversEachBranch() {
        val mid = WeeklyReview.of(thisWeek, lastWeek, 2, 60)
        assertEquals("按当前节奏，本周预计 420 分钟（还剩 4 天）", WeeklyReview.projectionText(mid))

        val done = WeeklyReview.of(thisWeek, lastWeek, 6, 60)
        assertEquals("本周已完整记录，共 180 分钟", WeeklyReview.projectionText(done))

        val idle = WeeklyReview.of(emptyList(), lastWeek, 2, 60)
        assertEquals("本周还没有开始，随时可以启动第一个番茄", WeeklyReview.projectionText(idle))
    }

    @Test
    fun projectionText_includesRemainingDays() {
        // elapsed = 7 时走「已完整记录」分支，这里覆盖 elapsed = 6（还剩 1 天）的正常尾部
        val review = WeeklyReview.of(thisWeek, lastWeek, 5, 60)
        assertEquals("按当前节奏，本周预计 210 分钟（还剩 1 天）", WeeklyReview.projectionText(review))
    }

    @Test
    fun isEmpty_onlyWhenBothWeeksBlank() {
        assertTrue(WeeklyReview.of(emptyList(), emptyList(), 2, 60).isEmpty)
        assertTrue(WeeklyReview.of(List(7) { 0 }, List(7) { 0 }, 2, 60).isEmpty)
        // 上周有记录但本周还没有：要显示「下降」而不是空状态
        assertTrue(!WeeklyReview.of(emptyList(), lastWeek, 2, 60).isEmpty)
        // 本周有记录上周没有：要显示「新起点」
        assertTrue(!WeeklyReview.of(thisWeek, emptyList(), 2, 60).isEmpty)
    }
}
