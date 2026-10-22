package com.excellentmindblow.navos.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.R

/**
 * The driver's arrow, Signal style: the app icon's arrow, ink with a
 * contrasting rim (black with a white rim by day, white with a black rim
 * at night - the theme's sig_ink / sig_on_ink) and a soft ground shadow.
 * Pointing up; the map rotates it to the heading.
 *
 * The arrow is drawn so the middle of its footprint sits exactly on the
 * bitmap centre. The map rotates a symbol about the bitmap centre and
 * pins that centre to the GPS position, so an off-centre drawing made the
 * arrow swing sideways off the route line whenever it turned.
 */
object PuckIcon {
    const val SIZE_DP = 64

    fun create(context: Context): Bitmap {
        val metrics = context.resources.displayMetrics
        val size = (SIZE_DP * metrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi
        val canvas = Canvas(bitmap)
        val s = size.toFloat()

        // Arrow from the icon, in a 56-unit box: tip (28, 6), wings (46, 42)
        // and (10, 42), notch (28, 34). Its footprint (6..42) is centred on 24,
        // so shift it down to put that on the bitmap centre (28).
        val u = s / 56f
        val dy = 4f * u
        val arrow =
            Path().apply {
                moveTo(28f * u, 6f * u + dy)
                lineTo(46f * u, 42f * u + dy)
                lineTo(28f * u, 34f * u + dy)
                lineTo(10f * u, 42f * u + dy)
                close()
            }

        val shadow =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(70, 0, 0, 0)
                maskFilter = BlurMaskFilter(3f * u, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.drawOval(RectF(12f * u, 40f * u + dy, 44f * u, 50f * u + dy), shadow)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.sig_ink) }
        val rim =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.5f * u
                strokeJoin = Paint.Join.ROUND
                color = ContextCompat.getColor(context, R.color.sig_on_ink)
            }
        canvas.drawPath(arrow, rim)
        canvas.drawPath(arrow, fill)
        canvas.drawPath(arrow, rim)
        return bitmap
    }
}
