package com.excellentmindblow.navos.traffic

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

/**
 * Road reports from drivers (accident, police, flood, hazard, Smart Lane open), anonymous:
 * sent to and read from the NavOS traffic server (see
 * server/traffic-worker). The pure parts - which reports are on the route
 * ahead, the box to ask for - are unit tested; the network calls block, so
 * call them off the main thread.
 */
object RoadReports {
    enum class Kind(
        val code: String,
    ) {
        ACCIDENT("accident"),
        POLICE("police"),
        FLOOD("flood"),
        HAZARD("hazard"),

        /** Traffic jam (stop-and-go), as Waze drivers report it. */
        JAM("jam"),

        /** A mobile speed trap (not a fixed AES camera - those are bundled, see SpeedCameras). */
        CAMERA("camera"),

        /** Road closed (works, accident, event). */
        CLOSED("closed"),

        /** A driver saw the PLUS Smart Lane (emergency lane) open - see SmartLanes. */
        SMART_LANE("smartlane"),
        ;

        val label: String
            get() =
                when (this) {
                    ACCIDENT -> tr("Accident", "Kemalangan")
                    POLICE -> tr("Police", "Polis")
                    FLOOD -> tr("Flood", "Banjir")
                    HAZARD -> tr("Hazard", "Bahaya")
                    JAM -> tr("Traffic jam", "Jem")
                    CAMERA -> tr("Speed trap", "Perangkap laju")
                    CLOSED -> tr("Road closed", "Jalan ditutup")
                    SMART_LANE -> tr("Smart lane open", "Smart lane dibuka")
                }

        /** What the voice says when one is ahead. */
        val spoken: String
            get() =
                when (this) {
                    ACCIDENT -> tr("Accident reported ahead", "Ada kemalangan di depan")
                    POLICE -> tr("Police reported ahead", "Ada polis di depan")
                    FLOOD -> tr("Flood reported ahead", "Ada banjir di depan")
                    HAZARD -> tr("Hazard reported ahead", "Ada bahaya di depan")
                    JAM -> tr("Traffic jam reported ahead", "Ada jem di depan")
                    CAMERA -> tr("Speed trap reported ahead", "Ada perangkap laju di depan")
                    CLOSED -> tr("Road closed ahead", "Jalan ditutup di depan")
                    SMART_LANE -> tr("Smart lane open ahead, you may use the left emergency lane", "Smart lane dibuka di depan, boleh guna lorong kecemasan kiri")
                }

        companion object {
            fun of(code: String) = entries.firstOrNull { it.code == code }
        }
    }

    data class Report(
        val id: Int,
        val kind: Kind,
        val latitude: Double,
        val longitude: Double,
        /** Direction the reporter was driving, -1 = unknown. */
        val heading: Int,
        val ageS: Int,
        val up: Int,
        val down: Int,
    )

    /** A report on the route, [atM] metres from its start. */
    data class OnRoute(
        val report: Report,
        val atM: Double,
    )

    /** Further off the route line than this, a report is on another road. */
    const val ON_ROUTE_M = 40.0

    /** Reports ask for the route this far ahead. */
    const val LOOK_AHEAD_M = 150_000.0
    private const val MAX_BOX_DEG = 2.4
    private const val PAD_DEG = 0.01

    /** Sends a report; returns its id (the one it confirmed, if already reported), or null. */
    @Throws(IOException::class)
    fun send(
        kind: Kind,
        here: RoutePoint,
        heading: Double?,
    ): Int? {
        val body =
            JSONObject()
                .put("k", kind.code)
                .put("lat", here.latitude)
                .put("lon", here.longitude)
        if (heading != null && !heading.isNaN()) body.put("h", ((heading.toInt() % 360) + 360) % 360)
        return TrafficClient.post("/report", body).optInt("id", -1).takeIf { it >= 0 }
    }

