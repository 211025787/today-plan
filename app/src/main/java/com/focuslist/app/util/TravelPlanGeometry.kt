package com.focuslist.app.util

import com.focuslist.app.data.local.entity.TripLegEntity
import kotlin.math.hypot

/**
 * 行程几何：把一段交通变成地图上的折线。
 *
 * 有真实道路折线就直接用；没有就按交通方式画一条平滑弧线，
 * 飞机弧度最大、步行几乎是一条直线，这样即使没联网也能一眼分出交通方式。
 */
object TravelPlanGeometry {

    /**
     * 一段交通的地图折线（WGS-84）。
     *
     * @param leg 交通实体
     * @param from 起点（上一站）
     * @param to 终点（下一站）
     * @return 折线坐标；起终点缺失时为空
     */
    fun legPoints(leg: TripLegEntity, from: LatLon, to: LatLon): List<LatLon> {
        val decoded = TravelGeo.decodePath(leg.polyline)
        if (decoded.size >= 2) return decoded
        val mode = TravelMode.of(leg.mode)
        val samples = when (mode) {
            TravelMode.FLIGHT -> 48
            TravelMode.CRUISE -> 40
            else -> 28
        }
        return TravelGeo.curve(from, to, mode.bend, samples)
    }

    /**
     * 折线的屏幕中点：按长度取一半，标签才会落在真正的中点。
     *
     * @param points 屏幕坐标点
     * @return 中点；空列表为 null
     */
    fun screenMidpoint(points: List<androidx.compose.ui.geometry.Offset>): androidx.compose.ui.geometry.Offset? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points.first()
        var total = 0f
        for (i in 0 until points.size - 1) {
            total += (points[i + 1] - points[i]).getDistance()
        }
        if (total <= 0f) return points.first()
        var target = total / 2f
        for (i in 0 until points.size - 1) {
            val segment = (points[i + 1] - points[i]).getDistance()
            if (target <= segment) {
                val t = if (segment <= 0f) 0f else target / segment
                return androidx.compose.ui.geometry.Offset(
                    points[i].x + (points[i + 1].x - points[i].x) * t,
                    points[i].y + (points[i + 1].y - points[i].y) * t
                )
            }
            target -= segment
        }
        return points.last()
    }

    /**
     * 点到折线的最短距离（屏幕像素），用于点击线段打开交通编辑。
     *
     * @param point 点击位置
     * @param line 折线
     * @return 最短距离；空折线返回 [Float.MAX_VALUE]
     */
    fun distanceToPolyline(
        point: androidx.compose.ui.geometry.Offset,
        line: List<androidx.compose.ui.geometry.Offset>
    ): Float {
        if (line.isEmpty()) return Float.MAX_VALUE
        if (line.size == 1) return (point - line.first()).getDistance()
        var best = Float.MAX_VALUE
        for (i in 0 until line.size - 1) {
            best = minOf(best, distanceToSegment(point, line[i], line[i + 1]))
        }
        return best
    }

    /**
     * 点到线段的距离。
     *
     * @param p 点
     * @param a 线段起点
     * @param b 线段终点
     * @return 距离（像素）
     */
    private fun distanceToSegment(
        p: androidx.compose.ui.geometry.Offset,
        a: androidx.compose.ui.geometry.Offset,
        b: androidx.compose.ui.geometry.Offset
    ): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSq = abx * abx + aby * aby
        if (lengthSq <= 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSq).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + abx * t), p.y - (a.y + aby * t))
    }
}
