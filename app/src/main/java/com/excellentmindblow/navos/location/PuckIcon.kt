package com.excellentmindblow.navos.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Draws the driver's arrow as a Waze-style 3D marker: a faceted chevron
 * (light left face, darker right face) with a visible thickness, a white
 * rim and a soft ground shadow. Pointing up; the map rotates it to the
 * heading. Drawn in code so there is no asset to maintain.
 *
 * The chevron is drawn so the middle of its footprint sits exactly on the
 * bitmap centre. The map rotates a symbol about the bitmap centre and
 * pins that centre to the GPS position, so an off-centre drawing made the
 * arrow swing sideways off the blue line whenever it turned.
 */
object PuckIcon {
    const val SIZE_DP = 76
    private const val CONTENT_SCALE = 0.86f

    fun create(context: Context): Bitmap {
        val metrics = context.resources.displayMetrics
        val size = (SIZE_DP * metrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = metrics.densityDpi
        val canvas = Canvas(bitmap)
        val s = size.toFloat()

        // Put the middle of the chevron's footprint (tip 0.07 .. wing 0.70)
        // on the bitmap centre, shrinking a little so the thickness and
        // shadow still fit inside the bitmap.
        canvas.translate(0.5f * s, 0.5f * s)
        canvas.scale(CONTENT_SCALE, CONTENT_SCALE)
        canvas.translate(-0.5f * s, -0.385f * s)

        // Chevron corners, normalised to the bitmap.
        val tipX = 0.5f * s
        val tipY = 0.07f * s
        val rightX = 0.87f * s
        val wingY = 0.70f * s
        val leftX = 0.13f * s
        val notchY = 0.56f * s
        val depth = (0.085f * s).toInt()

        fun outline() =
            Path().apply {
                moveTo(tipX, tipY)
                lineTo(rightX, wingY)
                lineTo(tipX, notchY)
                lineTo(leftX, wingY)
                close()
            }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 0.034f * s
                strokeJoin = Paint.Join.ROUND
            }

        // Soft ground shadow under the arrow.
        val shadow =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 0, 0, 0)
                maskFilter = BlurMaskFilter(0.05f * s, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.drawOval(RectF(0.14f * s, 0.66f * s + depth, 0.86f * s, 0.86f * s + depth), shadow)

        // Thickness: stack the outline downwards in a dark blue.
        val side = outline()
        fill.color = Color.parseColor("#0A5E96")
        canvas.save()
        canvas.translate(0f, depth.toFloat())
        canvas.drawPath(side, fill)
        canvas.drawPath(side, stroke)
        canvas.restore()
        for (i in depth - 1 downTo 1) {
            canvas.save()
            canvas.translate(0f, i.toFloat())
            canvas.drawPath(side, fill)
            canvas.restore()
        }

        // Top face: two facets so it reads as a folded, lit surface.
        val leftFace =
            Path().apply {
                moveTo(tipX, tipY)
                lineTo(tipX, notchY)
                lineTo(leftX, wingY)
                close()
            }
        val rightFace =
            Path().apply {
                moveTo(tipX, tipY)
                lineTo(rightX, wingY)
                lineTo(tipX, notchY)
                close()
            }
        fill.shader = LinearGradient(leftX, tipY, tipX, wingY, Color.parseColor("#9BEBFF"), Color.parseColor("#2CB8F7"), Shader.TileMode.CLAMP)
        canvas.drawPath(leftFace, fill)
        fill.shader = LinearGradient(tipX, tipY, rightX, wingY, Color.parseColor("#1EA3EA"), Color.parseColor("#0F7AC0"), Shader.TileMode.CLAMP)
        canvas.drawPath(rightFace, fill)
        fill.shader = null

        // White rim + a thin highlight along the fold.
        canvas.drawPath(outline(), stroke)
        val ridge =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(150, 255, 255, 255)
                strokeWidth = 0.012f * s
                strokeCap = Paint.Cap.ROUND
            }
        canvas.drawLine(tipX, tipY + 0.03f * s, tipX, notchY - 0.02f * s, ridge)

        return bitmap
    }
}
