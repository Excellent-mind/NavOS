package com.excellentmindblow.navos

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOfflineCacheTest {
    private val a = RoutePoint(3.15, 101.70)

    @Test
    fun thinsTheRouteAndKeepsItsEnds() {
        val line = (0..100).map { GeoUtils.offset(a, 0.0, it * 10.0) } // 1 km, a point every 10 m
        val c = RouteOfflineCache.corridor(line)
        assertTrue(c.size in 6..9)
        assertEquals(line.first(), c.first())
        assertEquals(line.last(), c.last())
    }

    @Test
    fun stopsAfterTheMaximumLength() {
        val line = (0..300).map { GeoUtils.offset(a, 0.0, it * 1000.0) } // 300 km
        val c = RouteOfflineCache.corridor(line)
        val km = GeoUtils.distanceMeters(c.first(), c.last()) / 1000
        assertTrue("$km km", km <= RouteOfflineCache.MAX_KM * 1.02)
        assertTrue("$km km", km >= RouteOfflineCache.MAX_KM * 0.98)
    }
}
