package com.excellentmindblow.navos.location

import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import kotlin.math.ln
import kotlin.math.pow

/**
 * Keeps the arrow moving along the route when GPS drops out - tunnels
 * (SMART, Jalan Duta, Penchala...), multi-storey car parks, dense city
 * blocks - the way Google Maps does, instead of freezing at the entrance.
 *
 * From the last real fix it carries on along the route line at the last
 * speed, slowly easing off ([DECAY_PER_S] per second, since traffic in a
 * tunnel often slows), for at most [MAX_S] seconds. It stops [END_MARGIN_M]
 * short of the end of the route: only a real fix may say "you have arrived".
 * Pure, so it is unit tested.
 */
class DeadReckoning(
    private val track: RouteTrack,
) {
    private var startProgressM = 0.0
    private var startSpeedMs = 0.0
    private var startMs = 0L
    var active = false
        private set

    fun start(
        progressM: Double,
        speedMs: Double,
        timeMs: Long,
    ) {
        startProgressM = progressM
        startSpeedMs = speedMs
        startMs = timeMs
        active = true
    }

    fun stop() {
        active = false
    }

    /** Estimated spot on the route at [timeMs]: point, heading, distance along, speed. Null when inactive or too long. */
    fun estimate(timeMs: Long): Estimate? {
        if (!active) return null
        val t = (timeMs - startMs) / 1000.0
        if (t < 0 || t > MAX_S) {
            active = false
            return null
        }
        // Distance = integral of v0 * r^t dt = v0 * (r^t - 1) / ln r.
        val r = DECAY_PER_S
        val travelled = startSpeedMs * (r.pow(t) - 1.0) / ln(r)
        val progress = (startProgressM + travelled).coerceAtMost(maxOf(startProgressM, track.length - END_MARGIN_M))
        val speed = startSpeedMs * r.pow(t)
        return Estimate(track.pointAt(progress), track.bearingAt(progress, 20.0), progress, speed, t)
    }

    data class Estimate(
        val point: RoutePoint,
        val bearingDeg: Double,
        val progressM: Double,
        val speedMs: Double,
        val secondsSinceFix: Double,
    )

    companion object {
        /** No fix for this long while moving: start estimating. */
        const val SILENCE_MS = 3_000L

        /** Only when actually driving (~15 km/h+); standing still needs no estimate. */
        const val MIN_SPEED_MS = 4.0

        const val DECAY_PER_S = 0.99
        const val MAX_S = 120.0

        /** Never estimated closer than this to the destination. */
        const val END_MARGIN_M = 50.0
    }
}
