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
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Route choice on the map: a bubble on each route ("25 min", "RM 3.40")
 * where that route runs apart from the others, and wide invisible lines so a
 * tap near any route picks it.
 */
object RouteBubbles {
    private const val SRC_BUBBLES = "navos-route-bubbles"
    const val LAYER_BUBBLES = "navos-route-bubbles"
    private const val SRC_HIT = "navos-route-hit"
    const val LAYER_HIT = "navos-route-hit"
    const val PROP_INDEX = "i"

    data class Bubble(
        val index: Int,
        val time: String,
        val extra: String?,
        val selected: Boolean,
    )

    fun install(style: Style) {
        if (style.getSource(SRC_BUBBLES) != null) return
        style.addSource(GeoJsonSource(SRC_HIT))
        style.addSource(GeoJsonSource(SRC_BUBBLES))
        // Invisible, finger-wide: makes thin grey lines easy to tap.
        style.addLayer(LineLayer(LAYER_HIT, SRC_HIT).withProperties(PropertyFactory.lineWidth(28f), PropertyFactory.lineOpacity(0.01f), PropertyFactory.lineColor("#000000")))
        style.addLayer(
            SymbolLayer(LAYER_BUBBLES, SRC_BUBBLES).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.symbolSortKey(Expression.get("z")),
            ),
        )
    }

    fun clear(style: Style) {
        style.getSourceAs<GeoJsonSource>(SRC_BUBBLES)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        style.getSourceAs<GeoJsonSource>(SRC_HIT)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    fun show(
        context: Context,
        style: Style,
        routes: List<Route>,
        bubbles: List<Bubble>,
        routeColour: Int,
    ) {
        val spots = spots(routes.map { it.geometry })
        val features =
            bubbles.mapNotNull { b ->
                val p = spots.getOrNull(b.index) ?: return@mapNotNull null
                val key = "navos-bubble-${b.time}-${b.extra}-${b.selected}-$routeColour"
                if (style.getImage(key) == null) style.addImage(key, draw(context, b, routeColour))
                Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude)).apply {
                    addStringProperty("icon", key)
                    addNumberProperty(PROP_INDEX, b.index)
                    addNumberProperty("z", if (b.selected) 100 else b.index)
                }
            }
        style.getSourceAs<GeoJsonSource>(SRC_BUBBLES)?.setGeoJson(FeatureCollection.fromFeatures(features))
        style.getSourceAs<GeoJsonSource>(SRC_HIT)?.setGeoJson(
            FeatureCollection.fromFeatures(
                routes.mapIndexed { i, r ->
                    Feature.fromGeometry(LineString.fromLngLats(r.geometry.map { Point.fromLngLat(it.longitude, it.latitude) })).apply { addNumberProperty(PROP_INDEX, i) }
                },
            ),
        )
    }

    /**
     * For each route, the point (in its middle stretch) furthest from every
     * other route, so bubbles sit where routes part ways and don't stack.
     */
    fun spots(lines: List<List<RoutePoint>>): List<RoutePoint?> =
        lines.mapIndexed { i, line ->
            if (line.size < 2) return@mapIndexed null
            val others = lines.filterIndexed { j, _ -> j != i }.filter { it.size >= 2 }
            val from = line.size / 5
            val to = (line.size * 4 / 5).coerceAtLeast(from + 1)
            val step = ((to - from) / 60).coerceAtLeast(1)
            if (others.isEmpty()) return@mapIndexed line[line.size / 2]
            var best: RoutePoint? = null
            var bestD = -1.0
            var k = from
            while (k < to) {
                val p = line[k]
                val d = others.minOf { o -> GeoUtils.snapToPolyline(p, thin(o))?.distanceMeters ?: Double.MAX_VALUE }
                if (d > bestD) {
                    bestD = d
                    best = p
                }
                k += step
            }
            best ?: line[line.size / 2]
        }

    private fun thin(line: List<RoutePoint>): List<RoutePoint> {
        val step = (line.size / 400).coerceAtLeast(1)
        return line.filterIndexed { i, _ -> i % step == 0 || i == line.size - 1 }
    }

    /** A rounded label with a pointer: route colour when chosen, white otherwise. */
    private fun draw(
        context: Context,
        b: Bubble,
        routeColour: Int,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val bold = ResourcesCompat.getFont(context, R.font.overpass_extrabold) ?: Typeface.DEFAULT_BOLD
        val reg = ResourcesCompat.getFont(context, R.font.overpass_bold) ?: Typeface.DEFAULT
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 15 * d }
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = reg; textSize = 11.5f * d }
        val padX = 10 * d
        val padY = 6 * d
        val tw = tp.measureText(b.time)
        val ew = b.extra?.let { ep.measureText(it) } ?: 0f
        val w = maxOf(tw, ew) + padX * 2
        val lineGap = 2 * d
        val textH = 15 * d + if (b.extra != null) 11.5f * d + lineGap else 0f
        val h = textH + padY * 2
        val tail = 7 * d
        val shadow = 3 * d
        val bmp = Bitmap.createBitmap((w + shadow * 2).toInt(), (h + tail + shadow * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = if (b.selected) routeColour else 0xFFFFFFFF.toInt()
        val ink = if (b.selected) 0xFFFFFFFF.toInt() else 0xFF15181C.toInt()
        val quiet = if (b.selected) 0xDDFFFFFF.toInt() else 0xFF5E636B.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; setShadowLayer(shadow, 0f, 1f * d, 0x55000000) }
        val left = shadow
        val top = shadow
        val path =
            Path().apply {
                addRoundRect(RectF(left, top, left + w, top + h), 10 * d, 10 * d, Path.Direction.CW)
                moveTo(left + w / 2 - 6 * d, top + h - 1)
                lineTo(left + w / 2, top + h + tail)
                lineTo(left + w / 2 + 6 * d, top + h - 1)
                close()
            }
        c.drawPath(path, p)
        tp.color = ink
        ep.color = quiet
        tp.textAlign = Paint.Align.CENTER
        ep.textAlign = Paint.Align.CENTER
        val cx = left + w / 2
        var y = top + padY - tp.fontMetrics.ascent * 0.92f
        c.drawText(b.time, cx, y, tp)
        if (b.extra != null) {
            y += lineGap - ep.fontMetrics.ascent * 0.95f
            c.drawText(b.extra, cx, y, ep)
        }
        return bmp
    }
}
