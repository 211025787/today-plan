package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 快速备注实体：右下圆点菜单"备注"产生的随手记。
 *
 * @property id 主键，自增；0 表示未插入
 * @property content 备注正文，最长 2000 字
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最近修改时间戳（毫秒）
 */
@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)