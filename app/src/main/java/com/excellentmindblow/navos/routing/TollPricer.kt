package com.excellentmindblow.navos.routing

import org.json.JSONObject
import java.util.Locale

/** What the tolls on a route cost, from the LLM rate table. */
data class TollEstimate(
    /** Sum of the fares that could be priced, in sen (RM 1 = 100). */
    val totalSen: Int,
    /** Plazas passed that couldn't be priced (unknown name, or a trip that
     * enters a closed highway and leaves it somewhere we can't see). */
    val unpriced: Int,
)

/**
 * Prices a route's toll plazas with the fares published by Lembaga Lebuhraya
 * Malaysia (its "Kalkulator Tol"), bundled as assets/tolls/llm_rates.json.
 *
 * Two kinds of plaza: on an OPEN highway (most city expressways) each plaza
 * charges a flat fare. On a CLOSED one (PLUS, LPT, WCE...) you collect a
 * ticket / tap in at the entry plaza and pay at the exit plaza by distance,
 * so the fare depends on the pair. The route passes plazas in order, so an
 * entry is held until the next plaza on the same highway, which is the exit.
 *
 * Plazas are matched by name: OpenStreetMap's "Plaza Tol Gombak" and LLM's
 * "GOMBAK" are the same plaza once the words "Plaza Tol" and direction
 * markers like "(U)" are dropped.
 */
class TollPricer(
    private val plazas: List<Plaza>,
    private val openFares: Map<PlazaKey, IntArray>,
    private val pairFares: Map<Pair<PlazaKey, PlazaKey>, IntArray>,
) {
    /** How many plazas the table knows - a sanity check for downloaded tables. */
    val plazaCount: Int get() = plazas.size

    /** How many entry>exit fares the table knows. */
    val pairCount: Int get() = pairFares.size

    data class PlazaKey(
        val highway: String,
        val id: String,
    )

    data class Plaza(
        val key: PlazaKey,
        val name: String,
        val normalized: String,
    )

    /** Fares for vehicle [vehicleClass] (1 = car) of the plazas named [names], in route order. */
    fun price(
        names: List<String>,
        vehicleClass: Int = 1,
    ): TollEstimate {
        val ci = (vehicleClass - 1).coerceAtLeast(0)
        var total = 0
        var unpriced = 0
        var entry: List<PlazaKey>? = null
        for (raw in names) {
            val matches = match(raw)
            if (matches.isEmpty()) {
                unpriced++
                continue
            }
            // Leaving a closed highway we entered earlier?
            val pending = entry
            if (pending != null) {
                val fare =
                    pending.firstNotNullOfOrNull { from ->
                        matches.firstNotNullOfOrNull { to -> if (from.highway == to.highway) pairFares[from to to] else null }
                    }
                if (fare != null) {
                    total += fare.getOrElse(ci) { fare[0] }
                    entry = null
                    continue
                }
            }
            val open = matches.firstNotNullOfOrNull { openFares[it] }
            if (open != null) {
                total += open.getOrElse(ci) { open[0] }
                continue
            }
            val closed = matches.filter { k -> pairFares.keys.any { it.first == k } }
            if (closed.isNotEmpty()) {
                if (pending != null) unpriced++ // the earlier entry's exit was never seen
                entry = closed
            } else {
                unpriced++
            }
        }
        if (entry != null) unpriced++
        return TollEstimate(total, unpriced)
    }

    /** LLM plazas whose name is this OSM name. */
    internal fun match(osmName: String): List<PlazaKey> {
        val n = normalize(osmName)
        if (n.isEmpty()) return emptyList()
        val exact = plazas.filter { it.normalized == n }
        if (exact.isNotEmpty()) return exact.map { it.key }
        if (n.length < 5) return emptyList()
        return plazas.filter { it.normalized.length >= 5 && (n.contains(it.normalized) || it.normalized.contains(n)) }.map { it.key }
    }

    companion object {
        private val NOISE =
            Regex("\\b(PLAZA|TOL|TOLL|SELATAN|UTARA|TIMUR|BARAT|ARAH|ATAH|KE|BOUND|NB|SB|EB|WB|PLUS|ENTRY|EXIT|EXIT\\d+|MASUK|KELUAR)\\b")

        // Short forms on the map -> LLM's spelling.
        private val SPELLING = listOf(Regex("\\bSG\\b") to "SUNGAI", Regex("\\bAIR\\b") to "AYER", Regex("\\bJLN\\b") to "JALAN", Regex("\\bBDR\\b") to "BANDAR")
        private val DIRECTION = Regex("\\((U|S|T|B|N|E|W|UTARA|SELATAN|TIMUR|BARAT)\\)")

        fun normalize(name: String): String =
            name
                .uppercase(Locale.ROOT)
                .replace(DIRECTION, " ")
                .replace(Regex("[^A-Z0-9 ]"), " ")
                .let { t -> SPELLING.fold(t) { acc, (re, to) -> acc.replace(re, to) } }
                .replace(NOISE, " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Reads the bundled table (see the class doc for its source). */
        fun fromJson(json: JSONObject): TollPricer {
            val plazas = ArrayList<Plaza>()
            val open = HashMap<PlazaKey, IntArray>()
            val pairs = HashMap<Pair<PlazaKey, PlazaKey>, IntArray>()
            val highways = json.getJSONArray("highways")
            for (h in 0 until highways.length()) {
                val hw = highways.getJSONObject(h)
                val id = hw.getString("id")
                val list = hw.getJSONArray("plazas")
                for (i in 0 until list.length()) {
                    val p = list.getJSONArray(i)
                    val name = p.getString(1)
                    plazas += Plaza(PlazaKey(id, p.get(0).toString()), name, normalize(name))
                }
                hw.optJSONObject("open")?.let { o ->
                    for (k in o.keys()) open[PlazaKey(id, k)] = ints(o.getJSONArray(k))
                }
                hw.optJSONObject("pairs")?.let { o ->
                    for (k in o.keys()) {
                        val (a, b) = k.split('>')
                        pairs[PlazaKey(id, a) to PlazaKey(id, b)] = ints(o.getJSONArray(k))
                    }
                }
            }
            return TollPricer(plazas, open, pairs)
        }

        private fun ints(a: org.json.JSONArray): IntArray = IntArray(a.length()) { a.getInt(it) }

        /** "RM 12.40". */
        fun format(sen: Int): String = String.format(Locale.US, "RM %d.%02d", sen / 100, sen % 100)
    }
}
