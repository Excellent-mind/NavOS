package com.excellentmindblow.navos.signs

import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnSignTest {
    private fun step(
        name: String,
        ref: String? = null,
        dest: String? = null,
        exits: String? = null,
    ) = RouteStep("Take the exit onto $name", 100.0, 10.0, "off ramp", "slight left", name, RoutePoint(3.0, 101.0), ref = ref, destinations = dest, exits = exits)

    @Test
    fun exitNumberMovesOutOfTheName() {
        val s = TurnSign.of(step("Exit 607 Putrajaya"), "E6", "x")
        assertEquals("607", s.exit)
        assertEquals("Putrajaya", s.text)
        assertTrue(s.expressway)
    }

    @Test
    fun destinationsBeatTheName() {
        val s = TurnSign.of(step("Jalan Duta", dest = "E33: Kuantan, Genting Highlands, Gombak", exits = "3305A"), "E1", "x")
        assertEquals("3305A", s.exit)
        assertEquals(listOf("E33"), s.refs)
        assertEquals("Kuantan · Genting Highlands", s.text)
        // Banner: the road big, where it goes small.
        assertEquals("Jalan Duta", s.primary)
        assertEquals("Kuantan · Genting Highlands", s.towards)
    }

    @Test
    fun townTurnIsBlueWithTheStreetName() {
        val s = TurnSign.of(step("Jalan SS 15/4"), "", "x")
        assertNull(s.exit)
        assertEquals("Jalan SS 15/4", s.text)
        assertFalse(s.expressway)
    }

    @Test
    fun noNameFallsBackToTheInstruction() {
        assertEquals("Keep left", TurnSign.of(step(""), null, "Keep left").text)
    }
}
