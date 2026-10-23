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
    /** Road number of the road taken at this step ("E33"), when the map has one. */
    val ref: String? = null,
    /** Signposted destinations at this junction, OSM style: "E33: Kuantan, Genting Highlands, Gombak". */
    val destinations: String? = null,
    /** Exit number(s) on the sign ("3305A"), expressway exits only. */
    val exits: String? = null,
    /** Roundabout steps: which exit to take (1 = first), else 0. */
    val roundaboutExit: Int = 0,
)

/** An intermediate stop on a multi-stop trip (the final destination is NOT a
 * RouteStop - it is simply where the route ends).
 *
 * [number] is the stop's number for the whole trip (1, 2, 3...). It stays the
 * same after a reroute drops the stops already visited, so the pin on the map
 * and the "Stop 2" the driver hears never get renumbered mid-drive.
 *
 * [stepIndex] / [geometryIndex] are filled in by [RoutingService] once a route
 * exists: where this stop's "arrive" step sits in [Route.steps], and which
 * vertex of [Route.geometry] it is nearest. -1 until then. */
data class RouteStop(
    val point: RoutePoint,
    val name: String,
    val number: Int,
    val stepIndex: Int = -1,
    val geometryIndex: Int = -1,
)

data class Route(
    val geometry: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>,
    /** Intermediate stops, in driving order. Empty for an ordinary A -> B route. */
    val stops: List<RouteStop> = emptyList(),
    /** OpenStreetMap node id at each point of [geometry] (same length), for
     * live traffic: the piece between points i and i+1 is "nodes[i]-nodes[i+1]".
     * Null when the server didn't send them or they don't line up. */
    val nodes: LongArray? = null,
    /** Normal (free-flow) speed of each piece of [geometry], m/s (length - 1). */
    val freeFlowMs: DoubleArray? = null,
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
    // How far either side of the estimated spot to look for a stop on the line.
    private const val STOP_LOCATE_WINDOW_METERS = 300.0

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
    ): List<Route> {
        val routes =
            try {
                requestRoutes(listOf(origin, destination), "3")
            } catch (e: Exception) {
                requestRoutes(listOf(origin, destination), "true")
            }
        return withConnectors(routes, origin, destination)
    }

    /** Multi-stop trip: origin -> [stops] (in this order) -> destination, as ONE
     * route. OSRM only offers alternatives for two-point requests, so a trip with
     * stops comes back as a single best route. With no stops this is exactly
     * [calculateRoutes] (alternatives and all). */
    @Throws(Exception::class)
    fun calculateRoutes(
        origin: RoutePoint,
        stops: List<RouteStop>,
        destination: RoutePoint,
    ): List<Route> {
        if (stops.isEmpty()) return calculateRoutes(origin, destination)
        val points = listOf(origin) + stops.map { it.point } + destination
        return withConnectors(requestRoutes(points, "false", stops), origin, destination)
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
        stops: List<RouteStop> = emptyList(),
    ): List<Route> {
        val exclude = RoutePrefs.excludeParam()
        return try {
            requestWithSnapping(points, alternatives, exclude, stops)
        } catch (e: ExcludeUnsupported) {
            RoutePrefs.excludeUnsupported = true
            requestWithSnapping(points, alternatives, null, stops)
        }
    }

    /** First asks OSRM to snap the start / end to ANY road (`snapping=any`), so a
     * destination on a small private road - an apartment's lobby road, a car park
     * aisle, a driveway - is reached instead of being pulled back to the nearest
     * main road. If that gets no route (old server that doesn't know the option,
     * or the spot sits on a tiny road island cut off from the rest), asks again
     * the ordinary way. */
    @Throws(Exception::class)
    private fun requestWithSnapping(
        points: List<RoutePoint>,
        alternatives: String,
        exclude: String?,
        stops: List<RouteStop>,
    ): List<Route> =
        try {
            requestRoutesOnce(points, alternatives, exclude, stops, snapAny = true)
        } catch (e: ExcludeUnsupported) {
            throw e
        } catch (e: Exception) {
            requestRoutesOnce(points, alternatives, exclude, stops, snapAny = false)
        }

    /** Routing servers, tried in order. All speak the OSRM request format.
     * To use your own server later, put its address FIRST in this list
     * (it must end with "/route/v1/driving/"). */
    private val SERVERS =
        listOf(
            "https://router.project-osrm.org/route/v1/driving/",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
        )

    // The server that worked last is tried first for a few minutes, so a
    // dead server is not waited on for every single request.
    private var stickyIndex = 0
    private var stickyUntilMs = 0L
    private const val STICKY_MS = 5 * 60 * 1000L

    @Throws(Exception::class)
    private fun requestRoutesOnce(
        points: List<RoutePoint>,
        alternatives: String,
        exclude: String?,
        stops: List<RouteStop> = emptyList(),
        snapAny: Boolean = false,
    ): List<Route> {
        val coords = points.joinToString(";") { String.format(Locale.US, "%f,%f", it.longitude, it.latitude) }
        val now = System.currentTimeMillis()
        val first = if (now < stickyUntilMs) stickyIndex else 0
        var lastError: Exception? = null
        for (n in SERVERS.indices) {
            val i = (first + n) % SERVERS.size
            try {
                val routes = requestFromServer(SERVERS[i], coords, alternatives, exclude, stops, snapAny)
                if (i != 0 || now < stickyUntilMs) {
                    stickyIndex = i
                    stickyUntilMs = now + STICKY_MS
                }
                return routes
            } catch (e: ExcludeUnsupported) {
                throw e // the caller retries without "exclude"
            } catch (e: Exception) {
                lastError = e // this server failed - try the next one
            }
        }
        throw lastError ?: RuntimeException("Route request failed")
    }

    @Throws(Exception::class)
    private fun requestFromServer(
        baseUrl: String,
        coords: String,
        alternatives: String,
        exclude: String?,
        stops: List<RouteStop> = emptyList(),
        snapAny: Boolean = false,
    ): List<Route> {
        val url =
            URL(
                "$baseUrl$coords" +
                    "?overview=full&geometries=polyline&steps=true&annotations=nodes,speed&alternatives=$alternatives" +
                    (if (exclude != null) "&exclude=$exclude" else "") +
                    (if (snapAny) "&snapping=any" else ""),
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
                routes.add(parseRoute(routesJson.getJSONObject(i), stops))
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
            val candidate = found?.let { withConnectors(listOf(it), origin, destination).first() } ?: continue
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

    // Where the routing server's road network ends (it snaps to the nearest road it
    // allows) and where the driver really is / is going can differ by a few metres
    // to a few hundred: an apartment lobby road, a car park, a private driveway.
    // The blue line is stretched to the real start and the real destination so it
    // reaches them instead of stopping on the nearest main road.
    private const val CONNECTOR_MIN_METERS = 4.0
    private const val CONNECTOR_MAX_METERS = 500.0
    private const val CONNECTOR_SPEED_MPS = 4.0

    private fun withConnectors(
        routes: List<Route>,
        origin: RoutePoint,
        destination: RoutePoint,
    ): List<Route> =
        routes.map { route ->
            val line = route.geometry
            if (line.size < 2) return@map route
            val startGap = GeoUtils.distanceMeters(origin, line.first())
            val endGap = GeoUtils.distanceMeters(line.last(), destination)
            val addStart = startGap in CONNECTOR_MIN_METERS..CONNECTOR_MAX_METERS
            val addEnd = endGap in CONNECTOR_MIN_METERS..CONNECTOR_MAX_METERS
            if (!addStart && !addEnd) return@map route

            val extended = ArrayList<RoutePoint>(line.size + 2)
            if (addStart) extended.add(origin)
            extended.addAll(line)
            if (addEnd) extended.add(destination)
            val extra = (if (addStart) startGap else 0.0) + (if (addEnd) endGap else 0.0)
            // A start connector shifts every vertex by one, so the stops' positions on the line move too.
            val shift = if (addStart) 1 else 0
            route.copy(
                geometry = extended,
                distanceMeters = route.distanceMeters + extra,
                durationSeconds = route.durationSeconds + extra / CONNECTOR_SPEED_MPS,
                stops = route.stops.map { if (it.geometryIndex >= 0) it.copy(geometryIndex = it.geometryIndex + shift) else it },
            )
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

    private fun parseRoute(
        route: JSONObject,
        stops: List<RouteStop> = emptyList(),
    ): Route {
        val geometry = PolylineDecoder.decode(route.getString("geometry"))

        val steps = mutableListOf<RouteStep>()
        val legs = route.getJSONArray("legs")
        val stopStepIndices = mutableListOf<Int>()
        val stopSnapped = mutableListOf<RoutePoint>()
        val legDistances = mutableListOf<Double>()
        for (legIndex in 0 until legs.length()) {
            val leg = legs.getJSONObject(legIndex)
            legDistances.add(leg.optDouble("distance", 0.0))
            val legSteps = leg.getJSONArray("steps")
            for (stepIndex in 0 until legSteps.length()) {
                val step = legSteps.getJSONObject(stepIndex)
                val maneuver = step.getJSONObject("maneuver")
                val location = maneuver.getJSONArray("location")
                val type = maneuver.optString("type", "turn")
                val here = RoutePoint(latitude = location.getDouble(1), longitude = location.getDouble(0))
                val isJoin = legIndex < legs.length() - 1
                if (type == "arrive" && isJoin) {
                    // End of a leg that is not the last. For a real stop the
                    // driver reaches it here: keep it as a step (the engine
                    // announces it, the turn list shows it) - its distance and
                    // duration are filled in from the next leg's "depart" just
                    // below. For a hidden detour point (route alternatives are
                    // found by forcing the trip through one) there is nothing
                    // to announce: drop it so it stays one ordinary trip.
                    val stop = stops.getOrNull(legIndex)
                    if (stop != null) {
                        steps.add(
                            RouteStep(
                                instruction = stopInstruction(stop, legIndex),
                                distanceMeters = 0.0,
                                durationSeconds = 0.0,
                                maneuverType = "arrive",
                                modifier = null,
                                roadName = stop.name,
                                point = here,
                                lanes = null,
                            ),
                        )
                        stopStepIndices.add(steps.size - 1)
                        stopSnapped.add(here)
                    }
                    continue
                }
                if (type == "depart" && legIndex > 0) {
                    // A leg's "depart" is the point we just left (a stop or a
                    // detour point). A step's distance/duration is "from this
                    // maneuver to the next one", so hand the leg's first
                    // stretch to the step before it instead of losing it -
                    // otherwise every stop / detour point would shave that
                    // stretch off the distance and time remaining.
                    val last = steps.lastOrNull()
                    if (last != null) {
                        steps[steps.size - 1] =
                            last.copy(
                                distanceMeters = last.distanceMeters + step.optDouble("distance", 0.0),
                                durationSeconds = last.durationSeconds + step.optDouble("duration", 0.0),
                            )
                    }
                    continue
                }
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
                        point = here,
                        lanes = parseLanes(step),
                        ref = step.optString("ref", "").split(';').map { it.trim() }.firstOrNull { it.isNotEmpty() && it != "null" },
                        destinations = step.optString("destinations", "").trim().takeIf { it.isNotEmpty() && it != "null" },
                        exits = step.optString("exits", "").split(';').map { it.trim() }.firstOrNull { it.isNotEmpty() && it != "null" },
                        roundaboutExit = if (type == "roundabout" || type == "rotary") maneuver.optInt("exit", 0) else 0,
                    ),
                )
            }
        }

        val geometryIndices = locateStopsOnGeometry(geometry, legDistances, stopSnapped)
        val placedStops =
            stops.mapIndexedNotNull { i, stop ->
                val stepIdx = stopStepIndices.getOrNull(i) ?: return@mapIndexedNotNull null
                stop.copy(stepIndex = stepIdx, geometryIndex = geometryIndices.getOrElse(i) { -1 })
            }

        val (nodes, speeds) = parseAnnotations(legs, geometry.size)
        return Route(
            geometry = geometry,
            distanceMeters = route.optDouble("distance", 0.0),
            durationSeconds = route.optDouble("duration", 0.0),
            steps = steps,
            stops = placedStops,
            nodes = nodes,
            freeFlowMs = speeds,
        )
    }

    /**
     * OSM node ids and per-piece speeds from each leg's annotation, joined
     * into one run that lines up with the route line: a leg starts where the
     * last one ended, so its first node is dropped. Null when they don't line
     * up with the [points] of the decoded line (then live traffic is just off
     * for this route).
     */
    internal fun parseAnnotations(
        legs: org.json.JSONArray,
        points: Int,
    ): Pair<LongArray?, DoubleArray?> {
        val nodes = ArrayList<Long>(points)
        val speeds = ArrayList<Double>(points)
        for (l in 0 until legs.length()) {
            val ann = legs.getJSONObject(l).optJSONObject("annotation") ?: return null to null
            val n = ann.optJSONArray("nodes") ?: return null to null
            val sp = ann.optJSONArray("speed") ?: return null to null
            if (sp.length() != n.length() - 1) return null to null
            for (i in (if (l == 0) 0 else 1) until n.length()) nodes += n.getLong(i)
            for (i in 0 until sp.length()) speeds += sp.optDouble(i, 0.0)
        }
        if (nodes.size != points || speeds.size != points - 1) return null to null
        return nodes.toLongArray() to speeds.toDoubleArray()
    }

    private fun stopInstruction(
        stop: RouteStop?,
        legIndex: Int,
    ): String {
        val number = stop?.number ?: (legIndex + 1)
        val name = stop?.name.orEmpty().trim()
        return if (name.isBlank()) "Arrive at stop $number" else "Arrive at stop $number: $name"
    }

    /**
     * For each intermediate stop, the index of the route-line vertex where the
     * stop is. Estimated from the cumulative leg distances OSRM reports, then
     * snapped to the vertex nearest the stop's on-road position within a small
     * window - so a stop passed twice (drive in, U-turn, drive back out the same
     * road) resolves to the first visit, i.e. the end of the right leg.
     * NavigationEngine uses these to only match the GPS fix against the CURRENT
     * leg of the line; without that, the way back along the same road would look
     * like heading the wrong way and trigger endless rerouting.
     */
    private fun locateStopsOnGeometry(
        geometry: List<RoutePoint>,
        legDistances: List<Double>,
        snappedStops: List<RoutePoint>,
    ): List<Int> {
        if (snappedStops.isEmpty() || geometry.size < 2) return emptyList()
        val cum = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) cum[i] = cum[i - 1] + GeoUtils.distanceMeters(geometry[i - 1], geometry[i])
        val osrmTotal = legDistances.sum()
        val scale = if (osrmTotal > 0.0) cum[geometry.size - 1] / osrmTotal else 1.0
        val result = ArrayList<Int>(snappedStops.size)
        var floor = 0
        var legEnd = 0.0
        for (k in snappedStops.indices) {
            legEnd += legDistances.getOrElse(k) { 0.0 }
            val estimate = legEnd * scale
            var best = -1
            var bestDist = Double.MAX_VALUE
            for (i in floor until geometry.size) {
                if (cum[i] < estimate - STOP_LOCATE_WINDOW_METERS) continue
                if (cum[i] > estimate + STOP_LOCATE_WINDOW_METERS) break
                val d = GeoUtils.distanceMeters(geometry[i], snappedStops[k])
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            if (best < 0) {
                // Window found nothing (odd data) - fall back to the estimate itself.
                best = (floor until geometry.size).firstOrNull { cum[it] >= estimate } ?: (geometry.size - 1)
            }
            result.add(best)
            floor = best
        }
        return result
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
