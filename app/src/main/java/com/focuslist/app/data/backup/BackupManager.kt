package com.focuslist.app.data.backup

import androidx.room.withTransaction
import com.focuslist.app.data.local.AppDatabase
import com.focuslist.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 导入结果，用于在界面上给出更具体的反馈。
 *
 * @property recordCount 备份里的记录总条数
 * @property achievementsAdded 合并式导入新增的徽章数（覆盖式导入恒为 0）
 * @property achievementsTotal 备份里携带的徽章数；0 表示旧备份没有成就字段
 */
data class RestoreResult(
    val recordCount: Int = 0,
    val achievementsAdded: Int = 0,
    val achievementsTotal: Int = 0
)

/**
 * 备份管理器：把本机全部数据导出成一份 JSON，或从 JSON 恢复回来。
 *
 * 导出内容包含 Room 业务数据，以及 DataStore 里的偏好设置、计划模板（schema 2 起）、
 * 成就解锁记录（schema 3 起）与旅游行程（schema 4 起）。
 *
 * 数据库读写都在单个事务内完成，保证导出的是一致快照、
 * 导入过程要么整体成功要么整体回滚（不会留下半份数据）。
 *
 * @property database 应用数据库
 * @property settingsStore 偏好设置存储；为空时只备份数据库部分（供测试或无上下文场景使用）
 */
