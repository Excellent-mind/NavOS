package com.excellentmindblow.navos.signs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoutePoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.hypot

/**
 * Stands the route's road signs up on the tilted driving map.
 *
 * Each frame, the signs [SignVisibility] picks are placed at their spot on the
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

            // One board at a time, never right in front of the arrow (SignVisibility).
            val visible = SignVisibility.pick(signs, here)
            if (visible.isEmpty()) return

            val cam = m.cameraPosition
            val target = cam.target ?: return
            val centre = m.projection.toScreenLocation(target)
            val camera = SignCamera(centre.x.toDouble(), centre.y.toDouble(), height.toDouble(), cam.tilt)
            val standing = cam.tilt >= MIN_TILT_DEG

            for ((sign, _, opacity) in visible) {
                val bmp = bitmaps.getOrPut(sign.id) { p.paint(sign.content) }
                val spec = specOf(sign.content)
                val heightM = spec.widthM * bmp.height / bmp.width
                val heading = t.bearingAt((sign.atMeters - 15.0).coerceAtLeast(0.0), 30.0)
                val foot = GeoUtils.offset(t.pointAt(sign.atMeters), heading + 90.0, sign.sideMeters + spec.sideM)
                val left = GeoUtils.offset(foot, heading - 90.0, spec.widthM / 2)
                val right = GeoUtils.offset(foot, heading + 90.0, spec.widthM / 2)
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

                val alpha = (255 * opacity).toInt()
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

        private class Spec(
            val widthM: Double,
            val postM: Double,
            val sideM: Double,
        )

        /** World sizes. Real signs would be tiny on screen, so boards are
         * drawn larger than life (about 3.5x), the way landmarks are - but
         * kept small enough not to cover the road ahead. */
        private fun specOf(c: SignContent): Spec =
            when (c) {
                is SignContent.Direction -> Spec(if (c.panels.size > 1) 78.0 else 54.0, 18.0, 0.0)
                is SignContent.Lanes -> Spec((c.lanes.size * 18.0).coerceIn(36.0, 84.0), 18.0, 0.0)
                is SignContent.Roundabout -> Spec(54.0, 14.0, 0.0)
                is SignContent.Confirmation -> Spec(48.0, 14.0, -28.0)
                is SignContent.Toll -> Spec(60.0, 16.0, 0.0)
                is SignContent.RestStop -> Spec(54.0, 14.0, -28.0)
                is SignContent.Aes -> Spec(42.0, 12.0, -26.0)
                SignContent.TrafficLight -> Spec(14.0, 0.0, 0.0)
            }

        companion object {
            const val MIN_TILT_DEG = 20.0
            const val MAX_SCREEN_FRACTION = 0.6f
        }
    }
