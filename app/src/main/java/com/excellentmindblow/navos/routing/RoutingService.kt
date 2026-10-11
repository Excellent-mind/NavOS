package com.excellentmindblow.navos.routing

import com.excellentmindblow.navos.navigation.GeoUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One lane at the intersection where this step's maneuver happens.
 * indications are OSRM's turn-direction vocabulary (e.g. "left",
 * "straight", "slight right" - same words as RouteStep.modifier), valid
 * is whether taking this lane keeps you on the route. Comes from OSM's
 * turn:lanes tagging, which isn't present on every road - null/empty
 * just means no lane data for this turn. */
data class LaneInfo(
    val indications: List<String>,
    val valid: Boolean,
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuverType: String,
    val modifier: String?,
    val roadName: String,
    val point: RoutePoint,
    val lanes: List<LaneInfo>? = null,
)

data class Route(
    val geometry: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>,
)

/**
 * Native port of legacy-expo-rn/src/services/routing.ts - same OSRM free
 * public demo server, same request shape, and the same maneuver/modifier
 * -> natural-language instruction logic. Extended with alternatives=true
 * (the legacy app only ever requested one route) so the UI can offer a
 * Waze-style route choice before starting navigation.
 *
 * Blocking/synchronous by design - callers must run this off the main
 * thread (see MainActivity's background executor).
 */
object RoutingService {
    // Same reason geocoding.ts needs one: OSRM's public demo server
    // 403s requests that carry Java's generic default User-Agent
    // (looks bot-like with no app identity behind it).
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"

    private val MODIFIER_TEXT =
        mapOf(
            "uturn" to "make a U-turn",
            "sharp right" to "turn sharp right",
            "sharp left" to "turn sharp left",
            "right" to "turn right",
            "left" to "turn left",
            "slight right" to "bear right",
            "slight left" to "bear left",
            "straight" to "continue straight",
        )

    /** Returns every route OSRM offers - index 0 is always its preferred
     * ("fastest") one, any further entries are alternatives (OSRM doesn't
     * always have one to offer, so this can come back with just 1). More,
     * genuinely different routes are found afterwards by [findExtraRoutes]. */
    @Throws(Exception::class)
    fun calculateRoutes(
        origin: RoutePoint,
        destination: RoutePoint,
    ): List<Route> =
        try {
            requestRoutes(listOf(origin, destination), "3")
        } catch (e: Exception) {
            requestRoutes(listOf(origin, destination), "true")
        }

    /** Thrown when the server rejects the `exclude` option (not every OSRM build supports it). */
    private class ExcludeUnsupported : Exception()

    /** Asks for the routes honouring "avoid tolls / highways". If the server can't do that,
     * falls back to ordinary routes and raises [RoutePrefs.excludeUnsupported] so the app
     * can say so, rather than silently ignoring the driver's setting. */
    @Throws(Exception::class)
    private fun requestRoutes(
        points: List<RoutePoint>,
        alternatives: String,
    ): List<Route> {
        val exclude = RoutePrefs.excludeParam()
        return try {
            requestRoutesOnce(points, alternatives, exclude)
        } catch (e: ExcludeUnsupported) {
            RoutePrefs.excludeUnsupported = true
            requestRoutesOnce(points, alternatives, null)
        }
    }

    @Throws(Exception::class)
    private fun requestRoutesOnce(
        points: List<RoutePoint>,
        alternatives: String,
        exclude: String?,
    ): List<Route> {
        val coords = points.joinToString(";") { String.format(Locale.US, "%f,%f", it.longitude, it.latitude) }
        val url =
            URL(
                "https://router.project-osrm.org/route/v1/driving/$coords" +
                    "?overview=full&geometries=polyline&steps=true&annotations=false&alternatives=$alternatives" +
                    (if (exclude != null) "&exclude=$exclude" else ""),
            )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody =
                    try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) {
                        null
                    }
                // OSRM words this "Exclude flag combination is not supported." - matching on that,
                // not just "InvalidValue", so an unrelated bad parameter isn't mistaken for it.
                if (exclude != null && errorBody?.contains("exclude", ignoreCase = true) == true) throw ExcludeUnsupported()
                throw RuntimeException("Route request failed (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                throw RuntimeException("No route found (${json.optString("code")})")
            }

