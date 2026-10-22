package com.excellentmindblow.navos.signs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.excellentmindblow.navos.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws each kind of road sign into a bitmap, JKR style: green for
 * expressways, blue for other roads, brown for tourist places, yellow
 * hexagon route shields. Board backgrounds are see-through (60%) so the
 * route line behind a sign still shows; text and shields are solid.
 *
 * Sizes are in design units (the canvas design), drawn [U] pixels per unit.
 */
class SignPainter(
    context: Context,
    private val night: Boolean,
) {
    private val text: Typeface = ResourcesCompat.getFont(context, R.font.atkinson_hyperlegible_bold) ?: Typeface.DEFAULT_BOLD
    private val numbers: Typeface = ResourcesCompat.getFont(context, R.font.saira_semicondensed_bold) ?: Typeface.DEFAULT_BOLD

    private val green = if (night) 0x16683A else 0x1B7F45
    private val blue = if (night) 0x18418A else 0x1D4FA8
    private val brown = if (night) 0x5C3119 else 0x6E3B1E
    private val edge = if (night) Color.rgb(0xE8, 0xE8, 0xE8) else Color.WHITE
    private val shieldYellow = Color.rgb(0xF2, 0xB7, 0x05)
    private val ink = Color.rgb(0x0A, 0x0A, 0x0A)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val type = Paint(Paint.ANTI_ALIAS_FLAG)

    fun paint(content: SignContent): Bitmap =
        when (content) {
            is SignContent.Direction -> direction(content)
            is SignContent.Lanes -> lanes(content)
            is SignContent.Roundabout -> roundabout(content)
            is SignContent.Confirmation -> confirmation(content)
            is SignContent.Toll -> toll(content)
            is SignContent.RestStop -> rest(content)
            is SignContent.Aes -> aes(content)
            SignContent.TrafficLight -> trafficLight()
        }

    // --- Direction / exit / split gantry -------------------------------

    private fun direction(c: SignContent.Direction): Bitmap {
        val panelW = c.panels.map { panelWidth(it) }
        val panelH = c.panels.maxOf { panelHeight(it) }
        val tabH = if (c.exitNumber != null) 22f else 0f
        val w = panelW.sum() + 4f
        val h = tabH + panelH + 4f
        return draw(w, h) { cv ->
            val litPanel = c.panels.firstOrNull { it.lit } ?: c.panels[0]
            if (c.exitNumber != null) {
                val label = "EXIT ${c.exitNumber}"
                type.typeface = numbers
                type.textSize = 13f
                val tw = type.measureText(label) + 16f
                val r = RectF(12f, 1f, 12f + tw, tabH + 2f)
                fill.color = withAlpha(if (litPanel.expressway) green else blue, BOARD_ALPHA)
                cv.drawRoundRect(r, 6f, 6f, fill)
                stroke.color = edge
                stroke.strokeWidth = 2f
                val p = Path().apply {
                    moveTo(r.left, r.bottom)
                    lineTo(r.left, r.top + 6f)
                    quadTo(r.left, r.top, r.left + 6f, r.top)
                    lineTo(r.right - 6f, r.top)
                    quadTo(r.right, r.top, r.right, r.top + 6f)
                    lineTo(r.right, r.bottom)
                }
                cv.drawPath(p, stroke)
                type.color = Color.WHITE
                cv.drawText(label, r.left + 8f, r.bottom - 6f, type)
            }
            val board = RectF(1f, tabH + 1f, w - 1f, h - 1f)
            cv.save()
            val clip = Path().apply { addRoundRect(board, 8f, 8f, Path.Direction.CW) }
            cv.clipPath(clip)
            var x = board.left
            c.panels.forEachIndexed { i, panel ->
                val pw = panelW[i]
                val area = RectF(x, board.top, x + pw, board.bottom)
                if (!panel.lit && c.panels.size > 1) cv.saveLayerAlpha(area, FADED_ALPHA)
                fill.color = withAlpha(if (panel.expressway) green else blue, BOARD_ALPHA)
                cv.drawRect(area, fill)
                drawPanel(cv, panel, area)
                if (!panel.lit && c.panels.size > 1) cv.restore()
                if (i > 0) {
                    stroke.color = edge
                    stroke.strokeWidth = 2f
                    cv.drawLine(x, board.top, x, board.bottom, stroke)
                }
                x += pw
            }
            cv.restore()
            stroke.color = edge
            stroke.strokeWidth = 2f
            cv.drawRoundRect(board, 8f, 8f, stroke)
        }
    }

    private fun panelWidth(p: SignPanel): Float {
        var widest = 60f
        for (line in p.lines) widest = max(widest, lineWidth(line))
        return widest + 20f
    }

    private fun panelHeight(p: SignPanel): Float = 9f + p.lines.size * 22f + (p.lines.size - 1) * 5f + 6f + 24f + 7f

    private fun lineWidth(line: SignLine): Float {
        var w = 0f
        for (s in line.shields) w += shieldWidth(s) + 7f
        if (line.symbol != null) w += 29f
        type.typeface = text
        type.textSize = if (line.tourist) 14f else 16f
        w += type.measureText(line.text) + if (line.tourist) 14f else 0f
        return w
    }

    private fun drawPanel(
        cv: Canvas,
        panel: SignPanel,
        area: RectF,
    ) {
        var y = area.top + 9f
        for (line in panel.lines) {
            drawLine(cv, line, area.left + 10f, y + 11f)
            y += 27f
        }
        val arrowY = area.bottom - 7f - 12f
        val ax =
            when (panel.arrow) {
                SignArrow.STRAIGHT, SignArrow.UTURN -> area.centerX()
                SignArrow.LEFT, SignArrow.SLIGHT_LEFT -> area.left + 24f
                SignArrow.RIGHT, SignArrow.SLIGHT_RIGHT -> area.right - 24f
            }
        drawArrow(cv, panel.arrow, ax, arrowY, 1.1f)
    }

    /** One sign line, vertically centred on [cy]. */
    private fun drawLine(
        cv: Canvas,
        line: SignLine,
        x0: Float,
        cy: Float,
    ) {
        var x = x0
        for (s in line.shields) x += drawShield(cv, s, x, cy) + 7f
        if (line.symbol != null) {
            drawSymbol(cv, line.symbol, x, cy)
            x += 29f
        }
        if (line.text.isEmpty()) return
        type.typeface = text
        type.color = Color.WHITE
        if (line.tourist) {
            type.textSize = 14f
            val tw = type.measureText(line.text)
            val r = RectF(x, cy - 10f, x + tw + 14f, cy + 10f)
            fill.color = withAlpha(brown, 255)
            cv.drawRoundRect(r, 4f, 4f, fill)
            stroke.color = edge
            stroke.strokeWidth = 1.5f
            cv.drawRoundRect(r, 4f, 4f, stroke)
            cv.drawText(line.text, x + 7f, cy + 5f, type)
        } else {
            type.textSize = 16f
            cv.drawText(line.text, x, cy + 6f, type)
        }
    }

    private fun shieldWidth(s: String): Float {
        type.typeface = numbers
        type.textSize = 15f
        return max(24f, type.measureText(s) + 16f)
    }

    /** Yellow hexagon route shield; returns its width. */
    private fun drawShield(
        cv: Canvas,
        s: String,
        x: Float,
        cy: Float,
    ): Float {
        val w = shieldWidth(s)
        val h = 21f
        val inset = w * 0.14f
        val p =
            Path().apply {
                moveTo(x + inset, cy - h / 2)
                lineTo(x + w - inset, cy - h / 2)
                lineTo(x + w, cy)
                lineTo(x + w - inset, cy + h / 2)
                lineTo(x + inset, cy + h / 2)
                lineTo(x, cy)
                close()
            }
        fill.color = shieldYellow
        cv.drawPath(p, fill)
        type.typeface = numbers
        type.textSize = 15f
        type.color = ink
        type.textAlign = Paint.Align.CENTER
        cv.drawText(s, x + w / 2, cy + 5.5f, type)
        type.textAlign = Paint.Align.LEFT
        return w
    }

    private fun drawSymbol(
        cv: Canvas,
        symbol: SignSymbol,
        x: Float,
        cy: Float,
    ) {
        val r = RectF(x, cy - 11f, x + 22f, cy + 11f)
        fill.color = Color.WHITE
        cv.drawRoundRect(r, 4f, 4f, fill)
        val c = withAlpha(blue, 255)
        when (symbol) {
            SignSymbol.HOSPITAL -> glyph(cv, "H", r, c, 17f, text)
            SignSymbol.BORDER -> glyph(cv, "CIQ", r, c, 10f, numbers)
            SignSymbol.AIRPORT -> {
                fill.color = c
                val p = Path()
                val cx = r.centerX()
                p.moveTo(cx, r.top + 3f)
                p.lineTo(cx + 2f, r.top + 8f)
                p.lineTo(r.right - 3f, cy + 2f)
                p.lineTo(r.right - 3f, cy + 4f)
                p.lineTo(cx + 2f, cy + 2f)
                p.lineTo(cx + 1.5f, r.bottom - 6f)
                p.lineTo(cx + 4f, r.bottom - 3f)
                p.lineTo(cx - 4f, r.bottom - 3f)
                p.lineTo(cx - 1.5f, r.bottom - 6f)
                p.lineTo(cx - 2f, cy + 2f)
                p.lineTo(r.left + 3f, cy + 4f)
                p.lineTo(r.left + 3f, cy + 2f)
                p.lineTo(cx - 2f, r.top + 8f)
                p.close()
                cv.drawPath(p, fill)
            }
            SignSymbol.PORT -> {
                stroke.color = c
                stroke.strokeWidth = 2f
                val cx = r.centerX()
                cv.drawCircle(cx, r.top + 5f, 2f, stroke)
                cv.drawLine(cx, r.top + 7f, cx, r.bottom - 3f, stroke)
                cv.drawLine(cx - 5f, cy - 1f, cx + 5f, cy - 1f, stroke)
                cv.drawArc(RectF(cx - 7f, cy - 4f, cx + 7f, r.bottom - 3f), 0f, 180f, false, stroke)
            }
        }
    }

    private fun glyph(
        cv: Canvas,
        s: String,
        r: RectF,
        color: Int,
        size: Float,
        face: Typeface,
    ) {
        type.typeface = face
        type.textSize = size
        type.color = color
        type.textAlign = Paint.Align.CENTER
        cv.drawText(s, r.centerX(), r.centerY() + size * 0.36f, type)
        type.textAlign = Paint.Align.LEFT
    }

    /** White gantry arrow centred on ([cx], [cy]); STRAIGHT points down at the lane. */
    private fun drawArrow(
        cv: Canvas,
        arrow: SignArrow,
        cx: Float,
        cy: Float,
        scale: Float,
    ) {
        stroke.color = Color.WHITE
        stroke.strokeWidth = 3.4f * scale
        stroke.strokeCap = Paint.Cap.ROUND
        fill.color = Color.WHITE
        val s = scale
        val head = Path()
        when (arrow) {
            SignArrow.STRAIGHT -> {
                cv.drawLine(cx, cy - 9f * s, cx, cy + 2f * s, stroke)
                head.moveTo(cx - 7f * s, cy + 1f * s)
                head.lineTo(cx + 7f * s, cy + 1f * s)
                head.lineTo(cx, cy + 10f * s)
            }
            SignArrow.SLIGHT_LEFT, SignArrow.SLIGHT_RIGHT -> {
                val d = if (arrow == SignArrow.SLIGHT_LEFT) -1f else 1f
                cv.drawLine(cx - 7f * s * d, cy - 9f * s, cx + 5f * s * d, cy + 3f * s, stroke)
                head.moveTo(cx + 11f * s * d, cy + 9f * s)
                head.lineTo(cx + 9f * s * d, cy - 2f * s)
                head.lineTo(cx + 0f * s * d, cy + 7f * s)
            }
            SignArrow.LEFT, SignArrow.RIGHT -> {
                val d = if (arrow == SignArrow.LEFT) -1f else 1f
                cv.drawLine(cx - 11f * s * d, cy, cx + 3f * s * d, cy, stroke)
                head.moveTo(cx + 11f * s * d, cy)
                head.lineTo(cx + 2f * s * d, cy - 7f * s)
                head.lineTo(cx + 2f * s * d, cy + 7f * s)
            }
            SignArrow.UTURN -> {
                val path = Path().apply {
                    moveTo(cx + 6f * s, cy + 9f * s)
                    lineTo(cx + 6f * s, cy - 3f * s)
                    arcTo(RectF(cx - 6f * s, cy - 9f * s, cx + 6f * s, cy + 3f * s), 0f, -180f)
                    lineTo(cx - 6f * s, cy + 1f * s)
                }
                stroke.style = Paint.Style.STROKE
                cv.drawPath(path, stroke)
                head.moveTo(cx - 12f * s, cy + 1f * s)
                head.lineTo(cx, cy + 1f * s)
                head.lineTo(cx - 6f * s, cy + 9f * s)
            }
        }
        head.close()
        cv.drawPath(head, fill)
    }

    // --- Lane gantry ----------------------------------------------------

    private fun lanes(c: SignContent.Lanes): Bitmap {
        val n = c.lanes.size
        val laneW = 70f
        val gap = 6f
        val w = n * laneW + (n - 1) * gap + 4f
        val h = 96f
        return draw(w, h) { cv ->
            c.lanes.forEachIndexed { i, lane ->
                val x = 2f + i * (laneW + gap)
                val r = RectF(x, 2f, x + laneW, h - 2f)
                if (!lane.lit) cv.saveLayerAlpha(r, FADED_ALPHA)
                fill.color = withAlpha(if (lane.lit && c.expressway) green else blue, BOARD_ALPHA)
                cv.drawRoundRect(r, 6f, 6f, fill)
                stroke.color = edge
                stroke.strokeWidth = 2f
                cv.drawRoundRect(r, 6f, 6f, stroke)
                val label = c.label
                if (lane.lit && label != null) {
                    val shield = label.shields.firstOrNull()
                    if (shield != null) {
                        val sw = shieldWidth(shield)
                        drawShield(cv, shield, r.centerX() - sw / 2, r.top + 17f, )
                    } else {
                        type.typeface = text
                        type.textSize = 12f
                        type.color = Color.WHITE
                        type.textAlign = Paint.Align.CENTER
                        cv.drawText(ellipsize(label.text, laneW - 8f), r.centerX(), r.top + 21f, type)
                        type.textAlign = Paint.Align.LEFT
                    }
                }
                for (word in lane.indications) drawLaneArrow(cv, word, r.centerX(), r.bottom - 8f, r.top + 38f)
                if (!lane.lit) cv.restore()
            }
        }
    }

    /** Up-pointing lane arrow: a stem from [bottom] that bends toward the lane word's direction. */
    private fun drawLaneArrow(
        cv: Canvas,
        word: String,
        cx: Float,
        bottom: Float,
        top: Float,
    ) {
        val deg =
            when (word) {
                "slight left" -> -40.0
                "left" -> -80.0
                "sharp left" -> -125.0
                "slight right" -> 40.0
                "right" -> 80.0
                "sharp right" -> 125.0
                "uturn" -> -170.0
                else -> 0.0
            }
        val a = Math.toRadians(deg)
        val bend = (bottom + top) / 2f + 4f
        val len = (bend - top) - 2f
        val ex = cx + (len * sin(a)).toFloat()
        val ey = bend - (len * cos(a)).toFloat()
        stroke.color = Color.WHITE
        stroke.strokeWidth = 4f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val p = Path().apply {
            moveTo(cx, bottom)
            lineTo(cx, bend)
            lineTo(ex, ey)
        }
        cv.drawPath(p, stroke)
        val hx = sin(a).toFloat()
        val hy = -cos(a).toFloat()
        val head = Path().apply {
            moveTo(ex + hx * 8f, ey + hy * 8f)
            lineTo(ex - hy * 7f, ey + hx * 7f)
            lineTo(ex + hy * 7f, ey - hx * 7f)
            close()
        }
        fill.color = Color.WHITE
        cv.drawPath(head, fill)
    }

    // --- Roundabout -----------------------------------------------------

    private fun roundabout(c: SignContent.Roundabout): Bitmap {
        val w = 250f
        val h = 150f
        return draw(w, h) { cv ->
            val board = RectF(1f, 1f, w - 1f, h - 1f)
            fill.color = withAlpha(blue, BOARD_ALPHA)
            cv.drawRoundRect(board, 8f, 8f, fill)
            stroke.color = edge
            stroke.strokeWidth = 2f
            cv.drawRoundRect(board, 8f, 8f, stroke)
            val cx = 125f
            val cy = 78f
            val r = 24f
            val arm = 46f
            stroke.color = Color.WHITE
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.alpha = 110
            stroke.strokeWidth = 6f
            cv.drawCircle(cx, cy, r, stroke)
            stroke.strokeWidth = 5f
            for (a in c.otherExitAngles) {
                val (sx, sy) = onRing(cx, cy, r, a)
                val (ex, ey) = onRing(cx, cy, r + arm, a)
                cv.drawLine(sx, sy, ex, ey, stroke)
            }
            stroke.alpha = 255
            stroke.strokeWidth = 9f
            cv.drawLine(cx, h - 6f, cx, cy + r, stroke)
            var sweep = ((c.exitAngle - 180.0) % 360.0).toFloat()
            if (sweep <= 0f) sweep += 360f
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 90f, sweep, false, stroke)
            val (sx, sy) = onRing(cx, cy, r, c.exitAngle)
            val (ex, ey) = onRing(cx, cy, r + arm - 10f, c.exitAngle)
            cv.drawLine(sx, sy, ex, ey, stroke)
            val a = Math.toRadians(c.exitAngle)
            val hx = sin(a).toFloat()
            val hy = -cos(a).toFloat()
            val head = Path().apply {
                moveTo(ex + hx * 16f, ey + hy * 16f)
                lineTo(ex - hy * 10f, ey + hx * 10f)
                lineTo(ex + hy * 10f, ey - hx * 10f)
                close()
            }
            fill.color = Color.WHITE
            cv.drawPath(head, fill)
            // Label beside the exit arm, kept inside the board.
            type.typeface = text
            type.textSize = 14f
            type.color = Color.WHITE
            val label = ellipsize(c.label.text.ifBlank { c.label.shields.firstOrNull().orEmpty() }, 100f)
            val tw = type.measureText(label)
            val (lx, ly) = onRing(cx, cy, r + arm + 4f, c.exitAngle)
            val tx = (if (hx >= 0) lx + 8f else lx - tw - 8f).coerceIn(8f, w - tw - 8f)
            val ty = (ly + 5f).coerceIn(20f, h - 8f)
            cv.drawText(label, tx, ty, type)
        }
    }

    private fun onRing(
        cx: Float,
        cy: Float,
        r: Float,
        deg: Double,
    ): Pair<Float, Float> {
        val a = Math.toRadians(deg)
        return (cx + r * sin(a)).toFloat() to (cy - r * cos(a)).toFloat()
    }

    // --- Confirmation ---------------------------------------------------

    private fun confirmation(c: SignContent.Confirmation): Bitmap {
        val w = 250f
        val h = 14f + (if (c.shield != null) 27f else 0f) + c.rows.size * 27f + 4f
        return draw(w, h) { cv ->
            val board = RectF(1f, 1f, w - 1f, h - 1f)
            fill.color = withAlpha(if (c.expressway) green else blue, BOARD_ALPHA)
            cv.drawRoundRect(board, 8f, 8f, fill)
            stroke.color = edge
            stroke.strokeWidth = 2f
            cv.drawRoundRect(board, 8f, 8f, stroke)
            var y = 12f
            if (c.shield != null) {
                drawShield(cv, c.shield, 12f, y + 11f)
                y += 27f
            }
            for ((place, meters) in c.rows) {
                type.typeface = text
                type.textSize = 16f
                type.color = Color.WHITE
                cv.drawText(ellipsize(place, 160f), 12f, y + 17f, type)
                type.typeface = numbers
                type.textAlign = Paint.Align.RIGHT
                cv.drawText(kmText(meters), w - 12f, y + 17f, type)
                type.textAlign = Paint.Align.LEFT
                y += 27f
            }
        }
    }

    // --- Toll, R&R, AES -------------------------------------------------

    private fun toll(c: SignContent.Toll): Bitmap {
        val w = 230f
        val h = 104f
        val name = c.name.replace(Regex("(?i)^plaza tol\\s*"), "").trim()
        return draw(w, h) { cv ->
            val board = RectF(1f, 1f, w - 1f, h - 1f)
            fill.color = withAlpha(green, BOARD_ALPHA)
            cv.drawRoundRect(board, 8f, 8f, fill)
            stroke.color = edge
            stroke.strokeWidth = 2f
            cv.drawRoundRect(board, 8f, 8f, stroke)
            type.typeface = numbers
            type.textSize = 20f
            type.color = Color.WHITE
            cv.drawText("PLAZA TOL", 12f, 28f, type)
            type.typeface = text
            type.textSize = 15f
            if (name.isNotBlank()) cv.drawText(ellipsize(name, w - 24f), 12f, 50f, type)
            var x = 12f
            for (chip in listOf("Touch 'n Go", "RFID")) x += drawChip(cv, chip, x, h - 30f, green) + 8f
        }
    }

    private fun rest(c: SignContent.RestStop): Bitmap {
        val w = 230f
        val h = 104f
        val title = if (c.services) "R&R" else "Hentian Sebelah"
        val name = c.name.replace(Regex("(?i)^(r&r|r & r|hentian sebelah)\\s*"), "").trim()
        return draw(w, h) { cv ->
            val board = RectF(1f, 1f, w - 1f, h - 1f)
            fill.color = withAlpha(blue, BOARD_ALPHA)
            cv.drawRoundRect(board, 8f, 8f, fill)
            stroke.color = edge
            stroke.strokeWidth = 2f
            cv.drawRoundRect(board, 8f, 8f, stroke)
            type.typeface = numbers
            type.textSize = 20f
            type.color = Color.WHITE
            cv.drawText(title, 12f, 28f, type)
            type.textAlign = Paint.Align.RIGHT
            cv.drawText(kmText(c.distanceMeters), w - 12f, 28f, type)
            type.textAlign = Paint.Align.LEFT
            type.typeface = text
            type.textSize = 15f
            if (name.isNotBlank()) cv.drawText(ellipsize(name, w - 24f), 12f, 50f, type)
            val icons = if (c.services) listOf("fuel", "food", "WC", "surau") else listOf("P", "WC")
            var x = 12f
            for (icon in icons) {
                drawServiceIcon(cv, icon, x, h - 34f)
                x += 30f
            }
        }
    }

    private fun drawServiceIcon(
        cv: Canvas,
        icon: String,
        x: Float,
        top: Float,
    ) {
        val r = RectF(x, top, x + 24f, top + 24f)
        fill.color = Color.WHITE
        cv.drawRoundRect(r, 4f, 4f, fill)
        val c = withAlpha(blue, 255)
        stroke.color = c
        stroke.strokeWidth = 2f
        stroke.strokeCap = Paint.Cap.ROUND
        when (icon) {
            "fuel" -> {
                fill.color = c
                cv.drawRect(r.left + 5f, r.top + 5f, r.left + 14f, r.bottom - 4f, fill)
                fill.color = Color.WHITE
                cv.drawRect(r.left + 7f, r.top + 7f, r.left + 12f, r.top + 11f, fill)
                cv.drawLine(r.left + 14f, r.top + 9f, r.right - 5f, r.top + 12f, stroke)
                cv.drawLine(r.right - 5f, r.top + 12f, r.right - 5f, r.bottom - 7f, stroke)
            }
            "food" -> {
                cv.drawLine(r.left + 8f, r.top + 5f, r.left + 8f, r.bottom - 4f, stroke)
                cv.drawLine(r.left + 5f, r.top + 5f, r.left + 5f, r.top + 10f, stroke)
                cv.drawLine(r.left + 11f, r.top + 5f, r.left + 11f, r.top + 10f, stroke)
                cv.drawLine(r.left + 5f, r.top + 10f, r.left + 11f, r.top + 10f, stroke)
                cv.drawLine(r.right - 7f, r.top + 5f, r.right - 7f, r.bottom - 4f, stroke)
                fill.color = c
                cv.drawOval(RectF(r.right - 10f, r.top + 4f, r.right - 4f, r.top + 13f), fill)
            }
            "surau" -> {
                fill.color = c
                cv.drawArc(RectF(r.left + 5f, r.top + 6f, r.right - 5f, r.bottom + 4f), 180f, 180f, true, fill)
                cv.drawRect(r.left + 5f, r.centerY() + 4f, r.right - 5f, r.bottom - 4f, fill)
                cv.drawLine(r.centerX(), r.top + 2f, r.centerX(), r.top + 7f, stroke)
            }
            else -> glyph(cv, icon, r, c, if (icon.length > 1) 11f else 16f, numbers)
        }
    }

    private fun aes(c: SignContent.Aes): Bitmap {
        val w = 200f
        val h = 96f
        return draw(w, h) { cv ->
            val board = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
            fill.color = withAlpha(0xF2B705, 235)
            cv.drawRoundRect(board, 8f, 8f, fill)
            stroke.color = ink
            stroke.strokeWidth = 3f
            cv.drawRoundRect(board, 8f, 8f, stroke)
            // Camera body + lens.
            fill.color = ink
            cv.drawRoundRect(RectF(14f, 30f, 52f, 58f), 4f, 4f, fill)
            cv.drawRect(22f, 25f, 34f, 31f, fill)
            fill.color = withAlpha(0xF2B705, 255)
            cv.drawCircle(33f, 44f, 8f, fill)
            fill.color = ink
            cv.drawCircle(33f, 44f, 4.5f, fill)
            type.typeface = numbers
            type.color = ink
            type.textSize = 15f
            cv.drawText("AWAS", 62f, 36f, type)
            type.textSize = 19f
            cv.drawText("KAMERA AES", 62f, 58f, type)
            val limit = c.limit
            if (limit != null) {
                val cx = w - 30f
                val cy = h - 26f
                fill.color = Color.WHITE
                cv.drawCircle(cx, cy, 17f, fill)
                stroke.color = Color.rgb(0xD7, 0x26, 0x3D)
                stroke.strokeWidth = 5f
                cv.drawCircle(cx, cy, 15f, stroke)
                type.textSize = 15f
                type.color = Color.rgb(0x11, 0x11, 0x11)
                type.textAlign = Paint.Align.CENTER
                cv.drawText(limit.toString(), cx, cy + 5.5f, type)
                type.textAlign = Paint.Align.LEFT
            }
        }
    }

    // --- Traffic light --------------------------------------------------

    /** Pole and a three-lamp head. Which lamp is on isn't known, so none glows. */
    private fun trafficLight(): Bitmap {
        val w = 44f
        val h = 160f
        return draw(w, h) { cv ->
            fill.color = if (night) Color.rgb(0x4A, 0x4E, 0x52) else Color.rgb(0x6B, 0x70, 0x75)
            cv.drawRoundRect(RectF(w / 2 - 3f, 70f, w / 2 + 3f, h), 2f, 2f, fill)
            val head = RectF(6f, 2f, w - 6f, 84f)
            fill.color = Color.rgb(0x14, 0x14, 0x14)
            cv.drawRoundRect(head, 7f, 7f, fill)
            stroke.color = if (night) Color.rgb(0x8A, 0x8A, 0x8A) else Color.rgb(0xF2, 0xB7, 0x05)
            stroke.strokeWidth = 2.5f
            cv.drawRoundRect(head, 7f, 7f, stroke)
            val lamps = intArrayOf(Color.rgb(0x5A, 0x1E, 0x24), Color.rgb(0x5A, 0x46, 0x14), Color.rgb(0x1A, 0x46, 0x2B))
            for ((i, lamp) in lamps.withIndex()) {
                fill.color = lamp
                cv.drawCircle(w / 2, 17f + i * 25f, 9f, fill)
            }
        }
    }

    // --- helpers --------------------------------------------------------

    private fun drawChip(
        cv: Canvas,
        label: String,
        x: Float,
        top: Float,
        bg: Int,
    ): Float {
        type.typeface = text
        type.textSize = 12f
        val tw = type.measureText(label) + 14f
        fill.color = Color.WHITE
        cv.drawRoundRect(RectF(x, top, x + tw, top + 20f), 4f, 4f, fill)
        type.color = withAlpha(bg, 255)
        cv.drawText(label, x + 7f, top + 14.5f, type)
        return tw
    }

    private fun ellipsize(
        s: String,
        maxW: Float,
    ): String {
        if (type.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && type.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun kmText(meters: Double): String =
        if (meters < 950.0) "${(Math.round(meters / 50.0) * 50).coerceAtLeast(50)} m" else "${Math.round(meters / 1000.0)} km"

    private fun withAlpha(
        rgb: Int,
        alpha: Int,
    ): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

    private inline fun draw(
        w: Float,
        h: Float,
        block: (Canvas) -> Unit,
    ): Bitmap {
        val bmp = Bitmap.createBitmap((w * U).toInt().coerceAtLeast(1), (h * U).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(U, U)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeJoin = Paint.Join.MITER
        stroke.alpha = 255
        block(cv)
        return bmp
    }

    companion object {
        /** Bitmap pixels per design unit. */
        const val U = 2.5f

        /** Board background opacity: the route line shows through. */
        const val BOARD_ALPHA = 153

        /** A way you are not taking. */
        const val FADED_ALPHA = 107
    }
}
