package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就达成预测 [AchievementForecasts] 的单元测试。
 *
 * 覆盖三类指标的不同口径：累积型给天数、峰值型只给差距、连续型只给「还要连几天」；
 * 以及已解锁 / 满分未落库 / 近期无数据这些边界。
 */
class AchievementForecastTest {

    /** 一天毫秒数 */
    private val day = 24L * 60L * 60L * 1000L

    /** 固定「现在」时刻，避免用例随系统时间漂移 */
    private val now = 1_800_000_000_000L

    /** 取一个已有成就定义 */
    private fun def(id: String): AchievementDef = Achievements.ALL.first { it.id == id }

    /** 直接构造成就状态，便于精确控制进度 */
    private fun state(
        id: String,
        current: Int,
        unlockedAt: Long = 0L
    ): AchievementState {
        val definition = def(id)
        return AchievementState(
            def = definition,
            current = current,
            target = definition.target,
            unlocked = unlockedAt > 0L,
            unlockedAt = unlockedAt,
            percent = Achievements.percent(current, definition.target)
        )
    }

    /** 近 14 天：累计 700 分钟（日均 50）、9 天活跃、最长连续 4 天、最佳单日 80 分钟 */
    private val recent = AchievementStats(
        totalMinutes = 700,
        totalPomos = 7,
        activeDays = 9,
        longestStreak = 4,
        bestDayMinutes = 80,
        earlyMinutes = 30,
        nightMinutes = 20,
        weekendMinutes = 120
    )

    @Test
    fun unlocked_answersWhenLit() {
        val unlockedAt = now - 3 * day
        val forecast = AchievementForecasts.of(state("first_focus", 25, unlockedAt), null, now = now)

        assertEquals(ForecastKind.UNLOCKED, forecast.kind)
        assertTrue(forecast.headline.startsWith("点亮于 "))
        assertEquals("3 天前点亮", forecast.detail)
        assertEquals(0, forecast.daysLeft)
    }

    @Test
    fun unlockedToday_saysToday() {
        val forecast = AchievementForecasts.of(state("first_focus", 25, now - 3600_000L), null, now = now)

        assertEquals(ForecastKind.UNLOCKED, forecast.kind)
        assertEquals("今天点亮", forecast.detail)
    }

    @Test
    fun daysSince_clampsFutureAndZero() {
        assertEquals(0, AchievementForecasts.daysSince(now + day, now))
        assertEquals(0, AchievementForecasts.daysSince(0L, now))
        assertEquals(2, AchievementForecasts.daysSince(now - 2 * day, now))
    }

    @Test
    fun ready_whenFullButNotStored() {
        val forecast = AchievementForecasts.of(state("hours_10", 600), recent, now = now)

        assertEquals(ForecastKind.READY, forecast.kind)
        assertTrue(forecast.headline.contains("等待结算"))
    }

    @Test
    fun pace_predictsDaysFromAverage() {
        // 已 300 / 600，日均 50 分钟 → 还差 300，正好 6 天
        val forecast = AchievementForecasts.of(state("hours_10", 300), recent, now = now)

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertEquals(6, forecast.daysLeft)
        assertEquals("按最近的节奏，约 6 天后点亮", forecast.headline)
        assertTrue(forecast.detail.contains("日均 50 分钟"))
    }

    @Test
    fun pace_roundsUpToOneDay() {
        // 还差 40 分钟，日均 50 → 0.8 天 → 向上取整 1 天
        val forecast = AchievementForecasts.of(state("hours_10", 560), recent, now = now)

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertEquals(1, forecast.daysLeft)
        assertEquals("按最近的节奏，明天就能点亮", forecast.headline)
    }

    @Test
    fun pace_roundsUpFractionalDays() {
        // 还差 301 分钟，日均 50 → 6.02 天 → 7 天
        val forecast = AchievementForecasts.of(state("hours_10", 299), recent, now = now)

        assertEquals(7, forecast.daysLeft)
    }

    @Test
    fun stalled_whenNoRecentGain() {
        val forecast = AchievementForecasts.of(state("hours_10", 300), AchievementStats(), now = now)

        assertEquals(ForecastKind.STALLED, forecast.kind)
        assertEquals("近 14 天没有新的积累", forecast.headline)
    }

    @Test
    fun pace_saysOverOneYearWhenTooFar() {
        // 只练了 1 分钟：日均约 0.07 分钟，还差 599 分钟 → 远超一年
        val forecast = AchievementForecasts.of(
            state("hours_10", 1),
            AchievementStats(totalMinutes = 1),
            now = now
        )

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertTrue(forecast.daysLeft > AchievementForecasts.LONG_ETA_DAYS)
        assertEquals("按最近的节奏，还要一年以上", forecast.headline)
    }

    @Test
    fun unknown_withoutRecentStats() {
        val forecast = AchievementForecasts.of(state("hours_10", 300), null, now = now)

        assertEquals(ForecastKind.UNKNOWN, forecast.kind)
        assertEquals("还差 300 分钟", forecast.headline)
        assertEquals(0, forecast.daysLeft)
    }

    @Test
    fun peak_reportsGapOnly() {
        val forecast = AchievementForecasts.of(state("deep_day", 60), recent, now = now)

        assertEquals(ForecastKind.PEAK, forecast.kind)
        assertEquals("需要有一天专注满 120 分钟", forecast.headline)
        assertTrue(forecast.detail.contains("最好的单日 80 分钟"))
        assertTrue(forecast.detail.contains("还差 60 分钟"))
        assertEquals(0, forecast.daysLeft)
    }

