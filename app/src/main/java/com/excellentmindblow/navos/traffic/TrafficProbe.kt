package com.excellentmindblow.navos.traffic

/**
 * Turns a drive into anonymous road speeds for live traffic: one average
 * speed for each road piece (OSM "nodeA-nodeB") the car went along.
 *
 * Fed with the car's distance along the route and its GPS speed on every
 * fix. When the car moves on from a piece, the piece's average speed is
 * queued for [drain]. Pieces skipped between two fixes (short ones at
 * speed) get the speed of the stretch around them.
 *
 * Privacy: pieces in the first and last [PRIVACY_M] of the trip are never
 * reported, so nobody can tell where a drive started or ended. There is no
 * time from the phone and no account in what is sent. Each piece is sent
 * with where it is on the map (its two OpenStreetMap points, the same public
 * place its node ids already name), so the server can colour roads by area;
 * the server stores that per piece, not per drive or sender.
 *
 * Pure, so it is unit tested.
 */
class TrafficProbe(
    private val nodes: LongArray,
    /** Distance from the route start of each route point (same length as [nodes]). */
    private val cumM: DoubleArray,
) {
    private val length = cumM.last()
    private var seg = -1
    private var speedSum = 0.0
    private var samples = 0
    private val pending = ArrayList<Pair<String, Double>>()

    fun onFix(
        progressM: Double,
        speedMs: Double,
    ) {
        if (progressM.isNaN() || speedMs.isNaN() || speedMs < 0.0) return
        val now = segmentAt(progressM)
        if (seg < 0) {
            seg = now
        }
        if (now == seg) {
            speedSum += speedMs
            samples++
            return
        }
        if (now < seg) return // GPS jitter backwards: ignore
        val kmh = if (samples > 0) speedSum / samples * 3.6 else speedMs * 3.6
        report(seg, kmh)
        // Pieces driven through between two fixes: same speed as around them.
        for (i in seg + 1 until now) report(i, (kmh + speedMs * 3.6) / 2)
        seg = now
        speedSum = speedMs
        samples = 1
    }

    /** Queued (piece, km/h) pairs, oldest first; at most [max]. */
    fun drain(max: Int = MAX_BATCH): List<Pair<String, Double>> {
        val n = minOf(max, pending.size)
        val out = pending.subList(0, n).toList()
        pending.subList(0, n).clear()
        return out
    }

    val queued: Int get() = pending.size

    private fun report(
        i: Int,
        kmh: Double,
    ) {
        if (i < 0 || i >= nodes.size - 1) return
        if (cumM[i] < PRIVACY_M || cumM[i + 1] > length - PRIVACY_M) return
        if (cumM[i + 1] - cumM[i] < MIN_PIECE_M && kmh > 5.0) return // too short to time well
        if (nodes[i] <= 0 || nodes[i + 1] <= 0) return
        pending += "${nodes[i]}-${nodes[i + 1]}" to (Math.round(kmh * 10) / 10.0)
        if (pending.size > MAX_QUEUE) pending.subList(0, pending.size - MAX_QUEUE).clear()
    }

    private fun segmentAt(d: Double): Int {
        var lo = 0
        var hi = cumM.size - 2
        val x = d.coerceIn(0.0, length)
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumM[mid] <= x) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        const val PRIVACY_M = 300.0
        const val MIN_PIECE_M = 3.0
        const val MAX_BATCH = 150
        const val MAX_QUEUE = 2000
    }
}
