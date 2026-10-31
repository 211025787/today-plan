package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签数据访问对象。
 */
@Dao
interface TagDao {

    /**
     * 观察全部标签，按创建时间升序。
     *
     * @return 标签流
     */
    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    /**
     * 插入标签（重名忽略，保证幂等）。
     *
     * @param tag 标签实体
     * @return 新标签 ID；重名时为 -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    /**
     * 更新标签。
     *
     * @param tag 标签实体
     */
    @Update
    suspend fun update(tag: TagEntity)

    /**
     * 删除标签。
     *
     * @param id 标签 ID
     */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---- 备份 / 恢复：全量读写 ----

    /** 同步读取全部标签，用于导出备份。 */
    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun getAll(): List<TagEntity>

    /** 批量写入标签（同 ID 覆盖），用于导入备份。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    /** 清空标签表（覆盖式导入前调用）。 */
    @Query("DELETE FROM tags")
    suspend fun clearAll()
}