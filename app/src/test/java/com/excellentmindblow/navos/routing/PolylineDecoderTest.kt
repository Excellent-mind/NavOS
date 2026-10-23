package com.excellentmindblow.navos.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {
    // The worked example from Google's encoded-polyline format docs.
    private val googleExample = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun decodesGoogleReferenceExample() {
        val points = PolylineDecoder.decode(googleExample)

        assertEquals(3, points.size)
        assertPoint(38.5, -120.2, points[0])
        assertPoint(40.7, -120.95, points[1])
        assertPoint(43.252, -126.453, points[2])
    }

    @Test
    fun emptyStringDecodesToNoPoints() {
        assertTrue(PolylineDecoder.decode("").isEmpty())
    }

    private fun assertPoint(
        latitude: Double,
        longitude: Double,
        actual: RoutePoint,
    ) {
        assertEquals(latitude, actual.latitude, 1e-9)
        assertEquals(longitude, actual.longitude, 1e-9)
    }
}