    /** "Still there" ([stillThere] = true) or "not there". */
    @Throws(IOException::class)
    fun vote(
        id: Int,
        stillThere: Boolean,
    ) {
        TrafficClient.post("/vote", JSONObject().put("id", id).put("v", if (stillThere) 1 else -1))
    }

    /** Live reports in [box] (south, west, north, east). */
    @Throws(IOException::class)
    fun fetch(box: DoubleArray): List<Report> {
        val body = TrafficClient.post("/reports", JSONObject().put("box", JSONArray(box.toList())))
        return parse(body.optJSONArray("r") ?: return emptyList())
    }

    internal fun parse(a: JSONArray): List<Report> =
        (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONArray(i) ?: return@mapNotNull null
            val kind = Kind.of(r.optString(1)) ?: return@mapNotNull null
            Report(r.optInt(0), kind, r.optDouble(2), r.optDouble(3), r.optInt(4, -1), r.optInt(5), r.optInt(6), r.optInt(7))
        }

    /** The box around the route from [fromM] to [LOOK_AHEAD_M] further (no bigger than the server allows). */
    fun boxAhead(
        track: RouteTrack,
        fromM: Double,
    ): DoubleArray? {
        val pts = track.source
        if (pts.size < 2) return null
        var s = Double.MAX_VALUE
        var w = Double.MAX_VALUE
        var n = -Double.MAX_VALUE
        var e = -Double.MAX_VALUE
        var any = false
        for (i in pts.indices) {
            val at = track.distanceAt(i)
            if (at < fromM - 1_000 && i < pts.size - 1 && track.distanceAt(i + 1) < fromM) continue
            if (at > fromM + LOOK_AHEAD_M) break
            val p = pts[i]
            val ns = minOf(s, p.latitude)
            val nw = minOf(w, p.longitude)
            val nn = maxOf(n, p.latitude)
            val ne = maxOf(e, p.longitude)
            if (any && (nn - ns > MAX_BOX_DEG - 2 * PAD_DEG || ne - nw > MAX_BOX_DEG - 2 * PAD_DEG)) break
            s = ns
            w = nw
            n = nn
            e = ne
            any = true
        }
        if (!any) return null
        return doubleArrayOf(s - PAD_DEG, w - PAD_DEG, n + PAD_DEG, e + PAD_DEG)
    }

    /**
     * The [reports] that lie on the route (within [ON_ROUTE_M] of the line)
     * and face the same way (a report made on the other side of a
     * dual carriageway isn't yours), sorted along the route.
     */
    fun onRoute(
        track: RouteTrack,
        reports: List<Report>,
    ): List<OnRoute> {
        if (track.source.size < 2) return emptyList()
        return reports
            .mapNotNull { r ->
                val snap = GeoUtils.snapToPolyline(RoutePoint(r.latitude, r.longitude), track.source) ?: return@mapNotNull null
                if (snap.distanceMeters > ON_ROUTE_M) return@mapNotNull null
                val atM = track.progressOf(snap)
                if (r.heading >= 0 && angleBetween(r.heading.toDouble(), track.bearingAt(atM, 30.0)) > 60.0) return@mapNotNull null
                OnRoute(r, atM)
            }.sortedBy { it.atM }
    }

    /** The next report at most [withinM] ahead of [progressM] (not one already behind). */
    fun nextAhead(
        onRoute: List<OnRoute>,
        progressM: Double,
        withinM: Double,
    ): OnRoute? = onRoute.firstOrNull { it.atM - progressM in -5.0..withinM }

    private fun angleBetween(
        a: Double,
        b: Double,
    ): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    /** "12 min ago · 3 still there" */
    fun detail(r: Report): String {
        val min = (r.ageS / 60).coerceAtLeast(1)
        val age = if (min < 60) tr("$min min ago", "$min minit lepas") else tr("${min / 60} h ago", "${min / 60} jam lepas")
        return if (r.up > 0) "$age · " + tr("${r.up} said still there", "${r.up} kata masih ada") else age
    }
}
