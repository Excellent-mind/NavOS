package com.excellentmindblow.navos.location

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One animation drives BOTH the driver's arrow and the camera.
 *
 * Before, the arrow was eased by MapLibre's location component while the
 * camera was eased by a separate 350 ms animation fired per GPS fix, so
 * the two moved on different clocks and visibly drifted apart. Here each
 * GPS fix just sets a new target; every display frame the position,
 * heading and zoom are interpolated once and applied to the arrow and the
 * camera together, so they can never disagree.
 *
 * Dynamic arrow bearing: the camera heading is deliberately smooth and
 * looks ahead, but the arrow itself is rotated every frame to the local
 * direction of the blue lane under it ([laneBearingProvider]), then
 * corrected for the 3D tilt so it lies along the lane the way the lane
 * looks on screen. Through a bend the arrow therefore turns with the line
 * instead of staying fixed upright while the road curves away.
 */
class DriveFollower(
    private val mapProvider: () -> MapLibreMap?,
    private val followProvider: () -> Follow,
) {
    enum class Follow { OFF, CENTER, DRIVE_3D }

    /** The GeoJSON source behind the arrow layer (re-set after a restyle). */
    var puckSource: GeoJsonSource? = null
        set(value) {
            field = value
            redrawPuck()
        }

    var driveTilt = 55.0

    /** Returns the direction (deg, 0 = north) of the route lane at a
     * position, or null when the position is not on the route. */
    var laneBearingProvider: ((Double, Double) -> Double?)? = null

    private var lat = Double.NaN
    private var lng = 0.0
    private var bearing = 0.0
    private var zoom = Double.NaN

    private var fromLat = 0.0
    private var fromLng = 0.0
    private var fromBearing = 0.0
    private var fromZoom = Double.NaN
    private var toLat = 0.0
    private var toLng = 0.0
    private var toBearing = 0.0
    private var toZoom = Double.NaN

    // Arrow heading actually drawn (smoothed lane direction) and what was
    // last sent to the map after the tilt correction.
    private var laneBearing = Double.NaN
    private var lastLaneMs = 0L
    private var shownBearing = 0.0

    private var animator: ValueAnimator? = null
    private var lastFixAtMs = 0L
    private var holdCameraUntilMs = 0L

    private var blendFrom: CameraPosition? = null
    private var blendStartMs = 0L
    private var blendDurationMs = 1L

    /** New target from a GPS fix (already snapped to the route if driving).
     * [newBearing] / [newZoom] may be NaN to keep the current value. */
    fun pushFix(
        newLat: Double,
        newLng: Double,
        newBearing: Double,
        newZoom: Double,
    ) {
        val now = SystemClock.uptimeMillis()
        if (lat.isNaN() || metersBetween(lat, lng, newLat, newLng) > JUMP_METERS) {
            lat = newLat
            lng = newLng
            if (!newBearing.isNaN()) bearing = newBearing
            if (!newZoom.isNaN()) zoom = newZoom
            lastFixAtMs = now
            laneBearing = Double.NaN
            animator?.let {
                it.removeAllListeners()
                it.cancel()
            }
            render()
            // A camera glide is in progress: keep frames running until it ends.
            val remaining = blendStartMs + blendDurationMs - now
            if (blendFrom != null && remaining > 16) {
                holdTarget()
                start(remaining)
            }
            return
        }
        val interval = (now - lastFixAtMs).coerceIn(MIN_ANIM_MS, MAX_ANIM_MS)
        lastFixAtMs = now

        // Idle fixes carry no zoom (NaN); the first drive fix must adopt
        // its zoom outright, otherwise zoom stays NaN and the drive zoom
        // is never applied.
        if (zoom.isNaN() && !newZoom.isNaN()) zoom = newZoom
        fromLat = lat
        fromLng = lng
        fromBearing = bearing
        fromZoom = zoom
        toLat = newLat
        toLng = newLng
        toBearing = if (newBearing.isNaN()) bearing else bearing + shortestDelta(bearing, newBearing)
        toZoom = if (newZoom.isNaN()) zoom else newZoom
        start(interval)
    }

    /** Eases the camera from wherever it is now onto the follow target
     * (Position button / entering navigation) - blended inside the same
     * frame loop so the arrow and camera still move together. */
    fun beginCameraBlend(durationMs: Long) {
        val cam = mapProvider()?.cameraPosition ?: return
        blendFrom = cam
        blendStartMs = SystemClock.uptimeMillis()
        blendDurationMs = durationMs.coerceAtLeast(1L)
        if (animator?.isRunning != true) {
            holdTarget()
            start(blendDurationMs)
        }
    }

    /** Stops frame-driven camera moves for a while, so a deliberate
     * easeCamera() elsewhere isn't cancelled by the next frame. */
    fun holdCamera(durationMs: Long) {
        holdCameraUntilMs = SystemClock.uptimeMillis() + durationMs
    }

    fun redrawPuck() {
        if (lat.isNaN()) return
        puckSource?.setGeoJson(puckFeature())
    }

    /** Forget the smoothed arrow heading (navigation started / ended). */
    fun resetArrowHeading() {
        laneBearing = Double.NaN
    }

    fun stop() {
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
    }

    private fun holdTarget() {
        fromLat = lat
        fromLng = lng
        fromBearing = bearing
        fromZoom = zoom
        toLat = lat
        toLng = lng
        toBearing = bearing
        toZoom = zoom
    }

    private fun start(durationMs: Long) {
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        val a =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val t = (it.animatedValue as Float).toDouble()
                    lat = fromLat + (toLat - fromLat) * t
                    lng = fromLng + (toLng - fromLng) * t
                    bearing = fromBearing + (toBearing - fromBearing) * t
                    if (!fromZoom.isNaN() && !toZoom.isNaN()) zoom = fromZoom + (toZoom - fromZoom) * t
                    render()
                }
            }
        a.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== a) return
                    // Keep frames running until an in-progress camera blend is done.
                    val remaining = blendStartMs + blendDurationMs - SystemClock.uptimeMillis()
                    if (blendFrom != null && remaining > 16) {
                        holdTarget()
                        start(remaining)
                    }
                }
            },
        )
        animator = a
        a.start()
    }

    private fun puckFeature(): Feature =
        Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addNumberProperty("bearing", ((shownBearing % 360.0) + 360.0) % 360.0)
        }

    /** Works out the heading to draw the arrow with for this frame.
     * [camBearing] / [camTilt] are the camera's heading and tilt when the
     * frame follows in 3D (the arrow is then corrected for perspective),
     * or null when the arrow is simply rotated with the map. */
    private fun updateArrowHeading(
        now: Long,
        camBearing: Double?,
        camTilt: Double,
    ) {
        val lane = laneBearingProvider?.invoke(lat, lng)
        val target = lane ?: bearing
        if (laneBearing.isNaN()) {
            laneBearing = target
        } else {
            val dt = ((now - lastLaneMs).coerceIn(0L, 250L)) / 1000.0
            val alpha = 1.0 - exp(-dt / ARROW_TURN_TAU_S)
            laneBearing += shortestDelta(laneBearing, target) * alpha
        }
        lastLaneMs = now
        laneBearing = ((laneBearing % 360.0) + 360.0) % 360.0

        shownBearing =
            if (camBearing == null || lane == null) {
                laneBearing
            } else {
                // A ground direction at angle d from the camera heading is
                // foreshortened along the view axis by cos(tilt), so on
                // screen it leans further from vertical than d itself.
                val d = Math.toRadians(shortestDelta(camBearing, laneBearing))
                val screen = Math.toDegrees(atan2(sin(d), cos(d) * cos(Math.toRadians(camTilt))))
                camBearing + screen
            }
    }

    private fun render() {
        val map = mapProvider() ?: return
        if (lat.isNaN()) return

        val now = SystemClock.uptimeMillis()
        val mode = followProvider()
        val frozen = now < holdCameraUntilMs

        if (mode != Follow.DRIVE_3D || frozen) {
            // Rotated together with the map: no perspective correction.
            updateArrowHeading(now, null, 0.0)
            puckSource?.setGeoJson(puckFeature())
            if (frozen) return
            when (mode) {
                Follow.CENTER -> map.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
                else -> Unit
            }
            return
        }

        var tLat = lat
        var tLng = lng
        var tZoom = if (zoom.isNaN()) map.cameraPosition.zoom else zoom
        var tTilt = driveTilt
        var tBearing = bearing

        val from = blendFrom
        if (from != null) {
            val raw = (now - blendStartMs).toDouble() / blendDurationMs
            if (raw >= 1.0) {
                blendFrom = null
            } else {
                val e = 1.0 - (1.0 - raw).pow(3) // ease-out: moves fast right away, then settles softly
                val fromLat = from.target?.latitude ?: lat
                val fromLng = from.target?.longitude ?: lng
                tLat = fromLat + (lat - fromLat) * e
                tLng = fromLng + (lng - fromLng) * e
                tZoom = from.zoom + (tZoom - from.zoom) * e
                tTilt = from.tilt + (tTilt - from.tilt) * e
                tBearing = from.bearing + shortestDelta(from.bearing, bearing) * e
            }
        }

        updateArrowHeading(now, tBearing, tTilt)
        puckSource?.setGeoJson(puckFeature())

        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(tLat, tLng))
                    .zoom(tZoom)
                    .tilt(tTilt)
                    .bearing(tBearing)
                    .build(),
            ),
        )
    }

    private fun shortestDelta(
        from: Double,
        to: Double,
    ): Double {
        var d = ((to - from) % 360.0 + 360.0) % 360.0
        if (d > 180.0) d -= 360.0
        return d
    }

    private fun metersBetween(
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double,
    ): Double {
        val dy = (bLat - aLat) * 111_320.0
        val dx = (bLng - aLng) * 111_320.0 * cos(Math.toRadians(aLat))
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val MIN_ANIM_MS = 400L
        const val MAX_ANIM_MS = 1500L
        const val ARROW_TURN_TAU_S = 0.07 // how quickly the arrow follows the lane direction
        const val JUMP_METERS = 250.0 // teleport / reroute: don't glide across the map
    }
}
