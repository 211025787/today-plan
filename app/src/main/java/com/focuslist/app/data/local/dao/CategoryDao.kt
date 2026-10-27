package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 清单（分类）数据访问对象。
 */
@Dao
interface CategoryDao {

    /**
     * 观察全部清单，按排序权重升序。
     *
     * @return 清单流
     */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /**
     * 插入清单。
     *
     * @param category 清单实体
     * @return 新清单 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    /**
     * 更新清单。
     *
     * @param category 清单实体
     */
    @Update
    suspend fun update(category: CategoryEntity)

    /**
     * 删除清单（仅限非内置清单）；清单内任务回落到收集箱。
     *
     * @param id 清单 ID
     */
    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteById(id: Long)

    /**
     * 将某清单下的任务移回收集箱。
     *
     * @param categoryId 清单 ID
     */
    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun resetTasksOf(categoryId: Long)

    /**
     * 统计内置清单数量，用于首启初始化。
     *
     * @return 清单总数
     */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}