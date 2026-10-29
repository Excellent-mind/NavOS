package com.excellentmindblow.navos.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** The pin for a spot the driver long-pressed or tapped on the home map. */
object DropPin {
    private const val SRC = "navos-drop"
    private const val ICON = "navos-drop-pin"

    fun install(
        context: Context,
        style: Style,
    ) {
        if (style.getSource(SRC) != null) return
        style.addImage(ICON, draw(context))
        style.addSource(GeoJsonSource(SRC))
        style.addLayer(
            SymbolLayer(SRC, SRC).withProperties(
                PropertyFactory.iconImage(ICON),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    fun show(
        style: Style,
        lat: Double?,
        lon: Double?,
    ) {
        val f = if (lat == null || lon == null) emptyList() else listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        style.getSourceAs<GeoJsonSource>(SRC)?.setGeoJson(FeatureCollection.fromFeatures(f))
    }

    /** A red map pin: round head with a white dot, pointed foot, soft shadow. */
    private fun draw(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (34 * d).toInt()
        val h = (46 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val r = 13 * d
        val cy = r + 3 * d
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x33000000
        c.drawOval(cx - 6 * d, h - 5 * d, cx + 6 * d, h - 1 * d, p)
        val pin =
            Path().apply {
                addCircle(cx, cy, r, Path.Direction.CW)
                moveTo(cx - r * 0.62f, cy + r * 0.78f)
                lineTo(cx, h - 3 * d)
                lineTo(cx + r * 0.62f, cy + r * 0.78f)
                close()
            }
        p.color = 0xFFD2283E.toInt()
        p.setShadowLayer(2.5f * d, 0f, 1f * d, 0x55000000)
        c.drawPath(pin, p)
        p.clearShadowLayer()
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, 4.8f * d, p)
        return bmp
    }
}
