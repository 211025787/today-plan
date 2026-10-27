package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划实体：一段时间范围内每天都要执行的目标，例如"健身""学习 Java 技术"。
 *
 * @property id 主键，自增；0 表示未插入
 * @property title 计划名称，最长 50 字
 * @property icon 图标（emoji），卡片左上角展示
 * @property colorHex 计划主题色，格式 "#RRGGBB"，用于卡片色条与进度条
 * @property dailyGoalMinutes 每日目标投入时长（分钟），取值 5-480
 * @property startDate 生效起始日期（当天 0 点时间戳，含）
 * @property endDate 生效截止日期（当天 0 点时间戳，含）
 * @property note 计划备注说明，可为空串
 * @property archived 是否归档（归档后不再出现在首页）
 * @property sortOrder 排序权重，数值越小越靠前
 * @property createdAt 创建时间戳（毫秒）
 */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String = "🎯",
    val colorHex: String = "#4772FA",
    val dailyGoalMinutes: Int = 30,
    val startDate: Long,
    val endDate: Long,
    val note: String = "",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 计划当日投入记录：同一计划同一天仅一条，秒数字段随多次计时不断累加。
 *
 * @property id 主键，自增
 * @property planId 所属计划 ID
 * @property date 日期（当天 0 点时间戳）
 * @property accumulatedSec 当日累计投入秒数（多次开始累加，不重置）
 * @property updatedAt 最近一次更新时间戳（毫秒）
 */
@Entity(
    tableName = "plan_logs",
    indices = [Index(value = ["planId", "date"], unique = true)]
)
data class PlanLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val date: Long,
    val accumulatedSec: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)