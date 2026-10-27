package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 习惯实体：每日打卡型目标，例如"背单词""喝水八杯"。
 *
 * @property id 主键，自增
 * @property name 习惯名称，最长 50 字
 * @property colorHex 展示颜色，格式 "#RRGGBB"
 * @property icon 图标标识符（预置 emoji 或图标名）
 * @property reminderAt 每日提醒时间戳（毫秒，仅时分有效），null 表示不提醒
 * @property archived 是否已归档（归档后不出现在打卡列表）
 * @property sortOrder 排序权重，数值越小越靠前
 * @property createdAt 创建时间戳（毫秒）
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#E94B3C",
    val icon: String = "✅",
    val reminderAt: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 习惯打卡记录实体：同一天同一习惯只保留一条（唯一索引保证幂等）。
 *
 * @property id 主键，自增
 * @property habitId 所属习惯 ID
 * @property date 打卡日期（当天 0 点的毫秒时间戳）
 * @property createdAt 打卡操作时间戳（毫秒）
 */
@Entity(
    tableName = "habit_checks",
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis()
)