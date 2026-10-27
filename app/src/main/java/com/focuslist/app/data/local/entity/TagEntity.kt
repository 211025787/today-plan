package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签实体：跨清单的任务标记，例如"紧急""阅读""电话"。
 *
 * @property id 主键，自增
 * @property name 标签名（唯一），最长 20 字
 * @property colorHex 标签颜色，格式 "#RRGGBB"
 * @property createdAt 创建时间戳（毫秒）
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#4772FA",
    val createdAt: Long = System.currentTimeMillis()
)