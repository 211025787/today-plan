package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focuslist.app.data.local.entity.FocusRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 专注记录数据访问对象：支撑统计页的日/周/月维度聚合。
 */
@Dao
interface FocusRecordDao {

    /**
     * 写入一条专注记录。
     *
     * @param record 专注记录实体
     * @return 新记录 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FocusRecordEntity): Long

    /**
     * 观察指定时间段内的记录，按开始时间倒序。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 记录流
     */
    @Query("SELECT * FROM focus_records WHERE startAt >= :start AND startAt < :end ORDER BY startAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<FocusRecordEntity>>

    /**
     * 统计时间段内 WORK 阶段的总秒数（仅完整完成的番茄）。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 总秒数；无记录时为 null
     */
    @Query("SELECT SUM(durationSec) FROM focus_records WHERE startAt >= :start AND startAt < :end AND phase = 'WORK' AND completed = 1")
    suspend fun sumWorkSeconds(start: Long, end: Long): Long?

    /**
     * 统计时间段内完整完成的番茄个数。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 番茄个数；无记录时为 null
     */
    @Query("SELECT COUNT(*) FROM focus_records WHERE startAt >= :start AND startAt < :end AND phase = 'WORK' AND completed = 1")
    suspend fun countCompletedPomos(start: Long, end: Long): Int?

    /**
     * 查询出现过完整番茄的日期列表（当天 0 点时间戳），用于连续天数计算。
     *
     * @return 各活跃日的 0 点时间戳，倒序排列
     */
    @Query(
        "SELECT DISTINCT (startAt / 86400000) * 86400000 AS dayStart FROM focus_records " +
            "WHERE phase = 'WORK' AND completed = 1 ORDER BY dayStart DESC"
    )
    suspend fun getActiveDays(): List<Long>
}