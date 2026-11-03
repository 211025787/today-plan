package com.focuslist.app.util

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import java.util.Locale

/**
 * 行程文案格式化：时长、停留、距离、总览。
 *
 * 全应用统一口径：数字与单位之间留一个空格（`4 小时 30 分`），
 * 超过一天的时长改用「天 + 小时」，不再堆分钟，避免地图标签过长。
 */
object TravelFormat {

    /**
     * 时长文案。
     *
     * @param minutes 分钟数，负数按 0 处理
     * @return 例如「45 分钟」「2 小时」「4 小时 30 分」「1 天 6 小时」
     */
    fun duration(minutes: Int): String {
        val total = minutes.coerceAtLeast(0)
        if (total == 0) return "0 分钟"
        if (total < 60) return "$total 分钟"
        val days = total / (24 * 60)
        if (days > 0) {
            var hours = (total % (24 * 60) / 60.0).roundToInt()
            var realDays = days
            if (hours >= 24) {
                realDays += 1
                hours = 0
            }
            return if (hours == 0) "$realDays 天" else "$realDays 天 $hours 小时"
        }
        val hours = total / 60
        val rest = total % 60
        return if (rest == 0) "$hours 小时" else "$hours 小时 $rest 分"
    }

    /**
     * 停留文案；0 表示还没安排。
     *
     * @param minutes 停留分钟数
     * @return 「停留 2 天」或「未安排停留」
     */
    fun stay(minutes: Int): String {
        return if (minutes <= 0) "未安排停留" else "停留 ${duration(minutes)}"
    }

    /**
     * 停留的短文案，用于地图标签。
     *
     * @param minutes 停留分钟数
     * @return 「2 天」「3 小时」或「未安排」
     */
    fun stayShort(minutes: Int): String {
        return if (minutes <= 0) "未安排" else duration(minutes)
    }

    /**
     * 距离文案。
     *
     * @param meters 距离（米），小于等于 0 时返回「—」
     * @return 例如「820 米」「12.4 公里」「1,240 公里」
     */
    fun distance(meters: Long): String {
        if (meters <= 0L) return "—"
        if (meters < 1_000L) return "$meters 米"
        if (meters < 100_000L) {
            val km = meters / 1000.0
            return String.format(Locale.US, "%.1f 公里", km)
        }
        return "${grouped((meters / 1000.0).roundToLong())} 公里"
    }

    /**
     * 行程总天数文案。
     *
     * @param totalMinutes 停留 + 在途总分钟数
     * @param hasStops 是否至少有一个地点（没有地点时不显示天数）
     * @return 例如「约 9 天」；没有地点返回空串
     */
    fun days(totalMinutes: Int, hasStops: Boolean): String {
        if (!hasStops) return ""
        val days = ceil(totalMinutes.coerceAtLeast(0) / 1440.0).toInt().coerceAtLeast(1)
        return "约 $days 天"
    }

    /**
     * 千分位分组，避免依赖 Locale 的小数点差异。
     *
     * @param value 非负整数
     * @return 例如 1240 → "1,240"
     */
    fun grouped(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        val sb = StringBuilder(raw.length + raw.length / 3)
        var count = 0
        for (i in raw.indices.reversed()) {
            sb.append(raw[i])
            count++
            if (count % 3 == 0 && i != 0) sb.append(',')
        }
        return sb.reverse().toString()
    }

    /**
     * 站点信息的一行摘要：有站点就展示「北京南 → 上海虹桥」，否则退回家次。
     *
     * @param fromStation 出发站
     * @param toStation 到达站
     * @param serviceNo 班次 / 线路
     * @return 摘要文本，没有信息时为空串
     */
    fun transfer(fromStation: String, toStation: String, serviceNo: String): String {
        val route = when {
            fromStation.isNotBlank() && toStation.isNotBlank() -> "$fromStation → $toStation"
            fromStation.isNotBlank() -> "从 $fromStation 出发"
            toStation.isNotBlank() -> "到达 $toStation"
            else -> ""
        }
        return listOf(route, serviceNo.trim()).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

/**
 * 一条行程的汇总数据。
 *
 * @property stopCount 地点数
 * @property stayMinutes 各地点停留时长总和（分钟）
 * @property travelMinutes 各段交通时长总和（分钟）
 * @property distanceMeters 已知路线里程总和（米），没有轨迹的段不计
 * @property approxDays 按每天 24 小时折算的行程天数，至少 1 天
 */
data class TravelSummary(
    val stopCount: Int,
    val stayMinutes: Int,
    val travelMinutes: Int,
    val distanceMeters: Long,
    val approxDays: Int
) {
    /** 停留 + 在途总时长 */
    val totalMinutes: Int get() = stayMinutes + travelMinutes

    /** 是否有内容可展示 */
    val isEmpty: Boolean get() = stopCount == 0
}

/**
 * 行程汇总计算：把停留与交通时长合成一句「几天、几站、多少公里」。
 */
object TravelSummaries {

    /**
     * 汇总一条行程。
     *
     * @param stayMinutes 每个地点的停留分钟数
     * @param legMinutes 每段交通的分钟数
     * @param distances 每段已知里程（米），未知传 0
     * @return 汇总结果
     */
    fun of(stayMinutes: List<Int>, legMinutes: List<Int>, distances: List<Long>): TravelSummary {
        val stay = stayMinutes.sumOf { it.coerceAtLeast(0) }
        val travel = legMinutes.sumOf { it.coerceAtLeast(0) }
        val distance = distances.sumOf { it.coerceAtLeast(0L) }
        // 停留与在途是并行的两部分：2 天停留 + 1 小时车程仍是 2 天，
        // 但 2 天停留 + 30 小时火车会跨到 3 天，所以取两者天数的较大值。
        val days = if (stayMinutes.isEmpty()) {
            0
        } else {
            maxOf(
                1,
                ceil(stay / 1440.0).toInt(),
                ceil(travel / 1440.0).toInt()
            )
        }
        return TravelSummary(
            stopCount = stayMinutes.size,
            stayMinutes = stay,
            travelMinutes = travel,
            distanceMeters = distance,
            approxDays = days
        )
    }
}
