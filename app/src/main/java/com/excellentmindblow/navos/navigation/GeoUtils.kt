package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoutePoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native port of legacy-expo-rn/src/services/geo.ts - same great-circle
 * distance, bearing, and point-to-polyline distance math, used for
 * off-route detection and step-advance decisions.
 */
object GeoUtils {
    private const val EARTH_RADIUS_M = 6371000.0

    private fun toRad(deg: Double) = deg * PI / 180

    /** Great-circle distance between two points, in meters. */
    fun distanceMeters(
        a: RoutePoint,
        b: RoutePoint,
    ): Double {
        val dLat = toRad(b.latitude - a.latitude)
        val dLng = toRad(b.longitude - a.longitude)
        val lat1 = toRad(a.latitude)
        val lat2 = toRad(b.latitude)

        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** The point [meters] from [p] towards compass [bearingDeg] (flat-earth; fine for a few km). */
    fun offset(
        p: RoutePoint,
        bearingDeg: Double,
        meters: Double,
    ): RoutePoint {
        if (meters == 0.0) return p
        val b = Math.toRadians(bearingDeg)
        val mPerDeg = Math.toRadians(1.0) * EARTH_RADIUS_M
        val dLat = meters * Math.cos(b) / mPerDeg
        val dLon = meters * Math.sin(b) / (mPerDeg * Math.cos(Math.toRadians(p.latitude)).coerceAtLeast(0.2))
        return RoutePoint(p.latitude + dLat, p.longitude + dLon)
    }

    /** Compass bearing from a to b, in degrees (0 = north). */
    fun bearingDegrees(
        a: RoutePoint,
        b: RoutePoint,
    ): Double {
        val lat1 = toRad(a.latitude)
        val lat2 = toRad(b.latitude)
        val dLng = toRad(b.longitude - a.longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val deg = atan2(y, x) * 180 / PI
        return (deg % 360 + 360) % 360
    }

    /** Smallest angle between two compass bearings, 0..180 degrees. */
    fun angleDiff(
        a: Double,
        b: Double,
    ): Double {
        var d = ((a - b) % 360.0 + 360.0) % 360.0
        if (d > 180.0) d -= 360.0
        return Math.abs(d)
    }

    /** Direction the route line heads just AFTER [point] (0 = north),
     * measured to a spot ~10 m further along it. */
    fun outgoingBearing(
        geometry: List<RoutePoint>,
        point: RoutePoint,
    ): Double {
        if (geometry.size < 2) return 0.0
        var nearest = 0
        var best = Double.MAX_VALUE
        for (i in geometry.indices) {
            val d = distanceMeters(geometry[i], point)
            if (d < best) {
                best = d
                nearest = i
            }
        }
        if (nearest >= geometry.size - 1) return bearingDegrees(geometry[geometry.size - 2], geometry[geometry.size - 1])
        var j = nearest + 1
        while (j < geometry.size - 1 && distanceMeters(geometry[nearest], geometry[j]) < 10.0) j++
        return bearingDegrees(geometry[nearest], geometry[j])
    }

    /** Distance (meters) from a point to the nearest point on a polyline,
     * and the index of that nearest segment's starting vertex. Used to
     * detect when the driver has drifted off the planned route. */
    fun distanceToPolyline(
        point: RoutePoint,
        line: List<RoutePoint>,
    ): Pair<Double, Int> {
        var best = Double.MAX_VALUE
        var bestIndex = 0
        for (i in 0 until line.size - 1) {
            val d = distanceToSegment(point, line[i], line[i + 1])
            if (d < best) {
                best = d
                bestIndex = i
            }
        }
        return best to bestIndex
    }

    private fun distanceToSegment(
        p: RoutePoint,
        a: RoutePoint,
        b: RoutePoint,
    ): Double {
        // Approximate by projecting in a local flat-earth frame; good
        // enough at city-block scale for off-route detection.
        val cosLatA = cos(toRad(a.latitude))
        fun toXY(pt: RoutePoint) = doubleArrayOf(pt.longitude * cosLatA, pt.latitude)

        val pXY = toXY(p)
        val aXY = toXY(a)
        val bXY = toXY(b)

        val dx = bXY[0] - aXY[0]
        val dy = bXY[1] - aXY[1]
        val lengthSq = dx * dx + dy * dy

        var t = if (lengthSq == 0.0) 0.0 else ((pXY[0] - aXY[0]) * dx + (pXY[1] - aXY[1]) * dy) / lengthSq
        t = t.coerceIn(0.0, 1.0)

        val closest =
            RoutePoint(
                latitude = a.latitude + t * (b.latitude - a.latitude),
                longitude = a.longitude + t * (b.longitude - a.longitude),
            )
        return distanceMeters(p, closest)
    }

    /** Where a GPS point lands on the route line. */
    data class RouteSnap(
        val point: RoutePoint,
        val bearing: Double,
        val distanceMeters: Double,
        val segmentIndex: Int,
    )

    /** Projects [p] onto the polyline and returns the closest spot on it,
     * the line's own heading there, and how far off the line the GPS fix
     * was. Only segments from [startIndex] for [maxSegments] are searched,
     * so a parallel road or a later loop of the route can't steal the match
     * and long routes stay cheap. Null when the line is too short. */
    fun snapToPolyline(
        p: RoutePoint,
        line: List<RoutePoint>,
        startIndex: Int = 0,
        maxSegments: Int = Int.MAX_VALUE,
    ): RouteSnap? {
        if (line.size < 2) return null
        val first = startIndex.coerceIn(0, line.size - 2)
        val last = if (maxSegments >= line.size) line.size - 2 else (first + maxSegments).coerceAtMost(line.size - 2)
        var best: RouteSnap? = null
        for (i in first..last) {
            val a = line[i]
            val b = line[i + 1]
            val cosLat = cos(toRad(a.latitude))
            val dx = (b.longitude - a.longitude) * cosLat
            val dy = b.latitude - a.latitude
            val lengthSq = dx * dx + dy * dy
            if (lengthSq == 0.0) continue
            val px = (p.longitude - a.longitude) * cosLat
            val py = p.latitude - a.latitude
            val t = ((px * dx + py * dy) / lengthSq).coerceIn(0.0, 1.0)
            val closest =
                RoutePoint(
                    latitude = a.latitude + t * (b.latitude - a.latitude),
                    longitude = a.longitude + t * (b.longitude - a.longitude),
                )
            val d = distanceMeters(p, closest)
            val currentBest = best
            if (currentBest == null || d < currentBest.distanceMeters) {
                best = RouteSnap(closest, bearingDegrees(a, b), d, i)
            }
        }
        return best
    }

    /** Heading of the route line itself a little way AHEAD of [snap] - the
     * direction the lane is about to go, not the direction of the tiny
     * segment underfoot. Looking ahead makes the arrow/camera start turning
     * into a corner before the arrow reaches it, and stops the heading from
     * flicking at every vertex. Works at any speed (stationary included). */
    fun bearingAhead(
        line: List<RoutePoint>,
        snap: RouteSnap,
        aheadMeters: Double,
    ): Double {
        var from = snap.point
        var travelled = 0.0
        var i = snap.segmentIndex + 1
        var target = from
        while (i < line.size) {
            val next = line[i]
            val d = distanceMeters(from, next)
            if (travelled + d >= aheadMeters) {
                val t = if (d == 0.0) 0.0 else (aheadMeters - travelled) / d
                target =
                    RoutePoint(
                        latitude = from.latitude + (next.latitude - from.latitude) * t,
                        longitude = from.longitude + (next.longitude - from.longitude) * t,
                    )
                return bearingDegrees(snap.point, target)
            }
            travelled += d
            from = next
            target = next
            i++
        }
        return if (target == snap.point) snap.bearing else bearingDegrees(snap.point, target)
    }
}
