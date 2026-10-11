package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint

/**
 * The route as a distance-indexed track: "how many metres from the start"
 * <-> a point on the line and the line's direction there.
 *
 * The arrow is moved by distance along this track, not by sliding in a
 * straight line between two GPS points. A straight slide cuts across the
 * inside of every bend, which is what made the arrow leave the blue line
 * in corners; moving by distance keeps it on the line through any bend.
 */
class RouteTrack(
    val source: List<RoutePoint>,
) {
    private val cum = DoubleArray(source.size)

    init {
        for (i in 1 until source.size) {
            cum[i] = cum[i - 1] + GeoUtils.distanceMeters(source[i - 1], source[i])
        }
    }

    val length: Double get() = if (source.isEmpty()) 0.0 else cum[source.size - 1]

    /** Distance from the start of the route to a snapped GPS fix. */
    fun progressOf(snap: GeoUtils.RouteSnap): Double {
        val i = snap.segmentIndex.coerceIn(0, source.size - 2)
        return cum[i] + GeoUtils.distanceMeters(source[i], snap.point)
    }

    /** The point [meters] from the start (clamped to the route). */
    fun pointAt(meters: Double): RoutePoint {
        val d = meters.coerceIn(0.0, length)
        val i = segmentAt(d)
        val a = source[i]
        val b = source[i + 1]
        val segLen = cum[i + 1] - cum[i]
        val t = if (segLen <= 0.0) 0.0 else (d - cum[i]) / segLen
        return RoutePoint(
            latitude = a.latitude + (b.latitude - a.latitude) * t,
            longitude = a.longitude + (b.longitude - a.longitude) * t,
        )
    }

    /** Direction of travel at [meters], measured to the point [aheadMeters]
     * further down the line (a short look-ahead = the local direction, a
     * long one = where the road is heading). */
    fun bearingAt(
        meters: Double,
        aheadMeters: Double,
    ): Double {
        val len = length
        // Near the end there is no "ahead": look back instead so the
        // heading stays that of the final stretch.
        val from = if (meters + aheadMeters > len) (len - aheadMeters).coerceAtLeast(0.0) else meters.coerceAtLeast(0.0)
        val to = (from + aheadMeters).coerceAtMost(len)
        val a = pointAt(from)
        val b = pointAt(to)
        if (a.latitude == b.latitude && a.longitude == b.longitude) {
            return GeoUtils.bearingDegrees(source[source.size - 2], source[source.size - 1])
        }
        return GeoUtils.bearingDegrees(a, b)
    }

    private fun segmentAt(d: Double): Int {
        var lo = 0
        var hi = source.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cum[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }
}
