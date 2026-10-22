package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class RouteTrackTest {
    private val metersPerDegree = 2 * PI * 6371000.0 / 360

    // ~1.1 km north, then ~1.1 km east (same L as GeoUtilsTest).
    private val start = RoutePoint(3.1500, 101.7000)
    private val corner = RoutePoint(3.1600, 101.7000)
    private val end = RoutePoint(3.1600, 101.7100)
    private val track = RouteTrack(listOf(start, corner, end))

    private val northLeg = 0.01 * metersPerDegree
    private val eastLeg = 0.01 * metersPerDegree * cos(3.16 * PI / 180)

    @Test
    fun lengthIsTheSumOfTheLegs() {
        assertEquals(northLeg + eastLeg, track.length, 1.0)
    }

    @Test
    fun pointAtTheEndsAndClampsBeyondThem() {
        assertPoint(start, track.pointAt(0.0))
        assertPoint(end, track.pointAt(track.length))
        assertPoint(start, track.pointAt(-50.0))
        assertPoint(end, track.pointAt(track.length + 500.0))
    }

    @Test
    fun pointAtFollowsTheBendInsteadOfCuttingIt() {
        // Halfway along the second leg must be ON the eastbound road
        // (latitude of the corner), never inside the bend.
        val p = track.pointAt(northLeg + eastLeg / 2)
        assertEquals(3.1600, p.latitude, 1e-6)
        assertEquals(101.7050, p.longitude, 1e-5)
    }

    @Test
    fun progressOfASnappedFixIsDistanceFromTheStart() {
        val snap = GeoUtils.snapToPolyline(RoutePoint(3.1550, 101.7003), track.source)!!
        assertEquals(northLeg / 2, track.progressOf(snap), 1.0)
    }

    @Test
    fun bearingAtMatchesEachLeg() {
        assertEquals(0.0, track.bearingAt(100.0, 50.0), 0.1)
        assertEquals(90.0, track.bearingAt(northLeg + 300.0, 50.0), 0.1)
    }

    @Test
    fun bearingAtTheVeryEndKeepsTheFinalHeading() {
        assertEquals(90.0, track.bearingAt(track.length, 50.0), 0.1)
    }

    private fun assertPoint(
        expected: RoutePoint,
        actual: RoutePoint,
    ) {
        assertEquals(expected.latitude, actual.latitude, 1e-9)
        assertEquals(expected.longitude, actual.longitude, 1e-9)
    }
}
