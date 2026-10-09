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
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.excellentmindblow.navos.location.LiveLocationTracker
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

/**
 * Native Android entry point. MapView lifecycle forwarding below:
 * MapLibre's MapView needs every Activity lifecycle callback forwarded to
 * it manually, unlike a typical Android view.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var searchInput: EditText
    private lateinit var searchResultsList: ListView
    private var map: MapLibreMap? = null
    private var locationTracker: LiveLocationTracker? = null
    private var hasCenteredOnFirstFix = false
    private var lastKnownLocation: Location? = null
    private var routeSource: GeoJsonSource? = null

    /** The destination the user last picked from search results - routing
     * (next port) will read this to calculate a route from the current GPS
     * fix to here. */
    var selectedDestination: GeocodeResult? = null
        private set

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

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { loadedMap ->
            map = loadedMap

            // MapLibre draws a rotation compass top-right by default, right
            // where our search bar sits, so it ends up half-hidden behind
            // it. We don't track map bearing yet (cameraMode is NONE), so
            // the compass has nothing useful to show right now anyway -
            // drop it. (Waze-style rotating-map + its own compass can come
            // back once turn-by-turn drives the camera.)
            loadedMap.uiSettings.isCompassEnabled = false

            loadedMap.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                enableLocationComponent(loadedMap, style)
                setUpRouteLayer(style)
                ensureLocationPermissionThenTrack()
            }
        }

        setUpSearch()
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

    private fun scheduleSearch(query: String) {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }

        if (query.trim().length < 3) {
            currentResults = emptyList()
            searchResultsList.visibility = android.view.View.GONE
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
            searchResultsList.visibility = android.view.View.GONE
            return
        }
        searchResultsList.adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, results.map { it.label })
        searchResultsList.visibility = android.view.View.VISIBLE
    }

    private fun onDestinationSelected(result: GeocodeResult) {
        selectedDestination = result
        searchResultsList.visibility = android.view.View.GONE
        currentResults = emptyList()
        searchInput.setText(result.label)
        searchInput.clearFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

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

    private fun setUpRouteLayer(style: Style) {
        val source = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(source)
        routeSource = source

        val layer =
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1CB0F6")),
                PropertyFactory.lineWidth(5f),
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
            val route =
                try {
                    RoutingService.calculateRoute(originPoint, destinationPoint)
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                if (route == null) {
                    Toast.makeText(this, "Couldn't calculate a route to that destination", Toast.LENGTH_SHORT).show()
                } else {
                    showRoute(route)
                }
            }
        }
    }

    private fun showRoute(route: Route) {
        val points = route.geometry.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.size >= 2) {
            routeSource?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))

            val bounds = LatLngBounds.Builder()
            route.geometry.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
            map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120, 120, 120, 420), 800)
        }

        val km = route.distanceMeters / 1000.0
        val minutes = route.durationSeconds / 60.0
        Toast
            .makeText(
                this,
                String.format(Locale.US, "Route: %.1f km, about %.0f min", km, minutes),
                Toast.LENGTH_LONG,
            ).show()
    }

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
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
