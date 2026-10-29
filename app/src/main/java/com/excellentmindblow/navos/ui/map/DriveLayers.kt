package com.excellentmindblow.navos.ui.map

import com.excellentmindblow.navos.traffic.RoadReports
import com.excellentmindblow.navos.traffic.SmartLanes
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Drive-only map layers: road reports on the route as coloured dots, and open Smart Lanes painted green. */
object DriveLayers {
    private const val SRC_REPORTS = "navos-reports"
    private const val SRC_LANES = "navos-smartlanes"
    private const val SRC_CAMS = "navos-cameras"
    private const val SRC_TURN = "navos-turn"
    private const val SRC_TURN_HEAD = "navos-turn-head"

    fun install(style: Style) {
        if (style.getSource(SRC_REPORTS) != null) return
        style.addSource(GeoJsonSource(SRC_LANES))
        style.addSource(GeoJsonSource(SRC_REPORTS))
        val below = style.layers.firstOrNull { it.id == "navos-route-case" }?.id ?: style.layers.firstOrNull { it.id == "road-label" }?.id
        // The left (emergency) lane, one lane wide; lines run the lane's way, so negative offset = left.
        val lane =
            LineLayer("navos-smartlanes", SRC_LANES).withProperties(
                PropertyFactory.lineColor("#00693D"),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineWidth(metres(3.0)),
                PropertyFactory.lineOffset(metres(-5.6)),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        if (below != null) style.addLayerBelow(lane, below) else style.addLayer(lane)
        // The next turn painted on the route: a white band along the line with an arrowhead, like
        // the arrows painted on the road before a junction.
        style.addSource(GeoJsonSource(SRC_TURN))
        style.addSource(GeoJsonSource(SRC_TURN_HEAD))
        val turn =
            LineLayer(SRC_TURN, SRC_TURN).withProperties(
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(RouteLayers.width(3f, 5f, 9f, 22f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        if (style.getLayer(RouteLayers.LAYER_SEL) != null) style.addLayerAbove(turn, RouteLayers.LAYER_SEL) else style.addLayer(turn)
        style.addLayerAbove(
            org.maplibre.android.style.layers.SymbolLayer(SRC_TURN_HEAD, SRC_TURN_HEAD).withProperties(
                PropertyFactory.iconImage("navos-turn-head"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconRotate(Expression.get("b")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconSize(Expression.interpolate(Expression.exponential(1.5f), Expression.zoom(), Expression.stop(14, 0.35f), Expression.stop(17, 0.7f), Expression.stop(19, 1.4f))),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
            SRC_TURN,
        )
        style.addSource(GeoJsonSource(SRC_CAMS))
        style.addLayer(
            org.maplibre.android.style.layers.SymbolLayer(SRC_CAMS, SRC_CAMS).withProperties(
                PropertyFactory.iconImage("navos-cam"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        style.addLayer(
            CircleLayer("navos-reports", SRC_REPORTS).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Expression.get("c")),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            ),
        )
    }

    fun show(
        style: Style,
        reports: List<RoadReports.OnRoute>,
        lanes: List<SmartLanes.Lane>,
    ) {
        style.getSourceAs<GeoJsonSource>(SRC_REPORTS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                reports.map { r ->
                    Feature.fromGeometry(Point.fromLngLat(r.report.longitude, r.report.latitude)).apply { addStringProperty("c", colour(r.report.kind)) }
                },
            ),
        )
        style.getSourceAs<GeoJsonSource>(SRC_LANES)?.setGeoJson(
            FeatureCollection.fromFeatures(lanes.map { l -> Feature.fromGeometry(LineString.fromLngLats(l.line.map { Point.fromLngLat(it.longitude, it.latitude) })) }),
        )
    }

    /** The turn arrow: [line] along the route through the turn, the head at its end pointing [bearing]; null hides it. */
    fun showTurn(
        style: Style,
        line: List<com.excellentmindblow.navos.routing.RoutePoint>?,
        bearing: Double,
    ) {
        val pts = line?.takeIf { it.size >= 2 }
        style.getSourceAs<GeoJsonSource>(SRC_TURN)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(pts?.let { Feature.fromGeometry(LineString.fromLngLats(it.map { p -> Point.fromLngLat(p.longitude, p.latitude) })) })),
        )
        style.getSourceAs<GeoJsonSource>(SRC_TURN_HEAD)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(pts?.last()?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)).apply { addNumberProperty("b", bearing) } }),
            ),
        )
    }

    /** Speed cameras on the route. */
    fun showCameras(
        style: Style,
        cams: List<com.excellentmindblow.navos.traffic.SpeedCameras.OnRoute>,
    ) {
        style.getSourceAs<GeoJsonSource>(SRC_CAMS)?.setGeoJson(
            FeatureCollection.fromFeatures(cams.map { Feature.fromGeometry(Point.fromLngLat(it.camera.longitude, it.camera.latitude)) }),
        )
    }

    fun colour(k: RoadReports.Kind): String =
        when (k) {
            RoadReports.Kind.ACCIDENT -> "#D2283E"
            RoadReports.Kind.POLICE -> "#15181C"
            RoadReports.Kind.FLOOD -> "#2457A6"
            RoadReports.Kind.HAZARD -> "#F2A900"
            RoadReports.Kind.SMART_LANE -> "#00693D"
        }

    /** [m] metres on the ground at any zoom (Malaysia's latitude), at least a few px far out. */
    private fun metres(m: Double): Expression {
        val z0 = 40075016.686 * Math.cos(Math.toRadians(3.5)) / 512
        fun at(z: Int) = (m * Math.pow(2.0, z.toDouble()) / z0).toFloat()
        val sign = if (m < 0) -1f else 1f
        return Expression.interpolate(
            Expression.exponential(2f),
            Expression.zoom(),
            Expression.stop(12, sign * maxOf(kotlin.math.abs(at(12)), 2f)),
            Expression.stop(15, sign * maxOf(kotlin.math.abs(at(15)), 3f)),
            Expression.stop(22, at(22)),
        )
    }
}
