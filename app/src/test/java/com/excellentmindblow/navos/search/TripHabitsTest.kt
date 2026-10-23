package com.excellentmindblow.navos.search

import com.excellentmindblow.navos.routing.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripHabitsTest {
    private val office = GeocodeResult("Menara Office, KL", 3.158, 101.712)
    private val gym = GeocodeResult("Gym, PJ", 3.110, 101.640)
    private val home = RoutePoint(3.050, 101.600)

    private fun t(
        place: GeocodeResult,
        minute: Int,
        day: Long,
        weekend: Boolean = false,
    ) = TripHabits.Trip(place, weekend, minute, day)

    @Test
    fun weekdayMorningsMeanTheOffice() {
        val trips = (1L..5L).map { t(office, 8 * 60 + 10, 100 + it) } + listOf(t(gym, 19 * 60, 104), t(gym, 19 * 60 + 20, 105))
        assertEquals(office, TripHabits.Model.predict(trips, false, 8 * 60, 108, home))
        assertEquals(gym, TripHabits.Model.predict(trips, false, 19 * 60 + 10, 108, home))
    }

    @Test
    fun oneTripIsNotAHabit() {
        assertNull(TripHabits.Model.predict(listOf(t(gym, 19 * 60, 104)), false, 19 * 60, 105, home))
    }

    @Test
    fun noSuggestionAtTheWrongTimeOrWhenAlreadyThere() {
        val trips = (1L..5L).map { t(office, 8 * 60, 100 + it) }
        assertNull(TripHabits.Model.predict(trips, false, 14 * 60, 108, home))
        assertNull(TripHabits.Model.predict(trips, false, 8 * 60, 108, RoutePoint(3.1581, 101.7121)))
    }
}
