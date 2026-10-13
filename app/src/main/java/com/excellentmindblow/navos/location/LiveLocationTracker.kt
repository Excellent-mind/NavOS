package com.excellentmindblow.navos.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * Native equivalent of legacy-expo-rn/src/services/useLiveLocation.ts -
 * same intent (GPS-grade accuracy, ~1s / 3m update cadence), but using
 * Android's built-in LocationManager directly instead of a Google Play
 * Services dependency, since this app has no other reason to need Play
 * Services and fewer dependencies means fewer places left for another
 * device-specific native crash to hide.
 *
 * Call start() only after permission has already been granted - this
 * class does not request permission itself, the caller (MainActivity)
 * owns that flow since it needs an Activity to show the system prompt.
 */
class LiveLocationTracker(
    private val locationManager: LocationManager,
    /** 0 = keep reporting even when standing still (needed while driving
     * so the camera can zoom in / settle when you stop). */
    private val minDistanceM: Float = MIN_DISTANCE_M,
    /** Injectable so JVM unit tests can exercise retry behavior without Android's main Looper. */
    private val callbackLooper: Looper? = Looper.getMainLooper(),
    private val onLocation: (Location) -> Unit,
) {
    private var listener: LocationListener? = null

    companion object {
        private const val MIN_INTERVAL_MS = 500L
        const val MIN_DISTANCE_M = 1f
    }

    fun start() {
        // A non-null listener means registration succeeded, not merely that a
        // listener object was allocated. Keep this null on all early failures
        // so callers can retry after enabling location or granting permission.
        if (listener != null) return

        val provider = try {
            when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
        } catch (_: SecurityException) {
            // Permission may have been revoked between the caller's check and
            // provider selection. A later start() call can retry.
            null
        } ?: return

        val newListener = LocationListener { location -> onLocation(location) }
        try {
            locationManager.requestLocationUpdates(
                provider,
                MIN_INTERVAL_MS,
                minDistanceM,
                newListener,
                callbackLooper,
            )
            // Publish the active listener only after registration succeeds.
            listener = newListener
            locationManager.getLastKnownLocation(provider)?.let(onLocation)
        } catch (_: SecurityException) {
            // If permission is revoked during registration, clean up defensively
            // and leave the tracker restartable instead of stuck in a false
            // "already started" state.
            runCatching { locationManager.removeUpdates(newListener) }
            listener = null
        }
    }

    fun stop() {
        val activeListener = listener
        // Clear state before touching the framework so even a permission error
        // cannot leave a stale listener that blocks a future start().
        listener = null
        if (activeListener != null) {
            runCatching { locationManager.removeUpdates(activeListener) }
        }
    }
}
