package com.excellentmindblow.navos.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitTest {
    // Pasar Seni (MRT + LRT, both directions of each line), a second mapping of
    // the same station, a stop and a Park & Ride car park; Masjid Jamek further away.
    private val body =
        """
        {"elements":[
          {"type":"node","id":1,"lat":3.1425,"lon":101.6953,"tags":{"railway":"station","name":"Pasar Seni"}},
          {"type":"way","id":2,"center":{"lat":3.1427,"lon":101.6955},"tags":{"public_transport":"station","name":"Pasar Seni"}},
          {"type":"node","id":3,"lat":3.1495,"lon":101.6964,"tags":{"railway":"station","name":"Masjid Jamek"}},
          {"type":"node","id":10,"lat":3.1426,"lon":101.6954},
          {"type":"node","id":11,"lat":3.1424,"lon":101.6952},
          {"type":"node","id":12,"lat":3.1496,"lon":101.6965},
          {"type":"relation","id":100,"members":[{"type":"node","ref":10,"role":"stop"},{"type":"node","ref":12,"role":"stop"}],
           "tags":{"route":"light_rail","ref":"KJ","name":"LRT Kelana Jaya Line: Gombak → Putra Heights","colour":"#E0115F"}},
          {"type":"relation","id":101,"members":[{"type":"node","ref":12,"role":"stop"},{"type":"node","ref":10,"role":"stop"}],
           "tags":{"route":"light_rail","ref":"KJ","name":"LRT Kelana Jaya Line: Putra Heights → Gombak","colour":"#E0115F"}},
          {"type":"relation","id":102,"members":[{"type":"node","ref":11,"role":"stop"}],
           "tags":{"route":"subway","ref":"KG","name":"MRT Kajang Line","colour":"#047940"}},
          {"type":"relation","id":103,"members":[{"type":"node","ref":11}],"tags":{"route":"bus","ref":"T100"}},
          {"type":"way","id":200,"center":{"lat":3.1430,"lon":101.6960},"tags":{"amenity":"parking","park_and_ride":"yes","capacity":"450"}}
        ]}
        """.trimIndent()

    @Test
    fun stationsWithTheirLinesNearestFirst() {
        val out = PoiService.parseTransit(body, 3.1420, 101.6950)
        assertEquals(listOf("Pasar Seni", "Masjid Jamek"), out.map { it.name }) // the double mapping is one station
        val ps = out[0]
        assertEquals(listOf("KJ", "KG"), ps.lines.map { it.badge }) // both directions = one badge, bus left out
        assertEquals(0xFFE0115F.toInt(), ps.lines[0].colour)
        assertEquals(listOf("KJ"), out[1].lines.map { it.badge })
    }

    @Test
    fun parkAndRideNextToTheStation() {
        val out = PoiService.parseTransit(body, 3.1420, 101.6950)
        val park = out[0].parkAndRide
        assertNotNull(park)
        assertEquals(450, park!!.spaces)
        assertNull(out[1].parkAndRide) // 800 m away: not this station's
    }

    @Test
    fun hexColours() {
        assertEquals(0xFF112233.toInt(), PoiService.hexColour("#112233"))
        assertEquals(0xFFEE1155.toInt(), PoiService.hexColour("e15"))
        assertNull(PoiService.hexColour("red"))
        assertTrue(TransitLine(null, "MRT Putrajaya Line: Kwasa Damansara → Putrajaya Sentral", null).badge == "MRT Putrajaya Line")
    }
}
