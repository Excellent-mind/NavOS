package com.excellentmindblow.navos.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChevronPulseTest {
    private val linear: (Float) -> Float = { it }

    @Test
    fun peaksAfterTheRiseThenFades() {
        assertEquals(0f, ChevronPulse.intensity(0, 0, linear), 1e-6f)
        assertEquals(1f, ChevronPulse.intensity(ChevronPulse.RISE_MS, 0, linear), 1e-6f)
        assertEquals(0.5f, ChevronPulse.intensity(ChevronPulse.RISE_MS + ChevronPulse.FALL_MS / 2, 0, linear), 1e-3f)
        assertEquals(0f, ChevronPulse.intensity(ChevronPulse.RISE_MS + ChevronPulse.FALL_MS + 10, 0, linear), 1e-6f)
    }

    @Test
    fun laterChevronsFollowByTheStagger() {
        val t = ChevronPulse.RISE_MS + 3 * ChevronPulse.STAGGER_MS
        assertEquals(1f, ChevronPulse.intensity(t, 3, linear), 1e-6f)
    }

    @Test
    fun loops() {
        val t = 123L
        assertEquals(ChevronPulse.intensity(t, 2, linear), ChevronPulse.intensity(t + ChevronPulse.PERIOD_MS * 5, 2, linear), 1e-6f)
    }
}
