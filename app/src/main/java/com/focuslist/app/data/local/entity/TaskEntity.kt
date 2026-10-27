package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务实体：对齐滴答清单的任务模型，支持标签、软删除垃圾桶、含时间的截止时间。
 *
 * @property id 主键，自增；0 表示未插入
 * @property title 任务标题，必填，最长 200 字
 * @property note 任务备注详情，可为空字符串
 * @property categoryId 所属清单 ID，null 表示收集箱
 * @property tags 标签名集合，逗号分隔存储（如 "工作,紧急"），空串表示无标签
 * @property priority 优先级：0 无、1 低（蓝旗）、2 中（黄旗）、3 高（红旗）
 * @property dueDate 截止时间戳（毫秒）；配合 [hasTime] 决定是全天还是精确到时刻
 * @property hasTime 截止时间是否包含具体时刻：true 时分有效，false 仅日期（全天任务）
 * @property reminderAt 提醒触发时间戳（毫秒），null 表示不提醒
 * @property repeatRule 重复规则：NONE 不重复、DAILY 每天、WEEKLY 每周、MONTHLY 每月
 * @property estimatedPomos 预计需要的番茄钟个数，0 表示未预估
 * @property completedPomos 已累计完成的番茄钟个数
 * @property isDone 是否已完成
 * @property doneAt 完成时间戳（毫秒），未完成为 null
 * @property deletedAt 移入垃圾桶时间戳（毫秒），null 表示正常；非 null 时不在任何清单显示
 * @property sortOrder 手动排序权重，数值越小越靠前
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最近一次修改时间戳（毫秒）
 */
@Entity(
    tableName = "tasks",
    indices = [Index("dueDate"), Index("categoryId"), Index("isDone"), Index("deletedAt")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 解析标签列表。
     *
     * @return 标签名列表，无标签时为空列表
     */
    fun tagList(): List<String> {
        return tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/**
 * 子任务实体：任务下挂的拆分步骤（滴答清单称"检查项"）。
 *
 * @property id 主键，自增
 * @property taskId 所属任务 ID
 * @property title 子任务标题
 * @property isDone 是否已完成
 * @property sortOrder 排序权重，数值越小越靠前
 */
@Entity(
    tableName = "sub_tasks",
    indices = [Index("taskId")]
)
data class SubTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0
)