package com.excellentmindblow.navos.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTest {
    private val body =
        """
        {"elements":[
          {"type":"way","id":1,"center":{"lat":3.1600,"lon":101.7120},
           "tags":{"amenity":"parking","name":"Suria KLCC Parking","parking":"underground","fee":"yes","capacity":"5400","opening_hours":"24/7"}},
          {"type":"node","id":2,"lat":3.1580,"lon":101.7117,"tags":{"amenity":"parking","fee":"no"}},
          {"type":"way","id":3,"center":{"lat":3.1581,"lon":101.7118},"tags":{"amenity":"parking","parking":"street_side"}},
          {"type":"node","id":4,"lat":3.1590,"lon":101.7110,"tags":{"amenity":"fuel"}}
        ]}
        """.trimIndent()

    @Test
    fun publicCarParksNearestFirst() {
        val out = PoiService.parseParking(body, 3.1579, 101.7116)
        assertEquals(listOf("node/2", "way/1"), out.map { it.id }) // street-side bays and the petrol station left out
        assertTrue(out.all { it.kind == PoiKind.PARKING })
        assertEquals("Parking", out[0].name)
    }

    @Test
    fun whatTheCarParkHas() {
        val klcc = PoiService.parseParking(body, 3.1579, 101.7116).first { it.id == "way/1" }
        val listed = klcc.facilities.filter { it.listed }.map { it.label }
        assertEquals(listOf("Paid", "Covered", "24 hours"), listed)
        assertEquals("Spaces: 5400", klcc.spec)
        val free = PoiService.parseParking(body, 3.1579, 101.7116).first { it.id == "node/2" }
        assertEquals(listOf("Free"), free.facilities.filter { it.listed }.map { it.label })
    }
}
