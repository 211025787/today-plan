package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置目的地、站点建议、地铁路线与模板数据的单元测试。
 */
class TravelPlacesTest {

    @Test
    fun all_hasEnoughPlaces() {
        assertTrue("places=${TravelPlaces.all.size}", TravelPlaces.all.size >= 200)
    }

    @Test
    fun all_namesAreUnique() {
        val duplicates = TravelPlaces.all.groupBy { it.name }.filterValues { it.size > 1 }.keys
        assertTrue("duplicates=$duplicates", duplicates.isEmpty())
    }

    @Test
    fun all_coordinatesAreValid() {
        TravelPlaces.all.forEach { place ->
            assertTrue("${place.name} lat", place.lat in -90.0..90.0)
            assertTrue("${place.name} lon", place.lon in -180.0..180.0)
        }
    }

    @Test
    fun search_exactNameWins() {
        assertEquals("成都", TravelPlaces.search("成都").first().name)
        assertEquals("京都", TravelPlaces.search("京都").first().name)
    }

    @Test
    fun search_acceptsAdministrativeSuffix() {
        assertEquals("成都", TravelPlaces.search("成都市").first().name)
        assertEquals("婺源", TravelPlaces.search("婺源县").first().name)
    }

    @Test
    fun search_matchesAlias() {
        assertEquals("稻城亚丁", TravelPlaces.search("亚丁").first().name)
        assertEquals("呼伦贝尔", TravelPlaces.search("海拉尔").first().name)
    }

    @Test
    fun search_matchesRegion() {
        val results = TravelPlaces.search("四川")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name == "成都" })
    }

    @Test
    fun search_isCaseInsensitiveForLatin() {
        assertEquals("东京", TravelPlaces.search("东京").first().name)
        assertTrue(TravelPlaces.search("zzzz").isEmpty())
    }

    @Test
    fun search_emptyQueryReturnsPopular() {
        val popular = TravelPlaces.search("")
        assertTrue(popular.isNotEmpty())
        assertEquals(TravelPlaces.popular().size, popular.size)
    }

    @Test
    fun search_respectsLimit() {
        assertTrue(TravelPlaces.search("山", 5).size <= 5)
    }

    @Test
    fun popular_allResolvable() {
        val popular = TravelPlaces.popular()
        assertTrue(popular.size >= 20)
        popular.forEach { assertNotNull(TravelPlaces.byName(it.name)) }
    }

    @Test
    fun byName_trimsAndMissesGracefully() {
        assertNotNull(TravelPlaces.byName(" 成都 "))
        assertNull(TravelPlaces.byName("不存在的城市"))
    }

    @Test
    fun nearest_findsClosestPlace() {
        val nearest = TravelPlaces.nearest(39.9042, 116.4074)
        assertEquals("北京", nearest?.name)
        assertNull(TravelPlaces.nearest(0.0, 0.0, 10_000.0))
    }

    @Test
    fun cityOf_recognizesCities() {
        assertEquals("成都", TravelPlaces.cityOf("成都", "四川"))
        assertEquals("成都", TravelPlaces.cityOf("成都市", "四川"))
    }

    @Test
    fun cityOf_scenicFallsBackToNearestCity() {
        val city = TravelPlaces.cityOf("四姑娘山", "四川 · 阿坝")
        assertTrue("city=$city", city == "都江堰" || city == "成都")
    }

    @Test
    fun stationSuggestions_highSpeedRailForChengdu() {
        val hints = TravelPlaces.stationSuggestions("成都", "四川", TravelMode.HSR)
        assertTrue(hints.contains("成都东站"))
        assertTrue(hints.size <= 6)
    }

    @Test
    fun stationSuggestions_flightUsesAirports() {
        val hints = TravelPlaces.stationSuggestions("成都", "四川", TravelMode.FLIGHT)
        assertTrue(hints.any { it.contains("机场") })
        assertTrue(hints.none { it.endsWith("站") })
    }

    @Test
    fun stationSuggestions_emptyForScenicAndNoStationModes() {
        assertTrue(TravelPlaces.stationSuggestions("四姑娘山", "四川 · 阿坝", TravelMode.HSR).isEmpty())
        assertTrue(TravelPlaces.stationSuggestions("成都", "四川", TravelMode.WALK).isEmpty())
        assertTrue(TravelPlaces.stationSuggestions("成都", "四川", TravelMode.CAR).isEmpty())
    }

    @Test
    fun stationSuggestions_genericForCityWithoutCuratedData() {
        val hints = TravelPlaces.stationSuggestions("洛阳", "河南", TravelMode.HSR)
        assertTrue(hints.any { it.startsWith("洛阳") })
    }

    @Test
    fun subwayLines_knownCities() {
        assertTrue(TravelPlaces.subwayLines("成都", "四川").contains("1号线"))
        assertTrue(TravelPlaces.subwayLines("北京", "北京").contains("10号线"))
        assertTrue(TravelPlaces.subwayLines("丽江", "云南").isEmpty())
    }

    @Test
    fun routes_isInChina() {
        assertTrue(TravelPlaces.isInChina(39.9042, 116.4074))
        assertTrue(!TravelPlaces.isInChina(35.6762, 139.6503))
    }

    @Test
    fun templates_allPlacesResolve() {
        assertTrue(TravelTemplates.all.size >= 4)
        TravelTemplates.all.forEach { template ->
            assertTrue(template.stops.size >= 3)
            template.stops.forEach { stop ->
                assertNotNull("${template.title}: ${stop.place}", TravelPlaces.byName(stop.place))
            }
        }
    }

    @Test
    fun templates_firstStopHasNoArrivalAndOthersDo() {
        TravelTemplates.all.forEach { template ->
            assertNull(template.stops.first().mode)
            template.stops.drop(1).forEach { stop ->
                assertNotNull("${template.title}: ${stop.place}", stop.mode)
                assertTrue(stop.travelMinutes > 0)
            }
        }
    }

    @Test
    fun templates_haveUniqueKeysAndColors() {
        val keys = TravelTemplates.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        TravelTemplates.all.forEach { template ->
            assertTrue(template.colorHex.startsWith("#"))
            assertTrue(template.emoji.isNotBlank())
        }
        assertNotNull(TravelTemplates.byKey("jiangnan"))
        assertNull(TravelTemplates.byKey("nope"))
    }

    @Test
    fun travelModes_roundTripUnknownFallsBack() {
        TravelMode.entries.forEach { assertEquals(it, TravelMode.of(it.key)) }
        assertEquals(TravelMode.OTHER, TravelMode.of("spaceship"))
        assertEquals(TravelMode.OTHER, TravelMode.of(null))
    }

    @Test
    fun travelModes_profilesAndFields() {
        assertEquals(listOf("driving", "car"), TravelMode.profiles(TravelMode.BUS))
        assertEquals(listOf("bike", "cycling"), TravelMode.profiles(TravelMode.BIKE))
        assertEquals(listOf("foot", "walking"), TravelMode.profiles(TravelMode.WALK))
        assertTrue(TravelMode.profiles(TravelMode.HSR).isEmpty())
        assertTrue(TravelMode.HSR.needsStations)
        assertTrue(TravelMode.BUS.isRealRoute)
        assertTrue(!TravelMode.FLIGHT.isRealRoute)
        assertTrue(TravelMode.SUBWAY.lineMode)
        assertEquals(11, TravelMode.pickerOrder.size)
    }
}
