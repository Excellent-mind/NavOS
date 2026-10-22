package com.excellentmindblow.navos.signs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignCameraTest {
    // The driving camera: 390 x 844 dp screen, arrow (camera target) 73% down.
    private val cam = SignCamera(195.0, 616.12, 844.0, 55.0)

    @Test
    fun cameraTargetIsTheGroundUnderTheCentre() {
        val g = cam.groundOf(195.0, 616.12)!!
        assertEquals(0.0, g[0], 1e-6)
        assertEquals(0.0, g[1], 1e-6)
    }

    @Test
    fun groundAndProjectAreInverse() {
        val p = cam.project(-120.0, 900.0, 0.0)!!
        val g = cam.groundOf(p[0], p[1])!!
        assertEquals(-120.0, g[0], 1e-6)
        assertEquals(900.0, g[1], 1e-6)
    }

    @Test
    fun boardStandsOnItsFeetAndLeansLikeTheMap() {
        // Feet 200 units apart = 1 unit per metre.
        val l = cam.project(-100.0, 1500.0, 0.0)!!
        val r = cam.project(100.0, 1500.0, 0.0)!!
        val q = cam.quad(l[0], l[1], r[0], r[1], 200.0, 26.0, 91.0)!!
        val tl = cam.project(-100.0, 1500.0, 91.0)!!
        val bl = cam.project(-100.0, 1500.0, 26.0)!!
        assertEquals(tl[0], q[0].toDouble(), 1e-3)
        assertEquals(tl[1], q[1].toDouble(), 1e-3)
        assertEquals(bl[0], q[6].toDouble(), 1e-3)
        assertEquals(bl[1], q[7].toDouble(), 1e-3)
        val topWidth = q[2] - q[0]
        val bottomWidth = q[4] - q[6]
        assertTrue("seen from above, the top edge is the wider one", topWidth > bottomWidth)
        assertTrue("upright: top is above bottom on screen", q[1] < q[7])
    }

    @Test
    fun skyHasNoGround() {
        val flat = SignCamera(195.0, 422.0, 844.0, 85.0)
        assertNull(flat.groundOf(195.0, 0.0))
        assertNotNull(flat.groundOf(195.0, 800.0))
    }
}
