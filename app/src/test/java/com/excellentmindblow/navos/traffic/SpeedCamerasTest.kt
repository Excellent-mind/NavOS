package com.excellentmindblow.navos.traffic

import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeedCamerasTest {
    // A straight road heading east along the equator-ish line at 3.0 N, ~11 km long.
    private val road = (0..100).map { RoutePoint(3.0, 101.5 + it * 0.001) }
    private val track = RouteTrack(road)

    private fun cam(
        latOffsetM: Double,
        lon: Double,
        dir: Int?,
        limit: Int? = 110,
    ) = SpeedCameras.Camera(3.0 + latOffsetM / 111_000.0, lon, limit, dir, false, "")

    @Test
    fun cameraOnYourCarriagewayCounts() {
        val on = SpeedCameras.onRoute(track, listOf(cam(-15.0, 101.55, 90)))
        assertEquals(1, on.size)
        assertEquals(5500.0, on[0].atM, 120.0)
    }

    @Test
    fun cameraWatchingTheOtherWayIsIgnoredUnlessOnTheLine() {
        assertTrue(SpeedCameras.onRoute(track, listOf(cam(-30.0, 101.55, 270))).isEmpty())
        assertEquals(1, SpeedCameras.onRoute(track, listOf(cam(-5.0, 101.55, 270))).size)
    }

    @Test
    fun farCamerasAreIgnored() {
        assertTrue(SpeedCameras.onRoute(track, listOf(cam(-300.0, 101.55, null))).isEmpty())
        assertTrue(SpeedCameras.onRoute(track, listOf(cam(0.0, 103.0, null))).isEmpty())
    }

    @Test
    fun warnsEarlierOnFastRoads() {
        val on = SpeedCameras.onRoute(track, listOf(cam(0.0, 101.55, null, 110), cam(0.0, 101.58, null, 60)))
        assertNull(SpeedCameras.nextToWarn(on, 4000.0))
        assertNotNull(SpeedCameras.nextToWarn(on, 4600.0))
        // The 60 km/h one (~8.9 km along) only within 500 m.
        assertNull(SpeedCameras.nextToWarn(on, 8000.0))
        assertNotNull(SpeedCameras.nextToWarn(on, 8500.0))
    }

    @Test
    fun bundledDataParses() {
        val cams = SpeedCameras.parse(File("src/main/assets/cameras.json").readText())
        assertTrue(cams.size > 30)
        assertTrue(cams.all { it.latitude in 0.5..7.6 && it.longitude in 99.0..120.0 })
    }
}
