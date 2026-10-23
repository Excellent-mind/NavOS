package com.excellentmindblow.navos.location

/**
 * How hard to work the GPS, from how the phone is moving - the battery
 * difference between a drive and a phone lying on a desk. Moving: full
 * rate. Standing still for a while: slower, and on the idle map satellite
 * GPS is switched off (Wi-Fi / cell is plenty to show where you are).
 * Any movement brings full rate straight back. Pure, so it is unit tested.
 */
class PowerPolicy(
    private val navigating: Boolean,
) {
    enum class Mode(
        val intervalMs: Long,
        val minDistanceM: Float,
        val useGps: Boolean,
    ) {
        /** Driving: every half second, every source. */
        FULL(500L, 0f, true),

        /** Stopped mid-drive (jam, R&R): every 2 s. */
        CALM(2_000L, 0f, true),

        /** Parked on the idle map: every 10 s, Wi-Fi / cell only. */
        PARKED(10_000L, 10f, false),
    }

    var mode = Mode.FULL
        private set

    private var stillSinceMs = -1L
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN

    /** A fix at [nowMs]; returns the mode to use from now on. */
    fun onFix(
        lat: Double,
        lon: Double,
        speedMs: Double?,
        nowMs: Long,
    ): Mode {
        val moved =
            !anchorLat.isNaN() && distance(lat, lon, anchorLat, anchorLon) > MOVE_M
        val moving = (speedMs ?: 0.0) > MOVING_MS || moved
        if (moving || anchorLat.isNaN()) {
            anchorLat = lat
            anchorLon = lon
            stillSinceMs = nowMs
            if (moving) {
                mode = Mode.FULL
                return mode
            }
        }
        val stillFor = nowMs - stillSinceMs
        mode =
            when {
                navigating && stillFor >= NAV_CALM_AFTER_MS -> Mode.CALM
                navigating -> Mode.FULL
                stillFor >= IDLE_PARK_AFTER_MS -> Mode.PARKED
                else -> Mode.FULL
            }
        return mode
    }

    private fun distance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1) * 6_371_000.0
        val dLon = Math.toRadians(lon2 - lon1) * 6_371_000.0 * Math.cos(Math.toRadians(lat1))
        return Math.hypot(dLat, dLon)
    }

    companion object {
        const val MOVING_MS = 1.5
        const val MOVE_M = 25.0
        const val NAV_CALM_AFTER_MS = 120_000L
        const val IDLE_PARK_AFTER_MS = 60_000L
    }
}
