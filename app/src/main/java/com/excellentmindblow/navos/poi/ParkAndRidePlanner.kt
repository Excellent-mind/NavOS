package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint

/**
 * "Park & ride: about 48 min, save 15 min": drive to a station with a Park &
 * Ride car park on the way, take one train (no changes) to a station near
 * the destination, walk the rest - compared with driving all the way (with
 * live traffic, plus finding parking in the city centre). No timetables:
 * the train time is estimated from the distance, so a plan is only offered
 * when it clearly wins. Pure, so it is unit tested.
 */
object ParkAndRidePlanner {
    data class Plan(
        /** Where to park and board. */
        val from: Poi,
        /** Where to get off. */
        val to: Poi,
        val line: TransitLine,
        val driveS: Double,
        val trainS: Double,
        val walkS: Double,
        val totalS: Double,
        val savedS: Double,
    )

    /** Offer a plan only when it saves at least this much. */
    const val MIN_SAVING_S = 600.0

    /** Trains average about 40 km/h including stops. */
    private const val TRAIN_MS = 40 / 3.6
    private const val TRACK_FACTOR = 1.3 // rails aren't straight
    private const val WAIT_S = 300.0
    private const val PARK_S = 180.0 // park and walk to the platform
    private const val WALK_MS = 1.33
    private const val WALK_FACTOR = 1.25
    private const val DETOUR_MS = 8.0 // the drive off the route to the car park

    /** Finding parking in the city centre (KLCC, Bukit Bintang, KL Sentral, Chinatown). */
    const val CITY_PARKING_S = 600.0

    /** The station must come after the first bit of the route and well before the end. */
    private const val MIN_FROM_START_M = 1_000.0
    private const val MAX_SHARE_OF_ROUTE = 0.8
    private const val MAX_OFF_ROUTE_M = 1_500.0

    fun inCityCentre(p: RoutePoint): Boolean = p.latitude in 3.128..3.168 && p.longitude in 101.680..101.725

    /**
     * [track]: the route being previewed; [driveAllS]: its time with traffic;
     * [destination]; [boardAt]: stations with Park & Ride near the route;
     * [alightAt]: stations near the destination. The best plan, or null.
     */
    fun best(
        track: RouteTrack,
        driveAllS: Double,
        destination: RoutePoint,
        boardAt: List<Poi>,
        alightAt: List<Poi>,
    ): Plan? {
        val length = track.length
        if (length <= 0 || track.source.size < 2) return null
        val driveAll = driveAllS + if (inCityCentre(destination)) CITY_PARKING_S else 0.0
        var best: Plan? = null
        for (a in boardAt) {
            val park = a.parkAndRide ?: continue
            val parkAt = RoutePoint(park.latitude, park.longitude)
            val snap = GeoUtils.snapToPolyline(parkAt, track.source) ?: continue
            if (snap.distanceMeters > MAX_OFF_ROUTE_M) continue
            val alongM = track.progressOf(snap)
            if (alongM < MIN_FROM_START_M || alongM > length * MAX_SHARE_OF_ROUTE) continue
            val driveS = driveAllS * (alongM / length) + 2 * snap.distanceMeters / DETOUR_MS + PARK_S
            val aLines = a.lines.associateBy { it.badge.lowercase() }
            for (b in alightAt) {
                if (b.id == a.id) continue
                val line = b.lines.firstOrNull { it.badge.lowercase() in aLines } ?: continue
                val pa = RoutePoint(a.latitude, a.longitude)
                val pb = RoutePoint(b.latitude, b.longitude)
                val trainS = GeoUtils.distanceMeters(pa, pb) * TRACK_FACTOR / TRAIN_MS + WAIT_S
                val walkS = GeoUtils.distanceMeters(pb, destination) * WALK_FACTOR / WALK_MS
                val total = driveS + trainS + walkS
                val saved = driveAll - total
                if (saved >= MIN_SAVING_S && (best == null || total < best.totalS)) {
                    best = Plan(a, b, line, driveS, trainS, walkS, total, saved)
                }
            }
        }
        return best
    }
}
