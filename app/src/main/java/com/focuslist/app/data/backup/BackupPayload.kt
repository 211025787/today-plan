package com.focuslist.app.data.backup

/** 备份文件结构版本；结构变更时递增，导入时据此判断是否兼容 */
const val BACKUP_SCHEMA_VERSION: Int = 1

/** 任务 */
data class BackupTask(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val tags: String = "",
    val priority: Int = 0,
    val dueDate: Long? = null,
    val hasTime: Boolean = false,
    val reminderAt: Long? = null,
    val repeatRule: String = "NONE",
    val estimatedPomos: Int = 0,
    val completedPomos: Int = 0,
    val isDone: Boolean = false,
    val doneAt: Long? = null,
    val deletedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/** 子任务 */
data class BackupSubTask(
    val id: Long = 0,
    val taskId: Long = 0,
    val title: String = "",
    val isDone: Boolean = false,
    val sortOrder: Int = 0
)

/** 专注记录 */
data class BackupFocusRecord(
    val id: Long = 0,
    val taskId: Long? = null,
    val startAt: Long = 0,
    val endAt: Long = 0,
    val durationSec: Int = 0,
    val phase: String = "WORK",
    val completed: Boolean = false
)

/** 习惯 */
data class BackupHabit(
    val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#E94B3C",
    val icon: String = "✅",
    val reminderAt: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0
)

/** 习惯打卡 */
data class BackupHabitCheck(
    val id: Long = 0,
    val habitId: Long = 0,
    val date: Long = 0,
    val createdAt: Long = 0
)

/** 清单 */
data class BackupCategory(
    val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#E94B3C",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)

/** 标签 */
data class BackupTag(
    val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#4772FA",
    val createdAt: Long = 0
)

/** 计划 */
data class BackupPlan(
    val id: Long = 0,
    val title: String = "",
    val icon: String = "🎯",
    val colorHex: String = "#4772FA",
    val dailyGoalMinutes: Int = 30,
    val startDate: Long = 0,
    val endDate: Long = 0,
    val note: String = "",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0
)

/** 计划当日投入 */
data class BackupPlanLog(
    val id: Long = 0,
    val planId: Long = 0,
    val date: Long = 0,
    val accumulatedSec: Long = 0,
    val updatedAt: Long = 0
)

/** 备注 */
data class BackupNote(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val date: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/** 备注附件 */
data class BackupAttachment(
    val id: Long = 0,
    val noteId: Long = 0,
    val type: String = "IMAGE",
    val uri: String = "",
    val createdAt: Long = 0
)

/**
 * 备份文件整体结构：一份 JSON 就是一个 [BackupPayload]。
 *
 * @property schemaVersion 结构版本，导入时校验
 * @property appVersion 导出时的应用版本名，仅作记录
 * @property exportedAt 导出时间戳（毫秒）
 */
data class BackupPayload(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val tasks: List<BackupTask> = emptyList(),
    val subTasks: List<BackupSubTask> = emptyList(),
    val focusRecords: List<BackupFocusRecord> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    val habitChecks: List<BackupHabitCheck> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    val plans: List<BackupPlan> = emptyList(),
    val planLogs: List<BackupPlanLog> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    val noteAttachments: List<BackupAttachment> = emptyList()
) {

    /** 备份中包含的记录总条数（用于导出结果提示）。 */
    fun totalCount(): Int = tasks.size + subTasks.size + focusRecords.size + habits.size +
        habitChecks.size + categories.size + tags.size + plans.size + planLogs.size +
        notes.size + noteAttachments.size

    /**
     * 生成人类可读的摘要，只列出非空的分类。
     *
     * @return 形如「计划 2 · 投入记录 30 · 备注 5」的文案
     */
    fun describe(): String {
        val parts = buildList {
            if (plans.isNotEmpty()) add("计划 ${plans.size}")
            if (planLogs.isNotEmpty()) add("投入记录 ${planLogs.size}")
            if (focusRecords.isNotEmpty()) add("番茄 ${focusRecords.size}")
            if (habits.isNotEmpty()) add("习惯 ${habits.size}")
            if (habitChecks.isNotEmpty()) add("打卡 ${habitChecks.size}")
            if (notes.isNotEmpty()) add("备注 ${notes.size}")
            if (noteAttachments.isNotEmpty()) add("附件 ${noteAttachments.size}")
            if (tasks.isNotEmpty()) add("任务 ${tasks.size}")
            if (subTasks.isNotEmpty()) add("子任务 ${subTasks.size}")
            if (categories.isNotEmpty()) add("清单 ${categories.size}")
            if (tags.isNotEmpty()) add("标签 ${tags.size}")
        }
        return parts.joinToString(" · ").ifEmpty { "空备份" }
    }
}
