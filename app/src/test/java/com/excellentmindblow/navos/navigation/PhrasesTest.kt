package com.excellentmindblow.navos.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasesTest {
    @Test
    fun englishStaysAsBefore() {
        assertEquals("Turn left onto Jalan Ampang", Phrases.instruction("turn", "left", "Jalan Ampang", 0, ms = false))
        assertEquals("At the roundabout, take exit 2 onto the road", Phrases.instruction("roundabout", "right", "", 2, ms = false))
        assertEquals("Keep left at the fork onto E8", Phrases.instruction("fork", "slight left", "E8", 0, ms = false))
        assertEquals("In 300 meters, turn left onto Jalan Ampang", Phrases.inDistance(304.0, "Turn left onto Jalan Ampang", ms = false))
    }

    @Test
    fun malayTurns() {
        assertEquals("Belok kiri ke Jalan Ampang", Phrases.instruction("turn", "left", "Jalan Ampang", 0, ms = true))
        assertEquals("Belok kanan", Phrases.instruction("turn", "right", "", 0, ms = true))
        assertEquals("Buat pusingan U ke Jalan Tun Razak", Phrases.instruction("continue", "uturn", "Jalan Tun Razak", 0, ms = true))
        assertEquals("Ikut kanan di simpang ke E2", Phrases.instruction("fork", "slight right", "E2", 0, ms = true))
        assertEquals("Ambil susur keluar ke Jalan Duta", Phrases.instruction("off ramp", "slight left", "Jalan Duta", 0, ms = true))
        assertEquals("Di bulatan, ambil jalan keluar ketiga ke Persiaran Perdana", Phrases.instruction("roundabout", null, "Persiaran Perdana", 3, ms = true))
        assertEquals("Tiba di destinasi anda", Phrases.instruction("arrive", null, "", 0, ms = true))
    }

    @Test
    fun malaySpoken() {
        assertEquals("Dalam 500 meter, belok kiri ke Jalan Ampang", Phrases.inDistance(498.0, "Belok kiri ke Jalan Ampang", ms = true))
        assertEquals("Anda telah tiba di hentian 2. Teruskan ke hentian seterusnya.", Phrases.stopReached(Phrases.stopName(2, ms = true), true, ms = true))
    }
}
