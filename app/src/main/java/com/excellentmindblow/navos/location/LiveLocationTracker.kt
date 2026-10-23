package com.excellentmindblow.navos.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper

/**
 * Live location for the app, hybrid like Google Maps: it listens to every
 * source the phone has at once - satellite GPS, Android's own fused
 * provider (GPS + Wi-Fi + cell + motion sensors, Android 12+), and the
 * Wi-Fi / cell network provider - and blends them with [LocationFusion]
 * into one steady, accurate position. Uses Android's built-in
 * LocationManager only (no Google Play Services dependency).
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
    private val listeners = ArrayList<LocationListener>()
    private val fusion = LocationFusion()

    companion object {
        private const val MIN_INTERVAL_MS = 500L
        const val MIN_DISTANCE_M = 1f
    }

    /** Every enabled source, best first. */
    private fun providers(): List<String> {
        val out = ArrayList<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.hasProvider(LocationManager.FUSED_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)
            ) {
                out += LocationManager.FUSED_PROVIDER
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) out += LocationManager.GPS_PROVIDER
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) out += LocationManager.NETWORK_PROVIDER
        } catch (_: SecurityException) {
            // Permission revoked between the caller's check and here; a later start() can retry.
            return emptyList()
        }
        return out
    }

    fun start() {
        // Non-empty listeners means registration succeeded. Keep it empty on all
        // early failures so callers can retry after enabling location or granting permission.
        if (listeners.isNotEmpty()) return
        val sources = providers()
        if (sources.isEmpty()) return

        try {
            var lastKnown: Location? = null
            for (provider in sources) {
                val l = LocationListener { location -> deliver(location) }
                locationManager.requestLocationUpdates(provider, MIN_INTERVAL_MS, minDistanceM, l, callbackLooper)
                listeners += l
                val known = locationManager.getLastKnownLocation(provider)
                if (known != null && (lastKnown == null || known.time > lastKnown.time)) lastKnown = known
            }
            lastKnown?.let { deliver(it) }
        } catch (_: SecurityException) {
            // Permission revoked during registration: clean up and stay restartable.
            stop()
        }
    }

    fun stop() {
        val active = listeners.toList()
        // Clear state before touching the framework so even a permission error
        // cannot leave a stale listener that blocks a future start().
        listeners.clear()
        for (l in active) runCatching { locationManager.removeUpdates(l) }
    }

    private fun deliver(raw: Location) {
        val source =
            when (raw.provider) {
                LocationManager.NETWORK_PROVIDER -> LocationFusion.Source.NETWORK
                LocationManager.GPS_PROVIDER -> LocationFusion.Source.GPS
                else -> LocationFusion.Source.FUSED
            }
        val fix =
            LocationFusion.Fix(
                latitude = raw.latitude,
                longitude = raw.longitude,
                accuracyM = if (raw.hasAccuracy()) raw.accuracy.toDouble() else 50.0,
                speedMs = if (raw.hasSpeed()) raw.speed.toDouble() else null,
                bearingDeg = if (raw.hasBearing() && raw.hasSpeed() && raw.speed > 1f) raw.bearing.toDouble() else null,
                timeMs = raw.time,
                source = source,
            )
        val out = fusion.accept(fix) ?: return
        onLocation(
            Location(raw).apply {
                latitude = out.latitude
                longitude = out.longitude
                accuracy = out.accuracyM.toFloat()
                out.speedMs?.let { speed = it.toFloat() }
                val heading = out.bearingDeg
                if (heading != null) {
                    bearing = heading.toFloat()
                } else {
                    @Suppress("DEPRECATION")
                    removeBearing()
                }
            },
        )
    }
}
