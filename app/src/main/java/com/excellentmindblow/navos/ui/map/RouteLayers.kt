package com.excellentmindblow.navos.ui.map

import com.excellentmindblow.navos.routing.Route
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The route lines on the map: the chosen route in blue with a white casing,
 * the others in grey under it. Added under the road labels, so names stay
 * readable; real lane width when zoomed right in.
 */
object RouteLayers {
    private const val SRC_SEL = "navos-route"
    private const val SRC_ALT = "navos-route-alt"
    private const val LAYER_ALT = "navos-route-alt"
    private const val LAYER_CASE = "navos-route-case"
    const val LAYER_SEL = "navos-route"

    /** Adds the (empty) sources and layers; call after every style load. */
    fun install(
        style: Style,
        routeColor: Int,
        altColor: Int,
        caseColor: Int,
    ) {
        if (style.getSource(SRC_SEL) != null) return
        selColor = routeColor
        caseCol = caseColor
        style.addSource(GeoJsonSource(SRC_ALT))
        // Line metrics: lets the line hide the part already driven (line-progress gradient).
        style.addSource(GeoJsonSource(SRC_SEL, org.maplibre.android.style.sources.GeoJsonOptions().withLineMetrics(true)))
        val below = style.layers.firstOrNull { it.id == "road-label" }?.id
        fun add(l: LineLayer) = if (below != null) style.addLayerBelow(l, below) else style.addLayer(l)
        add(
            LineLayer(LAYER_ALT, SRC_ALT).withProperties(
                PropertyFactory.lineColor(altColor),
                PropertyFactory.lineWidth(width(5f, 8f, 16f, 40f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        add(
            LineLayer(LAYER_CASE, SRC_SEL).withProperties(
                PropertyFactory.lineColor(caseColor),
                PropertyFactory.lineWidth(width(9f, 14f, 26f, 56f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        add(
            LineLayer(LAYER_SEL, SRC_SEL).withProperties(
                PropertyFactory.lineColor(routeColor),
                PropertyFactory.lineWidth(width(6f, 10f, 19f, 46f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }

    private var selColor = 0
    private var caseCol = 0

    /**
     * Hides the route behind you: [fraction] (0..1) of the line's length is
     * already driven. 0 shows it all (route choice).
     */
    fun driven(
        style: Style,
        fraction: Double,
    ) {
        val f = fraction.coerceIn(0.0, 0.999).toFloat()
        val clear = Expression.color(android.graphics.Color.TRANSPARENT)
        fun grad(c: Int) =
            if (f <= 0.0005f) {
                Expression.step(Expression.lineProgress(), Expression.color(c), Expression.stop(1f, Expression.color(c)))
            } else {
                Expression.step(Expression.lineProgress(), clear, Expression.stop(f, Expression.color(c)))
            }
        style.getLayer(LAYER_SEL)?.setProperties(PropertyFactory.lineGradient(grad(selColor)))
        style.getLayer(LAYER_CASE)?.setProperties(PropertyFactory.lineGradient(grad(caseCol)))
    }

    /** Shows [selected] and [others] (null / empty = nothing). */
    fun show(
        style: Style,
        selected: Route?,
        others: List<Route>,
    ) {
        style.getSourceAs<GeoJsonSource>(SRC_SEL)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(selected?.let(::feature))))
        style.getSourceAs<GeoJsonSource>(SRC_ALT)?.setGeoJson(FeatureCollection.fromFeatures(others.map(::feature)))
    }

    /** Fits the camera to [routes], keeping clear of the top card and the bottom sheet. */
    fun frame(
        map: MapLibreMap,
        routes: List<Route>,
        topPx: Int,
        bottomPx: Int,
        sidePx: Int,
    ) {
        val pts = routes.flatMap { it.geometry }
        if (pts.size < 2) return
        val b = LatLngBounds.Builder()
        pts.forEach { b.include(LatLng(it.latitude, it.longitude)) }
        runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), sidePx, topPx, sidePx, bottomPx), 700) }
    }

    private fun feature(r: Route) = Feature.fromGeometry(LineString.fromLngLats(r.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }))

    internal fun width(
        z10: Float,
        z14: Float,
        z18: Float,
        z20: Float,
    ): Expression =
        Expression.interpolate(
            Expression.exponential(1.5f),
            Expression.zoom(),
            Expression.stop(10, z10),
            Expression.stop(14, z14),
            Expression.stop(18, z18),
            Expression.stop(20, z20),
        )
}
