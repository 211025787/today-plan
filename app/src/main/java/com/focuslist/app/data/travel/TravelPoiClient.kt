package com.focuslist.app.data.travel

import com.focuslist.app.util.Gcj02
import com.focuslist.app.util.PlaceKind
import com.focuslist.app.util.TravelPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 高德地图 POI 文本搜索客户端。
 *
 * 用于在行程编辑时按名称查找景点、酒店、车站等具体地点，
 * 弥补内置目的地清单不够细的不足。需要用户在设置里填入高德 Web 服务 Key。
 *
 * 高德返回 GCJ-02 坐标，这里转成 WGS-84 再交给上层，与内置地点保持一致。
 *
 * @property keyProvider 高德 Key 提供者；空串时搜索直接返回空列表
 */
class TravelPoiClient(
    private val keyProvider: () -> String
) {

    /** 连接超时（毫秒） */
    private val connectTimeoutMs = 8_000

    /** 读取超时（毫秒） */
    private val readTimeoutMs = 12_000

    /**
     * 按关键词搜索 POI。
     *
     * @param keywords 关键词，如「天安门」「如家酒店」
     * @param city 限定城市名称，可空表示全国范围
     * @param limit 最多返回条数（1-25）
     * @return 搜索结果列表；未配置 Key 或请求失败时返回空列表
     */
    suspend fun search(keywords: String, city: String? = null, limit: Int = 20): List<TravelPlace> {
        val key = keyProvider().trim()
        if (key.isBlank() || keywords.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            request(keywords, city, limit.coerceIn(1, 25), key)
        }
    }

    /**
     * 发起一次高德 POI 搜索请求并解析。
     *
     * @param keywords 关键词
     * @param city 城市
     * @param limit 条数
     * @param key 高德 Key
     * @return 结果列表；失败返回空列表
     */
    private fun request(keywords: String, city: String?, limit: Int, key: String): List<TravelPlace> {
        val url = buildUrl(keywords, city, limit, key)
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseResponse(body)
        } catch (e: IOException) {
            emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    companion object {

        /** 高德 POI 文本搜索接口 */
        private const val ENDPOINT = "https://restapi.amap.com/v3/place/text"

        /**
         * 拼装请求地址。
         *
         * @param keywords 关键词
         * @param city 城市
         * @param limit 条数
         * @param key 高德 Key
         * @return 完整 URL
         */
        fun buildUrl(keywords: String, city: String?, limit: Int, key: String): String {
            val encodedKeywords = URLEncoder.encode(keywords, "UTF-8")
            val builder = StringBuilder(ENDPOINT)
                .append("?keywords=").append(encodedKeywords)
                .append("&key=").append(key)
                .append("&offset=").append(limit)
                .append("&page=1")
                .append("&extensions=base")
            if (!city.isNullOrBlank()) {
                builder.append("&city=").append(URLEncoder.encode(city, "UTF-8"))
            }
            return builder.toString()
        }

        /**
         * 解析高德 POI 响应。
         *
         * @param body 响应 JSON
         * @return 地点列表；无可用结果时返回空列表
         */
        fun parseResponse(body: String): List<TravelPlace> {
            return try {
                val json = JSONObject(body)
                if (json.optString("status") != "1") return emptyList()
                val pois = json.optJSONArray("pois") ?: return emptyList()
                val result = ArrayList<TravelPlace>(pois.length())
                for (i in 0 until pois.length()) {
                    val poi = pois.optJSONObject(i) ?: continue
                    val name = poi.optString("name").trim()
                    if (name.isEmpty()) continue
                    val location = poi.optString("location").trim()
                    if (location.isEmpty() || !location.contains(",")) continue
                    val parts = location.split(",")
                    val lon = parts[0].toDoubleOrNull() ?: continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    // 高德返回 GCJ-02，转 WGS-84 与内置地点一致
                    val wgs = Gcj02.toWgs(lat, lon)
                    val cityName = poi.optString("cityname").trim()
                    val adName = poi.optString("adname").trim()
                    val region = when {
                        cityName.isNotEmpty() && adName.isNotEmpty() && cityName != adName -> "$cityName · $adName"
                        cityName.isNotEmpty() -> cityName
                        adName.isNotEmpty() -> adName
                        else -> "高德"
                    }
                    result.add(
                        TravelPlace(
                            name = name,
                            region = region,
                            lat = wgs.lat,
                            lon = wgs.lon,
                            kind = guessKind(poi.optString("type"))
                        )
                    )
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * 根据高德类型标签猜测地点类型。
         *
         * @param type 高德 type 字段，如「风景名胜;风景名胜相关」
         * @return 对应的地点类型
         */
        private fun guessKind(type: String): PlaceKind {
            if (type.contains("岛屿")) return PlaceKind.ISLAND
            return PlaceKind.SCENIC
        }
    }
}