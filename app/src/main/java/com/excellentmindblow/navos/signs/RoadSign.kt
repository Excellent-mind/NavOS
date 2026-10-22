package com.excellentmindblow.navos.signs

/** Which way a sign's arrow points. Gantry style: STRAIGHT points down at the lane. */
enum class SignArrow { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN }

/** A small white symbol in front of a place name. */
enum class SignSymbol { AIRPORT, HOSPITAL, PORT, BORDER }

/** One line on a sign: route-number shields, then a place name. A tourist
 * place gets the brown inset; a symbol shows before the name. */
data class SignLine(
    val text: String,
    val shields: List<String> = emptyList(),
    val tourist: Boolean = false,
    val symbol: SignSymbol? = null,
)

/** One column of a direction sign. The driver's way is [lit]; the rest fade. */
data class SignPanel(
    val lines: List<SignLine>,
    val arrow: SignArrow,
    val lit: Boolean,
    /** Green (expressway) rather than blue. */
    val expressway: Boolean,
)

/** One lane on a lane gantry. */
data class SignLane(
    /** OSRM lane words: "left", "straight", "slight right"... */
    val indications: List<String>,
    val lit: Boolean,
)

/** What a sign shows. */
sealed class SignContent {
    /** Direction / exit sign; one panel is a plain board, several a split gantry. */
    data class Direction(
        val exitNumber: String?,
        val panels: List<SignPanel>,
    ) : SignContent()

    /** One panel per lane, your lanes lit. */
    data class Lanes(
        val lanes: List<SignLane>,
        val label: SignLine?,
        val expressway: Boolean,
    ) : SignContent()

    /** Roundabout diagram. Angles in degrees: 0 = ahead, 90 = right, 180 = where you come from. */
    data class Roundabout(
        val exitAngle: Double,
        val otherExitAngles: List<Double>,
        val label: SignLine,
    ) : SignContent()

    /** "Bentong 58 km" list, after you join a road. */
    data class Confirmation(
        val shield: String?,
        val rows: List<Pair<String, Double>>,
        val expressway: Boolean,
    ) : SignContent()

    data class Toll(
        val name: String,
    ) : SignContent()

    /** R&R (with services) or a plain lay-by, [distanceMeters] ahead of the sign. */
    data class RestStop(
        val name: String,
        val services: Boolean,
        val distanceMeters: Double,
    ) : SignContent()

    /** AES speed camera warning; [limit] in km/h when mapped. */
    data class Aes(
        val limit: Int?,
    ) : SignContent()

    /** A 3D traffic light. Its colour is unknown, so no lamp is lit. */
    object TrafficLight : SignContent()
}

/**
 * A sign placed on the route: it stands [atMeters] from the start of the
 * route, [sideMeters] off the line (+ right, - left; 0 = over the road) and
 * faces drivers coming along it.
 */
data class RoadSign(
    val id: String,
    val atMeters: Double,
    val content: SignContent,
    val sideMeters: Double = 0.0,
)
