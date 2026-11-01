package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 每周挑战 [WeeklyChallenges] 的单元测试。
 *
 * 固定周：2026-10-26（周一）~ 2026-11-01（周日）。
 */
class WeeklyChallengeTest {

    /** 日期串 → 本地 0 点时间戳 */
    private fun day(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val monday = day("2026-10-26")
    private val sunday = day("2026-11-01")

    /** 取某周的挑战 */
    private fun week(start: Long) = WeeklyChallenges.ofWeek(start)

    @Test
    fun weekStart_isMonday() {
        assertEquals(monday, WeeklyChallenges.weekStart(day("2026-10-29")))
        assertEquals(monday, WeeklyChallenges.weekStart(sunday))
    }

    @Test
    fun weekKey_isIsoDate() {
        assertEquals("2026-10-26", WeeklyChallenges.weekKey(monday))
    }

    @Test
    fun ofWeek_hasThreeKindsInOrder() {
        val list = week(monday)

        assertEquals(3, list.size)
        assertEquals(
            listOf(ChallengeKind.ACTIVE_DAYS, ChallengeKind.TOTAL_MINUTES, ChallengeKind.BEST_DAY),
            list.map { it.kind }
        )
        assertEquals(3, list.map { it.id }.toSet().size)
    }

    @Test
    fun ofWeek_idsCarryWeekKey() {
        val key = WeeklyChallenges.weekKey(monday)

        week(monday).forEach { assertTrue(it.id.contains(key)) }
    }

    @Test
    fun ofWeek_targetsRotateAcrossWeeks() {
        val targets = (0..2).map { offset ->
            week(DateUtils.addDays(monday, offset * 7))
                .first { it.kind == ChallengeKind.ACTIVE_DAYS }.target
        }

        assertEquals(setOf(4, 5, 3), targets.toSet())
        assertEquals(3, targets.toSet().size)
    }

    @Test
    fun ofWeek_cycleRepeatsEveryThreeWeeks() {
        val first = week(monday).map { it.target }
        val fourth = week(DateUtils.addDays(monday, 21)).map { it.target }

        assertEquals(first, fourth)
    }

    @Test
    fun ofWeek_titlesMatchTargets() {
        week(monday).forEach { challenge ->
            assertTrue(challenge.title.isNotBlank())
            assertTrue(challenge.emoji.isNotBlank())
            assertTrue(challenge.target > 0)
        }
        val total = week(monday).first { it.kind == ChallengeKind.TOTAL_MINUTES }
        assertTrue(total.title.contains("小时"))
        val best = week(monday).first { it.kind == ChallengeKind.BEST_DAY }
        assertTrue(best.title.contains("分钟"))
    }

    @Test
    fun progress_countsActiveDays() {
        val list = WeeklyChallenges.progress(monday, listOf(30, 0, 10, 0, 0, 5, 0))
        val active = list.first { it.challenge.kind == ChallengeKind.ACTIVE_DAYS }

        assertEquals(3, active.current)
    }

    @Test
    fun progress_sumsTotalMinutes() {
        val list = WeeklyChallenges.progress(monday, listOf(30, 20, 10, 0, 0, 0, 0))
        val total = list.first { it.challenge.kind == ChallengeKind.TOTAL_MINUTES }

        assertEquals(60, total.current)
    }

    @Test
    fun progress_takesBestDay() {
        val list = WeeklyChallenges.progress(monday, listOf(10, 50, 20, 0, 0, 0, 0))
        val best = list.first { it.challenge.kind == ChallengeKind.BEST_DAY }

        assertEquals(50, best.current)
    }

    @Test
    fun progress_padsShortList() {
        val list = WeeklyChallenges.progress(monday, listOf(30))

        assertEquals(30, list.first { it.challenge.kind == ChallengeKind.TOTAL_MINUTES }.current)
        assertEquals(1, list.first { it.challenge.kind == ChallengeKind.ACTIVE_DAYS }.current)
    }

    @Test
    fun progress_treatsNegativeAsZero() {
        val list = WeeklyChallenges.progress(monday, listOf(-30, -10))

        assertEquals(0, list.first { it.challenge.kind == ChallengeKind.TOTAL_MINUTES }.current)
    }

    @Test
    fun progress_doneAtExactTarget() {
        val challenge = week(monday).first { it.kind == ChallengeKind.BEST_DAY }
        val progress = ChallengeProgress(challenge, challenge.target)

        assertTrue(progress.done)
        assertEquals(100, progress.percent)
        assertEquals("已达成", progress.remainingText)
    }

    @Test
    fun progress_belowTargetIsNotDone() {
        val challenge = week(monday).first { it.kind == ChallengeKind.BEST_DAY }
        val progress = ChallengeProgress(challenge, challenge.target - 1)

        assertFalse(progress.done)
        assertEquals("还差 1 分钟", progress.remainingText)
    }

    @Test
    fun progress_percentRoundsAndClamps() {
        // 用固定目标值，避免受每周轮换影响
        val challenge = WeeklyChallenge("x", ChallengeKind.ACTIVE_DAYS, "📅", "本周专注 5 天", 5, "天")

        assertEquals(60, ChallengeProgress(challenge, 3).percent)
        assertEquals(100, ChallengeProgress(challenge, 9).percent)
        assertEquals(0, ChallengeProgress(challenge, 0).percent)
    }

    @Test
    fun progress_remainingUsesUnit() {
        val days = week(monday).first { it.kind == ChallengeKind.ACTIVE_DAYS }
        val text = ChallengeProgress(days, 1).remainingText

        assertTrue(text.endsWith("天"))
    }

    @Test
    fun progressText_showsCurrentAndTarget() {
        val challenge = week(monday).first { it.kind == ChallengeKind.ACTIVE_DAYS }

        assertEquals("1 / ${challenge.target} 天", ChallengeProgress(challenge, 1).progressText)
    }

    @Test
    fun daysLeft_countsInclusiveOfToday() {
        assertEquals(7, WeeklyChallenges.daysLeft(monday, monday))
        // 周三：还有周三到周日共 5 天
        assertEquals(5, WeeklyChallenges.daysLeft(day("2026-10-28"), monday))
        assertEquals(1, WeeklyChallenges.daysLeft(sunday, monday))
    }

    @Test
    fun daysLeft_clampsOutsideWeek() {
        assertEquals(7, WeeklyChallenges.daysLeft(day("2026-10-19"), monday))
        assertEquals(1, WeeklyChallenges.daysLeft(day("2026-11-09"), monday))
    }

    @Test
    fun summary_countsCompleted() {
        val list = WeeklyChallenges.progress(monday, listOf(200, 0, 0, 0, 0, 0, 0))
        val text = WeeklyChallenges.summary(list)

        assertTrue(text.contains("/3"))
    }

    @Test
    fun summary_allDone() {
        // 七天全勤、且单日远超目标：无论当周轮换到哪一档都已完成
        val list = WeeklyChallenges.progress(monday, List(7) { 200 })

        assertEquals("本周挑战全部完成，漂亮", WeeklyChallenges.summary(list))
    }

    @Test
    fun summary_emptyList() {
        assertEquals("", WeeklyChallenges.summary(emptyList()))
    }

    @Test
    fun completedIds_onlyForDoneChallenges() {
        val list = WeeklyChallenges.progress(monday, listOf(400, 100, 0, 0, 0, 0, 0))
        val ids = WeeklyChallenges.completedIds(list)

        assertEquals(list.count { it.done }, ids.size)
        assertTrue(ids.all { it.startsWith("challenge_2026-10-26_") })
    }

    @Test
    fun differentWeeksNeverShareIds() {
        val a = week(monday).map { it.id }
        val b = week(DateUtils.addDays(monday, 7)).map { it.id }

        assertNotEquals(a, b)
        assertTrue((a.toSet() intersect b.toSet()).isEmpty())
    }
}
