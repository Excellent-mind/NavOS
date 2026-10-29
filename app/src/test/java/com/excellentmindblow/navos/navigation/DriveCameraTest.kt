package com.excellentmindblow.navos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveCameraTest {
    @Test
    fun fasterMeansFurtherOut() {
        val town = DriveCamera.target(30.0, null)
        val highway = DriveCamera.target(110.0, null)
        assertTrue(highway.zoom < town.zoom)
        assertTrue(highway.carAt > town.carAt)
    }

    @Test
    fun turnAheadZoomsInAndTiltsDown() {
        val far = DriveCamera.target(90.0, 1000.0)
        val near = DriveCamera.target(90.0, 30.0)
        assertTrue(near.zoom > far.zoom)
        assertTrue(near.tilt < far.tilt)
        assertEquals(DriveCamera.TURN_ZOOM, DriveCamera.target(90.0, 0.0).zoom, 1e-9)
    }

    @Test
    fun stepIsRateLimitedAndIgnoresTinyChanges() {
        val now = DriveCamera.View(17.0, 55.0, 0.65)
        val big = DriveCamera.step(now, DriveCamera.View(15.0, 55.0, 0.65), 1.0)
        assertEquals(17.0 - DriveCamera.MAX_ZOOM_RATE, big.zoom, 1e-9)
        val tiny = DriveCamera.step(now, DriveCamera.View(17.05, 55.0, 0.65), 1.0)
        assertEquals(17.0, tiny.zoom, 1e-9)
    }

    @Test
    fun straightOnIsNotATurn() {
        assertFalse(DriveCamera.isRealTurn("continue", "straight"))
        assertFalse(DriveCamera.isRealTurn("new name", "slight left"))
        assertFalse(DriveCamera.isRealTurn("arrive", null))
        assertTrue(DriveCamera.isRealTurn("turn", "left"))
        assertTrue(DriveCamera.isRealTurn("off ramp", "slight left"))
    }

    @Test
    fun freeCameraResumesAfterQuiet() {
        assertFalse(DriveCamera.shouldResume(DriveCamera.Mode.FREE, 0, 5_000))
        assertTrue(DriveCamera.shouldResume(DriveCamera.Mode.FREE, 0, 12_000))
        assertFalse(DriveCamera.shouldResume(DriveCamera.Mode.OVERVIEW, 0, 60_000))
    }
}
