package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 行程几何纯逻辑测试：球面距离、墨卡托投影、GCJ 纠偏、弧线、抽稀与折线编解码。
 */
class TravelGeoTest {

    @Test
    fun distance_beijingToShanghai() {
        val d = TravelGeo.distanceMeters(
            LatLon(39.9042, 116.4074),
            LatLon(31.2304, 121.4737)
        )
        assertEquals(1_068_506.0, d, 2_000.0)
    }

    @Test
    fun distance_samePointIsZero() {
        val p = LatLon(30.5728, 104.0668)
        assertEquals(0.0, TravelGeo.distanceMeters(p, p), 1e-6)
    }

    @Test
    fun distance_isSymmetric() {
        val a = LatLon(39.9, 116.4)
        val b = LatLon(31.2, 121.5)
        assertEquals(TravelGeo.distanceMeters(a, b), TravelGeo.distanceMeters(b, a), 1e-6)
    }

    @Test
    fun world_projectRoundTrip() {
        val points = listOf(
            LatLon(39.9042, 116.4074),
            LatLon(-33.8688, 151.2093),
            LatLon(0.0, 0.0),
            LatLon(64.1466, -21.9426)
        )
        points.forEach { p ->
            val x = TravelGeo.worldX(p.lon)
            val y = TravelGeo.worldY(p.lat)
            val back = TravelGeo.fromWorld(x, y)
            assertEquals(p.lat, back.lat, 1e-6)
            assertEquals(p.lon, back.lon, 1e-6)
        }
    }

    @Test
    fun worldY_equatorIsHalf() {
        assertEquals(0.5, TravelGeo.worldY(0.0), 1e-9)
    }

    @Test
    fun world_coordinatesAreNormalized() {
        assertEquals(0.0, TravelGeo.worldX(-180.0), 1e-9)
        assertEquals(1.0, TravelGeo.worldX(180.0), 1e-9)
        assertEquals(0.5, TravelGeo.worldX(0.0), 1e-9)
    }

    @Test
    fun metersPerPixel_atEquatorAndZoomZero() {
        assertEquals(156_543.03, TravelGeo.metersPerPixel(0.0, 0f), 0.1)
    }

    @Test
    fun metersPerPixel_halvesEachZoomLevel() {
        val z0 = TravelGeo.metersPerPixel(0.0, 0f)
        val z1 = TravelGeo.metersPerPixel(0.0, 1f)
        assertEquals(z0 / 2.0, z1, 1e-6)
    }

    @Test
    fun gcj_shiftsBeijingIntoMarsCoordinates() {
        val shifted = Gcj02.fromWgs(39.9042, 116.4074)
        assertEquals(39.9056, shifted.lat, 0.0005)
        assertEquals(116.4136, shifted.lon, 0.0005)
        // 国内偏移在 300-700 米量级
        val offset = TravelGeo.distanceMeters(LatLon(39.9042, 116.4074), shifted)
        assertTrue("offset=$offset", offset in 200.0..900.0)
    }

    @Test
    fun gcj_inverseRoundTrip() {
        val gcj = Gcj02.fromWgs(30.5728, 104.0668)
        val wgs = Gcj02.toWgs(gcj.lat, gcj.lon)
        val error = TravelGeo.distanceMeters(LatLon(30.5728, 104.0668), wgs)
        assertTrue("error=$error", error < 1.0)
    }

    @Test
    fun gcj_leavesOverseasUntouched() {
        val gcj = Gcj02.fromWgs(35.6762, 139.6503)
        assertEquals(35.6762, gcj.lat, 1e-12)
        assertEquals(139.6503, gcj.lon, 1e-12)
        assertTrue(Gcj02.isOutOfChina(35.6762, 139.6503))
        assertTrue(Gcj02.isOutOfChina(48.8566, 2.3522))
        assertTrue(!Gcj02.isOutOfChina(39.9042, 116.4074))
    }

    @Test
    fun fit_singlePointUsesCityZoom() {
        val fit = TravelGeo.fit(
            points = listOf(LatLon(39.9042, 116.4074)),
            widthPx = 1080f,
            heightPx = 800f,
            paddingPx = 0f
        )
        assertNotNull(fit)
        assertEquals(11f, fit!!.zoom, 0.01f)
        assertEquals(39.9042, fit.center.lat, 0.01)
        assertEquals(116.4074, fit.center.lon, 0.01)
    }

    @Test
    fun fit_zoomsOutForDistantPoints() {
        val fit = TravelGeo.fit(
            points = listOf(LatLon(39.9042, 116.4074), LatLon(31.2304, 121.4737)),
            widthPx = 1080f,
            heightPx = 800f,
            paddingPx = 80f
        )
        assertNotNull(fit)
        assertTrue("zoom=${fit!!.zoom}", fit.zoom in 4f..8f)
        assertTrue(fit.center.lat in 31.0..40.0)
        assertTrue(fit.center.lon in 116.0..121.5)
    }

    @Test
    fun fit_returnsNullForEmptyPoints() {
        assertEquals(null, TravelGeo.fit(emptyList(), 100f, 100f, 0f))
    }

