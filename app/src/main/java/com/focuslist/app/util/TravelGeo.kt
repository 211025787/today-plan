package com.focuslist.app.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 地球坐标（WGS-84）：纬度在前，经度在后。
 *
 * @property lat 纬度，-90 ~ 90
 * @property lon 经度，-180 ~ 180
 */
data class LatLon(val lat: Double, val lon: Double)

/**
 * 经纬度包围盒。
 *
 * @property minLat 最小纬度（南）
 * @property maxLat 最大纬度（北）
 * @property minLon 最小经度（西）
 * @property maxLon 最大经度（东）
 */
data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    /** 中心纬度 */
    val centerLat: Double get() = (minLat + maxLat) / 2.0

    /** 中心经度 */
    val centerLon: Double get() = (minLon + maxLon) / 2.0

    /** 中心点 */
    val center: LatLon get() = LatLon(centerLat, centerLon)
}

/**
 * 地图取景结果：中心点 + 缩放级别。
 *
 * @property center 相机中心
 * @property zoom 缩放级别（3 约等于全国视野，12 约等于城区视野）
 */
data class MapFit(val center: LatLon, val zoom: Float)

/**
 * 行程地图的纯几何工具：Web 墨卡托投影、球面距离、路线折线编解码、
 * 无真实轨迹时的平滑弧线、Douglas-Peucker 抽稀，以及国内地图的 GCJ-02 纠偏。
 *
 * 这些函数全部是纯计算，不依赖 Android，方便单元测试。
 */
object TravelGeo {

    /** 赤道半径（米），与 OSRM / 高德一致 */
    const val EARTH_RADIUS_M = 6378137.0

    /** 瓦片边长（像素） */
    const val TILE_SIZE = 256.0

    /** Web 墨卡托允许的最大纬度 */
    const val MAX_MERCATOR_LAT = 85.0511287798066

