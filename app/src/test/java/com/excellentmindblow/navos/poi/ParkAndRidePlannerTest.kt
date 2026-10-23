package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkAndRidePlannerTest {
    // 20 km due south into KLCC, a point every 200 m.
    private val klcc = RoutePoint(3.1579, 101.7116)
    private val start = GeoUtils.offset(klcc, 0.0, 20_000.0)
    private val track = RouteTrack((0..100).map { GeoUtils.offset(start, 180.0, it * 200.0) })
    private val kg = TransitLine("KG", "MRT Kajang Line", null)
    private val kj = TransitLine("KJ", "LRT Kelana Jaya Line", null)

    private fun station(
        id: String,
        at: RoutePoint,
        lines: List<TransitLine>,
        park: Boolean = false,
    ) = Poi(id, PoiKind.TRANSIT, id, at.latitude, at.longitude, emptyList(), null, lines, if (park) ParkAndRide(at.latitude, at.longitude, 500) else null)

    // Boards 4 km into the drive, right on the route; gets off 300 m from KLCC.
    private val board = station("A", GeoUtils.offset(start, 180.0, 4_000.0), listOf(kg), park = true)
    private val alight = station("B", GeoUtils.offset(klcc, 0.0, 300.0), listOf(kg, kj))

    @Test
    fun jammedDriveIntoTheCityGetsAPlan() {
        // 70 min by car in a jam (plus city parking) vs ~8 min drive + ~29 min train + walk.
        val plan = ParkAndRidePlanner.best(track, 70 * 60.0, klcc, listOf(board), listOf(alight))
        assertNotNull(plan)
        assertEquals("A", plan!!.from.id)
        assertEquals("KG", plan.line.badge)
        assertTrue("saves ${plan.savedS}", plan.savedS >= ParkAndRidePlanner.MIN_SAVING_S)
    }

    @Test
    fun noPlanWhenDrivingIsQuickEnough() {
        assertNull(ParkAndRidePlanner.best(track, 25 * 60.0, klcc, listOf(board), listOf(alight)))
    }

    @Test
    fun noPlanWithoutAShared_line() {
        val other = station("B", alight.let { RoutePoint(it.latitude, it.longitude) }, listOf(kj))
        assertNull(ParkAndRidePlanner.best(track, 70 * 60.0, klcc, listOf(board), listOf(other)))
    }

    @Test
    fun noPlanWithoutParkAndRideOrNearTheEnd() {
        val noPark = station("A", RoutePoint(board.latitude, board.longitude), listOf(kg))
        assertNull(ParkAndRidePlanner.best(track, 70 * 60.0, klcc, listOf(noPark), listOf(alight)))
        val late = station("C", GeoUtils.offset(start, 180.0, 18_000.0), listOf(kg), park = true) // 90% of the way
        assertNull(ParkAndRidePlanner.best(track, 70 * 60.0, klcc, listOf(late), listOf(alight)))
    }

    @Test
    fun cityCentre() {
        assertTrue(ParkAndRidePlanner.inCityCentre(klcc))
        assertTrue(!ParkAndRidePlanner.inCityCentre(start))
    }
}
