package com.excellentmindblow.navos.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Blends location fixes from several sources (satellite GPS, Android's own
 * fused provider, Wi-Fi / cell network) into one steady position, the way
 * Google Maps does - instead of trusting whichever single provider happened
 * to be on.
 *
 *  - Picks the best source: network fixes (100 m+ accurate) are ignored
 *    while a good satellite fix is fresh, and are used when there is none
 *    (indoors, first seconds after start).
 *  - Drops impossible jumps (faster than [MAX_SPEED_MS]) - unless they keep
 *    coming, which means the old position was the wrong one.
 *  - Smooths what is left with a Kalman filter: position and velocity on
 *    each axis, trusting each fix by its own reported accuracy, so a 4 m
 *    GPS fix moves the arrow and a 60 m one barely nudges it. GPS Doppler
 *    speed and bearing, when given, correct the velocity directly.
 *
 * Pure (no Android), so it is unit tested.
 */
class LocationFusion {
    enum class Source { GPS, FUSED, NETWORK }

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        /** Horizontal accuracy, metres (68%). */
        val accuracyM: Double,
        val speedMs: Double?,
        val bearingDeg: Double?,
        val timeMs: Long,
        val source: Source,
    )

    private var originLat = Double.NaN
    private var originLon = 0.0
    private var cosLat = 1.0

    // Kalman state per axis: position (m from origin) and velocity (m/s), with covariance.
    private val x = Axis()
    private val y = Axis()
    private var lastTimeMs = 0L
    private var lastGoodSatelliteMs = Long.MIN_VALUE / 2
    private var rejectedInARow = 0

    /** The blended position after [fix], or null when the fix was discarded. */
    fun accept(fix: Fix): Fix? {
        if (fix.accuracyM <= 0.0 || fix.accuracyM.isNaN()) return null
        val satellite = fix.source != Source.NETWORK
        if (!originLat.isNaN()) {
            if (fix.timeMs <= lastTimeMs) return null // older than what we have (another provider's late copy)
            if (!satellite && fix.timeMs - lastGoodSatelliteMs < NETWORK_YIELD_MS) return null
        }
        if (satellite && fix.accuracyM <= GOOD_SATELLITE_M) lastGoodSatelliteMs = fix.timeMs

        if (originLat.isNaN() || fix.timeMs - lastTimeMs > RESET_GAP_MS) {
            reset(fix)
            return output(fix)
        }

        val dt = (fix.timeMs - lastTimeMs) / 1000.0
        val (mx, my) = toLocal(fix.latitude, fix.longitude)
        // Impossible jump? Compare against where we were, allowing for both fixes' error.
        val jump = hypot(mx - x.p, my - y.p) - fix.accuracyM - sqrt(x.pp)
        if (jump > MAX_SPEED_MS * dt) {
            if (rejectedInARow < MAX_REJECTS) {
                rejectedInARow++
                return null
            }
            // The "jumps" keep coming: it was the old position that was wrong.
            reset(fix)
            return output(fix)
        }
        rejectedInARow = 0

        x.predict(dt)
        y.predict(dt)
        val r = fix.accuracyM * fix.accuracyM
        x.updatePosition(mx, r)
        y.updatePosition(my, r)
        if (fix.speedMs != null && fix.bearingDeg != null && fix.speedMs >= 0.0) {
            val b = Math.toRadians(fix.bearingDeg)
            x.updateVelocity(fix.speedMs * sin(b), DOPPLER_VAR)
            y.updateVelocity(fix.speedMs * cos(b), DOPPLER_VAR)
        }
        lastTimeMs = fix.timeMs
        return output(fix)
    }

    private fun reset(fix: Fix) {
        originLat = fix.latitude
        originLon = fix.longitude
        cosLat = cos(Math.toRadians(fix.latitude)).coerceAtLeast(0.2)
        val r = fix.accuracyM * fix.accuracyM
        x.reset(0.0, r)
        y.reset(0.0, r)
        if (fix.speedMs != null && fix.bearingDeg != null) {
            val b = Math.toRadians(fix.bearingDeg)
            x.v = fix.speedMs * sin(b)
            y.v = fix.speedMs * cos(b)
        }
        lastTimeMs = fix.timeMs
        rejectedInARow = 0
    }

    private fun output(raw: Fix): Fix {
        val lat = originLat + y.p / M_PER_DEG
        val lon = originLon + x.p / (M_PER_DEG * cosLat)
        val kfSpeed = hypot(x.v, y.v)
        val speed = raw.speedMs ?: kfSpeed
        val bearing =
            raw.bearingDeg ?: if (kfSpeed > MIN_HEADING_SPEED_MS) (Math.toDegrees(atan2(x.v, y.v)) + 360.0) % 360.0 else null
        return raw.copy(latitude = lat, longitude = lon, accuracyM = sqrt((x.pp + y.pp) / 2.0), speedMs = speed, bearingDeg = bearing)
    }

    private fun toLocal(
        lat: Double,
        lon: Double,
    ): Pair<Double, Double> = (lon - originLon) * M_PER_DEG * cosLat to (lat - originLat) * M_PER_DEG

    /** Constant-velocity Kalman filter on one axis. */
    private class Axis {
        var p = 0.0
        var v = 0.0
        var pp = 1.0 // var(p)
        var pv = 0.0 // cov(p, v)
        var vv = VELOCITY_VAR0

        fun reset(
            position: Double,
            positionVar: Double,
        ) {
            p = position
            v = 0.0
            pp = positionVar
            pv = 0.0
            vv = VELOCITY_VAR0
        }

        fun predict(dt: Double) {
            p += v * dt
            val q = ACCEL_VAR
            val dt2 = dt * dt
            val newPp = pp + 2 * dt * pv + dt2 * vv + q * dt2 * dt2 / 4
            val newPv = pv + dt * vv + q * dt2 * dt / 2
            val newVv = vv + q * dt2
            pp = newPp
            pv = newPv
            vv = newVv
        }

        fun updatePosition(
            z: Double,
            r: Double,
        ) {
            val s = pp + r
            val kp = pp / s
            val kv = pv / s
            val e = z - p
            p += kp * e
            v += kv * e
            val newPp = (1 - kp) * pp
            val newPv = (1 - kp) * pv
            val newVv = vv - kv * pv
            pp = newPp
            pv = newPv
            vv = newVv
        }

        fun updateVelocity(
            z: Double,
            r: Double,
        ) {
            val s = vv + r
            val kp = pv / s
            val kv = vv / s
            val e = z - v
            p += kp * e
            v += kv * e
            val newPp = pp - kp * pv
            val newPv = (1 - kv) * pv
            val newVv = (1 - kv) * vv
            pp = newPp
            pv = newPv
            vv = newVv
        }
    }

    companion object {
        /** Faster than any car on a Malaysian road: ~250 km/h. */
        const val MAX_SPEED_MS = 70.0
        const val MAX_REJECTS = 3

        /** A satellite fix this accurate makes network fixes pointless for a while. */
        const val GOOD_SATELLITE_M = 35.0
        const val NETWORK_YIELD_MS = 6_000L

        /** A longer silence starts over (new trip, app was in the background). */
        const val RESET_GAP_MS = 12_000L

        const val MIN_HEADING_SPEED_MS = 1.5

        /** Driving acceleration noise, (m/s^2)^2. */
        private const val ACCEL_VAR = 9.0
        private const val VELOCITY_VAR0 = 100.0

        /** GPS Doppler speed is good to about 0.5 m/s. */
        private const val DOPPLER_VAR = 0.25
        private const val M_PER_DEG = 111_195.0
    }
}
