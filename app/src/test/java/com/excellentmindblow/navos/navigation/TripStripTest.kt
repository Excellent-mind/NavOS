package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStripTest {
    private val start = RoutePoint(3.15, 101.70)
    private val geometry = (0..100).map { GeoUtils.offset(start, 0.0, it * 100.0) } // 10 km north
    private val route = Route(geometry, 10_000.0, 600.0, emptyList())

    private fun at(
        alongM: Double,
        sideM: Double = 0.0,
    ) = GeoUtils.offset(GeoUtils.offset(start, 0.0, alongM), 90.0, sideM)

    private fun poi(
        kind: String,
        p: RoutePoint,
        name: String = "",
    ) = RoadPoi(kind, p.latitude, p.longitude, name)

    @Test
    fun placesEachKindAlongTheRouteInOrder() {
        val strip =
            TripStrip.build(
                route,
                roadPois = listOf(poi("toll", at(3000.0), "Plaza Tol Gombak"), poi("camera", at(6000.0))),
                extras = listOf(poi("fuel", at(1000.0, 80.0), "Petronas"), poi("services", at(5000.0, -150.0), "R&R"), poi("charging", at(8000.0, 50.0))),
                lightsM = listOf(400.0, 200.0),
            )!!
        assertEquals(10_000.0, strip.lengthM, 20.0)
        assertEquals(
            listOf(TripStrip.Kind.PETROL, TripStrip.Kind.TOLL, TripStrip.Kind.RNR, TripStrip.Kind.CAMERA, TripStrip.Kind.EV),
            strip.marks.map { it.kind },
        )
        assertEquals(3000.0, strip.marks[1].atM, 20.0)
        assertEquals(listOf(200.0, 400.0), strip.lightsM)
    }

    @Test
    fun plazaBoothsAndTheRandRAcrossTheHighwayDontCountTwice() {
        val strip =
            TripStrip.build(
                route,
                roadPois = listOf(poi("toll", at(3000.0)), poi("toll", at(3200.0), "Plaza Tol Gombak")),
                // On your side (west, left when driving north) and across the highway.
                extras = listOf(poi("services", at(5000.0, -150.0)), poi("services", at(5000.0, 150.0))),
                lightsM = emptyList(),
            )!!
        assertEquals(1, strip.count(TripStrip.Kind.TOLL))
        assertEquals("Plaza Tol Gombak", strip.marks.first { it.kind == TripStrip.Kind.TOLL }.name)
        assertEquals(1, strip.count(TripStrip.Kind.RNR))
    }

    @Test
    fun farOffStationsAreLeftOut() {
        val strip = TripStrip.build(route, emptyList(), listOf(poi("fuel", at(4000.0, 900.0))), emptyList())!!
        assertTrue(strip.marks.isEmpty())
    }
}
