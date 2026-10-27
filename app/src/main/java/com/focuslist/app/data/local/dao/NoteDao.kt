package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focuslist.app.data.local.entity.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * 快速备注数据访问对象。
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
}