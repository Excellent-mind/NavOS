package com.excellentmindblow.navos.search

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The destinations picked most recently, newest first, kept as a JSON list
 * in the app's preferences. Picking a place again moves it to the top
 * instead of adding a duplicate.
 */
class RecentPlaces(
    private val prefs: SharedPreferences,
) {
    fun load(): List<GeocodeResult> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val label = o.getString("label")
                GeocodeResult(
                    label = label,
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    name = o.optString("name").ifBlank { label.substringBefore(",").trim() },
                    address = o.optString("address"),
                    category = o.optString("category").ifBlank { null },
                    isAddress = o.optBoolean("isAddress"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(result: GeocodeResult) {
        val updated =
            (listOf(result) + load().filter { it.label != result.label })
                .take(MAX_STORED)
        val array = JSONArray()
        for (r in updated) {
            array.put(
                JSONObject()
                    .put("label", r.label)
                    .put("lat", r.latitude)
                    .put("lon", r.longitude)
                    .put("name", r.name)
                    .put("address", r.address)
                    .put("category", r.category)
                    .put("isAddress", r.isAddress),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "recent_places"
        const val MAX_STORED = 10
        const val MAX_SHOWN = 5
    }
}
