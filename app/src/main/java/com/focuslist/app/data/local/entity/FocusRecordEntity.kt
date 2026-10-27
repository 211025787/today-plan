package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 专注记录实体：每一次番茄钟（或休息）都会落库一条，用于统计报表。
 *
 * @property id 主键，自增
 * @property taskId 关联的任务 ID，自由专注（未选任务）时为 null
 * @property startAt 开始时间戳（毫秒）
 * @property endAt 结束时间戳（毫秒）；被中断时等于中断时刻
 * @property durationSec 实际持续秒数
 * @property phase 阶段类型：WORK 专注、SHORT_BREAK 短休息、LONG_BREAK 长休息
 * @property completed 是否完整跑完整个倒计时（false 表示中途放弃）
 */
@Entity(
    tableName = "focus_records",
    indices = [Index("startAt"), Index("taskId")]
)
data class FocusRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val startAt: Long,
    val endAt: Long,
    val durationSec: Int,
    val phase: String,
    val completed: Boolean
)