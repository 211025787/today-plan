package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * 计划数据访问对象：计划定义与当日累计时长的读写。
 */
@Dao
interface PlanDao {

    /**
     * 观察当前生效中的计划（今天落在 [startDate, endDate] 内且未归档）。
     *
     * @param today 今天 0 点时间戳
     * @return 生效计划流
     */
    @Query("SELECT * FROM plans WHERE archived = 0 AND startDate <= :today AND endDate >= :today ORDER BY sortOrder ASC, id ASC")
    fun observeActiveToday(today: Long): Flow<List<PlanEntity>>

    /**
     * 观察全部未归档计划（管理用）。
     *
     * @return 计划流
     */
    @Query("SELECT * FROM plans WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PlanEntity>>

    /**
     * 插入计划。
     *
     * @param plan 计划实体
     * @return 新计划 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PlanEntity): Long

    /**
     * 更新计划。
     *
     * @param plan 计划实体
     */
    @Update
    suspend fun update(plan: PlanEntity)

    /**
     * 物理删除计划及其全部日志。
     *
     * @param id 计划 ID
     */
    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除计划的全部日志。
     *
     * @param planId 计划 ID
     */
    @Query("DELETE FROM plan_logs WHERE planId = :planId")
    suspend fun deleteLogsOf(planId: Long)

    /**
     * 删除计划在指定日期区间内的日志（按范围删除计划用，区间含两端）。
     *
     * @param planId 计划 ID
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     */
    @Query("DELETE FROM plan_logs WHERE planId = :planId AND date >= :start AND date <= :end")
    suspend fun deleteLogsBetween(planId: Long, start: Long, end: Long)

    /**
     * 观察某天全部计划的累计记录。
     *
     * @param date 当天 0 点时间戳
     * @return 当日记录流
     */
    @Query("SELECT * FROM plan_logs WHERE date = :date")
    fun observeLogsOfDay(date: Long): Flow<List<PlanLogEntity>>

    /**
     * 观察日期区间内的累计记录（月历完成度聚合用）。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 记录流
     */
    @Query("SELECT * FROM plan_logs WHERE date >= :start AND date <= :end")
    fun observeLogsBetween(start: Long, end: Long): Flow<List<PlanLogEntity>>

    /**
     * 查询某计划当天的累计记录。
     *
     * @param planId 计划 ID
     * @param date 当天 0 点时间戳
     * @return 记录；无记录时为 null
     */
    @Query("SELECT * FROM plan_logs WHERE planId = :planId AND date = :date")
    suspend fun getLog(planId: Long, date: Long): PlanLogEntity?

    /**
     * 按 ID 查询计划实体。
     *
     * @param id 计划 ID
     * @return 计划实体；不存在时为 null
     */
    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: Long): PlanEntity?

    /**
     * 插入当日记录。
     *
     * @param log 记录实体
     * @return 新记录 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PlanLogEntity): Long

    /**
     * 当日累计秒数原子加增。
     *
     * @param planId 计划 ID
     * @param date 当天 0 点时间戳
     * @param deltaSec 追加秒数
     * @param updatedAt 更新时间戳
     */
    @Query("UPDATE plan_logs SET accumulatedSec = accumulatedSec + :deltaSec, updatedAt = :updatedAt WHERE planId = :planId AND date = :date")
    suspend fun addSeconds(planId: Long, date: Long, deltaSec: Long, updatedAt: Long)

    /**
     * 直接设置当日累计秒数（手动补录场景：覆盖原值而非追加）。
     *
     * @param planId 计划 ID
     * @param date 当天 0 点时间戳
     * @param sec 新的累计秒数
     * @param updatedAt 更新时间戳
     */
    @Query("UPDATE plan_logs SET accumulatedSec = :sec, updatedAt = :updatedAt WHERE planId = :planId AND date = :date")
    suspend fun setSeconds(planId: Long, date: Long, sec: Long, updatedAt: Long)

    /**
     * 按标题批量改名（用于旧示例计划的一次性更名）。
     *
     * @param oldTitle 旧标题
     * @param newTitle 新标题
     * @return 受影响行数
     */
    @Query("UPDATE plans SET title = :newTitle WHERE title = :oldTitle")
    suspend fun renameByTitle(oldTitle: String, newTitle: String): Int

}