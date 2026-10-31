package com.focuslist.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份内容统计与摘要的单元测试。
 */
class BackupPayloadTest {

    @Test
    fun totalCount_sumsAllCollections() {
        val payload = BackupPayload(
            plans = List(2) { BackupPlan(id = it.toLong()) },
            planLogs = List(30) { BackupPlanLog(id = it.toLong()) },
            notes = List(5) { BackupNote(id = it.toLong()) },
            noteAttachments = List(3) { BackupAttachment(id = it.toLong()) },
            habits = List(1) { BackupHabit(id = it.toLong()) },
            habitChecks = List(10) { BackupHabitCheck(id = it.toLong()) },
            tasks = List(4) { BackupTask(id = it.toLong()) },
            subTasks = List(6) { BackupSubTask(id = it.toLong()) },
            focusRecords = List(8) { BackupFocusRecord(id = it.toLong()) },
            categories = List(1) { BackupCategory(id = it.toLong()) },
            tags = List(2) { BackupTag(id = it.toLong()) }
        )
        assertEquals(2 + 30 + 5 + 3 + 1 + 10 + 4 + 6 + 8 + 1 + 2, payload.totalCount())
    }

    @Test
    fun describe_onlyListsNonEmptySections() {
        val payload = BackupPayload(
            plans = List(2) { BackupPlan(id = it.toLong()) },
            planLogs = List(30) { BackupPlanLog(id = it.toLong()) },
            notes = List(5) { BackupNote(id = it.toLong()) }
        )
        val text = payload.describe()
        assertTrue(text.contains("计划 2"))
        assertTrue(text.contains("投入记录 30"))
        assertTrue(text.contains("备注 5"))
        assertFalse(text.contains("习惯"))
        assertFalse(text.contains("任务"))
    }

    @Test
    fun describe_emptyPayload() {
        assertEquals("空备份", BackupPayload().describe())
        assertEquals(0, BackupPayload().totalCount())
    }

    @Test
    fun defaultPayload_carriesCurrentSchemaVersion() {
        assertEquals(BACKUP_SCHEMA_VERSION, BackupPayload().schemaVersion)
    }
}
