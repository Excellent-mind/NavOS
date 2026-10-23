package com.excellentmindblow.navos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartEtaTest {
    /** Drives for [minutes], one update a second, the route time falling at [rate] predicted seconds per real second. */
    private fun drive(
        eta: SmartEta,
        startRaw: Double,
        minutes: Int,
        rate: Double,
        startMs: Long = 0L,
    ): Double {
        var raw = startRaw
        for (s in 0..minutes * 60) {
            eta.observe(raw, startMs + s * 1000L)
            raw -= rate
        }
        return raw
    }

    @Test
    fun onTimeDriveKeepsTheServerTime() {
        val eta = SmartEta()
        val raw = drive(eta, 3600.0, 10, 1.0)
        assertEquals(raw, eta.adjust(raw), 30.0)
    }

    @Test
    fun slowTrafficStretchesTheRestOfTheTrip() {
        val eta = SmartEta()
        // 15 min of driving covered only 10 min of predicted route (pace 1.5).
        val raw = drive(eta, 3600.0, 15, 2.0 / 3.0)
        assertEquals(1.5, eta.pace, 0.05)
        val adjusted = eta.adjust(raw)
        assertTrue("adjusted $adjusted vs raw $raw", adjusted > raw * 1.3 && adjusted < raw * 1.5)
    }

    @Test
    fun aRerouteJumpIsNotTakenAsPace() {
        val eta = SmartEta()
        drive(eta, 3600.0, 5, 1.0)
        eta.rebase()
        drive(eta, 1200.0, 5, 1.0, startMs = 400_000L) // new, shorter route
        assertEquals(1.0, eta.pace, 0.05)
    }

    @Test
    fun nothingSeenYetChangesNothing() {
        assertEquals(1000.0, SmartEta().adjust(1000.0), 0.001)
    }
}
