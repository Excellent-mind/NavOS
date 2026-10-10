package com.excellentmindblow.navos.poi

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class PoiKind { FUEL, EV }

/** One facility chip on the station card. listed = OpenStreetMap says the
 * station has it; false means "not listed" (unknown), never "doesn't
 * have" - most stations simply aren't tagged in that much detail. */
data class PoiFacility(
    val label: String,
    val listed: Boolean,
)

data class Poi(
    val id: String,
    val kind: PoiKind,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val facilities: List<PoiFacility>,
    /** EV only: plug types/power and number of chargers, one per line. */
    val spec: String?,
)

/**
 * Petrol stations and EV chargers around a point, from OpenStreetMap via
 * the public Overpass API (free, no key). One request also pulls nearby
 * surau and public toilets, so a station counts as having one when it's
 * tagged directly OR one is mapped right next to it - surau in particular
 * are usually mapped as their own building rather than as a station tag.
 *
 * Blocking - call off the main thread. Throws on network/HTTP failure.
 */
object PoiService {
    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    private const val USER_AGENT = "NavOS/0.1 (navigation app; contact: excellentmindblow@gmail.com)"
    const val RADIUS_METERS = 5000
    private const val MAX_PER_KIND = 60

    // How close a separately-mapped surau / toilet / charger has to be to
    // count as "at" the station.
    private const val SURAU_NEARBY_M = 150f
    private const val TOILET_NEARBY_M = 80f
    private const val CHARGER_NEARBY_M = 100f

    private val FUEL_FACILITIES = listOf("Toilet", "Surau", "Shop", "ATM", "EV charging", "Car wash", "Air pump", "24 hours")
    private val EV_FACILITIES = listOf("24 hours", "Toilet", "Surau", "Free to use")

    // OSM socket:* keys -> what drivers in Malaysia call the plug.
    private val SOCKET_NAMES =
        linkedMapOf(
            "type2_combo" to "CCS2",
            "chademo" to "CHAdeMO",
            "type2" to "Type 2",
            "type2_cable" to "Type 2 (cable)",
            "type1_combo" to "CCS1",
            "type1" to "Type 1",
            "tesla_supercharger" to "Tesla",
        )

    private class Element(
        val id: String,
        val lat: Double,
        val lon: Double,
        val tags: Map<String, String>,
    ) {
        fun tag(key: String): String? = tags[key]?.trim()?.takeIf { it.isNotEmpty() }

        fun yes(key: String): Boolean = tag(key)?.lowercase() == "yes"
    }

