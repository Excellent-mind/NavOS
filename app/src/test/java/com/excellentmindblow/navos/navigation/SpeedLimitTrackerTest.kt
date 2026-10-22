package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoadLimit
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitTrackerTest {
    // Jalan Ampang, running east from KLCC.
    private val start = RoutePoint(3.1600, 101.7120)
    private val road = RoadLimit(60, listOf(RoutePoint(3.1600, 101.7100), RoutePoint(3.1600, 101.7300)))

    private var now = 100_000L
    private var wanted = true
    private val fetchedAt = ArrayList<RoutePoint>()
    private val background = ArrayList<() -> Unit>()
    private val main = ArrayList<() -> Unit>()
    private var fails = false

    private val tracker =
        SpeedLimitTracker(
            runInBackground = { background += it },
            postToMain = { main += it },
            clock = { now },
            stillWanted = { wanted },
            fetch = { lat, lon ->
                fetchedAt += RoutePoint(lat, lon)
                if (fails) throw RuntimeException("Overpass down")
                listOf(road)
            },
        )

    private fun finishDownloads() {
        background.toList().forEach { it() }
        background.clear()
        main.toList().forEach { it() }
        main.clear()
    }

    /** [meters] east of [start] along the road. */
    private fun east(meters: Double) = RoutePoint(start.latitude, start.longitude + meters / 111_320.0)

    @Test
    fun theFirstFixDownloadsAndThenTheLimitIsKnown() {
        assertNull(tracker.limitAt(start))
        tracker.onLocation(start)
        finishDownloads()
        assertEquals(1, fetchedAt.size)
        assertEquals(60, tracker.limitAt(start))
        assertNull(tracker.limitAt(RoutePoint(3.1700, 101.7120))) // 1 km off the road
    }

    @Test
    fun onlyOneDownloadRunsAtATime() {
        tracker.onLocation(start)
        now += 60_000
        tracker.onLocation(east(1000.0))
        assertEquals(1, background.size)
    }

    @Test
    fun noNewDownloadUntilTheDriverHasMoved700m() {
        tracker.onLocation(start)
        finishDownloads()
        now += 60_000
        tracker.onLocation(east(650.0))
        finishDownloads()
        assertEquals(1, fetchedAt.size)

        tracker.onLocation(east(750.0))
        finishDownloads()
        assertEquals(2, fetchedAt.size)
    }

    @Test
    fun noNewDownloadWithin20sEvenAfterMovingFar() {
        tracker.onLocation(start)
        finishDownloads()
        now += 19_000
        tracker.onLocation(east(1500.0))
        finishDownloads()
        assertEquals(1, fetchedAt.size)

        now += 1_000
        tracker.onLocation(east(1500.0))
        finishDownloads()
        assertEquals(2, fetchedAt.size)
    }

    @Test
    fun aFailedDownloadKeepsTheOldRoadsAndWaits20s() {
        tracker.onLocation(start)
        finishDownloads()
        fails = true
        now += 30_000
        tracker.onLocation(east(1000.0))
        finishDownloads()
        assertEquals(60, tracker.limitAt(start))

        now += 10_000
        tracker.onLocation(east(1000.0))
        finishDownloads()
        assertEquals(2, fetchedAt.size)
    }

    @Test
    fun aDownloadFinishingAfterTheDriveEndedIsDropped() {
        tracker.onLocation(start)
        wanted = false
        finishDownloads()
        assertNull(tracker.limitAt(start))
    }

    @Test
    fun resetForgetsTheRoadsAndAllowsAnImmediateDownload() {
        tracker.onLocation(start)
        finishDownloads()
        tracker.reset()
        assertNull(tracker.limitAt(start))
        tracker.onLocation(start)
        finishDownloads()
        assertEquals(2, fetchedAt.size)
        assertEquals(60, tracker.limitAt(start))
    }
}
