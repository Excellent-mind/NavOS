package com.excellentmindblow.navos.search

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Nominatim search hit. */
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

    @Throws(Exception::class)
    fun search(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val params =
            StringBuilder()
                .append("q=").append(URLEncoder.encode(trimmed, "UTF-8"))
                .append("&format=jsonv2")
                .append("&addressdetails=0")
                .append("&limit=6")
                .append("&countrycodes=my")

        if (biasLat != null && biasLon != null) {
            val left = biasLon - 1
            val top = biasLat + 1
            val right = biasLon + 1
            val bottom = biasLat - 1
            params
                .append("&viewbox=")
                .append(left).append(',').append(top).append(',').append(right).append(',').append(bottom)
            params.append("&bounded=0")
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
