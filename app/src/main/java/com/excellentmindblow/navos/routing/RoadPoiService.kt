package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A toll plaza ("toll") or speed camera ("camera") that lies on a route. */
data class RoadPoi(
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val charge: String?,
    /** Camera only: the posted limit from the map data (e.g. "90"), if mapped. */
    val maxspeed: String? = null,
)

/**
 * Finds toll plazas and speed cameras along routes using OpenStreetMap data
 * (Overpass API), then keeps only the ones that sit on each route.
 *
 * Toll PRICES are only known where OpenStreetMap has a `charge` tag on the
 * toll booth - many plazas don't, so those show as "Toll" without a price.
 * Blocking: call off the main thread.
 */
object RoadPoiService {
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"
    private val ENDPOINTS =
        listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

    private const val ON_ROUTE_METERS = 45.0
    private const val TOLL_CLUSTER_METERS = 300.0

    /** Route index -> toll plazas / cameras on that route. */
    @Throws(Exception::class)
    fun fetchAlong(routes: List<Route>): Map<Int, List<RoadPoi>> {
        if (routes.isEmpty()) return emptyMap()
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        for (r in routes) {
            for (p in r.geometry) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
        }
        if (maxLat - minLat > 5.0 || maxLon - minLon > 5.0) return emptyMap() // too big for a quick query
        val pad = 0.005
        val bbox =
            String.format(Locale.US, "%f,%f,%f,%f", minLat - pad, minLon - pad, maxLat + pad, maxLon + pad)
        val query =
            "[out:json][timeout:25];(" +
                "node[\"barrier\"=\"toll_booth\"]($bbox);" +
                "node[\"highway\"=\"toll_gantry\"]($bbox);" +
                "node[\"highway\"=\"speed_camera\"]($bbox);" +
                "node[\"enforcement\"=\"maxspeed\"]($bbox);" +
                ");out body 4000;"

        val all = parseRoadPois(runQuery(query))

        val result = HashMap<Int, List<RoadPoi>>()
        routes.forEachIndexed { index, route ->
            val line = thin(route.geometry, 50.0)
            if (line.size < 2) {
                result[index] = emptyList()
                return@forEachIndexed
            }
            val onRoute =
                all.filter { poi ->
                    GeoUtils.distanceToPolyline(RoutePoint(poi.latitude, poi.longitude), line).first <= ON_ROUTE_METERS
                }
            result[index] = clusterTolls(onRoute)
        }
        return result
    }

    /** Every toll plaza / speed camera within [radiusMeters] of a point, not
     * tied to any route - for the plain (idle) map. Plazas are merged to one
     * icon each. Blocking: call off the main thread. */
    @Throws(Exception::class)
    fun fetchArea(
        lat: Double,
        lon: Double,
        radiusMeters: Double = 8000.0,
    ): List<RoadPoi> {
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(lat)).coerceAtLeast(0.2))
        val bbox = String.format(Locale.US, "%f,%f,%f,%f", lat - dLat, lon - dLon, lat + dLat, lon + dLon)
        val query =
            "[out:json][timeout:25];(" +
                "node[\"barrier\"=\"toll_booth\"]($bbox);" +
                "node[\"highway\"=\"toll_gantry\"]($bbox);" +
                "node[\"highway\"=\"speed_camera\"]($bbox);" +
                "node[\"enforcement\"=\"maxspeed\"]($bbox);" +
                ");out body 2000;"
        return clusterTolls(parseRoadPois(runQuery(query)))
    }

    private fun parseRoadPois(json: JSONObject): List<RoadPoi> {
        val elements = json.optJSONArray("elements") ?: return emptyList()
        val all = ArrayList<RoadPoi>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            if (!e.has("lat") || !e.has("lon")) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val isToll = tags.optString("barrier") == "toll_booth" || tags.optString("highway") == "toll_gantry"
            val isCamera = tags.optString("highway") == "speed_camera" || tags.optString("enforcement") == "maxspeed"
            if (!isToll && !isCamera) continue
            all.add(
                RoadPoi(
                    kind = if (isToll) "toll" else "camera",
                    latitude = e.getDouble("lat"),
                    longitude = e.getDouble("lon"),
                    name = tags.optString("name").ifBlank { tags.optString("operator") },
                    charge = tags.optString("charge").ifBlank { null },
                    maxspeed = tags.optString("maxspeed").ifBlank { null },
                ),
            )
        }
        return all
    }

    /** A plaza is mapped as one node per lane - merge those into one icon. */
    private fun clusterTolls(pois: List<RoadPoi>): List<RoadPoi> {
        val out = ArrayList<RoadPoi>()
        for (poi in pois) {
            if (poi.kind != "toll") {
                out.add(poi)
                continue
            }
            val idx =
                out.indexOfFirst {
                    it.kind == "toll" &&
                        GeoUtils.distanceMeters(
                            RoutePoint(it.latitude, it.longitude),
                            RoutePoint(poi.latitude, poi.longitude),
                        ) < TOLL_CLUSTER_METERS
                }
            if (idx < 0) {
                out.add(poi)
            } else if (out[idx].charge == null && poi.charge != null) {
                out[idx] = out[idx].copy(charge = poi.charge, name = out[idx].name.ifBlank { poi.name })
            }
        }
        return out
    }

    private fun thin(
        points: List<RoutePoint>,
        spacing: Double,
    ): List<RoutePoint> {
        val out = ArrayList<RoutePoint>()
        var last: RoutePoint? = null
        for (p in points) {
            if (last == null || GeoUtils.distanceMeters(last, p) >= spacing) {
                out.add(p)
                last = p
            }
        }
        if (points.isNotEmpty() && out.lastOrNull() != points.last()) out.add(points.last())
        return out
    }

    /** The number in a charge tag like "RM 4.20" / "4.2 MYR", or null when
     * unknown or in another currency. */
    fun parsePrice(charge: String?): Double? {
        if (charge.isNullOrBlank()) return null
        val lower = charge.lowercase()
        val letters = lower.filter { it.isLetter() }
        if (letters.isNotEmpty() && letters != "rm" && letters != "myr") return null
        val match = Regex("(\\d+(?:[.,]\\d+)?)").find(charge) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    private fun runQuery(query: String): JSONObject {
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                try {
                    connection.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw RuntimeException("POI request failed (${connection.responseCode})")
                    }
                    return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("POI request failed")
    }
}
