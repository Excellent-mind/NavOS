package com.excellentmindblow.navos.signs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Stands the route's road signs up on the tilted driving map.
 *
 * Each frame, every sign within [VIEW_AHEAD_M] is placed at its spot on the
 * route: its two feet are projected by MapLibre (so they sit exactly on the
 * map), then [SignCamera] lifts the board to its height with the map's own
 * camera, and the sign's bitmap is stretched onto those four corners. The
 * board therefore leans and grows exactly like the map under it. On a flat
 * (2D) map a standing board would be edge-on, so it is drawn as an upright
 * card instead. Touches pass straight through to the map.
 */
class SignOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var map: MapLibreMap? = null
        private var track: RouteTrack? = null
        private var signs: List<RoadSign> = emptyList()

        /** Distance of the arrow from the start of the route; NaN when unknown. */
        var progress: () -> Double = { Double.NaN }

        private var night = false
        private var painter: SignPainter? = null
        private val bitmaps = HashMap<String, Bitmap>()
        private val matrix = Matrix()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
        private val src = FloatArray(8)
        private val visible = ArrayList<Pair<RoadSign, Double>>()

        init {
            isClickable = false
            isFocusable = false
            setWillNotDraw(false)
        }

        fun setRoute(
            routeTrack: RouteTrack?,
            routeSigns: List<RoadSign>,
        ) {
            track = routeTrack
            signs = routeSigns
            val keep = routeSigns.map { it.id }.toSet()
            bitmaps.keys.retainAll(keep)
            invalidate()
        }

        fun clear() = setRoute(null, emptyList())

        fun setNight(isNight: Boolean) {
            if (isNight == night && painter != null) return
            night = isNight
            painter = SignPainter(context, isNight)
            bitmaps.clear()
            postPaint.color = if (isNight) Color.rgb(0x3A, 0x3D, 0x40) else Color.rgb(0x8A, 0x8F, 0x94)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val m = map ?: return
            val t = track ?: return
            if (signs.isEmpty()) return
            val here = progress()
            if (here.isNaN()) return
            val p = painter ?: SignPainter(context, night).also { painter = it }

            visible.clear()
            for (s in signs) {
                val d = s.atMeters - here
                if (d > -PASSED_M && d < VIEW_AHEAD_M) visible.add(s to d)
            }
            if (visible.isEmpty()) return
            visible.sortByDescending { it.second } // far ones first, near ones on top

            val cam = m.cameraPosition
            val target = cam.target ?: return
            val centre = m.projection.toScreenLocation(target)
            val camera = SignCamera(centre.x.toDouble(), centre.y.toDouble(), height.toDouble(), cam.tilt)
            val standing = cam.tilt >= MIN_TILT_DEG

            for ((sign, d) in visible) {
                val bmp = bitmaps.getOrPut(sign.id) { p.paint(sign.content) }
                val spec = specOf(sign.content)
                val heightM = spec.widthM * bmp.height / bmp.width
                val heading = t.bearingAt((sign.atMeters - 15.0).coerceAtLeast(0.0), 30.0)
                val foot = offset(t.pointAt(sign.atMeters), heading + 90.0, sign.sideMeters + spec.sideM)
                val left = offset(foot, heading - 90.0, spec.widthM / 2)
                val right = offset(foot, heading + 90.0, spec.widthM / 2)
                val l = m.projection.toScreenLocation(LatLng(left.latitude, left.longitude))
                val r = m.projection.toScreenLocation(LatLng(right.latitude, right.longitude))

                val dst: FloatArray
                val feet: FloatArray?
                if (standing) {
                    dst = camera.quad(l.x.toDouble(), l.y.toDouble(), r.x.toDouble(), r.y.toDouble(), spec.widthM, spec.postM, spec.postM + heightM) ?: continue
                    feet = if (spec.postM > 0.0) floatArrayOf(l.x, l.y, r.x, r.y) else null
                } else {
                    // Flat map: an upright card standing on the sign's spot.
                    val w = hypot((r.x - l.x).toDouble(), (r.y - l.y).toDouble()).toFloat()
                    val h = w * bmp.height / bmp.width
                    val cx = (l.x + r.x) / 2
                    val bottom = (l.y + r.y) / 2 - w * (spec.postM / spec.widthM).toFloat()
                    dst = floatArrayOf(cx - w / 2, bottom - h, cx + w / 2, bottom - h, cx + w / 2, bottom, cx - w / 2, bottom)
                    feet = null
                }
                if (offScreen(dst)) continue
                clampSize(dst, feet)

                val alpha = alphaFor(d)
                if (feet != null) drawPosts(canvas, dst, feet, alpha)
                src[0] = 0f
                src[1] = 0f
                src[2] = bmp.width.toFloat()
                src[3] = 0f
                src[4] = bmp.width.toFloat()
                src[5] = bmp.height.toFloat()
                src[6] = 0f
                src[7] = bmp.height.toFloat()
                if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) continue
                paint.alpha = alpha
                canvas.save()
                canvas.concat(matrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
                canvas.restore()
            }
        }

        /** Two posts from the ground to the board's lower edge. */
        private fun drawPosts(
            canvas: Canvas,
            q: FloatArray,
            feet: FloatArray,
            alpha: Int,
        ) {
            val bw = hypot((q[4] - q[6]).toDouble(), (q[5] - q[7]).toDouble()).toFloat()
            postPaint.strokeWidth = (bw * 0.025f).coerceIn(2f, 14f)
            postPaint.alpha = alpha
            for (f in floatArrayOf(0.14f, 0.86f)) {
                val topX = q[6] + (q[4] - q[6]) * f
                val topY = q[7] + (q[5] - q[7]) * f
                val footX = feet[0] + (feet[2] - feet[0]) * f
                val footY = feet[1] + (feet[3] - feet[1]) * f
                canvas.drawLine(footX, footY, topX, topY, postPaint)
            }
        }

        /** Very close, a board could fill the screen: shrink it about its foot. */
        private fun clampSize(
            q: FloatArray,
            feet: FloatArray?,
        ) {
            val top = hypot((q[2] - q[0]).toDouble(), (q[3] - q[1]).toDouble()).toFloat()
            val maxW = width * MAX_SCREEN_FRACTION
            if (top <= maxW || top <= 0f) return
            val k = maxW / top
            val ax = (q[4] + q[6]) / 2
            val ay = feet?.let { (it[1] + it[3]) / 2 } ?: ((q[5] + q[7]) / 2)
            for (i in 0 until 4) {
                q[i * 2] = ax + (q[i * 2] - ax) * k
                q[i * 2 + 1] = ay + (q[i * 2 + 1] - ay) * k
            }
            if (feet != null) {
                feet[0] = ax + (feet[0] - ax) * k
                feet[2] = ax + (feet[2] - ax) * k
            }
        }

        private fun offScreen(q: FloatArray): Boolean {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in 0 until 4) {
                minX = minOf(minX, q[i * 2])
                maxX = maxOf(maxX, q[i * 2])
                minY = minOf(minY, q[i * 2 + 1])
                maxY = maxOf(maxY, q[i * 2 + 1])
            }
            return maxX < 0 || minX > width || maxY < 0 || minY > height
        }

        /** Fades in as it comes into range, out as you pass it. */
        private fun alphaFor(d: Double): Int {
            val inFade = ((VIEW_AHEAD_M - d) / FADE_IN_M).coerceIn(0.0, 1.0)
            val outFade = ((d + PASSED_M) / (PASSED_M + 40.0)).coerceIn(0.0, 1.0)
            return (255 * inFade * outFade).toInt()
        }

        private class Spec(
            val widthM: Double,
            val postM: Double,
            val sideM: Double,
        )

        /** World sizes. Real signs would be tiny on screen, so boards are
         * drawn about six times life size, the way landmarks are. */
        private fun specOf(c: SignContent): Spec =
            when (c) {
                is SignContent.Direction -> Spec(if (c.panels.size > 1) 130.0 else 90.0, 26.0, 0.0)
                is SignContent.Lanes -> Spec((c.lanes.size * 30.0).coerceIn(60.0, 140.0), 26.0, 0.0)
                is SignContent.Roundabout -> Spec(90.0, 18.0, 0.0)
                is SignContent.Confirmation -> Spec(80.0, 18.0, -40.0)
                is SignContent.Toll -> Spec(100.0, 22.0, 0.0)
                is SignContent.RestStop -> Spec(90.0, 18.0, -40.0)
                is SignContent.Aes -> Spec(70.0, 16.0, -36.0)
                SignContent.TrafficLight -> Spec(14.0, 0.0, 0.0)
            }

        private fun offset(
            p: RoutePoint,
            bearingDeg: Double,
            meters: Double,
        ): RoutePoint {
            if (meters == 0.0) return p
            val b = Math.toRadians(bearingDeg)
            val dLat = meters * cos(b) / M_PER_DEG
            val dLon = meters * sin(b) / (M_PER_DEG * cos(Math.toRadians(p.latitude)).coerceAtLeast(0.2))
            return RoutePoint(p.latitude + dLat, p.longitude + dLon)
        }

        companion object {
            const val VIEW_AHEAD_M = 1100.0
            const val FADE_IN_M = 200.0
            const val PASSED_M = 30.0
            const val MIN_TILT_DEG = 20.0
            const val MAX_SCREEN_FRACTION = 0.92f
            private const val M_PER_DEG = 111_195.0
        }
    }
