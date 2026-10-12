package com.excellentmindblow.navos.car

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.routing.Route
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Draws the NavOS map (same MapLibre engine + OpenFreeMap styles as the phone)
 * onto the car's screen. Android Auto hands us a raw Surface; MapLibre needs a
 * View, so we host a MapView inside a Presentation on a VirtualDisplay that
 * renders into that Surface.
 */
class CarMapRenderer(private val carContext: CarContext) : SurfaceCallback {
    private companion object {
        const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK = "https://tiles.openfreemap.org/styles/fiord"
        const val ROUTE_SRC = "car-route-src"
        const val ROUTE_LAYER = "car-route-layer"
        const val PIN_SRC = "car-pin-src"
        const val PIN_LAYER = "car-pin-layer"
        const val PIN_IMAGE = "car-pin-image"
        const val PUCK_SRC = "car-puck-src"
        const val PUCK_LAYER = "car-puck-layer"
        const val FOLLOW_ZOOM = 16.5
        const val FOLLOW_TILT = 55.0
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private var width = 0
    private var height = 0
    private var route: Route? = null
    private var lastBearing = 0f
    private var visibleArea: Rect? = null

    /** Latest GPS fix seen by the car side (also used to bias searches). */
    var lastLocation: Location? = null
        private set

    // ---- SurfaceCallback ----------------------------------------------------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        release()
        val surface = surfaceContainer.surface ?: return
        width = surfaceContainer.width
        height = surfaceContainer.height

        val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val vd =
            displayManager.createVirtualDisplay(
                "navos-car-map",
                width,
                height,
                surfaceContainer.dpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
        virtualDisplay = vd

        MapLibre.getInstance(carContext.applicationContext)
        val p = Presentation(carContext, vd.display)
        val options = MapLibreMapOptions.createFromAttributes(p.context, null).textureMode(true)
        val mv = MapView(p.context, options)
        p.setContentView(mv)
        p.show()
        mv.onCreate(null)
        mv.onStart()
        mv.onResume()
        presentation = p
        mapView = mv
        mv.getMapAsync { m ->
            map = m
            m.uiSettings.setAllGesturesEnabled(false)
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            applyPadding()
            loadStyle(m)
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        applyPadding()
    }

    // Keep the "you are here" point inside the part of the screen the turn
    // banner / buttons don't cover.
    private fun applyPadding() {
        val area = visibleArea ?: return
        map?.setPadding(
            area.left,
            area.top,
            (width - area.right).coerceAtLeast(0),
            (height - area.bottom).coerceAtLeast(0),
        )
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        release()
    }

    // ---- State pushed in from the screens -------------------------------------

    fun setRoute(r: Route) {
        route = r
        applyRoute()
    }

    fun clearRoute() {
        route = null
        applyRoute()
    }

    fun updateLocation(location: Location) {
        lastLocation = location
        if (location.hasBearing() && location.speed > 1.5f) lastBearing = location.bearing
        val st = style ?: return
        st.getSourceAs<GeoJsonSource>(PUCK_SRC)
            ?.setGeoJson(Point.fromLngLat(location.longitude, location.latitude))
        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(FOLLOW_ZOOM)
                    .tilt(FOLLOW_TILT)
                    .bearing(lastBearing.toDouble())
                    .build(),
            ),
            900,
        )
    }

    fun release() {
        try {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        } catch (e: Exception) {
            // map already torn down
        }
        presentation?.dismiss()
        virtualDisplay?.release()
        mapView = null
        presentation = null
        virtualDisplay = null
        map = null
        style = null
    }

    // ---- Internals ------------------------------------------------------------

    private fun loadStyle(m: MapLibreMap) {
        val url = if (carContext.isDarkMode) STYLE_DARK else STYLE_LIGHT
        m.setStyle(Style.Builder().fromUri(url)) { st ->
            style = st
            ContextCompat.getDrawable(carContext, R.drawable.ic_destination_pin)
                ?.toBitmap()
                ?.let { st.addImage(PIN_IMAGE, it) }

            st.addSource(GeoJsonSource(ROUTE_SRC))
            st.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
                    PropertyFactory.lineColor("#1CB0F6"),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            st.addSource(GeoJsonSource(PIN_SRC))
            st.addLayer(
                SymbolLayer(PIN_LAYER, PIN_SRC).withProperties(
                    PropertyFactory.iconImage(PIN_IMAGE),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconSize(1.4f),
                ),
            )
            st.addSource(GeoJsonSource(PUCK_SRC))
            st.addLayer(
                CircleLayer(PUCK_LAYER, PUCK_SRC).withProperties(
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleColor("#1CB0F6"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f),
                ),
            )
            applyRoute()
            lastLocation?.let { updateLocation(it) }
        }
    }

    private fun applyRoute() {
        val st = style ?: return
        val r = route
        val routeSource = st.getSourceAs<GeoJsonSource>(ROUTE_SRC)
        val pinSource = st.getSourceAs<GeoJsonSource>(PIN_SRC)
        if (r == null || r.geometry.size < 2) {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            pinSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        routeSource?.setGeoJson(
            LineString.fromLngLats(r.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }),
        )
        val end = r.geometry.last()
        pinSource?.setGeoJson(Point.fromLngLat(end.longitude, end.latitude))
    }
}
