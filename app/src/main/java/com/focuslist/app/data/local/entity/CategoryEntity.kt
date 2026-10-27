package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 清单（分类）实体：任务的归组，例如"工作""学习""生活"。
 *
 * @property id 主键，自增
 * @property name 清单名称，最长 30 字
 * @property colorHex 清单标识色，格式 "#RRGGBB"
 * @property sortOrder 排序权重，数值越小越靠前
 * @property isDefault 是否为内置清单（内置清单不可删除，如"收集箱"）
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#E94B3C",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)