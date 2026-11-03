package com.focuslist.app.data.repo

import androidx.room.withTransaction
import com.focuslist.app.data.local.AppDatabase
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelPlaces
import com.focuslist.app.util.TripTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 新增一站时一并带上的「从上一站怎么来」。
 *
 * @property mode 交通方式
 * @property durationMinutes 时长（分钟）
 * @property fromStation 出发站
 * @property toStation 到达站
 * @property serviceNo 班次 / 线路
 * @property lines 地铁线路，多个用 `|` 分隔
 * @property distanceMeters 里程（米）
 * @property polyline 真实道路折线
 */
data class TripArrival(
    val mode: TravelMode,
    val durationMinutes: Int,
    val fromStation: String = "",
    val toStation: String = "",
    val serviceNo: String = "",
    val lines: String = "",
    val distanceMeters: Long = 0,
    val polyline: String = ""
)

/**
 * 行程仓库：行程 / 站点 / 站间交通的增删改查。
 *
 * 站点顺序是核心不变量：`stops[i]` 与 `stops[i+1]` 之间对应 `legs[i]`。
 * 删除中间站点时会把两侧的交通合成一段新的（按距离猜一个默认方式），
 * 保证地图上「上一站与下一站永远有线段相连」。
 *
 * @property db Room 数据库（事务需要）
 */
class TripRepository(private val db: AppDatabase) {

    private val dao = db.tripDao()

    /**
     * 观察全部行程。
     *
     * @return 行程流
     */
    fun observeTrips(): Flow<List<TripEntity>> = dao.observeTrips()

    /**
     * 观察单条行程。
     *
     * @param tripId 行程 ID
     * @return 行程流
     */
    fun observeTrip(tripId: Long): Flow<TripEntity?> = dao.observeTrip(tripId)

    /**
     * 观察一条行程的站点。
     *
     * @param tripId 行程 ID
     * @return 站点流
     */
    fun observeStops(tripId: Long): Flow<List<TripStopEntity>> = dao.observeStops(tripId)

    /**
     * 观察一条行程的交通段。
     *
     * @param tripId 行程 ID
     * @return 交通段流
     */
    fun observeLegs(tripId: Long): Flow<List<TripLegEntity>> = dao.observeLegs(tripId)

