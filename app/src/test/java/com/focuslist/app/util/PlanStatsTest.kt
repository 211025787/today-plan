package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 日历计划统计的纯逻辑测试：只覆盖 plan_logs 口径，和番茄专注统计互不相干。
 */
class PlanStatsTest {

    private fun day(year: Int, month: Int, date: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month, date, 0, 0, 0)
        return cal.timeInMillis
    }

    private val today = day(2026, Calendar.JUNE, 17) // 周三
    private val weekStart = day(2026, Calendar.JUNE, 15) // 周一
    private val monthStart = day(2026, Calendar.JUNE, 1)

    private val plans = listOf(
        PlanInfoValue(1L, "健身", "💪", "#4772FA"),
        PlanInfoValue(2L, "阅读", "📖", "#E94B3C")
    )

    private fun of(
        logs: List<PlanLogValue>,
        goal: Int = 30,
        windowDays: Int = 42
    ) = PlanStats.of(logs, plans, today, weekStart, monthStart, goal, windowDays)

    private fun log(planId: Long, date: Long, sec: Long) = PlanLogValue(planId, date, sec)

    @Test
    fun `同一天多个计划先累加秒再取整`() {
        // 各 30 秒，逐个取整会变成 0+0，合计应得 1 分钟
        val result = of(
            listOf(
                log(1L, today, 30L),
                log(2L, today, 30L)
            )
        )
        assertEquals(1, result.todayMinutes)
        assertEquals(1, result.activeDays)
    }

    @Test
    fun `今日 本周 本月 累计按区间汇总`() {
        val lastWeek = day(2026, Calendar.JUNE, 14) // 周日，属于上周
        val lastMonth = day(2026, Calendar.MAY, 20)
        val inMonthBeforeWeek = day(2026, Calendar.JUNE, 3)
        val result = of(
            listOf(
                log(1L, today, 20 * 60L),
                log(1L, weekStart, 10 * 60L),
                log(1L, inMonthBeforeWeek, 5 * 60L),
                log(1L, lastWeek, 7 * 60L),
                log(1L, lastMonth, 3 * 60L)
            )
        )
        assertEquals(20, result.todayMinutes)
        assertEquals(30, result.weekMinutes)
        // 6 月 14 日虽属上周，但仍在 6 月里：20 + 10 + 5 + 7 = 42
        assertEquals(42, result.monthMinutes)
        assertEquals(45, result.totalMinutes)
    }

    @Test
    fun `活跃与达标天数按天计数`() {
        val d1 = day(2026, Calendar.JUNE, 15)
        val d2 = day(2026, Calendar.JUNE, 16)
        val result = of(
            listOf(
                log(1L, d1, 30 * 60L),
                log(1L, d2, 10 * 60L),
                log(2L, today, 31 * 60L)
            ),
            goal = 30
        )
        assertEquals(3, result.activeDays)
        assertEquals(2, result.goalDays)
        assertEquals(31, result.bestDayMinutes)
    }

    @Test
    fun `今天还没达标时连续天数从昨天起算`() {
        val yesterday = DateUtils.addDays(today, -1)
        val dayBefore = DateUtils.addDays(today, -2)
        val result = of(
            listOf(
                log(1L, yesterday, 40 * 60L),
                log(1L, dayBefore, 35 * 60L),
                log(1L, today, 5 * 60L)
            ),
            goal = 30
        )
        assertEquals(2, result.streakDays)
    }

    @Test
    fun `今天达标则把今天算进连续天数`() {
        val yesterday = DateUtils.addDays(today, -1)
        val result = of(
            listOf(
                log(1L, yesterday, 40 * 60L),
                log(1L, today, 30 * 60L)
            ),
            goal = 30
        )
        assertEquals(2, result.streakDays)
    }

    @Test
    fun `逐日窗口按日期升序且缺失补零`() {
        val threeDaysAgo = DateUtils.addDays(today, -3)
        val result = of(listOf(log(1L, threeDaysAgo, 15 * 60L)), windowDays = 7)
        assertEquals(7, result.days.size)
        assertEquals(today, result.days.last().date)
        assertEquals(0, result.days.last().minutes)
        assertEquals(15, result.days.first { it.date == threeDaysAgo }.minutes)
        assertTrue(result.days.zipWithNext().all { it.first.date < it.second.date })
    }

    @Test
    fun `计划排行按分钟降序 平局按 id 升序`() {
        val result = of(
            listOf(
                log(1L, today, 10 * 60L),
                log(2L, today, 10 * 60L)
            )
        )
        assertEquals(listOf(1L, 2L), result.perPlan.map { it.planId })
        assertEquals("健身", result.perPlan[0].title)
        assertEquals("#4772FA", result.perPlan[0].colorHex)
    }

    @Test
    fun `没有记录的计划不进排行`() {
        val result = of(listOf(log(999L, today, 50 * 60L)))
        assertTrue(result.perPlan.isEmpty())
        assertEquals(50, result.todayMinutes)
    }

    @Test
    fun `空记录与负秒数都安全`() {
        val empty = of(emptyList())
        assertEquals(0, empty.totalMinutes)
        assertEquals(0, empty.activeDays)
        assertEquals(0, empty.goalDays)
        assertEquals(0, empty.streakDays)
        assertTrue(empty.perPlan.isEmpty())

        val negative = of(listOf(log(1L, today, -100L)))
        assertEquals(0, negative.todayMinutes)
        assertEquals(0, negative.activeDays)
    }

    @Test
    fun `月历网格从周一起始并补齐整周`() {
        val november = day(2026, Calendar.NOVEMBER, 1) // 2026-11-01 是周日
        val cells = PlanStats.monthGrid(emptyList(), november)
        assertEquals(6, cells.indexOfFirst { it != null }) // 前面 6 个空格
        assertEquals(30, cells.count { it != null }) // 11 月 30 天
        assertEquals(0, cells.size % 7)
        assertTrue(cells.take(6).all { it == null })
        assertEquals(november, cells[6]?.date)
    }

    @Test
    fun `月历网格沿用传入的逐日数据`() {
        val november = day(2026, Calendar.NOVEMBER, 1)
        val target = day(2026, Calendar.NOVEMBER, 5)
        val cells = PlanStats.monthGrid(listOf(PlanDayValue(target, 66)), november)
        assertEquals(66, cells.first { it?.date == target }?.minutes)
        assertEquals(0, cells[6]?.minutes)
    }

    @Test
    fun `着色分档有目标与无目标两套边界`() {
        assertEquals(0, PlanStats.level(0, 60))
        assertEquals(1, PlanStats.level(19, 60))
        assertEquals(2, PlanStats.level(30, 60))
        assertEquals(3, PlanStats.level(50, 60))
        assertEquals(4, PlanStats.level(60, 60))
        assertEquals(4, PlanStats.level(180, 60))
        assertEquals(1, PlanStats.level(10, 0))
        assertEquals(4, PlanStats.level(100, 0))
    }

    @Test
    fun `时长文案`() {
        assertEquals("0 分", PlanStats.durationText(0))
        assertEquals("45 分", PlanStats.durationText(45))
        assertEquals("2 小时", PlanStats.durationText(120))
        assertEquals("1 小时 20 分", PlanStats.durationText(80))
        assertEquals("0 分", PlanStats.durationText(-5))
    }
}
