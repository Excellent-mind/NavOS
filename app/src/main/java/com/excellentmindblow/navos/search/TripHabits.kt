package com.excellentmindblow.navos.search

import android.content.SharedPreferences
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Learns where the driver goes at what time ("the office on weekday
 * mornings, the gym on Tuesday evenings") from the drives they start, and
 * guesses the trip now - so Home can offer it before they type anything.
 * Everything stays on the phone (app preferences); nothing is sent.
 */
class TripHabits(
    private val prefs: SharedPreferences,
) {
    /** One drive that was started. [minute]: minute of the day; [day]: days since 1970. */
    data class Trip(
        val place: GeocodeResult,
        val weekend: Boolean,
        val minute: Int,
        val day: Long,
    )

    fun record(
        place: GeocodeResult,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (!enabled) return
        val (weekend, minute, day) = whenOf(nowMs)
        val all = (load() + Trip(place, weekend, minute, day)).takeLast(MAX_TRIPS)
        val array = JSONArray()
        for (t in all) {
            array.put(
                JSONObject()
                    .put("label", t.place.label)
                    .put("name", t.place.name)
                    .put("address", t.place.address)
                    .put("lat", t.place.latitude)
                    .put("lon", t.place.longitude)
                    .put("we", t.weekend)
                    .put("min", t.minute)
                    .put("day", t.day),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun load(): List<Trip> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Trip(
                    GeocodeResult(o.getString("label"), o.getDouble("lat"), o.getDouble("lon"), o.optString("name"), o.optString("address")),
                    o.getBoolean("we"),
                    o.getInt("min"),
                    o.getLong("day"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    /** The driver lets NavOS learn their regular trips (Settings). On by default. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) clear()
        }

    /** The likely trip right now from [here], or null when nothing stands out. */
    fun predict(
        here: RoutePoint?,
        nowMs: Long = System.currentTimeMillis(),
    ): GeocodeResult? {
        if (!enabled) return null
        val (weekend, minute, day) = whenOf(nowMs)
        return Model.predict(load(), weekend, minute, day, here)
    }

    /** The pure part, unit tested. */
    object Model {
        /** Trips to places closer than this together are one place. */
        const val SAME_PLACE_M = 200.0

        /** Already this close to it: no point suggesting it. */
        const val ALREADY_THERE_M = 1000.0

        /** How sure it must be: about two matching trips at this time of day. */
        const val MIN_SCORE = 1.5

        fun predict(
            trips: List<Trip>,
            weekend: Boolean,
            minute: Int,
            today: Long,
            here: RoutePoint?,
        ): GeocodeResult? {
            val clusters = ArrayList<MutableList<Trip>>()
            for (t in trips) {
                val p = RoutePoint(t.place.latitude, t.place.longitude)
                val c = clusters.firstOrNull { GeoUtils.distanceMeters(RoutePoint(it[0].place.latitude, it[0].place.longitude), p) < SAME_PLACE_M }
                if (c != null) c += t else clusters += mutableListOf(t)
            }
            var best: Pair<GeocodeResult, Double>? = null
            for (c in clusters) {
                val newest = c.maxBy { it.day }.place
                if (here != null && GeoUtils.distanceMeters(here, RoutePoint(newest.latitude, newest.longitude)) < ALREADY_THERE_M) continue
                val score =
                    c.sumOf { t ->
                        val dMin = abs(t.minute - minute).let { minOf(it, 1440 - it) }
                        val time = exp(-0.5 * (dMin / 60.0).pow(2)) // within about an hour
                        val dayType = if (t.weekend == weekend) 1.0 else 0.3
                        val recency = 0.98.pow((today - t.day).coerceAtLeast(0).toDouble())
                        time * dayType * recency
                    }
                if (score >= MIN_SCORE && (best == null || score > best.second)) best = newest to score
            }
            return best?.first
        }
    }

    private fun whenOf(ms: Long): Triple<Boolean, Int, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val day = (ms + cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 86_400_000L
        return Triple(weekend, minute, day)
    }

    private companion object {
        const val KEY = "trip_habits"
        const val KEY_ENABLED = "trip_habits_on"
        const val MAX_TRIPS = 300
    }
}
