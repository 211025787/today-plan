package com.focuslist.app.data.backup

import androidx.room.withTransaction
import com.focuslist.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备份管理器：把本机全部数据导出成一份 JSON，或从 JSON 恢复回来。
 *
 * 所有读写都在单个数据库事务内完成，保证导出的是一致快照、
 * 导入过程要么整体成功要么整体回滚（不会留下半份数据）。
 *
 * @property database 应用数据库
 */
class BackupManager(private val database: AppDatabase) {

    /**
     * 收集当前数据库中的全部数据。
     *
     * @param appVersion 应用版本名，仅记录在备份文件里
     * @return 备份内容
     */
    suspend fun collect(appVersion: String = ""): BackupPayload = withContext(Dispatchers.IO) {
        database.withTransaction {
            val taskDao = database.taskDao()
            val habitDao = database.habitDao()
            val planDao = database.planDao()
            val noteDao = database.noteDao()
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
                        note = it.note, archived = it.archived, sortOrder = it.sortOrder, createdAt = it.createdAt
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
                }
            )
        }
    }

    /**
     * 将备份内容写回数据库。
     *
     * @param payload 备份内容
     * @param overwrite true 为覆盖式（先清空全部表，结果等同于备份文件）；
     *                  false 为合并式（相同 ID 覆盖，备份中没有的本机数据保留）
     */
    suspend fun restore(payload: BackupPayload, overwrite: Boolean) = withContext(Dispatchers.IO) {
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
        }
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
    }
}
