package com.excellentmindblow.navos.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCategoryTest {
    @Test
    fun commonPlacesGetPlainLabels() {
        assertEquals("Shopping mall", PlaceCategory.label("shop", "mall"))
        assertEquals("Petrol", PlaceCategory.label("amenity", "fuel"))
        assertEquals("Train station", PlaceCategory.label("railway", "station"))
        assertEquals("Road", PlaceCategory.label("highway", "primary"))
        assertEquals("Area", PlaceCategory.label("place", "suburb"))
        assertEquals("Bus stop", PlaceCategory.label("highway", "bus_stop"))
    }

    @Test
    fun unknownValuesAreReadableAndMissingOnesAreNull() {
        assertEquals("Car repair", PlaceCategory.label("shop", "car_repair"))
        assertEquals("Building", PlaceCategory.label("building", "yes"))
        assertNull(PlaceCategory.label("tourism", "yes"))
        assertNull(PlaceCategory.label(null, "mall"))
    }

    @Test
    fun housesAndRoadsAreAddressesNamedPlacesAreNot() {
        assertTrue(PlaceCategory.isAddress("place", "house", hasHouseNumber = true, hasName = false))
        assertTrue(PlaceCategory.isAddress("highway", "residential", hasHouseNumber = false, hasName = true))
        assertTrue(PlaceCategory.isAddress("building", "yes", hasHouseNumber = true, hasName = false))
        assertFalse(PlaceCategory.isAddress("shop", "mall", hasHouseNumber = false, hasName = true))
        assertFalse(PlaceCategory.isAddress("highway", "bus_stop", hasHouseNumber = false, hasName = true))
        assertFalse(PlaceCategory.isAddress("building", "commercial", hasHouseNumber = true, hasName = true))
    }
}
