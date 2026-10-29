package com.excellentmindblow.navos.signs

import com.excellentmindblow.navos.routing.RouteStep

/**
 * What the gantry sign says for the next turn, like a real JKR sign: the exit
 * number in its own tag, road numbers on shields, and where it goes ("Putrajaya
 * · Cyberjaya") - not a sentence like "Take the exit onto Exit 607 Putrajaya".
 * Pure, so it is unit tested.
 */
data class TurnSign(
    /** "607", "3305A", or null. */
    val exit: String?,
    /** Road numbers to show as shields ("E6", "FT29"), at most two. */
    val refs: List<String>,
    /** The big words: destinations or the road's name. */
    val text: String,
    /** Green JKR expressway sign (on or onto an E road), else blue. */
    val expressway: Boolean,
    /** The road you turn onto, when it has a name ("Jalan Ampang"); else the destinations. Big line of the banner. */
    val primary: String = text,
    /** Where it leads, shown small as "towards ..." when [primary] is a road name. */
    val towards: String? = null,
) {
    companion object {
        /** An unnamed piece at most this long counts as a link onto the next road. */
        const val LINK_M = 300.0
        private val UNNAMED = Regex("(?i)\\s+onto the road$")
        private val EXIT_IN_NAME = Regex("(?i)^\\s*(exit|keluar|susur keluar)\\s*([0-9]{2,4}[A-Za-z]?)\\s*[-:,]?\\s*")

        /**
         * [step] is the upcoming turn, [onRoadRef] the road number of the road
         * you're on now, [fallback] the plain instruction for when the map has
         * nothing better.
         */
        fun of(
            step: RouteStep?,
            onRoadRef: String?,
            fallback: String,
            /** The turn after [step]: an unnamed link that leads straight onto a named road takes that road's name. */
            next: RouteStep? = null,
        ): TurnSign {
            // "Bear left onto the road" (an unnamed road) reads as plain "Bear left".
            val fallback = fallback.replace(UNNAMED, "")
            if (step == null) return TurnSign(null, emptyList(), fallback, SignColour.isExpressway(onRoadRef))
            var name = step.roadName.trim()
            // A short unnamed piece (slip road, junction link) onto a named road: say the named road,
            // like other navigators do ("Turn left · Jalan Pembangunan", not "Turn left onto the road").
            if (name.isEmpty() && next != null && step.distanceMeters <= LINK_M && next.roadName.isNotBlank()) {
                name = next.roadName.trim()
            }
            var exit = step.exits?.trim()?.takeIf { it.isNotEmpty() }
            EXIT_IN_NAME.find(name)?.let { m ->
                if (exit == null) exit = m.groupValues[2]
                name = name.substring(m.range.last + 1).trim()
            }
            // OSM destination style: "E33: Kuantan, Genting Highlands; Gombak".
            val dest = step.destinations?.trim().orEmpty()
            val destRefs = if (':' in dest) dest.substringBefore(':').split(';', ',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
            val places =
                (if (':' in dest) dest.substringAfter(':') else dest)
                    .split(',', ';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(2)
            val refs = (destRefs + listOfNotNull(step.ref?.split(';')?.firstOrNull()?.trim())).filter { it.isNotEmpty() }.distinct().take(2)
            val text =
                when {
                    places.isNotEmpty() -> places.joinToString(" · ")
                    name.isNotEmpty() -> name
                    else -> fallback
                }
            val expressway = SignColour.isExpressway(onRoadRef) || refs.any { SignColour.isExpressway(it) }
            val placesText = places.joinToString(" · ").takeIf { it.isNotEmpty() }
            val primary = name.ifEmpty { placesText ?: fallback }
            val towards = if (name.isNotEmpty()) placesText else null
            return TurnSign(exit, refs, text, expressway, primary, towards)
        }
    }
}
