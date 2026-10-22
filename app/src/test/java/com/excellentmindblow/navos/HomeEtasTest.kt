package com.excellentmindblow.navos

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeEtasTest {
    private val klcc = RoutePoint(3.1579, 101.7116)
    private val home = RoutePoint(3.0800, 101.7400) // Cheras
    private val work = RoutePoint(3.1100, 101.6650) // Bangsar South

    private var now = 50_000L
    private var fails = false
    private val fetches = ArrayList<RoutePoint>()
    private val background = ArrayList<() -> Unit>()
    private val main = ArrayList<() -> Unit>()
    private var changes = 0

    private val etas =
        HomeEtas(
            runInBackground = { background += it },
            postToMain = { main += it },
            clock = { now },
            fetchSeconds = { _, to ->
                fetches += to
                if (fails) throw RuntimeException("offline")
                if (to == home) 720.0 else 1680.0
            },
        ).also { it.addListener { changes++ } }

    private fun finish() {
        background.toList().forEach { it() }
        background.clear()
        main.toList().forEach { it() }
        main.clear()
    }

    private fun north(meters: Double) = RoutePoint(klcc.latitude + meters / 111_320.0, klcc.longitude)

    @Test
    fun timesArriveForEveryPlace() {
        etas.refresh(klcc, mapOf("home" to home, "work" to work))
        assertNull(etas.secondsTo("home", home))
        finish()
        assertEquals(720.0, etas.secondsTo("home", home)!!, 0.0)
        assertEquals(1680.0, etas.secondsTo("work", work)!!, 0.0)
        assertEquals(2, changes)
    }

    @Test
    fun aKnownTimeIsNotFetchedAgainNearby() {
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        now += 60_000
        etas.refresh(north(900.0), mapOf("home" to home))
        finish()
        assertEquals(1, fetches.size)
    }

    @Test
    fun movingAKilometreOrWaitingTenMinutesRefreshes() {
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        etas.refresh(north(1100.0), mapOf("home" to home))
        finish()
        assertEquals(2, fetches.size)

        now += HomeEtas.MAX_AGE_MS
        etas.refresh(north(1100.0), mapOf("home" to home))
        finish()
        assertEquals(3, fetches.size)
    }

    @Test
    fun aMovedPlaceIsFetchedAgainAndTheOldTimeIsNotShown() {
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        val newHome = RoutePoint(3.0500, 101.7000)
        assertNull(etas.secondsTo("home", newHome))
        etas.refresh(klcc, mapOf("home" to newHome))
        finish()
        assertEquals(2, fetches.size)
    }

    @Test
    fun onlyOneFetchPerPlaceAtATime() {
        etas.refresh(klcc, mapOf("home" to home))
        etas.refresh(klcc, mapOf("home" to home))
        assertEquals(1, background.size)
    }

    @Test
    fun aFailureWaitsAMinuteBeforeTryingAgain() {
        fails = true
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        now += 30_000
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        assertEquals(1, fetches.size)
        assertEquals(0, changes)

        fails = false
        now += 30_000
        etas.refresh(klcc, mapOf("home" to home))
        finish()
        assertEquals(2, fetches.size)
        assertEquals(720.0, etas.secondsTo("home", home)!!, 0.0)
    }
}
