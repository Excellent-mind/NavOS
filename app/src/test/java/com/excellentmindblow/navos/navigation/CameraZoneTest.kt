package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraZoneTest {
    private val here = RoutePoint(3.1600, 101.7120)
    private val headingEast = 90.0

    private fun camera(
        metersEast: Double,
        metersNorth: Double = 0.0,
    ) = RoadPoi(
        kind = "camera",
        latitude = here.latitude + metersNorth / 111_320.0,
        longitude = here.longitude + metersEast / 111_320.0,
        name = "AWAS",
    )

    private fun nearest(
        cameras: List<RoadPoi>,
        heading: Double? = headingEast,
        passedSegment: Int = 5,
        segmentOf: (RoadPoi) -> Int = { 6 },
    ) = CameraZone.nearestAhead(here, heading, cameras, passedSegment, segmentOf)

    @Test
    fun aCameraAheadWithinTheZoneIsFound() {
        val cam = camera(150.0)
        val approach = nearest(listOf(cam))!!
        assertEquals(cam, approach.camera)
        assertEquals(150.0, approach.distanceMeters, 2.0)
    }

    @Test
    fun aCameraBeyond200mIsIgnored() {
        assertNull(nearest(listOf(camera(220.0))))
    }

    @Test
    fun theNearestOfSeveralWins() {
        val near = camera(80.0)
        assertEquals(near, nearest(listOf(camera(180.0), near))!!.camera)
    }

    @Test
    fun aCameraBehindIsIgnoredUnlessRightBeside() {
        assertNull(nearest(listOf(camera(-100.0))))
        assertEquals(10.0, nearest(listOf(camera(-10.0)))!!.distanceMeters, 1.0)
    }

    @Test
    fun withoutAHeadingEveryNearbyCameraCounts() {
        assertEquals(100.0, nearest(listOf(camera(-100.0)), heading = null)!!.distanceMeters, 2.0)
    }

    @Test
    fun aCameraOffTheRouteOrAlreadyPassedIsIgnored() {
        assertNull(nearest(listOf(camera(100.0)), segmentOf = { -1 }))
        assertNull(nearest(listOf(camera(100.0)), passedSegment = 10, segmentOf = { 8 }))
        // One segment of slack: the camera may sit on the segment just behind the snap.
        assertEquals(100.0, nearest(listOf(camera(100.0)), passedSegment = 10, segmentOf = { 9 })!!.distanceMeters, 2.0)
    }
}
