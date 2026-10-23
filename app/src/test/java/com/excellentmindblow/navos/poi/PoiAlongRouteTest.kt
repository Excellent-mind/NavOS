package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PoiAlongRouteTest {
    private val start = RoutePoint(3.15, 101.70)
    private val track = RouteTrack((0..100).map { GeoUtils.offset(start, 0.0, it * 100.0) }) // 10 km north

    private fun station(
        alongM: Double,
        sideM: Double,
    ) = GeoUtils.offset(GeoUtils.offset(start, 0.0, alongM), 90.0, sideM)

    @Test
    fun stationBesideTheRouteAheadIsShownWithDistances() {
        val s = PoiAlongRoute.spot(track, station(5000.0, 150.0), hereM = 2000.0)
        assertNotNull(s)
        assertEquals(3000.0, s!!.aheadM, 15.0)
        assertEquals(150.0, s.offRouteM, 5.0)
    }

    @Test
    fun stationOffTheCorridorIsNot() {
        assertNull(PoiAlongRoute.spot(track, station(5000.0, 900.0), hereM = 2000.0))
    }

    @Test
    fun stationBehindIsDroppedButOneJustPassedStays() {
        assertNull(PoiAlongRoute.spot(track, station(1000.0, 50.0), hereM = 2000.0))
        assertNotNull(PoiAlongRoute.spot(track, station(1900.0, 50.0), hereM = 2000.0))
    }

    @Test
    fun onAnExpresswayOnlyTheRandROnYourSideCounts() {
        val highway = { _: Double -> true }
        // Driving north: west (270) is on the left, east (90) across the highway.
        assertNotNull(PoiAlongRoute.spot(track, station(5000.0, -120.0), hereM = 2000.0, onExpressway = highway))
        assertNull(PoiAlongRoute.spot(track, station(5000.0, 120.0), hereM = 2000.0, onExpressway = highway))
    }

    @Test
    fun offTheExpresswayEitherSideAndAWiderCorridor() {
        assertNotNull(PoiAlongRoute.spot(track, station(5000.0, 120.0), hereM = 2000.0))
        assertNotNull(PoiAlongRoute.spot(track, station(5000.0, -550.0), hereM = 2000.0))
        assertNull(PoiAlongRoute.spot(track, station(5000.0, 550.0), hereM = 2000.0, onExpressway = { true }))
    }

    @Test
    fun nextStationAfterThisOne() {
        val n = PoiAlongRoute.nextAfter(3000.0, listOf(1000.0, 3100.0, 8000.0, 6000.0), 9000.0)
        assertEquals(6000.0, n.nextAheadM!!, 0.1) // 3100 is the same R&R, 1000 is before it
        assertNull(PoiAlongRoute.nextAfter(3000.0, listOf(1000.0), 9000.0).nextAheadM)
    }
}
