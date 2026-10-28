package com.excellentmindblow.navos.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TollFaresTest {
    private val bundled = File("src/main/assets/tolls/llm_rates.json").readText()

    @Test
    fun bundledTableIsAcceptedAndCarriesItsDate() {
        val got = TollFares.accept(bundled, "")
        assertNotNull(got)
        assertEquals("2026-10-23", got!!.second)
    }

    @Test
    fun sameOrNewerDateIsAccepted() {
        assertNotNull(TollFares.accept(bundled, "2026-10-23"))
        assertNotNull(TollFares.accept(bundled, "2026-01-01"))
    }

    @Test
    fun olderTableIsRejected() {
        assertNull(TollFares.accept(bundled, "2027-01-01"))
    }

    @Test
    fun brokenOrEmptyDownloadsAreRejected() {
        assertNull(TollFares.accept("", ""))
        assertNull(TollFares.accept("<html>502 Bad Gateway</html>", ""))
        assertNull(TollFares.accept("""{"fetched":"2027-01-01","highways":[]}""", ""))
        assertNull(TollFares.accept("null", ""))
    }

    @Test
    fun partialTableIsRejected() {
        val tiny = """{"fetched":"2027-01-01","highways":[{"id":"1","name":"X","plazas":[["1","A"],["2","B"]],"pairs":{"1>2":[100]}}]}"""
        assertNull(TollFares.accept(tiny, ""))
    }
}
