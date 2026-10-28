package com.excellentmindblow.navos.traffic

import android.content.Context
import android.util.Log
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * PLUS Smart Lanes: stretches where the emergency lane is opened as an extra
 * lane at peak times. Known from three places:
 *  - assets/smartlanes.json: the lanes that open on a daily schedule
 *    (tools/smartlanes/build.py);
 *  - Supabase rpc smart_lanes_doc: festive / one-off activations (dated
 *    windows) and new stretches, downloaded every few hours;
 *  - drivers' "Smart lane open" road reports (see [RoadReports.Kind.SMART_LANE]).
 * The pure parts (open now? on my route?) are unit tested.
 */
object SmartLanes {
    data class Window(
        /** ISO days, 1 = Monday ... 7 = Sunday. */
        val days: Set<Int>,
        /** Minutes after midnight, Malaysia time; [toMin] not included. */
        val fromMin: Int,
        val toMin: Int,
    )

    data class Lane(
        val id: String,
        val name: String,
        val nameMs: String,
        val highway: String,
        val speedKmh: Int,
        /** The carriageway, in the direction the Smart Lane runs. */
        val line: List<RoutePoint>,
        val schedule: List<Window>,
        /** One-off activations, epoch ms [start, end). */
        val dated: List<LongRange>,
        /** False when the hours come from a single report and may be out of date. */
        val confirmed: Boolean,
    )

    /** A lane the route drives along: from [startM] to [endM] on the route. */
    data class OnRoute(
        val lane: Lane,
        val startM: Double,
        val endM: Double,
    )

    val MALAYSIA: TimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")

    /** Further than this from the route, the lane is on another carriageway. */
    private const val ON_ROUTE_M = 40.0

    /** ISO day of week (1 = Monday) and minute of the day at [epochMs], Malaysia time. */
    private fun dayAndMinute(epochMs: Long): Pair<Int, Int> {
        val c = Calendar.getInstance(MALAYSIA)
        c.timeInMillis = epochMs
        val iso = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        return iso to c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun scheduled(
        lane: Lane,
        epochMs: Long,
    ): Window? {
        val (day, minute) = dayAndMinute(epochMs)
        return lane.schedule.firstOrNull { day in it.days && minute >= it.fromMin && minute < it.toMin }
    }

    /** Open at [epochMs] by its schedule or a dated activation. */
    fun isOpen(
        lane: Lane,
        epochMs: Long,
    ): Boolean = lane.dated.any { epochMs in it } || scheduled(lane, epochMs) != null

    /** When the current opening ends, in minutes after midnight (Malaysia time), or null when not open by schedule / date. */
    fun openUntil(
        lane: Lane,
        epochMs: Long,
    ): Int? {
        lane.dated.firstOrNull { epochMs in it }?.let { return dayAndMinute(it.last + 1).second }
        return scheduled(lane, epochMs)?.toMin
    }

    /**
     * The lanes [track] drives along, in the lane's direction: both ends of the
     * lane lie on the route and the start comes first.
     */
    fun onRoute(
        track: RouteTrack,
        lanes: List<Lane>,
    ): List<OnRoute> {
        if (track.source.size < 2) return emptyList()
        return lanes
            .mapNotNull { lane ->
                if (lane.line.size < 2) return@mapNotNull null
                val a = GeoUtils.snapToPolyline(lane.line.first(), track.source) ?: return@mapNotNull null
                val b = GeoUtils.snapToPolyline(lane.line.last(), track.source) ?: return@mapNotNull null
                if (a.distanceMeters > ON_ROUTE_M || b.distanceMeters > ON_ROUTE_M) return@mapNotNull null
                val startM = track.progressOf(a)
                val endM = track.progressOf(b)
                if (endM - startM < 200.0) return@mapNotNull null // other direction, or barely touched
                OnRoute(lane, startM, endM)
            }.sortedBy { it.startM }
    }

    // ---- data -------------------------------------------------------------

    fun parse(text: String): List<Lane> {
        val arr = JSONObject(text).optJSONArray("lanes") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { lane(arr.getJSONObject(i)) }.getOrNull() }
    }

    private fun lane(o: JSONObject): Lane {
        val line = o.getJSONArray("line")
        val sched = o.optJSONArray("schedule") ?: JSONArray()
        val dated = o.optJSONArray("windows") ?: JSONArray()
        return Lane(
            id = o.getString("id"),
            name = o.optString("name"),
            nameMs = o.optString("name_ms", o.optString("name")),
            highway = o.optString("highway"),
            speedKmh = o.optInt("speed_kmh", 60),
            line = (0 until line.length()).map { line.getJSONArray(it).let { p -> RoutePoint(p.getDouble(1), p.getDouble(0)) } },
            schedule =
                (0 until sched.length()).map {
                    val w = sched.getJSONObject(it)
                    val days = w.getJSONArray("days")
                    Window((0 until days.length()).map { d -> days.getInt(d) }.toSet(), minuteOf(w.getString("from")), minuteOf(w.getString("to")))
                },
            dated =
                (0 until dated.length()).mapNotNull {
                    val w = dated.getJSONObject(it)
                    runCatching { utc(w.getString("from")) until utc(w.getString("to")) }.getOrNull()
                },
            confirmed = o.optBoolean("confirmed", true),
        )
    }

    /** "06:30" -> 390. */
    private fun minuteOf(hhmm: String): Int {
        val (h, m) = hhmm.split(':').map { it.trim().toInt() }
        return h * 60 + m
    }

    /** "2026-10-31T07:00:00Z" -> epoch ms. */
    private fun utc(iso: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)!!.time

    /** Bundled lanes, with downloaded ones replacing them by id. */
    fun merge(
        bundled: List<Lane>,
        remote: List<Lane>,
    ): List<Lane> {
        val byId = LinkedHashMap<String, Lane>()
        for (l in bundled) byId[l.id] = l
        for (l in remote) byId[l.id] = l
        return byId.values.toList()
    }

    // ---- loading ----------------------------------------------------------

    private const val TAG = "NavOSSmartLanes"
    private const val ASSET = "smartlanes.json"
    private const val CACHE = "smartlanes/remote.json"
    private const val REFRESH_MS = 3L * 60 * 60 * 1000
    private const val SUPABASE_URL = "https://mksfocirdngualkahuqi.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_L9X5RO2x2aVTICl-st9PbQ_gf_E87We"

    @Volatile
    var lanes: List<Lane> = emptyList()
        private set

    @Volatile
    private var loadedAt = 0L

    private val worker = Executors.newSingleThreadExecutor()

    /** Reads the lanes (bundled + last download) and refreshes the download when it is old. */
    fun load(context: Context) {
        val app = context.applicationContext
        worker.execute {
            val bundled = runCatching { app.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())
            val cache = File(app.filesDir, CACHE)
            var remote = runCatching { if (cache.exists()) parse(cache.readText()) else emptyList() }.getOrDefault(emptyList())
            lanes = merge(bundled, remote)
            if (System.currentTimeMillis() - maxOf(loadedAt, cache.lastModified()) < REFRESH_MS) return@execute
            runCatching {
                val text = download()
                remote = parse(text)
                cache.parentFile?.mkdirs()
                cache.writeText(text)
                loadedAt = System.currentTimeMillis()
                lanes = merge(bundled, remote)
            }.onFailure { Log.w(TAG, "smart lanes refresh: $it") }
        }
    }

    private fun download(): String {
        val conn = URL("$SUPABASE_URL/rest/v1/rpc/smart_lanes_doc").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write("{}".toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("smart lanes ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
