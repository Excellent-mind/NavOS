package com.excellentmindblow.navos.search

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Nominatim search hit. label is Nominatim's display_name, which is
 * already the full locality/district/state hierarchy (e.g. "Jalan Tun
 * Razak, Kuala Lumpur, Wilayah Persekutuan Kuala Lumpur, Malaysia") - that
 * alone is what tells two same-named roads in different areas apart in
 * the results list, so callers don't need to build that string themselves. */
data class GeocodeResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Native equivalent of legacy-expo-rn/src/services/geocoding.ts - same
 * Nominatim endpoint, params, and User-Agent (Nominatim's usage policy
 * requires a descriptive User-Agent identifying the calling app).
 *
 * Blocking/synchronous by design - callers must run this off the main
 * thread (see MainActivity's search executor) and marshal the result
 * back themselves.
 */
object GeocodingService {
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"

    // Nominatim viewbox size around the driver's current GPS fix. The
    // local pass is a tight, HARD-bounded box (roughly a city's worth of
    // area) so a short query doesn't surface a same-named place clear
    // across the country ahead of the one nearby. Only when that comes
    // back thin do we retry with a wider, soft (unbounded) box - "search
    // nearby first, then expand" the way Waze/Google Maps behave,
    // approximated with what Nominatim's free API actually offers (no raw
    // proximity-sort param like a paid provider would have).
    private const val LOCAL_RADIUS_DEGREES = 0.3 // roughly city-sized (~30km)
    private const val WIDE_RADIUS_DEGREES = 2.0 // roughly state-sized (~220km)
    private const val MIN_LOCAL_RESULTS = 3

    @Throws(Exception::class)
    fun search(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (biasLat == null || biasLon == null) {
            // No GPS fix yet - fall back to a plain Malaysia-wide search
            // rather than failing outright.
            return performSearch(trimmed, null, null, bounded = false)
        }

        val local = performSearch(trimmed, biasLat, biasLon, LOCAL_RADIUS_DEGREES, bounded = true)
        if (local.size >= MIN_LOCAL_RESULTS) return local

        // Too few nearby matches - widen the search instead of leaving the
        // driver with a near-empty list. Still biased toward their
        // location (unbounded viewbox nudges ranking, doesn't exclude),
        // and local hits are kept first since they're the more useful
        // match for "near me" intent.
        val wide = performSearch(trimmed, biasLat, biasLon, WIDE_RADIUS_DEGREES, bounded = false)
        return (local + wide)
            .distinctBy { "${it.label}|${it.latitude}|${it.longitude}" }
            .take(8)
    }

    private fun performSearch(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        radiusDegrees: Double = 0.0,
        bounded: Boolean,
    ): List<GeocodeResult> {
        val params =
            StringBuilder()
                .append("q=").append(URLEncoder.encode(query, "UTF-8"))
                .append("&format=jsonv2")
                .append("&addressdetails=0")
                .append("&limit=8")
                // Keeps results inside Malaysia - the app's whole premise
                // is Malaysia-focused navigation, so this stays a hard
                // filter rather than opening up to every country. A
                // driver searching a specific town/city name that also
                // exists in Malaysia still matches; it just won't pull in
                // results from outside Malaysia.
                .append("&countrycodes=my")

        if (biasLat != null && biasLon != null) {
            val left = biasLon - radiusDegrees
            val top = biasLat + radiusDegrees
            val right = biasLon + radiusDegrees
            val bottom = biasLat - radiusDegrees
            params
                .append("&viewbox=")
                .append(left).append(',').append(top).append(',').append(right).append(',').append(bottom)
            params.append("&bounded=").append(if (bounded) "1" else "0")
        }

        val url = URL("https://nominatim.openstreetmap.org/search?$params")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Place search failed (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                GeocodeResult(
                    label = obj.getString("display_name"),
                    latitude = obj.getString("lat").toDouble(),
                    longitude = obj.getString("lon").toDouble(),
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
