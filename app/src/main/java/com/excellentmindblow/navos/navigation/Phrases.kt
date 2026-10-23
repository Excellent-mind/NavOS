package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.Lang
import com.excellentmindblow.navos.Units

/**
 * Every turn instruction and spoken sentence, in English and everyday
 * Malaysian Malay (the way people talk on the road, not formal Bahasa Baku).
 * Pure (the language is a parameter, defaulting to [Lang]), so it is unit tested.
 */
object Phrases {
    /** The instruction for one OSRM step, e.g. "Turn left onto Jalan Ampang" / "Belok kiri ke Jalan Ampang". */
    fun instruction(
        type: String,
        modifier: String?,
        roadName: String,
        roundaboutExit: Int,
        ms: Boolean = Lang.malay,
    ): String = if (ms) malay(type, modifier, roadName, roundaboutExit) else english(type, modifier, roadName, roundaboutExit)

    /** "In 300 meters, turn left onto ..." / "Dalam 300 meter, belok kiri ke ...". */
    fun inDistance(
        meters: Double,
        instruction: String,
        ms: Boolean = Lang.malay,
    ): String {
        val then = instruction.replaceFirstChar { it.lowercase() }
        return if (ms) "Lagi ${spokenDistance(meters, true)}, $then" else "In ${spokenDistance(meters, false)}, $then"
    }

    /** Spoken when a faster route is offered mid-drive. */
    fun fasterRoute(
        minutes: Int,
        ms: Boolean = Lang.malay,
    ): String = if (ms) "Jumpa laluan lebih cepat. Jimat $minutes minit." else "Faster route found. Saves $minutes minutes."

    /** "Turn left, then keep right" - two maneuvers close together. */
    fun then(
        first: String,
        second: String,
        ms: Boolean = Lang.malay,
    ): String {
        val b = second.replaceFirstChar { it.lowercase() }
        return if (ms) "$first, lepas tu $b" else "$first, then $b"
    }

    /** "Turn left, then your destination is ahead". */
    fun thenArrive(
        first: String,
        ms: Boolean = Lang.malay,
    ): String = if (ms) "$first, lepas tu sampai destinasi" else "$first, then your destination is ahead"

    fun arrived(ms: Boolean = Lang.malay): String = if (ms) "Dah sampai destinasi" else "You have arrived at your destination"

    fun stopName(
        number: Int,
        ms: Boolean = Lang.malay,
    ): String = if (ms) "hentian $number" else "stop $number"

    fun stopReached(
        label: String,
        moreStops: Boolean,
        ms: Boolean = Lang.malay,
    ): String =
        if (ms) {
            "Dah sampai $label. " + if (moreStops) "Sekarang gerak ke hentian seterusnya." else "Sekarang gerak ke destinasi."
        } else {
            "You have arrived at $label. " + if (moreStops) "Continuing to your next stop." else "Continuing to your destination."
        }

    /** How a distance is said aloud: "300 meters" / "300 meter", or feet / miles. */
    fun spokenDistance(
        meters: Double,
        ms: Boolean = Lang.malay,
    ): String {
        if (!Units.miles) return "${(Math.round(meters / 10) * 10).toInt()} ${if (ms) "meter" else "meters"}"
        val feet = meters * 3.28084
        return if (feet < 1000) {
            "${(Math.round(feet / 50.0) * 50).toInt().coerceAtLeast(50)} ${if (ms) "kaki" else "feet"}"
        } else {
            val mi = String.format(java.util.Locale.US, "%.1f", meters / 1609.344)
            if (ms) "$mi batu" else "$mi miles"
        }
    }

    private fun english(
        type: String,
        modifier: String?,
        roadName: String,
        exit: Int,
    ): String {
        val road = roadName.ifBlank { "the road" }
        val turn = (modifier?.let { EN_TURN[it] } ?: "continue").replaceFirstChar { it.uppercase() }
        return when (type) {
            "depart" -> if (roadName.isBlank()) "Head out" else "Head out onto $road"
            "arrive" -> "Arrive at your destination"
            "roundabout", "rotary" -> if (exit > 0) "At the roundabout, take exit $exit onto $road" else "Enter the roundabout"
            "merge" -> "Merge onto $road"
            "on ramp" -> "Take the ramp onto $road"
            "off ramp" -> "Take the exit onto $road"
            "fork" -> "Keep ${side(modifier) ?: "straight"} at the fork onto $road"
            "new name" -> "Continue onto $road"
            "continue" -> if (modifier == null || modifier == "straight") "Continue onto $road" else "$turn onto $road"
            else -> "$turn onto $road"
        }
    }

    private fun malay(
        type: String,
        modifier: String?,
        roadName: String,
        exit: Int,
    ): String {
        val onto = if (roadName.isBlank()) "" else " ke $roadName"
        val turn = (modifier?.let { MS_TURN[it] } ?: "terus").replaceFirstChar { it.uppercase() }
        return when (type) {
            "depart" -> if (roadName.isBlank()) "Jom gerak" else "Gerak$onto"
            "arrive" -> "Sampai destinasi"
            "roundabout", "rotary" -> if (exit > 0) "Masuk bulatan, keluar yang ${ordinal(exit)}$onto" else "Masuk bulatan"
            "merge" -> if (roadName.isBlank()) "Masuk jalan utama" else "Masuk$onto"
            "on ramp" -> if (roadName.isBlank()) "Masuk highway" else "Masuk$onto"
            "off ramp" -> if (roadName.isBlank()) "Keluar sini" else "Keluar$onto"
            "fork" ->
                when (side(modifier)) {
                    "left" -> "Ikut kiri$onto"
                    "right" -> "Ikut kanan$onto"
                    else -> "Jalan terus$onto"
                }
            "new name" -> "Terus$onto"
            "continue" -> if (modifier == null || modifier == "straight") "Terus$onto" else "$turn$onto"
            else -> "$turn$onto"
        }
    }

    private fun side(modifier: String?): String? =
        when {
            modifier == null -> null
            modifier.contains("left") -> "left"
            modifier.contains("right") -> "right"
            else -> null
        }

    /** "pertama", "kedua", ... */
    fun ordinal(n: Int): String =
        when (n) {
            1 -> "pertama"
            2 -> "kedua"
            3 -> "ketiga"
            4 -> "keempat"
            5 -> "kelima"
            6 -> "keenam"
            7 -> "ketujuh"
            8 -> "kelapan"
            else -> "ke-$n"
        }

    private val EN_TURN =
        mapOf(
            "uturn" to "make a U-turn",
            "sharp right" to "turn sharp right",
            "sharp left" to "turn sharp left",
            "right" to "turn right",
            "left" to "turn left",
            "slight right" to "bear right",
            "slight left" to "bear left",
            "straight" to "continue straight",
        )

    private val MS_TURN =
        mapOf(
            "uturn" to "buat U-turn",
            "sharp right" to "belok kanan tajam",
            "sharp left" to "belok kiri tajam",
            "right" to "belok kanan",
            "left" to "belok kiri",
            "slight right" to "ikut kanan",
            "slight left" to "ikut kiri",
            "straight" to "jalan terus",
        )
}
