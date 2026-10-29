package com.excellentmindblow.navos.traffic

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class SmartLanesTest {
    private fun at(local: String) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = SmartLanes.MALAYSIA }.parse(local)!!.time

    private val start = RoutePoint(3.09, 101.50)
    private val laneLine = (0..30).map { GeoUtils.offset(start, 90.0, 1000.0 + it * 100.0) } // 1 km to 4 km east

    private fun lane(
        dated: List<LongRange> = emptyList(),
        line: List<RoutePoint> = laneLine,
    ) = SmartLanes.Lane(
        id = "t",
        name = "Test",
        nameMs = "Ujian",
        highway = "E1",
        speedKmh = 60,
        line = line,
        schedule = listOf(SmartLanes.Window(setOf(1, 2, 3, 4, 5), 6 * 60 + 30, 9 * 60 + 30)),
        dated = dated,
        confirmed = true,
    )

    @Test
    fun openOnWeekdayMorningsOnly() {
        // 2026-10-28 is a Wednesday, 2026-10-31 a Saturday.
        assertTrue(SmartLanes.isOpen(lane(), at("2026-10-28T07:15")))
        assertTrue(SmartLanes.isOpen(lane(), at("2026-10-28T06:30")))
        assertFalse(SmartLanes.isOpen(lane(), at("2026-10-28T09:30")))
        assertFalse(SmartLanes.isOpen(lane(), at("2026-10-28T18:00")))
        assertFalse(SmartLanes.isOpen(lane(), at("2026-10-31T07:15")))
        assertEquals(9 * 60 + 30, SmartLanes.openUntil(lane(), at("2026-10-28T07:15")))
        assertNull(SmartLanes.openUntil(lane(), at("2026-10-28T18:00")))
    }

    @Test
    fun datedActivationOpensOutsideTheSchedule() {
        val raya = at("2026-10-31T07:00") until at("2026-10-31T23:00")
        assertTrue(SmartLanes.isOpen(lane(dated = listOf(raya)), at("2026-10-31T15:00")))
        assertFalse(SmartLanes.isOpen(lane(dated = listOf(raya)), at("2026-11-01T15:00")))
    }

    @Test
    fun onRouteOnlyInTheLanesDirection() {
        val east = RouteTrack((0..60).map { GeoUtils.offset(start, 90.0, it * 100.0) })
        val west = RouteTrack(east.source.reversed())
        val found = SmartLanes.onRoute(east, listOf(lane()))
        assertEquals(1, found.size)
        assertEquals(1000.0, found[0].startM, 15.0)
        assertEquals(4000.0, found[0].endM, 15.0)
        assertTrue(SmartLanes.onRoute(west, listOf(lane())).isEmpty())
    }

    @Test
    fun laneOnAnotherRoadIsIgnored() {
        val north = RouteTrack((0..60).map { GeoUtils.offset(start, 0.0, it * 100.0) })
        assertTrue(SmartLanes.onRoute(north, listOf(lane())).isEmpty())
    }

    @Test
    fun bundledLanesParseAndRemoteReplacesById() {
        val bundled = SmartLanes.parse(File("src/main/assets/smartlanes.json").readText())
        assertEquals(setOf("nkve-setia-alam-shah-alam", "elite-usj-seafield"), bundled.map { it.id }.toSet())
        assertTrue(bundled.all { it.line.size > 10 && it.line.first().latitude in 2.9..3.2 })
        val remote = SmartLanes.parse(
            """{"lanes":[{"id":"elite-usj-seafield","name":"x","line":[[101.5,3.0],[101.6,3.1]],"schedule":[],
              "windows":[{"from":"2026-10-31T00:00:00Z","to":"2026-10-31T15:00:00Z"}]}]}""",
        )
        val merged = SmartLanes.merge(bundled, remote)
        assertEquals(2, merged.size)
        assertEquals(1, merged.first { it.id == "elite-usj-seafield" }.dated.size)
    }
}
