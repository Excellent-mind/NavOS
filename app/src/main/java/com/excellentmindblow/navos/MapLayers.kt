package com.excellentmindblow.navos

import android.content.Context
import com.excellentmindblow.navos.poi.FuelBrand
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

// Ids of the sources, layers and icons NavOS adds on top of the base map style.
internal const val ROUTE_SOURCE_ID = "navos-route-source"
internal const val ROUTE_CASING_LAYER_ID = "navos-route-casing-layer"
internal const val ROUTE_LAYER_ID = "navos-route-layer"
internal const val TRAFFIC_SOURCE_ID = "navos-traffic-source"
internal const val TRAFFIC_LAYER_ID = "navos-traffic-layer"
internal const val TRAFFIC_LEVEL_PROPERTY = "lvl"
internal const val ROUTE_CHEVRON_LAYER_ID = "navos-route-chevron-layer"
internal const val ROUTE_CHEVRON_ICON_ID = "navos-route-chevron-icon"
internal const val ROUTE_PASSED_SOURCE_ID = "navos-route-passed-source"
internal const val ROUTE_PASSED_LAYER_ID = "navos-route-passed-layer"
internal const val ALT_ROUTE_SOURCE_ID = "navos-alt-route-source"
internal const val ALT_ROUTE_LAYER_ID = "navos-alt-route-layer"
internal const val DESTINATION_SOURCE_ID = "navos-destination-source"
internal const val DESTINATION_LAYER_ID = "navos-destination-layer"
internal const val DESTINATION_ICON_ID = "navos-destination-icon"
internal const val STOPS_SOURCE_ID = "navos-stops-source"
internal const val STOPS_LAYER_ID = "navos-stops-layer"
internal const val STOP_ICON_PREFIX = "navos-stop-"
internal const val STOP_ICON_COUNT = 9
internal const val SHOW_3D_BUILDINGS = true
internal const val TURN_SOURCE_ID = "navos-turn-source"
internal const val TURN_ARROW_LAYER_ID = "navos-turn-arrow-layer"
internal const val ROUTE_POI_SOURCE_ID = "navos-route-poi-source"
internal const val ROUTE_POI_LAYER_ID = "navos-route-poi-layer"
internal const val CAMERA_ZONE_SOURCE_ID = "navos-camera-zone-source"
internal const val CAMERA_ZONE_FILL_ID = "navos-camera-zone-fill"
internal const val CAMERA_ZONE_LINE_ID = "navos-camera-zone-line"
internal const val POI_ICON_TOLL = "navos-poi-toll"
internal const val POI_ICON_CAMERA = "navos-poi-camera"

/** Map image id of a road report's badge. */
internal fun reportIconId(kind: com.excellentmindblow.navos.traffic.RoadReports.Kind) = "navos-report-${kind.code}"
internal const val POI_SOURCE_ID = "navos-poi-source"
internal const val POI_LAYER_ID = "navos-poi-layer"
internal const val POI_ICON_PROPERTY = "poiIcon"
internal const val ROUTE_TIME_SOURCE_ID = "navos-route-time-source"
internal const val ROUTE_TIME_LAYER_ID = "navos-route-time-layer"
internal const val FUEL_PIN_ICON_ID = "navos-icon-fuel-pin"
internal const val EV_PIN_ICON_ID = "navos-icon-ev-pin"
internal const val PARKING_PIN_ICON_ID = "navos-icon-parking-pin"
internal const val TRANSIT_PIN_ICON_ID = "navos-icon-transit-pin"

/**
 * Adds NavOS's own sources and layers (3D buildings, route lines and
 * arrows, camera zones, toll / camera pins, turn names, destination and
 * stop pins, petrol / EV pins) to a freshly loaded map style, in the right
 * order relative to the style's own layers. MapLibre replaces everything on
 * a style switch, so this runs after every style load; the screen keeps the
 * returned [Sources] and draws into them.
 */
