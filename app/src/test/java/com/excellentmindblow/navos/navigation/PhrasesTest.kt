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
        assertEquals("Buat U-turn ke Jalan Tun Razak", Phrases.instruction("continue", "uturn", "Jalan Tun Razak", 0, ms = true))
        assertEquals("Ikut kanan ke E2", Phrases.instruction("fork", "slight right", "E2", 0, ms = true))
        assertEquals("Keluar ke Jalan Duta", Phrases.instruction("off ramp", "slight left", "Jalan Duta", 0, ms = true))
        assertEquals("Masuk bulatan, keluar yang ketiga ke Persiaran Perdana", Phrases.instruction("roundabout", null, "Persiaran Perdana", 3, ms = true))
        assertEquals("Gerak ke Jalan Ampang", Phrases.instruction("depart", null, "Jalan Ampang", 0, ms = true))
        assertEquals("Sampai destinasi", Phrases.instruction("arrive", null, "", 0, ms = true))
    }

    @Test
    fun malaySpoken() {
        assertEquals("Lagi 500 meter, belok kiri ke Jalan Ampang", Phrases.inDistance(498.0, "Belok kiri ke Jalan Ampang", ms = true))
        assertEquals("Dah sampai hentian 2. Sekarang gerak ke hentian seterusnya.", Phrases.stopReached(Phrases.stopName(2, ms = true), true, ms = true))
        assertEquals("Dah sampai destinasi", Phrases.arrived(ms = true))
    }
}
