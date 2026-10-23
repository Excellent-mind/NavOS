package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.navigation.TripStrip
import com.excellentmindblow.navos.poi.FuelBrand

/**
 * Draws a [TripStrip.Strip]: the trip as one straight line from you (left) to
 * the destination (right), black where traffic is clear, amber where slow,
 * red where jammed; tolls, R&Rs, petrol / EV stations and cameras above it at
 * their place, traffic lights as ticks below. [highlight] lights up one kind
 * and dims the rest. [mini]: just the line, for the other routes' cards.
 */
class TripStripView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var strip: TripStrip.Strip? = null
            set(value) {
                field = value
                contentDescription = value?.let { describe(it) }
                invalidate()
            }

        /** A kind to light up, or [LIGHTS] for the traffic lights; null = all equal. */
        var highlight: Any? = null
            set(value) {
                field = value
                invalidate()
            }

        var mini = false
            set(value) {
                field = value
                requestLayout()
            }

        private val density = resources.displayMetrics.density
        private fun dp(v: Float) = v * density

        private fun color(res: Int) = ContextCompat.getColor(context, res)

        private val line = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val icons = HashMap<Int, Drawable?>()
        private val badges = HashMap<FuelBrand, Bitmap>()

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val h = dp(if (mini) 6f else 60f).toInt()
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(h, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val s = strip ?: return
            if (s.lengthM <= 0) return
            val marker = dp(MARKER_DP)
            val pad = if (mini) 0f else marker / 2
            val w = width - 2 * pad
            fun x(m: Double) = pad + (m / s.lengthM).toFloat().coerceIn(0f, 1f) * w
            val lineH = dp(if (mini) 4f else 8f)
            val lineTop = if (mini) (height - lineH) / 2 else marker + dp(8f)
            val dimmed = highlight != null

            // The line, then slow / jammed stretches over it.
            line.color = if (mini) color(R.color.sig_text_quiet) else color(R.color.sig_route)
            rect.set(0f, lineTop, width.toFloat(), lineTop + lineH)
            canvas.drawRoundRect(rect, lineH / 2, lineH / 2, line)
            for (t in s.traffic) {
                paint.color = color(if (t.jam) R.color.sig_limit else R.color.sig_caution)
                val left = if (mini) (t.fromM / s.lengthM).toFloat() * width else x(t.fromM)
                val right = if (mini) (t.toM / s.lengthM).toFloat() * width else x(t.toM)
                canvas.drawRect(left, lineTop, maxOf(right, left + dp(3f)), lineTop + lineH, paint)
            }
            if (mini) return

            // You (ring) and the destination (dot).
            val cy = lineTop + lineH / 2
            paint.color = color(R.color.sig_sheet)
            canvas.drawCircle(dp(8f), cy, dp(8f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4f)
            paint.color = color(R.color.sig_route)
            canvas.drawCircle(dp(8f), cy, dp(6f), paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(width - dp(10f), cy, dp(10f), paint)
            paint.color = color(R.color.sig_sheet)
            canvas.drawCircle(width - dp(10f), cy, dp(4f), paint)

            // Traffic lights: ticks under the line.
            val lightsOn = highlight == LIGHTS
            paint.color = color(if (lightsOn) R.color.sig_text else R.color.sig_text_quiet)
            paint.alpha = if (dimmed && !lightsOn) DIM_ALPHA else 255
            val tickW = dp(if (lightsOn) 3f else 2f)
            for (m in s.lightsM) {
                val cx = x(m)
                canvas.drawRect(cx - tickW / 2, lineTop + lineH + dp(4f), cx + tickW / 2, lineTop + lineH + dp(12f), paint)
            }
            paint.alpha = 255

            // Markers: the important ones first; one that would overlap a placed one is left out
            // (it still counts in the chips and the list).
            val placed = ArrayList<Float>()
            for (m in s.marks.sortedBy { PRIORITY.indexOf(it.kind) }) {
                val cx = x(m.atM)
                if (placed.any { kotlin.math.abs(it - cx) < marker + dp(2f) }) continue
                placed += cx
                val on = !dimmed || highlight == m.kind
                drawMarker(canvas, m, cx - marker / 2, 0f, marker, if (on) 255 else DIM_ALPHA)
            }
        }

        private fun drawMarker(
            canvas: Canvas,
            m: TripStrip.Mark,
            left: Float,
            top: Float,
            size: Float,
            alpha: Int,
        ) {
            rect.set(left, top, left + size, top + size)
            val r = dp(8f)
            val brand = if (m.kind == TripStrip.Kind.PETROL) FuelBrand.of(m.name) else null
            if (brand != null) {
                val bmp = badges.getOrPut(brand) { MapIcons.brandBadge(context, brand, MARKER_DP.toInt()) }
                paint.alpha = alpha
                canvas.drawBitmap(bmp, null, rect, paint)
                paint.alpha = 255
                return
            }
            if (m.kind == TripStrip.Kind.RNR) {
                icon(R.drawable.ic_rnr_badge)?.let {
                    it.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
                    it.alpha = alpha
                    it.draw(canvas)
                }
                return
            }
            val (fill, iconRes, tint) =
                when (m.kind) {
                    TripStrip.Kind.TOLL -> Triple(R.color.sig_ink, R.drawable.ic_toll, R.color.sig_on_ink)
                    TripStrip.Kind.CAMERA -> Triple(R.color.sig_field, R.drawable.ic_alert_camera, R.color.sig_text)
                    TripStrip.Kind.EV -> Triple(R.color.sig_ev, R.drawable.ic_ev, R.color.sig_on_ink)
                    else -> Triple(R.color.sig_petrol, R.drawable.ic_fuel, R.color.sig_on_ink)
                }
            paint.color = color(fill)
            paint.alpha = alpha
            canvas.drawRoundRect(rect, r, r, paint)
            paint.alpha = 255
            icon(iconRes)?.let {
                val inset = (size * 0.22f).toInt()
                it.setBounds(left.toInt() + inset, top.toInt() + inset, (left + size).toInt() - inset, (top + size).toInt() - inset)
                it.setTint(color(tint))
                it.alpha = alpha
                it.draw(canvas)
            }
        }

        private fun icon(res: Int): Drawable? = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)?.mutate() }

        private fun describe(s: TripStrip.Strip): String =
            listOfNotNull(
                s.count(TripStrip.Kind.TOLL).takeIf { it > 0 }?.let { tr("$it tolls", "$it tol") },
                s.count(TripStrip.Kind.CAMERA).takeIf { it > 0 }?.let { tr("$it speed cameras", "$it kamera laju") },
                s.count(TripStrip.Kind.RNR).takeIf { it > 0 }?.let { tr("$it R&R", "$it R&R") },
                s.count(TripStrip.Kind.PETROL).takeIf { it > 0 }?.let { tr("$it petrol stations", "$it stesen minyak") },
                s.lightsM.size.takeIf { it > 0 }?.let { tr("$it traffic lights", "$it lampu isyarat") },
            ).joinToString(", ")

        companion object {
            /** [highlight] value for the traffic lights. */
            const val LIGHTS = "lights"
            private const val MARKER_DP = 28f
            private const val DIM_ALPHA = 60
            private val PRIORITY = listOf(TripStrip.Kind.TOLL, TripStrip.Kind.RNR, TripStrip.Kind.PETROL, TripStrip.Kind.EV, TripStrip.Kind.CAMERA)
        }
    }
