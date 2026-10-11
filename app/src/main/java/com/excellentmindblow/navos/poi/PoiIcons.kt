package com.excellentmindblow.navos.poi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** The map icons for toll plazas and speed cameras, drawn in code at the
 * phone's density (so they are the same physical size on every screen). */
object PoiIcons {
    private fun newBitmap(
        context: Context,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap {
        val m = context.resources.displayMetrics
        val bmp = Bitmap.createBitmap((widthDp * m.density).toInt(), (heightDp * m.density).toInt(), Bitmap.Config.ARGB_8888)
        bmp.density = m.densityDpi
        return bmp
    }

    /** Speed camera: red badge, white rim, white camera with a lens and
     * radar waves, soft ground shadow. */
    fun camera(context: Context): Bitmap {
        val bmp = newBitmap(context, 48, 52)
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w / 2f
        val cy = h * 0.46f
        val r = w * 0.43f

        p.color = Color.argb(90, 0, 0, 0)
        p.maskFilter = BlurMaskFilter(w * 0.05f, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(cx - r * 0.8f, h - h * 0.17f, cx + r * 0.8f, h - h * 0.03f), p)
        p.maskFilter = null

        p.shader = LinearGradient(0f, cy - r, 0f, cy + r, Color.parseColor("#FF6A6E"), Color.parseColor("#D5262C"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.06f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)

        // camera body
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawRoundRect(RectF(cx - r * 0.62f, cy - r * 0.22f, cx + r * 0.52f, cy + r * 0.5f), r * 0.14f, r * 0.14f, p)
        c.drawRoundRect(RectF(cx - r * 0.32f, cy - r * 0.46f, cx + r * 0.08f, cy - r * 0.18f), r * 0.1f, r * 0.1f, p)
        // lens
        p.color = Color.parseColor("#D5262C")
        c.drawCircle(cx - r * 0.05f, cy + r * 0.14f, r * 0.24f, p)
        p.color = Color.WHITE
        c.drawCircle(cx - r * 0.05f, cy + r * 0.14f, r * 0.13f, p)
        // radar waves
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w * 0.035f
        p.color = Color.argb(235, 255, 255, 255)
        c.drawArc(RectF(cx + r * 0.2f, cy - r * 0.45f, cx + r * 0.85f, cy + r * 0.2f), -50f, 100f, false, p)
        p.alpha = 150
        c.drawArc(RectF(cx + r * 0.02f, cy - r * 0.68f, cx + r * 1.03f, cy + r * 0.38f), -50f, 100f, false, p)
        return bmp
    }

    /** Toll plaza: blue badge with "TOLL" over a striped barrier gate. */
    fun toll(context: Context): Bitmap {
        val bmp = newBitmap(context, 60, 50)
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val box = RectF(w * 0.04f, h * 0.03f, w * 0.96f, h * 0.84f)
        p.color = Color.argb(90, 0, 0, 0)
        p.maskFilter = BlurMaskFilter(w * 0.04f, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(RectF(box.left + 2f, box.top + h * 0.1f, box.right - 2f, box.bottom + h * 0.1f), h * 0.2f, h * 0.2f, p)
        p.maskFilter = null

        p.shader = LinearGradient(0f, box.top, 0f, box.bottom, Color.parseColor("#3C7BFF"), Color.parseColor("#1744B5"), Shader.TileMode.CLAMP)
        c.drawRoundRect(box, h * 0.2f, h * 0.2f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.035f
        p.color = Color.WHITE
        c.drawRoundRect(box, h * 0.2f, h * 0.2f, p)

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = h * 0.27f
        c.drawText("TOLL", w / 2f, box.top + h * 0.30f, p)

        // barrier gate: post + striped arm
        val armTop = box.top + h * 0.40f
        val armBottom = armTop + h * 0.13f
        c.drawRoundRect(RectF(w * 0.18f, armTop - h * 0.05f, w * 0.27f, box.bottom - h * 0.07f), 3f, 3f, p)
        val arm = RectF(w * 0.27f, armTop, w * 0.84f, armBottom)
        c.drawRoundRect(arm, h * 0.06f, h * 0.06f, p)
        p.color = Color.parseColor("#E5484D")
        val stripe = arm.width() / 7f
        for (i in 1..5 step 2) {
            c.save()
            c.clipRect(arm.left + stripe * i, arm.top, arm.left + stripe * (i + 1), arm.bottom)
            c.drawRoundRect(arm, h * 0.06f, h * 0.06f, p)
            c.restore()
        }
        // little pointer under the badge
        p.color = Color.parseColor("#1744B5")
        val path = android.graphics.Path()
        path.moveTo(w * 0.42f, box.bottom - 1f)
        path.lineTo(w * 0.5f, h * 0.97f)
        path.lineTo(w * 0.58f, box.bottom - 1f)
        path.close()
        c.drawPath(path, p)
        return bmp
    }
}
