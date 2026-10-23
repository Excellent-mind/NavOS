package com.excellentmindblow.navos.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepartureAdvisorTest {
    @Test
    fun rushHourNowLeaveLater() {
        // 40 min trip, +25 min jam now, +5 min in two hours.
        val a = DepartureAdvisor.advise(2400.0, mapOf(0 to 1500.0, 1 to 900.0, 2 to 300.0, 3 to 320.0))!!
        assertEquals(2, a.offsetHours)
        assertEquals(1200.0, a.savedSeconds, 0.1)
    }

    @Test
    fun smallGainsOrNoDataSayNothing() {
        assertNull(DepartureAdvisor.advise(2400.0, mapOf(0 to 300.0, 1 to 0.0))) // 5 min
        assertNull(DepartureAdvisor.advise(2400.0, mapOf(1 to 0.0)))
    }

    @Test
    fun bucketsAreMalaysiaTime() {
        // 2026-10-23 (Friday) 00:30 UTC = 08:30 in Kuala Lumpur, a weekday.
        assertEquals(8, DepartureAdvisor.bucketAt(1792715400000L))
        // 2026-10-24 (Saturday) 02:00 UTC = 10:00 KL, weekend.
        assertEquals(34, DepartureAdvisor.bucketAt(1792807200000L))
    }
}
