package com.excellentmindblow.navos.location

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import com.excellentmindblow.navos.navigation.RouteTrack
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One animation drives BOTH the driver's arrow and the camera.
 *
 * Each GPS fix just sets a new target; every display frame the position,
 * heading and zoom are advanced once and applied to the arrow and the
 * camera together, so they can never disagree.
 *
 * While driving a route (see [track]) the arrow moves by DISTANCE along the
 * route line instead of sliding in a straight line between two fixes, so
 * it stays exactly on the blue line through bends (a straight slide cuts
 * the inside of every corner). That same distance also gives, every frame:
 *  - the arrow's own heading: the local direction of the lane under it,
 *    corrected for the 3D tilt, so it turns with the line;
 *  - the camera heading: where the road is going a little further ahead,
 *    eased softly so the view swings into a bend instead of stepping;
 *  - the zoom: eased toward the target on a time constant (not per GPS
 *    fix), so it breathes in and out smoothly even while you are stopped.
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

    /** Compass "north" mode while driving: the 3D follow camera keeps
     * tracking the arrow but stays pointed north instead of turning with
     * the heading. */
    var northUp = false

    /** Compass "heading" mode on the plain map: while the camera follows
     * the arrow it also turns to the direction you are travelling. */
    var idleHeadingUp = false

    /** The route being driven. A different track (reroute) or null (not
     * driving) restarts the distance-based movement. */
    var track: RouteTrack? = null
        set(value) {
            if (field === value) return
            field = value
            prog = Double.NaN
            fromProg = Double.NaN
            toProg = Double.NaN
            laneBearing = Double.NaN
        }

    /** The direction the arrow is currently travelling (deg, 0..360). */
    fun currentBearing(): Double = ((bearing % 360.0) + 360.0) % 360.0

    private var lat = Double.NaN
    private var lng = 0.0
    private var bearing = 0.0
    private var zoom = Double.NaN
    private var targetZoom = Double.NaN
    private var speedMs = 0.0

    // Distance along the route (metres); NaN while not on the track.
    private var prog = Double.NaN
    private var fromProg = Double.NaN
    private var toProg = Double.NaN

    private var fromLat = 0.0
    private var fromLng = 0.0
    private var fromBearing = 0.0
    private var toLat = 0.0
    private var toLng = 0.0
    private var toBearing = 0.0

    // Arrow heading actually drawn (smoothed lane direction) and what was
    // last sent to the map after the tilt correction.
    private var laneBearing = Double.NaN
    private var shownBearing = 0.0
    private var lastFrameMs = 0L

    private var animator: ValueAnimator? = null
    private var lastFixAtMs = 0L
    private var holdCameraUntilMs = 0L

    private var blendFrom: CameraPosition? = null
    private var blendStartMs = 0L
    private var blendDurationMs = 1L

    private fun onTrack(): Boolean = track != null && !prog.isNaN()

    /** New target from a GPS fix. [newLat]/[newLng] are already snapped to
     * the route when driving; [progressM] is that snapped spot's distance
     * along [track] (NaN when not on the route). [newBearing] / [newZoom]
     * may be NaN to keep the current value. */
    fun pushFix(
        newLat: Double,
        newLng: Double,
        newBearing: Double,
        newZoom: Double,
        progressM: Double = Double.NaN,
        newSpeedMs: Double = 0.0,
    ) {
        val now = SystemClock.uptimeMillis()
        speedMs = newSpeedMs
        if (!newZoom.isNaN()) {
            targetZoom = newZoom
            if (zoom.isNaN()) zoom = newZoom
        }

        val usable = track != null && !progressM.isNaN()
        val jump =
            lat.isNaN() ||
                (usable && prog.isNaN()) ||
                (if (usable) abs(progressM - prog) > JUMP_METERS else metersBetween(lat, lng, newLat, newLng) > JUMP_METERS)
        if (jump) {
            lat = newLat
            lng = newLng
            prog = if (usable) progressM else Double.NaN
            fromProg = prog
            toProg = prog
            if (!newBearing.isNaN()) bearing = newBearing
            laneBearing = Double.NaN
            lastFixAtMs = now
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
            } else {
                settle()
            }
            return
        }

        val interval = (now - lastFixAtMs).coerceIn(MIN_ANIM_MS, MAX_ANIM_MS)
        lastFixAtMs = now

        fromLat = lat
        fromLng = lng
        fromBearing = bearing
        toLat = newLat
        toLng = newLng
        toBearing = if (newBearing.isNaN()) bearing else bearing + shortestDelta(bearing, newBearing)
        if (usable) {
            // Never slide backwards on GPS jitter.
            fromProg = prog
            toProg = max(progressM, prog)
        } else {
            prog = Double.NaN
            fromProg = Double.NaN
            toProg = Double.NaN
        }
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
        toLat = lat
        toLng = lng
        toBearing = bearing
        fromProg = prog
        toProg = prog
    }

    /** Frames normally only run for the second between GPS fixes. If the
     * zoom or heading is still easing when they stop (you came to a halt),
     * keep a few more frames going so it finishes instead of freezing
     * half-way. */
    private fun settle() {
        if (animator?.isRunning == true) return
        val zoomBusy = !zoom.isNaN() && !targetZoom.isNaN() && abs(targetZoom - zoom) > 0.015
        val bearingBusy = onTrack() && abs(shortestDelta(bearing, camTargetBearing())) > 0.4
        if (zoomBusy || bearingBusy) {
            holdTarget()
            start(SETTLE_CHUNK_MS)
        }
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
                    if (!fromProg.isNaN() && !toProg.isNaN() && track != null) {
                        prog = fromProg + (toProg - fromProg) * t
                        val p = track!!.pointAt(prog)
                        lat = p.latitude
                        lng = p.longitude
                    } else {
                        lat = fromLat + (toLat - fromLat) * t
                        lng = fromLng + (toLng - fromLng) * t
                        // On a route the heading comes from the track instead.
                        bearing = fromBearing + (toBearing - fromBearing) * t
                    }
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
                    } else {
                        animator = null
                        settle()
                    }
                }
            },
        )
        animator = a
        a.start()
    }

    private fun camTargetBearing(): Double {
        val tr = track ?: return bearing
        val lookahead = (CAM_LOOKAHEAD_BASE_M + speedMs * CAM_LOOKAHEAD_PER_MS).coerceIn(CAM_LOOKAHEAD_MIN_M, CAM_LOOKAHEAD_MAX_M)
        return tr.bearingAt(prog, lookahead)
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
        dt: Double,
        camBearing: Double?,
        camTilt: Double,
    ) {
        val tr = track
        val lane = if (tr != null && !prog.isNaN()) tr.bearingAt(prog, ARROW_LOOKAHEAD_M) else null
        val target = lane ?: bearing
        if (laneBearing.isNaN()) {
            laneBearing = target
        } else {
            val alpha = 1.0 - exp(-dt / ARROW_TURN_TAU_S)
            laneBearing += shortestDelta(laneBearing, target) * alpha
        }
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
        val dt = ((now - lastFrameMs).coerceIn(0L, 100L)) / 1000.0
        lastFrameMs = now

        // Soft zoom: ease toward the target on a time constant.
        if (!targetZoom.isNaN()) {
            zoom = if (zoom.isNaN()) targetZoom else zoom + (targetZoom - zoom) * (1.0 - exp(-dt / ZOOM_TAU_S))
        }
        // Soft camera heading on a route: swing toward where the road goes.
        if (onTrack()) {
            bearing += shortestDelta(bearing, camTargetBearing()) * (1.0 - exp(-dt / CAM_TURN_TAU_S))
        }

        val mode = followProvider()
        val frozen = now < holdCameraUntilMs
        val drive3d = mode == Follow.DRIVE_3D
        val turnWithHeading = mode == Follow.CENTER && idleHeadingUp

        if (frozen || !(drive3d || turnWithHeading)) {
            // Rotated together with the map: no perspective correction.
            updateArrowHeading(dt, null, 0.0)
            puckSource?.setGeoJson(puckFeature())
            if (frozen) return
            if (mode == Follow.CENTER) map.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
            return
        }

        // Where the camera should end up. Driving: the tilted follow view.
        // Plain map in heading mode: keep whatever zoom / tilt the person
        // has and only turn with the heading.
        val camHeading = if (drive3d && northUp) 0.0 else bearing
        var tLat = lat
        var tLng = lng
        var tZoom = if (drive3d && !zoom.isNaN()) zoom else map.cameraPosition.zoom
        var tTilt = if (drive3d) driveTilt else map.cameraPosition.tilt
        var tBearing = camHeading

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
                tBearing = from.bearing + shortestDelta(from.bearing, camHeading) * e
            }
        }

        updateArrowHeading(dt, tBearing, tTilt)
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
        const val SETTLE_CHUNK_MS = 120L
        const val JUMP_METERS = 250.0 // teleport / reroute: don't glide across the map
        const val ARROW_LOOKAHEAD_M = 6.0 // the arrow's own heading: the lane right under it
        const val ARROW_TURN_TAU_S = 0.07 // how quickly the arrow follows the lane direction
        const val CAM_LOOKAHEAD_BASE_M = 12.0 // camera heading looks further ahead, more so when fast
        const val CAM_LOOKAHEAD_PER_MS = 1.6
        const val CAM_LOOKAHEAD_MIN_M = 15.0
        const val CAM_LOOKAHEAD_MAX_M = 45.0
        const val CAM_TURN_TAU_S = 0.45 // camera heading easing: soft swing into bends
        const val ZOOM_TAU_S = 0.8 // zoom easing: soft in/out
    }
}
