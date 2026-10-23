package com.excellentmindblow.navos

import android.os.Handler
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.traffic.TrafficClient
import com.excellentmindblow.navos.traffic.TrafficFlow
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live traffic on the route while driving: every [POLL_MS] asks the
 * traffic server how fast other drivers are going on the road ahead and
 * paints slow parts amber and jams red (layer from [MapLayers]). Only what
 * is ahead of the car is drawn, so the colour never trails behind it.
 * Main thread, except the network call.
 */
class TrafficLayer(
    private val handler: Handler,
    /** The route point the car is at now. */
    private val currentIndex: () -> Int,
) {
    var source: GeoJsonSource? = null
        set(value) {
            field = value
            draw(force = true)
        }

    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var route: Route? = null
    private var runs: List<TrafficFlow.Run> = emptyList()
    private var drawnFrom = -1
    private var token = 0
    private var cumM = DoubleArray(0)
    private var live: Map<String, Double> = emptyMap()

    /** Extra time the slow / jammed road still ahead costs (seconds), for the ETA. */
    val delayAheadSeconds: Double
        get() {
            val r = route ?: return 0.0
            val nodes = r.nodes ?: return 0.0
            val free = r.freeFlowMs ?: return 0.0
            if (live.isEmpty() || cumM.size != nodes.size) return 0.0
            return TrafficFlow.delaySeconds(nodes, free, cumM, live, currentIndex())
        }

    private val poll =
        object : Runnable {
            override fun run() {
                fetch()
                handler.postDelayed(this, POLL_MS)
            }
        }

    fun start(newRoute: Route) {
        stop()
        if (newRoute.nodes == null || newRoute.freeFlowMs == null) return
        route = newRoute
        cumM = DoubleArray(newRoute.geometry.size)
        for (i in 1 until cumM.size) cumM[i] = cumM[i - 1] + GeoUtils.distanceMeters(newRoute.geometry[i - 1], newRoute.geometry[i])
        handler.post(poll)
    }

    fun stop() {
        handler.removeCallbacks(poll)
        token++
        route = null
        runs = emptyList()
        live = emptyMap()
        draw(force = true)
    }

    /** The car moved on: drop the colour behind it. */
    fun onProgress() {
        if (runs.isNotEmpty() && currentIndex() != drawnFrom) draw(force = false)
    }

    fun shutdown() {
        stop()
        io.shutdownNow()
    }

    private fun fetch() {
        val r = route ?: return
        val nodes = r.nodes ?: return
        val free = r.freeFlowMs ?: return
        val from = currentIndex()
        val pieces = TrafficFlow.piecesAhead(nodes, from)
        if (pieces.isEmpty()) return
        val t = token
        io.execute {
            val got = runCatching { TrafficClient.flow(pieces) }.getOrNull() ?: return@execute
            val found = TrafficFlow.runs(nodes, free, got, from)
            handler.post {
                if (t != token || route !== r) return@post
                live = got
                runs = found
                draw(force = true)
            }
        }
    }

    private fun draw(force: Boolean) {
        val src = source ?: return
        val geometry = route?.geometry
        val from = currentIndex()
        if (!force && from == drawnFrom) return
        drawnFrom = from
        if (geometry == null || runs.isEmpty()) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features = ArrayList<Feature>()
        for (run in runs) {
            val a = maxOf(run.from, from)
            val b = minOf(run.to, geometry.size - 1)
            if (b - a < 1) continue
            val pts = (a..b).map { Point.fromLngLat(geometry[it].longitude, geometry[it].latitude) }
            features +=
                Feature.fromGeometry(LineString.fromLngLats(pts)).apply {
                    addStringProperty(TRAFFIC_LEVEL_PROPERTY, if (run.level == TrafficFlow.Level.JAM) "jam" else "slow")
                }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private companion object {
        const val POLL_MS = 90_000L
    }
}