class BackupManager(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore? = null
) {

    /**
     * 收集当前数据库中的全部数据。
     *
     * @param appVersion 应用版本名，仅记录在备份文件里
     * @return 备份内容
     */
    suspend fun collect(appVersion: String = ""): BackupPayload = withContext(Dispatchers.IO) {
        val settings = settingsStore?.settings?.first()?.toBackupSettings()
        val templates = settingsStore?.planTemplates?.first()?.map { it.toBackupTemplate() } ?: emptyList()
        val achievements = settingsStore?.achievements?.first()
            ?.let { BackupAchievements.snapshot(it) } ?: emptyList()
        database.withTransaction {
            val taskDao = database.taskDao()
            val habitDao = database.habitDao()
            val planDao = database.planDao()
            val noteDao = database.noteDao()
            val tripDao = database.tripDao()
            BackupPayload(
                schemaVersion = BACKUP_SCHEMA_VERSION,
                appVersion = appVersion,
                exportedAt = System.currentTimeMillis(),
                tasks = taskDao.getAllTasks().map {
                    BackupTask(
                        id = it.id, title = it.title, note = it.note, categoryId = it.categoryId,
                        tags = it.tags, priority = it.priority, dueDate = it.dueDate, hasTime = it.hasTime,
                        reminderAt = it.reminderAt, repeatRule = it.repeatRule,
                        estimatedPomos = it.estimatedPomos, completedPomos = it.completedPomos,
                        isDone = it.isDone, doneAt = it.doneAt, deletedAt = it.deletedAt,
                        sortOrder = it.sortOrder, createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                },
                subTasks = taskDao.getAllSubTasks().map {
                    BackupSubTask(id = it.id, taskId = it.taskId, title = it.title, isDone = it.isDone, sortOrder = it.sortOrder)
                },
                focusRecords = database.focusRecordDao().getAll().map {
                    BackupFocusRecord(
                        id = it.id, taskId = it.taskId, startAt = it.startAt, endAt = it.endAt,
                        durationSec = it.durationSec, phase = it.phase, completed = it.completed
                    )
                },
                habits = habitDao.getAllHabits().map {
                    BackupHabit(
                        id = it.id, name = it.name, colorHex = it.colorHex, icon = it.icon,
                        reminderAt = it.reminderAt, archived = it.archived, sortOrder = it.sortOrder,
                        createdAt = it.createdAt
                    )
                },
                habitChecks = habitDao.getAllChecks().map {
                    BackupHabitCheck(id = it.id, habitId = it.habitId, date = it.date, createdAt = it.createdAt)
                },
                categories = database.categoryDao().getAll().map {
                    BackupCategory(id = it.id, name = it.name, colorHex = it.colorHex, sortOrder = it.sortOrder, isDefault = it.isDefault)
                },
                tags = database.tagDao().getAll().map {
                    BackupTag(id = it.id, name = it.name, colorHex = it.colorHex, createdAt = it.createdAt)
                },
                plans = planDao.getAllPlans().map {
                    BackupPlan(
                        id = it.id, title = it.title, icon = it.icon, colorHex = it.colorHex,
                        dailyGoalMinutes = it.dailyGoalMinutes, startDate = it.startDate, endDate = it.endDate,
                        note = it.note, archived = it.archived, sortOrder = it.sortOrder,
                        cardSize = it.cardSize, cardCols = it.cardCols, cardRows = it.cardRows,
                        cardX = it.cardX, cardY = it.cardY, createdAt = it.createdAt
                    )
                },
                planLogs = planDao.getAllLogs().map {
                    BackupPlanLog(id = it.id, planId = it.planId, date = it.date, accumulatedSec = it.accumulatedSec, updatedAt = it.updatedAt)
                },
                notes = noteDao.getAllNotes().map {
                    BackupNote(id = it.id, title = it.title, content = it.content, date = it.date, createdAt = it.createdAt, updatedAt = it.updatedAt)
                },
                noteAttachments = noteDao.getAllAttachments().map {
                    BackupAttachment(id = it.id, noteId = it.noteId, type = it.type, uri = it.uri, createdAt = it.createdAt)
                },
                settings = settings,
                templates = templates,
                achievements = achievements,
                trips = tripDao.getAllTrips().map {
                    BackupTrip(
                        id = it.id, title = it.title, emoji = it.emoji, colorHex = it.colorHex,
                        note = it.note, startDate = it.startDate, endDate = it.endDate,
                        cardSize = it.cardSize, cardCols = it.cardCols, cardRows = it.cardRows,
                        cardX = it.cardX, cardY = it.cardY, cardZ = it.cardZ,
                        createdAt = it.createdAt, updatedAt = it.updatedAt
                    )
                },
                tripStops = tripDao.getAllTripStops().map {
                    BackupTripStop(
                        id = it.id, tripId = it.tripId, sortIndex = it.sortIndex, name = it.name,
                        region = it.region, lat = it.lat, lon = it.lon,
                        stayMinutes = it.stayMinutes, note = it.note
                    )
                },
                tripLegs = tripDao.getAllTripLegs().map {
                    BackupTripLeg(
                        id = it.id, tripId = it.tripId, sortIndex = it.sortIndex, mode = it.mode,
                        durationMinutes = it.durationMinutes, fromStation = it.fromStation,
                        toStation = it.toStation, serviceNo = it.serviceNo, lines = it.lines,
                        distanceMeters = it.distanceMeters, polyline = it.polyline
                    )
                }
            )
        }
    }

    /**
     * 将备份内容写回数据库。
     *
     * 设置、模板与成就的恢复策略与数据一致：
     * - 覆盖式：用备份中的设置整体替换本机设置，模板与成就记录也整体替换；
     * - 合并式：设置保持本机不变，模板只补充本机没有的（按指纹去重），成就只补未解锁的 id。
     *
     * @param payload 备份内容
     * @param overwrite true 为覆盖式（先清空全部表，结果等同于备份文件）；
     *                  false 为合并式（相同 ID 覆盖，备份中没有的本机数据保留）
     * @return 导入结果，含合并式导入补充的徽章数
     */
    suspend fun restore(payload: BackupPayload, overwrite: Boolean): RestoreResult =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                if (overwrite) clearAll()
                database.taskDao().apply {
                    insertTasks(payload.tasks.map { it.toEntity() })
                    insertSubTasks(payload.subTasks.map { it.toEntity() })
                }
                database.focusRecordDao().insertAll(payload.focusRecords.map { it.toEntity() })
                database.habitDao().apply {
                    insertHabits(payload.habits.map { it.toEntity() })
                    insertChecks(payload.habitChecks.map { it.toEntity() })
                }
                database.categoryDao().insertAll(payload.categories.map { it.toEntity() })
                database.tagDao().insertAll(payload.tags.map { it.toEntity() })
                database.planDao().apply {
                    insertPlans(payload.plans.map { it.toEntity() })
                    insertLogs(payload.planLogs.map { it.toEntity() })
                }
                database.noteDao().apply {
                    insertNotes(payload.notes.map { it.toEntity() })
                    insertAttachments(payload.noteAttachments.map { it.toEntity() })
                }
                database.tripDao().apply {
                    insertTrips(payload.trips.map { it.toEntity() })
                    insertTripStops(payload.tripStops.map { it.toEntity() })
                    insertTripLegs(payload.tripLegs.map { it.toEntity() })
                }
            }
            val store = settingsStore
            var addedAchievements = 0
            if (store != null) {
                val templates = payload.templates.map { it.toPlanTemplate() }
                val achievements = BackupAchievements.toRecord(payload.achievements)
                if (overwrite) {
                    payload.settings?.let { store.applyBackupSettings(it) }
                    store.replacePlanTemplates(templates)
                } else {
                    store.mergePlanTemplates(templates)
                    addedAchievements = BackupAchievements.addedCount(store.readAchievements(), achievements)
                }
                // 成就不随数据表清空：旧备份没有该字段时保留本机记录
                store.applyBackupAchievements(achievements, overwrite)
            }
            RestoreResult(
                recordCount = payload.totalCount(),
                achievementsAdded = addedAchievements,
                achievementsTotal = payload.achievements.size
            )
        }

    /** 清空全部业务表（覆盖式导入前调用）。 */
    private suspend fun clearAll() {
        database.taskDao().apply {
            clearSubTasks()
            clearTasks()
        }
        database.focusRecordDao().clearAll()
        database.habitDao().apply {
            clearChecks()
            clearHabits()
        }
        database.categoryDao().clearAll()
        database.tagDao().clearAll()
        database.planDao().apply {
            clearLogs()
            clearPlans()
        }
        database.noteDao().apply {
            clearAttachments()
            clearNotes()
        }
        database.tripDao().apply {
            clearTripLegs()
            clearTripStops()
            clearTrips()
        }
    }
}
