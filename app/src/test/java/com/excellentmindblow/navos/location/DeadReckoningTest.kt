package com.excellentmindblow.navos.location

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadReckoningTest {
    private val start = RoutePoint(3.15, 101.70)
    private val track = RouteTrack((0..50).map { GeoUtils.offset(start, 0.0, it * 100.0) }) // 5 km north

    @Test
    fun keepsGoingAlongTheRouteAtTheLastSpeed() {
        val dr = DeadReckoning(track)
        dr.start(progressM = 1000.0, speedMs = 20.0, timeMs = 0L)
        val e = dr.estimate(10_000L)!!
        // ~20 m/s for 10 s, easing off 1% a second: a little under 200 m.
        assertEquals(1191.0, e.progressM, 5.0)
        assertTrue(e.speedMs < 20.0)
        assertEquals(0.0, e.bearingDeg, 2.0)
    }

    @Test
    fun stopsAtTheEndOfTheRouteAndAfterTwoMinutes() {
        val dr = DeadReckoning(track)
        dr.start(progressM = 4900.0, speedMs = 30.0, timeMs = 0L)
        assertEquals(track.length, dr.estimate(60_000L)!!.progressM, 1.0)
        assertNull(dr.estimate(((DeadReckoning.MAX_S + 1) * 1000).toLong()))
        assertTrue(!dr.active)
    }

    @Test
    fun inactiveUntilStarted() {
        assertNull(DeadReckoning(track).estimate(1000L))
    }
}
