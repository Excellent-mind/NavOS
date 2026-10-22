package com.excellentmindblow.navos

import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Kilometre formatting (Units defaults to metric). */
class NavFormatTest {
    @Test
    fun countdownUsesKilometresThenFiftyThenTenMetreSteps() {
        assertEquals("1.2 km", NavFormat.formatCountdownDistance(1234.0))
        assertEquals("950 m", NavFormat.formatCountdownDistance(990.0))
        assertEquals("300 m", NavFormat.formatCountdownDistance(312.0))
        assertEquals("100 m", NavFormat.formatCountdownDistance(100.0))
        assertEquals("60 m", NavFormat.formatCountdownDistance(57.0))
        assertEquals("10 m", NavFormat.formatCountdownDistance(2.0))
    }

    @Test
    fun turnListRoundsToTenMetres() {
        assertEquals("450 m", NavFormat.formatTurnDistance(447.0))
        assertEquals("10 m", NavFormat.formatTurnDistance(3.0))
        assertEquals("2.5 km", NavFormat.formatTurnDistance(2480.0))
    }

    @Test
    fun tripDistanceAndDuration() {
        assertEquals("850 m", NavFormat.formatTripDistance(850.4))
        assertEquals("3.2 km", NavFormat.formatTripDistance(3181.0))
        assertEquals("58 km", NavFormat.formatTripDistance(57_900.0))
        assertEquals("1 min", NavFormat.formatTripDuration(10.0))
        assertEquals("24 min", NavFormat.formatTripDuration(24 * 60.0))
        assertEquals("1:05 h", NavFormat.formatTripDuration(65 * 60.0))
        assertEquals("1 h 5 min", NavFormat.formatRouteMinutes(65 * 60.0))
    }

    @Test
    fun searchResultDistance() {
        assertEquals("960 m", NavFormat.formatDistance(957f))
        assertEquals("9.8 km", NavFormat.formatDistance(9_800f))
        assertEquals("120 km", NavFormat.formatDistance(120_400f))
    }

    @Test
    fun viaRoadIsTheRoadDrivenFurthest() {
        val p = RoutePoint(3.15, 101.7)
        val route =
            Route(
                geometry = listOf(p, p),
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                steps =
                    listOf(
                        RouteStep("", 300.0, 0.0, "depart", null, "Jalan Ampang", p),
                        RouteStep("", 1400.0, 0.0, "turn", "right", "Jalan Tun Razak", p),
                        RouteStep("", 500.0, 0.0, "turn", "left", "Jalan Ampang", p),
                        RouteStep("", 0.0, 0.0, "arrive", null, "", p),
                    ),
            )
        assertEquals("Jalan Tun Razak", NavFormat.viaRoadName(route))
    }

    @Test
    fun maneuverIcons() {
        assertEquals(R.drawable.ic_turn_right, NavFormat.iconForManeuver("turn", "right"))
        assertEquals(R.drawable.ic_turn_roundabout_left, NavFormat.iconForManeuver("roundabout", "left"))
        assertEquals(R.drawable.ic_turn_fork_right, NavFormat.iconForManeuver("off ramp", "slight right"))
        assertEquals(R.drawable.ic_turn_uturn, NavFormat.iconForManeuver("turn", "uturn"))
        assertEquals(R.drawable.ic_turn_arrive, NavFormat.iconForManeuver("arrive", null))
        assertEquals(R.drawable.ic_turn_straight, NavFormat.iconForManeuver("new name", "straight"))
    }

    @Test
    fun noTollSummaryWithoutTollPlazas() {
        assertNull(NavFormat.tollSummary(null))
        assertNull(NavFormat.tollSummary(emptyList()))
    }
}
