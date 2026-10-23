package com.excellentmindblow.navos

import android.content.Context
import android.util.Log
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Offline safety net: when a drive starts, the map tiles along the route are
 * downloaded in the background (MapLibre offline region), so a tunnel, a
 * hill or a dead zone mid-drive doesn't leave a blank map. Only the tiles
 * the route line crosses, zoom [MIN_ZOOM]-[MAX_ZOOM], first [MAX_KM] km -
 * a 30 km trip is about 2 MB. One region at a time; the previous drive's is
 * deleted first.
 */
class RouteOfflineCache(
    context: Context,
) {
    private val app = context.applicationContext
    private val manager by lazy { OfflineManager.getInstance(app) }
    private val pixelRatio = context.resources.displayMetrics.density

    /** Starts saving the map along [route] (main thread). */
    fun save(
        route: Route,
        styleUrl: String,
    ) {
        val line = corridor(route.geometry)
        if (line.size < 2) return
        val definition =
            OfflineGeometryRegionDefinition(
                styleUrl,
                LineString.fromLngLats(line.map { Point.fromLngLat(it.longitude, it.latitude) }),
                MIN_ZOOM,
                MAX_ZOOM,
                pixelRatio,
            )
        clearOld {
            manager.createOfflineRegion(
                definition,
                TAG_BYTES,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        offlineRegion.setObserver(
                            object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    if (status.isComplete) {
                                        Log.i(LOG, "route map saved: ${status.completedTileCount} tiles, ${status.completedResourceSize / 1024} KB")
                                        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    }
                                }

                                override fun onError(error: OfflineRegionError) {
                                    Log.w(LOG, "route map: ${error.reason} ${error.message}")
                                }

                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    Log.w(LOG, "route map: tile limit $limit reached")
                                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                }
                            },
                        )
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }

                    override fun onError(error: String) {
                        Log.w(LOG, "route map: $error")
                    }
                },
            )
        }
    }

    /** Deletes the previous drive's saved map (only NavOS route regions), then runs [then]. */
    private fun clearOld(then: () -> Unit) {
        manager.listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val old = offlineRegions.orEmpty().filter { it.metadata.contentEquals(TAG_BYTES) }
                    if (old.isEmpty()) {
                        then()
                        return
                    }
                    var left = old.size
                    for (r in old) {
                        r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        r.delete(
                            object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {
                                    if (--left == 0) then()
                                }

                                override fun onError(error: String) {
                                    if (--left == 0) then()
                                }
                            },
                        )
                    }
                }

                override fun onError(error: String) = then()
            },
        )
    }

    companion object {
        const val MIN_ZOOM = 11.0
        const val MAX_ZOOM = 15.0
        const val MAX_KM = 200.0
        private const val LOG = "NavOSOffline"
        private val TAG_BYTES = "navos-route".toByteArray()

        /** The route thinned to a point every ~150 m, first [MAX_KM] km. */
        internal fun corridor(points: List<RoutePoint>): List<RoutePoint> {
            if (points.size < 2) return points
            val out = arrayListOf(points[0])
            var total = 0.0
            var sinceLast = 0.0
            for (i in 1 until points.size) {
                val d = GeoUtils.distanceMeters(points[i - 1], points[i])
                total += d
                sinceLast += d
                if (sinceLast >= 150.0) {
                    out += points[i]
                    sinceLast = 0.0
                }
                if (total >= MAX_KM * 1000) break
            }
            if (out.last() != points.last() && total < MAX_KM * 1000) out += points.last()
            return out
        }
    }
}
