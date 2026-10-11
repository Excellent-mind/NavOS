package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils

/**
 * Small helpers for deciding the ORDER of a multi-stop trip. They work on
 * straight-line distances only - no routing requests - so they are instant and
 * work offline; the real road route is then calculated for whatever order they
 * pick. Good enough to choose between orders, which is all they are used for.
 */
object TripPlanner {
    /** Most intermediate stops a trip can have (destination not counted). */
    const val MAX_STOPS = 8

    /**
     * Where to slot a newly added stop among [stops] so the detour is the
     * smallest: the position (0 = first stop ... stops.size = last stop before
     * the destination) that adds the least straight-line distance to
     * [start] -> [stops] -> [destination]. The destination always stays last.
     */
    fun cheapestInsertIndex(
        start: RoutePoint,
        stops: List<RoutePoint>,
        destination: RoutePoint,
        newPoint: RoutePoint,
    ): Int {
        var bestIndex = stops.size
        var bestExtra = Double.MAX_VALUE
        for (i in 0..stops.size) {
            val a = if (i == 0) start else stops[i - 1]
            val b = if (i == stops.size) destination else stops[i]
            val extra =
                GeoUtils.distanceMeters(a, newPoint) + GeoUtils.distanceMeters(newPoint, b) - GeoUtils.distanceMeters(a, b)
            if (extra < bestExtra) {
                bestExtra = extra
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * The visiting order of [stops] (as indices into it) that makes
     * [start] -> stops -> [destination] shortest. Start and destination stay
     * where they are; only the stops in between are reordered. Tries every
     * order (at most 8! = 40,320), so the answer is exact for the straight-line
     * measure.
     */
    fun bestOrder(
        start: RoutePoint,
        stops: List<RoutePoint>,
        destination: RoutePoint,
    ): List<Int> {
        val n = stops.size
        if (n < 2) return stops.indices.toList()
        val d = Array(n + 2) { DoubleArray(n + 2) }
        val all = listOf(start) + stops + destination
        for (i in all.indices) for (j in all.indices) d[i][j] = GeoUtils.distanceMeters(all[i], all[j])
        val destNode = n + 1

        var best = Double.MAX_VALUE
        var bestPerm = IntArray(n) { it }
        val perm = IntArray(n) { it }

        fun length(): Double {
            var total = d[0][perm[0] + 1]
            for (i in 0 until n - 1) total += d[perm[i] + 1][perm[i + 1] + 1]
            return total + d[perm[n - 1] + 1][destNode]
        }

        // Heap's algorithm - every permutation, one swap at a time.
        val c = IntArray(n)
        var len = length()
        if (len < best) {
            best = len
            bestPerm = perm.copyOf()
        }
        var i = 0
        while (i < n) {
            if (c[i] < i) {
                val a = if (i % 2 == 0) 0 else c[i]
                val tmp = perm[a]
                perm[a] = perm[i]
                perm[i] = tmp
                len = length()
                if (len < best) {
                    best = len
                    bestPerm = perm.copyOf()
                }
                c[i] += 1
                i = 0
            } else {
                c[i] = 0
                i += 1
            }
        }
        return bestPerm.toList()
    }
}
