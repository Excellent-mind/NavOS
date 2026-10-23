package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FasterRouteTest {
    private fun c(
        i: Int,
        total: Double,
        shared: Double = 0.3,
    ) = FasterRoute.Candidate(i, total, shared)

    @Test
    fun offersTheRouteThatSavesTheMost() {
        val best = FasterRoute.pick(3000.0, listOf(c(1, 2700.0), c(2, 2500.0)))!!
        assertEquals(2, best.first.index)
        assertEquals(500.0, best.second, 0.1)
    }

    @Test
    fun smallSavingsAreNotWorthTheFuss() {
        assertNull(FasterRoute.pick(3000.0, listOf(c(1, 2850.0)))) // 2.5 min
        assertNull(FasterRoute.pick(600.0, listOf(c(1, 450.0)))) // 2.5 min, though 25%
    }

    @Test
    fun theSameRoadIsNotADifferentRoute() {
        assertNull(FasterRoute.pick(3000.0, listOf(c(1, 2500.0, shared = 0.95))))
    }

    @Test
    fun sharedShareSeesOverlap() {
        val a = RoutePoint(3.15, 101.70)
        val line = (0..20).map { GeoUtils.offset(a, 0.0, it * 100.0) }
        val other = (0..20).map { GeoUtils.offset(GeoUtils.offset(a, 90.0, 500.0), 0.0, it * 100.0) }
        assertEquals(1.0, FasterRoute.sharedShare(line, line), 0.001)
        assertEquals(0.0, FasterRoute.sharedShare(other, line), 0.001)
    }
}
