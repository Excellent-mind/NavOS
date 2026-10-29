package com.excellentmindblow.navos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.navigation.TripStrip
import com.excellentmindblow.navos.poi.FuelBrand
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr
import com.excellentmindblow.navos.ui.theme.Traffic

/**
 * The trip as a strip of road, You on the left, the destination on the
 * right: asphalt with a dashed lane line, slow / jammed stretches painted on
 * it, and tolls, R&Rs, petrol, EV and cameras standing above it in order.
 * [mini]: just the asphalt and traffic (other routes' cards).
 */
@Composable
fun TripRibbon(
    strip: TripStrip.Strip,
    modifier: Modifier = Modifier,
    mini: Boolean = false,
) {
    val c = Jalan.colors
    val text = rememberTextMeasurer()
    val rnr = ImageBitmap.imageResource(R.drawable.sign_rnr_green)
    val logos = FuelBrand.entries.filter { it.logo != 0 }.associateWith { ImageBitmap.imageResource(it.logo) }
    val hasMarks = strip.marks.isNotEmpty()
    val description = remember(strip) { describe(strip) }
    Canvas(modifier.fillMaxWidth().height(if (mini) 8.dp else if (hasMarks) 62.dp else 26.dp).semantics { contentDescription = description }) {
        val roadH = if (mini) 6.dp.toPx() else 12.dp.toPx()
        val top = size.height - roadH - (if (mini) 1.dp.toPx() else 4.dp.toPx())
        fun x(m: Double) = (m / strip.lengthM).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(c.asphalt, Offset(0f, top), Size(size.width, roadH), CornerRadius(3.dp.toPx()))
        for (t in strip.traffic) {
            val l = x(t.fromM)
            drawRect(if (t.jam) Traffic.Jam else Traffic.Slow, Offset(l, top), Size((x(t.toM) - l).coerceAtLeast(3.dp.toPx()), roadH))
        }
        if (mini) return@Canvas
        // Lane line.
        val dash = 9.dp.toPx()
        var dx = 22.dp.toPx()
        while (dx + dash < size.width - 26.dp.toPx()) {
            drawRect(c.paint, Offset(dx, top + roadH / 2 - 0.9.dp.toPx()), Size(dash, 1.8.dp.toPx()))
            dx += dash * 2
        }
        // You and the destination.
        val cy = top + roadH / 2
        drawCircle(Color.White, 8.5.dp.toPx(), Offset(9.dp.toPx(), cy))
        drawCircle(c.route, 6.dp.toPx(), Offset(9.dp.toPx(), cy))
        drawRoundRect(Color.White, Offset(size.width - 18.dp.toPx(), cy - 8.5.dp.toPx()), Size(17.dp.toPx(), 17.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawRoundRect(Traffic.Jam, Offset(size.width - 15.5.dp.toPx(), cy - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(2.dp.toPx()))
        // Markers: the important ones first, one that would overlap a placed one is left out.
        val m = 26.dp.toPx()
        val placed = ArrayList<Float>()
        for (mark in strip.marks.sortedBy { PRIORITY.indexOf(it.kind) }) {
            val cx = x(mark.atM).coerceIn(m / 2, size.width - m / 2)
            if (placed.any { kotlin.math.abs(it - cx) < m + 2.dp.toPx() }) continue
            placed += cx
            val y = top - 6.dp.toPx() - m
            drawLine(c.inkQuiet.copy(alpha = 0.5f), Offset(cx, y + m), Offset(cx, top - 1.dp.toPx()), 1.5.dp.toPx())
            marker(mark, cx - m / 2, y, m, text, rnr, logos)
        }
    }
}

private val PRIORITY = listOf(TripStrip.Kind.REPORT, TripStrip.Kind.TOLL, TripStrip.Kind.CAMERA, TripStrip.Kind.RNR, TripStrip.Kind.PETROL, TripStrip.Kind.EV)

private fun DrawScope.marker(
    mark: TripStrip.Mark,
    left: Float,
    top: Float,
    s: Float,
    text: TextMeasurer,
    rnr: ImageBitmap,
    logos: Map<FuelBrand, ImageBitmap>,
) {
    val r = CornerRadius(6.dp.toPx())
    when (mark.kind) {
        TripStrip.Kind.TOLL -> {
            drawRoundRect(Jkr.Green, Offset(left, top), Size(s, s), r)
            drawRoundRect(Color.White, Offset(left + 2.dp.toPx(), top + 2.dp.toPx()), Size(s - 4.dp.toPx(), s - 4.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            val t = text.measure("TOL", JalanType.label.copy(fontSize = 9.sp, color = Color.White))
            drawText(t, topLeft = Offset(left + (s - t.size.width) / 2, top + (s - t.size.height) / 2))
        }
        TripStrip.Kind.RNR -> drawImage(rnr, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(s.toInt(), s.toInt()))
        TripStrip.Kind.PETROL -> {
            val brand = FuelBrand.of(mark.name)
            val logo = brand?.let { logos[it] }
            drawCircle(brand?.badgeFill?.let { Color(it) } ?: Color(0xFFC25E00), s / 2, Offset(left + s / 2, top + s / 2))
            if (logo != null) {
                val inner = s * 0.66f
                val scale = minOf(inner / logo.width, inner / logo.height)
                val w = logo.width * scale
                val h = logo.height * scale
                drawImage(logo, dstOffset = IntOffset((left + (s - w) / 2).toInt(), (top + (s - h) / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
            } else {
                val t = text.measure(brand?.mark ?: "P", JalanType.label.copy(fontSize = 11.sp, color = Color.White))
                drawText(t, topLeft = Offset(left + (s - t.size.width) / 2, top + (s - t.size.height) / 2))
            }
        }
        TripStrip.Kind.EV -> {
            drawCircle(Color(0xFF1F8A4C), s / 2, Offset(left + s / 2, top + s / 2))
            val t = text.measure("EV", JalanType.label.copy(fontSize = 10.sp, color = Color.White))
            drawText(t, topLeft = Offset(left + (s - t.size.width) / 2, top + (s - t.size.height) / 2))
        }
        TripStrip.Kind.CAMERA -> {
            drawRoundRect(Color(0xFF15181C), Offset(left, top), Size(s, s), r)
            drawRoundRect(Color.White, Offset(left + s * 0.2f, top + s * 0.32f), Size(s * 0.6f, s * 0.42f), CornerRadius(2.dp.toPx()))
            drawCircle(Color(0xFF15181C), s * 0.12f, Offset(left + s / 2, top + s * 0.53f))
        }
        TripStrip.Kind.REPORT -> drawCircle(Traffic.Report, s / 2, Offset(left + s / 2, top + s / 2))
    }
}

private fun describe(s: TripStrip.Strip): String {
    val parts = ArrayList<String>()
    s.count(TripStrip.Kind.TOLL).takeIf { it > 0 }?.let { parts += tr("$it toll plazas", "$it plaza tol") }
    s.count(TripStrip.Kind.RNR).takeIf { it > 0 }?.let { parts += tr("$it R&R", "$it R&R") }
    s.count(TripStrip.Kind.PETROL).takeIf { it > 0 }?.let { parts += tr("$it petrol stations", "$it stesen minyak") }
    s.count(TripStrip.Kind.CAMERA).takeIf { it > 0 }?.let { parts += tr("$it speed cameras", "$it kamera laju") }
    if (s.traffic.any { it.jam }) parts += tr("jams on the way", "ada jem")
    return tr("The trip: ", "Perjalanan: ") + parts.ifEmpty { listOf(tr("a clear road", "jalan lancar")) }.joinToString(", ")
}
