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
    private val onLocation: (Location) -> Unit,
) {
    private var listener: LocationListener? = null

    companion object {
        private const val MIN_INTERVAL_MS = 1000L
        private const val MIN_DISTANCE_M = 3f
    }

    fun start() {
        if (listener != null) return

        val newListener = LocationListener { location -> onLocation(location) }
        listener = newListener

        val provider =
            when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return

        locationManager.requestLocationUpdates(
            provider,
            MIN_INTERVAL_MS,
            MIN_DISTANCE_M,
            newListener,
            Looper.getMainLooper(),
        )

        locationManager.getLastKnownLocation(provider)?.let(onLocation)
    }

    fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }
}
