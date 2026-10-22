package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class NavigationEngineTest {
    private val metersPerDegree = 2 * PI * 6371000.0 / 360

    // ~1.1 km north, turn right, ~1.1 km east, arrive.
    private val start = RoutePoint(3.1500, 101.7000)
    private val corner = RoutePoint(3.1600, 101.7000)
    private val end = RoutePoint(3.1600, 101.7100)
    private val northLeg = 0.01 * metersPerDegree
    private val eastLeg = 0.01 * metersPerDegree * cos(3.16 * PI / 180)

    private val route =
        Route(
            geometry = listOf(start, corner, end),
            distanceMeters = northLeg + eastLeg,
            durationSeconds = 120.0,
            steps =
                listOf(
                    RouteStep("Head north on Jalan Ampang", northLeg, 60.0, "depart", null, "Jalan Ampang", start),
                    RouteStep("Turn right onto Jalan Sultan Ismail", eastLeg, 60.0, "turn", "right", "Jalan Sultan Ismail", corner),
                    RouteStep("Arrive at your destination", 0.0, 0.0, "arrive", null, "", end),
                ),
        )

    private val states = mutableListOf<NavigationState>()
    private val announcements = mutableListOf<Pair<String, String>>()
    private lateinit var engine: NavigationEngine

    @Before
    fun setUp() {
        engine =
            NavigationEngine(
                onState = { states += it },
                onAnnounce = { key, text -> announcements += key to text },
            )
        engine.setRoute(route)
    }

    /** A point on the northbound leg, [meters] before the corner. */
    private fun beforeCorner(meters: Double) = RoutePoint(corner.latitude - meters / metersPerDegree, corner.longitude)

    /** A point [meters] east of the northbound leg's midpoint. */
    private fun eastOfNorthLeg(meters: Double): RoutePoint {
        val lat = 3.1550
        return RoutePoint(lat, 101.7000 + meters / (metersPerDegree * cos(lat * PI / 180)))
    }

    @Test
    fun atTheStartTheFirstTurnIsUpcomingWithTheWholeTripLeft() {
        engine.onLocation(start)
        val s = states.last()

        assertEquals(1, s.currentStepIndex)
        assertEquals("turn", s.maneuverType)
        assertEquals("right", s.modifier)
        assertEquals(northLeg, s.distanceToNextTurnMeters, 1.0)
        assertEquals(northLeg + eastLeg, s.distanceRemainingMeters, 1.0)
        assertEquals(120.0, s.durationRemainingSeconds, 0.5)
        assertFalse(s.offRoute)
        assertFalse(s.isArrival)
        assertEquals("Arrive at your destination", s.nextInstruction)
    }

    @Test
    fun countdownShrinksAsTheDriverApproachesTheTurn() {
        engine.onLocation(beforeCorner(500.0))
        val s = states.last()

        assertEquals(500.0, s.distanceToNextTurnMeters, 1.0)
        assertEquals(500.0 + eastLeg, s.distanceRemainingMeters, 1.0)
        // Time left scales with the part of the leading step still to drive.
        assertEquals(60.0 * 500.0 / northLeg + 60.0, s.durationRemainingSeconds, 0.5)
    }

    @Test
    fun reachingTheTurnAdvancesToTheNextStep() {
        engine.onLocation(beforeCorner(10.0))
        val s = states.last()

        assertEquals(2, s.currentStepIndex)
        assertEquals("arrive", s.maneuverType)
        assertTrue(s.isArrival)
    }

    @Test
    fun stepIndexNeverGoesBackwards() {
        engine.onLocation(beforeCorner(10.0))
        engine.onLocation(beforeCorner(300.0))
        assertEquals(2, states.last().currentStepIndex)
    }

    @Test
    fun setRouteStartsAgainFromTheFirstTurn() {
        engine.onLocation(beforeCorner(10.0))
        engine.setRoute(route)
        engine.onLocation(start)
        assertEquals(1, states.last().currentStepIndex)
    }

    @Test
    fun farFromTheLineIsOffRouteAndSilent() {
        engine.onLocation(eastOfNorthLeg(111.0))
        val s = states.last()

        assertTrue(s.offRoute)
        assertFalse(s.offRouteConfirmed)
        assertTrue(announcements.isEmpty())
    }

    @Test
    fun gpsNoiseNearTheLineIsNotOffRoute() {
        engine.onLocation(eastOfNorthLeg(10.0))
        assertFalse(states.last().offRoute)
    }

    @Test
    fun headingTheWrongWaySlightlyOffTheLineConfirmsAWrongTurn() {
        // 18 m off (under the 28 m distance rule) but driving east while the
        // route goes north: that is another road, not GPS noise.
        engine.onLocation(eastOfNorthLeg(18.0), speedMetersPerSecond = 10.0, bearingDegrees = 90.0)
        val s = states.last()
        assertTrue(s.offRoute)
        assertTrue(s.offRouteConfirmed)
    }

    @Test
    fun headingAlongTheRouteSlightlyOffTheLineIsFine() {
        engine.onLocation(eastOfNorthLeg(18.0), speedMetersPerSecond = 10.0, bearingDegrees = 2.0)
        assertFalse(states.last().offRoute)
    }

    @Test
    fun headingIsIgnoredWhenCrawling() {
        // Bearing from a near-stationary GPS is unreliable.
        engine.onLocation(eastOfNorthLeg(18.0), speedMetersPerSecond = 1.0, bearingDegrees = 90.0)
        assertFalse(states.last().offRoute)
    }

    @Test
    fun announcesTheTurnFarThenNearWithoutSpeed() {
        engine.onLocation(beforeCorner(250.0))
        assertEquals("1-far", announcements.last().first)
        assertTrue(announcements.last().second.startsWith("In "))
        assertTrue(announcements.last().second.endsWith("turn right onto Jalan Sultan Ismail"))

        engine.onLocation(beforeCorner(60.0))
        assertEquals("1-near" to "Turn right onto Jalan Sultan Ismail", announcements.last())
    }

    @Test
    fun nothingIsAnnouncedWhileTheTurnIsStillFarAway() {
        engine.onLocation(beforeCorner(800.0))
        assertTrue(announcements.isEmpty())
    }

    @Test
    fun highwaySpeedAnnouncesEarlier() {
        // 400 m out: beyond the 300 m fixed fallback, but at 30 m/s (108 km/h)
        // 15 s ahead is 450 m, so the far announcement is due.
        engine.onLocation(beforeCorner(400.0))
        assertTrue(announcements.isEmpty())

        engine.onLocation(beforeCorner(400.0), speedMetersPerSecond = 30.0)
        assertEquals("1-far", announcements.last().first)
    }

    @Test
    fun arrivalIsAnnouncedNearTheDestination() {
        engine.onLocation(beforeCorner(10.0))
        val nearEnd = RoutePoint(end.latitude, end.longitude - 50.0 / (metersPerDegree * cos(3.16 * PI / 180)))
        engine.onLocation(nearEnd)

        val keys = announcements.map { it.first }
        assertTrue(keys.contains("2-near"))
        assertTrue(keys.contains("2-arrive"))
        assertTrue(states.last().isArrival)
    }

    @Test
    fun noRouteMeansNoState() {
        engine.setRoute(null)
        engine.onLocation(start)
        assertTrue(states.isEmpty())
    }
}
