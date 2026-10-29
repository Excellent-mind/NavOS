package com.excellentmindblow.navos.routing

import android.content.Context
import android.util.Log
import com.excellentmindblow.navos.navigation.GeoUtils
import org.json.JSONArray

/**
 * Every toll plaza in Malaysia, bundled (assets/tolls/plazas.json from
 * tools/tolls/fetch_plazas.py, OpenStreetMap): a route's tolls - and so
 * its toll price - are known at once and offline, without waiting for a
 * live Overpass lookup (which can time out). The live lookup still adds
 * speed cameras and replaces these when it answers.
 */
object BundledTolls {
    class Plaza(
        val latitude: Double,
        val longitude: Double,
        val name: String,
    )

    @Volatile
    private var plazas: List<Plaza>? = null

    /** Reads the table once (blocking; call off the main thread). */
    fun ensureLoaded(context: Context): Boolean {
        if (plazas != null) return plazas!!.isNotEmpty()
        plazas =
            runCatching {
                context.applicationContext.assets.open("tolls/plazas.json").bufferedReader().use { parse(it.readText()) }
            }.onFailure { Log.w("NavOSTolls", "plaza table: $it") }.getOrDefault(emptyList())
        return plazas!!.isNotEmpty()
    }

    internal fun parse(json: String): List<Plaza> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONArray(i) ?: return@mapNotNull null
            Plaza(p.optDouble(0), p.optDouble(1), p.optString(2))
        }
    }

    /** The plazas on [route] (within [ON_ROUTE_M] of its line). */
    fun along(route: Route): List<RoadPoi> = along(route.geometry, plazas.orEmpty())

    internal fun along(
        line: List<RoutePoint>,
        all: List<Plaza>,
    ): List<RoadPoi> {
        if (line.size < 2 || all.isEmpty()) return emptyList()
        val pad = 0.002
        val s = line.minOf { it.latitude } - pad
        val n = line.maxOf { it.latitude } + pad
        val w = line.minOf { it.longitude } - pad
        val e = line.maxOf { it.longitude } + pad
        return all
            .filter { it.latitude in s..n && it.longitude in w..e }
            .filter { GeoUtils.distanceToPolyline(RoutePoint(it.latitude, it.longitude), line).first <= ON_ROUTE_M }
            .map { RoadPoi("toll", it.latitude, it.longitude, it.name) }
    }

    private const val ON_ROUTE_M = 45.0
}
