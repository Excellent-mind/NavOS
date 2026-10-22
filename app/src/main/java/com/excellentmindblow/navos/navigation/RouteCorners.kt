package com.excellentmindblow.navos.navigation

import kotlin.math.abs

/** A bend in the route, from [startM] to [endM] metres along it, turning
 * [turnDeg] degrees in total (+ right, - left). */
data class RouteCorner(
    val startM: Double,
    val endM: Double,
    val turnDeg: Double,
)

/**
 * Finds every bend along a route - junction turns and the small curves
 * between them - so guide chevrons can be drawn through each one.
 *
 * The line is sampled every [STEP_M]; at each sample the heading over the
 * [ARM_M] before it is compared with the heading over the [ARM_M] after it.
 * Samples turning at least [MIN_SAMPLE_TURN_DEG] the same way, with no more
 * than [GAP_M] of straighter road between them, form one corner; a corner is kept when its whole turn is at least
 * [MIN_CORNER_TURN_DEG]. Pure, so it is unit tested.
 */
object RouteCorners {
    const val STEP_M = 5.0
    const val ARM_M = 15.0
    const val MIN_SAMPLE_TURN_DEG = 7.0
    const val MIN_CORNER_TURN_DEG = 20.0

    /** A curve may flatten for this long and still be one corner. */
    const val GAP_M = 20.0

    /** Extra line drawn before and after the bend itself. */
    const val LEAD_M = 10.0

    fun find(track: RouteTrack): List<RouteCorner> {
        val len = track.length
        if (len < ARM_M * 2 + STEP_M) return emptyList()
        val out = ArrayList<RouteCorner>()
        var runStart = Double.NaN
        var runEnd = Double.NaN
        var runSign = 0
        var s = ARM_M
        while (s <= len - ARM_M) {
            val turn = turnAt(track, s)
            val sign = if (turn >= MIN_SAMPLE_TURN_DEG) 1 else if (turn <= -MIN_SAMPLE_TURN_DEG) -1 else 0
            when {
                sign == 0 -> {
                    // A quiet stretch: a long one ends the corner, a short one
                    // is just the flatter middle of a smooth curve.
                    if (runSign != 0 && s - runEnd > GAP_M) {
                        close(track, runStart, runEnd, out)
                        runSign = 0
                    }
                }
                sign == runSign -> runEnd = s
                else -> {
                    if (runSign != 0) close(track, runStart, runEnd, out)
                    runSign = sign
                    runStart = s
                    runEnd = s
                }
            }
            s += STEP_M
        }
        if (runSign != 0) close(track, runStart, runEnd, out)
        return out
    }

    private fun close(
        track: RouteTrack,
        from: Double,
        to: Double,
        out: MutableList<RouteCorner>,
    ) {
        val before = track.bearingAt((from - ARM_M).coerceAtLeast(0.0), ARM_M)
        val after = track.bearingAt(to, ARM_M)
        val total = norm(after - before)
        if (abs(total) < MIN_CORNER_TURN_DEG) return
        out += RouteCorner((from - LEAD_M).coerceAtLeast(0.0), (to + LEAD_M).coerceAtMost(track.length), total)
    }

    private fun turnAt(
        track: RouteTrack,
        s: Double,
    ): Double {
        val inBearing = GeoUtils.bearingDegrees(track.pointAt(s - ARM_M), track.pointAt(s))
        val outBearing = GeoUtils.bearingDegrees(track.pointAt(s), track.pointAt(s + ARM_M))
        return norm(outBearing - inBearing)
    }

    private fun norm(d: Double): Double = ((d + 540.0) % 360.0) - 180.0
}
