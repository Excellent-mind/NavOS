package com.excellentmindblow.navos.traffic

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadReportsTest {
    private val start = RoutePoint(3.10, 101.60)

    // 5 km due north, a point every 100 m.
    private val track = RouteTrack((0..50).map { GeoUtils.offset(start, 0.0, it * 100.0) })

    private fun report(
        id: Int,
        at: RoutePoint,
        heading: Int = -1,
    ) = RoadReports.Report(id, RoadReports.Kind.ACCIDENT, at.latitude, at.longitude, heading, 120, 0, 0)

    @Test
    fun onTheRouteSortedByDistance() {
        val a = report(1, GeoUtils.offset(start, 0.0, 3000.0), heading = 2)
        val b = report(2, GeoUtils.offset(start, 0.0, 1000.0))
        val out = RoadReports.onRoute(track, listOf(a, b))
        assertEquals(listOf(2, 1), out.map { it.report.id })
        assertEquals(1000.0, out[0].atM, 5.0)
    }

    @Test
    fun anotherRoadOrTheOtherSideIsLeftOut() {
        val offRoad = report(1, GeoUtils.offset(GeoUtils.offset(start, 0.0, 2000.0), 90.0, 200.0))
        val otherWay = report(2, GeoUtils.offset(start, 0.0, 2000.0), heading = 180)
        assertTrue(RoadReports.onRoute(track, listOf(offRoad, otherWay)).isEmpty())
    }

    @Test
    fun nextAheadSkipsPassedOnes() {
        val list = RoadReports.onRoute(track, listOf(report(1, GeoUtils.offset(start, 0.0, 1000.0)), report(2, GeoUtils.offset(start, 0.0, 3000.0))))
        assertEquals(2, RoadReports.nextAhead(list, 1500.0, 2000.0)?.report?.id)
        assertNull(RoadReports.nextAhead(list, 1500.0, 1000.0))
    }

    @Test
    fun boxCoversTheRouteAheadOnly() {
        val box = RoadReports.boxAhead(track, 2500.0)!!
        assertTrue(box[0] > start.latitude) // the first 2.5 km are behind
        assertTrue(box[2] >= GeoUtils.offset(start, 0.0, 5000.0).latitude)
    }

    @Test
    fun boxNeverTooBig() {
        val long = RouteTrack((0..400).map { GeoUtils.offset(start, 45.0, it * 1000.0) }) // 400 km
        val box = RoadReports.boxAhead(long, 0.0)!!
        assertTrue(box[2] - box[0] <= 2.5 && box[3] - box[1] <= 2.5)
    }

    @Test
    fun parsesServerRows() {
        val rows = RoadReports.parse(JSONArray("""[[7,"flood",3.1,101.6,90,300,2,0],[8,"ufo",3.1,101.6,0,1,0,0]]"""))
        assertEquals(1, rows.size)
        assertEquals(RoadReports.Kind.FLOOD, rows[0].kind)
        assertEquals(90, rows[0].heading)
    }

    @Test
    fun reportsGoOnTheTripStrip() {
        val route = com.excellentmindblow.navos.routing.Route(track.source, 5000.0, 300.0, emptyList())
        val on = RoadReports.onRoute(track, listOf(report(1, GeoUtils.offset(start, 0.0, 2000.0))))
        val strip = com.excellentmindblow.navos.navigation.TripStrip.build(route, emptyList(), emptyList(), emptyList(), reports = on)!!
        val m = strip.marks.single()
        assertEquals(com.excellentmindblow.navos.navigation.TripStrip.Kind.REPORT, m.kind)
        assertEquals(RoadReports.Kind.ACCIDENT, m.report)
        assertEquals(2000.0, m.atM, 5.0)
    }
}
