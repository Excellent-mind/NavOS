package com.excellentmindblow.navos.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceStoresTest {
    private val prefs = InMemoryPrefs()

    private fun place(
        label: String,
        lat: Double = 3.15,
    ) = GeocodeResult(label, lat, 101.71)

    @Test
    fun savedPlaceIsUnsetUntilSaved() {
        val saved = SavedPlaces(prefs)
        assertNull(saved.get(SavedPlaces.HOME))

        saved.save(SavedPlaces.HOME, place("Taman Connaught, Cheras", lat = 3.08))
        val home = saved.get(SavedPlaces.HOME)!!
        assertEquals("Taman Connaught, Cheras", home.label)
        assertEquals(3.08, home.latitude, 1e-5)
        assertEquals(101.71, home.longitude, 1e-5)
        assertNull(saved.get(SavedPlaces.WORK))
    }

    @Test
    fun savedPlacesReadTheExistingStorageKeys() {
        // Places saved by earlier app versions must still be found.
        prefs.edit().putFloat("work_lat", 3.11f).putFloat("work_lon", 101.67f).putString("work_label", "Bangsar South").apply()
        assertEquals("Bangsar South", SavedPlaces(prefs).get(SavedPlaces.WORK)!!.label)
    }

    @Test
    fun recentsAreNewestFirstWithoutDuplicates() {
        val recents = RecentPlaces(prefs)
        recents.save(place("Suria KLCC, Kuala Lumpur"))
        recents.save(place("Mid Valley Megamall, Kuala Lumpur"))
        recents.save(place("Suria KLCC, Kuala Lumpur"))

        val labels = recents.load().map { it.label }
        assertEquals(listOf("Suria KLCC, Kuala Lumpur", "Mid Valley Megamall, Kuala Lumpur"), labels)
        assertEquals("Suria KLCC", recents.load().first().name)
    }

    @Test
    fun recentsKeepOnlyTheNewestTen() {
        val recents = RecentPlaces(prefs)
        for (i in 1..12) recents.save(place("Place $i"))
        val labels = recents.load().map { it.label }
        assertEquals(RecentPlaces.MAX_STORED, labels.size)
        assertEquals("Place 12", labels.first())
        assertEquals("Place 3", labels.last())
    }

    @Test
    fun clearRemovesAllRecents() {
        val recents = RecentPlaces(prefs)
        recents.save(place("Suria KLCC"))
        recents.clear()
        assertTrue(recents.load().isEmpty())
    }

    @Test
    fun corruptRecentsReadAsEmpty() {
        prefs.edit().putString("recent_places", "not json").apply()
        assertTrue(RecentPlaces(prefs).load().isEmpty())
    }
}