    /**
     * 两点球面距离（米），Haversine 公式。
     *
     * @param a 起点
     * @param b 终点
     * @return 距离（米）
     */
    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(max(0.0, 1 - h)))
    }

    /**
     * 起点到终点的方位角（度，正北为 0，顺时针）。
     *
     * @param a 起点
     * @param b 终点
     * @return 方位角 0-360
     */
    fun bearingDegrees(a: LatLon, b: LatLon): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * 经度转 Web 墨卡托横向坐标（0-1，世界左边缘为 0）。
     *
     * @param lon 经度
     * @return 归一化横坐标
     */
    fun worldX(lon: Double): Double = (lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0

    /**
     * 纬度转 Web 墨卡托纵向坐标（0-1，世界上边缘为 0）。
     *
     * @param lat 纬度
     * @return 归一化纵坐标
     */
    fun worldY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    /**
     * 归一化世界坐标还原为经纬度。
     *
     * @param x 归一化横坐标
     * @param y 归一化纵坐标
     * @return 经纬度
     */
    fun fromWorld(x: Double, y: Double): LatLon {
        val lon = x * 360.0 - 180.0
        val n = PI * (1.0 - 2.0 * y)
        val lat = Math.toDegrees(atan(sinh(n)))
        return LatLon(lat, lon)
    }

    /**
     * 一组坐标的包围盒。
     *
     * @param points 坐标列表
     * @return 包围盒；空列表返回 null
     */
    fun boundsOf(points: List<LatLon>): GeoBounds? {
        if (points.isEmpty()) return null
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        points.forEach { p ->
            minLat = min(minLat, p.lat)
            maxLat = max(maxLat, p.lat)
            minLon = min(minLon, p.lon)
            maxLon = max(maxLon, p.lon)
        }
        return GeoBounds(minLat, maxLat, minLon, maxLon)
    }

    /**
     * 计算把一组地点放进屏幕的取景参数。
     *
     * @param points 需要完整显示的地点
     * @param widthPx 地图宽度（像素）
     * @param heightPx 地图高度（像素）
     * @param paddingPx 四周留白（像素）
     * @param minZoom 最小缩放
     * @param maxZoom 最大缩放
     * @return 取景参数；没有地点时返回 null
     */
    fun fit(
        points: List<LatLon>,
        widthPx: Float,
        heightPx: Float,
        paddingPx: Float,
        minZoom: Float = 2.6f,
        maxZoom: Float = 15.5f
    ): MapFit? {
        if (points.isEmpty() || widthPx <= 0f || heightPx <= 0f) return null
        val bounds = boundsOf(points) ?: return null
        if (points.size == 1 || bounds.maxLat - bounds.minLat < 1e-7 && bounds.maxLon - bounds.minLon < 1e-7) {
            return MapFit(LatLon(bounds.centerLat, bounds.centerLon), 11f.coerceIn(minZoom, maxZoom))
        }
        val topY = worldY(bounds.maxLat)
        val bottomY = worldY(bounds.minLat)
        val leftX = worldX(bounds.minLon)
        val rightX = worldX(bounds.maxLon)
        val spanX = (rightX - leftX).coerceAtLeast(1e-9)
        val spanY = (bottomY - topY).coerceAtLeast(1e-9)
        val availW = (widthPx - paddingPx * 2f).coerceAtLeast(64f)
        val availH = (heightPx - paddingPx * 2f).coerceAtLeast(64f)
        val zoomX = log2(availW / (spanX * TILE_SIZE))
        val zoomY = log2(availH / (spanY * TILE_SIZE))
        val zoom = min(zoomX, zoomY).toFloat().coerceIn(minZoom, maxZoom)
        return MapFit(fromWorld((leftX + rightX) / 2.0, (topY + bottomY) / 2.0), zoom)
    }

    /**
     * 当前缩放级别下，每像素代表多少米。
     *
     * @param lat 所在纬度
     * @param zoom 缩放级别（256 像素瓦片）
     * @return 米 / 像素
     */
    fun metersPerPixel(lat: Double, zoom: Float): Double {
        return 156543.03392804097 * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom.toDouble())
    }

    /**
     * 两地之间没有真实轨迹时的平滑弧线。
     *
     * 在墨卡托平面里沿两点连线叠加一个正弦拱高，[bend] 越大弧度越明显：
     * 飞机 0.18、邮轮 0.14、高铁 0.10、步行 0.01。短距离时拱高随距离等比缩小，
     * 所以城区里不会出现夸张的弯线。
     *
     * @param from 起点
     * @param to 终点
     * @param bend 拱高比例，0 为直线
     * @param samples 采样段数，至少 2
     * @return 弧线坐标点（含首尾）
     */
    fun curve(from: LatLon, to: LatLon, bend: Double, samples: Int = 32): List<LatLon> {
        val count = samples.coerceAtLeast(2)
        val ax = worldX(from.lon)
        val ay = worldY(from.lat)
        val bx = worldX(to.lon)
        val by = worldY(to.lat)
        val dx = bx - ax
        val dy = by - ay
        val len = hypot(dx, dy)
        if (len < 1e-12 || bend <= 0.0) return listOf(from, to)
        val nx = -dy / len
        val ny = dx / len
        val arch = len * bend
        return (0..count).map { i ->
            val t = i.toDouble() / count
            val lift = sin(PI * t) * arch
            fromWorld(ax + dx * t + nx * lift, ay + dy * t + ny * lift)
        }
    }

    /**
     * 折线抽稀（Douglas-Peucker）：删掉对形状几乎没有贡献的点，减少绘制与存储开销。
     *
     * @param points 原始折线
     * @param toleranceMeters 允许的最大偏离（米）
     * @return 抽稀后的折线，首尾一定保留
     */
    fun simplify(points: List<LatLon>, toleranceMeters: Double): List<LatLon> {
        if (points.size <= 2 || toleranceMeters <= 0.0) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end - start < 2) continue
            var farthest = -1
            var farthestDistance = 0.0
            for (i in start + 1 until end) {
                val d = perpendicularMeters(points[i], points[start], points[end])
                if (d > farthestDistance) {
                    farthestDistance = d
                    farthest = i
                }
            }
            if (farthest > 0 && farthestDistance > toleranceMeters) {
                keep[farthest] = true
                stack.addLast(start to farthest)
                stack.addLast(farthest to end)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /**
     * 点到线段的地面近似距离（米），用于抽稀。
     *
     * @param p 待测点
     * @param a 线段起点
     * @param b 线段终点
     * @return 距离（米）
     */
    private fun perpendicularMeters(p: LatLon, a: LatLon, b: LatLon): Double {
        val latScale = 111_320.0
        val lonScale = 111_320.0 * cos(Math.toRadians(a.lat))
        val px = (p.lon - a.lon) * lonScale
        val py = (p.lat - a.lat) * latScale
        val bx = (b.lon - a.lon) * lonScale
        val by = (b.lat - a.lat) * latScale
        val lengthSq = bx * bx + by * by
        if (lengthSq <= 0.0) return hypot(px, py)
        val t = ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(px - bx * t, py - by * t)
    }

    /**
     * 沿折线按累计距离取一点，用于把「交通方式 + 时长」标签放在真正的中点。
     *
     * @param points 折线
     * @param fraction 0-1 的位置比例
     * @return 该位置附近的坐标；空折线返回 null
     */
    fun pointAtFraction(points: List<LatLon>, fraction: Double): LatLon? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points.first()
        val f = fraction.coerceIn(0.0, 1.0)
        val segments = ArrayList<Double>(points.size - 1)
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val d = distanceMeters(points[i], points[i + 1])
            segments.add(d)
            total += d
        }
        if (total <= 0.0) return points.first()
        var target = total * f
        for (i in segments.indices) {
            if (target <= segments[i]) {
                val t = if (segments[i] <= 0.0) 0.0 else target / segments[i]
                return LatLon(
                    points[i].lat + (points[i + 1].lat - points[i].lat) * t,
                    points[i].lon + (points[i + 1].lon - points[i].lon) * t
                )
            }
            target -= segments[i]
        }
        return points.last()
    }

    /**
     * 路线折线序列化为紧凑字符串：「纬度,经度;纬度,经度」，百万分之一度。
     *
     * @param points 折线
     * @return 可存入数据库的字符串；空列表返回空串
     */
    fun encodePath(points: List<LatLon>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder(points.size * 16)
        points.forEachIndexed { index, p ->
            if (index > 0) sb.append(';')
            sb.append((p.lat * 1_000_000).roundToLong())
            sb.append(',')
            sb.append((p.lon * 1_000_000).roundToLong())
        }
        return sb.toString()
    }

    /**
     * 解析数据库里的折线字符串，坏数据跳过而不是崩溃。
     *
     * @param raw 序列化字符串
     * @return 折线坐标（可能为空）
     */
    fun decodePath(raw: String): List<LatLon> {
        if (raw.isBlank()) return emptyList()
        val out = ArrayList<LatLon>()
        raw.split(';').forEach { pair ->
            val comma = pair.indexOf(',')
            if (comma <= 0) return@forEach
            val lat = pair.substring(0, comma).trim().toDoubleOrNull()?.div(1_000_000.0) ?: return@forEach
            val lon = pair.substring(comma + 1).trim().toDoubleOrNull()?.div(1_000_000.0) ?: return@forEach
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                out.add(LatLon(lat, lon))
            }
        }
        return out
    }

    /**
     * 坐标展示：「31.2304, 121.4737」。
     *
     * @param lat 纬度
     * @param lon 经度
     * @return 展示文本
     */
    fun coordinates(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format(java.util.Locale.US, "%.4f°%s  %.4f°%s", abs(lat), ns, abs(lon), ew)
    }
}

