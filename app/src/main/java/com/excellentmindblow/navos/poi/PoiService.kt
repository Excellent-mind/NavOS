package com.excellentmindblow.navos.poi

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class PoiKind { FUEL, EV, PARKING, TRANSIT }

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
    /** Train stations only: the lines that stop there. */
    val lines: List<TransitLine> = emptyList(),
    /** Train stations only: a Park & Ride car park next to it. */
    val parkAndRide: ParkAndRide? = null,
)

/** A train line at a station: "KJ" / "LRT Kelana Jaya Line", in its colour (ARGB, null = unknown). */
data class TransitLine(
    val ref: String?,
    val name: String,
    val colour: Int?,
) {
    /** What goes on the badge: the line code ("KJ"), else a short name. */
    val badge: String get() = ref ?: name.substringBefore(":").substringBefore("(").trim()
}

data class ParkAndRide(
    val latitude: Double,
    val longitude: Double,
    val spaces: Int?,
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
    private val PARKING_FACILITIES = listOf("Free", "Paid", "Covered", "24 hours", "Disabled parking")
    const val PARKING_RADIUS_M = 800
    private const val MAX_PARKING = 25
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
        return buildPois(parseElements(overpass(query)), lat, lon)
    }

    /** Car parks open to the public within [radiusM] of [lat],[lon] (the
     * destination), nearest first. Blocking; throws on network failure. */
    fun fetchParking(
        lat: Double,
        lon: Double,
        radiusM: Int = PARKING_RADIUS_M,
    ): List<Poi> {
        val query =
            "[out:json][timeout:25];(" +
                "nwr[\"amenity\"=\"parking\"][\"access\"!~\"^(private|no|permit)$\"](around:$radiusM,$lat,$lon);" +
                ");out center tags;"
        return parseParking(overpass(query), lat, lon)
    }

    internal fun parseParking(
        body: String,
        originLat: Double,
        originLon: Double,
    ): List<Poi> =
        parseElements(body)
            .filter { it.tag("amenity") == "parking" && it.tag("parking") != "street_side" }
            .map { e ->
                val kind = e.tag("parking")
                val fee = e.tag("fee")?.lowercase()
                val listed =
                    mapOf(
                        "Free" to (fee == "no"),
                        "Paid" to (fee != null && fee != "no"),
                        "Covered" to (kind == "multi-storey" || kind == "underground" || e.yes("covered")),
                        "24 hours" to (e.tag("opening_hours") == "24/7"),
                        "Disabled parking" to ((e.tag("capacity:disabled")?.toIntOrNull() ?: 0) > 0 || e.yes("capacity:disabled")),
                    )
                val spaces = e.tag("capacity")?.toIntOrNull()?.takeIf { it > 0 }
                Poi(
                    id = e.id,
                    kind = PoiKind.PARKING,
                    name = e.tag("name") ?: e.tag("operator") ?: "Parking",
                    latitude = e.lat,
                    longitude = e.lon,
                    facilities = PARKING_FACILITIES.map { PoiFacility(it, listed[it] == true) },
                    spec = spaces?.let { "Spaces: $it" },
                )
            }.sortedBy {
                com.excellentmindblow.navos.navigation.GeoUtils.distanceMeters(
                    com.excellentmindblow.navos.routing.RoutePoint(originLat, originLon),
                    com.excellentmindblow.navos.routing.RoutePoint(it.latitude, it.longitude),
                )
            }.take(MAX_PARKING)

    const val TRANSIT_RADIUS_M = 1500
    private const val STOP_NEAR_STATION_M = 300.0
    private const val PARK_AND_RIDE_M = 400
    private const val MAX_STATIONS = 12
    private val TRAIN_ROUTES = Regex("^(subway|light_rail|monorail|train)$")

    /** Train stations (MRT, LRT, Monorail, KTM, ERL) within [radiusM] of the
     * destination, with the lines stopping there and any Park & Ride car
     * park next door. One Overpass request. Blocking. */
    fun fetchTransit(
        lat: Double,
        lon: Double,
        radiusM: Int = TRANSIT_RADIUS_M,
    ): List<Poi> {
        val query =
            "[out:json][timeout:25];(" +
                "nwr[\"railway\"=\"station\"](around:$radiusM,$lat,$lon);" +
                "nwr[\"public_transport\"=\"station\"][\"bus\"!=\"yes\"](around:$radiusM,$lat,$lon);" +
                ")->.st;.st out center tags;" +
                "(node(around.st:250)[\"public_transport\"=\"stop_position\"];node(around.st:250)[\"railway\"=\"stop\"];)->.stops;" +
                ".stops out skel;" +
                "rel(bn.stops)[\"route\"~\"^(subway|light_rail|monorail|train)$\"];out body;" +
                "nwr[\"amenity\"=\"parking\"][\"park_and_ride\"~\"yes|train\"](around.st:$PARK_AND_RIDE_M);out center tags;"
        return parseTransit(overpass(query), lat, lon)
    }

    /** Stations with a Park & Ride car park within [radiusM] of the route
     * ([points], thinned to at most ~120), with their lines. Blocking. */
    fun fetchParkAndRideAlong(
        points: List<com.excellentmindblow.navos.routing.RoutePoint>,
        radiusM: Int = 1500,
    ): List<Poi> {
        if (points.isEmpty()) return emptyList()
        val step = (points.size / 120).coerceAtLeast(1)
        val line = points.filterIndexed { i, _ -> i % step == 0 }.joinToString(",") { "%.5f,%.5f".format(java.util.Locale.ROOT, it.latitude, it.longitude) }
        val query =
            "[out:json][timeout:40];" +
                "nwr[\"amenity\"=\"parking\"][\"park_and_ride\"~\"yes|train\"](around:$radiusM,$line)->.pk;.pk out center tags;" +
                "(nwr[\"railway\"=\"station\"](around.pk:$PARK_AND_RIDE_M);" +
                "nwr[\"public_transport\"=\"station\"][\"bus\"!=\"yes\"](around.pk:$PARK_AND_RIDE_M);)->.st;.st out center tags;" +
                "(node(around.st:250)[\"public_transport\"=\"stop_position\"];node(around.st:250)[\"railway\"=\"stop\"];)->.stops;" +
                ".stops out skel;" +
                "rel(bn.stops)[\"route\"~\"^(subway|light_rail|monorail|train)$\"];out body;"
        val first = points.first()
        return parseTransit(overpass(query), first.latitude, first.longitude, limit = 40).filter { it.parkAndRide != null }
    }

    internal fun parseTransit(
        body: String,
        originLat: Double,
        originLon: Double,
        limit: Int = MAX_STATIONS,
    ): List<Poi> {
        val array = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        class Station(
            val id: String,
            val name: String,
            val lat: Double,
            val lon: Double,
        ) {
            val lines = LinkedHashMap<String, TransitLine>()
            var park: ParkAndRide? = null
        }
        val stations = ArrayList<Station>()
        val stops = HashMap<Long, Pair<Double, Double>>()
        val routes = ArrayList<org.json.JSONObject>()
        val parks = ArrayList<Element>()
        fun near(
            aLat: Double,
            aLon: Double,
            bLat: Double,
            bLon: Double,
        ) = com.excellentmindblow.navos.navigation.GeoUtils.distanceMeters(
            com.excellentmindblow.navos.routing.RoutePoint(aLat, aLon),
            com.excellentmindblow.navos.routing.RoutePoint(bLat, bLon),
        )
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val type = o.optString("type")
            val tags = o.optJSONObject("tags")
            val center = o.optJSONObject("center")
            val lat = if (o.has("lat")) o.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
            val lon = if (o.has("lon")) o.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
            when {
                type == "relation" -> if (tags != null && TRAIN_ROUTES.matches(tags.optString("route"))) routes += o
                tags == null && type == "node" -> if (!lat.isNaN()) stops[o.optLong("id")] = lat to lon
                tags == null || lat.isNaN() -> Unit
                tags.optString("amenity") == "parking" ->
                    parks += Element("$type/${o.optLong("id")}", lat, lon, tags.keys().asSequence().associateWith { tags.optString(it) })
                tags.optString("railway") == "station" || tags.optString("public_transport") == "station" -> {
                    val name = tags.optString("name")
                    // The same station is often mapped twice (a node and its building): keep one.
                    if (name.isNotBlank() && stations.none { it.name.equals(name, true) && near(it.lat, it.lon, lat, lon) < STOP_NEAR_STATION_M }) {
                        stations += Station("$type/${o.optLong("id")}", name, lat, lon)
                    }
                }
            }
        }
        for (r in routes) {
            val tags = r.optJSONObject("tags") ?: continue
            val name = tags.optString("name").ifBlank { tags.optString("ref") }
            if (name.isBlank()) continue
            val ref = tags.optString("ref").ifBlank { null }
            val colour = hexColour(tags.optString("colour"))
            val line = TransitLine(ref, name, colour)
            // Both directions of a line are separate routes: one badge per line.
            val key = (ref ?: line.badge).lowercase()
            val members = r.optJSONArray("members") ?: continue
            val at = HashSet<Station>()
            for (j in 0 until members.length()) {
                val m = members.optJSONObject(j) ?: continue
                if (m.optString("type") != "node") continue
                val p = stops[m.optLong("ref")] ?: continue
                stations.filter { near(it.lat, it.lon, p.first, p.second) < STOP_NEAR_STATION_M }.minByOrNull { near(it.lat, it.lon, p.first, p.second) }?.let { at += it }
            }
            for (st in at) st.lines.putIfAbsent(key, line)
        }
        for (p in parks) {
            val st = stations.minByOrNull { near(it.lat, it.lon, p.lat, p.lon) } ?: continue
            if (near(st.lat, st.lon, p.lat, p.lon) > PARK_AND_RIDE_M || st.park != null) continue
            st.park = ParkAndRide(p.lat, p.lon, p.tag("capacity")?.toIntOrNull()?.takeIf { it > 0 })
        }
        return stations
            .sortedBy { near(originLat, originLon, it.lat, it.lon) }
            .take(limit)
            .map { st ->
                Poi(
                    id = st.id,
                    kind = PoiKind.TRANSIT,
                    name = st.name,
                    latitude = st.lat,
                    longitude = st.lon,
                    facilities = emptyList(),
                    spec = null,
                    lines = st.lines.values.toList(),
                    parkAndRide = st.park,
                )
            }
    }

    /** "#E0115F" / "e0115f" / "#e15" -> opaque ARGB; null if not a hex colour. */
    internal fun hexColour(text: String): Int? {
        var h = text.trim().removePrefix("#")
        if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
        if (h.length != 6) return null
        val rgb = h.toLongOrNull(16) ?: return null
        return (0xFF000000L or rgb).toInt()
    }

    private fun overpass(query: String): String {
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
            return connection.inputStream.bufferedReader().use { it.readText() }
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