    @Test
    fun streak_reportsRemainingDays() {
        val forecast = AchievementForecasts.of(state("streak_30", 2), recent, now = now)

        assertEquals(ForecastKind.STREAK, forecast.kind)
        assertEquals("需要连续 30 天不中断", forecast.headline)
        assertTrue(forecast.detail.contains("最长连续 4 天"))
        assertTrue(forecast.detail.contains("还差 28 天"))
    }

    @Test
    fun activeDays_predictsByAttendance() {
        // 已 40 / 100，近 14 天有 9 天专注 → 60 * 14 / 9 = 93.3 → 94 天
        val forecast = AchievementForecasts.of(state("century", 40), recent, now = now)

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertEquals(94, forecast.daysLeft)
        assertTrue(forecast.detail.contains("有 9 天专注"))
    }

    @Test
    fun activeDays_stalledWhenNoAttendance() {
        val forecast = AchievementForecasts.of(
            state("century", 40),
            AchievementStats(activeDays = 0),
            now = now
        )

        assertEquals(ForecastKind.STALLED, forecast.kind)
    }

    @Test
    fun pomos_keepsFractionalAverage() {
        // 近 14 天 7 个 → 日均 0.5 个；还差 5 个 → 10 天
        val forecast = AchievementForecasts.of(state("pomo_10", 5), recent, now = now)

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertEquals(10, forecast.daysLeft)
        assertTrue(forecast.detail.contains("日均 0.5 个"))
    }

    @Test
    fun pace_windowDaysChangesAverage() {
        // 近 7 天 350 分钟 → 日均 50，还差 300 → 6 天
        val seven = AchievementForecasts.of(
            state("hours_10", 300),
            AchievementStats(totalMinutes = 350),
            windowDays = 7,
            now = now
        )

        assertEquals(6, seven.daysLeft)
        assertTrue(seven.detail.contains("近 7 天"))
    }

    @Test
    fun pace_invalidWindowFallsBack() {
        // 日均仍按 14 天算：700 / 14 = 50 → 6 天
        val forecast = AchievementForecasts.of(
            state("hours_10", 300),
            AchievementStats(totalMinutes = 700),
            windowDays = 0,
            now = now
        )

        assertEquals(6, forecast.daysLeft)
        assertTrue(forecast.detail.contains("近 14 天"))
    }

    @Test
    fun pace_earlyBirdUsesEarlyMinutes() {
        // 清晨累计 30 分钟，日均 30/14 ≈ 2.14；还差 150 → 70 天
        val forecast = AchievementForecasts.of(state("early_bird", 30), recent, now = now)

        assertEquals(ForecastKind.PACE, forecast.kind)
        assertEquals(70, forecast.daysLeft)
        assertTrue(forecast.detail.contains("日均 2.14 分钟"))
    }

    @Test
    fun nextLine_appendsEta() {
        val target = state("hours_10", 300)
        val forecast = AchievementForecasts.of(target, recent, now = now)

        assertEquals("距离「十小时」还差 300 分钟 · 约 6 天", AchievementForecasts.nextLine(target, forecast))
    }

    @Test
    fun nextLine_readyWording() {
        val target = state("hours_10", 600)
        val forecast = AchievementForecasts.of(target, recent, now = now)

        assertEquals("「十小时」即将点亮", AchievementForecasts.nextLine(target, forecast))
    }

    @Test
    fun nextLine_withoutPaceKeepsPlainText() {
        val target = state("hours_10", 300)
        val forecast = AchievementForecasts.of(target, AchievementStats(), now = now)

        assertEquals("距离「十小时」还差 300 分钟", AchievementForecasts.nextLine(target, forecast))
        assertEquals(ForecastKind.STALLED, forecast.kind)
    }

    @Test
    fun unlocked_skipsPaceBranch() {
        val target = state("first_focus", 25, now - day)
        val forecast = AchievementForecasts.of(target, recent, now = now)

        assertTrue(target.unlocked)
        assertEquals(ForecastKind.UNLOCKED, forecast.kind)
        assertFalse(forecast.headline.contains("按最近的节奏"))
    }

    @Test
    fun rateText_picksDecimalsByScale() {
        assertEquals("42", AchievementForecasts.rateText(42.4f))
        assertEquals("10", AchievementForecasts.rateText(10f))
        assertEquals("9.99", AchievementForecasts.rateText(9.994f))
        assertEquals("1", AchievementForecasts.rateText(1f))
        assertEquals("0.7", AchievementForecasts.rateText(0.7f))
        assertEquals("0.07", AchievementForecasts.rateText(0.07f))
        assertEquals("0", AchievementForecasts.rateText(0f))
        assertEquals("0", AchievementForecasts.rateText(-3f))
    }

    @Test
    fun metricHint_coversAllMetrics() {
        AchievementMetric.values().forEach { metric ->
            assertTrue("缺少口径说明：$metric", AchievementForecasts.metricHint(metric).isNotBlank())
        }
        assertEquals("连续的自然日，中断一天重新计数", AchievementForecasts.metricHint(AchievementMetric.LONGEST_STREAK))
        assertEquals("周六与周日的专注时长", AchievementForecasts.metricHint(AchievementMetric.WEEKEND_MINUTES))
    }

    @Test
    fun allAchievements_produceHeadline() {
        val stats = AchievementStats(
            totalMinutes = 120, totalPomos = 3, activeDays = 2, longestStreak = 1,
            bestDayMinutes = 45, earlyMinutes = 10, nightMinutes = 10, weekendMinutes = 20
        )
        Achievements.ALL.forEach { definition ->
            val state = Achievements.stateOf(definition, stats, 0L)
            val forecast = AchievementForecasts.of(state, recent, now = now)
            assertTrue("${definition.id} 缺少主文案", forecast.headline.isNotBlank())
            assertEquals("${definition.id} 未解锁不应返回 UNLOCKED", false, forecast.kind == ForecastKind.UNLOCKED)
        }
    }
}
