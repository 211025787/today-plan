package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 快速备注实体：右下圆点菜单"备注"产生的随手记，可关联到某一天并支持 Markdown 与附件。
 *
 * @property id 主键，自增；0 表示未插入
 * @property title 备注标题，可为空串
 * @property content 备注正文（Markdown），最长 2000 字
 * @property date 关联日期（当天 0 点时间戳）；0 表示未关联具体日期
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最近修改时间戳（毫秒）
 */
@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String,
    val date: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 备注附件：关联的本地图片 / 视频（content URI）。
 *
 * @property id 主键，自增
 * @property noteId 所属备注 ID
 * @property type 附件类型：IMAGE / VIDEO
 * @property uri 附件 URI（content:// 本地媒体）
 * @property createdAt 创建时间戳（毫秒）
 */
@Entity(
    tableName = "note_attachments",
    indices = [Index("noteId")]
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val type: String,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis()
)