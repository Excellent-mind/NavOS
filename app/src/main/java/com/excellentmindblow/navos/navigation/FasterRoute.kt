package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint

/**
 * Whether a route found mid-drive is worth offering instead of the current
 * one ("Faster route · saves 6 min"), like Google Maps / Waze. A candidate
 * has to save real time - at least [MIN_SAVE_S] and [MIN_SAVE_SHARE] of what
 * is left - and be a genuinely different road (not the same route with a
 * slightly different time). Pure, so it is unit tested.
 */
object FasterRoute {
    const val MIN_SAVE_S = 240.0
    const val MIN_SAVE_SHARE = 0.10

    /** A candidate sharing more than this much of its length with the current route is the same way. */
    const val MAX_SHARED = 0.85

    /** Points closer than this to the current route count as on it. */
    private const val ON_ROUTE_M = 35.0

    data class Candidate(
        val index: Int,
        /** Free-road time from the routing server plus its live-traffic delay. */
        val totalS: Double,
        /** Share of its points that lie on the current route, 0..1. */
        val sharedWithCurrent: Double,
    )

    /** The best candidate worth offering (with the seconds it saves), or null. */
    fun pick(
        currentTotalS: Double,
        candidates: List<Candidate>,
    ): Pair<Candidate, Double>? {
        if (currentTotalS <= 0) return null
        val needed = maxOf(MIN_SAVE_S, currentTotalS * MIN_SAVE_SHARE)
        return candidates
            .filter { it.sharedWithCurrent <= MAX_SHARED }
            .map { it to currentTotalS - it.totalS }
            .filter { it.second >= needed }
            .maxByOrNull { it.second }
    }

    /** Share of [candidate]'s points (sampled) that are on [current], 0..1. */
    fun sharedShare(
        candidate: List<RoutePoint>,
        current: List<RoutePoint>,
    ): Double {
        if (candidate.size < 2 || current.size < 2) return 0.0
        val step = maxOf(1, candidate.size / 60)
        var on = 0
        var n = 0
        for (i in candidate.indices step step) {
            n++
            if (GeoUtils.distanceToPolyline(candidate[i], current).first <= ON_ROUTE_M) on++
        }
        return on.toDouble() / n
    }
}
