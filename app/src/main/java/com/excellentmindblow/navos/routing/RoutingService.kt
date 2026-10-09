package com.excellentmindblow.navos.routing

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuverType: String,
    val modifier: String?,
    val roadName: String,
    val point: RoutePoint,
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
 * -> natural-language instruction logic.
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

    @Throws(Exception::class)
    fun calculateRoute(
        origin: RoutePoint,
        destination: RoutePoint,
    ): Route {
        val coords =
            String.format(
                Locale.US,
                "%f,%f;%f,%f",
                origin.longitude,
                origin.latitude,
                destination.longitude,
                destination.latitude,
            )
        val url =
            URL(
                "https://router.project-osrm.org/route/v1/driving/$coords" +
                    "?overview=full&geometries=polyline&steps=true&annotations=false",
            )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Route request failed (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                throw RuntimeException("No route found (${json.optString("code")})")
            }

            val route = json.getJSONArray("routes").getJSONObject(0)
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
        } finally {
            connection.disconnect()
        }
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
