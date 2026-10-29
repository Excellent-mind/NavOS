package com.excellentmindblow.navos.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.excellentmindblow.navos.R
import org.maplibre.android.maps.Style

/**
 * The icons the Jalan style asks for by name (tools/mapstyle/build_style.py):
 * "navos-poi-<kind>" round badges for places, and "navos-oneway" arrows.
 * Drawn here, so the style needs no sprite sheet.
 */
object MapIcons {
    /** Kind -> badge colour. Same kinds as the style's "group" expression. */
    val KINDS: Map<String, Int> =
        linkedMapOf(
            "fuel" to 0xFFC25E00.toInt(),
            "food" to 0xFFD9622B.toInt(),
            "shop" to 0xFF7B4FB8.toInt(),
            "health" to 0xFFD2283E.toInt(),
            "worship" to 0xFF1F7A45.toInt(),
            "edu" to 0xFF8A6D3B.toInt(),
            "transit" to 0xFF2457A6.toInt(),
            "park" to 0xFF3E8E41.toInt(),
            "lodging" to 0xFF5B5FC7.toInt(),
            "money" to 0xFF2E7D6B.toInt(),
            "civic" to 0xFF4A5568.toInt(),
            "parking" to 0xFF2457A6.toInt(),
            "toll" to 0xFF00693D.toInt(),
            "fun" to 0xFFC2185B.toInt(),
            "toilet" to 0xFF5E636B.toInt(),
            "car" to 0xFF4A5568.toInt(),
            "other" to 0xFF7A7F87.toInt(),
        )

    fun addAll(
        context: Context,
        style: Style,
    ) {
        for ((kind, fill) in KINDS) style.addImage("navos-poi-$kind", badge(context, kind, fill))
        style.addImage("navos-oneway", oneway(context))
        style.addImage("navos-cam", camera(context))
        style.addImage("navos-turn-head", turnHead(context))
    }

    private fun badge(
        context: Context,
        kind: String,
        fill: Int,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (22 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val r = 9.5f * d
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFFFFF.toInt()
        p.setShadowLayer(1.2f * d, 0f, 0.5f * d, 0x44000000)
        c.drawCircle(cx, cx, r, p)
        p.clearShadowLayer()
        p.color = fill
        c.drawCircle(cx, cx, r - 1.4f * d, p)
        glyph(context, c, kind, cx, (r - 1.4f * d) / 10f)
        return bmp
    }

    /** A tiny white symbol on a 20-unit box ([u] = one unit) centred on [cx]. */
    private fun glyph(
        context: Context,
        c: Canvas,
        kind: String,
        cx: Float,
        u: Float,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val line =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * u
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        fun x(v: Float) = cx + v * u
        fun text(s: String, size: Float) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = 0xFFFFFFFF.toInt()
            tp.typeface = ResourcesCompat.getFont(context, R.font.overpass_extrabold) ?: Typeface.DEFAULT_BOLD
            tp.textAlign = Paint.Align.CENTER
            tp.textSize = size * u
            val fm = tp.fontMetrics
            c.drawText(s, cx, cx - (fm.ascent + fm.descent) / 2, tp)
        }
        when (kind) {
            "fuel" -> {
                c.drawRoundRect(RectF(x(-4.5f), x(-5.5f), x(1.5f), x(5.5f)), u, u, fill)
                c.drawPath(Path().apply { moveTo(x(1.5f), x(-3f)); lineTo(x(4f), x(-1f)); lineTo(x(4f), x(3.5f)) }, line)
            }
            "food" -> {
                // Fork and spoon.
                c.drawLine(x(-3f), x(-5f), x(-3f), x(5f), line)
                c.drawLine(x(-4.6f), x(-5f), x(-4.6f), x(-1.5f), line)
                c.drawLine(x(-1.4f), x(-5f), x(-1.4f), x(-1.5f), line)
                c.drawOval(RectF(x(1.5f), x(-5.5f), x(4.5f), x(-0.5f)), fill)
                c.drawLine(x(3f), x(-1f), x(3f), x(5f), line)
            }
            "shop" -> {
                c.drawRoundRect(RectF(x(-4.5f), x(-2f), x(4.5f), x(5f)), u, u, fill)
                c.drawArc(RectF(x(-2.5f), x(-5.5f), x(2.5f), x(0.5f)), 180f, 180f, false, line)
            }
            "health" -> {
                c.drawRect(RectF(x(-1.4f), x(-5f), x(1.4f), x(5f)), fill)
                c.drawRect(RectF(x(-5f), x(-1.4f), x(5f), x(1.4f)), fill)
            }
            "worship" -> {
                // Crescent and star.
                val moon = Path().apply { addCircle(x(-0.8f), cx, 5f * u, Path.Direction.CW) }
                val cut = Path().apply { addCircle(x(1.2f), x(-0.6f), 4.2f * u, Path.Direction.CW) }
                moon.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(moon, fill)
                c.drawCircle(x(3.2f), x(-0.4f), 1.3f * u, fill)
            }
            "edu" -> {
                c.drawPath(Path().apply { moveTo(x(-6f), x(-1f)); lineTo(x(0f), x(-4.5f)); lineTo(x(6f), x(-1f)); lineTo(x(0f), x(2.5f)); close() }, fill)
                c.drawLine(x(-3.5f), x(1f), x(-3.5f), x(4f), line)
                c.drawLine(x(3.5f), x(1f), x(3.5f), x(4f), line)
                c.drawLine(x(-3.5f), x(4f), x(3.5f), x(4f), line)
            }
            "transit" -> {
                c.drawRoundRect(RectF(x(-4f), x(-5.5f), x(4f), x(3f)), 2f * u, 2f * u, fill)
                c.drawLine(x(-2.5f), x(4f), x(-4f), x(6f), line)
                c.drawLine(x(2.5f), x(4f), x(4f), x(6f), line)
            }
            "park" -> {
                c.drawCircle(cx, x(-1.5f), 4.5f * u, fill)
                c.drawLine(cx, x(1f), cx, x(5.5f), line)
            }
            "lodging" -> {
                c.drawRect(RectF(x(-5.5f), x(0f), x(5.5f), x(3f)), fill)
                c.drawLine(x(-5.5f), x(-4f), x(-5.5f), x(5f), line)
                c.drawCircle(x(-2.5f), x(-1.8f), 1.6f * u, fill)
                c.drawLine(x(5.5f), x(3f), x(5.5f), x(5f), line)
            }
            "money" -> text("RM", 7.5f)
            "civic" -> {
                c.drawPath(Path().apply { moveTo(x(-5.5f), x(-2f)); lineTo(x(0f), x(-5.5f)); lineTo(x(5.5f), x(-2f)); close() }, fill)
                for (v in listOf(-3.8f, 0f, 3.8f)) c.drawLine(x(v), x(-1f), x(v), x(3.5f), line)
                c.drawLine(x(-5.5f), x(5f), x(5.5f), x(5f), line)
            }
            "parking" -> text("P", 12f)
            "toll" -> text("TOL", 6f)
            "fun" -> {
                val star = Path()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val rr = if (i % 2 == 0) 5.5f else 2.4f
                    val px = x((rr * Math.cos(a)).toFloat())
                    val py = x((rr * Math.sin(a)).toFloat())
                    if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close()
                c.drawPath(star, fill)
            }
            "toilet" -> text("WC", 7.5f)
            "car" -> {
                c.drawRoundRect(RectF(x(-5.5f), x(-1f), x(5.5f), x(3f)), u, u, fill)
                c.drawPath(Path().apply { moveTo(x(-3.5f), x(-1f)); lineTo(x(-2f), x(-4f)); lineTo(x(2f), x(-4f)); lineTo(x(3.5f), x(-1f)) }, line)
                c.drawCircle(x(-3f), x(3.5f), 1.4f * u, fill)
                c.drawCircle(x(3f), x(3.5f), 1.4f * u, fill)
            }
            else -> c.drawCircle(cx, cx, 2.2f * u, fill)
        }
    }

