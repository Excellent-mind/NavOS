package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One mapped road with a posted limit (km/h) and its shape. */
class RoadLimit(
    val kmh: Int,
    val line: List<RoutePoint>,
) {
    val minLat = line.minOf { it.latitude }
    val maxLat = line.maxOf { it.latitude }
    val minLon = line.minOf { it.longitude }
    val maxLon = line.maxOf { it.longitude }
}

/**
 * Posted speed limits from OpenStreetMap `maxspeed` tags (Overpass API, free,
 * no key). Coverage depends on how well an area is mapped: a road with no
 * `maxspeed` tag simply has no limit shown - it is never guessed.
 * Blocking: call off the main thread.
 */
object SpeedLimitService {
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"
    private val ENDPOINTS =
        listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

    /** Roads within this distance of the driver are downloaded at once. */
    const val FETCH_RADIUS_M = 1500

    /** A fix counts as "on" a road when it is this close to its line. */
    private const val MATCH_METERS = 22.0
    private const val BOX_DEGREES = 0.0003 // ~33 m, cheap pre-filter before real distance maths

    @Throws(Exception::class)
    fun fetchAround(
        lat: Double,
        lon: Double,
    ): List<RoadLimit> {
        val query =
            "[out:json][timeout:20];" +
                "way[\"highway\"][\"maxspeed\"](around:$FETCH_RADIUS_M,$lat,$lon);" +
                "out tags geom;"
        val elements = runQuery(query).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<RoadLimit>()
        for (i in 0 until elements.length()) {
            val way = elements.optJSONObject(i) ?: continue
            val kmh = parseMaxspeed(way.optJSONObject("tags")?.optString("maxspeed").orEmpty()) ?: continue
            val geometry = way.optJSONArray("geometry") ?: continue
            val line = ArrayList<RoutePoint>(geometry.length())
            for (j in 0 until geometry.length()) {
                val p = geometry.optJSONObject(j) ?: continue
                line.add(RoutePoint(p.getDouble("lat"), p.getDouble("lon")))
            }
            if (line.size >= 2) out.add(RoadLimit(kmh, line))
        }
        return out
    }

    /** The limit (km/h) of the road [at] is on, or null when it isn't on a road with a mapped limit. */
    fun limitAt(
        roads: List<RoadLimit>,
        at: RoutePoint,
    ): Int? {
        var best: RoadLimit? = null
        var bestDistance = MATCH_METERS
        for (road in roads) {
            if (at.latitude < road.minLat - BOX_DEGREES || at.latitude > road.maxLat + BOX_DEGREES) continue
            if (at.longitude < road.minLon - BOX_DEGREES || at.longitude > road.maxLon + BOX_DEGREES) continue
            val d = GeoUtils.distanceToPolyline(at, road.line).first
            if (d < bestDistance) {
                bestDistance = d
                best = road
            }
        }
        return best?.kmh
    }

    /** "60", "60 km/h" or "40 mph" -> km/h. Anything else ("signals", "none", ...) -> null. */
    fun parseMaxspeed(raw: String): Int? {
        val match = Regex("^(\\d{2,3})\\s*(mph|km/h|kph)?$").find(raw.trim().lowercase()) ?: return null
        val value = match.groupValues[1].toInt()
        return if (match.groupValues[2] == "mph") Math.round(value * 1.609).toInt() else value
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
                connection.connectTimeout = 10_000
                connection.readTimeout = 25_000
                try {
                    connection.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw RuntimeException("Speed limit request failed (${connection.responseCode})")
                    }
                    return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Speed limit request failed")
    }
}
