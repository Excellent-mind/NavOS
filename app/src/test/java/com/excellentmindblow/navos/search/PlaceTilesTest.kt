package com.excellentmindblow.navos.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTilesTest {
    private val here = 3.2290 to 101.7130 // Jalan Gombak

    private val places =
        PlaceTiles.parse(
            """{"v":1,"cell":"32_1017","places":[
            ["Kedai Basikal Cahaya","sporting_goods_store",3.2301,101.7101,"Jalan Gombak, Gombak"],
            ["Kok Ghee Motor Workshop","automotive_service",3.2205,101.7202,"Setapak"],
            ["Mydin","supermarket",3.1528,101.6974,"Jalan Masjid India, Kuala Lumpur"],
            ["Mydin","supermarket",3.2312,101.7240,"Gombak"],
            ["Domino's Pizza","restaurant",3.2001,101.7180,"Setapak"],
            ["Pasar Pagi Ahad Taman Greenwood","food_and_beverage_store",3.2290,101.7065,""]
            ]}""",
        )

    private fun search(q: String) = PlaceTiles.search(places, q, here.first, here.second)

    @Test
    fun findsSmallShopsByAnyStartOfTheirName() {
        assertEquals("Kedai Basikal Cahaya", search("kedai basikal").first().name)
        assertEquals("Kok Ghee Motor Workshop", search("kok ghee").first().name)
        assertEquals("Pasar Pagi Ahad Taman Greenwood", search("pasar pagi greenwood").first().name)
    }

    @Test
    fun punctuationAndCaseDoNotMatter() {
        assertEquals("Domino's Pizza", search("DOMINOS").first().name)
        assertEquals("Domino's Pizza", search("domino s pizza").first().name)
    }

    @Test
    fun nearerOfTwoSameNamesWins() {
        val r = search("mydin")
        assertEquals(2, r.size)
        assertEquals(3.2312, r[0].latitude, 1e-6)
    }

    @Test
    fun laterWordsMayMatchTheAddress() {
        val r = search("mydin gombak")
        assertEquals(1, r.size)
        assertEquals(3.2312, r[0].latitude, 1e-6)
    }

    @Test
    fun noMatchNoResult() {
        assertTrue(search("ikea").isEmpty())
        assertTrue(search("gombak").isEmpty()) // address alone is not a place name
    }

    @Test
    fun categoryReadsLikeWords() {
        assertEquals("Automotive service", search("kok").first().category)
    }

    @Test
    fun cells() {
        assertEquals("32_1017", PlaceTiles.cellOf(3.2290, 101.7130))
        assertEquals(9, PlaceTiles.cellsAround(3.2290, 101.7130).size)
        assertTrue("32_1017" in PlaceTiles.cellsAround(3.2290, 101.7130))
    }
}
