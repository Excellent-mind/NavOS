package com.excellentmindblow.navos.routing

import android.content.SharedPreferences

/**
 * "Avoid tolls" / "Avoid highways", sent to OSRM as its `exclude` option.
 * Not every OSRM server is built with exclude support (the free demo server
 * may refuse it), so RoutingService falls back to a normal route and sets
 * [excludeUnsupported] so the app can tell the driver instead of quietly
 * ignoring their setting.
 */
object RoutePrefs {
    private const val KEY_AVOID_TOLLS = "avoid_tolls"
    private const val KEY_AVOID_HIGHWAYS = "avoid_highways"

    @Volatile
    var avoidTolls: Boolean = false
        private set

    @Volatile
    var avoidHighways: Boolean = false
        private set

    /** True when the last request had to drop the exclude option. */
    @Volatile
    var excludeUnsupported: Boolean = false

    fun load(prefs: SharedPreferences) {
        avoidTolls = prefs.getBoolean(KEY_AVOID_TOLLS, false)
        avoidHighways = prefs.getBoolean(KEY_AVOID_HIGHWAYS, false)
    }

    fun save(
        prefs: SharedPreferences,
        tolls: Boolean,
        highways: Boolean,
    ) {
        avoidTolls = tolls
        avoidHighways = highways
        prefs.edit().putBoolean(KEY_AVOID_TOLLS, tolls).putBoolean(KEY_AVOID_HIGHWAYS, highways).apply()
    }

    /** e.g. "toll,motorway", or null when nothing is avoided. */
    fun excludeParam(): String? {
        val parts = mutableListOf<String>()
        if (avoidTolls) parts.add("toll")
        if (avoidHighways) parts.add("motorway")
        return parts.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}
