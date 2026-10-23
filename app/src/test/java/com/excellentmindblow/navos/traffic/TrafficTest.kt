package com.excellentmindblow.navos.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficTest {
    // A 2 km route of 100 m pieces, nodes 1..21.
    private val nodes = LongArray(21) { (it + 1).toLong() }
    private val cum = DoubleArray(21) { it * 100.0 }

    @Test
    fun reportsOneAverageSpeedPerPieceDriven() {
        val p = TrafficProbe(nodes, cum)
        // Through pieces 3..6 (300-700 m) at 10 m/s, a fix every 25 m.
        var d = 300.0
        while (d <= 700.0) {
            p.onFix(d, 10.0)
            d += 25.0
        }
        val out = p.drain()
        assertEquals(listOf("4-5", "5-6", "6-7", "7-8"), out.map { it.first })
        assertTrue(out.all { it.second == 36.0 })
    }

    @Test
    fun startAndEndOfTheTripAreNeverSent() {
        val p = TrafficProbe(nodes, cum)
        var d = 0.0
        while (d <= 2000.0) {
            p.onFix(d, 15.0)
            d += 20.0
        }
        val keys = p.drain(500).map { it.first }
        assertTrue("nothing in the first 300 m", keys.none { it in setOf("1-2", "2-3", "3-4") })
        assertTrue("nothing in the last 300 m", keys.none { it in setOf("18-19", "19-20", "20-21") })
        assertTrue("4-5" in keys && "17-18" in keys)
    }

    @Test
    fun piecesSkippedBetweenFixesStillGetASpeed() {
        val p = TrafficProbe(nodes, cum)
        p.onFix(450.0, 20.0)
        p.onFix(860.0, 20.0) // jumped over 500-800
        val keys = p.drain().map { it.first }
        assertEquals(listOf("5-6", "6-7", "7-8", "8-9"), keys)
    }

    @Test
    fun levels() {
        val ff = 25.0 // 90 km/h
        assertNull(TrafficFlow.levelOf(80.0, ff))
        assertEquals(TrafficFlow.Level.SLOW, TrafficFlow.levelOf(60.0, ff))
        assertEquals(TrafficFlow.Level.JAM, TrafficFlow.levelOf(20.0, ff))
        assertNull("car-park speeds aren't judged", TrafficFlow.levelOf(1.0, 2.0))
    }

    @Test
    fun neighbouringSlowPiecesMergeIntoOneRun() {
        val ff = DoubleArray(20) { 25.0 }
        val live = mapOf("3-4" to 60.0, "4-5" to 60.0, "5-6" to 15.0, "6-7" to 10.0, "9-10" to 85.0)
        val runs = TrafficFlow.runs(nodes, ff, live)
        assertEquals(
            listOf(
                TrafficFlow.Run(2, 4, TrafficFlow.Level.SLOW),
                TrafficFlow.Run(4, 6, TrafficFlow.Level.JAM),
            ),
            runs,
        )
        assertEquals(listOf("19-20", "20-21"), TrafficFlow.piecesAhead(nodes, 18))
    }
}
