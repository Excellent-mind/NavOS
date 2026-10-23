package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

/** Reference values come from first principles: on a 6,371 km sphere one
 * degree of latitude is 2*pi*6371000/360 = 111,194.93 m, and a degree of
 * longitude is that times cos(latitude). */
class GeoUtilsTest {
    private val metersPerDegree = 2 * PI * 6371000.0 / 360

    // An L-shaped route in Kuala Lumpur: ~1.1 km north, then ~1.1 km east.
    private val start = RoutePoint(3.1500, 101.7000)
    private val corner = RoutePoint(3.1600, 101.7000)
    private val end = RoutePoint(3.1600, 101.7100)
    private val route = listOf(start, corner, end)

    @Test
    fun distanceOfOneDegreeOfLatitude() {
        val d = GeoUtils.distanceMeters(RoutePoint(0.0, 0.0), RoutePoint(1.0, 0.0))
        assertEquals(metersPerDegree, d, 0.5)
    }

    @Test
    fun distanceToSamePointIsZero() {
        assertEquals(0.0, GeoUtils.distanceMeters(start, start), 1e-9)
    }

    @Test
    fun bearingsPointToTheCompassDirections() {
        val origin = RoutePoint(0.0, 0.0)
        assertEquals(0.0, GeoUtils.bearingDegrees(origin, RoutePoint(1.0, 0.0)), 1e-6)
        assertEquals(90.0, GeoUtils.bearingDegrees(origin, RoutePoint(0.0, 1.0)), 1e-6)
        assertEquals(180.0, GeoUtils.bearingDegrees(origin, RoutePoint(-1.0, 0.0)), 1e-6)
        assertEquals(270.0, GeoUtils.bearingDegrees(origin, RoutePoint(0.0, -1.0)), 1e-6)
    }

    @Test
    fun distanceToPolylineMeasuresSidewaysFromTheNearestLeg() {
        // 0.001 deg east of the middle of the northbound leg.
        val beside = RoutePoint(3.1550, 101.7010)
        val (distance, segment) = GeoUtils.distanceToPolyline(beside, route)
        val expected = 0.001 * metersPerDegree * cos(3.155 * PI / 180)
        assertEquals(expected, distance, 1.0)
        assertEquals(0, segment)
    }

    @Test
    fun snapProjectsOntoTheLineWithItsHeading() {
        val snap = GeoUtils.snapToPolyline(RoutePoint(3.1550, 101.7004), route)
        assertNotNull(snap)
        snap!!
        assertEquals(3.1550, snap.point.latitude, 1e-6)
        assertEquals(101.7000, snap.point.longitude, 1e-9)
        assertEquals(0.0, snap.bearing, 1e-6)
        assertEquals(0, snap.segmentIndex)
    }

    @Test
    fun snapIgnoresSegmentsBeforeStartIndex() {
        // Right next to the first leg, but the search starts at leg 1, so
        // the best it may return is a point on the eastbound leg.
        val snap = GeoUtils.snapToPolyline(RoutePoint(3.1550, 101.7001), route, startIndex = 1)
        assertEquals(1, snap!!.segmentIndex)
        assertEquals(90.0, snap.bearing, 0.1)
    }

    @Test
    fun snapNeedsAtLeastTwoPoints() {
        assertNull(GeoUtils.snapToPolyline(start, listOf(start)))
    }

    @Test
    fun bearingAheadLooksAroundTheCorner() {
        // 0.001 deg (~111 m) before the corner, looking 300 m ahead: the
        // look-ahead point is ~189 m along the eastbound leg, so the heading
        // is atan(189 / 111) from north, about 60 degrees.
        val snap = GeoUtils.snapToPolyline(RoutePoint(3.1590, 101.7000), route)!!
        val bearing = GeoUtils.bearingAhead(route, snap, aheadMeters = 300.0)
        assertTrue("expected ~60 degrees, was $bearing", bearing in 57.0..62.0)
    }

    @Test
    fun bearingAheadPastTheEndUsesTheLastStretch() {
        val snap = GeoUtils.snapToPolyline(RoutePoint(3.1600, 101.7095), route)!!
        val bearing = GeoUtils.bearingAhead(route, snap, aheadMeters = 5000.0)
        assertEquals(90.0, bearing, 0.1)
    }
}
