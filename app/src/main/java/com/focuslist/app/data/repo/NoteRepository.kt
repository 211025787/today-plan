package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.NoteDao
import com.focuslist.app.data.local.entity.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 快速备注仓库。
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
     * 新增备注。
     *
     * @param content 正文
     */
    suspend fun add(content: String) {
        if (content.isNotBlank()) {
            noteDao.insert(QuickNoteEntity(content = content.trim()))
        }
    }

    /**
     * 删除备注。
     *
     * @param id 备注 ID
     */
    suspend fun delete(id: Long) {
        noteDao.deleteById(id)
    }
}