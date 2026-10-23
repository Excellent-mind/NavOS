package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Something on or beside a route: a toll plaza ("toll"), speed camera
 * ("camera"), traffic light ("signal") or a rest stop - an R&R with
 * services ("services") or a plain lay-by ("rest_area"). */
data class RoadPoi(
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    /** Camera only: the posted limit from the map data (e.g. "90"), if mapped. */
    val maxspeed: String? = null,
)

/**
 * Finds toll plazas and speed cameras along routes using OpenStreetMap data
 * (Overpass API), then keeps only the ones that sit on each route.
 *
 * Toll prices are deliberately not handled - plazas show as "Toll" only.
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
    private const val SIGNAL_METERS = 20.0
    private const val REST_METERS = 200.0

    private const val BOX_SPAN_METERS = 8000.0
    private const val SIGNAL_PAD_DEG = 0.0004 // ~45 m
    private const val REST_PAD_DEG = 0.0025 // ~280 m

    /** A lat/lon box in Overpass order. */
    internal class Box(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
    ) {
        fun padded(d: Double): String = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", minLat - d, minLon - d, maxLat + d, maxLon + d)
    }

    /** Covers [line] with boxes, each spanning at most [BOX_SPAN_METERS] of it. */
    internal fun boxesAlong(line: List<RoutePoint>): List<Box> {
        if (line.isEmpty()) return emptyList()
        val boxes = ArrayList<Box>()
        var start = 0
        var run = 0.0
        for (i in 1 until line.size) {
            run += GeoUtils.distanceMeters(line[i - 1], line[i])
            if (run >= BOX_SPAN_METERS || i == line.size - 1) {
                val part = line.subList(start, i + 1)
                boxes.add(Box(part.minOf { it.latitude }, part.minOf { it.longitude }, part.maxOf { it.latitude }, part.maxOf { it.longitude }))
                start = i
                run = 0.0
            }
        }
        if (boxes.isEmpty()) boxes.add(Box(line[0].latitude, line[0].longitude, line[0].latitude, line[0].longitude))
        return boxes
    }

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

    /** Traffic lights within [SIGNAL_METERS] of the route line, plus R&Rs and
     * lay-bys beside it - for the 3D signs and the "how many lights" count.
     * One query covers every route (each its own around-the-line filter);
     * the results are then shared out to the route they sit on.
     * Blocking: call off the main thread. */
    @Throws(Exception::class)
    fun fetchExtrasAlong(routes: List<Route>): Map<Int, List<RoadPoi>> {
        if (routes.isEmpty()) return emptyMap()
        // Small boxes along each route (a box per ~8 km stretch) rather than
        // one around-the-line filter: public Overpass servers answer box
        // queries quickly but time out on long "around" lines, and one box
        // over a long trip would hold every light in every town it touches.
        val statements = StringBuilder()
        for (route in routes) {
            for (box in boxesAlong(route.geometry)) {
                val sig = box.padded(SIGNAL_PAD_DEG)
                val rest = box.padded(REST_PAD_DEG)
                statements.append("node[\"highway\"=\"traffic_signals\"]($sig);")
                statements.append("node[\"crossing\"=\"traffic_signals\"]($sig);")
                statements.append("nwr[\"highway\"~\"^(services|rest_area)$\"]($rest);")
                statements.append("nwr[\"amenity\"~\"^(fuel|charging_station)$\"]($rest);")
            }
        }
        if (statements.isEmpty()) return emptyMap()
        val json = runQuery("[out:json][timeout:25];($statements);out center 5000;")
        val all = parseExtras(json)
        val result = HashMap<Int, List<RoadPoi>>()
        routes.forEachIndexed { index, route ->
            val line = thin(route.geometry, 25.0)
            result[index] =
                if (line.size < 2) {
                    emptyList()
                } else {
                    all.filter { poi ->
                        val limit = if (poi.kind == "signal") SIGNAL_METERS + 5.0 else REST_METERS + 20.0
                        GeoUtils.distanceToPolyline(RoutePoint(poi.latitude, poi.longitude), line).first <= limit
                    }
                }
        }
        return result
    }

    internal fun parseExtras(json: JSONObject): List<RoadPoi> {
        val elements = json.optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<RoadPoi>()
        val seen = HashSet<String>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            if (!seen.add(e.optString("type") + e.optLong("id"))) continue
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon") ?: continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val highway = tags.optString("highway")
            val kind =
                when {
                    highway == "traffic_signals" || tags.optString("crossing") == "traffic_signals" -> "signal"
                    highway == "services" -> "services"
                    highway == "rest_area" -> "rest_area"
                    tags.optString("amenity") == "fuel" -> "fuel"
                    tags.optString("amenity") == "charging_station" -> "charging"
                    else -> continue
                }
            val name = tags.optString("name").ifBlank { tags.optString("brand") }.ifBlank { tags.optString("operator") }
            out.add(RoadPoi(kind = kind, latitude = lat, longitude = lon, name = name))
        }
        return out
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
            } else if (out[idx].name.isBlank() && poi.name.isNotBlank()) {
                out[idx] = out[idx].copy(name = poi.name)
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