/**
 * WGS-84 → GCJ-02（国测局坐标）纠偏。
 *
 * 高德地图的瓦片使用 GCJ-02，我们内置的地点和 OSRM 返回的路线是 WGS-84；
 * 在国内直接叠加会整体偏移 300-600 米。这里只对国内范围做转换，
 * 境外坐标原样返回（高德境外瓦片同样是 WGS-84）。
 */
object Gcj02 {

    /** 克拉索夫斯基椭球长半轴 */
    private const val A = 6378245.0

    /** 克拉索夫斯基椭球第一偏心率平方 */
    private const val EE = 0.00669342162296594323

    /**
     * 是否在中国境外（粗略矩形判断，用于决定要不要纠偏）。
     *
     * @param lat 纬度
     * @param lon 经度
     * @return true 表示境外，不需要纠偏
     */
    fun isOutOfChina(lat: Double, lon: Double): Boolean {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    /**
     * WGS-84 转 GCJ-02。
     *
     * @param lat WGS 纬度
     * @param lon WGS 经度
     * @return GCJ 坐标系下的点
     */
    fun fromWgs(lat: Double, lon: Double): LatLon {
        if (isOutOfChina(lat, lon)) return LatLon(lat, lon)
        val dLat = transformLat(lon - 105.0, lat - 35.0)
        val dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val mgLat = lat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val mgLon = lon + (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLon(mgLat, mgLon)
    }

    /**
     * GCJ-02 转 WGS-84：用四轮迭代逼近，误差在厘米级。
     *
     * @param lat GCJ 纬度
     * @param lon GCJ 经度
     * @return WGS 坐标系下的点
     */
    fun toWgs(lat: Double, lon: Double): LatLon {
        if (isOutOfChina(lat, lon)) return LatLon(lat, lon)
        var wgsLat = lat
        var wgsLon = lon
        repeat(4) {
            val back = fromWgs(wgsLat, wgsLon)
            wgsLat += lat - back.lat
            wgsLon += lon - back.lon
        }
        return LatLon(wgsLat, wgsLon)
    }

    /** 纬度方向的偏移多项式 */
    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    /** 经度方向的偏移多项式 */
    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
