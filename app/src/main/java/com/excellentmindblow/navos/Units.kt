package com.excellentmindblow.navos

import android.content.SharedPreferences
import java.util.Locale

/**
 * Kilometres or miles, in one place so the on-screen text, the notification
 * and the spoken guidance (which runs inside NavigationService) all agree.
 * Metric text is exactly what the app showed before this setting existed.
 */
object Units {
    private const val KEY_MILES = "units_miles"
    private const val METERS_PER_MILE = 1609.344
    private const val FEET_PER_METER = 3.28084

    @Volatile
    var miles: Boolean = false
        private set

    fun load(prefs: SharedPreferences) {
        miles = prefs.getBoolean(KEY_MILES, false)
    }

    fun save(
        prefs: SharedPreferences,
        useMiles: Boolean,
    ) {
        miles = useMiles
        prefs.edit().putBoolean(KEY_MILES, useMiles).apply()
    }

    val speedUnit: String get() = if (miles) "mph" else "km/h"

    /** A speed given in km/h, as the number to show in the current unit. */
    fun speed(kmh: Double): Int = Math.round(if (miles) kmh / 1.609344 else kmh).toInt()

    /** Short on-screen distance: "350 m" / "1.2 km", or "400 ft" / "0.4 mi". */
    fun short(meters: Double): String {
        if (!miles) {
            return when {
                meters < 1000 -> "${(Math.round(meters / 10.0) * 10).toInt()} m"
                meters < 100_000 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
                else -> "${Math.round(meters / 1000.0)} km"
            }
        }
        return when {
            meters < 160 -> "${(Math.round(meters * FEET_PER_METER / 50.0) * 50).toInt().coerceAtLeast(50)} ft"
            meters < 16_093 -> String.format(Locale.US, "%.1f mi", meters / METERS_PER_MILE)
            else -> "${Math.round(meters / METERS_PER_MILE)} mi"
        }
    }

    /** How a distance is said aloud: "300 meters" (as before) or "500 feet" / "0.4 miles". */
    fun spoken(meters: Double): String {
        if (!miles) return "${(Math.round(meters / 10) * 10).toInt()} meters"
        val feet = meters * FEET_PER_METER
        return if (feet < 1000) {
            "${(Math.round(feet / 50.0) * 50).toInt().coerceAtLeast(50)} feet"
        } else {
            String.format(Locale.US, "%.1f miles", meters / METERS_PER_MILE)
        }
    }
}
