package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoutePoint

/** Decides which speed camera, if any, the driver is approaching. */
object CameraZone {
    /** A camera alerts once the driver is this close to it. */
    const val RADIUS_M = 200.0

    /** Within this distance the camera counts as ahead whatever the heading says. */
    private const val ALWAYS_AHEAD_M = 15.0

    /** A camera more than this far off the heading is behind the driver. */
    private const val BEHIND_ANGLE_DEGREES = 100.0

    data class Approach(
        val camera: RoadPoi,
        val distanceMeters: Double,
    )

    /**
     * The nearest camera that is within [RADIUS_M] of [here], lies on the
     * route ([segmentOf] gives its route segment, or -1 when it is not on
     * the route), has not been passed yet (its segment is not before
     * [passedSegment] - 1) and is ahead of the driver's [heading].
     */
    fun nearestAhead(
        here: RoutePoint,
        heading: Double?,
        cameras: List<RoadPoi>,
        passedSegment: Int,
        segmentOf: (RoadPoi) -> Int,
    ): Approach? {
        var nearest: Approach? = null
        for (camera in cameras) {
            val at = RoutePoint(camera.latitude, camera.longitude)
            val d = GeoUtils.distanceMeters(here, at)
            if (d > RADIUS_M) continue
            val segment = segmentOf(camera)
            if (segment < 0 || segment < passedSegment - 1) continue
            if (heading != null && d > ALWAYS_AHEAD_M &&
                GeoUtils.angleDiff(GeoUtils.bearingDegrees(here, at), heading) > BEHIND_ANGLE_DEGREES
            ) {
                continue
            }
            if (nearest == null || d < nearest.distanceMeters) nearest = Approach(camera, d)
        }
        return nearest
    }
}
