package com.excellentmindblow.navos.location

import com.excellentmindblow.navos.location.PowerPolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerPolicyTest {
    private fun still(
        p: PowerPolicy,
        seconds: Int,
        from: Long = 0L,
    ): Mode {
        var m = p.mode
        for (s in 0..seconds) m = p.onFix(3.15, 101.70, 0.0, from + s * 1000L)
        return m
    }

    @Test
    fun aParkedPhoneOnTheIdleMapGoesQuiet() {
        val p = PowerPolicy(navigating = false)
        assertEquals(Mode.FULL, still(p, 30))
        assertEquals(Mode.PARKED, still(p, 70))
    }

    @Test
    fun aDriveStaysFullUntilALongStop() {
        val p = PowerPolicy(navigating = true)
        assertEquals(Mode.FULL, still(p, 90))
        assertEquals(Mode.CALM, still(p, 130))
    }

    @Test
    fun movingAgainIsFullAtOnce() {
        val p = PowerPolicy(navigating = false)
        still(p, 70)
        assertEquals(Mode.FULL, p.onFix(3.15, 101.70, 8.0, 80_000L))
        // Or a walk of more than 25 m, even with no speed reported.
        still(p, 70, from = 100_000L)
        assertEquals(Mode.FULL, p.onFix(3.1504, 101.70, null, 180_000L))
    }
}
