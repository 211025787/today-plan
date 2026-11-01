package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划投入画像（连续达标 / 近 14 天窗口 / 文案）的单元测试。
 *
 * 全部用相对日期构造，避免依赖测试运行当天的真实日期。
 */
class PlanInsightTest {

    /** 基准日 0 点（2026-03-01，取本地时区） */
    private val base: Long = DateUtils.startOfDay(
        LocalDate.of(2026, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    /** 相对基准日第 offset 天的 0 点 */
    private fun day(offset: Int): Long = DateUtils.addDays(base, offset)

    /** 30 分钟对应的秒数 */
    private val goalSec = 30 * 60L

    /**
     * 构造计划投入画像。
     *
     * @param logs 偏移天 -> 秒数
     */
    private fun insight(
        start: Int = 0,
        end: Int = 60,
        goal: Int = 30,
        logs: Map<Int, Long> = emptyMap(),
        today: Int = 20,
        window: Int = PlanInsights.WINDOW_DAYS
    ): PlanInsight {
        val map = logs.entries.associate { (offset, sec) -> day(offset) to sec }
        return PlanInsights.of(day(start), day(end), goal, map, day(today), window)
    }

    // ---- 窗口构建 ----

    @Test
    fun `窗口取最近14天且最后一天是今天`() {
        val result = insight(today = 20)
        assertEquals(14, result.windowDays)
        assertEquals(day(7), result.days.first().date)
        assertEquals(day(20), result.days.last().date)
    }

    @Test
    fun `窗口起点夹到计划开始日`() {
        val result = insight(start = 18, today = 20)
        assertEquals(3, result.windowDays)
        assertEquals(day(18), result.days.first().date)
        assertEquals(day(20), result.days.last().date)
    }

    @Test
    fun `计划已结束时窗口锚定在结束日`() {
        val result = insight(start = 0, end = 10, today = 20)
        assertEquals(11, result.windowDays)
        assertEquals(day(0), result.days.first().date)
        assertEquals(day(10), result.days.last().date)
        assertFalse(result.activeToday)
        assertNull(result.today)
    }

    @Test
    fun `计划还没开始时返回空画像`() {
        val result = insight(start = 25, today = 20)
        assertTrue(result.isEmpty)
        assertEquals(0, result.windowDays)
        assertEquals(0, result.streak)
        assertEquals(0L, result.totalSeconds)
        assertFalse(result.activeToday)
        assertEquals("计划还没开始", result.headline)
    }

    @Test
    fun `没有记录的日期补零`() {
        val result = insight(today = 20)
        assertTrue(result.days.all { it.seconds == 0L })
        assertEquals(goalSec, result.days.first().goalSeconds)
    }

    @Test
    fun `窗口天数传0也至少保留一天`() {
        val result = insight(today = 20, window = 0)
        assertEquals(1, result.windowDays)
        assertEquals(day(20), result.days.single().date)
    }

    @Test
    fun `计划范围外的记录不计入窗口`() {
        val result = insight(start = 15, end = 30, logs = mapOf(10 to 9999L, 16 to 1200L), today = 20)
        assertEquals(1200L, result.totalSeconds)
        assertEquals(1, result.activeDays)
    }

    // ---- 达标判定 ----

    @Test
    fun `刚好达到目标即算达标`() {
        val result = insight(logs = mapOf(20 to goalSec), today = 20)
        assertTrue(result.today!!.achieved)
        assertEquals(1, result.achievedDays)
    }

    @Test
    fun `差一秒不算达标`() {
        val result = insight(logs = mapOf(20 to goalSec - 1), today = 20)
        assertFalse(result.today!!.achieved)
        assertEquals(0, result.achievedDays)
    }

    @Test
    fun `目标为零时永不达标且比例为零`() {
        val point = PlanDayPoint(day(0), 3600L, 0L)
        assertFalse(point.achieved)
        assertEquals(0f, point.ratio, 0.0001f)
        assertEquals(0, PlanInsights.of(day(0), day(60), 0, mapOf(day(0) to 3600L), day(0)).streak)
    }

    // ---- 连续达标 ----

    @Test
    fun `今天达标时从今天开始数连续`() {
        val result = insight(logs = mapOf(18 to goalSec, 19 to goalSec, 20 to goalSec), today = 20)
        assertEquals(3, result.streak)
    }

    @Test
    fun `今天未达标时从昨天往回数`() {
        val result = insight(logs = mapOf(17 to goalSec, 18 to goalSec, 19 to goalSec), today = 20)
        assertEquals(3, result.streak)
    }

    @Test
    fun `中间断档后连续只算最近一段`() {
        val result = insight(logs = mapOf(17 to goalSec, 19 to goalSec, 20 to goalSec), today = 20)
        assertEquals(2, result.streak)
    }

    @Test
    fun `今天和昨天都没达标时连续为零`() {
        val result = insight(logs = mapOf(17 to goalSec, 18 to goalSec), today = 20)
        assertEquals(0, result.streak)
    }

    @Test
    fun `计划已结束时最后一天没达标不再回落`() {
        val result = insight(start = 0, end = 10, logs = mapOf(8 to goalSec, 9 to goalSec), today = 20)
        assertEquals(0, result.streak)
    }

    @Test
    fun `计划已结束且最后一天达标时正常计数`() {
        val result = insight(start = 0, end = 10, logs = mapOf(9 to goalSec, 10 to goalSec), today = 20)
        assertEquals(2, result.streak)
    }

    @Test
    fun `连续不越过计划开始日`() {
        val result = insight(start = 18, logs = mapOf(17 to goalSec, 18 to goalSec, 19 to goalSec), today = 20)
        assertEquals(2, result.streak)
    }

    // ---- 最长连续 ----

    @Test
    fun `最长连续可以长于展示窗口`() {
        val logs = (0..9).associate { it to goalSec }
        val result = insight(logs = logs, today = 9, window = 3)
        assertEquals(10, result.bestStreak)
        assertEquals(3, result.windowDays)
    }

    @Test
    fun `最长连续只统计计划有效期内的日子`() {
        val logs = (3..7).associateWith { goalSec }
        val result = insight(start = 5, end = 60, logs = logs, today = 7)
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `最长连续遇断档重新计数并取最大值`() {
        val logs = mapOf(
            0 to goalSec, 1 to goalSec, 2 to goalSec,
            4 to goalSec,
            6 to goalSec, 7 to goalSec
        )
        val result = insight(logs = logs, today = 10)
        assertEquals(3, result.bestStreak)
    }

    // ---- 聚合 ----

    @Test
    fun `累计只加窗口内的投入`() {
        val result = insight(
            logs = mapOf(17 to 60L, 18 to 120L, 20 to 600L),
            today = 20,
            window = 4
        )
        assertEquals(780L, result.totalSeconds)
        assertEquals(3, result.activeDays)
    }

    @Test
    fun `活跃天数与达标天数分开统计`() {
        val result = insight(
            logs = mapOf(18 to 60L, 19 to goalSec, 20 to goalSec * 2),
            today = 20
        )
        assertEquals(3, result.activeDays)
        assertEquals(2, result.achievedDays)
        assertEquals(14, result.ratePercent)
    }

    @Test
    fun `日均按活跃日计算`() {
        val result = insight(logs = mapOf(18 to 600L, 19 to 1200L), today = 20)
        assertEquals(900L, result.averageSeconds)
        assertEquals(2, result.activeDays)
    }

    @Test
    fun `完全没有记录时各项为零`() {
        val result = insight(today = 20)
        assertEquals(0L, result.totalSeconds)
        assertEquals(0, result.activeDays)
        assertEquals(0, result.achievedDays)
        assertEquals(0L, result.averageSeconds)
        assertEquals(0, result.ratePercent)
        assertEquals("还没有投入记录", result.headline)
        assertEquals("今天还差 30 分钟达标", result.todayText)
        assertEquals("还没有投入记录", result.totalText)
        assertEquals("活跃日日均 —", result.averageText)
        assertEquals("达标率 0%（0/14 天）", result.rateText)
    }

    // ---- 单点派生 ----

    @Test
    fun `单点比例封顶且柱状图比例最多满格`() {
        val over = PlanDayPoint(day(0), 3600L, 1800L)
        assertEquals(PlanInsights.MAX_RATIO, over.ratio, 0.0001f)
        assertEquals(1f, over.barRatio, 0.0001f)
        assertEquals(1800L, over.overshootSeconds)
        assertEquals(0L, over.remainSeconds)

        val half = PlanDayPoint(day(0), 900L, 1800L)
        assertEquals(0.5f, half.ratio, 0.0001f)
        assertEquals(15, half.minutes)
        assertEquals(900L, half.remainSeconds)
    }

    // ---- 文案 ----

    @Test
    fun `标题文案按优先顺序分支`() {
        assertEquals("连续达标 3 天", insight(logs = mapOf(18 to goalSec, 19 to goalSec, 20 to goalSec), today = 20).headline)
        assertEquals("今天已达标", insight(logs = mapOf(20 to goalSec), today = 20).headline)
        assertEquals("近 14 天达标 1 天", insight(logs = mapOf(10 to goalSec), today = 20).headline)
        assertEquals("近 14 天还没达标", insight(logs = mapOf(20 to 60L), today = 20).headline)
        assertEquals("计划还没开始", insight(start = 25, today = 20).headline)
    }

    @Test
    fun `今日文案覆盖刚好达标与超出`() {
        assertEquals("今天刚好达标，30 分钟", insight(logs = mapOf(20 to goalSec), today = 20).todayText)
        assertEquals(
            "今天超出目标 10 分钟，共 40 分钟",
            insight(logs = mapOf(20 to goalSec + 600L), today = 20).todayText
        )
    }

    @Test
    fun `今日文案差额向上取整到分钟`() {
        val result = insight(logs = mapOf(20 to goalSec - 30L), today = 20)
        assertEquals("今天还差 1 分钟达标", result.todayText)
    }

    @Test
    fun `计划结束时今日文案说明已结束并给出累计`() {
        val result = insight(start = 0, end = 10, logs = mapOf(9 to 3600L), today = 20)
        assertEquals("计划已结束，共投入 1 小时", result.todayText)
    }

    @Test
    fun `时长文案覆盖分钟小时与不足一分钟`() {
        assertEquals("0 分钟", PlanInsights.durationText(0))
        assertEquals("不足 1 分钟", PlanInsights.durationText(30))
        assertEquals("1 分钟", PlanInsights.durationText(60))
        assertEquals("59 分钟", PlanInsights.durationText(3599))
        assertEquals("1 小时", PlanInsights.durationText(3600))
        assertEquals("1 小时 30 分", PlanInsights.durationText(5400))
    }

    @Test
    fun `向上取整分钟`() {
        assertEquals(0, PlanInsights.ceilMinutes(0))
        assertEquals(1, PlanInsights.ceilMinutes(1))
        assertEquals(1, PlanInsights.ceilMinutes(60))
        assertEquals(2, PlanInsights.ceilMinutes(61))
        assertEquals(30, PlanInsights.ceilMinutes(1800))
    }

    @Test
    fun `达标率文案含分子分母`() {
        val logs = (14..20).associate { it to goalSec }
        val result = insight(logs = logs, today = 20)
        assertEquals(7, result.achievedDays)
        assertEquals("达标率 50%（7/14 天）", result.rateText)
    }
}
