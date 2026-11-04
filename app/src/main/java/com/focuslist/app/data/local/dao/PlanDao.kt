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
     * 按计划汇总区间内的累计投入秒数。
     *
     * 日卡片的「落后 N 天」角标只需要每个计划的一条总数，
     * 用一次分组查询代替「逐计划查一次」，计划再多也只有一次查询。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 每个计划的累计秒数；没有记录的计划不出现在结果里
     */
    @Query(
        "SELECT planId AS planId, SUM(accumulatedSec) AS seconds FROM plan_logs " +
            "WHERE date >= :start AND date <= :end GROUP BY planId"
    )
    suspend fun sumSecondsByPlan(start: Long, end: Long): List<PlanTotal>

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
     * 查询某计划在日期区间内的逐日累计记录（计划详情用，一次取回整段窗口）。
     *
     * @param planId 计划 ID
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 按日期升序的记录列表
     */
    @Query("SELECT * FROM plan_logs WHERE planId = :planId AND date >= :start AND date <= :end ORDER BY date ASC")
    suspend fun getLogsOfPlanBetween(planId: Long, start: Long, end: Long): List<PlanLogEntity>

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

    /**
     * 更新计划卡片尺寸（日历网格中占用的列数与行数编码）。
     *
     * @param id 计划 ID
     * @param cardSize 卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2
     */
    @Query("UPDATE plans SET cardSize = :cardSize WHERE id = :id")
    suspend fun updateCardSize(id: Long, cardSize: Int)

    /**
     * 更新计划卡片的自由画布位置与大小。
     *
     * @param id 计划 ID
     * @param cols 占列数（1-4）
     * @param rows 占行数（1-4）
     * @param x 左上角列（0 起）
     * @param y 左上角行（0 起）
     */
    @Query("UPDATE plans SET cardCols = :cols, cardRows = :rows, cardX = :x, cardY = :y WHERE id = :id")
    suspend fun updateCardLayout(id: Long, cols: Int, rows: Int, x: Int, y: Int)

    // ---- 备份 / 恢复：全量读写 ----

    /** 同步读取全部计划（含已归档），用于导出备份。 */
    @Query("SELECT * FROM plans ORDER BY id ASC")
    suspend fun getAllPlans(): List<PlanEntity>

    /** 同步读取全部计划投入日志，用于导出备份。 */
    @Query("SELECT * FROM plan_logs ORDER BY id ASC")
    suspend fun getAllLogs(): List<PlanLogEntity>

    /** 批量写入计划（同 ID 覆盖），用于导入备份。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<PlanEntity>)

    /** 批量写入投入日志（同 ID 覆盖），用于导入备份。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<PlanLogEntity>)

    /** 清空计划表（覆盖式导入前调用）。 */
    @Query("DELETE FROM plans")
    suspend fun clearPlans()

    /** 清空投入日志表（覆盖式导入前调用）。 */
    @Query("DELETE FROM plan_logs")
    suspend fun clearLogs()
}