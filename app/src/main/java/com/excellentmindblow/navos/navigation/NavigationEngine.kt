package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint

data class NavigationState(
    val currentStepIndex: Int,
    val instruction: String,
    val maneuverType: String,
    val modifier: String?,
    val distanceToNextTurnMeters: Double,
    val distanceRemainingMeters: Double,
    val durationRemainingSeconds: Double,
    val offRoute: Boolean,
    val isArrival: Boolean,
    // The turn after this one, so the UI can show Waze/Google Maps' "and
    // then" preview strip. Null once there's no further step (this one IS
    // the arrival).
    val nextInstruction: String? = null,
    val nextManeuverType: String? = null,
    val nextModifier: String? = null,
)

/**
 * Native port of legacy-expo-rn/src/services/navigationEngine.ts - same
 * thresholds and step-advance / off-route / announce-distance logic,
 * driven by pushing each GPS fix in (onLocation) instead of a React
 * hook's effect dependencies.
 *
 * steps[0] is "depart" and the last step is "arrive" (OSRM convention),
 * so stepIndex starts at 1 - the first real turn to look out for.
 */
class NavigationEngine(
    private val onState: (NavigationState) -> Unit,
    private val onAnnounce: (key: String, text: String) -> Unit,
) {
    companion object {
        private const val ADVANCE_STEP_METERS = 25.0
        private const val OFF_ROUTE_METERS = 40.0

        // Fixed-distance fallback for when speed isn't known (first fix,
        // or a GPS provider that doesn't report it).
        private const val ANNOUNCE_FAR_METERS = 300.0
        private const val ANNOUNCE_NEAR_METERS = 80.0

        // Speed-aware timing: announce by how many seconds out the turn is,
        // not a fixed distance - at highway speed a fixed 300m fallback
        // instruction comes far too late, and the same distance on a slow
        // kampung road fires far too early. Bounded on both ends so a
        // near-stationary or very fast reading doesn't produce a silly
        // distance.
        private const val ANNOUNCE_FAR_SECONDS = 15.0
        private const val ANNOUNCE_NEAR_SECONDS = 5.0
        private const val ANNOUNCE_FAR_MIN_METERS = 150.0
        private const val ANNOUNCE_FAR_MAX_METERS = 700.0
        private const val ANNOUNCE_NEAR_MIN_METERS = 40.0
        private const val ANNOUNCE_NEAR_MAX_METERS = 150.0

        // Below this, GPS speed is noisy/unreliable (stopped at a light,
        // crawling in traffic) - fall back to the fixed thresholds rather
        // than let a near-zero speed collapse both windows to the minimum.
        private const val MIN_RELIABLE_SPEED_MPS = 1.0
    }

    private var route: Route? = null
    private var stepIndex = 1

    fun setRoute(newRoute: Route?) {
        route = newRoute
        stepIndex = 1
    }

    fun onLocation(
        location: RoutePoint,
        speedMetersPerSecond: Double? = null,
    ) {
        val currentRoute = route ?: return
        val steps = currentRoute.steps
        if (steps.size < 2) return

        var idx = stepIndex.coerceAtMost(steps.size - 1)
        var distToTurn = GeoUtils.distanceMeters(location, steps[idx].point)

        // Advance past any steps we've effectively already reached (handles
        // a GPS jump skipping a short step, not just the normal case).
        while (idx < steps.size - 1 && distToTurn < ADVANCE_STEP_METERS) {
            idx += 1
            distToTurn = GeoUtils.distanceMeters(location, steps[idx].point)
        }
        stepIndex = idx

        val remainingSteps = steps.subList(idx, steps.size)
        val distanceRemaining = distToTurn + remainingSteps.drop(1).sumOf { it.distanceMeters }
        val durationRemaining = remainingSteps.sumOf { it.durationSeconds }

        val offLineDistance = GeoUtils.distanceToPolyline(location, currentRoute.geometry).first
        val offRoute = offLineDistance > OFF_ROUTE_METERS
        val isArrival = idx == steps.size - 1
        val nextStep = steps.getOrNull(idx + 1)

        onState(
            NavigationState(
                currentStepIndex = idx,
                instruction = steps[idx].instruction,
                maneuverType = steps[idx].maneuverType,
                modifier = steps[idx].modifier,
                distanceToNextTurnMeters = distToTurn,
                distanceRemainingMeters = distanceRemaining,
                durationRemainingSeconds = durationRemaining,
                offRoute = offRoute,
                isArrival = isArrival,
                nextInstruction = nextStep?.instruction,
                nextManeuverType = nextStep?.maneuverType,
                nextModifier = nextStep?.modifier,
            ),
        )

        if (!offRoute) {
            announceIfNeeded(idx, distToTurn, steps[idx].instruction, isArrival, speedMetersPerSecond)
        }
    }

    private fun announceIfNeeded(
        stepIndex: Int,
        distanceToTurn: Double,
        instruction: String,
        isArrival: Boolean,
        speedMetersPerSecond: Double?,
    ) {
        val nearThreshold = announceDistance(speedMetersPerSecond, ANNOUNCE_NEAR_SECONDS, ANNOUNCE_NEAR_MIN_METERS, ANNOUNCE_NEAR_MAX_METERS, ANNOUNCE_NEAR_METERS)
        val farThreshold = announceDistance(speedMetersPerSecond, ANNOUNCE_FAR_SECONDS, ANNOUNCE_FAR_MIN_METERS, ANNOUNCE_FAR_MAX_METERS, ANNOUNCE_FAR_METERS)

        if (distanceToTurn <= nearThreshold) {
            onAnnounce("$stepIndex-near", instruction)
        } else if (distanceToTurn <= farThreshold) {
            val rounded = (Math.round(distanceToTurn / 10) * 10).toInt()
            onAnnounce(
                "$stepIndex-far",
                "In $rounded meters, ${instruction.replaceFirstChar { it.lowercase() }}",
            )
        }
        if (isArrival && distanceToTurn <= nearThreshold) {
            onAnnounce("$stepIndex-arrive", "You have arrived at your destination")
        }
    }

    /** Converts a "seconds before the turn" target into a distance using
     * the driver's current speed, clamped to a sane range. Falls back to a
     * fixed distance when speed isn't known or isn't reliable yet. */
    private fun announceDistance(
        speedMetersPerSecond: Double?,
        seconds: Double,
        minMeters: Double,
        maxMeters: Double,
        fallbackMeters: Double,
    ): Double {
        val speed = speedMetersPerSecond ?: return fallbackMeters
        if (speed < MIN_RELIABLE_SPEED_MPS) return fallbackMeters
        return (speed * seconds).coerceIn(minMeters, maxMeters)
    }
}
