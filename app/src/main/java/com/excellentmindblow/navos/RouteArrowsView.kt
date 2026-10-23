package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.provider.Settings
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.navigation.ChevronPulse
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteCorner
import com.excellentmindblow.navos.navigation.RouteTrack
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Guide chevrons on the route line through every bend ahead - junction turns
 * and small curves alike - pointing the way the line goes.
 *
 * Each chevron is laid flat on the road: its three points are placed in
 * metres around the line and projected by MapLibre, so it tilts and shrinks
 * with the map exactly like the line it sits on. A pulse of light runs
 * through each corner's chevrons in driving order ([ChevronPulse]); with the
 * system's "Remove animations" setting the chevrons stay lit and still.
 * Drawn in the line's casing colour, so they read as part of the line.
 */
class RouteArrowsView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var map: MapLibreMap? = null
        private var track: RouteTrack? = null
        private var corners: List<RouteCorner> = emptyList()

        /** Distance of the arrow from the start of the route; NaN when unknown. */
        var progress: () -> Double = { Double.NaN }

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        private val path = Path()
        private val density = resources.displayMetrics.density
        private val ease: (Float) -> Float = { Motion.easeOut.getInterpolation(it) }

        init {
            isClickable = false
            isFocusable = false
            setWillNotDraw(false)
            refreshColor()
        }

        fun setRoute(
            routeTrack: RouteTrack?,
            routeCorners: List<RouteCorner>,
        ) {
            track = routeTrack
            corners = routeCorners
            invalidate()
        }

        fun clear() = setRoute(null, emptyList())

        /** Day / night: the line's casing colour flips with the map. */
        fun refreshColor() {
            paint.color = ContextCompat.getColor(context, R.color.sig_route_casing)
            invalidate()
        }

        private fun motionEnabled(): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

        override fun onDraw(canvas: Canvas) {
            val m = map ?: return
            val t = track ?: return
            if (corners.isEmpty()) return
            val here = progress()
            if (here.isNaN()) return
            val cam = m.cameraPosition
            val target = cam.target ?: return

            // Size the chevrons from the line's width at the arrow: metres
            // per pixel there, so a chevron is the same size on screen as the
            // line it sits in and shrinks with it into the distance.
            val proj = m.projection
            val c0 = proj.toScreenLocation(target)
            val ahead = GeoUtils.offset(com.excellentmindblow.navos.routing.RoutePoint(target.latitude, target.longitude), cam.bearing, 10.0)
            val c1 = proj.toScreenLocation(LatLng(ahead.latitude, ahead.longitude))
            val pxPerM = hypot((c1.x - c0.x).toDouble(), (c1.y - c0.y).toDouble()) / 10.0
            if (pxPerM <= 0.0) return
            val mPerPx = 1.0 / pxPerM
            val halfLen = HALF_LENGTH_DP * density * mPerPx
            val halfWidth = HALF_WIDTH_DP * density * mPerPx
            val spacing = (SPACING_DP * density * mPerPx).coerceAtLeast(halfLen * 3)
            val strokeDp = STROKE_DP * density

            val animate = motionEnabled()
            val now = SystemClock.uptimeMillis()
            var drewAny = false
            for (corner in corners) {
                if (corner.endM < here - 2.0) continue
                if (corner.startM > here + VIEW_AHEAD_M) break
                val span = corner.endM - corner.startM
                val n = ((span / spacing).roundToInt() + 1).coerceIn(MIN_CHEVRONS, MAX_CHEVRONS)
                val step = span / (n - 1)
                val distanceFade = ((VIEW_AHEAD_M - (corner.startM - here)) / FADE_IN_M).coerceIn(0.0, 1.0).toFloat()
                for (i in 0 until n) {
                    val at = corner.startM + i * step
                    // Chevrons the arrow has driven over fade out over a few metres.
                    val passFade = ((at - here + 2.0) / 6.0).coerceIn(0.0, 1.0).toFloat()
                    if (passFade <= 0f) continue
                    val glow = if (animate) ChevronPulse.intensity(now, i, ease) else 1f
                    val alpha = distanceFade * passFade * (BASE_ALPHA + (1f - BASE_ALPHA) * glow)
                    if (alpha <= 0.02f) continue
                    val scale = SCALE_REST + (1f - SCALE_REST) * glow
                    if (drawChevron(canvas, t, at, halfLen * scale, halfWidth * scale, strokeDp * scale, alpha)) drewAny = true
                }
            }
            // Keep the pulse running only while a chevron is on screen.
            if (drewAny && animate) postInvalidateOnAnimation()
            if (now - lastLogMs > 3_000) {
                lastLogMs = now
                Log.i("NavOSArrows", "at ${here.toInt()} m: ${corners.count { it.endM >= here - 2.0 && it.startM <= here + VIEW_AHEAD_M }} corners ahead, drawn=$drewAny, ${"%.2f".format(pxPerM)} px/m")
            }
        }

        private var lastLogMs = 0L

        private val tip = PointF()
        private val left = PointF()
        private val right = PointF()

        /** One chevron [at] metres along the route, pointing along it. */
        private fun drawChevron(
            canvas: Canvas,
            t: RouteTrack,
            at: Double,
            halfLen: Double,
            halfWidth: Double,
            strokePx: Float,
            alpha: Float,
        ): Boolean {
            val m = map ?: return false
            val centre = t.pointAt(at)
            val bearing = t.bearingAt((at - 2.0).coerceAtLeast(0.0), 4.0)
            val tipP = GeoUtils.offset(centre, bearing, halfLen)
            val back = GeoUtils.offset(centre, bearing, -halfLen)
            val leftP = GeoUtils.offset(back, bearing - 90.0, halfWidth)
            val rightP = GeoUtils.offset(back, bearing + 90.0, halfWidth)
            val proj = m.projection
            project(proj.toScreenLocation(LatLng(tipP.latitude, tipP.longitude)), tip)
            project(proj.toScreenLocation(LatLng(leftP.latitude, leftP.longitude)), left)
            project(proj.toScreenLocation(LatLng(rightP.latitude, rightP.longitude)), right)
            if (offScreen(tip) && offScreen(left) && offScreen(right)) return false
            // Stroke width follows the chevron's own foreshortening.
            val local = hypot((left.x - right.x).toDouble(), (left.y - right.y).toDouble()).toFloat()
            val full = (HALF_WIDTH_DP * 2 * density).toFloat()
            paint.strokeWidth = (strokePx * (local / full)).coerceIn(1f, strokePx * 1.6f)
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            path.reset()
            path.moveTo(left.x, left.y)
            path.lineTo(tip.x, tip.y)
            path.lineTo(right.x, right.y)
            canvas.drawPath(path, paint)
            return true
        }

        private fun project(
            src: PointF,
            dst: PointF,
        ) {
            dst.set(src.x, src.y)
        }

        private fun offScreen(p: PointF): Boolean = p.x < -40 || p.y < -40 || p.x > width + 40 || p.y > height + 40

        companion object {
            /** Chevron size at the arrow, in dp - fits inside the ~18 dp line. */
            const val HALF_LENGTH_DP = 3.5
            const val HALF_WIDTH_DP = 5.5
            const val STROKE_DP = 2.6f
            const val SPACING_DP = 22.0

            const val MIN_CHEVRONS = 3
            const val MAX_CHEVRONS = 9

            /** Resting brightness between pulses, and resting size. Never scale from 0. */
            const val BASE_ALPHA = 0.55f
            const val SCALE_REST = 0.9f

            const val VIEW_AHEAD_M = 700.0
            const val FADE_IN_M = 120.0
        }
    }
