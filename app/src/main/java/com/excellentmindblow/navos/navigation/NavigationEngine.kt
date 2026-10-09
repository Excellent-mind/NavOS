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
        private const val ANNOUNCE_FAR_METERS = 300.0
        private const val ANNOUNCE_NEAR_METERS = 80.0
    }

    private var route: Route? = null
    private var stepIndex = 1

    fun setRoute(newRoute: Route?) {
        route = newRoute
        stepIndex = 1
    }

    fun onLocation(location: RoutePoint) {
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
            ),
        )

        if (!offRoute) {
            announceIfNeeded(idx, distToTurn, steps[idx].instruction, isArrival)
        }
    }

    private fun announceIfNeeded(
        stepIndex: Int,
        distanceToTurn: Double,
        instruction: String,
        isArrival: Boolean,
    ) {
        if (distanceToTurn <= ANNOUNCE_NEAR_METERS) {
            onAnnounce("$stepIndex-near", instruction)
        } else if (distanceToTurn <= ANNOUNCE_FAR_METERS) {
            val rounded = (Math.round(distanceToTurn / 10) * 10).toInt()
            onAnnounce(
                "$stepIndex-far",
                "In $rounded meters, ${instruction.replaceFirstChar { it.lowercase() }}",
            )
        }
        if (isArrival && distanceToTurn <= ANNOUNCE_NEAR_METERS) {
            onAnnounce("$stepIndex-arrive", "You have arrived at your destination")
        }
    }
}
