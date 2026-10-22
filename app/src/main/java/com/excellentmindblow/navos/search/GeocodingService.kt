package com.excellentmindblow.navos.search

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** One place search hit. label is the full one-line description (name plus
 * locality/district/state), used for saved places. name/address are the
 * same thing split for the two-line results row (bold name, grey area
 * underneath) - they default to splitting label at its first comma, which
 * is right for Nominatim's display_name and for saved Home/Work labels.
 *
 * houseNumber / approximate are new and default-valued, so every existing
 * GeocodeResult(...) call keeps compiling. approximate = true means the
 * house number was not found in the map data and the pin is placed on the
 * street instead. */
data class GeocodeResult(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val name: String = label.substringBefore(",").trim(),
    val address: String = label.substringAfter(",", "").trim(),
    val houseNumber: String? = null,
    val approximate: Boolean = false,
    /** Short kind of place ("Shopping mall", "Road"), when the source says. See [PlaceCategory]. */
    val category: String? = null,
    /** A house or a road rather than a named place. */
    val isAddress: Boolean = false,
)

/**
 * Place search for NavOS (Photon for search-as-you-type, Nominatim for
 * address-level lookups, both OpenStreetMap data).
 *
 * What changed vs the previous version (house numbers missing / slow popup):
 *  1. Results were sorted purely by distance, which pushed the exact
 *     "12 Jalan Ampang" match below unrelated nearby places. Results are
 *     now ranked by relevance first (house number match, name/token match),
 *     then distance.
 *  2. Queries are normalised before they hit the network: "No. 12, Jln
 *     Ampang" -> "12 Jalan Ampang" (No/Lot/Unit/Blok prefixes dropped,
 *     Jln/Tmn/Lrg/Kg/... expanded, commas removed).
 *  3. Live search used a single-threaded executor on the caller side, so one
 *     slow request made every following keystroke wait. Now every new live
 *     search aborts the previous in-flight one immediately, the nearby and
 *     Malaysia-wide requests run in parallel instead of one after the other,
 *     and a small cache answers repeat queries instantly.
 *  4. When the map data has the street but not that house number, an
 *     "approximate" result on the street is added so the address still shows
 *     up and can be navigated to (like Google Maps does).
 *  5. Explicit Search / shared addresses use Nominatim first for
 *     address-style queries (it understands house numbers best).
 *
 * Blocking/synchronous by design - callers must run this off the main
 * thread (see MainActivity's search executor) and marshal the result
 * back themselves.
 */
object GeocodingService {
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"

    private const val LOCAL_RADIUS_DEGREES = 0.3 // roughly city-sized (~30km)
    private const val WIDE_RADIUS_DEGREES = 2.0 // roughly state-sized (~220km)
    private const val MIN_LOCAL_RESULTS = 3

    // Photon servers, tried in order. The second is OpenRoad's self-hosted
    // Photon (Asia extract, same OpenStreetMap data) - github.com/Rekabytes-Enterprise/openroad -
    // used only when the public komoot server times out or fails.
    private val PHOTON_BASES = listOf("https://photon.komoot.io", "https://po-openroad.techapi.my")
    private const val MALAYSIA_BBOX = "99.6,0.85,119.4,7.45" // minLon,minLat,maxLon,maxLat
    private const val LIVE_LOCAL_RADIUS_DEGREES = 0.3 // roughly city-sized (~30km)
    private const val MIN_LIVE_LOCAL_RESULTS = 5
    private const val LIVE_RESULT_LIMIT = 15
    private const val PHOTON_FETCH_LIMIT = 20
    const val MIN_LIVE_QUERY_LENGTH = 1

    private const val LIVE_TIMEOUT_MS = 4_000
    private const val RETRY_TIMEOUT_MS = 8_000
    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val CACHE_MAX = 80

    // ---- shared plumbing ---------------------------------------------------

    /** Worker threads for the parallel nearby / Malaysia-wide requests. */
    private val pool: ExecutorService =
        Executors.newCachedThreadPool { r -> Thread(r, "geocode-io").apply { isDaemon = true } }

    /** Connections of the current as-you-type search. A newer keystroke
     * disconnects them so a slow, stale request can never block the next one. */
    private val liveConnections: MutableSet<HttpURLConnection> =
        Collections.synchronizedSet(HashSet())

    /** Bumped whenever a newer keystroke cancels the live search. */
    @Volatile private var liveGeneration = 0

