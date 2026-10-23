package com.excellentmindblow.navos.signs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignVisibilityTest {
    private fun board(at: Double) = RoadSign("b$at", at, SignContent.Toll("Gombak"))

    private fun light(at: Double) = RoadSign("l$at", at, SignContent.TrafficLight)

    @Test
    fun onlyTheNextBoardShows() {
        val shown = SignVisibility.pick(listOf(board(1000.0), board(1150.0), board(1600.0)), here = 500.0)
        assertEquals(listOf("b1000.0"), shown.map { it.first.id })
    }

    @Test
    fun theFollowingBoardWaitsUntilThisOneIsPassed() {
        val signs = listOf(board(1000.0), board(1150.0))
        // Arrow 60 m before the first board: it is hidden as too close,
        // and the second takes over.
        assertEquals(listOf("b1150.0"), SignVisibility.pick(signs, here = 940.0).map { it.first.id })
    }

    @Test
    fun boardFadesOutBeforeTheArrowReachesIt() {
        val s = listOf(board(1000.0))
        assertEquals(1f, SignVisibility.pick(s, here = 700.0).single().third, 1e-3f)
        val mid = SignVisibility.pick(s, here = 1000.0 - 115.0).single().third
        assertTrue(mid in 0.3f..0.7f)
        assertTrue(SignVisibility.pick(s, here = 1000.0 - 80.0).isEmpty())
    }

    @Test
    fun farBoardFadesIn() {
        val a = SignVisibility.pick(listOf(board(1000.0)), here = 1000.0 - 850.0).single().third
        assertTrue(a in 0.2f..0.3f)
        assertTrue(SignVisibility.pick(listOf(board(1000.0)), here = 0.0).isEmpty())
    }

    @Test
    fun lightsShowAlongsideTheBoard() {
        val shown = SignVisibility.pick(listOf(board(800.0), light(300.0), light(450.0), light(900.0)), here = 200.0)
        assertEquals(setOf("b800.0", "l300.0", "l450.0"), shown.map { it.first.id }.toSet())
        assertEquals("b800.0", shown.first().first.id) // far ones drawn first
    }
}
