package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份体检的单元测试：距上次备份天数、健康分级、文案与提醒判断。
 *
 * 时间基准统一用「固定 epoch 的当天 0 点 + 偏移天数」，每次断言都带上当天中午时刻，
 * 避免运行时刻落在边界上导致偶发失败。
 */
class BackupHealthTest {

    /** 基准日 0 点 */
    private val day0 = DateUtils.startOfDay(1_763_000_000_000L)

    /** 基准日往后第 n 天的 0 点 */
    private fun day(n: Int): Long = DateUtils.addDays(day0, n)

    /** 第 n 天中午，用作「当前时刻」 */
    private fun noon(n: Int): Long = day(n) + 12 * 60 * 60 * 1000L

    @Test
    fun daysSince_neverBackedUp_isNull() {
        assertNull(BackupHealth.daysSince(0L, noon(10)))
        assertNull(BackupHealth.daysSince(-1L, noon(10)))
    }

    @Test
    fun daysSince_sameDay_isZero() {
        assertEquals(0, BackupHealth.daysSince(day(3), noon(3)))
        // 当天 0 点备份、当天 23:59 查看，仍算 0 天前
        assertEquals(0, BackupHealth.daysSince(day(3), day(3) + 23 * 3600_000L + 59 * 60_000L))
    }

    @Test
    fun daysSince_countsWholeDays() {
        assertEquals(1, BackupHealth.daysSince(day(3), noon(4)))
        assertEquals(5, BackupHealth.daysSince(day(3), noon(8)))
        assertEquals(29, BackupHealth.daysSince(day(0), noon(29)))
    }

    @Test
    fun daysSince_futureTimestamp_isClampedToZero() {
        assertEquals(0, BackupHealth.daysSince(day(9), noon(2)))
    }

    @Test
    fun level_never() {
        assertEquals(BackupHealth.Level.NEVER, BackupHealth.level(0L, noon(5)))
    }

    @Test
    fun level_freshBeforeSoonDays() {
        assertEquals(BackupHealth.Level.FRESH, BackupHealth.level(day(0), noon(0)))
        assertEquals(BackupHealth.Level.FRESH, BackupHealth.level(day(0), noon(BackupHealth.SOON_DAYS - 1)))
    }

    @Test
    fun level_soonBetweenSoonAndOverdue() {
        assertEquals(BackupHealth.Level.SOON, BackupHealth.level(day(0), noon(BackupHealth.SOON_DAYS)))
        assertEquals(BackupHealth.Level.SOON, BackupHealth.level(day(0), noon(BackupHealth.OVERDUE_DAYS - 1)))
    }

    @Test
    fun level_overdueFromThreshold() {
        assertEquals(BackupHealth.Level.OVERDUE, BackupHealth.level(day(0), noon(BackupHealth.OVERDUE_DAYS)))
        assertEquals(BackupHealth.Level.OVERDUE, BackupHealth.level(day(0), noon(200)))
    }

    @Test
    fun statusText_never() {
        assertEquals("还没备份过", BackupHealth.statusText(0L, noon(1)))
    }

    @Test
    fun statusText_todayAndYesterday() {
        assertEquals("今天已备份", BackupHealth.statusText(day(2), noon(2)))
        assertEquals("上次备份在昨天", BackupHealth.statusText(day(2), noon(3)))
    }

    @Test
    fun statusText_manyDays() {
        assertEquals("上次备份在 7 天前", BackupHealth.statusText(day(1), noon(8)))
    }

    @Test
    fun adviceText_neverSuggestsExport() {
        val text = BackupHealth.adviceText(0L, noon(1))
        assertTrue(text.contains("备份"))
    }

    @Test
    fun adviceText_overdueMentionsThreshold() {
        assertTrue(BackupHealth.adviceText(day(0), noon(45)).contains("${BackupHealth.OVERDUE_DAYS} 天"))
    }

    @Test
    fun adviceText_freshIsReassuring() {
        assertTrue(BackupHealth.adviceText(day(0), noon(2)).contains("良好"))
    }

    @Test
    fun shouldRemind_onlyFalseWhenFresh() {
        assertTrue(BackupHealth.shouldRemind(0L, noon(1)))
        assertTrue(BackupHealth.shouldRemind(day(0), noon(BackupHealth.SOON_DAYS)))
        assertTrue(BackupHealth.shouldRemind(day(0), noon(BackupHealth.OVERDUE_DAYS)))
        assertFalse(BackupHealth.shouldRemind(day(0), noon(BackupHealth.SOON_DAYS - 1)))
    }

    @Test
    fun progress_neverIsFull() {
        assertEquals(1f, BackupHealth.progress(0L, noon(1)), 0.0001f)
    }

    @Test
    fun progress_scalesToThreshold() {
        assertEquals(0f, BackupHealth.progress(day(0), noon(0)), 0.0001f)
        assertEquals(0.5f, BackupHealth.progress(day(0), noon(BackupHealth.OVERDUE_DAYS / 2)), 0.0001f)
        assertEquals(1f, BackupHealth.progress(day(0), noon(BackupHealth.OVERDUE_DAYS)), 0.0001f)
    }

    @Test
    fun progress_clampsAboveThreshold() {
        assertEquals(1f, BackupHealth.progress(day(0), noon(90)), 0.0001f)
    }

    @Test
    fun countdownText_never() {
        assertEquals("尚未备份", BackupHealth.countdownText(0L, noon(1)))
    }

    @Test
    fun countdownText_beforeThreshold() {
        assertEquals("0 / ${BackupHealth.OVERDUE_DAYS} 天", BackupHealth.countdownText(day(0), noon(0)))
        assertEquals("12 / ${BackupHealth.OVERDUE_DAYS} 天", BackupHealth.countdownText(day(0), noon(12)))
    }

    @Test
    fun countdownText_afterThreshold() {
        assertEquals("已逾期 0 天", BackupHealth.countdownText(day(0), noon(BackupHealth.OVERDUE_DAYS)))
        assertEquals("已逾期 5 天", BackupHealth.countdownText(day(0), noon(BackupHealth.OVERDUE_DAYS + 5)))
    }

    @Test
    fun levels_haveReadableLabels() {
        assertEquals("从未备份", BackupHealth.Level.NEVER.label)
        assertEquals("状态良好", BackupHealth.Level.FRESH.label)
        assertEquals("临近提醒", BackupHealth.Level.SOON.label)
        assertEquals("已逾期", BackupHealth.Level.OVERDUE.label)
    }

    @Test
    fun constants_soonIsBeforeOverdue() {
        assertTrue(BackupHealth.SOON_DAYS < BackupHealth.OVERDUE_DAYS)
        assertEquals(30, BackupHealth.OVERDUE_DAYS)
        assertEquals(14, BackupHealth.SOON_DAYS)
    }
}
