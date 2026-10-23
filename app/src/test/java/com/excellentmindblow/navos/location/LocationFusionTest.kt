package com.excellentmindblow.navos.location

import com.excellentmindblow.navos.location.LocationFusion.Fix
import com.excellentmindblow.navos.location.LocationFusion.Source
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFusionTest {
    private val start = RoutePoint(3.2290, 101.7130)

    /** Point [m] metres north of the start. */
    private fun north(m: Double) = GeoUtils.offset(start, 0.0, m)

    private fun fix(
        p: RoutePoint,
        t: Long,
        acc: Double = 5.0,
        src: Source = Source.GPS,
        speed: Double? = null,
        bearing: Double? = null,
    ) = Fix(p.latitude, p.longitude, acc, speed, bearing, t, src)

    private fun dist(
        f: Fix,
        p: RoutePoint,
    ) = GeoUtils.distanceMeters(RoutePoint(f.latitude, f.longitude), p)

    @Test
    fun smoothsNoiseWhileDriving() {
        val k = LocationFusion()
        // 15 m/s north for 30 s; every fix is off by +/-12 m sideways.
        var worstRaw = 0.0
        var worstOut = 0.0
        for (i in 0..30) {
            val truth = north(15.0 * i)
            val noisy = GeoUtils.offset(truth, 90.0, if (i % 2 == 0) 12.0 else -12.0)
            val out = k.accept(fix(noisy, i * 1000L, acc = 12.0, speed = 15.0, bearing = 0.0))!!
            if (i >= 5) {
                worstRaw = maxOf(worstRaw, 12.0)
                worstOut = maxOf(worstOut, dist(out, truth))
            }
        }
        assertTrue("smoothed error $worstOut should beat the raw 12 m", worstOut < 7.0)
    }

    @Test
    fun dropsAnImpossibleJump() {
        val k = LocationFusion()
        for (i in 0..5) k.accept(fix(north(15.0 * i), i * 1000L, speed = 15.0, bearing = 0.0))
        // One fix 2 km away, one second later.
        assertNull(k.accept(fix(north(2000.0), 6000L)))
        val next = k.accept(fix(north(105.0), 7000L, speed = 15.0, bearing = 0.0))!!
        assertTrue(dist(next, north(105.0)) < 10.0)
    }

    @Test
    fun followsWhenTheJumpsKeepComing() {
        val k = LocationFusion()
        k.accept(fix(north(0.0), 0L, acc = 50.0, src = Source.NETWORK)) // a bad start from the network
        var last: Fix? = null
        for (i in 1..6) last = k.accept(fix(north(3000.0), i * 1000L)) // the real place, 3 km off
        assertNotNull(last)
        assertTrue(dist(last!!, north(3000.0)) < 10.0)
    }

    @Test
    fun networkWaitsWhileGpsIsGood() {
        val k = LocationFusion()
        k.accept(fix(north(0.0), 0L, acc = 5.0))
        assertNull(k.accept(fix(north(50.0), 1000L, acc = 150.0, src = Source.NETWORK)))
        // No good GPS for a while (indoors): the network is used.
        assertNotNull(k.accept(fix(north(5.0), 8000L, acc = 60.0, src = Source.NETWORK)))
    }

    @Test
    fun aLateCopyFromAnotherProviderIsIgnored() {
        val k = LocationFusion()
        k.accept(fix(north(0.0), 2000L, src = Source.FUSED))
        assertNull(k.accept(fix(north(0.0), 1500L, src = Source.GPS)))
    }

    @Test
    fun headingFromMovementWhenGpsGivesNone() {
        val k = LocationFusion()
        var out: Fix? = null
        for (i in 0..10) out = k.accept(fix(GeoUtils.offset(start, 90.0, 12.0 * i), i * 1000L, src = Source.FUSED))
        assertEquals(90.0, out!!.bearingDeg!!, 10.0)
        assertEquals(12.0, out.speedMs!!, 2.0)
    }
}
