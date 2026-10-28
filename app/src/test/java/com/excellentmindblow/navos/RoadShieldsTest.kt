package com.excellentmindblow.navos

import org.junit.Assert.assertEquals
import org.junit.Test

class RoadShieldsTest {
    private fun refs(t: String) = RoadShields.refRanges(t).map { t.substring(it) }

    @Test
    fun findsExpresswayAndFederalCodes() {
        assertEquals(listOf("E12"), refs("via E12 AKLEH"))
        assertEquals(listOf("E1", "FT2"), refs("via E1 PLUS, FT2"))
        assertEquals(listOf("FT 50"), refs("ikut FT 50 Jalan Kluang"))
    }

    @Test
    fun findsBareNumberAfterVia() {
        assertEquals(listOf("2"), refs("via 2 Lebuhraya Persekutuan"))
        assertEquals(listOf("2"), refs("+3 min · via 2 Lebuhraya Persekutuan"))
    }

    @Test
    fun leavesOtherNumbersAlone() {
        assertEquals(emptyList<String>(), refs("via Jalan SS 2/24"))
        assertEquals(emptyList<String>(), refs("+5 min · No tolls"))
        assertEquals(emptyList<String>(), refs("via Jalan Ehsan"))
    }
}