class MapLayers(
    private val context: Context,
) {
    class Sources(
        val route: GeoJsonSource,
        val routePassed: GeoJsonSource,
        val altRoute: GeoJsonSource,
        val routePoi: GeoJsonSource,
        val cameraZone: GeoJsonSource,
        val turnCallouts: GeoJsonSource,
        val destination: GeoJsonSource,
        val stops: GeoJsonSource,
        val poi: GeoJsonSource,
        val routeTimes: GeoJsonSource,
        val traffic: GeoJsonSource,
    )

    fun install(style: Style): Sources {
        // The base style's own road/POI name labels are symbol layers
        // further up its layer stack. style.addLayer() appends at the very
        // top by default, which was silently burying every label under our
        // route lines and the destination pin - inserting our route layers
        // below the first symbol layer keeps street names readable on top
        // of the route, matching how Google Maps/Waze draw it. (The
        // destination pin is deliberately NOT inserted this way - it's
        // meant to sit on top of labels too, like a real map pin.)
        // The base style also has icon-only symbol layers (one-way arrows)
        // that sit BEFORE its bridge road layers. Anchoring to the first
        // symbol layer of any kind therefore put the route underneath the
        // bridges, which painted over it - the blue line "vanished" on every
        // bridge/flyover. Anchor to the first symbol layer AFTER the last
        // bridge layer instead, so the route draws over bridges but still
        // under the street-name labels.
        val allLayers = style.layers
        val lastBridgeIndex = allLayers.indexOfLast { it.id.contains("bridge", ignoreCase = true) }
        val firstSymbolLayerId =
            allLayers.drop(lastBridgeIndex + 1).firstOrNull { it is SymbolLayer }?.id
                ?: allLayers.firstOrNull { it is SymbolLayer }?.id
        fun addBelowLabels(layer: Layer) {
            if (firstSymbolLayerId != null) {
                style.addLayerBelow(layer, firstSymbolLayerId)
            } else {
                style.addLayer(layer)
            }
        }

        // 3D buildings - OpenFreeMap's vector tiles already carry
        // building height data (OpenMapTiles schema: source-layer
        // "building", render_height/render_min_height in metres), the
        // style just never had a layer drawing them as extrusions. Only
        // visible once the camera is tilted (active nav, or a two-finger
        // tilt gesture while browsing) - flat top-down looks the same
        // either way, which is correct, not a sign this isn't working.
        // Wrapped defensively: if OpenFreeMap ever renames the source or
        // the fields are absent, this silently draws nothing rather than
        // crash.
        try {
            val buildingsLayer =
                FillExtrusionLayer("navos-3d-buildings", "openmaptiles")
                    .withSourceLayer("building")
                    .withProperties(
                        PropertyFactory.fillExtrusionColor("#D6D9E0"),
                        PropertyFactory.fillExtrusionOpacity(0.85f),
                        PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                        PropertyFactory.fillExtrusionBase(
                            Expression.switchCase(
                                Expression.has("render_min_height"),
                                Expression.toNumber(Expression.get("render_min_height")),
                                Expression.literal(0f),
                            ),
                        ),
                    )
            if (SHOW_3D_BUILDINGS) {
                buildingsLayer.setProperties(
                    PropertyFactory.visibility(if (MapDisplayPrefs.buildings3d) Property.VISIBLE else Property.NONE),
                )
                addBelowLabels(buildingsLayer)
            }
        } catch (e: Exception) {
            // Missing source/source-layer on whatever style version is
            // live - buildings just stay flat, nothing else breaks.
        }

        // Alternates first so the selected route's layers (added next) end
        // up drawn on top of them.
        val altSource = GeoJsonSource(ALT_ROUTE_SOURCE_ID)
        style.addSource(altSource)

        val altLayer =
            LineLayer(ALT_ROUTE_LAYER_ID, ALT_ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(color(R.color.sig_route_alt)),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(altLayer)

        val source = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(source)

        // A white casing under the blue line, same two-tone look Google
        // Maps draws its route in, instead of a single flat line.
        // The part of the route already driven: same width, but faded, drawn
        // beneath the live line (see trimRoute).
        val passedSource = GeoJsonSource(ROUTE_PASSED_SOURCE_ID)
        style.addSource(passedSource)
        val passedLayer =
            LineLayer(ROUTE_PASSED_LAYER_ID, ROUTE_PASSED_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(color(R.color.sig_route_passed)),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineWidth(routeWidthByZoom(6f, 11f, 19f, 29f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(passedLayer)

        val routeCasingLayer =
            LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(color(R.color.sig_route_casing)),
                PropertyFactory.lineWidth(routeWidthByZoom(9f, 15f, 26f, 38f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(routeCasingLayer)

        val layer =
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(color(R.color.sig_route)),
                PropertyFactory.lineWidth(routeWidthByZoom(6f, 11f, 19f, 29f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(layer)

        // Live traffic on top of the route line: amber where it's slow, red
        // where it's jammed (see TrafficFlow). Same width, so the line just
        // changes colour there, like Google Maps.
        val trafficSource = GeoJsonSource(TRAFFIC_SOURCE_ID)
        style.addSource(trafficSource)
        addBelowLabels(
            LineLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(
                    Expression.match(
                        Expression.get(TRAFFIC_LEVEL_PROPERTY),
                        Expression.color(color(R.color.sig_caution)),
                        Expression.stop("jam", Expression.color(color(R.color.sig_limit))),
                    ),
                ),
                PropertyFactory.lineWidth(routeWidthByZoom(6f, 11f, 19f, 29f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        // White direction chevrons painted INSIDE the blue lane, like Google Maps
        // and Waze. They are placed along the line itself, so each one is turned
        // to the exact direction of the lane at its spot (through bends too) and
        // lies flat on the road with the map's tilt instead of floating upright.
        style.addImage(ROUTE_CHEVRON_ICON_ID, MapIcons.routeChevron(color(R.color.sig_on_ink)))
        addBelowLabels(
            SymbolLayer(ROUTE_CHEVRON_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.symbolSpacing(62f),
                PropertyFactory.iconImage(ROUTE_CHEVRON_ICON_ID),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(11, 0.22f),
                        Expression.stop(15, 0.34f),
                        Expression.stop(19, 0.5f),
                    ),
                ),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconKeepUpright(false),
                PropertyFactory.visibility(if (MapDisplayPrefs.routeArrows) Property.VISIBLE else Property.NONE),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )

        // Turn callouts: a white arrow at every corner of the route plus a
        // blue name bubble above it, like Waze. Above the route line, below
        // the destination pin and the driver's arrow (added after this).
        // Toll plazas and speed cameras along the route (always visible).
        // Same pin look as the petrol / EV pins (see installPoiLayer).
        registerMapIcon(style, POI_ICON_TOLL, R.drawable.ic_poi_toll_pin)
        registerMapIcon(style, POI_ICON_CAMERA, R.drawable.ic_poi_camera_pin)
        for (kind in com.excellentmindblow.navos.traffic.RoadReports.Kind.entries) {
            style.addImage(reportIconId(kind), MapIcons.reportBadge(context, kind))
        }
        val poiSrc = GeoJsonSource(ROUTE_POI_SOURCE_ID)
        style.addSource(poiSrc)
        // 200 m alert zone around each speed camera (under the icons).
        val zoneSrc = GeoJsonSource(CAMERA_ZONE_SOURCE_ID)
        style.addSource(zoneSrc)
        addBelowLabels(
            FillLayer(CAMERA_ZONE_FILL_ID, CAMERA_ZONE_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(color(R.color.sig_limit)),
                PropertyFactory.fillOpacity(0.13f),
            ),
        )
        addBelowLabels(
            LineLayer(CAMERA_ZONE_LINE_ID, CAMERA_ZONE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(color(R.color.sig_limit)),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.75f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ),
        )
        style.addLayer(
            SymbolLayer(ROUTE_POI_LAYER_ID, ROUTE_POI_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM), // pin tip on the spot, like the petrol pins
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        val turnSrc = GeoJsonSource(TURN_SOURCE_ID)
        style.addSource(turnSrc)
        // One layer: the badge, with the road-name bubble above it. If two
        // corners are too close, the earlier one wins and the other is
        // hidden (instead of piling up); a name that doesn't fit is dropped
        // but its badge stays.
        style.addLayer(
            SymbolLayer(TURN_ARROW_LAYER_ID, TURN_SOURCE_ID).withProperties(
                // Road-name bubble only: the direction arrows are the chevrons
                // inside the blue lane (see ROUTE_CHEVRON_LAYER_ID).
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor(color(R.color.sig_on_ink)),
                PropertyFactory.textHaloColor(color(R.color.sig_ink)),
                PropertyFactory.textHaloWidth(5f),
                PropertyFactory.textMaxWidth(9f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                PropertyFactory.textOffset(arrayOf(0f, -0.9f)),
                PropertyFactory.textOptional(false),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textPadding(6f),
                PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.symbolSortKey(Expression.get("order")),
            ),
        )

        // Route preview: a time bubble on each route ("24 min"); its image is
        // drawn per route by MainActivity (MapIcons.timeBubble).
        val timeSrc = GeoJsonSource(ROUTE_TIME_SOURCE_ID)
        style.addSource(timeSrc)
        style.addLayer(
            SymbolLayer(ROUTE_TIME_LAYER_ID, ROUTE_TIME_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.symbolSortKey(Expression.get("order")),
            ),
        )

        // Destination pin - added last, with the plain addLayer() that
        // puts it above everything (including labels), same as a real pin.
        val pinBitmap = ContextCompat.getDrawable(context, R.drawable.ic_destination_pin)?.toBitmap()
        if (pinBitmap != null) {
            style.addImage(DESTINATION_ICON_ID, pinBitmap)
        }

        val destSource = GeoJsonSource(DESTINATION_SOURCE_ID)
        style.addSource(destSource)

        val destLayer =
            SymbolLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(DESTINATION_ICON_ID),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
            )
        style.addLayer(destLayer)

        // Numbered stop pins: drawn below the destination pin so it stays on top.
        for (n in 1..STOP_ICON_COUNT) style.addImage("$STOP_ICON_PREFIX$n", MapIcons.stopPin(context, n))
        val stopsSrc = GeoJsonSource(STOPS_SOURCE_ID)
        style.addSource(stopsSrc)
        style.addLayerBelow(
            SymbolLayer(STOPS_LAYER_ID, STOPS_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
            DESTINATION_LAYER_ID,
        )

        return Sources(
            route = source,
            routePassed = passedSource,
            altRoute = altSource,
            routePoi = poiSrc,
            cameraZone = zoneSrc,
            turnCallouts = turnSrc,
            destination = destSource,
            stops = stopsSrc,
            poi = installPoiLayer(style),
            routeTimes = timeSrc,
            traffic = trafficSource,
        )
    }

    /** Petrol (orange) and EV (green) pins - above street labels but below
     * the destination pin, so the destination always stays on top. */
    private fun installPoiLayer(style: Style): GeoJsonSource {
        registerMapIcon(style, FUEL_PIN_ICON_ID, R.drawable.ic_poi_fuel_pin)
        for (brand in FuelBrand.entries) style.addImage(brand.pinIconId, MapIcons.brandPin(context, brand))
        registerMapIcon(style, EV_PIN_ICON_ID, R.drawable.ic_poi_ev_pin)
        registerMapIcon(style, PARKING_PIN_ICON_ID, R.drawable.ic_poi_parking_pin)
        registerMapIcon(style, TRANSIT_PIN_ICON_ID, R.drawable.ic_poi_transit_pin)

        val src = GeoJsonSource(POI_SOURCE_ID)
        style.addSource(src)

        val layer =
            SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get(POI_ICON_PROPERTY)),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
            )
        style.addLayerBelow(layer, DESTINATION_LAYER_ID)
        return src
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun registerMapIcon(
        style: Style,
        iconId: String,
        drawableRes: Int,
    ) {
        val bitmap = ContextCompat.getDrawable(context, drawableRes)?.toBitmap()
        if (bitmap != null) {
            style.addImage(iconId, bitmap)
        }
    }

    /** Line width that grows with zoom (z10 / z14 / z18 / z20), so the
     * route stays thick and easy to follow when driving close-in. */
    private fun routeWidthByZoom(
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
