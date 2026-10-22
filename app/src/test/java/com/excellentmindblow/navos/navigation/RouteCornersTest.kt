package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCornersTest {
    private val start = RoutePoint(3.0, 101.0)

    /** Drives [legs] of (bearing, metres) from [start], one point per 2 m. */
    private fun path(vararg legs: Pair<Double, Double>): RouteTrack {
        val pts = arrayListOf(start)
        var p = start
        for ((bearing, meters) in legs) {
            var done = 0.0
            while (done < meters) {
                p = GeoUtils.offset(p, bearing, 2.0)
                pts += p
                done += 2.0
            }
        }
        return RouteTrack(pts)
    }

    @Test
    fun rightTurnAtAJunction() {
        val c = RouteCorners.find(path(0.0 to 200.0, 90.0 to 200.0))
        assertEquals(1, c.size)
        assertEquals(90.0, c[0].turnDeg, 6.0)
        assertTrue("covers the junction at 200 m", c[0].startM < 200.0 && c[0].endM > 200.0)
    }

    @Test
    fun smallBendIsACornerToo() {
        val c = RouteCorners.find(path(0.0 to 200.0, -30.0 to 200.0))
        assertEquals(1, c.size)
        assertTrue(c[0].turnDeg < -20.0)
    }

    @Test
    fun straightRoadAndTinyWobblesHaveNoCorners() {
        assertTrue(RouteCorners.find(path(0.0 to 400.0)).isEmpty())
        assertTrue(RouteCorners.find(path(0.0 to 200.0, 8.0 to 200.0)).isEmpty())
    }

    @Test
    fun gentleCurveIsOneCorner() {
        // 60 degrees spread over 120 m, 10 degrees every 20 m.
        val legs = arrayOf(0.0 to 150.0) + (1..6).map { (it * 10.0) to 20.0 } + arrayOf(60.0 to 150.0)
        val c = RouteCorners.find(path(*legs))
        assertEquals(1, c.size)
        assertEquals(60.0, c[0].turnDeg, 8.0)
    }

    @Test
    fun sBendIsTwoCorners() {
        val c = RouteCorners.find(path(0.0 to 150.0, 45.0 to 100.0, 0.0 to 150.0))
        assertEquals(2, c.size)
        assertTrue(c[0].turnDeg > 0 && c[1].turnDeg < 0)
    }
}
