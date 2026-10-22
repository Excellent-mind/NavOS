package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.excellentmindblow.navos.poi.FuelBrand

/** Bitmaps drawn in code: the route chevron, numbered stop pins and petrol brand pins / badges. */
object MapIcons {
    /** A bold chevron pointing RIGHT (east) in [ink] - the opposite of the
     * route colour (white on the black day route, black on the white night
     * route). On a line-placed symbol the icon's "right" is the direction
     * the line runs, so this points the way the route goes. */
    fun routeChevron(ink: Int): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path =
            android.graphics.Path().apply {
                moveTo(20f, 14f)
                lineTo(46f, 32f)
                lineTo(20f, 50f)
            }
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
        paint.color = ink
        paint.strokeWidth = 10f
        canvas.drawPath(path, paint)
        return bitmap
    }

    /** A round navy pin with the stop's number in white. */
    fun stopPin(
        context: Context,
        number: Int,
    ): Bitmap {
        fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

        val size = dp(30)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size / 2f
        paint.color = Color.WHITE
        canvas.drawCircle(r, r, r, paint)
        paint.color = ContextCompat.getColor(context, R.color.navos_navy)
        canvas.drawCircle(r, r, r - dp(2), paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.5f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(number.toString(), r, r - (paint.descent() + paint.ascent()) / 2f, paint)
        return bitmap
    }

    /** A map pin (same teardrop as the petrol pin) in [brand]'s colour with its mark. */
    fun brandPin(
        context: Context,
        brand: FuelBrand,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (38 * density).toInt()
        val h = (38 * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Pin outline in a 24-unit box, like the vector pins: head circle
        // centred at (12, 9) r 7.5, tip at (12, 22.5).
        val unit = w / 24f
        val path =
            Path().apply {
                moveTo(12f * unit, 22.5f * unit)
                cubicTo(12f * unit, 22.5f * unit, 4.5f * unit, 14.6f * unit, 4.5f * unit, 9f * unit)
                cubicTo(4.5f * unit, 4.86f * unit, 7.86f * unit, 1.5f * unit, 12f * unit, 1.5f * unit)
                cubicTo(16.14f * unit, 1.5f * unit, 19.5f * unit, 4.86f * unit, 19.5f * unit, 9f * unit)
                cubicTo(19.5f * unit, 14.6f * unit, 12f * unit, 22.5f * unit, 12f * unit, 22.5f * unit)
                close()
            }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = brand.fill
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * unit
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        drawMark(context, canvas, brand, 12f * unit, 9f * unit, 10f * unit)
        return bitmap
    }

    /** A round badge in [brand]'s colour with its mark, [sizeDp] across (rows, station card). */
    fun brandBadge(
        context: Context,
        brand: FuelBrand,
        sizeDp: Int,
    ): Bitmap {
        val size = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand.fill }
        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        drawMark(context, canvas, brand, r, r, size * 0.62f)
        return bitmap
    }

    /** The brand's mark centred on ([cx], [cy]), fitted into [maxWidth]. */
    private fun drawMark(
        context: Context,
        canvas: Canvas,
        brand: FuelBrand,
        cx: Float,
        cy: Float,
        maxWidth: Float,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = brand.ink
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.signal_numbers)?.let { Typeface.create(it, Typeface.BOLD) }
                    ?: Typeface.DEFAULT_BOLD
                textSize = maxWidth * 0.95f
            }
        val width = paint.measureText(brand.mark)
        if (width > maxWidth) paint.textSize *= maxWidth / width
        canvas.drawText(brand.mark, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    /** Route preview bubble with the route's time: ink for the chosen route, sheet colour for the others. */
    fun timeBubble(
        context: Context,
        text: String,
        selected: Boolean,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ResourcesCompat.getFont(context, R.font.signal_numbers)?.let { Typeface.create(it, if (selected) Typeface.BOLD else Typeface.NORMAL) }
                textSize = (if (selected) 17 else 16) * density
            }
        val padX = 9 * density
        val padY = 5 * density
        val tail = 6 * density
        val textW = paint.measureText(text)
        val fm = paint.fontMetrics
        val textH = fm.descent - fm.ascent
        val w = (textW + padX * 2).toInt() + 2
        val boxH = textH + padY * 2
        val h = (boxH + tail).toInt() + 2
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = ContextCompat.getColor(context, if (selected) R.color.sig_ink else R.color.sig_sheet)
        val ink = ContextCompat.getColor(context, if (selected) R.color.sig_on_ink else R.color.sig_text)
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        val rect = android.graphics.RectF(1f, 1f, w - 1f, boxH)
        canvas.drawRoundRect(rect, 10 * density, 10 * density, box)
        // Small pointer under the bubble, onto the route.
        val pointer =
            Path().apply {
                moveTo(w / 2f - tail, boxH - 1)
                lineTo(w / 2f, boxH + tail - 1)
                lineTo(w / 2f + tail, boxH - 1)
                close()
            }
        canvas.drawPath(pointer, box)
        if (!selected) {
            val outline =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = density
                    color = ContextCompat.getColor(context, R.color.sig_outline)
                }
            canvas.drawRoundRect(rect, 10 * density, 10 * density, outline)
        }
        paint.color = ink
        canvas.drawText(text, padX + 1, padY - fm.ascent, paint)
        return bitmap
    }
}