    @Throws(Exception::class)
    fun fetchNearby(
        lat: Double,
        lon: Double,
    ): List<Poi> {
        val r = RADIUS_METERS
        val rWide = RADIUS_METERS + 200 // so a surau/toilet just past the edge still counts for an edge station
        val query =
            "[out:json][timeout:25];(" +
                "nwr[\"amenity\"=\"fuel\"](around:$r,$lat,$lon);" +
                "nwr[\"amenity\"=\"charging_station\"](around:$r,$lat,$lon);" +
                "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:$rWide,$lat,$lon);" +
                "nwr[\"amenity\"=\"toilets\"](around:$rWide,$lat,$lon);" +
                ");out center tags;"

        val connection = URL(OVERPASS_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        try {
            connection.outputStream.use { out ->
                out.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Station lookup failed (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return buildPois(parseElements(body), lat, lon)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseElements(body: String): List<Element> {
        val array = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val out = mutableListOf<Element>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            // Nodes carry lat/lon directly; ways/relations (stations mapped
            // as an area) carry a "center" thanks to "out center".
            val center = obj.optJSONObject("center")
            val lat = if (obj.has("lat")) obj.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
            val lon = if (obj.has("lon")) obj.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
            if (lat.isNaN() || lon.isNaN()) continue

            val tagsObj = obj.optJSONObject("tags") ?: continue
            val tags = mutableMapOf<String, String>()
            val keys = tagsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                tags[key] = tagsObj.optString(key, "")
            }
            out.add(Element("${obj.optString("type")}/${obj.optLong("id")}", lat, lon, tags))
        }
        return out
    }

    private fun buildPois(
        elements: List<Element>,
        originLat: Double,
        originLon: Double,
    ): List<Poi> {
        val fuel = elements.filter { it.tag("amenity") == "fuel" }
        val chargers = elements.filter { it.tag("amenity") == "charging_station" }
        val suraus = elements.filter { it.tag("amenity") == "place_of_worship" }
        val toilets = elements.filter { it.tag("amenity") == "toilets" }

        fun Element.near(
            others: List<Element>,
            meters: Float,
        ): Boolean = others.any { it !== this && distance(lat, lon, it.lat, it.lon) <= meters }

        val fuelPois =
            fuel.map { e ->
                val listed =
                    mapOf(
                        "Toilet" to (e.tag("toilets").let { it != null && it.lowercase() != "no" } || e.near(toilets, TOILET_NEARBY_M)),
                        "Surau" to (e.yes("surau") || e.yes("prayer_room") || e.near(suraus, SURAU_NEARBY_M)),
                        "Shop" to (e.tag("shop").let { it != null && it.lowercase() != "no" }),
                        "ATM" to e.yes("atm"),
                        "EV charging" to (e.yes("fuel:electricity") || e.near(chargers, CHARGER_NEARBY_M)),
                        "Car wash" to e.yes("car_wash"),
                        "Air pump" to e.yes("compressed_air"),
                        "24 hours" to (e.tag("opening_hours") == "24/7"),
                    )
                Poi(
                    id = e.id,
                    kind = PoiKind.FUEL,
                    name = fuelName(e),
                    latitude = e.lat,
                    longitude = e.lon,
                    facilities = FUEL_FACILITIES.map { PoiFacility(it, listed[it] == true) },
                    spec = null,
                )
            }

        val evPois =
            chargers.map { e ->
                val listed =
                    mapOf(
                        "24 hours" to (e.tag("opening_hours") == "24/7"),
                        "Toilet" to (e.tag("toilets").let { it != null && it.lowercase() != "no" } || e.near(toilets, TOILET_NEARBY_M)),
                        "Surau" to e.near(suraus, SURAU_NEARBY_M),
                        "Free to use" to (e.tag("fee")?.lowercase() == "no"),
                    )
                Poi(
                    id = e.id,
                    kind = PoiKind.EV,
                    name = e.tag("name") ?: e.tag("operator") ?: e.tag("brand") ?: e.tag("network") ?: "EV charger",
                    latitude = e.lat,
                    longitude = e.lon,
                    facilities = EV_FACILITIES.map { PoiFacility(it, listed[it] == true) },
                    spec = evSpec(e),
                )
            }

        fun List<Poi>.nearestFirst() =
            sortedBy { distance(originLat, originLon, it.latitude, it.longitude) }.take(MAX_PER_KIND)

        return fuelPois.nearestFirst() + evPois.nearestFirst()
    }

    /** "Petronas Jalan Meru" style - brand in front unless the name
     * already contains it. */
    private fun fuelName(e: Element): String {
        val name = e.tag("name")
        val brand = e.tag("brand")
        return when {
            name != null && brand != null && !name.contains(brand, ignoreCase = true) -> "$brand $name"
            name != null -> name
            brand != null -> brand
            else -> "Petrol station"
        }
    }

    private fun evSpec(e: Element): String? {
        val plugs =
            SOCKET_NAMES.mapNotNull { (key, label) ->
                val count = e.tag("socket:$key") ?: return@mapNotNull null
                if (count.lowercase() == "no" || count == "0") return@mapNotNull null
                val output = e.tag("socket:$key:output")
                when {
                    output == null -> label
                    output.all { it.isDigit() || it == '.' } -> "$label ($output kW)"
                    else -> "$label ($output)"
                }
            }
        val lines = mutableListOf<String>()
        if (plugs.isNotEmpty()) lines.add("Plugs: " + plugs.joinToString(", "))
        e.tag("capacity")?.let { lines.add("Chargers: $it") }
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    private fun distance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0]
    }
}
