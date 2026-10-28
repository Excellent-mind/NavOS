package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Self-drawn compass: a light circle with a thin ring and a needle (red =
 * north, grey = south). Only the needle turns - the circle stays put - and
 * it is drawn entirely inside the view, so nothing can be clipped. Light
 * circle on purpose: it reads on both the dark and the light map.
 * Tap it to switch between north-up and heading-up (blue ring).
 */
class CompassView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        /** Direction of north on screen, in degrees clockwise (= -map bearing). */
        var northAngle: Float = 0f
            set(value) {
                field = value
                invalidate()
            }

        /** True while the map turns with your heading (ring turns blue);
         * false in north-up mode (navy ring). */
        var headingMode: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private val density = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val shadow =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(4f * density, 0f, 1.5f * density, Color.argb(110, 0, 0, 0))
            }
        private val ring =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#15181C")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            }
        private val north = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5484D") }
        private val south = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA5B1") }
        private val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15181C") }
        private val northPath = Path()
        private val southPath = Path()

        init {
            // Needed for the soft shadow behind the circle.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) / 2f - 5f * density
            if (radius <= 0f) return

            canvas.drawCircle(cx, cy, radius, shadow)
            canvas.drawCircle(cx, cy, radius, fill)
            ring.color = Color.parseColor(if (headingMode) "#2F7BFF" else "#15181C")
            ring.strokeWidth = (if (headingMode) 3f else 1.5f) * density
            canvas.drawCircle(cx, cy, radius, ring)

            val length = radius * 0.72f
            val half = radius * 0.30f
            canvas.save()
            canvas.rotate(northAngle, cx, cy)
            northPath.reset()
            northPath.moveTo(cx, cy - length)
            northPath.lineTo(cx + half, cy)
            northPath.lineTo(cx - half, cy)
            northPath.close()
            canvas.drawPath(northPath, north)
            southPath.reset()
            southPath.moveTo(cx, cy + length)
            southPath.lineTo(cx + half, cy)
            southPath.lineTo(cx - half, cy)
            southPath.close()
            canvas.drawPath(southPath, south)
            canvas.drawCircle(cx, cy, 2.2f * density, hub)
            canvas.restore()
        }
    }
