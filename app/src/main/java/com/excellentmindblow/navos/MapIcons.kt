package com.excellentmindblow.navos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat

/** Bitmaps drawn in code for the map: the route chevron and numbered stop pins. */
object MapIcons {
    /** A bold white chevron pointing RIGHT (east). On a line-placed symbol the
     * icon's "right" is the direction the line runs, so this points the way
     * the route goes. A faint dark edge keeps it readable on the blue. */
    fun routeChevron(): Bitmap {
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
        paint.color = Color.argb(70, 10, 70, 120)
        paint.strokeWidth = 15f
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
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
}
