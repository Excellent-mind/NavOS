package com.excellentmindblow.navos

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

/**
 * Signal's "working on it" mark: a thin track with a short ink segment
 * sliding along it, like a route line being drawn. Constant motion, so
 * linear; it only runs while the view is on screen. With animations off
 * the segment stands still in the middle.
 */
class SignalLoaderView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        // Track: the ink colour, faint - reads on the day and the night pill alike.
        private val track =
            Paint(Paint.ANTI_ALIAS_FLAG).also {
                it.color = ContextCompat.getColor(context, R.color.sig_text)
                it.alpha = TRACK_ALPHA
            }
        private val segment = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sig_text) }
        private val bounds = RectF()
        private val clip = Path()
        private var phase = 0.5f

        private val sweep =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = SWEEP_MS
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
            }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            bounds.set(0f, 0f, w.toFloat(), h.toFloat())
            clip.reset()
            clip.addRoundRect(bounds, h / 2f, h / 2f, Path.Direction.CW)
        }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(bounds, r, r, track)
            val segW = width * SEGMENT_SHARE
            // Enters fully off the left edge, leaves fully off the right.
            val x = -segW + (width + segW) * phase
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawRoundRect(x, 0f, x + segW, height.toFloat(), r, r, segment)
            canvas.restore()
        }

        override fun onVisibilityAggregated(isVisible: Boolean) {
            super.onVisibilityAggregated(isVisible)
            update(isVisible)
        }

        override fun onDetachedFromWindow() {
            update(false)
            super.onDetachedFromWindow()
        }

        private fun update(visible: Boolean) {
            if (visible && Motion.animationsEnabled()) {
                if (!sweep.isStarted) sweep.start()
            } else {
                sweep.cancel()
                phase = 0.5f
                invalidate()
            }
        }

        private companion object {
            const val SWEEP_MS = 1100L
            const val SEGMENT_SHARE = 0.36f
            const val TRACK_ALPHA = 40 // of 255
        }
    }
