package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 计划结业 [PlanGraduations] 的单元测试。
 *
 * 基准计划：2026-11-01 ~ 2026-11-30，共 30 天，每天 30 分钟 → 目标 900 分钟。
 */
class PlanGraduationTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val plan = GraduationPlan(
        id = 3,
        title = "写作",
        icon = "✍️",
        colorHex = "#4772FA",
        start = day("2026-11-01"),
        end = day("2026-11-30"),
        dailyGoalMinutes = 30
    )

    @Test
    fun endedRecently_includesJustEndedPlan() {
        val list = PlanGraduations.endedRecently(listOf(plan), day("2026-12-05"))

        assertEquals(1, list.size)
        assertEquals(3L, list.first().id)
    }

    @Test
    fun endedRecently_excludesPlanEndingToday() {
        val list = PlanGraduations.endedRecently(listOf(plan), day("2026-11-30"))

        assertTrue(list.isEmpty())
    }

    @Test
    fun endedRecently_excludesPlansOlderThanWindow() {
        // 11-30 结束后第 31 天
        assertTrue(PlanGraduations.endedRecently(listOf(plan), day("2026-12-31")).isEmpty())
    }

    @Test
    fun endedRecently_windowIsInclusive() {
        // 正好第 30 天仍在窗口内
        assertEquals(1, PlanGraduations.endedRecently(listOf(plan), day("2026-12-30")).size)
    }

    @Test
    fun endedRecently_sortsByEndDesc() {
        val older = plan.copy(id = 1, end = day("2026-11-20"))
        val newer = plan.copy(id = 2, end = day("2026-11-29"))
        val list = PlanGraduations.endedRecently(listOf(older, newer), day("2026-12-05"))

        assertEquals(listOf(2L, 1L), list.map { it.id })
    }

    @Test
    fun endedRecently_customWindow() {
        val list = PlanGraduations.endedRecently(listOf(plan), day("2026-12-05"), days = 3)

        assertTrue(list.isEmpty())
    }

    @Test
    fun report_percentRoundsDown() {
        // 899 分钟 / 900 分钟 = 99.9% → 99
        val report = PlanGraduations.report(plan, 899 * 60L)

        assertEquals(99, report.completionPercent)
        assertEquals(30, report.totalDays)
        assertEquals(899, report.totalMinutes)
    }

    @Test
    fun report_percentCanExceedHundred() {
        val report = PlanGraduations.report(plan, 90_000L)

        assertEquals(166, report.completionPercent)
        assertTrue(report.verdict.contains("说到做到"))
    }

    @Test
    fun report_verdictTiers() {
        assertTrue(PlanGraduations.verdictOf(100).contains("说到做到"))
        assertTrue(PlanGraduations.verdictOf(85).contains("只差一点点"))
        assertTrue(PlanGraduations.verdictOf(50).contains("坚持过就是收获"))
        assertTrue(PlanGraduations.verdictOf(10).contains("至少你开始过"))
        assertTrue(PlanGraduations.verdictOf(0).contains("没有留下记录"))
    }

    @Test
    fun report_detailMentionsDaysAndTime() {
        val report = PlanGraduations.report(plan, 54_000L)

        assertEquals("共 30 天 · 累计 15 小时", report.detail)
    }

    @Test
    fun report_zeroGoalDoesNotDivideByZero() {
        val report = PlanGraduations.report(plan.copy(dailyGoalMinutes = 0), 3_600L)

        assertEquals(0, report.completionPercent)
    }

    @Test
    fun report_singleDayPlan() {
        val oneDay = plan.copy(start = day("2026-11-10"), end = day("2026-11-10"))
        val report = PlanGraduations.report(oneDay, 1_800L)

        assertEquals(1, report.totalDays)
        assertEquals(100, report.completionPercent)
    }

    @Test
    fun renewed_restartsFromToday() {
        val renewed = PlanGraduations.renewed(plan, day("2026-12-05"))

        assertEquals(0L, renewed.id)
        assertEquals(day("2026-12-05"), renewed.start)
        assertEquals(day("2027-01-03"), renewed.end)
    }

    @Test
    fun renewed_keepsConfiguration() {
        val renewed = PlanGraduations.renewed(plan, day("2026-12-05"))

        assertEquals(plan.title, renewed.title)
        assertEquals(plan.icon, renewed.icon)
        assertEquals(plan.colorHex, renewed.colorHex)
        assertEquals(plan.dailyGoalMinutes, renewed.dailyGoalMinutes)
    }

    @Test
    fun renewed_singleDayPlanStaysOneDay() {
        val oneDay = plan.copy(start = day("2026-11-10"), end = day("2026-11-10"))
        val renewed = PlanGraduations.renewed(oneDay, day("2026-12-05"))

        assertEquals(renewed.start, renewed.end)
    }

    @Test
    fun report_ignoresNegativeSeconds() {
        val report = PlanGraduations.report(plan, -500L)

        assertEquals(0, report.totalMinutes)
        assertEquals(0, report.completionPercent)
    }
}
