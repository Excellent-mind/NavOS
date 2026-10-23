package com.excellentmindblow.navos.poi

/**
 * The petrol companies in Malaysia NavOS can recognise, shown as a badge in
 * the company's colour with a short mark - never the company's logo, which
 * is a trademark. Recognised from the station name, which already carries
 * the OpenStreetMap brand ("Shell Jalan Ampang", see PoiService.fuelName).
 */
enum class FuelBrand(
    /** Short mark drawn on pins and badges. */
    val mark: String,
    /** Full company name, for screen readers. */
    val displayName: String,
    val fill: Int,
    val ink: Int,
) {
    PETRONAS("P", "Petronas", 0xFF00A19C.toInt(), 0xFFFFFFFF.toInt()),
    SHELL("S", "Shell", 0xFFFFD500.toInt(), 0xFFDD1D21.toInt()),
    PETRON("P", "Petron", 0xFF0B3D91.toInt(), 0xFFFFFFFF.toInt()),
    CALTEX("C", "Caltex", 0xFFE1251B.toInt(), 0xFFFFFFFF.toInt()),
    BHPETROL("BHP", "BHPetrol", 0xFFC8102E.toInt(), 0xFFFFC72C.toInt()),
    ;

    /** Map icon id for this brand's pin (registered by MapLayers). */
    val pinIconId: String get() = "navos-fuel-pin-${name.lowercase()}"

    companion object {
        /** The brand a station name belongs to, or null when it isn't one we know. */
        fun of(stationName: String?): FuelBrand? {
            val n = stationName?.lowercase() ?: return null
            return when {
                "petronas" in n -> PETRONAS
                Regex("\\bshell\\b").containsMatchIn(n) -> SHELL
                Regex("\\bpetron\\b").containsMatchIn(n) -> PETRON
                "caltex" in n -> CALTEX
                "bhpetrol" in n || Regex("\\bbhp\\b").containsMatchIn(n) -> BHPETROL
                else -> null
            }
        }
    }
}
