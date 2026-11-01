package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 月度回顾纯逻辑单元测试：覆盖月份换算、同期口径、逐日快照、着色等级与全部文案分支。
 */
class MonthReviewTest {

    /** 固定「今天」，避免用例随真实日期漂移 */
    private val today = LocalDate.of(2026, 11, 17)

    private val month = MonthKey.of(today)

    /** 本月数据：1、2 号有记录，3 号空白，4、5 号有记录，18 号之后的数据必须被忽略 */
    private val dayMinutes = mapOf(
        "2026-09-01" to 10,
        "2026-10-01" to 20,
        "2026-10-02" to 40,
        "2026-10-03" to 60,
        "2026-11-01" to 30,
        "2026-11-02" to 60,
        "2026-11-04" to 90,
        "2026-11-05" to 120,
        "2026-11-18" to 500,
        "2026-11-30" to 700
    )

    private fun report(goal: Int = 60): MonthReport = MonthReview.build(month, dayMinutes, goal, today)

    // ---------- MonthKey ----------

    @Test
    fun key_formatsTwoDigits() {
        assertEquals("2026-11", month.key)
        assertEquals("2026年11月", month.label)
        assertEquals("11月", month.shortLabel)
        assertEquals("2026-11-05", month.dayKey(5))
    }

    @Test
    fun prev_crossesYearBoundary() {
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).prev())
        assertEquals(MonthKey(2026, 10), MonthKey(2026, 11).prev())
    }

    @Test
    fun next_crossesYearBoundary() {
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
        assertEquals(MonthKey(2026, 12), MonthKey(2026, 11).next())
    }

    @Test
    fun minusMonths_crossesYears() {
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 11).minusMonths(11))
        assertEquals(MonthKey(2025, 11), MonthKey(2026, 11).minusMonths(12))
        assertEquals(MonthKey(2026, 11), MonthKey(2026, 11).minusMonths(0))
    }

    @Test
    fun of_normalizesOutOfRangeMonth() {
        assertEquals(MonthKey(2027, 1), MonthKey.of(2026, 13))
        assertEquals(MonthKey(2025, 12), MonthKey.of(2026, 0))
    }

    @Test
    fun length_handlesLeapFebruary() {
        assertEquals(29, MonthKey(2024, 2).length)
        assertEquals(28, MonthKey(2026, 2).length)
        assertEquals(30, MonthKey(2026, 4).length)
        assertEquals(31, MonthKey(2026, 12).length)
    }

    @Test
    fun firstWeekday_isMondayBased() {
        // 2026-06-01 是周一
        assertEquals(0, MonthKey(2026, 6).firstWeekday)
        // 2026-11-01 是周日
        assertEquals(6, MonthKey(2026, 11).firstWeekday)
    }

    @Test
    fun parse_roundTripAndInvalid() {
        assertEquals(month, MonthKey.parse("2026-11"))
        assertNull(MonthKey.parse("2026/11"))
        assertNull(MonthKey.parse("2026-13"))
        assertNull(MonthKey.parse("abc-de"))
        assertEquals(MonthKey(2026, 11), MonthKey.ofDayKey("2026-11-30"))
        assertNull(MonthKey.ofDayKey("2026-11"))
    }

    // ---------- 逐日快照 ----------

    @Test
    fun build_currentMonth_onlyCountsElapsedDays() {
        val result = report()
        assertEquals(17, result.elapsedDays)
        // 18 号与 30 号的数据必须被忽略
        assertEquals(300, result.totalMinutes)
        assertEquals(30, result.days.size)
        assertTrue(result.days[17].isFuture)
        assertFalse(result.days[0].isFuture)
        assertTrue(result.days[16].isToday)
    }

    @Test
    fun build_pastMonth_countsWholeMonth() {
        val lastMonth = MonthKey(2026, 10)
        val result = MonthReview.build(lastMonth, dayMinutes, 60, today)
        assertEquals(31, result.elapsedDays)
        assertFalse(result.isCurrent)
        assertEquals(120, result.totalMinutes)
        assertTrue(result.days.none { it.isFuture })
    }

    @Test
    fun build_futureMonth_hasNoElapsedDays() {
        val result = MonthReview.build(MonthKey(2026, 12), dayMinutes, 60, today)
        assertEquals(0, result.elapsedDays)
        assertEquals(0, result.totalMinutes)
        assertEquals(0, result.dailyAverage)
        assertEquals(0, result.longestStreak)
        assertTrue(result.isEmpty)
    }

    @Test
    fun build_prevMinutesUsesSameElapsedSpan() {
        val result = report()
        // 只取 10 月 1~17 号：20 + 40 + 60 = 120
        assertEquals(120, result.prevMinutes)
        assertEquals(180, result.delta)
    }

    @Test
    fun build_countsActiveAndAchievedDays() {
        val result = report(goal = 60)
        assertEquals(4, result.activeDays)
        assertEquals(3, result.achievedDays)
    }

    @Test
    fun build_goalZeroDisablesAchieved() {
        val result = report(goal = 0)
        assertEquals(0, result.achievedDays)
        assertEquals(4, result.activeDays)
    }

    @Test
    fun build_bestDayIsMaxAndTieTakesFirst() {
        assertEquals(5, report().bestDay?.day)
        val tie = mapOf("2026-11-02" to 90, "2026-11-03" to 90)
        assertEquals(2, MonthReview.build(month, tie, 60, today).bestDay?.day)
    }

    @Test
    fun build_bestDayIsNullWhenEmpty() {
        val result = MonthReview.build(month, emptyMap(), 60, today)
        assertNull(result.bestDay)
        assertTrue(result.isEmpty)
    }

    @Test
    fun build_longestStreakStopsAtGap() {
        // 1、2 号连续，3 号断，4、5 号连续 → 最长 2 天
        assertEquals(2, report().longestStreak)
    }

    @Test
    fun dailyAverage_dividesByElapsedDays() {
        // 300 / 17 = 17.6 → 18，而不是按活跃天数 (300/4 = 75)
        assertEquals(18, report().dailyAverage)
    }

    @Test
    fun projectedMinutes_scalesToWholeMonth() {
        // 300 / 17 * 30 = 529.4 → 529
        assertEquals(529, report().projectedMinutes)
    }

    @Test
    fun projectedMinutes_pastMonthEqualsTotal() {
        val lastMonth = MonthReview.build(MonthKey(2026, 10), dayMinutes, 60, today)
        assertEquals(lastMonth.totalMinutes, lastMonth.projectedMinutes)
    }

    @Test
    fun trend_reflectsDelta() {
        assertEquals(MonthTrend.UP, report().trend)
        val weaker = mapOf("2026-10-01" to 500, "2026-11-01" to 10)
        assertEquals(MonthTrend.DOWN, MonthReview.build(month, weaker, 60, today).trend)
        val flat = mapOf("2026-10-01" to 10, "2026-11-01" to 10)
        assertEquals(MonthTrend.FLAT, MonthReview.build(month, flat, 60, today).trend)
    }

    @Test
    fun remainingDays_countsRestOfMonth() {
        assertEquals(13, report().remainingDays)
    }

    // ---------- 着色等级 ----------

    @Test
    fun level_withGoalFollowsRatio() {
        assertEquals(0, MonthReview.level(0, 60))
        assertEquals(1, MonthReview.level(10, 60))
        assertEquals(2, MonthReview.level(24, 60))
        assertEquals(3, MonthReview.level(45, 60))
        assertEquals(4, MonthReview.level(60, 60))
        assertEquals(4, MonthReview.level(200, 60))
    }

    @Test
    fun level_withoutGoalUsesAbsoluteMinutes() {
        assertEquals(0, MonthReview.level(0, 0))
        assertEquals(1, MonthReview.level(5, 0))
        assertEquals(2, MonthReview.level(15, 0))
        assertEquals(3, MonthReview.level(45, 0))
        assertEquals(4, MonthReview.level(60, 0))
    }

    @Test
    fun level_isCappedAtFour() {
        assertEquals(MonthReview.MAX_LEVEL, MonthReview.level(1000, 60))
    }

    // ---------- 文案 ----------

    @Test
    fun durationText_switchesUnit() {
        assertEquals("0 分钟", MonthReview.durationText(0))
        assertEquals("45 分钟", MonthReview.durationText(45))
        assertEquals("1 小时", MonthReview.durationText(60))
        assertEquals("2 小时 15 分", MonthReview.durationText(135))
        assertEquals("0 分钟", MonthReview.durationText(-10))
    }

    @Test
    fun badgeText_coversAllBranches() {
        assertEquals("暂无记录", MonthReview.badgeText(MonthReview.build(month, emptyMap(), 60, today)))
        assertEquals("首次记录", MonthReview.badgeText(MonthReview.build(month, mapOf("2026-11-01" to 30), 60, today)))
        assertEquals("+150%", MonthReview.badgeText(report()))
        val weaker = mapOf("2026-10-01" to 200, "2026-11-01" to 100)
        assertEquals("-50%", MonthReview.badgeText(MonthReview.build(month, weaker, 60, today)))
        val flat = mapOf("2026-10-01" to 10, "2026-11-01" to 10)
        assertEquals("持平", MonthReview.badgeText(MonthReview.build(month, flat, 60, today)))
    }

    @Test
    fun deltaText_coversAllBranches() {
        assertEquals("这个月还没有专注记录", MonthReview.deltaText(MonthReview.build(month, emptyMap(), 60, today)))
        assertEquals("上月同期还没有专注记录", MonthReview.deltaText(MonthReview.build(month, mapOf("2026-11-01" to 30), 60, today)))
        assertEquals("比上月同期多 180 分钟", MonthReview.deltaText(report()))
        val weaker = mapOf("2026-10-01" to 200, "2026-11-01" to 100)
        assertEquals("比上月同期少 100 分钟", MonthReview.deltaText(MonthReview.build(month, weaker, 60, today)))
        val flat = mapOf("2026-10-01" to 10, "2026-11-01" to 10)
        assertEquals("与上月同期持平", MonthReview.deltaText(MonthReview.build(month, flat, 60, today)))
    }

    @Test
    fun spanText_differsForCurrentAndPastMonth() {
        assertEquals("11月1日 – 11月17日（17/30 天）", MonthReview.spanText(report()))
        val lastMonth = MonthReview.build(MonthKey(2026, 10), dayMinutes, 60, today)
        assertEquals("10月1日 – 10月31日（整月 31 天）", MonthReview.spanText(lastMonth))
    }

    @Test
    fun metricText_switchesGoalLabel() {
        assertTrue(MonthReview.metricText(report(goal = 60)).contains("达标 3 天"))
        assertTrue(MonthReview.metricText(report(goal = 0)).contains("最长连续 2 天"))
        assertTrue(MonthReview.metricText(report()).contains("最佳 11月5日 120 分钟"))
        assertTrue(
            MonthReview.metricText(MonthReview.build(month, emptyMap(), 60, today)).contains("完成第一个番茄")
        )
    }

    @Test
    fun averageText_dividesByElapsed() {
        assertEquals("日均 18 分钟", MonthReview.averageText(report()))
    }

    @Test
    fun projectionText_dependsOnMonthState() {
        assertTrue(MonthReview.projectionText(report()).startsWith("按当前节奏"))
        val lastMonth = MonthReview.build(MonthKey(2026, 10), dayMinutes, 60, today)
        assertEquals("整月合计 2 小时", MonthReview.projectionText(lastMonth))
        assertEquals("", MonthReview.projectionText(MonthReview.build(month, emptyMap(), 60, today)))
    }

    @Test
    fun summaryText_encouragesWhenMostDaysDone() {
        val steady = (1..17).associate { "2026-11-%02d".format(it) to 60 }
        val result = MonthReview.build(month, steady, 60, today)
        assertTrue(MonthReview.summaryText(result).contains("节奏很稳"))
        assertTrue(
            MonthReview.summaryText(MonthReview.build(month, emptyMap(), 60, today)).contains("先完成一个番茄")
        )
    }

    // ---------- 工具方法 ----------

    @Test
    fun monthsBetween_isAscendingAndInclusive() {
        val months = MonthReview.monthsBetween(MonthKey(2025, 11), MonthKey(2026, 2))
        assertEquals(listOf(MonthKey(2025, 11), MonthKey(2025, 12), MonthKey(2026, 1), MonthKey(2026, 2)), months)
        assertTrue(MonthReview.monthsBetween(MonthKey(2026, 2), MonthKey(2026, 1)).isEmpty())
        assertEquals(1, MonthReview.monthsBetween(month, month).size)
    }

    @Test
    fun keyedByDay_convertsTimestampsToDateKeys() {
        val stamp = DayKeys.parse("2026-11-03")
        val converted = MonthReview.keyedByDay(mapOf(stamp to 42))
        assertEquals(42, converted["2026-11-03"])
        assertTrue(MonthReview.keyedByDay(emptyMap()).isEmpty())
    }

    @Test
    fun build_monthReportMatchesParsedMonthKey() {
        val parsed = MonthKey.parse("2026-11") ?: error("月份解析失败")
        assertEquals(month, parsed)
        assertNotNull(report().month)
    }
}
