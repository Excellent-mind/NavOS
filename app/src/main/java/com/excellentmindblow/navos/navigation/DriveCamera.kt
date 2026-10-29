package com.excellentmindblow.navos.navigation

/**
 * Where the drive camera looks, from how you're driving. Pure, so it is unit
 * tested; the drive screen animates the map to it on every fix.
 *
 *  - Speed: slow in town = close in (you need the junction detail); fast on
 *    the expressway = further out (you need to see what's coming).
 *  - A turn coming up: zooms in over the last [TURN_ZOOM_FROM_M] so the
 *    junction fills the screen, and the camera tilts down a little.
 *  - Changes are smoothed and limited in speed, so the map doesn't "pump"
 *    with every small speed change.
 *  - The car sits lower on the screen when going fast (more road ahead).
 */
object DriveCamera {
    data class View(
        val zoom: Double,
        val tilt: Double,
        /** Where the car sits, as a fraction of the screen height from the top (0.5 = centre). */
        val carAt: Double,
    )

    /** Zoom by speed (km/h -> zoom), linear between. */
    private val SPEED_ZOOM = listOf(0.0 to 17.6, 25.0 to 17.4, 50.0 to 16.9, 80.0 to 16.3, 110.0 to 15.8)

    const val TURN_ZOOM = 17.8
    const val TURN_ZOOM_FROM_M = 300.0
    const val CRUISE_TILT = 55.0
    const val TURN_TILT = 48.0

    /** Fastest the zoom may change (zoom levels per second). */
    const val MAX_ZOOM_RATE = 0.35

    /** Below this change the zoom stays put (no pumping). */
    const val ZOOM_DEADBAND = 0.12

    /** Turns that need a closer look (not "continue straight" or a slight bend). */
    fun isRealTurn(
        maneuverType: String?,
        modifier: String?,
    ): Boolean {
        if (maneuverType == null) return false
        if (maneuverType in setOf("arrive", "depart")) return false
        if (maneuverType in setOf("continue", "new name") && (modifier == null || modifier == "straight" || modifier.startsWith("slight"))) return false
        return true
    }

    /** The view to aim for. [toTurnM] = distance to the next real turn (or null when none soon). */
    fun target(
        speedKmh: Double,
        toTurnM: Double?,
    ): View {
        val s = if (speedKmh.isNaN()) 0.0 else speedKmh.coerceAtLeast(0.0)
        var zoom = interpolate(SPEED_ZOOM, s)
        var tilt = CRUISE_TILT
        if (toTurnM != null && toTurnM < TURN_ZOOM_FROM_M) {
            val k = 1.0 - (toTurnM.coerceAtLeast(0.0) / TURN_ZOOM_FROM_M)
            zoom = maxOf(zoom, zoom + (TURN_ZOOM - zoom) * k)
            tilt = CRUISE_TILT + (TURN_TILT - CRUISE_TILT) * k
        }
        // Car from 62 % down the screen when slow to 72 % when fast.
        val carAt = 0.62 + 0.10 * (s.coerceAtMost(110.0) / 110.0)
        return View(zoom, tilt, carAt)
    }

    /** One smoothing step from [current] towards [target] over [dtS] seconds. */
    fun step(
        current: View?,
        target: View,
        dtS: Double,
    ): View {
        if (current == null) return target
        val dt = dtS.coerceIn(0.0, 2.0)
        val dz = target.zoom - current.zoom
        val zoom =
            if (kotlin.math.abs(dz) < ZOOM_DEADBAND) {
                current.zoom
            } else {
                current.zoom + dz.coerceIn(-MAX_ZOOM_RATE * dt, MAX_ZOOM_RATE * dt)
            }
        val tilt = current.tilt + (target.tilt - current.tilt).coerceIn(-12.0 * dt, 12.0 * dt)
        val carAt = current.carAt + (target.carAt - current.carAt).coerceIn(-0.05 * dt, 0.05 * dt)
        return View(zoom, tilt, carAt)
    }

    /** Follow / free / overview: who is in charge of the camera. */
    enum class Mode { FOLLOW, FREE, OVERVIEW }

    /** After the driver moved the map, follow again once it has been left alone this long. */
    const val FREE_RESUME_MS = 12_000L

    fun shouldResume(
        mode: Mode,
        lastGestureMs: Long,
        nowMs: Long,
    ): Boolean = mode == Mode.FREE && nowMs - lastGestureMs >= FREE_RESUME_MS

    private fun interpolate(
        table: List<Pair<Double, Double>>,
        x: Double,
    ): Double {
        if (x <= table.first().first) return table.first().second
        if (x >= table.last().first) return table.last().second
        for (i in 1 until table.size) {
            val (x1, y1) = table[i]
            val (x0, y0) = table[i - 1]
            if (x <= x1) return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return table.last().second
    }
}
