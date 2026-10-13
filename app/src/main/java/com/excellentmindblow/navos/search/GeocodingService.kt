package com.excellentmindblow.navos.search

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One place search hit. label is the full one-line description (name plus
 * locality/district/state), used for saved places. name/address are the
 * same thing split for the two-line results row (bold name, grey area
 * underneath) - they default to splitting label at its first comma, which
 * is right for Nominatim's display_name and for saved Home/Work labels. */
data class GeocodeResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val name: String = label.substringBefore(",").trim(),
    val address: String = label.substringAfter(",", "").trim(),
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

    // ---- Photon (search-as-you-type) -------------------------------------
    //
    // Nominatim's usage policy doesn't allow autocomplete on its public
    // server, so live results while typing come from Photon instead - also
    // OpenStreetMap data, but built specifically for search-as-you-type
    // (prefix matching, e.g. "lorong" -> every Lorong nearby) with a
    // location bias. Same "nearby first, then widen" approach as the
    // Nominatim path below.
    private const val PHOTON_URL = "https://photon.komoot.io/api/"
    private const val MALAYSIA_BBOX = "99.6,0.85,119.4,7.45" // minLon,minLat,maxLon,maxLat
    private const val LIVE_LOCAL_RADIUS_DEGREES = 0.3 // roughly city-sized (~30km)
    private const val MIN_LIVE_LOCAL_RESULTS = 5
    private const val LIVE_RESULT_LIMIT = 15
    const val MIN_LIVE_QUERY_LENGTH = 1

    /** Live (as-you-type) search via Photon. Local results come back sorted
     * nearest-first; if there are too few nearby, Malaysia-wide matches are
     * appended after them. Throws on network/HTTP failure. */
    @Throws(Exception::class)
    fun searchLive(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_LIVE_QUERY_LENGTH) return emptyList()

        if (biasLat == null || biasLon == null) {
            return photonSearch(trimmed, null, null, MALAYSIA_BBOX)
        }

        val r = LIVE_LOCAL_RADIUS_DEGREES
        val localBox = "${biasLon - r},${biasLat - r},${biasLon + r},${biasLat + r}"
        val local =
            photonSearch(trimmed, biasLat, biasLon, localBox)
                .sortedBy { distanceMeters(biasLat, biasLon, it.latitude, it.longitude) }
        if (local.size >= MIN_LIVE_LOCAL_RESULTS) return local.take(LIVE_RESULT_LIMIT)

        val wide = photonSearch(trimmed, biasLat, biasLon, MALAYSIA_BBOX)
        return (local + wide)
            .distinctBy { "${it.label}|${it.latitude}|${it.longitude}" }
            .take(LIVE_RESULT_LIMIT)
    }

    private fun photonSearch(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        bbox: String,
    ): List<GeocodeResult> {
        val params =
            StringBuilder()
                .append("q=").append(URLEncoder.encode(query, "UTF-8"))
                .append("&limit=").append(LIVE_RESULT_LIMIT)
                .append("&bbox=").append(bbox)
        if (biasLat != null && biasLon != null) {
            params.append("&lat=").append(biasLat).append("&lon=").append(biasLon)
        }

        val connection = URL("$PHOTON_URL?$params").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Live search failed (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
            val results = mutableListOf<GeocodeResult>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                parsePhotonFeature(feature)?.let { results.add(it) }
            }
            return results
        } finally {
            connection.disconnect()
        }
    }

    /** Name of whatever is at a tapped spot on the map (Photon reverse
     * lookup). Returns the same place moved to the exact tapped point, or
     * null when nothing nameable is there. Blocking - run off the main
     * thread. */
    @Throws(Exception::class)
    fun reverse(
        lat: Double,
        lon: Double,
    ): GeocodeResult? {
        require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid coordinates" }

        // Photon is good at identifying named places; Nominatim zoom 18 is a
        // useful second source for address-level detail (house number/street).
        // Keep the exact tapped coordinate for navigation even if the provider
        // returns the centre of the matched building or road.
        val photon = runCatching { reversePhoton(lat, lon) }.getOrNull()
        val detailed = runCatching { reverseNominatim(lat, lon) }.getOrNull()
        return when {
            detailed == null -> photon
            photon == null -> detailed.copy(latitude = lat, longitude = lon)
            else -> {
                val photonIsNamedPlace = photon.name.isNotBlank() &&
                    !photon.name.equals(photon.address, ignoreCase = true)
                val detailedIsSpecific = detailed.label.contains(Regex("\\d")) ||
                    detailed.label.split(',').size >= 3
                when {
                    photonIsNamedPlace && !detailedIsSpecific -> photon.copy(latitude = lat, longitude = lon)
                    else -> detailed.copy(
                        label = if (photonIsNamedPlace && !photon.label.equals(detailed.label, true))
                            "${photon.name}, ${detailed.label}" else detailed.label,
                        latitude = lat,
                        longitude = lon,
                        name = if (photonIsNamedPlace) photon.name else detailed.name,
                    )
                }
            }
        }
    }

    private fun reversePhoton(lat: Double, lon: Double): GeocodeResult? {
        val connection = URL("https://photon.komoot.io/reverse?lat=$lat&lon=$lon&limit=1").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val feature = JSONObject(body).optJSONArray("features")?.optJSONObject(0) ?: return null
            return parsePhotonFeature(feature)?.copy(latitude = lat, longitude = lon)
        } finally { connection.disconnect() }
    }

    private fun reverseNominatim(lat: Double, lon: Double): GeocodeResult? {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=18&addressdetails=1&namedetails=1")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val obj = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val label = obj.optString("display_name", "").trim()
            if (label.isBlank()) return null
            val a = obj.optJSONObject("address")
            fun part(key: String) = a?.optString(key, "")?.takeIf { !it.isNullOrBlank() && it != "null" }
            val number = part("house_number")
            val road = part("road") ?: part("pedestrian") ?: part("residential")
            val name = part("amenity") ?: part("building") ?: listOfNotNull(number, road).joinToString(" ").takeIf { it.isNotBlank() }
                ?: label.substringBefore(',').trim()
            return GeocodeResult(label, lat, lon, name, label.removePrefix(name).trim().trimStart(','))
        } finally { connection.disconnect() }
    }

    private fun parsePhotonFeature(feature: JSONObject): GeocodeResult? {
        val props = feature.optJSONObject("properties") ?: return null
        // The bbox also clips bits of Singapore/Thailand/Brunei/Indonesia -
        // keep this Malaysia-only, same as the Nominatim path.
        val country = props.str("countrycode")
        if (country != null && !country.equals("MY", ignoreCase = true)) return null

        val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        if (coords.length() < 2) return null
        val lon = coords.optDouble(0)
        val lat = coords.optDouble(1)
        if (lat.isNaN() || lon.isNaN()) return null

        val street = props.str("street")
        val houseNumber = props.str("housenumber")
        val placeName = props.str("name")
        val postcode = props.str("postcode")
        val name =
            placeName
                ?: street?.let { if (houseNumber != null) "$houseNumber $it" else it }
                ?: props.str("city")
                ?: return null

        // Preserve address-level detail returned by Photon. A named building/POI
        // remains the primary label, while house number + street are retained in
        // the subtitle instead of being silently discarded.
        val streetAddress = street?.let { if (houseNumber != null) "$houseNumber $it" else it }
        val address =
            listOf(
                if (placeName != null) streetAddress else null,
                props.str("housenumber")?.takeIf { placeName != null && streetAddress == null },
                props.str("district"),
                props.str("locality"),
                props.str("city"),
                props.str("county"),
                props.str("state"),
                postcode,
                props.str("country"),
            ).filterNotNull()
                .filter { !it.equals(name, ignoreCase = true) }
                .distinct()
                .joinToString(", ")

        val label = if (address.isEmpty()) name else "$name, $address"
        return GeocodeResult(label, lat, lon, name, address)
    }

    /** optString() returns "" for a missing key and "null" for an explicit
     * JSON null - treat both (and blanks) as absent. */
    private fun JSONObject.str(key: String): String? {
        val value = optString(key, "")
        return if (value.isBlank() || value == "null") null else value.trim()
    }

    fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
    ): Float {
        val out = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, toLat, toLon, out)
        return out[0]
    }

    // ---- Explicit search (keyboard Search key) ----------------------------

    /** Search-key search: tries Photon first (same results the driver was
     * already seeing while typing), and only falls back to Nominatim if
     * Photon fails or finds nothing - so this is never worse than the
     * Nominatim-only search it replaced. */
    @Throws(Exception::class)
    fun search(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val live =
            try {
                searchLive(trimmed, biasLat, biasLon)
            } catch (e: Exception) {
                emptyList()
            }
        if (live.isNotEmpty()) return live

        return searchNominatim(trimmed, biasLat, biasLon)
    }

    @Throws(Exception::class)
    private fun searchNominatim(
        trimmed: String,
        biasLat: Double?,
        biasLon: Double?,
    ): List<GeocodeResult> {
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
                .append("&addressdetails=1")
                .append("&namedetails=1")
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
            return (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val displayName = obj.optString("display_name", "").trim()
                val lat = obj.optString("lat", "").toDoubleOrNull() ?: return@mapNotNull null
                val lon = obj.optString("lon", "").toDoubleOrNull() ?: return@mapNotNull null
                if (displayName.isBlank()) return@mapNotNull null
                val address = obj.optJSONObject("address")
                val name = obj.optString("name", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: listOfNotNull(
                        address?.optString("house_number")?.takeIf { it.isNotBlank() && it != "null" },
                        address?.optString("road")?.takeIf { it.isNotBlank() && it != "null" },
                    ).joinToString(" ").takeIf { it.isNotBlank() }
                    ?: displayName.substringBefore(",").trim()
                GeocodeResult(displayName, lat, lon, name, displayName.removePrefix(name).trim().trimStart(','))
            }
        } finally {
            connection.disconnect()
        }
    }
}
