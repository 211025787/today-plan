package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 快速备注数据访问对象：备注与其附件的读写。
 */
@Dao
interface NoteDao {

    /**
     * 观察全部备注，按创建时间倒序。
     *
     * @return 备注流
     */
    @Query("SELECT * FROM quick_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuickNoteEntity>>

    /**
     * 观察某天的备注，按创建时间倒序。
     *
     * @param date 当天 0 点时间戳
     * @return 当日备注流
     */
    @Query("SELECT * FROM quick_notes WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: Long): Flow<List<QuickNoteEntity>>

    /**
     * 插入备注。
     *
     * @param note 备注实体
     * @return 新备注 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: QuickNoteEntity): Long

    /**
     * 删除备注。
     *
     * @param id 备注 ID
     */
    @Query("DELETE FROM quick_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 观察某条备注的附件。
     *
     * @param noteId 备注 ID
     * @return 附件流
     */
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY id ASC")
    fun observeAttachmentsOf(noteId: Long): Flow<List<NoteAttachmentEntity>>

    /**
     * 插入附件。
     *
     * @param attachment 附件实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: NoteAttachmentEntity)

    /**
     * 删除备注的全部附件。
     *
     * @param noteId 备注 ID
     */
    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun deleteAttachmentsOf(noteId: Long)
}