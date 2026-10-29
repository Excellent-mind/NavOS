package com.excellentmindblow.navos.signs

/**
 * Which JKR sign colour a road gets. Malaysian expressways carry E route codes
 * (E1 PLUS, E33 DUKE, ...) and are signed green; federal, state and town roads
 * are signed blue.
 */
object SignColour {
    private val EXPRESSWAY = Regex("^E\\s?\\d+[A-Z]?$")

    /** True for an OSM / OSRM ref such as "E33", "E 1" or "FT2;E33". */
    fun isExpressway(ref: String?): Boolean =
        ref
            ?.split(';', ',', '/')
            ?.any { EXPRESSWAY.matches(it.trim().uppercase()) }
            ?: false
}
