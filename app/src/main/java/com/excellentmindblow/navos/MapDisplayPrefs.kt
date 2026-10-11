package com.excellentmindblow.navos

import android.content.SharedPreferences

/** The "Map display" choices in Settings (like Waze's Display & map). */
object MapDisplayPrefs {
    private const val K_VIEW_3D = "map_view_3d"
    private const val K_BUILDINGS = "map_show_3d_buildings"
    private const val K_ROUTE_ARROWS = "map_show_route_arrows"
    private const val K_SPEEDOMETER = "map_show_speedometer"
    private const val K_SPEED_LIMIT = "map_show_speed_limit"
    private const val K_CAMERA_ALERTS = "map_show_camera_alerts"

    /** Driving view: tilted 3D (true) or flat top-down 2D (false). */
    @Volatile var view3d = true

    @Volatile var buildings3d = true

    @Volatile var routeArrows = true

    @Volatile var speedometer = true

    @Volatile var speedLimit = true

    @Volatile var cameraAlerts = true

    fun load(prefs: SharedPreferences) {
        view3d = prefs.getBoolean(K_VIEW_3D, true)
        buildings3d = prefs.getBoolean(K_BUILDINGS, true)
        routeArrows = prefs.getBoolean(K_ROUTE_ARROWS, true)
        speedometer = prefs.getBoolean(K_SPEEDOMETER, true)
        speedLimit = prefs.getBoolean(K_SPEED_LIMIT, true)
        cameraAlerts = prefs.getBoolean(K_CAMERA_ALERTS, true)
    }

    fun save(prefs: SharedPreferences) {
        prefs
            .edit()
            .putBoolean(K_VIEW_3D, view3d)
            .putBoolean(K_BUILDINGS, buildings3d)
            .putBoolean(K_ROUTE_ARROWS, routeArrows)
            .putBoolean(K_SPEEDOMETER, speedometer)
            .putBoolean(K_SPEED_LIMIT, speedLimit)
            .putBoolean(K_CAMERA_ALERTS, cameraAlerts)
            .apply()
    }
}