    /** The head of the turn arrow painted on the route: a white triangle pointing up (north; the layer turns it). */
    private fun turnHead(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (44 * d).toInt()
        val h = (40 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawPath(Path().apply { moveTo(w / 2f, 1f * d); lineTo(w - 2f * d, h - 2f * d); lineTo(2f * d, h - 2f * d); close() }, p)
        return bmp
    }

    /** Speed camera pin: red-ringed white disc with a black camera, like the roadside AES sign. */
    private fun camera(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (30 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFD2152A.toInt()
        p.setShadowLayer(2f * d, 0f, 1f * d, 0x55000000)
        c.drawCircle(cx, cx, 13f * d, p)
        p.clearShadowLayer()
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cx, 9.8f * d, p)
        p.color = 0xFF111111.toInt()
        val u = d * 0.9f
        c.drawRoundRect(RectF(cx - 6 * u, cx - 4 * u, cx + 3 * u, cx + 2 * u), u, u, p)
        c.drawPath(Path().apply { moveTo(cx + 3 * u, cx - 2.5f * u); lineTo(cx + 6.5f * u, cx - 4 * u); lineTo(cx + 6.5f * u, cx + 2 * u); lineTo(cx + 3 * u, cx + 0.5f * u); close() }, p)
        c.drawRect(RectF(cx - 2.5f * u, cx + 2 * u, cx - 0.5f * u, cx + 6.5f * u), p)
        return bmp
    }

    /** A white chevron pointing along the road (+x), with a dark edge so it reads on asphalt. */
    private fun oneway(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (20 * d).toInt()
        val h = (12 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path =
            Path().apply {
                moveTo(3 * d, 2.5f * d)
                lineTo(w - 4 * d, h / 2f)
                lineTo(3 * d, h - 2.5f * d)
            }
        val p =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = 3.6f * d
                color = 0x66000000
            }
        c.drawPath(path, p)
        p.strokeWidth = 2.2f * d
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(path, p)
        return bmp
    }
}
