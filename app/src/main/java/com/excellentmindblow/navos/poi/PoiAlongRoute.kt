package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint

/**
 * Which petrol / EV stations are worth showing during a drive: those on the
 * way - near the route line *ahead* - so the driver can decide to fuel up or
 * carry on. Pure, so it is unit tested.
 *
 * On an expressway the R&R across the highway is as close to the line as the
 * one on your side, but you can't get to it. Malaysia drives on the left, so
 * there only stations on the left count. Off the expressway a station can be
 * on either side and a little further off (just past a junction).
 */
object PoiAlongRoute {
    /** Expressway: R&Rs sit right by the road. */
    const val EXPRESSWAY_CORRIDOR_M = 400.0

    /** Other roads: a station just off a junction is still on the way. */
    const val ROAD_CORRIDOR_M = 600.0

    /** Closer than this to the line it counts as on the road (shared R&R, a bridge). */
    const val ON_LINE_M = 40.0

    /** Just passed: still shown this far back (you may be pulling in). */
    const val BEHIND_M = 150.0

    /** Where a station sits relative to the drive. */
    data class Spot(
        val aheadM: Double,
        val offRouteM: Double,
    )

    /** [poi]'s place along the route, or null when it is off the way or behind. */
    fun spot(
        track: RouteTrack,
        poi: RoutePoint,
        hereM: Double,
        fromSegment: Int = 0,
        onExpressway: (progressM: Double) -> Boolean = { false },
    ): Spot? {
        val snap = GeoUtils.snapToPolyline(poi, track.source, fromSegment.coerceAtLeast(0)) ?: return null
        val progress = track.progressOf(snap)
        val expressway = onExpressway(progress)
        if (snap.distanceMeters > if (expressway) EXPRESSWAY_CORRIDOR_M else ROAD_CORRIDOR_M) return null
        if (expressway && snap.distanceMeters > ON_LINE_M && !onLeft(track, progress, poi)) return null
        val ahead = progress - (if (hereM.isNaN()) 0.0 else hereM)
        if (ahead < -BEHIND_M) return null
        return Spot(ahead, snap.distanceMeters)
    }

    /** Is [poi] on the left of the direction of travel at [progressM]? */
    private fun onLeft(
        track: RouteTrack,
        progressM: Double,
        poi: RoutePoint,
    ): Boolean {
        val road = track.bearingAt(progressM, 30.0)
        val toPoi = GeoUtils.bearingDegrees(track.pointAt(progressM), poi)
        val rel = ((toPoi - road) % 360.0 + 360.0) % 360.0 // 0..360, clockwise from the road's direction
        return rel > 180.0
    }
}
