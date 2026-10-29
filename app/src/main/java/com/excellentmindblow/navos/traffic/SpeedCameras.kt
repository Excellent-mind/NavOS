package com.excellentmindblow.navos.traffic

import android.content.Context
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONObject

/**
 * Malaysia's speed and red-light cameras (AES / AwAS), bundled from
 * OpenStreetMap (assets/cameras.json, tools/cameras/build.py), and which of
 * them a route passes. The pure parts are unit tested.
 *
 * A camera only counts for your carriageway: expressway carriageways are
 * 20-40 m apart, so it must sit on your line, and when the map says which
 * way it watches, that way must roughly be yours.
 */
object SpeedCameras {
    data class Camera(
        val latitude: Double,
        val longitude: Double,
        /** Posted limit (km/h), or null when the map doesn't say. */
        val limitKmh: Int?,
        /** Compass heading of the traffic it watches, or null. */
        val direction: Int?,
        val redLight: Boolean,
        val name: String,
    )

    /** A camera on the route, [atM] metres from the route start. */
    data class OnRoute(
        val camera: Camera,
        val atM: Double,
    )

    /** On your carriageway: this close when the camera's direction matches yours (or is unknown). */
    const val SAME_WAY_M = 40.0
    const val UNKNOWN_WAY_M = 25.0

    /** Facing the other way: only when right on your line (a two-way road). */
    const val OTHER_WAY_M = 12.0

    /** Warn this far ahead: fast roads (limit 90+) earlier. */
    fun warnAheadM(limitKmh: Int?): Double = if ((limitKmh ?: 0) >= 90) 1000.0 else 500.0

    @Volatile
    var all: List<Camera> = emptyList()
        private set

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        all = runCatching { context.applicationContext.assets.open("cameras.json").bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())
    }

    fun parse(text: String): List<Camera> {
        val arr = JSONObject(text).optJSONArray("cameras") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONArray(i) ?: return@mapNotNull null
            runCatching {
                Camera(
                    latitude = c.getDouble(0),
                    longitude = c.getDouble(1),
                    limitKmh = c.optInt(2, 0).takeIf { it > 0 },
                    direction = c.optInt(3, -1).takeIf { it >= 0 },
                    redLight = c.optString(4) == "redlight",
                    name = c.optString(5),
                )
            }.getOrNull()
        }
    }

    /** The cameras [track] drives past on its own carriageway, in order. */
    fun onRoute(
        track: RouteTrack,
        cameras: List<Camera>,
    ): List<OnRoute> {
        if (track.source.size < 2 || cameras.isEmpty()) return emptyList()
        // Quick box test first: most of Malaysia's cameras are nowhere near any one route.
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        for (p in track.source) {
            minLat = minOf(minLat, p.latitude)
            maxLat = maxOf(maxLat, p.latitude)
            minLon = minOf(minLon, p.longitude)
            maxLon = maxOf(maxLon, p.longitude)
        }
        val pad = 0.002
        return cameras
            .filter { it.latitude in (minLat - pad)..(maxLat + pad) && it.longitude in (minLon - pad)..(maxLon + pad) }
            .mapNotNull { cam ->
                val at = RoutePoint(cam.latitude, cam.longitude)
                val snap = GeoUtils.snapToPolyline(at, track.source) ?: return@mapNotNull null
                val progress = track.progressOf(snap)
                val road = track.bearingAt(progress, 30.0)
                val limit =
                    when {
                        cam.direction == null -> UNKNOWN_WAY_M
                        GeoUtils.angleDiff(cam.direction.toDouble(), road) <= 60.0 -> SAME_WAY_M
                        else -> OTHER_WAY_M
                    }
                if (snap.distanceMeters > limit) null else OnRoute(cam, progress)
            }.sortedBy { it.atM }
    }

    /** The next camera ahead of [hereM] within its warning distance, or null. */
    fun nextToWarn(
        onRoute: List<OnRoute>,
        hereM: Double,
    ): OnRoute? =
        onRoute.firstOrNull { c ->
            val ahead = c.atM - hereM
            ahead >= -20.0 && ahead <= warnAheadM(c.camera.limitKmh)
        }

    /** For the trip strip, which draws cameras from road points. */
    fun asRoadPois(onRoute: List<OnRoute>): List<RoadPoi> = onRoute.map { RoadPoi("camera", it.camera.latitude, it.camera.longitude, it.camera.name, it.camera.limitKmh?.toString()) }
}