    @Test
    fun bounds_containsAllPoints() {
        val bounds = TravelGeo.boundsOf(
            listOf(LatLon(30.0, 100.0), LatLon(40.0, 120.0), LatLon(35.0, 110.0))
        )!!
        assertEquals(30.0, bounds.minLat, 1e-9)
        assertEquals(40.0, bounds.maxLat, 1e-9)
        assertEquals(100.0, bounds.minLon, 1e-9)
        assertEquals(120.0, bounds.maxLon, 1e-9)
        assertEquals(35.0, bounds.centerLat, 1e-9)
        assertEquals(110.0, bounds.centerLon, 1e-9)
    }

    @Test
    fun curve_keepsEndpointsAndBends() {
        val from = LatLon(30.0, 100.0)
        val to = LatLon(35.0, 110.0)
        val curve = TravelGeo.curve(from, to, 0.18, 24)
        assertEquals(25, curve.size)
        assertEquals(from.lat, curve.first().lat, 1e-6)
        assertEquals(from.lon, curve.first().lon, 1e-6)
        assertEquals(to.lat, curve.last().lat, 1e-6)
        assertEquals(to.lon, curve.last().lon, 1e-6)
        // 中点应该离开直线
        val middle = TravelGeo.pointAtFraction(curve, 0.5)!!
        val straight = TravelGeo.pointAtFraction(listOf(from, to), 0.5)!!
        assertTrue(TravelGeo.distanceMeters(middle, straight) > 1_000.0)
    }

    @Test
    fun curve_zeroBendIsStraight() {
        val curve = TravelGeo.curve(LatLon(30.0, 100.0), LatLon(30.0, 110.0), 0.0)
        assertEquals(2, curve.size)
    }

    @Test
    fun simplify_removesCollinearPoints() {
        val line = (0..10).map { LatLon(30.0, 100.0 + it * 0.01) }
        val simplified = TravelGeo.simplify(line, 20.0)
        assertEquals(2, simplified.size)
    }

    @Test
    fun simplify_keepsCorners() {
        val corner = listOf(
            LatLon(30.0, 100.0),
            LatLon(30.5, 100.0),
            LatLon(30.5, 100.5)
        )
        val simplified = TravelGeo.simplify(corner, 10.0)
        assertEquals(3, simplified.size)
    }

    @Test
    fun simplify_shortInputUntouched() {
        val points = listOf(LatLon(30.0, 100.0), LatLon(31.0, 101.0))
        assertEquals(points, TravelGeo.simplify(points, 100.0))
    }

    @Test
    fun encodeDecode_roundTrip() {
        val points = listOf(
            LatLon(30.123456, 104.654321),
            LatLon(31.000001, 105.999999),
            LatLon(29.5, 103.25)
        )
        val encoded = TravelGeo.encodePath(points)
        assertTrue(encoded.startsWith("30123456,104654321;"))
        val decoded = TravelGeo.decodePath(encoded)
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (a, b) ->
            assertTrue(abs(a.lat - b.lat) < 1e-6)
            assertTrue(abs(a.lon - b.lon) < 1e-6)
        }
    }

    @Test
    fun decode_skipsGarbage() {
        val decoded = TravelGeo.decodePath(
            "30000000,104000000;oops;31000000,105000000;999000000,999000000;;"
        )
        assertEquals(2, decoded.size)
        assertEquals(30.0, decoded[0].lat, 1e-9)
        assertEquals(104.0, decoded[0].lon, 1e-9)
    }

    @Test
    fun decode_emptyReturnsEmpty() {
        assertTrue(TravelGeo.decodePath("").isEmpty())
        assertTrue(TravelGeo.decodePath("   ").isEmpty())
    }

    @Test
    fun pointAtFraction_halfway() {
        val points = listOf(LatLon(0.0, 0.0), LatLon(0.0, 2.0))
        val middle = TravelGeo.pointAtFraction(points, 0.5)!!
        assertEquals(0.0, middle.lat, 1e-6)
        assertEquals(1.0, middle.lon, 1e-6)
    }

    @Test
    fun pointAtFraction_clampsAndHandlesSinglePoint() {
        val single = listOf(LatLon(30.0, 100.0))
        assertEquals(single.first(), TravelGeo.pointAtFraction(single, 0.7))
        val pair = listOf(LatLon(0.0, 0.0), LatLon(0.0, 1.0))
        assertEquals(0.0, TravelGeo.pointAtFraction(pair, -1.0)!!.lon, 1e-6)
        assertEquals(1.0, TravelGeo.pointAtFraction(pair, 2.0)!!.lon, 1e-6)
    }

    @Test
    fun coordinates_formatsHemispheres() {
        assertEquals("39.9042°N  116.4074°E", TravelGeo.coordinates(39.9042, 116.4074))
        assertEquals("33.8688°S  151.2093°E", TravelGeo.coordinates(-33.8688, 151.2093))
        assertEquals("40.7128°N  74.0060°W", TravelGeo.coordinates(40.7128, -74.0060))
    }
}
