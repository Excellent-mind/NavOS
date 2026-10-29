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

    // ---- parked car

    private const val SRC_CAR = "navos-parked"
    const val LAYER_CAR = "navos-parked"
    private const val ICON_CAR = "navos-parked-car"

    fun installCar(
        context: Context,
        style: Style,
    ) {
        if (style.getSource(SRC_CAR) != null) return
        style.addImage(ICON_CAR, carPin(context))
        style.addSource(GeoJsonSource(SRC_CAR))
        style.addLayer(
            SymbolLayer(LAYER_CAR, SRC_CAR).withProperties(
                PropertyFactory.iconImage(ICON_CAR),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    fun showCar(
        style: Style,
        lat: Double?,
        lon: Double?,
    ) {
        val f = if (lat == null || lon == null) emptyList() else listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        style.getSourceAs<GeoJsonSource>(SRC_CAR)?.setGeoJson(FeatureCollection.fromFeatures(f))
    }

    /** Blue pin with a white "P" and a small car roof: where you parked. */
    private fun carPin(context: Context): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (36 * d).toInt()
        val h = (46 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        val r = 14 * d
        val cy = r + 3 * d
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val pin =
            Path().apply {
                addCircle(cx, cy, r, Path.Direction.CW)
                moveTo(cx - r * 0.6f, cy + r * 0.8f)
                lineTo(cx, h - 3 * d)
                lineTo(cx + r * 0.6f, cy + r * 0.8f)
                close()
            }
        p.color = 0xFF2457A6.toInt()
        p.setShadowLayer(2.5f * d, 0f, 1f * d, 0x55000000)
        c.drawPath(pin, p)
        p.clearShadowLayer()
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = 17 * d
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val fm = p.fontMetrics
        c.drawText("P", cx, cy - (fm.ascent + fm.descent) / 2, p)
        return bmp
    }
}
