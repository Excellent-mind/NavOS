package com.excellentmindblow.navos.traffic

/**
 * Where the route is slow: compares live speeds from other drivers with each
 * road piece's normal (free-flow) speed and marks stretches as slow (amber)
 * or jammed (red), like Google Maps / Waze. Pure, so it is unit tested.
 */
object TrafficFlow {
    enum class Level { SLOW, JAM }

    /** Route points [from]..[to] (inclusive) are at [level]. */
    data class Run(
        val from: Int,
        val to: Int,
        val level: Level,
    )

    /** Below this share of normal speed it's slow; below [JAM_RATIO] jammed. */
    const val SLOW_RATIO = 0.8
    const val JAM_RATIO = 0.5

    /** Normal speed must be at least this (m/s) to judge - car parks, U-turns are noise. */
    const val MIN_FREE_FLOW_MS = 4.0

    fun levelOf(
        liveKmh: Double,
        freeFlowMs: Double,
    ): Level? {
        if (freeFlowMs < MIN_FREE_FLOW_MS) return null
        val ratio = liveKmh / (freeFlowMs * 3.6)
        return when {
            ratio < JAM_RATIO -> Level.JAM
            ratio < SLOW_RATIO -> Level.SLOW
            else -> null
        }
    }

    /**
     * Slow / jammed stretches of a route from point [fromIndex] on, merging
     * neighbouring pieces at the same level. [live] maps "nodeA-nodeB" to km/h.
     */
    fun runs(
        nodes: LongArray,
        freeFlowMs: DoubleArray,
        live: Map<String, Double>,
        fromIndex: Int = 0,
    ): List<Run> {
        val out = ArrayList<Run>()
        var start = -1
        var level: Level? = null
        for (i in fromIndex.coerceAtLeast(0) until nodes.size - 1) {
            val kmh = live["${nodes[i]}-${nodes[i + 1]}"]
            val l = if (kmh == null) null else levelOf(kmh, freeFlowMs[i])
            if (l != level) {
                if (level != null) out += Run(start, i, level)
                start = i
                level = l
            }
        }
        if (level != null) out += Run(start, nodes.size - 1, level)
        return out
    }

    /** Road pieces of the route from point [fromIndex], up to [maxPieces], to ask the server about. */
    fun piecesAhead(
        nodes: LongArray,
        fromIndex: Int,
        maxPieces: Int = 1500,
    ): List<String> {
        val out = LinkedHashSet<String>()
        var i = fromIndex.coerceAtLeast(0)
        while (i < nodes.size - 1 && out.size < maxPieces) {
            out += "${nodes[i]}-${nodes[i + 1]}"
            i++
        }
        return out.toList()
    }
}
