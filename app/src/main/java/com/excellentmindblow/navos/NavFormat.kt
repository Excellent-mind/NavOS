package com.excellentmindblow.navos

import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.Route
import java.util.Locale

/**
 * How distances, durations and maneuvers are shown on screen. Pure functions
 * of their inputs (plus the [Units] setting), so every screen formats the
 * same way and the rules are unit-tested.
 */
object NavFormat {
    /** Distance to a search result or place. */
    fun formatDistance(meters: Float): String =
        if (Units.miles) Units.short(meters.toDouble()) else when {
            meters < 1000f -> "${(Math.round(meters / 10f) * 10)} m"
            meters < 100_000f -> String.format(Locale.US, "%.1f km", meters / 1000f)
            else -> "${Math.round(meters / 1000f)} km"
        }

    /** "Toll" when a toll plaza is on the route, else null. No price. */
    fun tollSummary(pois: List<RoadPoi>?): String? =
        if (pois?.any { it.kind == "toll" } == true) "Toll" else null

    /** A route's duration in the route list: "24 min", "1 h 5 min". */
    fun formatRouteMinutes(seconds: Double): String {
        val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    /** Remaining trip time under the ETA: "24 min", "1:05 h". */
    fun formatTripDuration(seconds: Double): String {
        val totalMinutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (totalMinutes >= 60) {
            String.format(Locale.US, "%d:%02d h", totalMinutes / 60, totalMinutes % 60)
        } else {
            "$totalMinutes min"
        }
    }

    /** Remaining trip distance under the ETA. */
    fun formatTripDistance(meters: Double): String =
        if (Units.miles) Units.short(meters) else when {
            meters < 1000 -> "${meters.toInt()} m"
            meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
            else -> String.format(Locale.US, "%.0f km", meters / 1000.0)
        }

    /** The road this route spends the most distance on - the "Via ..." line. */
    fun viaRoadName(route: Route): String? =
        route.steps
            .filter { it.roadName.isNotBlank() }
            .groupBy { it.roadName }
            .maxByOrNull { (_, steps) -> steps.sumOf { it.distanceMeters } }
            ?.key

    /** Picks the Waze-style arrow for a maneuver: a distinct drawn arrow
     * for every turn angle, plus U-turn, fork/ramp, roundabout (exit side)
     * and arrival. No rotation tricks - each is its own icon. */
    fun iconForManeuver(
        maneuverType: String,
        modifier: String?,
    ): Int {
        val mod = modifier.orEmpty()
        return when {
            maneuverType == "arrive" -> R.drawable.ic_turn_arrive
            maneuverType.contains("roundabout") || maneuverType.contains("rotary") ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_roundabout_left
                    mod.contains("right") -> R.drawable.ic_turn_roundabout_right
                    else -> R.drawable.ic_turn_roundabout_straight
                }
            maneuverType == "uturn" || mod == "uturn" -> R.drawable.ic_turn_uturn
            maneuverType == "fork" || maneuverType == "off ramp" || maneuverType == "on ramp" ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_fork_left
                    mod.contains("right") -> R.drawable.ic_turn_fork_right
                    else -> R.drawable.ic_turn_straight
                }
            else ->
                when (mod) {
                    "sharp right" -> R.drawable.ic_turn_sharp_right
                    "right" -> R.drawable.ic_turn_right
                    "slight right" -> R.drawable.ic_turn_slight_right
                    "slight left" -> R.drawable.ic_turn_slight_left
                    "left" -> R.drawable.ic_turn_left
                    "sharp left" -> R.drawable.ic_turn_sharp_left
                    else -> R.drawable.ic_turn_straight // straight / depart / continue / new name / merge
                }
        }
    }

    /** Turn countdown shown on the round button: km with one decimal from
     * 1 km up, then 950 m, 900 m ... 100 m in 50 m steps, then 10 m steps
     * for the last stretch. */
    fun formatCountdownDistance(meters: Double): String =
        if (Units.miles) Units.short(meters) else when {
            meters >= 1000 -> String.format(Locale.US, "%.1f km", meters / 1000)
            meters >= 100 -> "${(Math.round(meters / 50.0) * 50).toInt().coerceAtMost(950)} m"
            else -> "${(Math.round(meters / 10.0) * 10).toInt().coerceAtLeast(10)} m"
        }

    /** Distance in the "Next turns" list. */
    fun formatTurnDistance(meters: Double): String =
        if (Units.miles) {
            Units.short(meters)
        } else if (meters >= 1000) {
            String.format(Locale.US, "%.1f km", meters / 1000)
        } else {
            // Round to 10 m like Waze's list so it doesn't flicker every fix.
            "${(Math.round(meters / 10.0) * 10).toInt().coerceAtLeast(10)} m"
        }

    /** Rotation (degrees) of the straight lane arrow for an OSRM lane
     * indication. */
    fun rotationForIndication(indication: String): Float =
        when (indication) {
            "sharp right" -> 135f
            "right" -> 90f
            "slight right" -> 45f
            "slight left" -> -45f
            "left" -> -90f
            "sharp left" -> -135f
            "uturn" -> 180f
            else -> 0f // straight / none
        }
}
