package com.excellentmindblow.navos.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private val lat = 3.1488
    private val lon = 101.7136

    private fun house(
        number: String,
        street: String,
        lat: Double = 3.13,
        lon: Double = 101.67,
    ) = GeocodeResult("$number $street, Bangsar, Kuala Lumpur", lat, lon, "$number $street", "Bangsar, Kuala Lumpur", houseNumber = number, isAddress = true)

    @Test
    fun abbreviationsAndHouseNumbersAreUnderstood() {
        val q = GeocodingService.parseQuery("No 5, Jln SS 2/24, Petaling Jaya")
        assertEquals("5", q.houseNumber)
        assertEquals("jalan ss 2/24 petaling jaya", q.withoutNumber.lowercase())
    }

    @Test
    fun theExactHouseNeedsTheSameStreetNotJustTheSameNumber() {
        val q = GeocodingService.parseQuery("3 Jalan Telawi 3 Bangsar")
        assertTrue(GeocodingService.isExactHouse(q, house("3", "Jalan Telawi 3")))
        assertFalse(GeocodingService.isExactHouse(q, house("3", "Jalan Telawi 2")))
        assertFalse(GeocodingService.isExactHouse(GeocodingService.parseQuery("3 Jalan Telawi 33"), house("3", "Jalan Telawi 3")))
    }

    @Test
    fun theRightStreetBeatsANeighbouringStreetWithTheSameNumber() {
        val q = GeocodingService.parseQuery("3 Jalan Telawi 3 Bangsar")
        val wrong = house("3", "Jalan Telawi 2", lon = 101.6705)
        val right = house("3", "Jalan Telawi 3", lon = 101.6710)
        val ranked = GeocodingService.rank(q, listOf(wrong, right), lat, lon)
        assertEquals(right, ranked.first())
    }

    @Test
    fun aBuildingBeatsTheBusStopAndCarParkNamedAfterIt() {
        val q = GeocodingService.parseQuery("Menara Maybank")
        val stop = GeocodeResult("Menara Maybank, Jalan Tun Perak", 3.1478, 101.6990, "Menara Maybank", "Jalan Tun Perak", category = "Bus stop")
        val park = GeocodeResult("Menara Maybank Car Park, Jalan Pudu Lama", 3.1470, 101.6995, "Menara Maybank Car Park", "Jalan Pudu Lama", category = "Parking")
        val tower = GeocodeResult("Menara Maybank, Jalan Tun Perak", 3.1475, 101.6985, "Menara Maybank", "Jalan Tun Perak", category = "Building")
        assertEquals(tower, GeocodingService.rank(q, listOf(stop, park, tower), lat, lon).first())
        // ...unless the driver asked for the stop.
        assertEquals(stop, GeocodingService.rank(GeocodingService.parseQuery("Menara Maybank bus stop"), listOf(tower, stop), lat, lon).first())
    }

    @Test
    fun aFarAwayNameMatchRanksBelowANearbyOne() {
        val q = GeocodingService.parseQuery("mid valley")
        val bintulu = GeocodeResult("Mid Valley Seafood, Bintulu, Sarawak", 3.17, 113.03, "Mid Valley Seafood", "Bintulu, Sarawak")
        val kl = GeocodeResult("Mid Valley Megamall, Kuala Lumpur", 3.118, 101.677, "Mid Valley Megamall", "Kuala Lumpur")
        assertEquals(kl, GeocodingService.rank(q, listOf(bintulu, kl), lat, lon).first())
    }

    @Test
    fun anUnknownHouseOnTheRightStreetComesFirstAsApproximate() {
        val q = GeocodingService.parseQuery("5 Jalan SS 2/24 Petaling Jaya")
        val road = GeocodeResult("Jalan SS 2/24, Petaling Jaya", 3.118, 101.62, "Jalan SS 2/24", "Petaling Jaya", category = "Road", isAddress = true)
        val other = house("24", "Jalan SS 2/5")
        val out = GeocodingService.withApproximate(q, listOf(other, road))
        assertTrue(out.first().approximate)
        assertEquals("5 Jalan SS 2/24", out.first().name)
        assertEquals(road.latitude, out.first().latitude, 0.0)
    }
}
