package com.focuslist.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份编解码的单元测试：验证导出—导入的往返一致性与异常输入的拒绝策略。
 */
class BackupCodecTest {

    /** 构造一份覆盖各类数据的备份样本 */
    private fun sample(): BackupPayload = BackupPayload(
        schemaVersion = BACKUP_SCHEMA_VERSION,
        appVersion = "1.0.53",
        exportedAt = 1_763_000_000_000L,
        plans = listOf(
            BackupPlan(
                id = 7, title = "学习英语", icon = "📚", colorHex = "#4772FA",
                dailyGoalMinutes = 30, startDate = 1_762_000_000_000L, endDate = 1_763_000_000_000L,
                note = "每天半小时", archived = false, sortOrder = 1, createdAt = 1_761_000_000_000L
            )
        ),
        planLogs = listOf(
            BackupPlanLog(id = 11, planId = 7, date = 1_762_500_000_000L, accumulatedSec = 1800, updatedAt = 1_762_508_000_000L)
        ),
        notes = listOf(
            BackupNote(id = 3, title = "周会纪要", content = "# 要点\n- 备份功能", date = 1_762_500_000_000L)
        ),
        noteAttachments = listOf(BackupAttachment(id = 5, noteId = 3, type = "IMAGE", uri = "content://media/1")),
        habits = listOf(BackupHabit(id = 2, name = "喝水", colorHex = "#38B6FF", icon = "💧")),
        habitChecks = listOf(BackupHabitCheck(id = 9, habitId = 2, date = 1_762_500_000_000L)),
        tasks = listOf(BackupTask(id = 1, title = "写周报", priority = 3, dueDate = 1_762_600_000_000L, hasTime = true)),
        subTasks = listOf(BackupSubTask(id = 4, taskId = 1, title = "整理数据", isDone = true, sortOrder = 2)),
        focusRecords = listOf(
            BackupFocusRecord(id = 6, taskId = 1, startAt = 1_762_600_000_000L, endAt = 1_762_601_500_000L,
                durationSec = 1500, phase = "WORK", completed = true)
        ),
        categories = listOf(BackupCategory(id = 1, name = "收集箱", colorHex = "#E94B3C", isDefault = true)),
        tags = listOf(BackupTag(id = 1, name = "紧急", colorHex = "#FF5A5F"))
    )

    @Test
    fun encodeThenDecode_keepsEveryField() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        val original = sample()
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.appVersion, restored.appVersion)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.plans, restored.plans)
        assertEquals(original.planLogs, restored.planLogs)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.noteAttachments, restored.noteAttachments)
        assertEquals(original.habits, restored.habits)
        assertEquals(original.habitChecks, restored.habitChecks)
        assertEquals(original.tasks, restored.tasks)
        assertEquals(original.subTasks, restored.subTasks)
        assertEquals(original.focusRecords, restored.focusRecords)
        assertEquals(original.categories, restored.categories)
        assertEquals(original.tags, restored.tags)
    }

    @Test
    fun encode_isReadableIndentedJson() {
        val json = BackupCodec.encode(sample())
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.trimStart().startsWith("{"))
        // 中文不应被转义成 \uXXXX，便于用户直接查看
        assertTrue(json.contains("学习英语"))
    }

    @Test
    fun decode_keepsNullableFieldsNull() {
        val json = BackupCodec.encode(
            BackupPayload(tasks = listOf(BackupTask(id = 1, title = "无截止时间的任务")))
        )
        val restored = BackupCodec.decode(json)
        assertEquals(1, restored.tasks.size)
        assertEquals(null, restored.tasks[0].dueDate)
        assertEquals(null, restored.tasks[0].deletedAt)
    }

    @Test
    fun decode_rejectsInvalidJson() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("这不是 JSON")
        }
    }

    @Test
    fun decode_rejectsUnknownSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("{\"schemaVersion\": 0}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("{\"schemaVersion\": 999}")
        }
    }

    @Test
    fun decode_acceptsOlderSchemaVersion() {
        val payload = BackupCodec.decode("{\"schemaVersion\": 1, \"plans\": []}")
        assertEquals(1, payload.schemaVersion)
        assertTrue(payload.plans.isEmpty())
    }
}
