package com.excellentmindblow.navos.search

import android.content.SharedPreferences

/**
 * Home and Work, saved on the phone and read by both the phone UI and
 * Android Auto. Stored as "<key>_lat", "<key>_lon", "<key>_label" in the
 * app's preferences (the format the app has always used).
 */
class SavedPlaces(
    private val prefs: SharedPreferences,
) {
    data class SavedPlace(
        val label: String?,
        val latitude: Double,
        val longitude: Double,
    )

    /** The saved place for [key] ("home" / "work"), or null if not set. */
    fun get(key: String): SavedPlace? {
        if (!prefs.contains("${key}_lat")) return null
        return SavedPlace(
            label = prefs.getString("${key}_label", null),
            latitude = prefs.getFloat("${key}_lat", 0f).toDouble(),
            longitude = prefs.getFloat("${key}_lon", 0f).toDouble(),
        )
    }

    fun save(
        key: String,
        result: GeocodeResult,
    ) {
        prefs
            .edit()
            .putFloat("${key}_lat", result.latitude.toFloat())
            .putFloat("${key}_lon", result.longitude.toFloat())
            .putString("${key}_label", result.label)
            .apply()
    }

    companion object {
        const val HOME = "home"
        const val WORK = "work"
    }
}
