package com.focuslist.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                note = "每天半小时", archived = false, sortOrder = 1,
                cardCols = 2, cardRows = 1, cardX = 0, cardY = 0, createdAt = 1_761_000_000_000L
            )
        ),
        trips = listOf(
            BackupTrip(
                id = 2, title = "西安三日", emoji = "🗺", colorHex = "#4772FA",
                startDate = 1_762_000_000_000L, endDate = 1_762_200_000_000L,
                cardCols = 2, cardRows = 2, cardX = 2, cardY = 0, cardZ = 1_763_000_000_123L
            )
        ),
        tripStops = listOf(
            BackupTripStop(
                id = 21, tripId = 2, sortIndex = 0, name = "西安北", region = "陕西",
                lat = 34.37, lon = 108.93, stayMinutes = 60, note = "集合"
            )
        ),
        tripLegs = listOf(
            BackupTripLeg(
                id = 31, tripId = 2, sortIndex = 0, mode = "HSR",
                durationMinutes = 180, fromStation = "北京西", toStation = "西安北",
                serviceNo = "G87", lines = "", distanceMeters = 1_200_000L, polyline = ""
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
        tags = listOf(BackupTag(id = 1, name = "紧急", colorHex = "#FF5A5F")),
        settings = BackupSettings(focusMinutes = 40, themeColor = "#E94B3C", dailyGoalMinutes = 60, strictMode = true),
        templates = listOf(
            BackupTemplate(id = "学习英语#30#21#📚", title = "学习英语", icon = "📚", dailyMinutes = 30, durationDays = 21, useCount = 4)
        ),
        achievements = listOf(
            BackupAchievement(id = "first_focus", unlockedAt = 1_762_000_500_000L),
            BackupAchievement(id = "hours_10", unlockedAt = 1_762_900_000_000L)
        )
    )

    @Test
    fun encodeThenDecode_keepsEveryField() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        val original = sample()
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.appVersion, restored.appVersion)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.plans, restored.plans)
        assertEquals(original.trips, restored.trips)
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
        assertEquals(original.settings, restored.settings)
        assertEquals(original.templates, restored.templates)
        assertEquals(original.achievements, restored.achievements)
    }

    @Test
    fun encodeThenDecode_keepsSettingsSnapshot() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(40, restored.settings?.focusMinutes)
        assertEquals("#E94B3C", restored.settings?.themeColor)
        assertEquals(60, restored.settings?.dailyGoalMinutes)
        assertEquals(true, restored.settings?.strictMode)
    }

    @Test
    fun encodeThenDecode_keepsTemplates() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(1, restored.templates.size)
        assertEquals("学习英语#30#21#📚", restored.templates[0].id)
        assertEquals(21, restored.templates[0].durationDays)
        assertEquals(4, restored.templates[0].useCount)
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

    @Test
    fun decode_oldSchemaWithoutSettings_leavesThemEmpty() {
        val payload = BackupCodec.decode(
            "{\"schemaVersion\": 1, \"plans\": [{\"id\": 1, \"title\": \"旧备份\"}]}"
        )
        assertEquals(null, payload.settings)
        assertTrue(payload.templates.isEmpty())
        assertTrue(payload.achievements.isEmpty())
        assertFalse(payload.hasAchievements())
        assertFalse(payload.hasSettings())
        assertEquals(1, payload.totalCount())
    }

    @Test
    fun decode_schema2Backup_hasNoAchievements() {
        val payload = BackupCodec.decode(
            "{\"schemaVersion\": 2, \"settings\": {\"focusMinutes\": 30}, \"templates\": []}"
        )
        assertEquals(2, payload.schemaVersion)
        assertTrue(payload.achievements.isEmpty())
        assertEquals(30, payload.settings?.focusMinutes)
    }

    @Test
    fun encodeThenDecode_keepsAchievements() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(2, restored.achievements.size)
        assertEquals("first_focus", restored.achievements[0].id)
        assertEquals(1_762_000_500_000L, restored.achievements[0].unlockedAt)
        assertTrue(BackupCodec.encode(sample()).contains("\"achievements\""))
    }

    @Test
    fun encodeThenDecode_keepsTripGeometryAndRoute() {
        val restored = BackupCodec.decode(BackupCodec.encode(sample()))

        assertEquals(1, restored.trips.size)
        assertEquals("西安三日", restored.trips[0].title)
        assertEquals(2, restored.trips[0].cardX)
        assertEquals(2, restored.trips[0].cardRows)
        assertEquals(1_763_000_000_123L, restored.trips[0].cardZ)
        assertEquals(1, restored.tripStops.size)
        assertEquals("西安北", restored.tripStops[0].name)
        assertEquals(1, restored.tripLegs.size)
        assertEquals("G87", restored.tripLegs[0].serviceNo)
    }
}
