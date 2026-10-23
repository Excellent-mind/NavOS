package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint

/**
 * Which petrol / EV stations are worth showing during a drive: those close
 * to the route line *ahead*, so the driver can decide to fuel up or carry on.
 * Pure, so it is unit tested.
 */
object PoiAlongRoute {
    /** Further from the route line than this: a detour, not "on the way". */
    const val CORRIDOR_M = 400.0

    /** Just passed: still shown this far back (you may be pulling in). */
    const val BEHIND_M = 150.0

    /** Where a station sits relative to the drive. */
    data class Spot(
        val aheadM: Double,
        val offRouteM: Double,
    )

    /** [poi]'s place along the route, or null when it is off the corridor or behind. */
    fun spot(
        track: RouteTrack,
        poi: RoutePoint,
        hereM: Double,
        fromSegment: Int = 0,
    ): Spot? {
        val snap = GeoUtils.snapToPolyline(poi, track.source, fromSegment.coerceAtLeast(0)) ?: return null
        if (snap.distanceMeters > CORRIDOR_M) return null
        val ahead = track.progressOf(snap) - (if (hereM.isNaN()) 0.0 else hereM)
        if (ahead < -BEHIND_M) return null
        return Spot(ahead, snap.distanceMeters)
    }
}
