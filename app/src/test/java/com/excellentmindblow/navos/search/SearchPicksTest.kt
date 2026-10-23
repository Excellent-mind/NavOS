package com.excellentmindblow.navos.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchPicksTest {
    private val day = 86_400_000L
    private val now = 400 * day
    private val pyramid = GeocodeResult("Sunway Pyramid, Bandar Sunway", 3.0730, 101.6070)
    private val sunwayLagoon = GeocodeResult("Sunway Lagoon, Bandar Sunway", 3.0690, 101.6070)
    private val sunMed = GeocodeResult("Sunway Medical Centre, Bandar Sunway", 3.0660, 101.6090)
    private val results = listOf(sunwayLagoon, sunMed, pyramid)

    private fun pick(p: GeocodeResult, n: Int, ago: Long) = SearchPicks.Pick(SearchPicks.keyOf(p), n, now - ago)

    @Test
    fun aPlaceYouPickedComesFirst() {
        val out = SearchPicks.boost(results, "sunway", listOf(pick(pyramid, 3, day)), now)
        assertEquals(listOf(pyramid, sunwayLagoon, sunMed), out)
    }

    @Test
    fun onlyWhenItMatchesWhatWasTyped() {
        val out = SearchPicks.boost(results, "sunway lag", listOf(pick(pyramid, 3, day)), now)
        assertSame(results, out)
    }

    @Test
    fun recentAndFrequentWins() {
        val picks = listOf(pick(pyramid, 4, 300 * day), pick(sunMed, 2, day))
        val out = SearchPicks.boost(results, "sun", picks, now)
        assertEquals(listOf(sunMed, pyramid, sunwayLagoon), out)
    }

    @Test
    fun samePlaceEvenIfCoordsWobbleALittle() {
        val moved = pyramid.copy(latitude = 3.07304, longitude = 101.60698)
        assertEquals(SearchPicks.keyOf(pyramid), SearchPicks.keyOf(moved))
    }

    @Test
    fun emptyQueryLeavesOrder() {
        assertSame(results, SearchPicks.boost(results, "  ", listOf(pick(pyramid, 3, day)), now))
    }
}