            val routesJson = json.getJSONArray("routes")
            val routes = mutableListOf<Route>()
            for (i in 0 until routesJson.length()) {
                routes.add(parseRoute(routesJson.getJSONObject(i)))
            }
            return routes
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Finds extra, genuinely different routes (Waze offers 4-5): OSRM only
     * volunteers 1-2 alternatives, so this forces the trip through detour
     * points to either side of the main route, keeps results that are
     * reasonably quick and clearly differ from what we already have, and
     * reports each one through [onFound] as soon as it is found. Blocking.
     */
    fun findExtraRoutes(
        origin: RoutePoint,
        destination: RoutePoint,
        existing: List<Route>,
        maxTotal: Int,
        isCancelled: () -> Boolean,
        onFound: (Route) -> Unit,
    ) {
        val base = existing.firstOrNull() ?: return
        if (base.geometry.size < 2 || base.distanceMeters < 1500.0) return
        val kept = existing.toMutableList()
        val bestDuration = existing.minOf { it.durationSeconds }
        // (how far along the main route, how far to the side as a share of its length)
        val plan =
            listOf(
                0.5 to 0.18, 0.5 to -0.18, 0.5 to 0.32, 0.5 to -0.32,
                0.3 to 0.15, 0.3 to -0.15, 0.7 to 0.15, 0.7 to -0.15,
                0.4 to 0.45, 0.6 to -0.45,
            )
        for ((fraction, side) in plan) {
            if (kept.size >= maxTotal || isCancelled()) break
            val (anchor, bearing) = pointAtFraction(base.geometry, fraction)
            val meters = (Math.abs(side) * base.distanceMeters).coerceIn(600.0, 25_000.0)
            val via = offsetPoint(anchor, bearing + if (side > 0) 90.0 else -90.0, meters)
            val found =
                try {
                    requestRoutes(listOf(origin, via, destination), "false").firstOrNull()
                } catch (e: Exception) {
                    null
                }
            val candidate = found ?: continue
            val reasonable =
                candidate.durationSeconds <= bestDuration * 1.5 + 300.0 &&
                    candidate.distanceMeters <= base.distanceMeters * 1.6 &&
                    candidate.steps.none { it.modifier == "uturn" }
            if (reasonable && kept.none { overlapFraction(candidate.geometry, it.geometry) > 0.85 }) {
                kept.add(candidate)
                onFound(candidate)
            }
            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun pointAtFraction(
        geometry: List<RoutePoint>,
        fraction: Double,
    ): Pair<RoutePoint, Double> {
        val seg = DoubleArray(geometry.size - 1) { GeoUtils.distanceMeters(geometry[it], geometry[it + 1]) }
        val target = seg.sum() * fraction
        var run = 0.0
        for (i in seg.indices) {
            if (run + seg[i] >= target) {
                val t = if (seg[i] == 0.0) 0.0 else (target - run) / seg[i]
                val a = geometry[i]
                val b = geometry[i + 1]
                val p = RoutePoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                return p to GeoUtils.bearingDegrees(a, b)
            }
            run += seg[i]
        }
        val n = geometry.size
        return geometry[n - 1] to GeoUtils.bearingDegrees(geometry[n - 2], geometry[n - 1])
    }

    private fun offsetPoint(
        p: RoutePoint,
        bearingDeg: Double,
        meters: Double,
    ): RoutePoint {
        val d = meters / 6371000.0
        val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(p.latitude)
        val lon1 = Math.toRadians(p.longitude)
        val lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(b))
        val lon2 =
            lon1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2))
        return RoutePoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
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
        return out
    }

    /** Share of [candidate] that runs along [other] (0 = totally different). */
    private fun overlapFraction(
        candidate: List<RoutePoint>,
        other: List<RoutePoint>,
    ): Double {
        val samples = thin(candidate, 150.0)
        val line = thin(other, 40.0)
        if (samples.isEmpty() || line.isEmpty()) return 0.0
        var near = 0
        for (s in samples) {
            var best = Double.MAX_VALUE
            for (o in line) {
                if (Math.abs(o.latitude - s.latitude) > 0.002 || Math.abs(o.longitude - s.longitude) > 0.002) continue
                val d = GeoUtils.distanceMeters(s, o)
                if (d < best) best = d
            }
            if (best <= 70.0) near++
        }
        return near.toDouble() / samples.size
    }

    private fun parseRoute(route: JSONObject): Route {
        val geometry = PolylineDecoder.decode(route.getString("geometry"))

        val steps = mutableListOf<RouteStep>()
        val legs = route.getJSONArray("legs")
        for (legIndex in 0 until legs.length()) {
            val legSteps = legs.getJSONObject(legIndex).getJSONArray("steps")
            for (stepIndex in 0 until legSteps.length()) {
                val step = legSteps.getJSONObject(stepIndex)
                val maneuver = step.getJSONObject("maneuver")
                val location = maneuver.getJSONArray("location")
                val type = maneuver.optString("type", "turn")
                // A detour point splits the trip into legs; drop the fake
                // "arrive" / "depart" steps at the joins so it stays one trip.
                if (type == "arrive" && legIndex < legs.length() - 1) continue
                if (type == "depart" && legIndex > 0) continue
                val modifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null
                val roadName = step.optString("name", "")

                steps.add(
                    RouteStep(
                        instruction = buildInstruction(type, modifier, roadName, maneuver),
                        distanceMeters = step.optDouble("distance", 0.0),
                        durationSeconds = step.optDouble("duration", 0.0),
                        maneuverType = type,
                        modifier = modifier,
                        roadName = roadName,
                        point = RoutePoint(latitude = location.getDouble(1), longitude = location.getDouble(0)),
                        lanes = parseLanes(step),
                    ),
                )
            }
        }

        return Route(
            geometry = geometry,
            distanceMeters = route.optDouble("distance", 0.0),
            durationSeconds = route.optDouble("duration", 0.0),
            steps = steps,
        )
    }

    /** A step's maneuver happens at its first intersection (OSRM: "the
     * very first [intersection] belonging to the StepManeuver"), so that
     * intersection's lanes are the ones to show before taking this turn.
     * Returns null when the underlying OSM way isn't lane-tagged - most
     * roads aren't, so this is the common case, not an error. */
    private fun parseLanes(step: JSONObject): List<LaneInfo>? {
        val intersections = step.optJSONArray("intersections") ?: return null
        if (intersections.length() == 0) return null
        val lanesJson = intersections.getJSONObject(0).optJSONArray("lanes") ?: return null
        if (lanesJson.length() == 0) return null

        val lanes = mutableListOf<LaneInfo>()
        for (i in 0 until lanesJson.length()) {
            val lane = lanesJson.getJSONObject(i)
            val indicationsJson = lane.optJSONArray("indications") ?: continue
            val indications = (0 until indicationsJson.length()).map { indicationsJson.getString(it) }
            lanes.add(LaneInfo(indications = indications, valid = lane.optBoolean("valid", false)))
        }
        return lanes.ifEmpty { null }
    }

    private fun buildInstruction(
        type: String,
        modifier: String?,
        roadName: String,
        maneuver: JSONObject,
    ): String {
        val namedRoad = roadName.ifBlank { "the road" }
        val modifierText = (modifier?.let { MODIFIER_TEXT[it] } ?: "continue").replaceFirstChar { it.uppercase() }

        return when (type) {
            "depart" -> if (roadName.isBlank()) "Head out" else "Head out onto $namedRoad"
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary" -> {
                val exit = maneuver.optInt("exit", 0)
                if (exit > 0) "At the roundabout, take exit $exit onto $namedRoad" else "Enter the roundabout"
            }
            "merge" -> "Merge onto $namedRoad"
            "on ramp" -> "Take the ramp onto $namedRoad"
            "off ramp" -> "Take the exit onto $namedRoad"
            "fork" -> "Keep ${modifier ?: "straight"} at the fork onto $namedRoad"
            "end of road" -> "$modifierText onto $namedRoad"
            "new name" -> "Continue onto $namedRoad"
            "continue" -> if (modifier == null || modifier == "straight") "Continue onto $namedRoad" else "$modifierText onto $namedRoad"
            else -> "$modifierText onto $namedRoad"
        }
    }
}
