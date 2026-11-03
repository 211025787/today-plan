package com.focuslist.app.util

import androidx.compose.ui.geometry.Offset
import com.focuslist.app.data.local.entity.TripLegEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行程几何映射测试：交通段折线来源与屏幕上的中点 / 点线距离。
 */
class TravelPlanGeometryTest {

    @Test
    fun legPoints_usesStoredPolylineWhenPresent() {
        val leg = TripLegEntity(
            tripId = 1,
            sortIndex = 0,
            mode = TravelMode.CAR.key,
            polyline = TravelGeo.encodePath(
                listOf(LatLon(30.0, 104.0), LatLon(30.1, 104.1), LatLon(30.2, 104.2))
            )
        )
        val points = TravelPlanGeometry.legPoints(leg, LatLon(30.0, 104.0), LatLon(30.2, 104.2))
        assertEquals(3, points.size)
        assertEquals(30.1, points[1].lat, 1e-6)
    }

    @Test
    fun legPoints_fallsBackToCurveForRail() {
        val leg = TripLegEntity(tripId = 1, sortIndex = 0, mode = TravelMode.HSR.key)
        val from = LatLon(30.0, 104.0)
        val to = LatLon(31.0, 105.0)
        val points = TravelPlanGeometry.legPoints(leg, from, to)
        assertEquals(29, points.size)
        assertEquals(from.lat, points.first().lat, 1e-6)
        assertEquals(from.lon, points.first().lon, 1e-6)
        assertEquals(to.lat, points.last().lat, 1e-6)
        assertEquals(to.lon, points.last().lon, 1e-6)
    }

    @Test
    fun legPoints_unknownModeStillConnects() {
        val leg = TripLegEntity(tripId = 1, sortIndex = 0, mode = "spaceship")
        val points = TravelPlanGeometry.legPoints(leg, LatLon(0.0, 0.0), LatLon(0.0, 1.0))
        assertTrue(points.size >= 2)
    }

    @Test
    fun screenMidpoint_halfwayAlongPolyline() {
        val midpoint = TravelPlanGeometry.screenMidpoint(
            listOf(Offset(0f, 0f), Offset(0f, 10f))
        )!!
        assertEquals(0f, midpoint.x, 1e-4f)
        assertEquals(5f, midpoint.y, 1e-4f)
    }

    @Test
    fun screenMidpoint_handlesEmptyAndSingle() {
        assertEquals(null, TravelPlanGeometry.screenMidpoint(emptyList()))
        assertEquals(Offset(3f, 4f), TravelPlanGeometry.screenMidpoint(listOf(Offset(3f, 4f))))
    }

    @Test
    fun distanceToPolyline_pointToSegment() {
        val line = listOf(Offset(0f, 0f), Offset(10f, 0f))
        assertEquals(5f, TravelPlanGeometry.distanceToPolyline(Offset(5f, 5f), line), 1e-4f)
        assertEquals(0f, TravelPlanGeometry.distanceToPolyline(Offset(5f, 0f), line), 1e-4f)
        assertEquals(7.071f, TravelPlanGeometry.distanceToPolyline(Offset(15f, 5f), line), 1e-3f)
    }

    @Test
    fun distanceToPolyline_emptyIsHuge() {
        assertEquals(Float.MAX_VALUE, TravelPlanGeometry.distanceToPolyline(Offset.Zero, emptyList()), 0f)
    }
}
