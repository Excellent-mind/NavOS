package com.excellentmindblow.navos.search

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10

/**
 * Searches Overture Maps places (shops, workshops, restaurants... the many
 * places OpenStreetMap - and so Photon - doesn't have yet), cut into
 * 0.1 degree tiles (about 11 km) so the phone only loads where it is.
 *
 * Data: Overture Maps Foundation "places" theme, mostly Meta (CDLA-
 * Permissive-2.0), plus Foursquare (Apache-2.0), Microsoft and
 * AllThePlaces; confidence >= 0.7 only. Pure apart from the loader, so the
 * matching and ranking are unit tested.
 */
object PlaceTiles {
    const val CELL_DEG = 0.1

    /** One place. [key] is its name + address, lower-case and accent-free, for matching. */
    class Place(
        val name: String,
        val category: String,
        val latitude: Double,
        val longitude: Double,
        val address: String,
    ) {
        internal val nameKey = normalize(name)
        internal val nameTokens = nameKey.split(' ').filter { it.isNotEmpty() }
        internal val nameCompact = nameKey.replace(" ", "")
        internal val addressTokens = normalize(address).split(' ').filter { it.isNotEmpty() }
    }

    fun cellOf(
        lat: Double,
        lon: Double,
    ): String = "${floor(lat * 10).toInt()}_${floor(lon * 10).toInt()}"

    /** The tile under ([lat], [lon]) and the [ring] rings of tiles around it. */
    fun cellsAround(
        lat: Double,
        lon: Double,
        ring: Int = 1,
    ): List<String> {
        val cy = floor(lat * 10).toInt()
        val cx = floor(lon * 10).toInt()
        val out = ArrayList<String>()
        for (dy in -ring..ring) for (dx in -ring..ring) out += "${cy + dy}_${cx + dx}"
        return out
    }

    fun parse(json: String): List<Place> {
        val arr = JSONObject(json).optJSONArray("places") ?: return emptyList()
        val out = ArrayList<Place>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            out += Place(p.optString(0), p.optString(1), p.optDouble(2), p.optDouble(3), p.optString(4))
        }
        return out
    }

    /**
     * Places matching [query] near ([lat], [lon]), best first. Every word
     * typed must start a word of the place's name or address, and the first
     * word must be in the name ("mydin gombak" finds the Mydin in Gombak).
     */
    fun search(
        places: Iterable<Place>,
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 8,
    ): List<GeocodeResult> {
        val q = normalize(query)
        val words = q.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val compact = q.replace(" ", "")
        val here = RoutePoint(lat, lon)
        val scored = ArrayList<Pair<Double, Place>>()
        for (p in places) {
            // Typed without the apostrophe / space ("dominos", "lotuss"): match the name run together.
            val runTogether = compact.length >= 4 && p.nameCompact.startsWith(compact)
            if (!runTogether && p.nameTokens.none { it.startsWith(words[0]) }) continue
            var inName = if (runTogether) words.size else 1
            var ok = true
            for (w in if (runTogether) emptyList() else words.drop(1)) {
                when {
                    p.nameTokens.any { it.startsWith(w) } -> inName++
                    p.addressTokens.any { it.startsWith(w) } -> {}
                    else -> {
                        ok = false
                        break
                    }
                }
            }
            if (!ok) continue
            var score = 20.0 * inName / words.size
            if (p.nameKey == q) {
                score += 100.0
            } else if (p.nameKey.startsWith(q) || runTogether) {
                score += 60.0
            }
            val km = GeoUtils.distanceMeters(here, RoutePoint(p.latitude, p.longitude)) / 1000.0
            score -= 25.0 * log10(1.0 + km)
            scored += score to p
        }
        return scored
            .sortedByDescending { it.first }
            .take(limit)
            .map { (_, p) ->
                GeocodeResult(
                    label = if (p.address.isBlank()) p.name else "${p.name}, ${p.address}",
                    latitude = p.latitude,
                    longitude = p.longitude,
                    name = p.name,
                    address = p.address,
                    category = categoryLabel(p.category),
                )
            }
    }

    /** "automotive_service" -> "Automotive service". */
    internal fun categoryLabel(raw: String): String? =
        raw.takeIf { it.isNotBlank() }?.replace('_', ' ')?.replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Lower-case, accents and punctuation removed, single spaces ("Lotus's" -> "lotus s"). */
    internal fun normalize(s: String): String =
        Normalizer
            .normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
