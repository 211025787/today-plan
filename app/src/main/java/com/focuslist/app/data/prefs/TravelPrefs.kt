package com.focuslist.app.data.prefs

import android.content.Context

/**
 * 行程模块的轻量偏好：上次打开的行程、路线服务地址、引导是否看过。
 *
 * 这些值不属于核心数据，用 SharedPreferences 就够了，不必进备份。
 *
 * @property context 应用上下文
 */
class TravelPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("travel_prefs", Context.MODE_PRIVATE)

    /** 上次打开的行程 ID；0 表示还没有选择 */
    var activeTripId: Long
        get() = prefs.getLong(KEY_ACTIVE_TRIP, 0L)
        set(value) {
            prefs.edit().putLong(KEY_ACTIVE_TRIP, value).apply()
        }

    /** 路线服务根地址；空串表示用默认 OSRM */
    var routeEndpoint: String
        get() = prefs.getString(KEY_ROUTE_ENDPOINT, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_ROUTE_ENDPOINT, value).apply()
        }

    /** 地图手势提示是否已经看过 */
    var mapHintSeen: Boolean
        get() = prefs.getBoolean(KEY_MAP_HINT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MAP_HINT, value).apply()
        }

    /** 高德地图开放平台的 Web 服务 Key；空串表示未配置，POI 在线搜索不可用 */
    var amapKey: String
        get() = prefs.getString(KEY_AMAP_KEY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_AMAP_KEY, value).apply()
        }

    private companion object {
        /** 上次打开的行程 */
        const val KEY_ACTIVE_TRIP = "active_trip_id"

        /** 路线服务地址 */
        const val KEY_ROUTE_ENDPOINT = "route_endpoint"

        /** 地图提示 */
        const val KEY_MAP_HINT = "map_hint_seen"

        /** 高德地图 Key */
        const val KEY_AMAP_KEY = "amap_key"
    }
}
