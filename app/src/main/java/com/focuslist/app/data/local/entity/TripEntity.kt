package com.focuslist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focuslist.app.util.TravelMode

/**
 * 一趟旅行：可以有多站，站与站之间是一段交通。
 *
 * @property id 主键，自增；0 表示未插入
 * @property title 行程名称，如「川西小环线」
 * @property emoji 封面 emoji
 * @property colorHex 主题色，路线与站点标记都用它
 * @property note 备注
 * @property startDate 行程起始日 0 点时间戳（毫秒）；0 表示未设置，不显示到日历
 * @property endDate 行程结束日 0 点时间戳（毫秒）；0 表示未设置
 * @property cardSize 旧版卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2；仅用于旧数据兜底
 * @property cardCols 卡片占列数（1-4；0 表示沿用 cardSize）
 * @property cardRows 卡片占行数（1-4；0 表示沿用 cardSize）
 * @property cardX 卡片左上角列（0 起）；-1 表示还没手动摆放
 * @property cardY 卡片左上角行（0 起）；-1 表示还没手动摆放
 * @property cardZ 最近一次移动/缩放的叠放时间戳；越大越靠上
 * @property createdAt 创建时间戳（毫秒）
 * @property updatedAt 最近更新时间戳（毫秒），行程库按它倒序
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String = "🏔",
    val colorHex: String = "#4772FA",
    val note: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val cardSize: Int = 3,
    val cardCols: Int = 0,
    val cardRows: Int = 0,
    val cardX: Int = -1,
    val cardY: Int = -1,
    val cardZ: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 行程里的一站：地点 + 停留时长。
 *
 * 顺序由 [sortIndex] 决定（0 开始），站与站的交通存在 [TripLegEntity]，
 * 第 i 段交通连接第 i 站与第 i+1 站。
 *
 * @property id 主键，自增
 * @property tripId 所属行程
 * @property sortIndex 在本行程中的顺序
 * @property name 地点名称
 * @property region 所属地区，可为空（地图选点时没有）
 * @property lat WGS-84 纬度
 * @property lon WGS-84 经度
 * @property stayMinutes 停留分钟数，0 表示只是路过
 * @property note 本站备注
 */
@Entity(
    tableName = "trip_stops",
    indices = [Index(value = ["tripId"])]
)
data class TripStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val sortIndex: Int,
    val name: String,
    val region: String = "",
    val lat: Double,
    val lon: Double,
    val stayMinutes: Int = 0,
    val note: String = ""
)

/**
 * 一段交通：从第 [sortIndex] 站到第 [sortIndex] + 1 站。
 *
 * @property id 主键，自增
 * @property tripId 所属行程
 * @property sortIndex 起点站在行程里的顺序
 * @property mode 交通方式，见 [TravelMode.key]
 * @property durationMinutes 交通时长（分钟）
 * @property fromStation 出发站 / 出发机场 / 登船港
 * @property toStation 到达站 / 到达机场 / 到达港
 * @property serviceNo 航班号 / 车次 / 公交线路 / 船次
 * @property lines 地铁路线，多个用 `|` 分隔
 * @property distanceMeters 里程（米）；没有真实轨迹时是直线距离，0 表示未知
 * @property polyline 真实道路折线（「纬度,经度;…」）；空串表示没有轨迹，地图画平滑弧线
 */
@Entity(
    tableName = "trip_legs",
    indices = [Index(value = ["tripId"])]
)
data class TripLegEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val sortIndex: Int,
    val mode: String = TravelMode.HSR.key,
    val durationMinutes: Int = 0,
    val fromStation: String = "",
    val toStation: String = "",
    val serviceNo: String = "",
    val lines: String = "",
    val distanceMeters: Long = 0,
    val polyline: String = ""
)
