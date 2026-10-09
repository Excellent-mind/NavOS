package com.excellentmindblow.navos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.navigation.NavigationEngine
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.navigation.VoiceNarrator
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Same free, no-API-key, production-safe tile style used by the earlier
// React Native build. https://openfreemap.org
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val ROUTE_SOURCE_ID = "navos-route-source"
private const val ROUTE_LAYER_ID = "navos-route-layer"
private const val ALT_ROUTE_SOURCE_ID = "navos-alt-route-source"
private const val ALT_ROUTE_LAYER_ID = "navos-alt-route-layer"
private const val ROUTE_INDEX_PROPERTY = "routeIndex"

private const val NAV_TILT_DEGREES = 55.0
private const val NAV_ZOOM = 18.0

/** Where the app currently is in the search -> preview -> drive flow. */
private enum class NavMode { IDLE, PREVIEW, ACTIVE }

/**
 * Native Android entry point. MapView lifecycle forwarding below:
 * MapLibre's MapView needs every Activity lifecycle callback forwarded to
 * it manually, unlike a typical Android view.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var searchInput: EditText
    private lateinit var searchResultsList: ListView
    private lateinit var routePreviewCard: View
    private lateinit var routePreviewSummary: TextView
    private lateinit var routePreviewHint: TextView
    private lateinit var activeNavBar: View
    private lateinit var turnBanner: TextView
    private lateinit var turnIcon: ImageView

    private var map: MapLibreMap? = null
    private var locationTracker: LiveLocationTracker? = null
    private var hasCenteredOnFirstFix = false
    private var lastKnownLocation: Location? = null

    private var routeSource: GeoJsonSource? = null
    private var altRouteSource: GeoJsonSource? = null

    private var navMode = NavMode.IDLE
    private var routeOptions: List<Route> = emptyList()
    private var selectedRouteIndex = 0

    /** Only non-null while NavMode.ACTIVE - gates feeding GPS fixes into
     * the navigation engine. */
    private var activeRoute: Route? = null

    private lateinit var navigationEngine: NavigationEngine
    private lateinit var voiceNarrator: VoiceNarrator

    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var pendingSearch: Runnable? = null
    private var currentResults: List<GeocodeResult> = emptyList()

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLocationTracking()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before any MapView touches its native state.
        MapLibre.getInstance(applicationContext)

        super.onCreate(savedInstanceState)

        // Classic, opt-in "draw behind the status bar" (available since API
        // 21, fully under our control) for a true full-screen map - NOT the
        // same thing as Android 15+'s mandatory edge-to-edge enforcement
        // that caused the real crash in the old React Native build. We
        // deliberately target SDK 34 so that enforcement never applies;
        // this is just making the status bar transparent on purpose.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_main)

        voiceNarrator = VoiceNarrator(this)
        navigationEngine =
            NavigationEngine(
                onState = { state -> onNavigationState(state) },
                onAnnounce = { key, text -> voiceNarrator.speak(key, text) },
            )

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { loadedMap ->
            map = loadedMap

            // MapLibre draws a rotation compass top-right by default, right
            // where our search bar sits, so it ends up half-hidden behind
            // it. We don't track map bearing in free-map mode (cameraMode
            // is NONE), so the compass has nothing useful to show there -
            // drop it. Active navigation gets its own rotating, tilted
            // camera instead (see beginActiveNavigation()).
            loadedMap.uiSettings.isCompassEnabled = false

            loadedMap.addOnMapClickListener { latLng -> onMapTapped(loadedMap, latLng) }

            loadedMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                enableLocationComponent(loadedMap, style)
                setUpRouteLayers(style)
                ensureLocationPermissionThenTrack()
            }
        }

        setUpSearch()
        setUpRouteControls()
        setUpSettingsDrawer()
    }

    private fun setUpSettingsDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        findViewById<Switch>(R.id.voiceGuidanceSwitch).setOnCheckedChangeListener { _, isChecked ->
            voiceNarrator.enabled = isChecked
        }

        // "Dark" is disabled in the layout until we've confirmed a working
        // dark style URL to switch to - nothing more to wire up here yet.
    }

    private fun setUpSearch() {
        searchInput = findViewById(R.id.searchInput)
        searchResultsList = findViewById(R.id.searchResultsList)

        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    scheduleSearch(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        searchResultsList.setOnItemClickListener { _, _, position, _ ->
            val result = currentResults.getOrNull(position) ?: return@setOnItemClickListener
            onDestinationSelected(result)
        }
    }

    private fun setUpRouteControls() {
        routePreviewCard = findViewById(R.id.routePreviewCard)
        routePreviewSummary = findViewById(R.id.routePreviewSummary)
        routePreviewHint = findViewById(R.id.routePreviewHint)
        activeNavBar = findViewById(R.id.activeNavBar)
        turnBanner = findViewById(R.id.turnBanner)
        turnIcon = findViewById(R.id.turnIcon)

        findViewById<android.widget.Button>(R.id.startNavigationButton).setOnClickListener {
            beginActiveNavigation()
        }
        findViewById<android.widget.Button>(R.id.cancelRouteButton).setOnClickListener {
            cancelRoutePreview()
        }
        findViewById<ImageButton>(R.id.stopNavigationButton).setOnClickListener {
            endActiveNavigation()
        }
    }

    private fun scheduleSearch(query: String) {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }

        if (query.trim().length < 3) {
            currentResults = emptyList()
            searchResultsList.visibility = View.GONE
            return
        }

        val runnable = Runnable { runSearch(query) }
        pendingSearch = runnable
        // Debounce so we don't hammer Nominatim's free, rate-limited
        // (~1 req/sec) endpoint on every keystroke.
        mainHandler.postDelayed(runnable, 400)
    }

    private fun runSearch(query: String) {
        val bias = lastKnownLocation
        searchExecutor.execute {
            val results =
                try {
                    GeocodingService.search(query, bias?.latitude, bias?.longitude)
                } catch (e: Exception) {
                    emptyList()
                }
            mainHandler.post { showSearchResults(results) }
        }
    }

    private fun showSearchResults(results: List<GeocodeResult>) {
        currentResults = results
        if (results.isEmpty()) {
            searchResultsList.visibility = View.GONE
            return
        }
        searchResultsList.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, results.map { it.label })
        searchResultsList.visibility = View.VISIBLE
    }

    private fun onDestinationSelected(result: GeocodeResult) {
        searchResultsList.visibility = View.GONE
        currentResults = emptyList()
        searchInput.setText(result.label)
        searchInput.clearFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

        // A new destination picked mid-drive replaces whatever was active.
        if (navMode == NavMode.ACTIVE) endActiveNavigation()

        map?.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(result.latitude, result.longitude))
                    .zoom(15.0)
                    .build(),
            ),
            600,
        )

        fetchRoute(result)
    }

    private fun setUpRouteLayers(style: Style) {
        // Alternates first so the selected route's layer (added next) ends
        // up drawn on top of them.
        val altSource = GeoJsonSource(ALT_ROUTE_SOURCE_ID)
        style.addSource(altSource)
        altRouteSource = altSource

        val altLayer =
            LineLayer(ALT_ROUTE_LAYER_ID, ALT_ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#9AA5B1")),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        style.addLayer(altLayer)

        val source = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(source)
        routeSource = source

        val layer =
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1CB0F6")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        style.addLayer(layer)
    }

    private fun fetchRoute(destination: GeocodeResult) {
        val origin = lastKnownLocation
        if (origin == null) {
            Toast.makeText(this, "Still waiting for a GPS fix - can't calculate a route yet", Toast.LENGTH_SHORT).show()
            return
        }

        val originPoint = RoutePoint(origin.latitude, origin.longitude)
        val destinationPoint = RoutePoint(destination.latitude, destination.longitude)

        searchExecutor.execute {
            var routes: List<Route> = emptyList()
            try {
                routes = RoutingService.calculateRoutes(originPoint, destinationPoint)
            } catch (e: Exception) {
                // handled below - empty list means "couldn't route"
            }
            mainHandler.post {
                if (routes.isNotEmpty()) {
                    routeOptions = routes
                    selectedRouteIndex = 0
                    showRoutePreview()
                } else {
                    Toast.makeText(this, "Couldn't calculate a route to that destination", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---- Route preview (pre-navigation) --------------------------------

    private fun showRoutePreview() {
        navMode = NavMode.PREVIEW
        activeNavBar.visibility = View.GONE
        redrawRouteLines()

        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        val bounds = LatLngBounds.Builder()
        selected.geometry.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120, 120, 120, 460), 800)

        updateRoutePreviewSummary()
        routePreviewCard.visibility = View.VISIBLE
    }

    private fun updateRoutePreviewSummary() {
        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        val km = selected.distanceMeters / 1000.0
        val min = selected.durationSeconds / 60.0
        routePreviewSummary.text = String.format(Locale.US, "%.1f km • %.0f min", km, min)
        routePreviewHint.text =
            if (routeOptions.size > 1) {
                "${routeOptions.size} routes found - tap a grey route to switch"
            } else {
                "Fastest route available"
            }
    }

    /** Redraws the map lines for the current routeOptions/selectedRouteIndex:
     * the selected route in blue on top, every other option in grey
     * underneath (each tagged with its index so a tap can select it). */
    private fun redrawRouteLines() {
        val routes = routeOptions
        val selected = routes.getOrNull(selectedRouteIndex) ?: return

        val altFeatures =
            routes.mapIndexedNotNull { index, route ->
                if (index == selectedRouteIndex) {
                    null
                } else {
                    val points = route.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
                    if (points.size < 2) {
                        null
                    } else {
                        Feature.fromGeometry(LineString.fromLngLats(points)).also {
                            it.addNumberProperty(ROUTE_INDEX_PROPERTY, index)
                        }
                    }
                }
            }
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(altFeatures))

        val selectedPoints = selected.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (selectedPoints.size >= 2) {
            routeSource?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(selectedPoints)))
        }
    }

    private fun onMapTapped(
        map: MapLibreMap,
        latLng: LatLng,
    ): Boolean {
        if (navMode != NavMode.PREVIEW) return false
        val screenPoint = map.projection.toScreenLocation(latLng)
        val tapped =
            try {
                map.queryRenderedFeatures(screenPoint, ALT_ROUTE_LAYER_ID)
            } catch (e: Exception) {
                emptyList()
            }
        val index = tapped.firstOrNull()?.getNumberProperty(ROUTE_INDEX_PROPERTY)?.toInt() ?: return false
        selectRoute(index)
        return true
    }

    private fun selectRoute(index: Int) {
        if (navMode != NavMode.PREVIEW) return
        if (index !in routeOptions.indices || index == selectedRouteIndex) return
        selectedRouteIndex = index
        redrawRouteLines()
        updateRoutePreviewSummary()
    }

    private fun cancelRoutePreview() {
        navMode = NavMode.IDLE
        routeOptions = emptyList()
        routePreviewCard.visibility = View.GONE
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    // ---- Active (driving) navigation ------------------------------------

    private fun beginActiveNavigation() {
        val route = routeOptions.getOrNull(selectedRouteIndex) ?: return

        navMode = NavMode.ACTIVE
        routePreviewCard.visibility = View.GONE
        // Only the chosen route stays on screen once driving - alternates
        // were just for picking, not useful mid-drive.
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

        activeRoute = route
        navigationEngine.setRoute(route)
        voiceNarrator.resetGuard()
        activeNavBar.visibility = View.VISIBLE
        turnBanner.text = "Starting navigation..."

        // Waze-style 3D follow camera. NOTE: we deliberately do NOT use
        // LocationComponent's CameraMode.TRACKING_GPS here - it resets
        // tilt back to flat on every GPS update (confirmed on-device: the
        // map never actually tilted with it on), so instead we drive the
        // camera ourselves on every location update below, which lets us
        // hold tilt/zoom/bearing exactly where we want them.
        map?.locationComponent?.apply {
            renderMode = RenderMode.GPS
            cameraMode = CameraMode.NONE
        }

        lastKnownLocation?.let { location ->
            updateNavCamera(location)
            navigationEngine.onLocation(RoutePoint(location.latitude, location.longitude))
        }
    }

    /** Keeps the 3D follow camera (tilt + zoom + heading) locked on the
     * driver's position during active navigation. Called on every GPS fix
     * instead of leaning on LocationComponent's own camera tracking. */
    private fun updateNavCamera(location: Location) {
        val bearing =
            if (location.hasBearing()) {
                location.bearing.toDouble()
            } else {
                map?.cameraPosition?.bearing ?: 0.0
            }
        map?.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .tilt(NAV_TILT_DEGREES)
                    .zoom(NAV_ZOOM)
                    .bearing(bearing)
                    .build(),
            ),
            1000,
        )
    }

    private fun endActiveNavigation() {
        navMode = NavMode.IDLE
        activeRoute = null
        activeNavBar.visibility = View.GONE
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        routeOptions = emptyList()

        map?.locationComponent?.apply {
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.COMPASS
        }

        val location = lastKnownLocation
        if (location != null) {
            map?.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition
                        .Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .tilt(0.0)
                        .zoom(15.0)
                        .build(),
                ),
                600,
            )
        }
    }

    private fun onNavigationState(state: NavigationState) {
        val distanceLabel =
            if (state.distanceToNextTurnMeters >= 1000) {
                String.format(Locale.US, "%.1f km", state.distanceToNextTurnMeters / 1000)
            } else {
                "${state.distanceToNextTurnMeters.toInt()} m"
            }
        val remainingKm = state.distanceRemainingMeters / 1000.0
        val remainingMin = state.durationRemainingSeconds / 60.0
        val suffix = if (state.offRoute) " (off route - recalculating soon)" else ""
        turnBanner.text =
            "${state.instruction} • $distanceLabel$suffix\n" +
            String.format(Locale.US, "%.1f km • %.0f min left", remainingKm, remainingMin)

        val (iconRes, rotation) = iconForManeuver(state.maneuverType, state.modifier)
        turnIcon.setImageResource(iconRes)
        turnIcon.rotation = rotation

        if (state.isArrival && state.distanceToNextTurnMeters <= 30) {
            endActiveNavigation()
        }
    }

    /** Picks a turn-icon drawable + rotation for a maneuver. Distinct
     * glyphs for u-turn/roundabout/arrival; every other maneuver reuses
     * one directional arrow rotated to the right angle - the same
     * approach professional nav apps use rather than drawing a separate
     * icon per angle. */
    private fun iconForManeuver(
        maneuverType: String,
        modifier: String?,
    ): Pair<Int, Float> {
        if (maneuverType == "arrive") return R.drawable.ic_nav_flag to 0f
        if (maneuverType == "roundabout" || maneuverType == "rotary") return R.drawable.ic_nav_roundabout to 0f
        if (maneuverType == "uturn" || modifier == "uturn") return R.drawable.ic_nav_uturn to 0f

        val rotation =
            when (modifier) {
                "sharp right" -> 135f
                "right" -> 90f
                "slight right" -> 45f
                "slight left" -> -45f
                "left" -> -90f
                "sharp left" -> -135f
                else -> 0f // straight / depart / continue / new name / merge / fork / ramps
            }
        return R.drawable.ic_nav_arrow to rotation
    }

    // ---- Location / map plumbing ----------------------------------------

    private fun ensureLocationPermissionThenTrack() {
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startLocationTracking()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationTracking() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val tracker =
            LiveLocationTracker(locationManager) { location ->
                onLocationUpdate(location)
            }
        locationTracker = tracker
        tracker.start()
    }

    @SuppressLint("MissingPermission") // permission is confirmed before startLocationTracking() runs
    private fun onLocationUpdate(location: Location) {
        lastKnownLocation = location
        map?.locationComponent?.forceLocationUpdate(location)

        if (activeRoute != null) {
            updateNavCamera(location)
            navigationEngine.onLocation(RoutePoint(location.latitude, location.longitude))
        }

        if (!hasCenteredOnFirstFix) {
            hasCenteredOnFirstFix = true
            map?.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition
                        .Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(15.0)
                        .build(),
                ),
                600,
            )
        }
    }

    @SuppressLint("MissingPermission") // activation itself needs no permission; display does, gated by ensureLocationPermissionThenTrack()
    private fun enableLocationComponent(
        map: MapLibreMap,
        style: Style,
    ) {
        val locationComponent = map.locationComponent
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions
                .builder(this, style)
                .useDefaultLocationEngine(false) // we feed it ourselves via LiveLocationTracker
                .build(),
        )
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.NONE // we drive the camera ourselves on first fix
        locationComponent.renderMode = RenderMode.COMPASS
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        locationTracker?.stop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        searchExecutor.shutdownNow()
        voiceNarrator.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
