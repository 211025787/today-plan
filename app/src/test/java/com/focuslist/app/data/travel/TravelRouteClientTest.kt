package com.focuslist.app.data.travel

import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实路线请求地址拼装的单元测试。
 *
 * 响应解析依赖 Android 自带的 org.json，本地 JVM 测试里只有桩实现，
 * 所以这里只覆盖不依赖 Android 的 URL 规则。
 */
class TravelRouteClientTest {

    @Test
    fun buildUrl_usesLonLatOrderAndFullGeometry() {
        val url = TravelRouteClient.buildUrl(
            endpoint = "https://router.project-osrm.org",
            profile = "driving",
            from = LatLon(39.9042, 116.4074),
            to = LatLon(31.2304, 121.4737)
        )
        assertEquals(
            "https://router.project-osrm.org/route/v1/driving/" +
                "116.407400,39.904200;121.473700,31.230400" +
                "?overview=full&geometries=geojson&steps=false",
            url
        )
    }

    @Test
    fun buildUrl_normalizesEndpointSlashAndBlank() {
        val trimmed = TravelRouteClient.buildUrl(
            endpoint = " https://example.com/osrm/ ",
            profile = "foot",
            from = LatLon(30.0, 104.0),
            to = LatLon(31.0, 105.0)
        )
        assertTrue(trimmed.startsWith("https://example.com/osrm/route/v1/foot/"))

        val fallback = TravelRouteClient.buildUrl(
            endpoint = "",
            profile = "bike",
            from = LatLon(30.0, 104.0),
            to = LatLon(31.0, 105.0)
        )
        assertTrue(fallback.startsWith(TravelRouteClient.DEFAULT_ENDPOINT))
    }

    @Test
    fun profiles_busUsesDriving() {
        assertEquals("driving", TravelMode.profiles(TravelMode.BUS).first())
        assertEquals("driving", TravelMode.profiles(TravelMode.COACH).first())
        assertEquals("driving", TravelMode.profiles(TravelMode.CAR).first())
        assertEquals("bike", TravelMode.profiles(TravelMode.BIKE).first())
        assertEquals("foot", TravelMode.profiles(TravelMode.WALK).first())
        assertTrue(TravelMode.profiles(TravelMode.FLIGHT).isEmpty())
        assertTrue(TravelMode.profiles(TravelMode.SUBWAY).isEmpty())
    }
}
