package com.excellentmindblow.navos.search

import android.content.SharedPreferences

/**
 * Where you left the car: saved when a drive ends at its destination, shown
 * on the home map for a day (Waze-style "your car is parked here").
 */
object ParkedCar {
    data class Spot(
        val latitude: Double,
        val longitude: Double,
        /** The destination it was parked for ("Mid Valley"), may be empty. */
        val near: String,
        val atMs: Long,
    )

    /** Shown this long after parking. */
    const val SHOW_MS = 24L * 60 * 60 * 1000

    private const val K = "parked_"

    fun save(
        prefs: SharedPreferences,
        spot: Spot,
    ) = prefs
        .edit()
        .putString(K + "lat", spot.latitude.toString())
        .putString(K + "lon", spot.longitude.toString())
        .putString(K + "near", spot.near)
        .putLong(K + "at", spot.atMs)
        .apply()

    /** The saved spot, or null when there is none or it is older than [SHOW_MS]. */
    fun get(
        prefs: SharedPreferences,
        nowMs: Long = System.currentTimeMillis(),
    ): Spot? {
        val lat = prefs.getString(K + "lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(K + "lon", null)?.toDoubleOrNull() ?: return null
        val at = prefs.getLong(K + "at", 0L)
        if (nowMs - at > SHOW_MS) return null
        return Spot(lat, lon, prefs.getString(K + "near", "").orEmpty(), at)
    }

    fun clear(prefs: SharedPreferences) = prefs.edit().remove(K + "lat").remove(K + "lon").remove(K + "near").remove(K + "at").apply()
}