    /**
     * 新建一条空行程。
     *
     * @param title 名称
     * @param emoji 封面 emoji
     * @param colorHex 主题色
     * @param note 备注
     * @return 新行程 ID
     */
    suspend fun createTrip(
        title: String,
        emoji: String = "🏔",
        colorHex: String = "#4772FA",
        note: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertTrip(
            TripEntity(
                title = title.trim().ifBlank { "未命名行程" }.take(30),
                emoji = emoji,
                colorHex = colorHex,
                note = note,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /**
     * 保存行程名称 / 颜色 / 备注。
     *
     * @param trip 行程实体
     */
    suspend fun updateTrip(trip: TripEntity) {
        dao.updateTrip(trip.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * 删除一条行程及其全部站点与交通。
     *
     * @param tripId 行程 ID
     */
    suspend fun deleteTrip(tripId: Long) {
        db.withTransaction {
            dao.deleteLegsOf(tripId)
            dao.deleteStopsOf(tripId)
            dao.deleteTrip(tripId)
        }
    }

    /**
     * 在行程末尾添加一站，并写入「从上一站怎么来」。
     *
     * @param tripId 行程 ID
     * @param name 地点名
     * @param region 所属地区
     * @param lat 纬度
     * @param lon 经度
     * @param stayMinutes 停留分钟数
     * @param note 站点备注
     * @param arrival 从上一站来的交通；第一站传 null
     * @return 新站点 ID
     */
    suspend fun addStop(
        tripId: Long,
        name: String,
        region: String,
        lat: Double,
        lon: Double,
        stayMinutes: Int,
        note: String = "",
        arrival: TripArrival? = null
    ): Long {
        return db.withTransaction {
            val index = dao.countStops(tripId)
            val previous = if (index > 0) dao.getStops(tripId).lastOrNull() else null
            val stopId = dao.insertStop(
                TripStopEntity(
                    tripId = tripId,
                    sortIndex = index,
                    name = name.trim().ifBlank { "未命名地点" }.take(30),
                    region = region,
                    lat = lat,
                    lon = lon,
                    stayMinutes = stayMinutes.coerceIn(0, 60 * 24 * 30),
                    note = note
                )
            )
            if (index > 0) {
                // 即使调用方没给交通，也要保证「上一站 → 这一站」有一段默认连线
                val payload = arrival ?: defaultArrival(previous, lat, lon)
                dao.insertLeg(payload.toLeg(tripId, index - 1))
            }
            dao.touchTrip(tripId, System.currentTimeMillis())
            stopId
        }
    }

    /**
     * 删除站点后补的默认交通：按距离猜一个方式，时长留空等用户填。
     *
     * @param previous 上一站
     * @param lat 新站纬度
     * @param lon 新站经度
     * @return 默认交通
     */
    private fun defaultArrival(previous: TripStopEntity?, lat: Double, lon: Double): TripArrival {
        if (previous == null) return TripArrival(TravelMode.CAR, 0)
        val distance = TravelGeo.distanceMeters(
            LatLon(previous.lat, previous.lon),
            LatLon(lat, lon)
        ).toLong()
        val mode = when {
            distance > 200_000 -> TravelMode.HSR
            distance > 20_000 -> TravelMode.CAR
            else -> TravelMode.WALK
        }
        return TripArrival(mode = mode, durationMinutes = 0, distanceMeters = distance)
    }

    /**
     * 更新站点本身，并同步它上一段交通（如果传了 [arrival]）。
     *
     * @param stop 站点实体
     * @param arrival 从上一站来的交通；null 表示只改站点
     */
    suspend fun updateStop(stop: TripStopEntity, arrival: TripArrival? = null) {
        db.withTransaction {
            dao.updateStop(stop.copy(stayMinutes = stop.stayMinutes.coerceIn(0, 60 * 24 * 30)))
            if (arrival != null && stop.sortIndex > 0) {
                upsertLeg(stop.tripId, stop.sortIndex - 1, arrival)
            }
            dao.touchTrip(stop.tripId, System.currentTimeMillis())
        }
    }

    /**
     * 删除一站：同时删掉进站与出站两段交通，重排后面的顺序；
     * 如果删的是中间站，会给新的相邻两站补一段默认交通，保证路线不断开。
     *
     * @param stop 要删除的站点
     */
    suspend fun deleteStop(stop: TripStopEntity) {
        db.withTransaction {
            val stops = dao.getStops(stop.tripId)
            val target = stops.firstOrNull { it.id == stop.id } ?: return@withTransaction
            val index = target.sortIndex
            val prev = stops.getOrNull(index - 1)
            val next = stops.getOrNull(index + 1)
            dao.deleteLegAt(stop.tripId, index - 1)
            dao.deleteLegAt(stop.tripId, index)
            dao.deleteStop(target.id)
            dao.shiftStopsAfter(stop.tripId, index)
            dao.shiftLegsAfter(stop.tripId, index - 1)
            if (prev != null && next != null) {
                val distance = TravelGeo.distanceMeters(
                    LatLon(prev.lat, prev.lon),
                    LatLon(next.lat, next.lon)
                ).toLong()
                val mode = when {
                    distance > 200_000 -> TravelMode.HSR
                    distance > 20_000 -> TravelMode.CAR
                    else -> TravelMode.WALK
                }
                dao.insertLeg(
                    TripLegEntity(
                        tripId = stop.tripId,
                        sortIndex = index - 1,
                        mode = mode.key,
                        durationMinutes = 0,
                        distanceMeters = distance
                    )
                )
            }
            dao.touchTrip(stop.tripId, System.currentTimeMillis())
        }
    }

    /**
     * 保存一段交通（按起点顺序覆盖写入）。
     *
     * @param leg 交通实体
     */
    suspend fun saveLeg(leg: TripLegEntity) {
        db.withTransaction {
            upsertLeg(leg.tripId, leg.sortIndex, leg.toArrival())
            dao.touchTrip(leg.tripId, System.currentTimeMillis())
        }
    }

    /**
     * 只更新某段交通的真实路线（在线规划回来后写库，不覆盖用户填的时长）。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     * @param polyline 折线
     * @param distanceMeters 里程
     * @param durationMinutes 估算时长，传 0 表示保留原值
     */
    suspend fun saveLegRoute(
        tripId: Long,
        sortIndex: Int,
        polyline: String,
        distanceMeters: Long,
        durationMinutes: Int
    ) {
        val current = dao.getLegAt(tripId, sortIndex) ?: return
        dao.updateLeg(
            current.copy(
                polyline = polyline,
                distanceMeters = distanceMeters,
                durationMinutes = if (durationMinutes > 0) durationMinutes else current.durationMinutes
            )
        )
    }

    /**
     * 读取某一段交通。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     * @return 交通；没有为 null
     */
    suspend fun legAt(tripId: Long, sortIndex: Int): TripLegEntity? = dao.getLegAt(tripId, sortIndex)

    /**
     * 按模板生成一条完整行程。
     *
     * @param template 模板
     * @return 新行程 ID
     */
    suspend fun createFromTemplate(template: TripTemplate): Long {
        return db.withTransaction {
            val tripId = createTrip(template.title, template.emoji, template.colorHex, template.subtitle)
            val valid = template.stops.mapNotNull { item ->
                TravelPlaces.byName(item.place)?.let { it to item }
            }
            var index = 0
            valid.forEach { (place, item) ->
                dao.insertStop(
                    TripStopEntity(
                        tripId = tripId,
                        sortIndex = index,
                        name = place.name,
                        region = place.region,
                        lat = place.lat,
                        lon = place.lon,
                        stayMinutes = item.stayMinutes
                    )
                )
                if (index > 0 && item.mode != null) {
                    val prev = valid.getOrNull(index - 1)?.first
                    val distance = if (prev != null) {
                        TravelGeo.distanceMeters(
                            LatLon(prev.lat, prev.lon),
                            LatLon(place.lat, place.lon)
                        ).toLong()
                    } else {
                        0L
                    }
                    dao.insertLeg(
                        TripLegEntity(
                            tripId = tripId,
                            sortIndex = index - 1,
                            mode = item.mode.key,
                            durationMinutes = item.travelMinutes,
                            fromStation = item.fromStation,
                            toStation = item.toStation,
                            serviceNo = item.serviceNo,
                            distanceMeters = distance
                        )
                    )
                }
                index += 1
            }
            tripId
        }
    }

    /**
     * 写入或更新一段交通。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     * @param arrival 交通内容
     */
    private suspend fun upsertLeg(tripId: Long, sortIndex: Int, arrival: TripArrival) {
        val existing = dao.getLegAt(tripId, sortIndex)
        if (existing == null) {
            dao.insertLeg(arrival.toLeg(tripId, sortIndex))
        } else {
            dao.updateLeg(
                existing.copy(
                    mode = arrival.mode.key,
                    durationMinutes = arrival.durationMinutes.coerceAtLeast(0),
                    fromStation = arrival.fromStation,
                    toStation = arrival.toStation,
                    serviceNo = arrival.serviceNo,
                    lines = arrival.lines,
                    distanceMeters = arrival.distanceMeters.coerceAtLeast(0),
                    polyline = arrival.polyline
                )
            )
        }
    }

    /**
     * 交通草稿转实体。
     *
     * @param tripId 行程 ID
     * @param sortIndex 起点站顺序
     * @return 实体
     */
    private fun TripArrival.toLeg(tripId: Long, sortIndex: Int): TripLegEntity {
        return TripLegEntity(
            tripId = tripId,
            sortIndex = sortIndex,
            mode = mode.key,
            durationMinutes = durationMinutes.coerceAtLeast(0),
            fromStation = fromStation,
            toStation = toStation,
            serviceNo = serviceNo,
            lines = lines,
            distanceMeters = distanceMeters.coerceAtLeast(0),
            polyline = polyline
        )
    }

    /**
     * 实体转交通草稿。
     *
     * @return 草稿
     */
    private fun TripLegEntity.toArrival(): TripArrival {
        return TripArrival(
            mode = TravelMode.of(mode),
            durationMinutes = durationMinutes,
            fromStation = fromStation,
            toStation = toStation,
            serviceNo = serviceNo,
            lines = lines,
            distanceMeters = distanceMeters,
            polyline = polyline
        )
    }
}
