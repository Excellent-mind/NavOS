package com.excellentmindblow.navos.search

/**
 * What kind of place a search hit is, from its OpenStreetMap tag
 * (Photon's osm_key / osm_value): a short label for the result row
 * ("Shopping mall", "Petrol") and whether it is an address (a house or a
 * road) rather than a named place - the Places / Addresses filter.
 */
object PlaceCategory {
    private val LABELS =
        mapOf(
            "mall" to "Shopping mall",
            "department_store" to "Shopping mall",
            "supermarket" to "Supermarket",
            "convenience" to "Shop",
            "fuel" to "Petrol",
            "charging_station" to "EV charging",
            "restaurant" to "Restaurant",
            "fast_food" to "Restaurant",
            "food_court" to "Food court",
            "cafe" to "Cafe",
            "hospital" to "Hospital",
            "clinic" to "Clinic",
            "pharmacy" to "Pharmacy",
            "school" to "School",
            "university" to "University",
            "college" to "College",
            "hotel" to "Hotel",
            "parking" to "Parking",
            "place_of_worship" to "Place of worship",
            "bank" to "Bank",
            "atm" to "ATM",
            "police" to "Police",
            "toll_booth" to "Toll plaza",
            "aerodrome" to "Airport",
            "terminal" to "Airport terminal",
            "park" to "Park",
            "attraction" to "Attraction",
            "museum" to "Museum",
            "stadium" to "Stadium",
        )

    private val STATION_KEYS = setOf("railway", "public_transport")
    private val AREA_VALUES = setOf("city", "town", "village", "suburb", "neighbourhood", "quarter", "hamlet", "state", "county", "district")

    /** A short label for the row, or null when the tag says nothing useful. */
    fun label(
        osmKey: String?,
        osmValue: String?,
    ): String? {
        if (osmKey == null || osmValue == null) return null
        LABELS[osmValue]?.let { return it }
        return when {
            osmKey in STATION_KEYS && (osmValue == "station" || osmValue == "halt" || osmValue == "stop") -> "Train station"
            osmKey == "highway" && osmValue == "bus_stop" -> "Bus stop"
            osmKey == "highway" -> "Road"
            osmKey == "place" && osmValue in AREA_VALUES -> "Area"
            osmKey == "place" && osmValue == "house" -> "Address"
            osmKey == "building" -> "Building"
            osmValue == "yes" -> null
            else -> osmValue.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    /** True for a house number or a road - what the Addresses filter keeps. */
    fun isAddress(
        osmKey: String?,
        osmValue: String?,
        hasHouseNumber: Boolean,
        hasName: Boolean,
    ): Boolean =
        hasHouseNumber && !hasName ||
            osmKey == "highway" && osmValue != "bus_stop" ||
            osmKey == "place" && osmValue == "house" ||
            osmKey == "building" && !hasName
}
