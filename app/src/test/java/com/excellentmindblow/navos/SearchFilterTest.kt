package com.excellentmindblow.navos

import com.excellentmindblow.navos.SearchScreen.Filter
import com.excellentmindblow.navos.search.GeocodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFilterTest {
    private val mall = GeocodeResult("Mid Valley Megamall", 3.118, 101.677, category = "Shopping mall")
    private val road = GeocodeResult("Jalan Mid Valley", 3.117, 101.676, category = "Road", isAddress = true)
    private val house = GeocodeResult("12 Jalan Bangsar", 3.13, 101.67, houseNumber = "12", isAddress = true)
    private val results = listOf(mall, road, house)

    private val shell = GeocodeResult("Shell Jalan Bangsar", 3.128, 101.678, category = "Petrol")
    private val petronas = GeocodeResult("Petronas Mid Valley", 3.116, 101.68, category = "Petrol")
    private val petrol = listOf(shell, petronas)

    @Test
    fun allKeepsEverything() {
        assertEquals(results, SearchScreen.apply(Filter.ALL, results, "mid", petrol))
    }

    @Test
    fun placesAndAddressesSplitTheResults() {
        assertEquals(listOf(mall), SearchScreen.apply(Filter.PLACES, results, "mid", petrol))
        assertEquals(listOf(road, house), SearchScreen.apply(Filter.ADDRESSES, results, "mid", petrol))
    }

    @Test
    fun petrolShowsNearbyStationsMatchingTheQuery() {
        assertEquals(petrol, SearchScreen.apply(Filter.PETROL, results, "", petrol))
        assertEquals(listOf(petronas), SearchScreen.apply(Filter.PETROL, results, "mid valley", petrol))
    }

    @Test
    fun petrolWithANonMatchingQueryStillShowsTheNearbyStations() {
        assertEquals(petrol, SearchScreen.apply(Filter.PETROL, results, "klcc", petrol))
    }
}
