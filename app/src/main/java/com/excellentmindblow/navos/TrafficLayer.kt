package com.excellentmindblow.navos

import android.os.Handler
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
        handler.post(poll)
    }

    fun stop() {
        handler.removeCallbacks(poll)
        token++
        route = null
        runs = emptyList()
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
            val live = runCatching { TrafficClient.flow(pieces) }.getOrNull() ?: return@execute
            val found = TrafficFlow.runs(nodes, free, live, from)
            handler.post {
                if (t != token || route !== r) return@post
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
