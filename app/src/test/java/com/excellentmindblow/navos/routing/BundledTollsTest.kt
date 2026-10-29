package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledTollsTest {
    private val start = RoutePoint(3.2400, 101.7000)
    private val line = (0..50).map { GeoUtils.offset(start, 90.0, it * 200.0) } // 10 km east

    @Test
    fun onlyPlazasOnTheRoute() {
        val all =
            BundledTolls.parse(
                """[[${GeoUtils.offset(start, 90.0, 4000.0).latitude},${GeoUtils.offset(start, 90.0, 4000.0).longitude},"Plaza Tol Gombak",""],
                    [${GeoUtils.offset(GeoUtils.offset(start, 90.0, 6000.0), 0.0, 300.0).latitude},${GeoUtils.offset(start, 90.0, 6000.0).longitude},"Plaza Tol Jauh",""],
                    [5.4,100.3,"Jambatan Pulau Pinang",""]]""",
            )
        val on = BundledTolls.along(line, all)
        assertEquals(listOf("Plaza Tol Gombak"), on.map { it.name })
        assertEquals("toll", on[0].kind)
    }

    @Test
    fun boothsCloseTogetherAreOnePlazaKeepingEveryName() {
        val route = Route(line, 10_000.0, 600.0, emptyList())
        val a = GeoUtils.offset(start, 90.0, 4000.0)
        val b = GeoUtils.offset(start, 90.0, 4100.0)
        val strip =
            com.excellentmindblow.navos.navigation.TripStrip.build(
                route,
                listOf(RoadPoi("toll", a.latitude, a.longitude, "Tol Berlapis (Arah Timur)"), RoadPoi("toll", b.latitude, b.longitude, "Plaza Tol Gombak (Arah Timur)")),
                emptyList(),
                emptyList(),
            )!!
        val toll = strip.marks.single()
        assertEquals("Tol Berlapis (Arah Timur)", toll.name)
        assertEquals(listOf("Plaza Tol Gombak (Arah Timur)"), toll.otherNames)
    }
}
