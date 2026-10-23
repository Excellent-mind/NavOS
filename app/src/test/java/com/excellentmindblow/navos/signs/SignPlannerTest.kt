package com.excellentmindblow.navos.signs

import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignPlannerTest {
    // A straight road due north; 1 degree of latitude ~ 111.195 km (GeoUtils earth radius).
    private fun north(m: Double) = RoutePoint(3.0 + m / 111_195.0, 101.0)

    private fun east(
        alongM: Double,
        sideM: Double,
    ) = RoutePoint(3.0 + alongM / 111_195.0, 101.0 + sideM / (111_195.0 * Math.cos(Math.toRadians(3.0))))

    private val line = (0..100).map { north(it * 100.0) } // 10 km

    private fun step(
        type: String,
        at: Double,
        modifier: String? = null,
        road: String = "",
        ref: String? = null,
        destinations: String? = null,
        exits: String? = null,
        distance: Double = 1000.0,
        roundaboutExit: Int = 0,
    ) = RouteStep("", distance, 60.0, type, modifier, road, north(at), ref = ref, destinations = destinations, exits = exits, roundaboutExit = roundaboutExit)

    private fun route(vararg steps: RouteStep) = Route(line, 10_000.0, 600.0, steps.toList())

    @Test
    fun destinationsBecomeShieldAndPlaceLines() {
        val lines = SignPlanner.targetLines(step("off ramp", 0.0, destinations = "E33: Kuantan, Genting Highlands, Gombak"))
        assertEquals(listOf("Kuantan", "Genting Highlands", "Gombak"), lines.map { it.text })
        assertEquals(listOf("E33"), lines[0].shields)
        assertTrue(lines[1].tourist)
        assertFalse(lines[0].tourist)
    }

    @Test
    fun expresswayExitIsASplitGantryWithYourWayLit() {
        val r =
            route(
                step("depart", 0.0, road = "Lebuhraya Utara-Selatan", ref = "E1", distance = 5000.0),
                step("off ramp", 5000.0, modifier = "slight left", destinations = "E33: Kuantan, Gombak", exits = "3305A"),
                step("arrive", 10_000.0),
            )
        val dir = SignPlanner.plan(r, emptyList(), emptyList()).single { it.content is SignContent.Direction }
        assertEquals(5000.0 - SignPlanner.DIRECTION_BEFORE_M, dir.atMeters, 2.0)
        val c = dir.content as SignContent.Direction
        assertEquals("3305A", c.exitNumber)
        assertEquals(2, c.panels.size)
        assertTrue("keep left: lit panel on the left", c.panels[0].lit)
        assertEquals(SignArrow.SLIGHT_LEFT, c.panels[0].arrow)
        assertTrue(c.panels[0].expressway)
        assertFalse(c.panels[1].lit)
        assertEquals("Lebuhraya Utara-Selatan", c.panels[1].lines[0].text)
    }

    @Test
    fun plainTurnWithOnlyAStreetNameGetsNoBoard() {
        val r = route(step("depart", 0.0, road = "Jalan A"), step("turn", 3000.0, modifier = "left", road = "Jalan B"), step("arrive", 10_000.0))
        assertTrue(SignPlanner.plan(r, emptyList(), emptyList()).none { it.content is SignContent.Direction })
    }

    @Test
    fun lightsAtOneJunctionCountOnce() {
        val r = route(step("depart", 0.0, road = "Jalan Ampang"), step("arrive", 10_000.0))
        val pois =
            listOf(
                signal(east(1000.0, 3.0)),
                signal(east(1030.0, -4.0)), // other approach of the same junction
                signal(east(3000.0, 0.0)),
                signal(east(6000.0, 80.0)), // a side street, not on the route
            )
        val lights = SignPlanner.trafficLights(r, pois)
        assertEquals(2, lights.size)
        assertEquals(1000.0, lights[0], 5.0)
        assertEquals(2, SignPlanner.lightsAhead(lights, 0.0))
        assertEquals(1, SignPlanner.lightsAhead(lights, 2000.0))
        assertEquals(0, SignPlanner.lightsAhead(lights, 3100.0))
    }

    @Test
    fun lightsUnderAnExpresswayAreNotCounted() {
        val r = route(step("depart", 0.0, road = "Lebuhraya", ref = "E1"), step("arrive", 10_000.0))
        assertTrue(SignPlanner.trafficLights(r, listOf(signal(east(2000.0, 2.0)))).isEmpty())
    }

    @Test
    fun restStopOnlyOnTheDriversSide() {
        val r = route(step("depart", 0.0, ref = "E1", road = "Lebuhraya"), step("arrive", 10_000.0))
        val left = RoadPoi("services", east(6000.0, -120.0).latitude, east(6000.0, -120.0).longitude, "R&R Rawang")
        val right = RoadPoi("services", east(8000.0, 120.0).latitude, east(8000.0, 120.0).longitude, "R&R Other")
        val signs = SignPlanner.plan(r, emptyList(), listOf(left, right)).filter { it.content is SignContent.RestStop }
        assertEquals(1, signs.size)
        val c = signs[0].content as SignContent.RestStop
        assertEquals("R&R Rawang", c.name)
        assertTrue(c.services)
        assertEquals(6000.0 - SignPlanner.REST_BEFORE_M, signs[0].atMeters, 5.0)
    }

    @Test
    fun tollAndAesBoardsStandBeforeThem() {
        val r = route(step("depart", 0.0, road = "Lebuhraya", ref = "E1"), step("arrive", 10_000.0))
        val toll = RoadPoi("toll", north(4000.0).latitude, north(4000.0).longitude, "Plaza Tol Gombak")
        val cam = RoadPoi("camera", north(7000.0).latitude, north(7000.0).longitude, "", maxspeed = "90")
        val signs = SignPlanner.plan(r, listOf(toll, cam), emptyList())
        val t = signs.single { it.content is SignContent.Toll }
        assertEquals(4000.0 - SignPlanner.TOLL_BEFORE_M, t.atMeters, 5.0)
        val a = signs.single { it.content is SignContent.Aes }
        assertEquals(90, (a.content as SignContent.Aes).limit)
    }

    @Test
    fun roundaboutGoesClockwise() {
        val r = route(step("depart", 0.0, road = "Jalan A"), step("roundabout", 4000.0, modifier = "straight", road = "Jalan Kajang", roundaboutExit = 2), step("arrive", 10_000.0))
        val c = SignPlanner.plan(r, emptyList(), emptyList()).single { it.content is SignContent.Roundabout }.content as SignContent.Roundabout
        assertEquals(0.0, c.exitAngle, 0.1)
        // Left-hand traffic: the exit before "straight on" is on the left.
        assertEquals(listOf(-90.0), c.otherExitAngles)
    }

    @Test
    fun confirmationListsUpcomingPlaces() {
        val r =
            route(
                step("depart", 0.0, road = "Jalan A", distance = 1000.0),
                step("on ramp", 1000.0, modifier = "slight right", ref = "E8", road = "Lebuhraya KL-Karak", distance = 4000.0),
                step("off ramp", 5000.0, modifier = "slight left", destinations = "Bentong", distance = 2000.0),
                step("off ramp", 7000.0, modifier = "slight left", destinations = "Karak", distance = 3000.0),
                step("arrive", 10_000.0),
            )
        val c = SignPlanner.plan(r, emptyList(), emptyList()).single { it.content is SignContent.Confirmation }
        val rows = (c.content as SignContent.Confirmation).rows
        assertEquals(listOf("Bentong", "Karak"), rows.map { it.first })
        assertEquals(5000.0 - (1000.0 + SignPlanner.CONFIRM_AFTER_M), rows[0].second, 5.0)
    }

    @Test
    fun symbolsAndLimits() {
        assertEquals(SignSymbol.AIRPORT, SignPlanner.symbolOf("KLIA"))
        assertEquals(SignSymbol.HOSPITAL, SignPlanner.symbolOf("Hospital Selayang"))
        assertEquals(SignSymbol.PORT, SignPlanner.symbolOf("Pelabuhan Klang"))
        assertEquals(SignSymbol.BORDER, SignPlanner.symbolOf("CIQ Johor Bahru"))
        assertNull(SignPlanner.symbolOf("Gombak"))
        assertEquals(90, SignPlanner.limitOf("90 km/h"))
        assertNull(SignPlanner.limitOf("signals"))
        assertTrue(SignPlanner.isExpressway("E33"))
        assertFalse(SignPlanner.isExpressway("Exit"))
    }

    private fun signal(p: RoutePoint) = RoadPoi("signal", p.latitude, p.longitude, "")
}
