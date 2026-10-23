package com.excellentmindblow.navos.routing

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TollPricerTest {
    // PLUS (closed: pay at the exit by entry/exit pair) and an open city highway.
    private val pricer =
        TollPricer.fromJson(
            JSONObject(
                """
                {"highways":[
                  {"id":"1","name":"PLUS","plazas":[["10","RAWANG"],["20","JALAN DUTA"],["30","IPOH SELATAN"]],
                   "pairs":{"10>20":[290],"30>10":[1652],"10>30":[1652]}},
                  {"id":"13","name":"AKLEH","plazas":[["100","KERAMAT"]],"open":{"100":[150]}}
                ]}
                """.trimIndent(),
            ),
        )

    @Test
    fun closedHighwayIsPricedByEntryAndExit() {
        val e = pricer.price(listOf("Plaza Tol Ipoh Selatan", "Plaza Tol Rawang (S)"))
        assertEquals(1652, e.totalSen)
        assertEquals(0, e.unpriced)
    }

    @Test
    fun openPlazaIsAFlatFareAndTheyAddUp() {
        val e = pricer.price(listOf("Plaza Tol Rawang", "Plaza Tol Jalan Duta", "Plaza Tol Keramat"))
        assertEquals(290 + 150, e.totalSen)
        assertEquals(0, e.unpriced)
    }

    @Test
    fun unknownPlazaOrMissingExitIsCounted() {
        val e = pricer.price(listOf("Plaza Tol Entah", "Plaza Tol Rawang"))
        assertEquals(0, e.totalSen)
        assertEquals(2, e.unpriced) // unknown name + an entry never left
    }

    @Test
    fun ringgitFormat() {
        assertEquals("RM 16.52", TollPricer.format(1652))
        assertEquals("RM 0.50", TollPricer.format(50))
    }

    @Test
    fun mapSpellingsMatchLlmNames() {
        assertEquals(TollPricer.normalize("SUNGAI LONG"), TollPricer.normalize("Plaza Tol Sg Long (Arah Barat)"))
        assertEquals(TollPricer.normalize("AYER KEROH"), TollPricer.normalize("Plaza Toll PLUS Air Keroh"))
        assertEquals("GOMBAK", TollPricer.normalize("Plaza Tol Gombak (Arah Timur)"))
    }
}
