package com.focuslist.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就解锁记录与备份结构互转的单元测试。
 *
 * 覆盖三类风险：脏数据（空 id / 时间为 0）、旧备份（没有成就字段）、
 * 以及「覆盖 / 合并」两种导入方式下解锁日期会不会被改坏。
 */
class BackupAchievementsTest {

    private val day = 86_400_000L

    @Test
    fun snapshot_dropsInvalidEntries() {
        val list = BackupAchievements.snapshot(
            mapOf("first_focus" to day, "" to day * 2, "hours_10" to 0L, "pomo_10" to -5L)
        )
        assertEquals(1, list.size)
        assertEquals("first_focus", list[0].id)
    }

    @Test
    fun snapshot_sortsByUnlockedAtDescending() {
        val list = BackupAchievements.snapshot(
            mapOf("a" to day, "b" to day * 3, "c" to day * 2)
        )
        assertEquals(listOf("b", "c", "a"), list.map { it.id })
    }

    @Test
    fun snapshot_sameTimeSortedByIdAscending() {
        val list = BackupAchievements.snapshot(mapOf("c" to day, "a" to day, "b" to day))
        assertEquals(listOf("a", "b", "c"), list.map { it.id })
    }

    @Test
    fun snapshot_isIndependentOfInputOrder() {
        val first = BackupAchievements.snapshot(mapOf("a" to day, "b" to day * 2))
        val second = BackupAchievements.snapshot(mapOf("b" to day * 2, "a" to day))
        assertEquals(first, second)
    }

    @Test
    fun snapshot_emptyRecordGivesEmptyList() {
        assertTrue(BackupAchievements.snapshot(emptyMap()).isEmpty())
    }

    @Test
    fun toRecord_dropsInvalidEntries() {
        val record = BackupAchievements.toRecord(
            listOf(
                BackupAchievement("first_focus", day),
                BackupAchievement("", day),
                BackupAchievement("hours_10", 0L)
            )
        )
        assertEquals(mapOf("first_focus" to day), record)
    }

    @Test
    fun toRecord_duplicateIdKeepsEarliestTime() {
        val record = BackupAchievements.toRecord(
            listOf(
                BackupAchievement("streak_3", day * 5),
                BackupAchievement("streak_3", day * 2)
            )
        )
        assertEquals(1, record.size)
        assertEquals(day * 2, record["streak_3"])
    }

    @Test
    fun toRecord_keepsUnknownIds() {
        val record = BackupAchievements.toRecord(listOf(BackupAchievement("future_badge", day)))
        assertEquals(day, record["future_badge"])
    }

    @Test
    fun snapshotThenToRecord_roundTripsTheSameRecord() {
        val original = mapOf("first_focus" to day, "hours_10" to day * 7, "century" to day * 30)
        assertEquals(original, BackupAchievements.toRecord(BackupAchievements.snapshot(original)))
    }

    @Test
    fun restore_overwrite_replacesLocalRecord() {
        val local = mapOf("first_focus" to day, "hours_10" to day)
        val incoming = mapOf("century" to day * 9)
        assertEquals(incoming, BackupAchievements.restore(local, incoming, overwrite = true))
    }

    @Test
    fun restore_merge_keepsLocalAndAddsMissing() {
        val local = mapOf("first_focus" to day)
        val incoming = mapOf("first_focus" to day * 3, "century" to day * 9)
        val merged = BackupAchievements.restore(local, incoming, overwrite = false)
        assertEquals(setOf("first_focus", "century"), merged.keys)
        // 本机已有的解锁日期优先，不被备份里的更晚时间覆盖
        assertEquals(day, merged["first_focus"])
        assertEquals(day * 9, merged["century"])
    }

    @Test
    fun restore_emptyIncoming_keepsLocalEvenWhenOverwriting() {
        val local = mapOf("first_focus" to day)
        assertEquals(local, BackupAchievements.restore(local, emptyMap(), overwrite = true))
        assertEquals(local, BackupAchievements.restore(local, emptyMap(), overwrite = false))
    }

    @Test
    fun restore_intoEmptyLocal_writesIncoming() {
        val incoming = mapOf("pomo_10" to day)
        assertEquals(incoming, BackupAchievements.restore(emptyMap(), incoming, overwrite = false))
    }

    @Test
    fun addedCount_countsOnlyMissingIds() {
        val local = mapOf("first_focus" to day, "hours_10" to day)
        val incoming = mapOf("first_focus" to 1L, "hours_10" to 2L, "century" to 3L)
        assertEquals(1, BackupAchievements.addedCount(local, incoming))
    }

    @Test
    fun addedCount_zeroWhenEverythingKnown() {
        val record = mapOf("first_focus" to day)
        assertEquals(0, BackupAchievements.addedCount(record, record))
        assertEquals(0, BackupAchievements.addedCount(record, emptyMap()))
    }

    @Test
    fun isValid_boundaries() {
        assertTrue(BackupAchievements.isValid("first_focus", 1L))
        assertFalse(BackupAchievements.isValid("", 1L))
        assertFalse(BackupAchievements.isValid("   ", 1L))
        assertFalse(BackupAchievements.isValid("first_focus", 0L))
        assertFalse(BackupAchievements.isValid("first_focus", -1L))
    }

    @Test
    fun countText_emptyAndNormal() {
        assertEquals("暂无成就", BackupAchievements.countText(emptyMap()))
        assertEquals("暂无成就", BackupAchievements.countText(mapOf("a" to 0L)))
        assertEquals("成就 2 枚", BackupAchievements.countText(mapOf("a" to day, "b" to 0L, "c" to day)))
    }

    @Test
    fun payload_roundTripsAchievementsThroughJson() {
        val payload = BackupPayload(
            achievements = BackupAchievements.snapshot(mapOf("first_focus" to day, "century" to day * 9))
        )
        val restored = BackupCodec.decode(BackupCodec.encode(payload))
        assertEquals(payload.achievements, restored.achievements)
        assertEquals(mapOf("century" to day * 9, "first_focus" to day), BackupAchievements.toRecord(restored.achievements))
    }

    @Test
    fun payload_countsAndDescribesAchievements() {
        val payload = BackupPayload(achievements = List(3) { BackupAchievement("ach$it", day) })
        assertTrue(payload.describe().contains("成就 3"))
        assertTrue(payload.hasAchievements())
        assertEquals(3, payload.totalCount())
    }
}
