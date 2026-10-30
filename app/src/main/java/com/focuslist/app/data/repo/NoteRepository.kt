package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.NoteDao
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 快速备注仓库：备注与其附件的读写。
 *
 * @property noteDao 备注 DAO
 */
class NoteRepository(private val noteDao: NoteDao) {

    /**
     * 观察全部备注（倒序）。
     *
     * @return 备注流
     */
    fun observeAll(): Flow<List<QuickNoteEntity>> {
        return noteDao.observeAll()
    }

    /**
     * 观察某天的备注（倒序）。
     *
     * @param date 当天 0 点时间戳
     * @return 当日备注流
     */
    fun observeByDate(date: Long): Flow<List<QuickNoteEntity>> {
        return noteDao.observeByDate(date)
    }

    /**
     * 观察某条备注的附件。
     *
     * @param noteId 备注 ID
     * @return 附件流
     */
    fun observeAttachmentsOf(noteId: Long): Flow<List<NoteAttachmentEntity>> {
        return noteDao.observeAttachmentsOf(noteId)
    }

    /**
     * 新增备注（含标题、关联日期与附件）。
     *
     * @param title 标题，可为空串
     * @param content 正文（Markdown）
     * @param date 关联日期 0 点时间戳；0 表示未关联
     * @param attachments 附件列表（图片/视频 URI），类型见 [NoteAttachmentEntity.type]
     */
    suspend fun add(title: String, content: String, date: Long, attachments: List<NoteAttachmentEntity> = emptyList()) {
        val hasBody = title.isNotBlank() || content.isNotBlank() || attachments.isNotEmpty()
        if (!hasBody) {
            return
        }
        val noteId = noteDao.insert(
            QuickNoteEntity(
                title = title.trim(),
                content = content.trim(),
                date = date
            )
        )
        attachments.forEach { noteDao.insertAttachment(it.copy(noteId = noteId)) }
    }

    /**
     * 按 ID 查询备注。
     *
     * @param id 备注 ID
     * @return 备注实体；不存在时为 null
     */
    suspend fun getNote(id: Long): QuickNoteEntity? {
        return noteDao.getById(id)
    }

    /**
     * 更新备注内容与附件（附件整体替换：先清空再写入当前列表）。
     *
     * @param note 备注实体（需带 id）
     * @param attachments 新的附件列表
     */
    suspend fun update(note: QuickNoteEntity, attachments: List<NoteAttachmentEntity>) {
        noteDao.insert(note.copy(updatedAt = System.currentTimeMillis()))
        noteDao.deleteAttachmentsOf(note.id)
        attachments.forEach { noteDao.insertAttachment(it.copy(id = 0, noteId = note.id)) }
    }

    /**
     * 删除备注及其附件。
     *
     * @param id 备注 ID
     */
    suspend fun delete(id: Long) {
        noteDao.deleteAttachmentsOf(id)
        noteDao.deleteById(id)
    }
}