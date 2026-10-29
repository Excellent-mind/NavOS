package com.excellentmindblow.navos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turn arrows drawn the way JKR signs draw them: one thick stroke with a
 * solid arrowhead. Exits and forks show the road you leave as a faded stem,
 * with your way peeling off it. Roundabouts go clockwise (Malaysia drives on
 * the left) and U-turns turn right. Drawn on a 48-unit box.
 */
@Composable
fun ManeuverGlyph(
    maneuverType: String,
    modifier: String?,
    sizeModifier: Modifier,
    color: Color = Color.White,
    /** The sign's colour, for the hole in the destination pin. */
    hole: Color = Color.Black,
) {
    Canvas(sizeModifier) {
        val u = size.minDimension / 48f
        val type = maneuverType
        val mod = modifier.orEmpty()
        val left = mod.contains("left")
        val right = mod.contains("right")
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val w = 6.5f * u
        val main = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ghost = Stroke(width = w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(
            path: Path,
            stroke: Stroke = main,
            alpha: Float = 1f,
        ) = drawPath(path, color.copy(alpha = color.alpha * alpha), style = stroke)

        // The stroke ends [back] short of the tip; the head covers the gap.
        fun head(
            tip: Offset,
            from: Offset,
            lenU: Float = 13f,
            halfU: Float = 8.5f,
            rimU: Float = 2f,
        ) {
            val a = atan2((tip.y - from.y).toDouble(), (tip.x - from.x).toDouble())
            val len = lenU * u
            val half = halfU * u
            val base = Offset(tip.x - (len * cos(a)).toFloat(), tip.y - (len * sin(a)).toFloat())
            val nx = (-sin(a)).toFloat() * half
            val ny = cos(a).toFloat() * half
            val tri =
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(base.x + nx, base.y + ny)
                    lineTo(base.x - nx, base.y - ny)
                    close()
                }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = rimU * u, join = StrokeJoin.Round))
        }

        fun mirrorIf(
            flip: Boolean,
            block: DrawScope.() -> Unit,
        ) {
            if (flip) {
                drawContext.transform.scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f))
                block()
                drawContext.transform.scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f))
            } else {
                block()
            }
        }

        when {
            type == "arrive" -> {
                // A destination pin.
                val pin =
                    Path().apply {
                        moveTo(24f * u, 44f * u)
                        cubicTo(14f * u, 32f * u, 10f * u, 26f * u, 10f * u, 19f * u)
                        cubicTo(10f * u, 11f * u, 16.5f * u, 5f * u, 24f * u, 5f * u)
                        cubicTo(31.5f * u, 5f * u, 38f * u, 11f * u, 38f * u, 19f * u)
                        cubicTo(38f * u, 26f * u, 34f * u, 32f * u, 24f * u, 44f * u)
                        close()
                    }
                drawPath(pin, color)
                drawCircle(hole, radius = 5.5f * u, center = p(24f, 19f))
            }
            type.contains("roundabout") || type.contains("rotary") -> {
                // The whole ring faded; your way round it bold, clockwise (Malaysia drives on the left):
                // in from the bottom, out left (first exit), straight on, right, or all the way round.
                // Big ring, slim strokes, a short stub and the head right on it, so it reads at 24 dp.
                val c = p(24f, 26f)
                val r = 10.5f * u
                drawCircle(color.copy(alpha = color.alpha * 0.35f), radius = r, center = c, style = Stroke(width = 3.4f * u))
                val sweep =
                    when {
                        mod == "uturn" -> 315f
                        mod.contains("left") -> 90f
                        mod.contains("right") -> 270f
                        else -> 180f
                    }
                val endA = Math.toRadians((90f + sweep).toDouble())
                val ux = cos(endA).toFloat()
                val uy = sin(endA).toFloat()
                val path =
                    Path().apply {
                        moveTo(24f * u, 46f * u)
                        lineTo(24f * u, c.y + r)
                        arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), 90f, sweep, false)
                        lineTo(c.x + (r + 2.5f * u) * ux, c.y + (r + 2.5f * u) * uy)
                    }
                line(path, Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                head(Offset(c.x + (r + 11f * u) * ux, c.y + (r + 11f * u) * uy), Offset(c.x, c.y), lenU = 8.5f, halfU = 6.5f, rimU = 1f)
            }
            type == "uturn" || mod == "uturn" ->
                // Malaysia: U-turns go round to the right.
                {
                    line(Path().apply { moveTo(15f * u, 44f * u); lineTo(15f * u, 19f * u); cubicTo(15f * u, 8f * u, 33f * u, 8f * u, 33f * u, 19f * u); lineTo(33f * u, 27f * u) })
                    head(p(33f, 39f), p(33f, 25f))
                }
            type == "off ramp" || type == "fork" || type == "on ramp" || (type == "merge" && (left || right)) -> {
                // The road you leave goes straight on, faded; your way peels off it.
                mirrorIf(right) {
                    line(Path().apply { moveTo(30f * u, 44f * u); lineTo(30f * u, 6f * u) }, ghost, alpha = 0.38f)
                    line(Path().apply { moveTo(30f * u, 44f * u); lineTo(30f * u, 32f * u); cubicTo(30f * u, 24f * u, 15f * u, 22f * u, 15f * u, 15f * u) })
                    head(p(15f, 3f), p(15f, 16f))
                }
            }
            mod == "sharp left" || mod == "sharp right" ->
                mirrorIf(right) {
                    line(Path().apply { moveTo(30f * u, 44f * u); lineTo(30f * u, 20f * u); cubicTo(30f * u, 13f * u, 24f * u, 13f * u, 20f * u, 19f * u); lineTo(17f * u, 24f * u) })
                    head(p(9f, 35f), p(17f, 24f))
                }
            mod == "left" || mod == "right" ->
                mirrorIf(right) {
                    line(Path().apply { moveTo(30f * u, 44f * u); lineTo(30f * u, 24f * u); cubicTo(30f * u, 19f * u, 27f * u, 16f * u, 22f * u, 16f * u); lineTo(16f * u, 16f * u) })
                    head(p(4f, 16f), p(16f, 16f))
                }
            mod == "slight left" || mod == "slight right" ->
                mirrorIf(right) {
                    line(Path().apply { moveTo(28f * u, 44f * u); lineTo(28f * u, 28f * u); lineTo(19f * u, 16f * u) })
                    head(p(12f, 6f), p(19f, 16f))
                }
            else -> {
                line(Path().apply { moveTo(24f * u, 44f * u); lineTo(24f * u, 15f * u) })
                head(p(24f, 3f), p(24f, 15f))
            }
        }
    }
}