    private fun cancelLiveInFlight() {
        liveGeneration++
        val snapshot = synchronized(liveConnections) { liveConnections.toList() }
        snapshot.forEach { runCatching { it.disconnect() } }
        liveConnections.clear()
    }

    private class CacheEntry(val at: Long, val value: List<GeocodeResult>)

    private val cache =
        object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
                size > CACHE_MAX
        }

    private fun cacheKey(q: String, lat: Double?, lon: Double?) =
        "${q.lowercase()}|${lat?.let { Math.round(it * 100) }}|${lon?.let { Math.round(it * 100) }}"

    private fun cacheGet(key: String): List<GeocodeResult>? =
        synchronized(cache) {
            val e = cache[key] ?: return null
            if (System.currentTimeMillis() - e.at > CACHE_TTL_MS) {
                cache.remove(key)
                null
            } else {
                e.value
            }
        }

    private fun cachePut(key: String, value: List<GeocodeResult>) {
        if (value.isEmpty()) return
        synchronized(cache) { cache[key] = CacheEntry(System.currentTimeMillis(), value) }
    }

    // Nominatim's usage policy: max 1 request per second.
    private val nominatimLock = Any()
    private var lastNominatimAt = 0L

    private fun throttleNominatim() {
        synchronized(nominatimLock) {
            val wait = 1_000L - (System.currentTimeMillis() - lastNominatimAt)
            if (wait > 0) Thread.sleep(wait)
            lastNominatimAt = System.currentTimeMillis()
        }
    }

    // ---- query normalisation -----------------------------------------------

    internal class ParsedQuery(
        val raw: String,
        /** Cleaned text sent to Photon: "12 Jalan Ampang Kuala Lumpur". */
        val cleaned: String,
        /** "12", "12A", "12-3", or null when the query has no house number. */
        val houseNumber: String?,
        /** The query without the house number: "Jalan Ampang Kuala Lumpur". */
        val withoutNumber: String,
        val tokens: List<String>,
    )

    private val ABBREVIATIONS =
        mapOf(
            "jln" to "jalan", "jl" to "jalan", "tmn" to "taman", "lrg" to "lorong",
            "lbh" to "lebuh", "lbhrya" to "lebuhraya", "kg" to "kampung", "bdr" to "bandar",
            "psn" to "persiaran", "bt" to "batu", "ppr" to "projek perumahan rakyat",
            "apt" to "apartment", "blk" to "blok",
        )
    private val UNIT_PREFIX = Regex("""\b(?:no|lot|unit|blok|block|blk|plot)\.?\s*(?=[#]?\d)""", RegexOption.IGNORE_CASE)
    private val HOUSE_TOKEN = Regex("""^#?\d{1,5}[a-zA-Z]?(?:[-/]\d{1,5}[a-zA-Z]?){0,3}$""")

    /** Only treat "<number> <words>" as a street address when the words look
     * like a street/area ("7 eleven" is a shop, not house 7 on "eleven"). */
    private val STREET_HINT =
        Regex(
            "\\b(jalan|lorong|lebuh|lebuhraya|persiaran|lengkok|laluan|solok|lintasan|taman|bandar|kampung|" +
                "bukit|desa|presint|precinct|seksyen|section|street|road|lane|avenue|drive|court|close|ss\\d+|usj|pjs|pju)\\b",
            RegexOption.IGNORE_CASE,
        )

    private fun looksLikeStreetAddress(q: ParsedQuery): Boolean =
        q.houseNumber != null && STREET_HINT.containsMatchIn(q.withoutNumber)

    internal fun parseQuery(raw: String): ParsedQuery {
        var s = raw.trim()
        s = UNIT_PREFIX.replace(s, "")
        // Delivery apps (Grab, Lalamove...) put a phone / order number in front:
        // "15377174 3, Jalan P11d/17 ..." - drop 6+ digit tokens and admin noise.
        s = s.replace(Regex("""\b\d{6,}\b"""), " ")
        s = s.replace(Regex("""\bwilayah\s+persekutuan\b""", RegexOption.IGNORE_CASE), " ")
        s = s.replace(Regex("""[,;]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        val words =
            s.split(' ').map { it.trimEnd('.') }.filter { it.isNotBlank() }.map { w ->
                ABBREVIATIONS[w.lowercase()] ?: w
            }.toMutableList()
        // "Putrajaya Putrajaya" -> "Putrajaya"
        var i = words.size - 1
        while (i > 0) {
            if (words[i].equals(words[i - 1], ignoreCase = true)) words.removeAt(i)
            i--
        }

        // House number: the first token, or a short trailing one ("Jalan Ampang 12").
        var house: String? = null
        if (words.isNotEmpty() && HOUSE_TOKEN.matches(words.first()) && words.size > 1) {
            house = words.removeAt(0).removePrefix("#")
        } else if (words.size > 1) {
            val last = words.last()
            // 5-digit trailing numbers are postcodes, not house numbers.
            if (HOUSE_TOKEN.matches(last) && last.count { it.isDigit() } <= 4) {
                house = last.removePrefix("#")
                words.removeAt(words.lastIndex)
            }
        }
        val without = words.joinToString(" ")
        val cleaned = if (house != null) "$house $without" else without
        val tokens = (listOfNotNull(house) + words).map { it.lowercase() }.filter { it.length > 1 || it[0].isDigit() }
        return ParsedQuery(raw.trim(), cleaned, house, without, tokens)
    }

    // ---- ranking -----------------------------------------------------------

    private fun sameHouse(a: String?, b: String?): Boolean =
        a != null && b != null && a.replace(" ", "").equals(b.replace(" ", ""), ignoreCase = true)

    /** The street part of a result's name: "3 Jalan Telawi 2" -> "jalan telawi 2". */
    private fun streetOf(r: GeocodeResult): String {
        val name = r.name.lowercase().substringBefore(',').trim()
        val number = r.houseNumber?.lowercase()
        return if (number != null && name.startsWith(number)) name.removePrefix(number).trim() else name
    }

    /** The exact house: same number AND on the street the query names (so
     * "3 Jalan Telawi 3" is not satisfied by "3 Jalan Telawi 2"). */
    internal fun isExactHouse(
        q: ParsedQuery,
        r: GeocodeResult,
    ): Boolean {
        if (!sameHouse(q.houseNumber, r.houseNumber)) return false
        val street = streetOf(r)
        val wanted = q.withoutNumber.lowercase()
        if (street.isEmpty() || !wanted.startsWith(street)) return false
        // Whole words only: "jalan telawi 3" must not match "jalan telawi 33".
        return wanted.length == street.length || wanted[street.length] == ' '
    }

    /** Stops and car parks carry a building's name ("Menara Maybank" bus
     * stop); unless the driver asked for one, the place itself should win. */
    private val TRANSIT_CATEGORIES = setOf("Bus stop", "Parking", "Train station")
    private val TRANSIT_WORDS = Regex("\\b(bus|stop|hentian|parking|park|letak|station|stesen|lrt|mrt|ktm|monorail)\\b", RegexOption.IGNORE_CASE)

    /** Lower = better. Relevance dominates; distance only breaks ties. */
    internal fun rank(
        q: ParsedQuery,
        results: List<GeocodeResult>,
        biasLat: Double?,
        biasLon: Double?,
    ): List<GeocodeResult> {
        if (results.isEmpty()) return results
        val nameWords = q.withoutNumber.lowercase()
        val wantsTransit = TRANSIT_WORDS.containsMatchIn(q.raw)
        fun score(r: GeocodeResult): Double {
            val hay = r.label.lowercase()
            val matched = q.tokens.count { hay.contains(it) }
            val coverage = if (q.tokens.isEmpty()) 0.0 else matched.toDouble() / q.tokens.size
            var s = -coverage * 500.0
            if (q.houseNumber != null && isExactHouse(q, r)) s -= 1_000.0
            val name = r.name.lowercase()
            if (nameWords.isNotEmpty() && name == nameWords) s -= 300.0
            if (nameWords.isNotEmpty() && name.startsWith(nameWords)) s -= 250.0
            if (!wantsTransit && r.category in TRANSIT_CATEGORIES) s += 200.0
            if (r.approximate) s += 400.0 // exact hits always beat the street fallback
            if (biasLat != null && biasLon != null) {
                // 1 point per km, up to across the country (Sabah / Sarawak).
                s += (distanceMeters(biasLat, biasLon, r.latitude, r.longitude) / 1000.0).coerceAtMost(2_000.0)
            }
            return s
        }
        return results.sortedBy { score(it) }
    }

    private fun dedupe(list: List<GeocodeResult>): List<GeocodeResult> =
        list.distinctBy { "${it.label.lowercase()}|${"%.4f".format(it.latitude)}|${"%.4f".format(it.longitude)}" }

    /** If the query had a house number that no result carries, add the same
     * number on the best street hit, flagged approximate. */
    internal fun withApproximate(q: ParsedQuery, results: List<GeocodeResult>): List<GeocodeResult> {
        val num = q.houseNumber ?: return results
        if (!looksLikeStreetAddress(q)) return results
        if (results.any { isExactHouse(q, it) }) return results
        val firstWord = q.withoutNumber.lowercase().substringBefore(' ')
        val wanted = q.withoutNumber.lowercase()
        val exactStreet =
            results.firstOrNull {
                val s = streetOf(it)
                it.houseNumber == null && s.isNotEmpty() && wanted.startsWith(s) && (wanted.length == s.length || wanted[s.length] == ' ')
            }
        val street =
            exactStreet
                ?: results.firstOrNull { it.houseNumber == null && it.label.lowercase().contains(firstWord) }
                ?: results.firstOrNull { it.houseNumber == null }
                ?: return results
        val streetName = street.name
        if (streetName.isBlank()) return results
        val area = street.address
        val fake =
            GeocodeResult(
                label = if (area.isBlank()) "$num $streetName" else "$num $streetName, $area",
                latitude = street.latitude,
                longitude = street.longitude,
                name = "$num $streetName",
                address = if (area.isBlank()) "Approximate location on street" else "Approximate location on street, $area",
                houseNumber = num,
                approximate = true,
            )
        // On the very street asked for, the approximate house is the best
        // answer (like Google Maps); on a guessed street it goes last.
        return if (street === exactStreet) listOf(fake) + results else results + fake
    }

    // ---- Photon (search-as-you-type) ----------------------------------------

    /** Live (as-you-type) search via Photon. Throws on network/HTTP failure
     * (including when a newer keystroke cancelled this request). */
    @Throws(Exception::class)
    fun searchLive(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
    ): List<GeocodeResult> = searchLiveInternal(query, biasLat, biasLon, cancellable = true)

    private fun searchLiveInternal(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        cancellable: Boolean,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_LIVE_QUERY_LENGTH) return emptyList()

        val key = cacheKey(trimmed, biasLat, biasLon)
        cacheGet(key)?.let { return it }

        if (cancellable) cancelLiveInFlight()
        val q = parseQuery(trimmed)
        if (q.cleaned.isBlank()) return emptyList()

        val merged: List<GeocodeResult>
        if (biasLat == null || biasLon == null) {
            merged = photonSearch(q.cleaned, null, null, MALAYSIA_BBOX, cancellable)
        } else {
            val r = LIVE_LOCAL_RADIUS_DEGREES
            val localBox = "${biasLon - r},${biasLat - r},${biasLon + r},${biasLat + r}"
            // Nearby and Malaysia-wide go out together instead of one after
            // the other - this is what makes the popup feel instant.
            val localF: Future<List<GeocodeResult>> =
                pool.submit(Callable { photonSearch(q.cleaned, biasLat, biasLon, localBox, cancellable) })
            val wideF: Future<List<GeocodeResult>> =
                pool.submit(Callable { photonSearch(q.cleaned, biasLat, biasLon, MALAYSIA_BBOX, cancellable) })

            val local = futureResult(localF)
            val wide = if (local.size >= MIN_LIVE_LOCAL_RESULTS && q.houseNumber == null) emptyList() else futureResult(wideF)
            if (local.isEmpty() && wide.isEmpty()) {
                // Both failed or are empty: surface a real failure to the caller.
                localF.get()
                wideF.get()
            }
            merged = dedupe(local + wide)
        }

        var ranked = rank(q, merged, biasLat, biasLon)
        if (looksLikeStreetAddress(q) && ranked.none { isExactHouse(q, it) }) {
            // No exact house number: grab the street itself so we can still offer it.
            val street =
                runCatching {
                    photonSearch(q.withoutNumber, biasLat, biasLon, MALAYSIA_BBOX, cancellable)
                }.getOrDefault(emptyList())
            ranked = rank(q, dedupe(ranked + street), biasLat, biasLon)
            ranked = withApproximate(q, ranked)
        }
        val out = ranked.take(LIVE_RESULT_LIMIT)
        cachePut(key, out)
        return out
    }

    /** Result of a parallel request; a failed one counts as empty so the other can still answer. */
    private fun futureResult(f: Future<List<GeocodeResult>>): List<GeocodeResult> =
        try {
            f.get()
        } catch (e: Exception) {
            emptyList()
        }

    /** The public Photon server often times out on a query's first request
     * and then answers the same one quickly, so a timeout gets one retry with
     * more time - unless a newer keystroke has replaced this search. */
    private fun photonSearch(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        bbox: String,
        cancellable: Boolean,
    ): List<GeocodeResult> {
        val generation = liveGeneration
        return try {
            photonRequest(PHOTON_BASES[0], query, biasLat, biasLon, bbox, cancellable, LIVE_TIMEOUT_MS)
        } catch (e: IOException) {
            // Cancelled by a newer keystroke: give up. Otherwise the public
            // server is slow or down - ask the backup server instead.
            if (cancellable && generation != liveGeneration) throw e
            photonRequest(PHOTON_BASES[1], query, biasLat, biasLon, bbox, cancellable, RETRY_TIMEOUT_MS)
        }
    }

    private fun photonRequest(
        base: String,
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        bbox: String,
        cancellable: Boolean,
        timeoutMs: Int,
    ): List<GeocodeResult> {
        val params =
            StringBuilder()
                .append("q=").append(URLEncoder.encode(query, "UTF-8"))
                .append("&limit=").append(PHOTON_FETCH_LIMIT)
                .append("&bbox=").append(bbox)
        if (biasLat != null && biasLon != null) {
            params.append("&lat=").append(biasLat).append("&lon=").append(biasLon)
            params.append("&location_bias_scale=0.3")
        }

        val connection = URL("$base/api/?$params").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        if (cancellable) liveConnections.add(connection)

        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Live search failed ($code)")
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
            liveConnections.remove(connection)
            connection.disconnect()
        }
    }

    // ---- Reverse (tap on map) -----------------------------------------------

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

        val photonF = pool.submit(Callable { runCatching { reversePhoton(lat, lon) }.getOrNull() })
        val detailedF = pool.submit(Callable { runCatching { reverseNominatim(lat, lon) }.getOrNull() })
        val photon = runCatching { photonF.get() }.getOrNull()
        val detailed = runCatching { detailedF.get() }.getOrNull()
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
        for (base in PHOTON_BASES) {
            val found = runCatching { reversePhotonAt(base, lat, lon) }
            if (found.isSuccess) return found.getOrNull()
        }
        return null
    }

    /** Null = nothing nameable there; throws when the server didn't answer. */
    private fun reversePhotonAt(
        base: String,
        lat: Double,
        lon: Double,
    ): GeocodeResult? {
        val connection = URL("$base/reverse?lat=$lat&lon=$lon&limit=1").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Photon reverse failed (${connection.responseCode})")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val feature = JSONObject(body).optJSONArray("features")?.optJSONObject(0) ?: return null
            return parsePhotonFeature(feature)?.copy(latitude = lat, longitude = lon)
        } finally { connection.disconnect() }
    }

    private fun reverseNominatim(lat: Double, lon: Double): GeocodeResult? {
        throttleNominatim()
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
            return GeocodeResult(label, lat, lon, name, label.removePrefix(name).trim().trimStart(',').trim(), houseNumber = number)
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
        val streetAddress = street?.let { if (houseNumber != null) "$houseNumber $it" else it }

        // Address hits (house number + street, no building name): show
        // "12 Jalan Ampang" as the bold line - previously the number could
        // get lost when a name was also present.
        val name =
            placeName
                ?: streetAddress
                ?: props.str("city")
                ?: return null

        val address =
            listOf(
                if (placeName != null) streetAddress else null,
                houseNumber?.takeIf { placeName != null && streetAddress == null },
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
        val osmKey = props.str("osm_key")
        val osmValue = props.str("osm_value")
        return GeocodeResult(
            label,
            lat,
            lon,
            name,
            address,
            houseNumber = houseNumber,
            category = PlaceCategory.label(osmKey, osmValue),
            isAddress = PlaceCategory.isAddress(osmKey, osmValue, houseNumber != null, placeName != null),
        )
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
    ): Float = GeoUtils.distanceMeters(RoutePoint(fromLat, fromLon), RoutePoint(toLat, toLon)).toFloat()

    // ---- Address / explicit search --------------------------------------------

    /**
     * Resolve addresses shared from other apps. Nominatim first (best with
     * house numbers), then Photon. Must be called off the main thread.
     */
    @Throws(Exception::class)
    fun searchAddress(query: String, biasLat: Double? = null, biasLon: Double? = null): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val q = parseQuery(trimmed)
        val detailed = try {
            searchNominatim(q, biasLat, biasLon)
        } catch (_: Exception) {
            emptyList()
        }
        if (detailed.isNotEmpty()) return detailed
        return searchLiveInternal(trimmed, biasLat, biasLon, cancellable = false)
    }

    /** Search-key search. Address-style queries (with a house number) go to
     * Nominatim first, merged with Photon; plain place names use Photon
     * first and fall back to Nominatim. Either way the exact house number
     * is ranked first, and an approximate street hit is added when the
     * number is unknown to the map. */
    @Throws(Exception::class)
    fun search(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null,
    ): List<GeocodeResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val q = parseQuery(trimmed)

        val photonF = pool.submit(Callable { runCatching { searchLiveInternal(trimmed, biasLat, biasLon, cancellable = false) }.getOrDefault(emptyList()) })

        if (q.houseNumber != null) {
            val nominatim = runCatching { searchNominatim(q, biasLat, biasLon) }.getOrDefault(emptyList())
            val photon = futureResult(photonF)
            val merged = rank(q, dedupe(nominatim + photon), biasLat, biasLon)
            return withApproximate(q, merged).take(10)
        }

        val live = futureResult(photonF)
        if (live.isNotEmpty()) return live
        return searchNominatim(q, biasLat, biasLon)
    }

    @Throws(Exception::class)
    private fun searchNominatim(
        q: ParsedQuery,
        biasLat: Double?,
        biasLon: Double?,
    ): List<GeocodeResult> {
        val freeText = q.cleaned
        var found: List<GeocodeResult> =
            if (biasLat == null || biasLon == null) {
                // No GPS fix yet - fall back to a plain Malaysia-wide search.
                performSearch(freeText = freeText, structuredStreet = null, biasLat = null, biasLon = null, bounded = false)
            } else {
                val local = performSearch(freeText, null, biasLat, biasLon, LOCAL_RADIUS_DEGREES, bounded = true)
                if (local.size >= MIN_LOCAL_RESULTS) {
                    local
                } else {
                    // Too few nearby matches - widen (still biased, not bounded).
                    val wide = performSearch(freeText, null, biasLat, biasLon, WIDE_RADIUS_DEGREES, bounded = false)
                    dedupe(local + wide)
                }
            }

        // Free-text search often misses a house number that the structured
        // form ("street=12 Jalan Ampang") finds.
        if (q.houseNumber != null && found.none { isExactHouse(q, it) }) {
            val structured =
                runCatching {
                    performSearch(
                        freeText = null,
                        structuredStreet = "${q.houseNumber} ${q.withoutNumber}",
                        biasLat = biasLat,
                        biasLon = biasLon,
                        radiusDegrees = WIDE_RADIUS_DEGREES,
                        bounded = false,
                    )
                }.getOrDefault(emptyList())
            found = dedupe(structured + found)
        }
        return rank(q, found, biasLat, biasLon).take(10)
    }

    private fun performSearch(
        freeText: String?,
        structuredStreet: String?,
        biasLat: Double?,
        biasLon: Double?,
        radiusDegrees: Double = 0.0,
        bounded: Boolean,
    ): List<GeocodeResult> {
        throttleNominatim()
        val params = StringBuilder()
        if (freeText != null) {
            params.append("q=").append(URLEncoder.encode(freeText, "UTF-8"))
        } else if (structuredStreet != null) {
            params.append("street=").append(URLEncoder.encode(structuredStreet, "UTF-8"))
        }
        params
            .append("&format=jsonv2")
            .append("&addressdetails=1")
            .append("&namedetails=1")
            .append("&limit=10")
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
                val number = address?.optString("house_number")?.takeIf { it.isNotBlank() && it != "null" }
                val road = address?.optString("road")?.takeIf { it.isNotBlank() && it != "null" }
                val name = obj.optString("name", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: listOfNotNull(number, road).joinToString(" ").takeIf { it.isNotBlank() }
                    ?: displayName.substringBefore(",").trim()
                // jsonv2 names the tag key "category" (plain json: "class").
                val osmKey = (obj.optString("category", "").ifBlank { obj.optString("class", "") }).takeIf { it.isNotBlank() && it != "null" }
                val osmValue = obj.optString("type", "").takeIf { it.isNotBlank() && it != "null" }
                GeocodeResult(
                    label = displayName,
                    latitude = lat,
                    longitude = lon,
                    name = name,
                    address = displayName.removePrefix(name).trim().trimStart(',').trim().replace(';', '/'),
                    houseNumber = number,
                    category = PlaceCategory.label(osmKey, osmValue),
                    isAddress = PlaceCategory.isAddress(osmKey, osmValue, number != null, obj.has("name") && obj.optString("name").isNotBlank()),
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
