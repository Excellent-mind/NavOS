package com.excellentmindblow.navos.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.poi.FuelBrand
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.ui.drive.DriveViewModel
import com.excellentmindblow.navos.ui.drive.OnTheWay
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Petrol / EV / parking / transit pins, and live traffic painted on the
 * route line. Pins are round badges: the company's logo (or its colour and
 * mark) for petrol, a bolt for EV, P for parking, the line code for a station.
 */
object PoiPins {
    private const val SRC_PINS = "navos-pois"
    const val LAYER_PINS = "navos-pois"
    private const val SRC_TRAFFIC = "navos-traffic"
    private const val LAYER_TRAFFIC = "navos-traffic"
    const val PROP_ID = "id"

    const val FUEL_FILL = 0xFFC25E00.toInt()
    const val EV_FILL = 0xFF1F8A4C.toInt()
    const val PARKING_FILL = 0xFF2457A6.toInt()
    const val TRANSIT_FILL = 0xFF3A3F45.toInt()

    fun install(style: Style) {
        if (style.getSource(SRC_PINS) != null) return
        style.addSource(GeoJsonSource(SRC_TRAFFIC))
        style.addSource(GeoJsonSource(SRC_PINS))
        val traffic =
            LineLayer(LAYER_TRAFFIC, SRC_TRAFFIC).withProperties(
                PropertyFactory.lineColor(Expression.switchCase(Expression.get("jam"), Expression.color(0xFFD2283E.toInt()), Expression.color(0xFFE88600.toInt()))),
                PropertyFactory.lineWidth(RouteLayers.width(6f, 10f, 19f, 46f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        if (style.getLayer(RouteLayers.LAYER_SEL) != null) style.addLayerAbove(traffic, RouteLayers.LAYER_SEL) else style.addLayer(traffic)
        style.addLayer(
            SymbolLayer(LAYER_PINS, SRC_PINS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(Expression.switchCase(Expression.get("sel"), Expression.literal(1.3f), Expression.literal(1f))),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.symbolSortKey(Expression.get("z")),
            ),
        )
    }

    private const val SRC_AREA = "navos-area-traffic"

    /** Traffic on every road around (home map): a thin amber / red band on the left of each carriageway. */
    fun installArea(style: Style) {
        if (style.getSource(SRC_AREA) != null) return
        style.addSource(GeoJsonSource(SRC_AREA))
        val l =
            LineLayer(SRC_AREA, SRC_AREA).withProperties(
                PropertyFactory.lineColor(Expression.switchCase(Expression.get("jam"), Expression.color(0xFFD2283E.toInt()), Expression.color(0xFFE88600.toInt()))),
                PropertyFactory.lineWidth(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(11, 1.5f), Expression.stop(14, 3f), Expression.stop(17, 5f))),
                // Pieces run the way traffic goes; Malaysia drives on the left.
                PropertyFactory.lineOffset(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(11, -1f), Expression.stop(14, -2.5f), Expression.stop(17, -6f))),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineOpacity(0.9f),
            )
        if (style.getLayer("road-label") != null) style.addLayerBelow(l, "road-label") else style.addLayer(l)
    }

    fun showArea(
        style: Style,
        pieces: List<com.excellentmindblow.navos.traffic.TrafficClient.AreaPiece>,
    ) {
        style.getSourceAs<GeoJsonSource>(SRC_AREA)?.setGeoJson(
            FeatureCollection.fromFeatures(
                pieces.map { p ->
                    Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(p.aLon, p.aLat), Point.fromLngLat(p.bLon, p.bLat)))).apply { addBooleanProperty("jam", p.jam) }
                },
            ),
        )
    }

    fun showTraffic(
        style: Style,
        runs: List<DriveViewModel.TrafficRun>,
    ) {
        style.getSourceAs<GeoJsonSource>(SRC_TRAFFIC)?.setGeoJson(
            FeatureCollection.fromFeatures(
                runs.filter { it.points.size >= 2 }.map { r ->
                    Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply { addBooleanProperty("jam", r.jam) }
                },
            ),
        )
    }

    fun showPins(
        context: Context,
        style: Style,
        pins: List<OnTheWay.Pin>,
        selected: String?,
    ) {
        val features =
            pins.mapIndexed { i, pin ->
                val icon = iconFor(context, style, pin.poi)
                Feature.fromGeometry(Point.fromLngLat(pin.poi.longitude, pin.poi.latitude)).apply {
                    addStringProperty(PROP_ID, pin.poi.id)
                    addStringProperty("icon", icon)
                    addBooleanProperty("sel", pin.poi.id == selected)
                    // Nearest on top; the selected one above all.
                    addNumberProperty("z", if (pin.poi.id == selected) 1000 else pins.size - i)
                }
            }
        style.getSourceAs<GeoJsonSource>(SRC_PINS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun iconFor(
        context: Context,
        style: Style,
        poi: Poi,
    ): String {
        val brand = if (poi.kind == PoiKind.FUEL) FuelBrand.of(poi.name) else null
        val line = poi.lines.firstOrNull()
        val key =
            when (poi.kind) {
                PoiKind.FUEL -> "navos-pin-fuel-${brand?.name ?: "x"}"
                PoiKind.EV -> "navos-pin-ev"
                PoiKind.PARKING -> "navos-pin-parking"
                PoiKind.TRANSIT -> "navos-pin-transit-${line?.badge ?: ""}-${line?.colour ?: 0}"
            }
        if (style.getImage(key) == null) style.addImage(key, draw(context, poi.kind, brand, line?.badge?.take(3), line?.colour))
        return key
    }

    /** A 40 dp round badge with a white rim and a soft shadow. */
    fun draw(
        context: Context,
        kind: PoiKind,
        brand: FuelBrand?,
        badge: String?,
        lineColour: Int?,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val size = (44 * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = size / 2f
        val r = 18 * d
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setShadowLayer(3 * d, 0f, 1.2f * d, 0x55000000)
        paint.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()
        val fill =
            when (kind) {
                PoiKind.FUEL -> brand?.badgeFill ?: FUEL_FILL
                PoiKind.EV -> EV_FILL
                PoiKind.PARKING -> PARKING_FILL
                PoiKind.TRANSIT -> lineColour ?: TRANSIT_FILL
            }
        paint.color = fill
        val inner = r - 2.5f * d
        cv.drawCircle(cx, cx, inner, paint)
        val white = 0xFFFFFFFF.toInt()
        when {
            kind == PoiKind.FUEL && brand != null && brand.logo != 0 -> {
                ContextCompat.getDrawable(context, brand.logo)?.let { dr ->
                    val box = inner * 1.25f
                    val w = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
                    val h = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
                    val s = box / maxOf(w, h)
                    val bw = (w * s).toInt()
                    val bh = (h * s).toInt()
                    dr.setBounds((cx - bw / 2).toInt(), (cx - bh / 2).toInt(), (cx + bw / 2).toInt(), (cx + bh / 2).toInt())
                    dr.draw(cv)
                }
            }
            kind == PoiKind.FUEL && brand != null -> text(context, cv, brand.mark, cx, inner, brand.ink)
            kind == PoiKind.FUEL -> pump(cv, cx, inner, white)
            kind == PoiKind.EV -> bolt(cv, cx, inner, white)
            kind == PoiKind.PARKING -> text(context, cv, "P", cx, inner, white)
            else -> if (badge.isNullOrBlank()) train(cv, cx, inner, white) else text(context, cv, badge, cx, inner, white)
        }
        return bmp
    }

    private fun text(
        context: Context,
        cv: Canvas,
        s: String,
        cx: Float,
        inner: Float,
        colour: Int,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = colour
        p.typeface = ResourcesCompat.getFont(context, R.font.overpass_extrabold) ?: Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = inner * if (s.length <= 1) 1.25f else if (s.length == 2) 0.95f else 0.72f
        val fm = p.fontMetrics
        cv.drawText(s, cx, cx - (fm.ascent + fm.descent) / 2, p)
    }

    private fun bolt(
        cv: Canvas,
        cx: Float,
        inner: Float,
        colour: Int,
    ) {
        val u = inner / 10f
        val path =
            Path().apply {
                moveTo(cx + 1.5f * u, cx - 7 * u)
                lineTo(cx - 4.5f * u, cx + 1 * u)
                lineTo(cx - 0.5f * u, cx + 1 * u)
                lineTo(cx - 1.5f * u, cx + 7 * u)
                lineTo(cx + 4.5f * u, cx - 1 * u)
                lineTo(cx + 0.5f * u, cx - 1 * u)
                close()
            }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
    }

    private fun pump(
        cv: Canvas,
        cx: Float,
        inner: Float,
        colour: Int,
    ) {
        val u = inner / 10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        cv.drawRoundRect(RectF(cx - 5 * u, cx - 6 * u, cx + 2 * u, cx + 6 * u), u, u, p)
        p.color = FUEL_FILL
        cv.drawRect(RectF(cx - 3.5f * u, cx - 4.5f * u, cx + 0.5f * u, cx - 1 * u), p)
        p.color = colour
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f * u
        p.strokeCap = Paint.Cap.ROUND
        val hose =
            Path().apply {
                moveTo(cx + 2 * u, cx - 3 * u)
                lineTo(cx + 4.5f * u, cx - 1 * u)
                lineTo(cx + 4.5f * u, cx + 3.5f * u)
                quadTo(cx + 4.5f * u, cx + 5 * u, cx + 3.2f * u, cx + 5 * u)
            }
        cv.drawPath(hose, p)
    }

    private fun train(
        cv: Canvas,
        cx: Float,
        inner: Float,
        colour: Int,
    ) {
        val u = inner / 10f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        cv.drawRoundRect(RectF(cx - 4.5f * u, cx - 6 * u, cx + 4.5f * u, cx + 4 * u), 2 * u, 2 * u, p)
        p.color = TRANSIT_FILL
        cv.drawRect(RectF(cx - 3 * u, cx - 4.5f * u, cx + 3 * u, cx - 1 * u), p)
        p.color = colour
        p.strokeWidth = 1.2f * u
        p.strokeCap = Paint.Cap.ROUND
        cv.drawLine(cx - 3 * u, cx + 4.5f * u, cx - 5 * u, cx + 7 * u, p)
        cv.drawLine(cx + 3 * u, cx + 4.5f * u, cx + 5 * u, cx + 7 * u, p)
    }
}
