package com.focuslist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import kotlinx.coroutines.flow.Flow

/**
 * 行程数据访问对象：行程、站点与站间交通的读写。
 */
@Dao
interface TripDao {

    // ---- 行程 ----

    /**
     * 观察全部行程，最近更新的排前面。
     *
     * @return 行程流
     */
    @Query("SELECT * FROM trips ORDER BY updatedAt DESC, id DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    /**
     * 观察单条行程。
     *
     * @param id 行程 ID
     * @return 行程流；删除后为 null
     */
    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): Flow<TripEntity?>

    /**
     * 按 ID 读取行程。
     *
     * @param id 行程 ID
     * @return 行程；不存在为 null
     */
    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): TripEntity?

    /**
     * 插入行程。
     *
     * @param trip 行程实体
     * @return 新行程 ID
     */
    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    /**
     * 更新行程。
     *
     * @param trip 行程实体
     */
    @Update
    suspend fun updateTrip(trip: TripEntity)

    /**
     * 删除行程本身（站点与交通由仓库一并删除）。
     *
     * @param id 行程 ID
     */
    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    /**
     * 只更新行程的更新时间，用于行程库排序。
     *
     * @param id 行程 ID
     * @param updatedAt 时间戳
     */
    @Query("UPDATE trips SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchTrip(id: Long, updatedAt: Long)

    /**
     * 更新行程卡片尺寸（日历网格中占用的列数与行数编码）。
     *
     * @param id 行程 ID
     * @param cardSize 卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2
     */
    @Query("UPDATE trips SET cardSize = :cardSize WHERE id = :id")
    suspend fun updateCardSize(id: Long, cardSize: Int)

    /**
     * 更新行程卡片的自由画布位置与大小。
     *
     * @param id 行程 ID
     * @param cols 占列数（1-4）
     * @param rows 占行数（1-4）
     * @param x 左上角列（0 起）
     * @param y 左上角行（0 起）
     * @param z 叠放时间戳；越大越靠上
     */
    @Query("UPDATE trips SET cardCols = :cols, cardRows = :rows, cardX = :x, cardY = :y, cardZ = :z WHERE id = :id")
    suspend fun updateCardLayout(id: Long, cols: Int, rows: Int, x: Int, y: Int, z: Long)

    // ---- 站点 ----

    /**
     * 观察一条行程的全部站点。
     *
     * @param tripId 行程 ID
     * @return 站点流（按顺序）
     */
    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY sortIndex ASC, id ASC")
    fun observeStops(tripId: Long): Flow<List<TripStopEntity>>

    /**
     * 观察全部行程的站点（全局搜索用：搜「成都」也能找到经过成都的行程）。
     *
     * @return 所有站点流
     */
    @Query("SELECT * FROM trip_stops ORDER BY tripId ASC, sortIndex ASC, id ASC")
    fun observeAllStops(): Flow<List<TripStopEntity>>

    /**
     * 读取一条行程的全部站点。
     *
     * @param tripId 行程 ID
     * @return 站点列表（按顺序）
     */
    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId ORDER BY sortIndex ASC, id ASC")
    suspend fun getStops(tripId: Long): List<TripStopEntity>

    /**
     * 统计一条行程的站点数。
     *
     * @param tripId 行程 ID
     * @return 站点数
     */
    @Query("SELECT COUNT(*) FROM trip_stops WHERE tripId = :tripId")
    suspend fun countStops(tripId: Long): Int

    /**
     * 插入站点。
     *
     * @param stop 站点实体
     * @return 新站点 ID
     */
    @Insert
    suspend fun insertStop(stop: TripStopEntity): Long

    /**
     * 更新站点。
     *
     * @param stop 站点实体
     */
    @Update
    suspend fun updateStop(stop: TripStopEntity)

    /**
     * 删除站点。
     *
     * @param id 站点 ID
     */
    @Query("DELETE FROM trip_stops WHERE id = :id")
    suspend fun deleteStop(id: Long)

    /**
     * 删除一条行程的全部站点。
     *
     * @param tripId 行程 ID
     */
    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    suspend fun deleteStopsOf(tripId: Long)

    /**
     * 站点前移一位（删除中间某站后重排）。
     *
     * @param tripId 行程 ID
     * @param from 起始顺序（不含）
     */
    @Query("UPDATE trip_stops SET sortIndex = sortIndex - 1 WHERE tripId = :tripId AND sortIndex > :from")
    suspend fun shiftStopsAfter(tripId: Long, from: Int)

    // ---- 交通 ----

    /**
     * 观察一条行程的全部交通段。
     *
     * @param tripId 行程 ID
     * @return 交通段流（按顺序）
     */
    @Query("SELECT * FROM trip_legs WHERE tripId = :tripId ORDER BY sortIndex ASC, id ASC")
    fun observeLegs(tripId: Long): Flow<List<TripLegEntity>>

    /**
     * 读取一条行程的全部交通段。
     *
     * @param tripId 行程 ID
     * @return 交通段列表（按顺序）
     */
    @Query("SELECT * FROM trip_legs WHERE tripId = :tripId ORDER BY sortIndex ASC, id ASC")
    suspend fun getLegs(tripId: Long): List<TripLegEntity>

    /**
     * 读取某一段交通。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     * @return 交通段；没有为 null
     */
    @Query("SELECT * FROM trip_legs WHERE tripId = :tripId AND sortIndex = :sortIndex LIMIT 1")
    suspend fun getLegAt(tripId: Long, sortIndex: Int): TripLegEntity?

    /**
     * 插入交通段。
     *
     * @param leg 交通段实体
     * @return 新记录 ID
     */
    @Insert
    suspend fun insertLeg(leg: TripLegEntity): Long

    /**
     * 更新交通段。
     *
     * @param leg 交通段实体
     */
    @Update
    suspend fun updateLeg(leg: TripLegEntity)

    /**
     * 删除某一段交通。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     */
    @Query("DELETE FROM trip_legs WHERE tripId = :tripId AND sortIndex = :sortIndex")
    suspend fun deleteLegAt(tripId: Long, sortIndex: Int)

    /**
     * 删除一条行程的全部交通段。
     *
     * @param tripId 行程 ID
     */
    @Query("DELETE FROM trip_legs WHERE tripId = :tripId")
    suspend fun deleteLegsOf(tripId: Long)

    /**
     * 交通段前移一位（删除中间某站后重排）。
     *
     * @param tripId 行程 ID
     * @param from 起始顺序（不含）
     */
    @Query("UPDATE trip_legs SET sortIndex = sortIndex - 1 WHERE tripId = :tripId AND sortIndex > :from")
    suspend fun shiftLegsAfter(tripId: Long, from: Int)

    // ---- 备份 / 恢复：全量读写 ----

    /** 同步读取全部行程（导出备份）。 */
    @Query("SELECT * FROM trips ORDER BY id ASC")
    suspend fun getAllTrips(): List<TripEntity>

    /** 同步读取全部行程站点（导出备份）。 */
    @Query("SELECT * FROM trip_stops ORDER BY id ASC")
    suspend fun getAllTripStops(): List<TripStopEntity>

    /** 同步读取全部站间交通（导出备份）。 */
    @Query("SELECT * FROM trip_legs ORDER BY id ASC")
    suspend fun getAllTripLegs(): List<TripLegEntity>

    /** 批量写入行程（同 ID 覆盖，导入备份）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    /** 批量写入行程站点（同 ID 覆盖，导入备份）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripStops(stops: List<TripStopEntity>)

    /** 批量写入站间交通（同 ID 覆盖，导入备份）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripLegs(legs: List<TripLegEntity>)

    /** 清空行程表（覆盖式导入前调用）。 */
    @Query("DELETE FROM trips")
    suspend fun clearTrips()

    /** 清空行程站点表（覆盖式导入前调用）。 */
    @Query("DELETE FROM trip_stops")
    suspend fun clearTripStops()

    /** 清空站间交通表（覆盖式导入前调用）。 */
    @Query("DELETE FROM trip_legs")
    suspend fun clearTripLegs()
}
