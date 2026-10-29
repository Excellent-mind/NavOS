package com.excellentmindblow.navos.traffic

import android.content.SharedPreferences
import android.location.Location
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Collects this drive's road speeds ([TrafficProbe]) and sends them every
 * minute - only if the driver agreed to share ([TrafficPrefs]). A new
 * random id per drive; nothing ties it to the phone or a person.
 * Main thread, except the uploads.
 */
class TrafficReporter(
    private val prefs: SharedPreferences,
) {
    private val io = Executors.newSingleThreadExecutor()
    private var session = newSession()
    private var probe: TrafficProbe? = null
    private var route: Route? = null
    private var track: RouteTrack? = null
    private var lastSeg = 0
    private var where: Map<String, DoubleArray> = emptyMap()
    private var lastUploadMs = 0L

    /** A new drive (new id) or a reroute ([sameDrive] keeps the id). */
    fun setRoute(
        newRoute: Route?,
        sameDrive: Boolean,
    ) {
        flush()
        if (!sameDrive) session = newSession()
        route = newRoute
        val nodes = newRoute?.nodes
        if (newRoute == null || nodes == null || newRoute.geometry.size < 2) {
            probe = null
            track = null
            return
        }
        val cum = DoubleArray(newRoute.geometry.size)
        for (i in 1 until cum.size) cum[i] = cum[i - 1] + GeoUtils.distanceMeters(newRoute.geometry[i - 1], newRoute.geometry[i])
        probe = TrafficProbe(nodes, cum)
        // Where each piece is and its normal speed, sent along with its live speed.
        val free = newRoute.freeFlowMs
        val g = newRoute.geometry
        val w = HashMap<String, DoubleArray>(nodes.size)
        if (free != null) {
            for (i in 0 until minOf(nodes.size - 1, free.size)) {
                if (nodes[i] <= 0 || nodes[i + 1] <= 0) continue
                w["${nodes[i]}-${nodes[i + 1]}"] = doubleArrayOf(g[i].latitude, g[i].longitude, g[i + 1].latitude, g[i + 1].longitude, free[i] * 3.6)
            }
        }
        where = w
        track = RouteTrack(newRoute.geometry)
        lastSeg = 0
    }

    /** A real GPS fix (not a tunnel estimate). */
    fun onFix(
        location: Location,
        nowMs: Long,
    ) {
        val p = probe ?: return
        val r = route ?: return
        val t = track ?: return
        if (!location.hasSpeed() || (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M)) return
        val snap = GeoUtils.snapToPolyline(RoutePoint(location.latitude, location.longitude), r.geometry, (lastSeg - 3).coerceAtLeast(0), SEARCH_SEGMENTS) ?: return
        if (snap.distanceMeters > MAX_OFF_LINE_M) return
        lastSeg = snap.segmentIndex
        p.onFix(t.progressOf(snap), location.speed.toDouble())
        if (nowMs - lastUploadMs >= UPLOAD_EVERY_MS) {
            lastUploadMs = nowMs
            flush()
        }
    }

    /** Sends what's queued (or drops it, if not sharing). */
    fun flush() {
        val p = probe ?: return
        if (!TrafficPrefs.sharing(prefs)) {
            p.drain(Int.MAX_VALUE)
            return
        }
        val batch = p.drain()
        if (batch.isEmpty()) return
        val id = session
        val w = where
        io.execute { runCatching { TrafficClient.sendProbes(id, batch, w) } }
    }

    /** End of the drive. What's still queued is dropped, not sent: it is the
     * stretch just before where the driver stopped. */
    fun stop() {
        probe?.drain(Int.MAX_VALUE)
        probe = null
        route = null
        track = null
        io.shutdown()
    }

    private fun newSession(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return String(CharArray(32) { chars[rnd.nextInt(chars.length)] })
    }

    private companion object {
        const val UPLOAD_EVERY_MS = 60_000L
        const val MAX_ACCURACY_M = 30f
        const val MAX_OFF_LINE_M = 25.0
        const val SEARCH_SEGMENTS = 80
    }
}
