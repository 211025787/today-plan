package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.HabitCheckEntity
import com.focuslist.app.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

/**
 * 习惯数据访问对象：习惯定义与打卡记录的读写。
 */
@Dao
interface HabitDao {

    /**
     * 观察所有未归档习惯。
     *
     * @return 习惯流，按排序权重升序
     */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    /**
     * 直接查询所有未归档习惯（供小组件同步调用）。
     *
     * @return 习惯列表
     */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun getActiveSync(): List<HabitEntity>

    /**
     * 插入新习惯。
     *
     * @param habit 习惯实体
     * @return 新习惯 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    /**
     * 更新习惯。
     *
     * @param habit 习惯实体
     */
    @Update
    suspend fun update(habit: HabitEntity)

    /**
     * 删除习惯及其全部打卡记录。
     *
     * @param id 习惯 ID
     */
    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除习惯的全部打卡记录。
     *
     * @param habitId 习惯 ID
     */
    @Query("DELETE FROM habit_checks WHERE habitId = :habitId")
    suspend fun deleteChecksOf(habitId: Long)

    /**
     * 插入打卡记录；同一天重复打卡时忽略（唯一索引兜底，保证幂等）。
     *
     * @param check 打卡记录
     * @return 新记录 ID；冲突时为 -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(check: HabitCheckEntity): Long

    /**
     * 取消某天的打卡。
     *
     * @param habitId 习惯 ID
     * @param date 当天 0 点时间戳
     */
    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCheck(habitId: Long, date: Long)

    /**
     * 观察某天所有打卡记录。
     *
     * @param date 当天 0 点时间戳
     * @return 打卡记录流
     */
    @Query("SELECT * FROM habit_checks WHERE date = :date")
    fun observeChecksOfDay(date: Long): Flow<List<HabitCheckEntity>>

    /**
     * 观察日期区间内的所有打卡记录（周视图用）。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 打卡记录流
     */
    @Query("SELECT * FROM habit_checks WHERE date >= :start AND date <= :end")
    fun observeChecksBetween(start: Long, end: Long): Flow<List<HabitCheckEntity>>

    /**
     * 查询某天已打卡的习惯 ID 集合（供小组件同步调用）。
     *
     * @param date 当天 0 点时间戳
     * @return 已打卡习惯 ID 列表
     */
    @Query("SELECT habitId FROM habit_checks WHERE date = :date")
    fun getCheckedHabitIdsSync(date: Long): List<Long>

    /**
     * 查询习惯的全部打卡日期（当天 0 点时间戳），倒序，用于连续天数计算。
     *
     * @param habitId 习惯 ID
     * @return 打卡日期列表
     */
    @Query("SELECT date FROM habit_checks WHERE habitId = :habitId ORDER BY date DESC")
    suspend fun getCheckDates(habitId: Long): List<Long>
}