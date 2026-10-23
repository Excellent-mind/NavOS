package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoadLimit
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.SpeedLimitService

/**
 * Keeps the posted speed limits of the roads around the driver. The roads
 * are downloaded on the first fix of a drive, then again once the driver
 * has moved [REFETCH_DISTANCE_M] from the last download - but never more
 * often than every [MIN_REFETCH_MS], and a failed download waits that long
 * too (Overpass is a free shared server).
 *
 * The download runs through [runInBackground]; its result is handed back
 * through [postToMain] and kept only if [stillWanted] says the drive is
 * still on. All other calls are made on the main thread.
 */
class SpeedLimitTracker(
    private val runInBackground: (() -> Unit) -> Unit,
    private val postToMain: (() -> Unit) -> Unit,
    private val clock: () -> Long,
    private val stillWanted: () -> Boolean,
    private val fetch: (latitude: Double, longitude: Double) -> List<RoadLimit> = SpeedLimitService::fetchAround,
) {
    private var roads: List<RoadLimit> = emptyList()
    private var roadsCenter: RoutePoint? = null
    private var fetchInFlight = false
    private var lastFetchAtMs: Long? = null

    /** Starts a download around [here] when one is due. */
    fun onLocation(here: RoutePoint) {
        if (fetchInFlight) return
        val now = clock()
        val last = lastFetchAtMs
        if (last != null && now - last < MIN_REFETCH_MS) return
        val center = roadsCenter
        if (center != null && GeoUtils.distanceMeters(center, here) < REFETCH_DISTANCE_M) return

        fetchInFlight = true
        lastFetchAtMs = now
        runInBackground {
            val fetched =
                try {
                    fetch(here.latitude, here.longitude)
                } catch (e: Exception) {
                    null
                }
            postToMain {
                fetchInFlight = false
                if (fetched == null || !stillWanted()) return@postToMain
                roads = fetched
                roadsCenter = here
            }
        }
    }

    /** The limit (km/h) of the road at [here], or null when no limit is mapped there. */
    fun limitAt(here: RoutePoint): Int? = SpeedLimitService.limitAt(roads, here)

    /** Forgets the downloaded roads, so the next drive downloads afresh. */
    fun reset() {
        roads = emptyList()
        roadsCenter = null
        lastFetchAtMs = null
    }

    companion object {
        /** The download covers 1.5 km around the driver; refresh well before its edge. */
        const val REFETCH_DISTANCE_M = 700.0
        const val MIN_REFETCH_MS = 20_000L

        /** Speeding alert tolerance, so GPS noise doesn't flash the badge red. */
        const val SPEEDING_MARGIN_KMH = 3.0
    }
}
