package com.excellentmindblow.navos.routing

/** A decoded route point - plain lat/lng, no dependency on MapLibre's own
 * LatLng so this package stays independent of the map SDK. */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Native port of the original React Native app's polyline.ts - same Google
 * encoded-polyline decode (OSRM's `geometries=polyline` response format),
 * same algorithm shape (result seeded at 1, offset-by-one byte read) so
 * the two stay byte-for-byte equivalent in behavior.
 */
object PolylineDecoder {
    fun decode(
        encoded: String,
        precision: Int = 5,
    ): List<RoutePoint> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<RoutePoint>()
        var index = 0
        var lat = 0
        var lng = 0
        val length = encoded.length

        while (index < length) {
            var result = 1
            var shift = 0
            var b: Int
            do {
                b = encoded[index].code - 63 - 1
                index++
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 1
            shift = 0
            do {
                b = encoded[index].code - 63 - 1
                index++
                result += b shl shift
                shift += 5
            } while (b >= 0x1f)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(RoutePoint(latitude = lat / factor, longitude = lng / factor))
        }

        return points
    }
}
