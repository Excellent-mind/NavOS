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
) {
    companion object {
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
        ): TurnSign {
            if (step == null) return TurnSign(null, emptyList(), fallback, SignColour.isExpressway(onRoadRef))
            var name = step.roadName.trim()
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
            return TurnSign(exit, refs, text, expressway)
        }
    }
}
