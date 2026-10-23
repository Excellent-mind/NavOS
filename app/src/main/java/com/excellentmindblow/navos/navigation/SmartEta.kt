package com.excellentmindblow.navos.navigation

/**
 * Learns how this drive is actually going against the routing server's
 * times, like Google Maps' ETA does: the server's times are for typical
 * free roads, but KL traffic, lights and the driver's own pace make real
 * drives slower (or faster). It compares, over the last stretch of the
 * drive, the real time taken with the time the server predicted for that
 * same stretch, and scales the remaining time by that pace - trusting it
 * more the more it has seen, and less for the far part of the trip.
 * Pure, so it is unit tested.
 */
class SmartEta {
    private var realS = 0.0
    private var predictedS = 0.0
    private var lastRaw = Double.NaN
    private var lastMs = 0L

    /** A new drive: forget everything. */
    fun reset() {
        realS = 0.0
        predictedS = 0.0
        rebase()
    }

    /** A new route mid-drive (reroute, stop added): keep the pace, restart the baseline. */
    fun rebase() {
        lastRaw = Double.NaN
    }

    /** Each navigation update: the server's remaining time ([rawRemainingS]) at [nowMs]. */
    fun observe(
        rawRemainingS: Double,
        nowMs: Long,
    ) {
        if (lastRaw.isNaN()) {
            lastRaw = rawRemainingS
            lastMs = nowMs
            return
        }
        val dt = (nowMs - lastMs) / 1000.0
        val consumed = lastRaw - rawRemainingS
        lastRaw = rawRemainingS
        lastMs = nowMs
        // A jump (new route, GPS glitch) or a long gap (app asleep) says nothing about pace.
        if (dt <= 0 || dt > MAX_GAP_S || consumed < -JUMP_S || consumed > dt * 6 + 30) return
        realS += dt
        predictedS += consumed.coerceAtLeast(0.0)
        // Keep a moving window: recent driving counts most.
        if (predictedS > WINDOW_S) {
            val k = WINDOW_S / predictedS
            realS *= k
            predictedS *= k
        }
    }

    /** Real time per predicted second over the recent drive (1.0 until enough is seen). */
    val pace: Double
        get() = if (predictedS < MIN_SEEN_S) 1.0 else (realS / predictedS).coerceIn(MIN_PACE, MAX_PACE)

    /** How much the pace is trusted, 0..[MAX_TRUST]. */
    private val trust: Double get() = (predictedS / FULL_TRUST_S).coerceAtMost(1.0) * MAX_TRUST

    /** The remaining time adjusted for this drive's pace. */
    fun adjust(rawRemainingS: Double): Double {
        val f = 1 + (pace - 1) * trust
        val near = rawRemainingS.coerceAtMost(NEAR_S)
        val far = rawRemainingS - near
        // Far ahead conditions may differ: half the correction there.
        return near * f + far * (1 + (f - 1) / 2)
    }

    companion object {
        const val WINDOW_S = 900.0
        const val MIN_SEEN_S = 120.0
        const val FULL_TRUST_S = 600.0
        const val MAX_TRUST = 0.8
        const val NEAR_S = 1800.0
        const val MIN_PACE = 0.75
        const val MAX_PACE = 1.8
        private const val MAX_GAP_S = 30.0
        private const val JUMP_S = 60.0
    }
}
