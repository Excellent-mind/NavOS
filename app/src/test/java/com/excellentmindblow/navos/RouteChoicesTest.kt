package com.excellentmindblow.navos

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteChoicesTest {
    private fun r(minutes: Int) = RouteChoices.Input(minutes * 60.0, minutes * 500.0, "Jalan $minutes", tolls = null)

    @Test
    fun quickestFirstAndOnlyTheQuickestIsTaggedFastest() {
        val out = RouteChoices.order(listOf(r(31), r(24), r(28)), selected = 0)
        assertEquals(listOf(1, 2, 0), out.map { it.index })
        assertTrue(out[0].fastest)
        assertFalse(out[1].fastest || out[2].fastest)
        assertTrue(out.single { it.selected }.index == 0)
    }

    @Test
    fun aSingleRouteIsNotCalledFastest() {
        assertFalse(RouteChoices.order(listOf(r(20)), selected = 0).single().fastest)
    }

    @Test
    fun theSelectedRouteIsAlwaysAmongTheShownCards() {
        val routes = listOf(r(20), r(22), r(25), r(30), r(40))
        val shown = RouteChoices.order(routes, selected = 4, limit = RouteChoices.SHOWN)
        assertEquals(listOf(0, 1, 4), shown.map { it.index })
        assertEquals(listOf(0, 1, 2), RouteChoices.order(routes, selected = 1, limit = 3).map { it.index })
    }

    @Test
    fun theBubbleSitsHalfwayAlongTheRouteByDistance() {
        val a = RoutePoint(3.10, 101.60)
        val b = RoutePoint(3.10, 101.61) // short leg
        val c = RoutePoint(3.10, 101.69) // long leg
        val mid = RouteChoices.labelPoint(listOf(a, b, c))!!
        val total = GeoUtils.distanceMeters(a, b) + GeoUtils.distanceMeters(b, c)
        assertEquals(total / 2, GeoUtils.distanceMeters(a, mid), 5.0)
    }
}
