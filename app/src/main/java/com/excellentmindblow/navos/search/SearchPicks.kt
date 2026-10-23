package com.excellentmindblow.navos.search

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

/**
 * Search learns your choices: every place you pick from search is counted,
 * and the next time a search shows it, it moves up (picked often and lately
 * = higher). "Sun" finds *your* Sunway Pyramid first, not the one across
 * town. Stays on the phone; off (and wiped) with the "regular trips" switch.
 */
class SearchPicks(
    private val prefs: SharedPreferences,
) {
    data class Pick(
        val key: String,
        val count: Int,
        val lastMs: Long,
    )

    fun record(
        place: GeocodeResult,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!TripHabits(prefs).enabled) return
        val k = keyOf(place)
        val all = load().associateBy { it.key }.toMutableMap()
        val old = all[k]
        all[k] = Pick(k, (old?.count ?: 0) + 1, nowMs)
        val kept = all.values.sortedByDescending { weight(it, nowMs) }.take(MAX_PICKS)
        val a = JSONArray()
        for (p in kept) a.put(JSONObject().put("k", p.key).put("n", p.count).put("t", p.lastMs))
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    fun load(): List<Pick> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Pick(o.getString("k"), o.getInt("n"), o.getLong("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** [results] with the places you picked before (and that match [query]) moved to the top. */
    fun boost(
        results: List<GeocodeResult>,
        query: String,
        nowMs: Long = System.currentTimeMillis(),
    ): List<GeocodeResult> = boost(results, query, load(), nowMs)

    companion object {
        internal const val KEY = "search_picks"
        const val MAX_PICKS = 200
        private const val HALF_LIFE_DAYS = 60.0
        private const val MAX_BOOSTED = 3

        /** Same place = same name within ~100 m. */
        internal fun keyOf(p: GeocodeResult) = "${p.name.trim().lowercase()}|${"%.3f".format(java.util.Locale.ROOT, p.latitude)}|${"%.3f".format(java.util.Locale.ROOT, p.longitude)}"

        /** Picks fade: one pick 60 days ago counts half of one today. */
        internal fun weight(
            p: Pick,
            nowMs: Long,
        ): Double {
            val days = ((nowMs - p.lastMs).coerceAtLeast(0L)) / 86_400_000.0
            return p.count * 0.5.pow(days / HALF_LIFE_DAYS)
        }

        internal fun boost(
            results: List<GeocodeResult>,
            query: String,
            picks: List<Pick>,
            nowMs: Long,
        ): List<GeocodeResult> {
            val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            if (words.isEmpty() || picks.isEmpty() || results.size < 2) return results
            val byKey = picks.associateBy { it.key }
            val boosted =
                results
                    .mapNotNull { r ->
                        val p = byKey[keyOf(r)] ?: return@mapNotNull null
                        val hay = r.label.lowercase()
                        // Every word typed must be in it (the last one may be half typed).
                        if (!words.all { w -> hay.contains(w) }) return@mapNotNull null
                        r to weight(p, nowMs)
                    }.sortedByDescending { it.second }
                    .take(MAX_BOOSTED)
                    .map { it.first }
            if (boosted.isEmpty()) return results
            return boosted + results.filter { it !in boosted }
        }
    }
}
