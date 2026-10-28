package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.core.content.ContextCompat

/**
 * Route numbers in running text ("via E12 AKLEH", "via 2 Lebuhraya
 * Persekutuan") drawn on the chrome-yellow JKR route shield, like the map.
 */
object RoadShields {
    /** E1, E33, E11A, FT2, FT 50 - and a bare number right after "via" / "ikut" ("via 2 Lebuhraya ..."). */
    private val REF = Regex("\\b(E\\d{1,3}[A-Z]?|FT\\s?\\d{1,4})\\b")
    private val NUMBER_AFTER_VIA = Regex("(?<=\\b(?:via|ikut) )\\d{1,4}(?=\\s+\\p{L})")

    /** Spans of [text] that are route numbers. */
    internal fun refRanges(text: String): List<IntRange> =
        (REF.findAll(text) + NUMBER_AFTER_VIA.findAll(text)).map { it.range }.sortedBy { it.first }.toList()

    fun decorate(
        context: Context,
        text: String,
    ): CharSequence {
        val ranges = refRanges(text)
        if (ranges.isEmpty()) return text
        val sb = SpannableStringBuilder(text)
        val span = { ShieldSpan(context) }
        for (r in ranges) sb.setSpan(span(), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private class ShieldSpan(context: Context) : ReplacementSpan() {
        private val density = context.resources.displayMetrics.density
        private val fill = ContextCompat.getColor(context, R.color.jkr_shield)
        private val ink = ContextCompat.getColor(context, R.color.jkr_shield_ink)
        private val padH = 5 * density
        private val gap = 2 * density

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int = (bold(paint).measureText(text, start, end) + 2 * padH + 2 * gap).toInt()

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val p = bold(paint)
            val w = p.measureText(text, start, end) + 2 * padH
            val fm = p.fontMetrics
            val rect = RectF(x + gap, y + fm.ascent * 0.92f, x + gap + w, y + fm.descent * 0.55f + 2 * density)
            val r = 3.5f * density
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.color = ink
            canvas.drawRoundRect(rect, r, r, bg)
            bg.color = fill
            val s = 1.3f * density
            canvas.drawRoundRect(RectF(rect.left + s, rect.top + s, rect.right - s, rect.bottom - s), r - s, r - s, bg)
            p.color = ink
            canvas.drawText(text, start, end, x + gap + padH, y.toFloat(), p)
        }

        private fun bold(paint: Paint): Paint =
            Paint(paint).apply {
                isFakeBoldText = true
                textSize = paint.textSize * 0.9f
            }
    }
}
