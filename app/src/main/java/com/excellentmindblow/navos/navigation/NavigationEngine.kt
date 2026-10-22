package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import com.excellentmindblow.navos.routing.RouteStop

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
    // Lane guidance for the upcoming turn - null/empty when the road isn't
    // lane-tagged in OSM, which is most roads, so this is routinely absent.
    val lanes: List<LaneInfo>? = null,
    // True when we're sure the driver took a different road (heading no
    // longer matches the route while drifting off it), not just a noisy GPS
    // fix. The service reroutes on the very first such fix.
    val offRouteConfirmed: Boolean = false,
    // Multi-stop trips: how many intermediate stops are still ahead, which
    // one is next (null when only the final destination is left) and how far
    // / how long to it. All zero/null on an ordinary A -> B drive.
    val stopsRemaining: Int = 0,
    val nextStop: RouteStop? = null,
    val distanceToNextStopMeters: Double = 0.0,
    val durationToNextStopSeconds: Double = 0.0,
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
    // Fired once each time the driver reaches an intermediate stop (multi-stop
    // trips). The engine itself carries on to the next leg automatically.
    private val onStopReached: (RouteStop) -> Unit = {},
) {
    companion object {
        private const val ADVANCE_STEP_METERS = 25.0

        // An intermediate stop counts as reached a little further out than a
        // turn: the stop may be on a side street or in a car park, and the
        // driver should not have to hit one exact spot for the trip to carry on.
        private const val STOP_REACHED_METERS = 40.0

        // Segments of slack either side of the current leg when matching the
        // GPS fix to the route line on a multi-stop trip.
        private const val LEG_MATCH_MARGIN_SEGMENTS = 4

        // Passing a maneuver without a fix inside ADVANCE_STEP_METERS of it (a
        // GPS gap: tunnel, lost fix, a fast car on a slow GPS) still counts once
        // the driver is on the line and this far past it along the route.
        // Without this the engine stayed on the old turn for the rest of the
        // drive, with its countdown growing.
        private const val PASSED_STEP_MARGIN_METERS = 15.0

        // How far ahead of the last maneuver the fix is matched to the line,
        // so a later stretch that reuses the same road (out and back to a
        // stop) can't be mistaken for where the driver is now.
        private const val PROGRESS_SEARCH_SEGMENTS = 150
        private const val OFF_ROUTE_METERS = 28.0 // was 40: notice a wrong turn sooner

        // Faster check for taking another road: slightly off the line AND
        // pointing a different way than the route is already a wrong turn.
        private const val HEADING_OFF_ROUTE_METERS = 14.0
        private const val HEADING_MISMATCH_DEGREES = 55.0
        private const val HEADING_MIN_SPEED_MPS = 2.0

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
    private var stopsByStep: Map<Int, RouteStop> = emptyMap()

    // Where each maneuver sits on the route line: its segment and its
    // distance from the start. Null when the line is too short to match.
    private var track: RouteTrack? = null
    private var stepSegments = IntArray(0)
    private var stepProgress = DoubleArray(0)

    fun setRoute(newRoute: Route?) {
        route = newRoute
        stepIndex = 1
        stopsByStep = newRoute?.stops?.filter { it.stepIndex >= 0 }?.associateBy { it.stepIndex } ?: emptyMap()
        placeStepsOnLine(newRoute)
    }

    /** Matches every maneuver to the route line in driving order (each search
     * starts where the previous maneuver matched, so a route that passes the
     * same spot twice keeps its maneuvers in sequence). */
    private fun placeStepsOnLine(newRoute: Route?) {
        val line = newRoute?.geometry
        if (newRoute == null || line == null || line.size < 2) {
            track = null
            stepSegments = IntArray(0)
            stepProgress = DoubleArray(0)
            return
        }
        val routeTrack = RouteTrack(line)
        val segments = IntArray(newRoute.steps.size)
        val progress = DoubleArray(newRoute.steps.size)
        var from = 0
        for ((i, step) in newRoute.steps.withIndex()) {
            val snap = GeoUtils.snapToPolyline(step.point, line, from)
            if (snap == null) {
                segments[i] = from
                progress[i] = if (i == 0) 0.0 else progress[i - 1]
            } else {
                segments[i] = snap.segmentIndex
                progress[i] = routeTrack.progressOf(snap)
                from = snap.segmentIndex
            }
        }
        track = routeTrack
        stepSegments = segments
        stepProgress = progress
    }

    /** Distance along the route of [location], matched on the stretch from the
     * last maneuver passed onward. Null when off the line or no track. */
    private fun progressAlongRoute(
        location: RoutePoint,
        line: List<RoutePoint>,
        idx: Int,
    ): Double? {
        val routeTrack = track ?: return null
        val from = stepSegments.getOrNull(idx - 1) ?: 0
        val snap = GeoUtils.snapToPolyline(location, line, from, PROGRESS_SEARCH_SEGMENTS) ?: return null
        if (snap.distanceMeters > OFF_ROUTE_METERS) return null
        return routeTrack.progressOf(snap)
    }

    /** Distance (m) and time (s) left of the road leading to the step at
     * [stepIdx]: how far the driver still is from the upcoming maneuver, plus
     * every full step between that maneuver and [untilStep] (exclusive). */
    private fun roadTo(
        steps: List<RouteStep>,
        idx: Int,
        untilStep: Int,
        distToTurn: Double,
    ): Pair<Double, Double> {
        var meters = distToTurn
        var seconds = approachSeconds(steps, idx, distToTurn)
        for (i in idx until untilStep.coerceAtMost(steps.size)) {
            meters += steps[i].distanceMeters
            seconds += steps[i].durationSeconds
        }
        return meters to seconds
    }

    /** Time still needed to drive the remaining straight-line distance to the
     * upcoming maneuver, estimated from how long the step that leads to it takes. */
    private fun approachSeconds(
        steps: List<RouteStep>,
        idx: Int,
        distToTurn: Double,
    ): Double {
        val prev = steps.getOrNull(idx - 1) ?: return 0.0
        if (prev.distanceMeters <= 1.0) return 0.0
        return prev.durationSeconds * (distToTurn / prev.distanceMeters).coerceIn(0.0, 1.0)
    }

    /** Distance to the route line and the segment it is nearest. On a multi-stop
     * trip only the CURRENT leg of the line is searched: the way back along a
     * road already driven out (stop, U-turn, return) lies on the same asphalt but
     * runs the opposite direction, and matching it would read as a wrong turn. */
    private fun nearestOnRoute(
        location: RoutePoint,
        currentRoute: Route,
        stepIdx: Int,
    ): Pair<Double, Int> {
        val line = currentRoute.geometry
        val stops = currentRoute.stops
        if (stops.isEmpty() || stops.any { it.geometryIndex < 0 } || line.size < 2) {
            return GeoUtils.distanceToPolyline(location, line)
        }
        val leg = stops.count { it.stepIndex < stepIdx }
        val start = if (leg == 0) 0 else stops[leg - 1].geometryIndex
        val end = if (leg < stops.size) stops[leg].geometryIndex else line.size - 1
        val first = (start - LEG_MATCH_MARGIN_SEGMENTS).coerceAtLeast(0)
        val span = (end + LEG_MATCH_MARGIN_SEGMENTS) - first
        val snap = GeoUtils.snapToPolyline(location, line, first, span)
        return if (snap != null) snap.distanceMeters to snap.segmentIndex else GeoUtils.distanceToPolyline(location, line)
    }

    fun onLocation(
        location: RoutePoint,
        speedMetersPerSecond: Double? = null,
        bearingDegrees: Double? = null,
    ) {
        val currentRoute = route ?: return
        val steps = currentRoute.steps
        if (steps.size < 2) return

        var idx = stepIndex.coerceAtMost(steps.size - 1)
        var distToTurn = GeoUtils.distanceMeters(location, steps[idx].point)

        // Advance past any steps we've effectively already reached (handles
        // a GPS jump skipping a short step, not just the normal case).
        val reachedStops = ArrayList<RouteStop>(1)
        while (idx < steps.size - 1 && distToTurn < (if (stopsByStep.containsKey(idx)) STOP_REACHED_METERS else ADVANCE_STEP_METERS)) {
            stopsByStep[idx]?.let { reachedStops.add(it) }
            idx += 1
            distToTurn = GeoUtils.distanceMeters(location, steps[idx].point)
        }
        // Then anything the driver is already past along the line, even if no
        // fix came close to it.
        val progress = progressAlongRoute(location, currentRoute.geometry, idx)
        if (progress != null) {
            while (idx < steps.size - 1 && idx < stepProgress.size && stepProgress[idx] < progress - PASSED_STEP_MARGIN_METERS) {
                stopsByStep[idx]?.let { reachedStops.add(it) }
                idx += 1
                distToTurn = GeoUtils.distanceMeters(location, steps[idx].point)
            }
        }
        stepIndex = idx

        // Distance/time left = the way to the upcoming maneuver plus every step
        // from that maneuver on (a step's distance is "from its maneuver to the
        // next one", so the current step's own stretch counts too).
        val (distanceRemaining, durationRemaining) = roadTo(steps, idx, steps.size, distToTurn)

        val upcomingStops = currentRoute.stops.filter { it.stepIndex >= idx }
        val nextStop = upcomingStops.firstOrNull()
        val toNextStop = nextStop?.let { roadTo(steps, idx, it.stepIndex, distToTurn) }

        val (offLineDistance, segmentIndex) = nearestOnRoute(location, currentRoute, idx)
        var headingMismatch = false
        val line = currentRoute.geometry
        if (bearingDegrees != null &&
            (speedMetersPerSecond ?: 0.0) > HEADING_MIN_SPEED_MPS &&
            offLineDistance > HEADING_OFF_ROUTE_METERS &&
            segmentIndex + 1 < line.size
        ) {
            val routeBearing = GeoUtils.bearingDegrees(line[segmentIndex], line[segmentIndex + 1])
            var diff = Math.abs(bearingDegrees - routeBearing) % 360.0
            if (diff > 180.0) diff = 360.0 - diff
            headingMismatch = diff > HEADING_MISMATCH_DEGREES
        }
        val offRoute = offLineDistance > OFF_ROUTE_METERS || headingMismatch
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
                lanes = steps[idx].lanes,
                offRouteConfirmed = headingMismatch,
                stopsRemaining = upcomingStops.size,
                nextStop = nextStop,
                distanceToNextStopMeters = toNextStop?.first ?: 0.0,
                durationToNextStopSeconds = toNextStop?.second ?: 0.0,
            ),
        )

        for (stop in reachedStops) {
            val more = currentRoute.stops.count { it.stepIndex > stop.stepIndex }
            val label = stop.name.ifBlank { "stop ${stop.number}" }
            onAnnounce(
                "stop-reached-${stop.number}",
                "You have arrived at $label. " + if (more > 0) "Continuing to your next stop." else "Continuing to your destination.",
            )
            onStopReached(stop)
        }

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
            onAnnounce(
                "$stepIndex-far",
                "In ${Units.spoken(distanceToTurn)}, ${instruction.replaceFirstChar { it.lowercase() }}",
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
