package com.excellentmindblow.navos

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint

/**
 * Drive times to the saved places (Home, Work) for the Home screen cards.
 * A time is fetched once and kept until the driver has moved
 * [REFRESH_DISTANCE_M], it is [MAX_AGE_MS] old, or the place itself
 * changed. A failed fetch is not retried for [RETRY_AFTER_MS], so the
 * public routing server is not hammered while offline.
 *
 * [fetchSeconds] runs through [runInBackground]; results come back through
 * [postToMain], then the listeners are told. Everything else is main thread.
 */
class HomeEtas(
    private val runInBackground: (() -> Unit) -> Unit,
    private val postToMain: (() -> Unit) -> Unit,
    private val clock: () -> Long,
    private val fetchSeconds: (from: RoutePoint, to: RoutePoint) -> Double,
) {
    private val listeners = ArrayList<() -> Unit>()

    /** [listener] runs on the main thread whenever a new time is known. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    private class Known(
        val seconds: Double,
        val from: RoutePoint,
        val to: RoutePoint,
        val atMs: Long,
    )

    private val known = HashMap<String, Known>()
    private val inFlight = HashSet<String>()
    private val failedAtMs = HashMap<String, Long>()

    /** Drive time to [key] in seconds, or null when not known (yet). */
    fun secondsTo(
        key: String,
        to: RoutePoint,
    ): Double? = known[key]?.takeIf { it.to == to }?.seconds

    /** Fetches every place in [places] whose time is missing or stale. */
    fun refresh(
        from: RoutePoint,
        places: Map<String, RoutePoint>,
    ) {
        val now = clock()
        for ((key, to) in places) {
            if (key in inFlight) continue
            val failed = failedAtMs[key]
            if (failed != null && now - failed < RETRY_AFTER_MS) continue
            val k = known[key]
            if (k != null && k.to == to && now - k.atMs < MAX_AGE_MS &&
                GeoUtils.distanceMeters(k.from, from) < REFRESH_DISTANCE_M
            ) {
                continue
            }
            inFlight += key
            runInBackground {
                val seconds =
                    try {
                        fetchSeconds(from, to)
                    } catch (e: Exception) {
                        null
                    }
                postToMain {
                    inFlight -= key
                    if (seconds == null) {
                        failedAtMs[key] = clock()
                    } else {
                        failedAtMs -= key
                        known[key] = Known(seconds, from, to, clock())
                        listeners.forEach { it() }
                    }
                }
            }
        }
    }

    companion object {
        const val REFRESH_DISTANCE_M = 1000.0
        const val MAX_AGE_MS = 10 * 60_000L
        const val RETRY_AFTER_MS = 60_000L
    }
}
