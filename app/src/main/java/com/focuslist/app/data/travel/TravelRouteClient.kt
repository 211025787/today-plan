package com.focuslist.app.data.travel

import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 在线规划回来的一条真实路线。
 *
 * @property points 道路折线（WGS-84，已抽稀）
 * @property distanceMeters 里程（米）
 * @property durationMinutes 预计用时（分钟）
 */
data class TravelRouteResult(
    val points: List<LatLon>,
    val distanceMeters: Long,
    val durationMinutes: Int
)

/**
 * 真实道路路线服务（OSRM）。
 *
 * 只用于公交、大巴、驾车、骑行、步行这五种「有道路」的方式；
 * 飞机 / 高铁 / 火车 / 地铁 / 邮轮没有公开的免费轨迹接口，地图按弧线绘制。
 *
 * 默认走 OSRM 官方演示服务，无需 Key。请求失败（断网、被墙、限流）时返回 null，
 * 调用方保留原来的弧线示意，不影响继续编辑行程。
 *
 * @property endpointProvider 路线服务根地址，便于以后换成自建 OSRM
 */
class TravelRouteClient(
    private val endpointProvider: () -> String = { DEFAULT_ENDPOINT }
) {

    /** 单次请求的连接 / 读取超时（毫秒） */
    private val connectTimeoutMs = 8_000

    /** 读取超时略长，长距离路线计算会慢一些 */
    private val readTimeoutMs = 14_000

    /**
     * 规划一条真实路线。
     *
     * @param mode 交通方式（内部会取对应的 profile）
     * @param from 起点（WGS-84）
     * @param to 终点（WGS-84）
     * @return 路线结果；不支持或失败时 null
     */
    suspend fun route(mode: TravelMode, from: LatLon, to: LatLon): TravelRouteResult? {
        val profiles = TravelMode.profiles(mode)
        if (profiles.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            profiles.firstNotNullOfOrNull { profile ->
                request(profile, from, to)
            }
        }
    }

    /**
     * 发起一次 OSRM 请求并解析。
     *
     * @param profile 出行方式
     * @param from 起点
     * @param to 终点
     * @return 路线结果；失败返回 null
     */
    private fun request(profile: String, from: LatLon, to: LatLon): TravelRouteResult? {
        val url = buildUrl(endpointProvider(), profile, from, to)
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            // OSRM 演示服务要求一个能识别身份的 UA，默认的 Java/okhttp UA 会被限流
            connection.setRequestProperty("User-Agent", "FocusList/1.0 (personal travel planner)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseResponse(body)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {

        /** 默认路线服务（OSRM 官方演示服务，无需 Key） */
        const val DEFAULT_ENDPOINT = "https://router.project-osrm.org"

        /**
         * 拼装 OSRM 路线请求地址。
         *
         * @param endpoint 服务根地址
         * @param profile 出行方式
         * @param from 起点
         * @param to 终点
         * @return 完整 URL
         */
        fun buildUrl(endpoint: String, profile: String, from: LatLon, to: LatLon): String {
            val base = endpoint.trim().trimEnd('/').ifBlank { DEFAULT_ENDPOINT }
            val coords = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                from.lon, from.lat, to.lon, to.lat
            )
            return "$base/route/v1/$profile/$coords?overview=full&geometries=geojson&steps=false"
        }

        /**
         * 解析 OSRM 响应；只取第一条路线。
         *
         * @param body 响应 JSON
         * @return 路线结果；没有可用路线时 null
         */
        fun parseResponse(body: String): TravelRouteResult? {
            return try {
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return null
                val route = json.optJSONArray("routes")?.optJSONObject(0) ?: return null
                val coordinates = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
                if (coordinates.length() < 2) return null
                val points = ArrayList<LatLon>(coordinates.length())
                for (i in 0 until coordinates.length()) {
                    val pair = coordinates.optJSONArray(i) ?: continue
                    val lon = pair.optDouble(0, Double.NaN)
                    val lat = pair.optDouble(1, Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) points.add(LatLon(lat, lon))
                }
                if (points.size < 2) return null
                // 地图最多看到街道级，20 米的偏离肉眼不可见，抽稀后数据库更轻
                val simplified = TravelGeo.simplify(points, 20.0)
                val distance = route.optDouble("distance", 0.0)
                val duration = route.optDouble("duration", 0.0)
                TravelRouteResult(
                    points = simplified,
                    distanceMeters = distance.roundToLong().coerceAtLeast(0L),
                    durationMinutes = ceil(duration / 60.0).toInt().coerceAtLeast(1)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
