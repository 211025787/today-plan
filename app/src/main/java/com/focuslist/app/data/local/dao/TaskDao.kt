package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.SubTaskEntity
import com.focuslist.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 任务数据访问对象：所有查询默认排除垃圾桶（deletedAt IS NULL），智能清单按滴答清单语义实现。
 */
@Dao
interface TaskDao {

    /**
     * 观察指定清单下未删除任务。
     *
     * @param categoryId 清单 ID；传 null 查询收集箱
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 " +
            "AND (:categoryId IS NULL AND categoryId IS NULL OR categoryId = :categoryId) " +
            "ORDER BY priority DESC, dueDate ASC, sortOrder ASC"
    )
    fun observeByCategory(categoryId: Long?): Flow<List<TaskEntity>>

    /**
     * 观察"今天"智能清单：未完成且截止不超过今天结束（含逾期）。
     *
     * @param todayEnd 今天 23:59:59.999 的毫秒时间戳
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 AND dueDate IS NOT NULL AND dueDate <= :todayEnd " +
            "ORDER BY dueDate ASC, priority DESC"
    )
    fun observeToday(todayEnd: Long): Flow<List<TaskEntity>>

    /**
     * 观察"明天"智能清单。
     *
     * @param tomorrowStart 明天 0 点时间戳
     * @param tomorrowEnd 明天结束时间戳
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 AND dueDate >= :tomorrowStart AND dueDate <= :tomorrowEnd " +
            "ORDER BY priority DESC"
    )
    fun observeTomorrow(tomorrowStart: Long, tomorrowEnd: Long): Flow<List<TaskEntity>>

    /**
     * 观察"最近 7 天"智能清单：7 天内到期或已逾期的未完成任务。
     *
     * @param end 7 天后的结束时间戳
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 AND dueDate IS NOT NULL AND dueDate <= :end " +
            "ORDER BY dueDate ASC, priority DESC"
    )
    fun observeNext7Days(end: Long): Flow<List<TaskEntity>>

    /**
     * 观察"全部"：所有未完成且未删除任务。
     *
     * @return 任务流
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 ORDER BY priority DESC, dueDate ASC, sortOrder ASC")
    fun observePending(): Flow<List<TaskEntity>>

    /**
     * 同步查询全部未完成未删除任务（小组件用）。
     *
     * @return 任务列表
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 ORDER BY priority DESC, dueDate ASC, sortOrder ASC")
    fun getPendingSync(): List<TaskEntity>

    /**
     * 观察已完成任务（未删除），按完成时间倒序。
     *
     * @return 已完成任务流
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 1 ORDER BY doneAt DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    /**
     * 观察垃圾桶：按删除时间倒序。
     *
     * @return 已删除任务流
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TaskEntity>>

    /**
     * 观察某标签下的未完成任务（逗号分隔字段做包裹匹配，防止子串误配）。
     *
     * @param tagLike 标签匹配模式，形如 "%,tag,%"
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 AND " +
            "(',' || tags || ',') LIKE :tagLike ORDER BY priority DESC, dueDate ASC"
    )
    fun observeByTag(tagLike: String): Flow<List<TaskEntity>>

    /**
     * 观察日历区间内的任务（截止日期落在区间内）。
     *
     * @param start 区间起点（含）
     * @param end 区间终点（含）
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND dueDate IS NOT NULL AND dueDate >= :start AND dueDate <= :end " +
            "ORDER BY dueDate ASC, priority DESC"
    )
    fun observeDueBetween(start: Long, end: Long): Flow<List<TaskEntity>>

    /**
     * 全文搜索未删除任务（标题/备注/标签）。
     *
     * @param keywordLike 关键字 LIKE 模式，形如 "%key%"
     * @return 任务流
     */
    @Query(
        "SELECT * FROM tasks WHERE deletedAt IS NULL AND " +
            "(title LIKE :keywordLike OR note LIKE :keywordLike OR tags LIKE :keywordLike) " +
            "ORDER BY isDone ASC, priority DESC"
    )
    fun search(keywordLike: String): Flow<List<TaskEntity>>

    /**
     * 根据 ID 查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体，不存在时为 null
     */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /**
     * 插入新任务。
     *
     * @param task 任务实体
     * @return 新任务自增 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /**
     * 更新任务。
     *
     * @param task 任务实体
     */
    @Update
    suspend fun update(task: TaskEntity)

    /**
     * 物理删除任务。
     *
     * @param id 任务 ID
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 切换任务完成状态。
     *
     * @param id 任务 ID
     * @param done 是否完成
     * @param doneAt 完成时间戳；取消完成时传 null
     * @param updatedAt 更新时间戳
     */
    @Query("UPDATE tasks SET isDone = :done, doneAt = :doneAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, updatedAt: Long)

    /**
     * 移入或移出垃圾桶。
     *
     * @param id 任务 ID
     * @param deletedAt 删除时间戳；恢复时传 null
     */
    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: Long, deletedAt: Long?)

    /**
     * 清空垃圾桶（物理删除所有已软删任务）。
     */
    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    /**
     * 任务番茄累计数加一。
     *
     * @param id 任务 ID
     */
    @Query("UPDATE tasks SET completedPomos = completedPomos + 1 WHERE id = :id")
    suspend fun incCompletedPomo(id: Long)

    /**
     * 查询所有未来需要提醒的未完成任务（开机重排闹钟用）。
     *
     * @param now 当前时间戳（毫秒）
     * @return 待提醒任务列表
     */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND isDone = 0 AND reminderAt IS NOT NULL AND reminderAt > :now")
    suspend fun getFutureReminders(now: Long): List<TaskEntity>

    /**
     * 观察任务的全部子任务。
     *
     * @param taskId 任务 ID
     * @return 子任务流
     */
    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId ORDER BY sortOrder ASC, id ASC")
    fun observeSubTasks(taskId: Long): Flow<List<SubTaskEntity>>

    /**
     * 插入子任务。
     *
     * @param subTask 子任务实体
     * @return 新子任务 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTaskEntity): Long

    /**
     * 切换子任务完成状态。
     *
     * @param id 子任务 ID
     * @param done 是否完成
     */
    @Query("UPDATE sub_tasks SET isDone = :done WHERE id = :id")
    suspend fun setSubTaskDone(id: Long, done: Boolean)

    /**
     * 删除单条子任务。
     *
     * @param id 子任务 ID
     */
    @Query("DELETE FROM sub_tasks WHERE id = :id")
    suspend fun deleteSubTask(id: Long)

    /**
     * 删除指定任务的全部子任务。
     *
     * @param taskId 任务 ID
     */
    @Query("DELETE FROM sub_tasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksOf(taskId: Long)
}