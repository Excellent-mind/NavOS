package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainedGuidanceTest {
    private val a = RoutePoint(3.15, 101.70)
    private val b = GeoUtils.offset(a, 0.0, 1000.0) // turn right here
    private val c = GeoUtils.offset(b, 90.0, 80.0) // then left, 80 m on
    private val d = GeoUtils.offset(c, 0.0, 1000.0)

    private fun route(secondGap: Double): Route {
        val c2 = GeoUtils.offset(b, 90.0, secondGap)
        val d2 = GeoUtils.offset(c2, 0.0, 1000.0)
        return Route(
            listOf(a, b, c2, d2),
            2000.0 + secondGap,
            200.0,
            listOf(
                RouteStep("Head north", 1000.0, 90.0, "depart", null, "Jalan A", a),
                RouteStep("Turn right onto Jalan B", secondGap, 10.0, "turn", "right", "Jalan B", b),
                RouteStep("Turn left onto Jalan C", 1000.0, 90.0, "turn", "left", "Jalan C", c2),
                RouteStep("Arrive at your destination", 0.0, 0.0, "arrive", null, "", d2),
            ),
        )
    }

    private fun spokenNear(r: Route): List<String> {
        val said = mutableListOf<String>()
        val engine = NavigationEngine(onState = {}, onAnnounce = { _, text -> said += text })
        engine.setRoute(r)
        for (m in listOf(900.0, 960.0, 990.0)) engine.onLocation(GeoUtils.offset(a, 0.0, m), 10.0, 0.0)
        return said
    }

    @Test
    fun twoTurnsCloseTogetherAreSaidTogether() {
        val said = spokenNear(route(80.0))
        assertTrue(said.toString(), said.any { it.startsWith("Turn right onto Jalan B, then turn left") })
    }

    @Test
    fun turnsFarApartAreSaidOneAtATime() {
        val said = spokenNear(route(600.0))
        assertTrue(said.toString(), said.any { it == "Turn right onto Jalan B" })
        assertTrue(said.toString(), said.none { it.contains(", then ") })
    }
}
