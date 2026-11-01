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
     * 查询出现过完整番茄的日期（**本地时区** `yyyy-MM-dd`），用于连续天数计算。
     *
     * 注意：不能写成 `(startAt / 86400000) * 86400000`，那是按 UTC 分桶，
     * 在东八区会得到前一天，与 [com.focuslist.app.util.DateUtils.startOfDay] 对不上。
     *
     * @return 各活跃日的本地日期串，倒序排列
     */
    @Query(
        "SELECT DISTINCT date(startAt / 1000, 'unixepoch', 'localtime') AS day " +
            "FROM focus_records WHERE phase = 'WORK' AND completed = 1 ORDER BY day DESC"
    )
    suspend fun getActiveDayKeys(): List<String>

    /**
     * 按本地自然日聚合专注秒数，用于年度热力图。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 每天的 [日期串, 秒数]，仅包含有记录的日期，按日期升序
     */
    @Query(
        "SELECT date(startAt / 1000, 'unixepoch', 'localtime') AS day, SUM(durationSec) AS seconds " +
            "FROM focus_records " +
            "WHERE startAt >= :start AND startAt < :end AND phase = 'WORK' AND completed = 1 " +
            "GROUP BY day ORDER BY day ASC"
    )
    suspend fun sumByDay(start: Long, end: Long): List<DayTotal>

    /**
     * 按**本地小时**聚合专注秒数，用于专注节律（一天里什么时候最专注）。
     *
     * 与 [sumByDay] 同理走 `localtime` 修饰符，只是这里取的是小时而非日期串；
     * `strftime('%H')` 返回的是字符串，需 `CAST ... AS INTEGER` 才能映射成 [HourTotal.hour]。
     *
     * @param start 段起点时间戳（含）
     * @param end 段终点时间戳（不含）
     * @return 每个小时的 `[小时, 秒数]`，仅包含有记录的小时，按小时升序
     */
    @Query(
        "SELECT CAST(strftime('%H', startAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
            "SUM(durationSec) AS seconds " +
            "FROM focus_records " +
            "WHERE startAt >= :start AND startAt < :end AND phase = 'WORK' AND completed = 1 " +
            "GROUP BY hour ORDER BY hour ASC"
    )
    suspend fun sumByHour(start: Long, end: Long): List<HourTotal>

    /**
     * 统计全部时间里出现过完整番茄的自然日天数。
     *
     * 与 [getActiveDayKeys] 一样走 `localtime` 修饰，保证与本地 0 点口径一致。
     *
     * @return 活跃天数；无记录时为 null
     */
    @Query(
        "SELECT COUNT(DISTINCT date(startAt / 1000, 'unixepoch', 'localtime')) " +
            "FROM focus_records WHERE phase = 'WORK' AND completed = 1"
    )
    suspend fun countActiveDays(): Int?

    /**
     * 查历史最佳单日：按本地自然日聚合后取秒数最高的一天。
     *
     * 返回列表而非标量，是因为 Room 对「GROUP BY + LIMIT」这类查询按列表投影最稳。
     *
     * @return 至多一条 `[日期串, 秒数]`；无记录时为空列表
     */
    @Query(
        "SELECT date(startAt / 1000, 'unixepoch', 'localtime') AS day, SUM(durationSec) AS seconds " +
            "FROM focus_records WHERE phase = 'WORK' AND completed = 1 " +
            "GROUP BY day ORDER BY seconds DESC LIMIT 1"
    )
    suspend fun bestDay(): List<DayTotal>

    // ---- 备份 / 恢复：全量读写 ----

    /** 同步读取全部专注记录，用于导出备份。 */
    @Query("SELECT * FROM focus_records ORDER BY id ASC")
    suspend fun getAll(): List<FocusRecordEntity>

    /** 批量写入专注记录（同 ID 覆盖），用于导入备份。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<FocusRecordEntity>)

    /** 清空专注记录表（覆盖式导入前调用）。 */
    @Query("DELETE FROM focus_records")
    suspend fun clearAll()
}