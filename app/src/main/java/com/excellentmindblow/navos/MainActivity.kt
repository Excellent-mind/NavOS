package com.excellentmindblow.navos

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.excellentmindblow.navos.location.DriveFollower
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.location.PuckIcon
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.poi.PoiService
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoadPoiService
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Same free, no-API-key, production-safe tile style used by the earlier
// React Native build. https://openfreemap.org
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// OpenFreeMap's own dark basemap - one of their six default styles
// (Positron/Bright/Liberty/Dark/Fiord/3D), same free no-API-key CDN as the
// light style above, no new provider or dependency. "Fiord" is the one the
// user picked (confirmed against openfreemap.org's own style list).
private const val MAP_STYLE_URL_DARK = "https://tiles.openfreemap.org/styles/fiord"

private const val ROUTE_SOURCE_ID = "navos-route-source"
private const val ROUTE_CASING_LAYER_ID = "navos-route-casing-layer"
private const val ROUTE_LAYER_ID = "navos-route-layer"
private const val ALT_ROUTE_SOURCE_ID = "navos-alt-route-source"
private const val ALT_ROUTE_LAYER_ID = "navos-alt-route-layer"
private const val ROUTE_INDEX_PROPERTY = "routeIndex"

private const val DESTINATION_SOURCE_ID = "navos-destination-source"
private const val DESTINATION_LAYER_ID = "navos-destination-layer"
private const val DESTINATION_ICON_ID = "navos-destination-icon"

private const val INCIDENTS_SOURCE_ID = "navos-incidents-source"
private const val INCIDENTS_LAYER_ID = "navos-incidents-layer"
private const val INCIDENT_ICON_PROPERTY = "icon"
private const val TRAFFIC_ICON_ID = "navos-icon-traffic"
private const val POLICE_ICON_ID = "navos-icon-police"
private const val HAZARD_ICON_ID = "navos-icon-hazard"
private const val CRASH_ICON_ID = "navos-icon-crash"

// Drive camera: 3D tilted view, heading-up, the arrow sitting in the lower
// part of the screen so more road ahead is visible.
private const val NAV_TILT_DEGREES = 55.0
private const val NAV_ZOOM = 18.4 // cruising zoom
private const val NAV_TOP_PADDING_FRACTION = 0.40 // puts the arrow ~70% down the screen
private const val SHOW_3D_BUILDINGS = true

// Turn callouts on the route line (arrow + road name at every corner).
private const val TURN_SOURCE_ID = "navos-turn-source"
private const val TURN_ARROW_LAYER_ID = "navos-turn-arrow-layer"
private const val TURN_LABEL_LAYER_ID = "navos-turn-label-layer"
private const val MAX_TURN_CALLOUTS = 12

// Toll plazas / speed cameras drawn on the route, and how many route
// choices to offer (Waze gives 4-5).
private const val ROUTE_POI_SOURCE_ID = "navos-route-poi-source"
private const val ROUTE_POI_LAYER_ID = "navos-route-poi-layer"
private const val POI_ICON_TOLL = "navos-poi-toll"
private const val POI_ICON_CAMERA = "navos-poi-camera"
private const val MAX_ROUTE_OPTIONS = 5

// Smart zoom: pull in as a turn / U-turn / roundabout gets close, ease back
// out on straights, and open up a little at highway speed.
private const val NAV_ZOOM_SLOW_BONUS = 0.25 // crawling in town
private const val NAV_ZOOM_FAST_PENALTY = 0.7 // 120 km/h and up
private const val NAV_ZOOM_SMOOTHING = 0.3 // share of the gap closed per GPS fix

// Position-button / nav-follow camera tuning. Two different ease
// durations on purpose: a short one for the continuous per-GPS-fix follow
// (so it reads as smooth tracking, not a fresh animation firing every
// second) and a longer one only for the deliberate "snap into place"
// moment - the Position button tap or entering active navigation.
private const val NAV_FOLLOW_EASE_MS = 350
private const val SNAP_MAX_METERS = 35.0 // beyond this the fix isn't "on" the route
private const val SNAP_LOOKAHEAD_SEGMENTS = 150
private const val SNAP_LOOKBACK_SEGMENTS = 5
private const val PUCK_SOURCE_ID = "navos-puck-source"
private const val PUCK_LAYER_ID = "navos-puck-layer"
private const val PUCK_ICON_ID = "navos-puck-icon"
private const val NAV_RECENTER_EASE_MS = 450 // quick, soft glide back to the arrow
private const val IDLE_RECENTER_ZOOM = 16.5

private const val PREFS_NAME = "navos_prefs"
private const val KEY_VOICE_ENABLED = "voice_enabled"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled" // legacy (pre "Follow system")
private const val KEY_MAP_STYLE_MODE = "map_style_mode"
private const val MAP_STYLE_SYSTEM = "system"
private const val MAP_STYLE_LIGHT = "light"
private const val MAP_STYLE_DARK = "dark"

/** Pause after the last keystroke before a live search fires - long
 * enough not to query on every letter, short enough to feel instant. */
private const val LIVE_SEARCH_DEBOUNCE_MS = 120L
private const val KEY_RECENT_PLACES = "recent_places"
private const val MAX_RECENTS_STORED = 10
private const val MAX_RECENTS_SHOWN = 5

// Petrol station / EV charger pins during a drive.
private const val POI_SOURCE_ID = "navos-poi-source"
private const val POI_LAYER_ID = "navos-poi-layer"
private const val POI_ICON_PROPERTY = "poiIcon"
private const val POI_ID_PROPERTY = "poiId"
private const val FUEL_PIN_ICON_ID = "navos-icon-fuel-pin"
private const val EV_PIN_ICON_ID = "navos-icon-ev-pin"
private const val KEY_SHOW_FUEL = "show_fuel_pins"
private const val KEY_SHOW_EV = "show_ev_pins"
private const val FUEL_COLOR = "#E8710A"
private const val EV_COLOR = "#12A150"

/** Look stations up again once the driver has moved this far from the
 * last lookup (keeps the 5 km ring roughly centred on them)... */
private const val POI_REFETCH_DISTANCE_M = 2000f

/** ...but never more often than this - Overpass is a free shared server,
 * and a failed lookup also waits this long before retrying. */
private const val POI_MIN_REFETCH_MS = 60_000L

/** Where the app currently is in the search -> preview -> drive flow. */
private enum class NavMode { IDLE, PREVIEW, ACTIVE }

/** Which state the free-roam home bottom sheet is in. COLLAPSED shows just
 * the search pill + settings icon; SEARCH and SETTINGS both expand the same
 * card in place, with a back arrow to return to COLLAPSED. */
private enum class HomeSheetState { COLLAPSED, SEARCH, SETTINGS }

/**
 * Native Android entry point. MapView lifecycle forwarding below:
 * MapLibre's MapView needs every Activity lifecycle callback forwarded to
 * it manually, unlike a typical Android view.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var topBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchResultsList: ListView
    private lateinit var routePreviewCard: View
    private lateinit var routePreviewSummary: TextView
    private lateinit var routePreviewHint: TextView
    private lateinit var topNavBar: View
    private lateinit var bottomNavBar: View
    private lateinit var turnDistanceText: TextView
    private lateinit var turnStreetText: TextView
    private lateinit var recalcPill: View
    private lateinit var routeViewToggle: View
    private lateinit var toggleMap: TextView
    private lateinit var toggleList: TextView
    private lateinit var routeListOverlay: View
    private lateinit var routeListItems: LinearLayout
    private lateinit var recalcText: TextView
    private var recalcShown = false
    private var recalcPulse: ObjectAnimator? = null
    private lateinit var etaClockText: TextView
    private lateinit var etaDetailText: TextView
    private lateinit var navStopButton: View
    private lateinit var routePreviewEta: TextView
    private lateinit var routePreviewVia: TextView
    private lateinit var routePreviewDestination: TextView
    private var navSheetExpanded = false
    private lateinit var navBarRow: View
    private var destinationName: String? = null
    private lateinit var turnIcon: ImageView
    private lateinit var speedBadge: TextView
    private lateinit var recenterButton: View
    private lateinit var nextTurnsSheet: View
    private lateinit var nextTurnsList: LinearLayout

    /** Step the Next-turns list was last built for, and its first-row
     * distance label (updated every GPS fix without rebuilding the list). */
    private var nextTurnsRenderedIndex = -1
    private var nextTurnsFirstDistance: TextView? = null
    private lateinit var reportButton: ImageButton
    private lateinit var homeButton: View
    private lateinit var homeButtonLabel: TextView
    private lateinit var workButton: View
    private lateinit var workButtonLabel: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var nextTurnStrip: View
    private lateinit var laneContainer: LinearLayout
    private lateinit var permissionDeniedOverlay: View
    private lateinit var gpsDisabledOverlay: View
    private lateinit var nextTurnIcon: ImageView
    private lateinit var searchBackButton: ImageButton

    // ---- Home bottom sheet (replaces the old tab bar / saved panel /
    // settings drawer) ----
    private lateinit var homeSheet: View
    private lateinit var homeSheetCollapsedRow: View
    private lateinit var homeSheetExpandedRow: View
    private lateinit var homeSearchPill: TextView
    private lateinit var homeSettingsButton: ImageButton
    private lateinit var homeSheetBackButton: ImageButton
    private lateinit var homeSearchInput: EditText
    private lateinit var homeSearchInputContainer: View
    private lateinit var homeSearchClearButton: ImageButton
    private lateinit var searchClearButton: ImageButton
    private lateinit var homeSheetExpandedTitle: TextView
    private lateinit var homeSearchBody: View
    private lateinit var homeSettingsBody: View
    private lateinit var homeSearchResultsList: ListView
    private lateinit var homeSheetDragHandle: View
    private var homeSheetState = HomeSheetState.COLLAPSED

    private var dragStartRawY = 0f
    private var dragging = false

    /** True while a drag or the snap animation after it owns the sheet's
     * height - keeps applySheetInsets() from fighting it (e.g. the keyboard
     * closing mid-snap would otherwise re-apply full/wrap height and make
     * the sheet jump). */
    private var settlingDrag = false

    /** The sheet's own height (px) when the current drag began, and the
     * velocity tracker + running snap animation for the drag handle. */
    private var dragStartSheetHeight = 0
    private var dragVelocityTracker: VelocityTracker? = null
    private var sheetSettleAnimator: ValueAnimator? = null

    /** Whichever search box (the idle sheet's or the mid-drive topBar's)
     * most recently opened - showSearchResults()/runSearch() target this
     * one, since the two are never active at the same time. */
    private var activeSearchInput: EditText? = null
    private var activeSearchResultsList: ListView? = null

    /** Pending debounced live search (cancelled on every keystroke), and
     * a counter bumped on every new search/clear - a response is only
     * shown if no newer search started after it, so a slow reply for
     * "lo" can never overwrite the results for "lorong". */
    private var liveSearchRunnable: Runnable? = null
    private var searchSeq = 0

    /** Latest keyboard height / status bar height from window insets -
     * kept so the sheet can be re-laid out when its state changes, not
     * only when the keyboard moves. */
    private var lastImeBottom = 0
    private var statusBarTop = 0
    private var homeSheetBasePaddingBottom = 0

    // ---- Petrol / EV pins (active drive only) ----
    private lateinit var poiToggleColumn: View
    private lateinit var fuelToggleButton: ImageButton
    private lateinit var evToggleButton: ImageButton
    private lateinit var poiCard: View
    private lateinit var poiLogo: View
    private lateinit var poiLogoIcon: ImageView
    private lateinit var poiName: TextView
    private lateinit var poiMeta: TextView
    private lateinit var poiSpec: TextView
    private lateinit var poiChips: ChipGroup
    private var poiSource: GeoJsonSource? = null
    private var nearbyPois: List<Poi> = emptyList()
    private var selectedPoi: Poi? = null
    private var lastPoiFetchLocation: Location? = null
    private var lastPoiFetchAtMs = 0L
    private var poiFetchInFlight = false
    private var showFuelPins = true
    private var showEvPins = true

    /** Set while waiting for the user to pick a destination to save under
     * "home" or "work" - cleared once that pick is saved. */
    private var pendingSavedPlaceLabel: String? = null

    /** True while the search bar was opened from the drive bar's search
     * icon (rather than from idle/free-roam) - lets exitSearchFromNav()
     * know to bring the nav UI back instead of just hiding the search. */
    private var searchOpenedFromNav = false

    private var incidentsSource: GeoJsonSource? = null
    private val incidentFeatures = mutableListOf<Feature>()

    private var map: MapLibreMap? = null
    private var locationTracker: LiveLocationTracker? = null
    private var hasCenteredOnFirstFix = false
    private var lastKnownLocation: Location? = null

    /** True while the free-roam camera should keep re-centering on the
     * driver as they move (Waze-style), turned off the moment they drag
     * the map themselves so we don't fight their pan. */
    private var followingUser = true

    /** Same idea as followingUser, but for the 3D nav camera during
     * NavMode.ACTIVE - a separate flag since the two modes have different
     * cameras (flat pan vs. tilted follow) and shouldn't share one state. */
    private var navFollowing = true
    private var smoothedBearing: Double? = null

    /** Index of the route segment the arrow was last snapped to, so the
     * next fix only searches around it. */
    private var snapSegmentIndex = 0

    /** Drives the arrow AND the camera from one animation (see class doc). */
    private val driveFollower =
        DriveFollower(
            mapProvider = { map },
            followProvider = {
                when (navMode) {
                    NavMode.ACTIVE -> if (navFollowing) DriveFollower.Follow.DRIVE_3D else DriveFollower.Follow.OFF
                    NavMode.IDLE -> if (followingUser) DriveFollower.Follow.CENTER else DriveFollower.Follow.OFF
                    NavMode.PREVIEW -> DriveFollower.Follow.OFF
                }
            },
        ).also { it.driveTilt = NAV_TILT_DEGREES }
    private var smoothedNavZoom: Double? = null
    private var lastNavState: NavigationState? = null

    private var routeSource: GeoJsonSource? = null
    private var altRouteSource: GeoJsonSource? = null
    private var destinationSource: GeoJsonSource? = null

    /** The destination pin's position, kept around purely so it can be
     * redrawn after a map restyle (e.g. toggling dark mode) wipes every
     * custom source/layer - MapLibre's setStyle() fully replaces the style,
     * it doesn't merge into it. */
    private var destinationLatLng: LatLng? = null

    private var navMode = NavMode.IDLE
    private var routeOptions: List<Route> = emptyList()
    private var selectedRouteIndex = 0

    /** Only non-null while NavMode.ACTIVE - gates feeding GPS fixes into
     * the navigation engine. */
    private var activeRoute: Route? = null

    // The actual GPS subscription, NavigationEngine and voice guidance for
    // an active drive all live in NavigationService now, not here - that's
    // what lets a drive keep tracking/announcing turns when the screen
    // locks or the app is backgrounded, which a plain Activity field can't
    // do on its own. MainActivity only binds to mirror state into the UI
    // (camera, turn card, lane strip) while it's actually visible; see
    // startAndBindNavigationService()/navServiceConnection below.
    private var navigationService: NavigationService? = null
    private var navServiceBound = false
    private var pendingStartRoute: Route? = null

    private val navServiceListener =
        object : NavigationService.Listener {
            override fun onNavLocation(location: Location) {
                lastKnownLocation = location
                // Arrow + camera are animated together by driveFollower.
                updateNavCamera(location)
                maybeFetchPois(location)
                val kmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
                speedBadge.text = "$kmh\nkm/h"
            }

            override fun onNavState(state: NavigationState) {
                onNavigationState(state)
            }

            override fun onNavigationEnded() {
                if (navMode == NavMode.ACTIVE) endActiveNavigation()
            }

            override fun onRerouted(route: Route) {
                activeRoute = route
                snapSegmentIndex = 0
                routeOptions = listOf(route)
                selectedRouteIndex = 0
                redrawRouteLines()
                setRecalculating(false)
                activeRoutePois = emptyList()
                renderRoutePois()
                loadActiveRoutePois(route)
                renderedCalloutStep = -1
                renderTurnCallouts(1)
                nextTurnsRenderedIndex = -1
                if (nextTurnsSheet.visibility == View.VISIBLE) refreshNextTurns()
            }
        }

    private val navServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val service = (binder as NavigationService.LocalBinder).getService()
                navigationService = service
                service.setListener(navServiceListener)
                pendingStartRoute?.let { route ->
                    service.startNavigation(route)
                    pendingStartRoute = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                navigationService = null
            }
        }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Place search gets its own thread so a burst of as-you-type lookups
    // never queues up in front of a route calculation (searchExecutor).
    private val placeSearchExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Station lookups can take several seconds - own thread, so they never
    // hold up a place search or a route calculation.
    private val poiExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentResults: List<GeocodeResult> = emptyList()

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startLocationTracking()
            } else {
                permissionDeniedOverlay.visibility = View.VISIBLE
            }
        }

    // Denying this only hides the ongoing-navigation notification (API
    // 33+) - the drive and the foreground service keep running either
    // way, so there's nothing to react to here beyond the system request
    // itself.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before any MapView touches its native state.
        MapLibre.getInstance(applicationContext)

        // The Map style setting (Follow system / Light / Dark) drives the
        // WHOLE app's day/night theme, not just the map tiles. Must run
        // before super.onCreate so the first inflate already uses it.
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyAppNightMode()

        super.onCreate(savedInstanceState)
        uiDarkApplied = isNightConfig(resources.configuration)

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

            // Our own always-visible compass (the built-in MapLibre one hides
            // itself facing north and was getting clipped). Rotates with the
            // map; tap to turn the map back to north.
            loadedMap.uiSettings.isCompassEnabled = false
            val compass = findViewById<CompassView>(R.id.compassButton)
            loadedMap.addOnCameraMoveListener {
                compass.northAngle = (-loadedMap.cameraPosition.bearing).toFloat()
            }
            compass.setOnClickListener {
                if (navMode == NavMode.ACTIVE) {
                    navFollowing = false
                    placeRecenterButton(drivingLayout = true)
                    recenterButton.visibility = View.VISIBLE
                }
                driveFollower.holdCamera(600L)
                loadedMap.easeCamera(CameraUpdateFactory.bearingTo(0.0), 400)
            }

            loadedMap.addOnMapClickListener { latLng -> onMapTapped(loadedMap, latLng) }

            // A manual pan/rotate/tilt means the driver wants to look
            // elsewhere - stop auto-recentering (in whichever mode is
            // actually active) and offer a way back via the Position
            // button, same affordance Waze/Google Maps both show once
            // you've panned away. Only during PREVIEW is a manual gesture
            // left alone entirely - that screen has no camera-follow of
            // its own to pause.
            loadedMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    when (navMode) {
                        NavMode.ACTIVE -> {
                            navFollowing = false
                            placeRecenterButton(drivingLayout = true)
                            recenterButton.visibility = View.VISIBLE
                        }
                        NavMode.IDLE -> {
                            followingUser = false
                            placeRecenterButton(drivingLayout = false)
                            recenterButton.visibility = View.VISIBLE
                        }
                        NavMode.PREVIEW -> {}
                    }
                }
            }

            val initialStyleUrl =
                mapStyleUrl()
            applyMapStyle(loadedMap, initialStyleUrl, isRestyle = false)
        }

        setUpSearch()
        setUpRouteControls()
        setUpSettings()
        setUpSavedPlaces()
        setUpSettingsExtras()
        setUpImeInsetHandling()
    }

    /** Pushes the home sheet up above the on-screen keyboard while it's
     * open. Needed because onCreate() turns off automatic window
     * fit/resize (WindowCompat.setDecorFitsSystemWindows(window, false),
     * above) to get a transparent status bar - that same opt-out also
     * stops the keyboard from pushing bottom-anchored content out of its
     * way on its own, so without this the sheet (and the search box inside
     * it) ends up rendered underneath the keyboard instead of above it. */
    private fun setUpImeInsetHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            lastImeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            applySheetInsets()
            insets
        }
    }

    /** Lays the home sheet out for its current state and the keyboard:
     *  - SEARCH: full height (just below the status bar), like Waze's
     *    search screen, with bottom padding equal to the keyboard height so
     *    the results list ends right above the keyboard and scrolls.
     *  - otherwise: normal wrap-content bottom sheet, lifted above the
     *    keyboard if one is open.
     * Also pads the mid-drive search bar (topBar) the same way. */
    private fun applySheetInsets() {
        val ownedByDrag = dragging || settlingDrag
        val fullHeight = homeSheetState == HomeSheetState.SEARCH && !ownedByDrag
        val params = homeSheet.layoutParams as? ViewGroup.MarginLayoutParams
        // While a drag/snap owns the sheet it sets the height itself, every
        // frame - leave height and margin alone so nothing pops.
        if (params != null && !ownedByDrag) {
            val wantHeight = if (fullHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            val wantTop = if (fullHeight) sheetTopGap() else 0
            if (params.height != wantHeight || params.topMargin != wantTop) {
                params.height = wantHeight
                params.topMargin = wantTop
                homeSheet.layoutParams = params
            }
        }
        val bottomPad = homeSheetBasePaddingBottom + if (fullHeight) lastImeBottom else 0
        homeSheet.setPadding(homeSheet.paddingLeft, homeSheet.paddingTop, homeSheet.paddingRight, bottomPad)
        homeSheet.translationY = if (fullHeight || ownedByDrag) 0f else -lastImeBottom.toFloat()

        topBar.setPadding(topBar.paddingLeft, topBar.paddingTop, topBar.paddingRight, lastImeBottom + dp(12))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Gap between the top of the screen and the sheet when it's full height. */
    private fun sheetTopGap(): Int = (if (statusBarTop > 0) statusBarTop else dp(32)) + dp(8)

    /** The sheet's height when fully open (same pixels MATCH_PARENT + the top
     * margin from applySheetInsets() resolves to, so snapping into the real
     * full-height layout afterwards is seamless). */
    private fun sheetFullHeight(): Int {
        val parentHeight = (homeSheet.parent as View).height
        return (parentHeight - sheetTopGap()).coerceAtLeast(sheetCollapsedHeight() + 1)
    }

    /** The sheet's height when collapsed: padding + drag handle (24dp) + the
     * 52dp search-pill row, with the body hidden. */
    private fun sheetCollapsedHeight(): Int =
        homeSheet.paddingTop + dp(24) + dp(52) + homeSheetBasePaddingBottom

    private fun setUpSettings() {
        findViewById<Switch>(R.id.voiceGuidanceSwitch).apply {
            isChecked = prefs.getBoolean(KEY_VOICE_ENABLED, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(KEY_VOICE_ENABLED, isChecked).apply()
                navigationService?.setVoiceEnabled(isChecked)
            }
        }

        val styleButtonIds =
            mapOf(
                MAP_STYLE_SYSTEM to R.id.mapStyleSystem,
                MAP_STYLE_LIGHT to R.id.mapStyleLight,
                MAP_STYLE_DARK to R.id.mapStyleDark,
            )
        findViewById<RadioButton>(styleButtonIds.getValue(mapStyleMode())).isChecked = true

        findViewById<RadioGroup>(R.id.mapStyleGroup).setOnCheckedChangeListener { _, checkedId ->
            val mode =
                when (checkedId) {
                    R.id.mapStyleDark -> MAP_STYLE_DARK
                    R.id.mapStyleLight -> MAP_STYLE_LIGHT
                    else -> MAP_STYLE_SYSTEM
                }
            if (mode == mapStyleMode()) return@setOnCheckedChangeListener
            prefs.edit().putString(KEY_MAP_STYLE_MODE, mode).apply()
            // Re-theme the whole UI too (see onConfigurationChanged).
            applyAppNightMode()
            val loadedMap = map ?: return@setOnCheckedChangeListener
            // e.g. Follow system -> Light while the phone is already light:
            // same map either way, so don't reload the style for nothing.
            if (wantsDarkMap() == appliedMapDark) return@setOnCheckedChangeListener
            applyMapStyle(loadedMap, mapStyleUrl(), isRestyle = true)
        }
    }

    /** The saved map style choice: Follow system (the default), Light or
     * Dark. Anyone who had picked Dark before "Follow system" existed keeps
     * Dark; everyone else starts on Follow system. */
    private fun mapStyleMode(): String =
        prefs.getString(KEY_MAP_STYLE_MODE, null)
            ?: if (prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)) MAP_STYLE_DARK else MAP_STYLE_SYSTEM

    private fun wantsDarkMap(): Boolean =
        when (mapStyleMode()) {
            MAP_STYLE_DARK -> true
            MAP_STYLE_LIGHT -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    private fun mapStyleUrl(): String = if (wantsDarkMap()) MAP_STYLE_URL_DARK else MAP_STYLE_URL

    /** Whether the UI currently on screen was inflated in dark mode. */
    private var uiDarkApplied = false

    /** A day/night change arrived mid-trip; rebuild once back at idle. */
    private var pendingUiRecreate = false

    private fun isNightConfig(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Follow system -> follow the phone; Light / Dark -> force it. */
    private fun applyAppNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (mapStyleMode()) {
                MAP_STYLE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                MAP_STYLE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

    /** Rebuilds the screen for the new theme - but only when idle, so a
     * route preview or an active drive is never thrown away. Otherwise it
     * waits until the trip ends (see applyPendingUiRecreate). */
    private fun requestUiRecreate() {
        if (navMode == NavMode.IDLE) {
            pendingUiRecreate = false
            recreate()
        } else {
            pendingUiRecreate = true
        }
    }

    private fun applyPendingUiRecreate() {
        if (pendingUiRecreate && navMode == NavMode.IDLE) requestUiRecreate()
    }

    /** Whether the map style currently loaded (or loading) is the dark one. */
    private var appliedMapDark: Boolean? = null

    /** The phone switched between light and dark (manually or on a
     * schedule) - if the map follows the system, swap its style live. The
     * manifest opts MainActivity out of being recreated for uiMode changes
     * so this can't interrupt a drive. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The app's own day/night changed (setting changed, or the phone
        // switched while on Follow system): the sheets, cards and text are
        // inflated once, so rebuild the screen to pick up the new colours.
        if (isNightConfig(newConfig) != uiDarkApplied) requestUiRecreate()
        if (mapStyleMode() != MAP_STYLE_SYSTEM) return
        val loadedMap = map ?: return
        if (wantsDarkMap() == appliedMapDark) return
        applyMapStyle(loadedMap, mapStyleUrl(), isRestyle = true)
    }

    /** Placeholder settings-body rows (help/about) - there's no account/
     * backend system yet, so these are simple, honest actions rather than
     * fake screens. */
    private fun setUpSettingsExtras() {
        findViewById<TextView>(R.id.helpFeedbackRow).setOnClickListener {
            val intent =
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:excellentmindblow@gmail.com")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "NavOS feedback")
                }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.aboutRow).setOnClickListener {
            AlertDialog
                .Builder(this)
                .setTitle("NavOS")
                .setMessage("Version ${BuildConfig.VERSION_NAME}\nPowered by Excellent Mindblow")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun setUpSearch() {
        searchInput = findViewById(R.id.searchInput)
        searchResultsList = findViewById(R.id.searchResultsList)
        homeSearchInput = findViewById(R.id.homeSearchInput)
        homeSearchResultsList = findViewById(R.id.homeSearchResultsList)
        homeSearchInputContainer = findViewById(R.id.homeSearchInputContainer)
        homeRecentsContainer = findViewById(R.id.homeRecentsContainer)
        findViewById<View>(R.id.homeRecentsClear).setOnClickListener {
            prefs.edit().remove(KEY_RECENT_PLACES).apply()
            renderRecents()
        }
        homeSearchClearButton = findViewById(R.id.homeSearchClearButton)
        searchClearButton = findViewById(R.id.searchClearButton)

        // Two search boxes share this wiring: the mid-drive one (topBar)
        // and the idle home sheet's own one. They're never open at the
        // same time, so both just feed the same runSearch()/currentResults
        // - showSearchResults() writes into whichever one's
        // activeSearchResultsList was set when it was opened.
        wireSearchBox(searchInput, searchResultsList, searchClearButton)
        wireSearchBox(homeSearchInput, homeSearchResultsList, homeSearchClearButton)
    }

    private fun wireSearchBox(
        input: EditText,
        resultsList: ListView,
        clearButton: ImageButton,
    ) {
        // Search-as-you-type: each keystroke (re)starts a short timer, and
        // the live search (Photon - see GeocodingService.searchLive) fires
        // once typing pauses. Clearing the box, or getting under 2
        // letters, cancels anything pending and drops stale results.
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString().orEmpty()
                    clearButton.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE

                    liveSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                    liveSearchRunnable = null

                    if (text.trim().length < GeocodingService.MIN_LIVE_QUERY_LENGTH) {
                        searchSeq++ // any reply still in flight is now stale
                        currentResults = emptyList()
                        resultsList.visibility = View.GONE
                        if (resultsList === homeSearchResultsList) setHomeQuickPicksVisible(true)
                        return
                    }
                    // Only the driver typing triggers a search - not a
                    // programmatic setText() on a box that isn't in use.
                    if (!input.hasFocus()) return

                    activeSearchInput = input
                    activeSearchResultsList = resultsList

                    // Instant: a repeat query comes straight from the cache,
                    // otherwise show matches from recents / the previous
                    // results right now while the network request runs.
                    val cached = searchCache[text.trim().lowercase()]
                    if (cached != null) {
                        searchSeq++
                        showSearchResults(cached)
                        return
                    }
                    val instant = instantMatches(text)
                    if (instant.isNotEmpty()) showSearchResults(instant)

                    val pending = Runnable { runSearch(text, live = true) }
                    liveSearchRunnable = pending
                    mainHandler.postDelayed(pending, LIVE_SEARCH_DEBOUNCE_MS)
                }

                override fun afterTextChanged(s: Editable?) {}
            },
        )

        clearButton.setOnClickListener {
            input.setText("")
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO
            ) {
                liveSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                liveSearchRunnable = null
                activeSearchInput = input
                activeSearchResultsList = resultsList
                runSearch(input.text?.toString().orEmpty(), live = false)
                true
            } else {
                false
            }
        }

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val result = currentResults.getOrNull(position) ?: return@setOnItemClickListener
            onDestinationSelected(result)
        }
    }

    private fun setUpRouteControls() {
        topBar = findViewById(R.id.topBar)
        routePreviewCard = findViewById(R.id.routePreviewCard)
        routePreviewSummary = findViewById(R.id.routePreviewSummary)
        routePreviewHint = findViewById(R.id.routePreviewHint)
        topNavBar = findViewById(R.id.topNavBar)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        navBarRow = findViewById(R.id.navBarRow)
        // Keep the speed badge / report / POI / recenter buttons riding on
        // the drive bar's top edge as it expands or collapses (runs every
        // frame, so it also follows the 220 ms expand animation).
        bottomNavBar.viewTreeObserver.addOnPreDrawListener {
            updateNavFloatingOffset()
            true
        }
        turnDistanceText = findViewById(R.id.turnDistanceText)
        turnStreetText = findViewById(R.id.turnStreetText)
        recalcPill = findViewById(R.id.recalcPill)
        routeViewToggle = findViewById(R.id.routeViewToggle)
        toggleMap = findViewById(R.id.toggleMap)
        toggleList = findViewById(R.id.toggleList)
        routeListOverlay = findViewById(R.id.routeListOverlay)
        routeListItems = findViewById(R.id.routeListItems)
        toggleMap.setOnClickListener { setRouteListVisible(false) }
        toggleList.setOnClickListener { setRouteListVisible(true) }
        recalcText = findViewById(R.id.recalcText)
        etaClockText = findViewById(R.id.etaClockText)
        etaDetailText = findViewById(R.id.etaDetailText)
        navStopButton = findViewById(R.id.stopNavigationButton)
        routePreviewEta = findViewById(R.id.routePreviewEta)
        routePreviewVia = findViewById(R.id.routePreviewVia)
        routePreviewDestination = findViewById(R.id.routePreviewDestination)
        turnIcon = findViewById(R.id.turnIcon)
        speedBadge = findViewById(R.id.speedBadge)
        recenterButton = findViewById(R.id.recenterButton)
        nextTurnsSheet = findViewById(R.id.nextTurnsSheet)
        nextTurnsList = findViewById(R.id.nextTurnsList)
        reportButton = findViewById(R.id.reportButton)
        nextTurnStrip = findViewById(R.id.nextTurnStrip)
        laneContainer = findViewById(R.id.laneContainer)
        nextTurnIcon = findViewById(R.id.nextTurnIcon)
        searchBackButton = findViewById(R.id.searchBackButton)
        setUpPoiControls()

        homeSheet = findViewById(R.id.homeSheet)
        homeSheetBasePaddingBottom = homeSheet.paddingBottom
        homeSheetCollapsedRow = findViewById(R.id.homeSheetCollapsedRow)
        homeSheetExpandedRow = findViewById(R.id.homeSheetExpandedRow)
        homeSearchPill = findViewById(R.id.homeSearchPill)
        homeSettingsButton = findViewById(R.id.homeSettingsButton)
        homeSheetBackButton = findViewById(R.id.homeSheetBackButton)
        homeSheetExpandedTitle = findViewById(R.id.homeSheetExpandedTitle)
        homeSearchBody = findViewById(R.id.homeSearchBody)
        homeSettingsBody = findViewById(R.id.homeSettingsBody)
        homeSheetDragHandle = findViewById(R.id.homeSheetDragHandle)

        homeSearchPill.setOnClickListener { openHomeSheetAnimated() }
        homeSettingsButton.setOnClickListener { setHomeSheetState(HomeSheetState.SETTINGS) }
        homeSheetBackButton.setOnClickListener {
            if (searchOpenedFromNav) exitSearchFromNav() else closeHomeSheetAnimated()
        }
        homeSheetDragHandle.setOnTouchListener { _, event -> onHomeSheetDragTouch(event) }

        permissionDeniedOverlay = findViewById(R.id.permissionDeniedOverlay)
        gpsDisabledOverlay = findViewById(R.id.gpsDisabledOverlay)
        findViewById<View>(R.id.permissionRetryButton).setOnClickListener {
            ensureLocationPermissionThenTrack()
        }
        findViewById<View>(R.id.permissionSettingsButton).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
        findViewById<View>(R.id.gpsSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        findViewById<View>(R.id.startNavigationButton).setOnClickListener {
            beginActiveNavigation()
        }
        findViewById<View>(R.id.cancelRouteButton).setOnClickListener {
            cancelRoutePreview()
        }
        navStopButton.setOnClickListener {
            endActiveNavigation()
        }
        setUpNavSheetToggle()
        recenterButton.setOnClickListener { recenterCamera() }
        reportButton.setOnClickListener {
            showReportDialog()
        }
        findViewById<ImageButton>(R.id.bottomSearchButton).setOnClickListener {
            enterSearchFromNav()
        }
        findViewById<View>(R.id.bottomAltRouteButton).setOnClickListener {
            openNextTurns()
        }
        findViewById<View>(R.id.nextTurnsClose).setOnClickListener { closeNextTurns() }
        findViewById<View>(R.id.nextTurnsSwitchRoute).setOnClickListener {
            closeNextTurns()
            showRoutePreview()
        }
        // Back closes the Next-turns sheet first; otherwise behaves as before.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (routeListShowing) {
                        setRouteListVisible(false)
                    } else if (nextTurnsSheet.visibility == View.VISIBLE) {
                        closeNextTurns()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
        searchBackButton.setOnClickListener {
            exitSearchFromNav()
        }
    }

    /** Switches the free-roam home bottom sheet between collapsed, search
     * and settings. Only meaningful while idle - PREVIEW/ACTIVE hide the
     * whole sheet and drive their own bottom sheets instead. */
    private fun setHomeSheetState(state: HomeSheetState) {
        homeSheetState = state
        homeSheetCollapsedRow.visibility = if (state == HomeSheetState.COLLAPSED) View.VISIBLE else View.GONE
        homeSheetExpandedRow.visibility = if (state == HomeSheetState.COLLAPSED) View.GONE else View.VISIBLE
        homeSearchInputContainer.visibility = if (state == HomeSheetState.SEARCH) View.VISIBLE else View.GONE
        homeSheetExpandedTitle.visibility = if (state == HomeSheetState.SETTINGS) View.VISIBLE else View.GONE
        homeSearchBody.visibility = if (state == HomeSheetState.SEARCH) View.VISIBLE else View.GONE
        homeSettingsBody.visibility = if (state == HomeSheetState.SETTINGS) View.VISIBLE else View.GONE

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (state == HomeSheetState.SEARCH) {
            activeSearchInput = homeSearchInput
            activeSearchResultsList = homeSearchResultsList
            if (homeSearchInput.text.isNullOrEmpty()) renderRecents()
            homeSearchInput.requestFocus()
            imm.showSoftInput(homeSearchInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            // Leaving search (back to COLLAPSED, or into SETTINGS) clears
            // whatever was typed - otherwise the next time the sheet opens
            // in search mode it still shows the last destination typed,
            // instead of the empty "Search destination in Malaysia" hint.
            homeSearchInput.setText("")
            homeSearchInput.clearFocus()
            homeSearchResultsList.visibility = View.GONE
            currentResults = emptyList()
            imm.hideSoftInputFromWindow(homeSearchInput.windowToken, 0)
        }
        applySheetInsets()
    }

    /** Handles the home sheet's drag handle - the sheet's top edge follows
     * the finger 1:1 between collapsed and full height (the same two
     * states as COLLAPSED and SEARCH), and on release snaps to whichever
     * end the fling/position points at with a decelerating animation.
     * SETTINGS has no drag - it's a short fixed list, not something to
     * "pull up more of", so it stays tap-only (gear icon in, back arrow
     * out). */
    private fun onHomeSheetDragTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (homeSheetState == HomeSheetState.SETTINGS) return false
                dragStartRawY = event.rawY
                dragging = false
                dragVelocityTracker?.recycle()
                dragVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragVelocityTracker?.addMovement(event)
                val deltaY = dragStartRawY - event.rawY // positive = finger moved up
                if (!dragging) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(deltaY) <= slop) return true
                    if (homeSheetState == HomeSheetState.SETTINGS) return false
                    startSheetDrag()
                }
                val collapsed = sheetCollapsedHeight()
                val full = sheetFullHeight()
                val target = (dragStartSheetHeight + deltaY.toInt()).coerceIn(collapsed, full)
                applySheetDragHeight(target, collapsed, full)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = dragVelocityTracker
                dragVelocityTracker = null
                if (dragging) {
                    dragging = false
                    tracker?.computeCurrentVelocity(1000)
                    val velocityY = tracker?.yVelocity ?: 0f // px/s, negative = flinging up
                    tracker?.recycle()
                    val collapsed = sheetCollapsedHeight()
                    val full = sheetFullHeight()
                    val progress = (homeSheet.height - collapsed).toFloat() / (full - collapsed).coerceAtLeast(1)
                    val flingThreshold = 900f
                    val open =
                        when {
                            velocityY < -flingThreshold -> true
                            velocityY > flingThreshold -> false
                            else -> progress > 0.5f
                        }
                    animateHomeSheetTo(open, velocityY)
                    return true
                }
                tracker?.recycle()
                return false
            }
            else -> return false
        }
    }

    /** Takes over the sheet's height for a drag: pins it to its current
     * pixel height (so nothing jumps), shows the expanded header + body
     * (clipped by the sheet's own bounds as it grows) and hides the keyboard. */
    private fun startSheetDrag() {
        // Grabbing the sheet mid-snap hands control straight back to the
        // finger instead of waiting for the animation to finish.
        sheetSettleAnimator?.cancel()
        sheetSettleAnimator = null
        dragging = true
        settlingDrag = true
        dragStartSheetHeight = homeSheet.height
        if (homeSheetState == HomeSheetState.COLLAPSED) {
            beginDragOpenFromCollapsed()
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(homeSearchInput.windowToken, 0)
        }
        val params = homeSheet.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = 0
        params.height = dragStartSheetHeight
        homeSheet.layoutParams = params
        applySheetInsets()
    }

    /** Sets the sheet to [height] px and fades the body in with it. */
    private fun applySheetDragHeight(
        height: Int,
        collapsed: Int = sheetCollapsedHeight(),
        full: Int = sheetFullHeight(),
    ) {
        val params = homeSheet.layoutParams
        if (params.height != height) {
            params.height = height
            homeSheet.layoutParams = params
        }
        homeSearchBody.alpha = ((height - collapsed).toFloat() / (full - collapsed).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /** Swaps the sheet's top row to the search/back-arrow layout right as a
     * drag-open begins (instant, not animated - only the sheet's height
     * follows the finger), without yet touching focus/keyboard - that
     * happens once the sheet actually settles open, in setHomeSheetState(). */
    private fun beginDragOpenFromCollapsed() {
        homeSheetCollapsedRow.visibility = View.GONE
        homeSheetExpandedRow.visibility = View.VISIBLE
        homeSearchInputContainer.visibility = View.VISIBLE
        homeSheetExpandedTitle.visibility = View.GONE
        homeSearchBody.visibility = View.VISIBLE
        homeSearchBody.alpha = 0f
    }

    /** Animates the sheet from wherever it is to fully open or fully
     * collapsed, then hands off to setHomeSheetState() for the final,
     * authoritative visibility/focus/keyboard/full-height handling - same
     * path the other callers use, so a half-finished drag can never leave
     * the sheet in a state tapping wouldn't also reach. Used for both the
     * release of a drag and (with zero velocity) the tap open/close. */
    private fun animateHomeSheetTo(
        open: Boolean,
        velocityY: Float = 0f,
    ) {
        val collapsed = sheetCollapsedHeight()
        val full = sheetFullHeight()
        val from = homeSheet.height.coerceIn(collapsed, full)
        val to = if (open) full else collapsed
        settlingDrag = true
        sheetSettleAnimator?.cancel()

        // Longer travel takes a little longer, and a hard fling in the
        // same direction as the snap shortens it - so it keeps the finger's
        // momentum instead of stopping and restarting.
        val distanceFraction = kotlin.math.abs(to - from).toFloat() / (full - collapsed).coerceAtLeast(1)
        val flingBoost = (kotlin.math.abs(velocityY) / 6000f).coerceAtMost(0.4f)
        val durationMs = (240 + 160 * distanceFraction) * (1f - flingBoost)

        var cancelled = false
        val animator =
            ValueAnimator.ofInt(from, to).apply {
                duration = durationMs.toLong().coerceAtLeast(160L)
                interpolator = DecelerateInterpolator(1.8f)
                addUpdateListener { anim ->
                    applySheetDragHeight(anim.animatedValue as Int, collapsed, full)
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (cancelled) return
                            sheetSettleAnimator = null
                            settlingDrag = false
                            homeSearchBody.alpha = 1f
                            setHomeSheetState(if (open) HomeSheetState.SEARCH else HomeSheetState.COLLAPSED)
                            // Dragged down mid-drive: treat as "back to navigation".
                            if (!open && searchOpenedFromNav) exitSearchFromNav()
                        }
                    },
                )
            }
        sheetSettleAnimator = animator
        animator.start()
    }

    /** Tap on the search pill: same slide-up as a drag. */
    private fun openHomeSheetAnimated() {
        if (homeSheetState != HomeSheetState.COLLAPSED || settlingDrag) {
            setHomeSheetState(HomeSheetState.SEARCH)
            return
        }
        startSheetDrag()
        dragging = false // no finger involved - startSheetDrag() only needed for the setup
        animateHomeSheetTo(open = true)
    }

    /** Tap on the back arrow: slides down from search; settings (and any
     * other state) collapses instantly as before. */
    private fun closeHomeSheetAnimated() {
        if (homeSheetState != HomeSheetState.SEARCH || settlingDrag) {
            setHomeSheetState(HomeSheetState.COLLAPSED)
            return
        }
        startSheetDrag()
        dragging = false
        animateHomeSheetTo(open = false)
    }

    /** Opens the search bar over the drive view without stopping the
     * current turn-by-turn session, same as tapping the magnifier icon in
     * Waze's drive bar - picking a destination still ends this drive via
     * onDestinationSelected()'s existing ACTIVE -> endActiveNavigation()
     * check, same as it already does when idle. */
    private fun enterSearchFromNav() {
        if (navMode != NavMode.ACTIVE) return
        searchOpenedFromNav = true
        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        speedBadge.visibility = View.GONE
        reportButton.visibility = View.GONE
        poiToggleColumn.visibility = View.GONE
        hidePoiCard()
        nextTurnStrip.visibility = View.GONE
        laneContainer.visibility = View.GONE
        // Reuse the home sheet's full-height search (Home/Work shortcuts +
        // results above the keyboard) instead of the old bare top bar, so
        // search looks the same mid-drive as it does when idle.
        topBar.visibility = View.GONE
        homeSheet.visibility = View.VISIBLE
        setHomeSheetState(HomeSheetState.SEARCH)
    }

    /** Backs out of a search opened mid-drive without picking anything,
     * restoring the drive UI exactly as it was. */
    private fun exitSearchFromNav() {
        searchOpenedFromNav = false
        setHomeSheetState(HomeSheetState.COLLAPSED) // also clears text + hides keyboard
        homeSheet.visibility = View.GONE

        if (navMode == NavMode.ACTIVE) {
            bottomNavBar.visibility = View.VISIBLE
            speedBadge.visibility = View.VISIBLE
            reportButton.visibility = View.VISIBLE
            poiToggleColumn.visibility = View.VISIBLE
        }
    }

    // ---- Incident reporting ----------------------------------------------

    /** Shows the "What do you see?" grid; each tap reports that type at
     * the current location and dismisses the dialog. */
    private fun showReportDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_report, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        val typeToViewId =
            mapOf(
                "Traffic" to R.id.reportTraffic,
                "Police" to R.id.reportPolice,
                "Hazard" to R.id.reportHazard,
                "Crash" to R.id.reportCrash,
            )
        typeToViewId.forEach { (type, viewId) ->
            view.findViewById<View>(viewId).setOnClickListener {
                reportIncident(type)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun reportIncident(type: String) {
        val location = lastKnownLocation
        if (location == null) {
            Toast.makeText(this, "Still waiting for a GPS fix", Toast.LENGTH_SHORT).show()
            return
        }
        val iconId =
            when (type) {
                "Traffic" -> TRAFFIC_ICON_ID
                "Police" -> POLICE_ICON_ID
                "Hazard" -> HAZARD_ICON_ID
                else -> CRASH_ICON_ID
            }
        val feature = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))
        feature.addStringProperty(INCIDENT_ICON_PROPERTY, iconId)
        incidentFeatures.add(feature)
        incidentsSource?.setGeoJson(FeatureCollection.fromFeatures(incidentFeatures))
        Toast.makeText(this, "$type reported - thanks!", Toast.LENGTH_SHORT).show()
    }

    // ---- Petrol / EV charging pins (active drive) ---------------------------

    private fun setUpPoiControls() {
        poiToggleColumn = findViewById(R.id.poiToggleColumn)
        fuelToggleButton = findViewById(R.id.fuelToggleButton)
        evToggleButton = findViewById(R.id.evToggleButton)
        poiCard = findViewById(R.id.poiCard)
        poiLogo = findViewById(R.id.poiLogo)
        poiLogoIcon = findViewById(R.id.poiLogoIcon)
        poiName = findViewById(R.id.poiName)
        poiMeta = findViewById(R.id.poiMeta)
        poiSpec = findViewById(R.id.poiSpec)
        poiChips = findViewById(R.id.poiChips)

        showFuelPins = prefs.getBoolean(KEY_SHOW_FUEL, true)
        showEvPins = prefs.getBoolean(KEY_SHOW_EV, true)
        stylePoiToggles()

        fuelToggleButton.setOnClickListener {
            showFuelPins = !showFuelPins
            prefs.edit().putBoolean(KEY_SHOW_FUEL, showFuelPins).apply()
            onPoiTogglesChanged(if (showFuelPins) "Petrol stations shown" else "Petrol stations hidden")
        }
        evToggleButton.setOnClickListener {
            showEvPins = !showEvPins
            prefs.edit().putBoolean(KEY_SHOW_EV, showEvPins).apply()
            onPoiTogglesChanged(if (showEvPins) "EV charging shown" else "EV charging hidden")
        }

        findViewById<View>(R.id.poiCloseButton).setOnClickListener { hidePoiCard() }
        findViewById<View>(R.id.poiGoButton).setOnClickListener {
            val poi = selectedPoi ?: return@setOnClickListener
            hidePoiCard()
            // Same path as picking a search result: ends this drive and
            // previews a route to the station.
            onDestinationSelected(GeocodeResult(poi.name, poi.latitude, poi.longitude))
        }
    }

    private fun onPoiTogglesChanged(message: String) {
        stylePoiToggles()
        val selected = selectedPoi
        if (selected != null && !isPoiKindShown(selected.kind)) hidePoiCard()
        renderPoiPins()
        lastKnownLocation?.let { maybeFetchPois(it) }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isPoiKindShown(kind: PoiKind): Boolean = if (kind == PoiKind.FUEL) showFuelPins else showEvPins

    /** On = filled orange/green with a white icon; off = plain white with
     * a navy icon. */
    private fun stylePoiToggles() {
        val offIcon = ContextCompat.getColor(this, R.color.navos_text)
        val offFill = ContextCompat.getColor(this, R.color.navos_surface)
        fun style(
            button: ImageButton,
            on: Boolean,
            color: String,
        ) {
            button.backgroundTintList = ColorStateList.valueOf(if (on) Color.parseColor(color) else offFill)
            button.imageTintList = ColorStateList.valueOf(if (on) Color.WHITE else offIcon)
        }
        style(fuelToggleButton, showFuelPins, FUEL_COLOR)
        style(evToggleButton, showEvPins, EV_COLOR)
    }

    /** Draws the current station list - only during an active drive, and
     * only the kinds the driver hasn't toggled off. */
    private fun renderPoiPins() {
        val src = poiSource ?: return
        val features =
            if (navMode != NavMode.ACTIVE) {
                emptyList()
            } else {
                nearbyPois.filter { isPoiKindShown(it.kind) }.map { poi ->
                    Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).also {
                        it.addStringProperty(POI_ICON_PROPERTY, if (poi.kind == PoiKind.FUEL) FUEL_PIN_ICON_ID else EV_PIN_ICON_ID)
                        it.addStringProperty(POI_ID_PROPERTY, poi.id)
                    }
                }
            }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Looks up stations within 5 km on the first fix of a drive, then
     * again each time the driver has moved 2 km - at most once a minute,
     * and a failed lookup quietly retries a minute later. */
    private fun maybeFetchPois(location: Location) {
        if (navMode != NavMode.ACTIVE || poiFetchInFlight) return
        if (!showFuelPins && !showEvPins) return
        val now = SystemClock.elapsedRealtime()
        if (lastPoiFetchAtMs != 0L && now - lastPoiFetchAtMs < POI_MIN_REFETCH_MS) return
        val last = lastPoiFetchLocation
        if (last != null && last.distanceTo(location) < POI_REFETCH_DISTANCE_M) return

        poiFetchInFlight = true
        lastPoiFetchAtMs = now
        val fetchAt = Location(location)
        poiExecutor.execute {
            val pois =
                try {
                    PoiService.fetchNearby(fetchAt.latitude, fetchAt.longitude)
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                poiFetchInFlight = false
                if (isDestroyed || navMode != NavMode.ACTIVE || pois == null) return@post
                lastPoiFetchLocation = fetchAt
                nearbyPois = pois
                val selected = selectedPoi
                if (selected != null && pois.none { it.id == selected.id }) hidePoiCard()
                renderPoiPins()
            }
        }
    }

    /** During a drive, a tap on a pin opens its card; a tap anywhere else
     * on the map closes an open card. */
    private fun onDriveMapTapped(
        map: MapLibreMap,
        latLng: LatLng,
    ): Boolean {
        val point = map.projection.toScreenLocation(latLng)
        val slop = dp(14).toFloat()
        val hits =
            try {
                map.queryRenderedFeatures(RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop), POI_LAYER_ID)
            } catch (e: Exception) {
                emptyList()
            }
        val id = hits.firstNotNullOfOrNull { feature -> feature.getStringProperty(POI_ID_PROPERTY) }
        val poi = id?.let { poiId -> nearbyPois.firstOrNull { it.id == poiId } }
        if (poi == null) {
            if (poiCard.visibility == View.VISIBLE) {
                hidePoiCard()
                return true
            }
            return false
        }
        showPoiCard(poi)
        return true
    }

    private fun showPoiCard(poi: Poi) {
        selectedPoi = poi
        val isFuel = poi.kind == PoiKind.FUEL
        poiLogo.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isFuel) FUEL_COLOR else EV_COLOR))
        poiLogoIcon.setImageResource(if (isFuel) R.drawable.ic_fuel else R.drawable.ic_ev)
        poiName.text = poi.name

        val kindLabel = if (isFuel) "Petrol station" else "EV charging"
        val here = lastKnownLocation
        poiMeta.text =
            if (here != null) {
                val meters = GeocodingService.distanceMeters(here.latitude, here.longitude, poi.latitude, poi.longitude)
                "$kindLabel · ${formatDistance(meters)} away"
            } else {
                kindLabel
            }

        val spec = poi.spec
        poiSpec.text = spec ?: ""
        poiSpec.visibility = if (spec.isNullOrBlank()) View.GONE else View.VISIBLE

        poiChips.removeAllViews()
        poi.facilities.forEach { facility ->
            val chip =
                Chip(this).apply {
                    text = if (facility.listed) "\u2713 ${facility.label}" else "${facility.label}: not listed"
                    isClickable = false
                    isCheckable = false
                    isFocusable = false
                    setEnsureMinTouchTargetSize(false)
                    chipStrokeWidth = 0f
                    chipBackgroundColor =
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                context,
                                if (facility.listed) R.color.navos_chip_ok_bg else R.color.navos_chip_off_bg,
                            ),
                        )
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (facility.listed) R.color.navos_chip_ok_text else R.color.navos_chip_off_text,
                        ),
                    )
                    textSize = 12.5f
                }
            poiChips.addView(chip)
        }

        poiCard.visibility = View.VISIBLE
    }

    private fun hidePoiCard() {
        selectedPoi = null
        poiCard.visibility = View.GONE
    }

    // ---- Saved places (Home/Work) -----------------------------------------

    private fun setUpSavedPlaces() {
        homeButton = findViewById(R.id.homeButton)
        homeButtonLabel = findViewById(R.id.homeButtonLabel)
        workButton = findViewById(R.id.workButton)
        workButtonLabel = findViewById(R.id.workButtonLabel)

        homeButton.setOnClickListener { onSavedPlaceTapped("home") }
        workButton.setOnClickListener { onSavedPlaceTapped("work") }

        refreshSavedPlaceLabels()
    }

    private fun refreshSavedPlaceLabels() {
        homeButtonLabel.text = savedPlaceLabel("home") ?: "Home"
        workButtonLabel.text = savedPlaceLabel("work") ?: "Work"
    }

    private fun savedPlaceLabel(key: String): String? {
        if (!prefs.contains("${key}_lat")) return null
        return prefs.getString("${key}_label", null)
    }

    private fun onSavedPlaceTapped(key: String) {
        val label = savedPlaceLabel(key)
        if (label != null) {
            val lat = prefs.getFloat("${key}_lat", 0f).toDouble()
            val lon = prefs.getFloat("${key}_lon", 0f).toDouble()
            onDestinationSelected(GeocodeResult(label, lat, lon))
            return
        }

        // Not set yet - same as Waze's "Set once and go": start a search,
        // and save whatever destination gets picked next under this label.
        pendingSavedPlaceLabel = key
        val name = if (key == "home") "home" else "work"
        Toast.makeText(this, "Search your $name address - we'll save it", Toast.LENGTH_LONG).show()
        setHomeSheetState(HomeSheetState.SEARCH)
    }

    private fun maybeSaveAsPendingPlace(result: GeocodeResult) {
        val key = pendingSavedPlaceLabel ?: return
        pendingSavedPlaceLabel = null
        prefs
            .edit()
            .putFloat("${key}_lat", result.latitude.toFloat())
            .putFloat("${key}_lon", result.longitude.toFloat())
            .putString("${key}_label", result.label)
            .apply()
        refreshSavedPlaceLabels()
    }

    /** live = as-you-type (Photon only, failures stay silent and keep the
     * previous results); otherwise it's the keyboard's Search key (Photon,
     * falling back to Nominatim, with a message if nothing is found). */
    private fun runSearch(
        query: String,
        live: Boolean,
    ) {
        val trimmed = query.trim()
        if (trimmed.length < GeocodingService.MIN_LIVE_QUERY_LENGTH) {
            searchSeq++
            currentResults = emptyList()
            activeSearchResultsList?.visibility = View.GONE
            return
        }
        val bias = lastKnownLocation
        val seq = ++searchSeq
        placeSearchExecutor.execute {
            val results: List<GeocodeResult>? =
                try {
                    if (live) {
                        GeocodingService.searchLive(trimmed, bias?.latitude, bias?.longitude)
                    } else {
                        GeocodingService.search(trimmed, bias?.latitude, bias?.longitude)
                    }
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                if (seq != searchSeq) return@post // a newer search/clear replaced this one
                if (results == null) {
                    if (!live) Toast.makeText(this, "Search failed - check your connection", Toast.LENGTH_SHORT).show()
                    return@post
                }
                if (!live && results.isEmpty()) {
                    Toast.makeText(this, "No places found", Toast.LENGTH_SHORT).show()
                }
                if (live && results.isNotEmpty()) {
                    lastLiveResults = results
                    searchCache[trimmed.lowercase()] = results
                    if (searchCache.size > 40) searchCache.remove(searchCache.keys.first())
                }
                showSearchResults(results)
            }
        }
    }

    private fun showSearchResults(results: List<GeocodeResult>) {
        currentResults = results
        val list = activeSearchResultsList ?: return
        // Home/Work make way for real results, and come back once the box
        // is cleared - same as Waze showing Home/Work only on an empty box.
        if (list === homeSearchResultsList) setHomeQuickPicksVisible(results.isEmpty())
        if (results.isEmpty()) {
            list.visibility = View.GONE
            return
        }
        list.adapter = SearchResultAdapter(results, lastKnownLocation)
        list.visibility = View.VISIBLE
        list.setSelection(0)
    }

    private fun setHomeQuickPicksVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        homeButton.visibility = v
        workButton.visibility = v
        if (visible) renderRecents() else homeRecentsContainer.visibility = View.GONE
    }

    // ---- Search helpers: instant matches + recent searches --------------

    private val searchCache = LinkedHashMap<String, List<GeocodeResult>>()
    private var lastLiveResults: List<GeocodeResult> = emptyList()
    private lateinit var homeRecentsContainer: LinearLayout

    /** Matches shown the moment a key is pressed, before the network
     * answers: recent searches first, then the last live results. */
    private fun instantMatches(query: String): List<GeocodeResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return (loadRecents() + lastLiveResults)
            .distinctBy { it.label }
            .filter { it.label.lowercase().contains(needle) }
            .take(8)
    }

    private fun loadRecents(): List<GeocodeResult> {
        val raw = prefs.getString(KEY_RECENT_PLACES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val label = o.getString("label")
                GeocodeResult(
                    label = label,
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    name = o.optString("name").ifBlank { label.substringBefore(",").trim() },
                    address = o.optString("address"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecent(result: GeocodeResult) {
        val updated =
            (listOf(result) + loadRecents().filter { it.label != result.label })
                .take(MAX_RECENTS_STORED)
        val array = JSONArray()
        for (r in updated) {
            array.put(
                JSONObject()
                    .put("label", r.label)
                    .put("lat", r.latitude)
                    .put("lon", r.longitude)
                    .put("name", r.name)
                    .put("address", r.address),
            )
        }
        prefs.edit().putString(KEY_RECENT_PLACES, array.toString()).apply()
    }

    /** Fills the "Recent" block under Home/Work (hidden when empty). */
    private fun renderRecents() {
        val container = homeRecentsContainer
        // Child 0 is the "Recent / Clear" header - everything after it is a row.
        while (container.childCount > 1) container.removeViewAt(1)
        val recents = loadRecents().take(MAX_RECENTS_SHOWN)
        if (recents.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        val origin = lastKnownLocation
        for (r in recents) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_search_result, container, false)
            row.findViewById<ImageView>(R.id.resultIcon).setImageResource(R.drawable.ic_history)
            row.findViewById<TextView>(R.id.resultName).text = r.name.ifBlank { r.label }
            val address = row.findViewById<TextView>(R.id.resultAddress)
            address.text = r.address
            address.visibility = if (r.address.isBlank()) View.GONE else View.VISIBLE
            val distance = row.findViewById<TextView>(R.id.resultDistance)
            if (origin != null) {
                distance.text =
                    formatDistance(GeocodingService.distanceMeters(origin.latitude, origin.longitude, r.latitude, r.longitude))
                distance.visibility = View.VISIBLE
            } else {
                distance.visibility = View.GONE
            }
            row.isClickable = true
            row.setOnClickListener { onDestinationSelected(r) }
            container.addView(row)
        }
        container.visibility = View.VISIBLE
    }

    /** Two-line results row (bold name, grey area) with the distance from
     * the driver's last GPS fix on the right, like Waze. */
    private inner class SearchResultAdapter(
        private val results: List<GeocodeResult>,
        private val origin: Location?,
    ) : BaseAdapter() {
        override fun getCount(): Int = results.size

        override fun getItem(position: Int): Any = results[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val row = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
            val result = results[position]
            row.findViewById<TextView>(R.id.resultName).text = result.name.ifBlank { result.label }
            val address = row.findViewById<TextView>(R.id.resultAddress)
            address.text = result.address
            address.visibility = if (result.address.isBlank()) View.GONE else View.VISIBLE
            val distance = row.findViewById<TextView>(R.id.resultDistance)
            if (origin != null) {
                distance.text =
                    formatDistance(
                        GeocodingService.distanceMeters(origin.latitude, origin.longitude, result.latitude, result.longitude),
                    )
                distance.visibility = View.VISIBLE
            } else {
                distance.visibility = View.GONE
            }
            return row
        }
    }

    private fun formatDistance(meters: Float): String =
        when {
            meters < 1000f -> "${(Math.round(meters / 10f) * 10)} m"
            meters < 100_000f -> String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
            else -> "${Math.round(meters / 1000f)} km"
        }

    private fun onDestinationSelected(result: GeocodeResult) {
        liveSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        liveSearchRunnable = null
        searchSeq++ // a late search reply must not reopen the results list
        activeSearchResultsList?.visibility = View.GONE
        currentResults = emptyList()
        // Clear rather than fill with the picked destination's label - the
        // search box is about to be hidden (route preview/active nav takes
        // over), and leaving the full address in it meant the next time
        // search opened, it still showed the old destination instead of
        // the empty hint.
        activeSearchInput?.setText("")
        activeSearchInput?.clearFocus()

        val activeInput = activeSearchInput
        if (activeInput != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activeInput.windowToken, 0)
        }

        // A new destination picked mid-drive replaces whatever was active.
        if (navMode == NavMode.ACTIVE) endActiveNavigation()
        searchOpenedFromNav = false
        searchBackButton.visibility = View.GONE

        saveRecent(result)
        maybeSaveAsPendingPlace(result)
        destinationName = result.name

        topBar.visibility = View.GONE
        homeSheet.visibility = View.GONE
        recenterButton.visibility = View.GONE

        val destinationLatLng = LatLng(result.latitude, result.longitude)
        showDestinationMarker(destinationLatLng)

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

    /** Loads a map style (initial load, or a later switch e.g. the
     * Light/Dark setting). MapLibre's setStyle() fully replaces the style -
     * it does not merge into the existing one - so every custom
     * source/layer (location puck, route lines, destination pin, incident
     * reports) has to be re-added afterward, and on a restyle (not the
     * first load) any live state those sources held has to be redrawn into
     * the fresh, empty sources too. */
    private fun applyMapStyle(
        loadedMap: MapLibreMap,
        styleUrl: String,
        isRestyle: Boolean,
    ) {
        appliedMapDark = styleUrl == MAP_STYLE_URL_DARK
        loadedMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            setUpRouteLayers(style)
            if (isRestyle) {
                redrawRouteLines()
                destinationLatLng?.let { showDestinationMarker(it) }
                renderPoiPins()
                renderRoutePois()
                if (navMode == NavMode.ACTIVE) {
                    renderedCalloutStep = -1
                    renderTurnCallouts(lastNavState?.currentStepIndex ?: 1)
                }
                if (incidentFeatures.isNotEmpty()) {
                    incidentsSource?.setGeoJson(FeatureCollection.fromFeatures(incidentFeatures))
                }
            } else {
                ensureLocationPermissionThenTrack()
            }
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

    private var routePoiSource: GeoJsonSource? = null
    private val routePoisByIndex = HashMap<Int, List<RoadPoi>>()
    private var activeRoutePois: List<RoadPoi> = emptyList()

    @Volatile private var previewToken = 0
    private var routeListShowing = false
    private val extrasExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private fun makeTollIcon(): Bitmap {
        val w = 132
        val h = 92
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val box = RectF(5f, 5f, w - 5f, h - 5f)
        p.color = Color.parseColor("#1E5BD8")
        c.drawRoundRect(box, 22f, 22f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.WHITE
        c.drawRoundRect(box, 22f, 22f, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textSize = 40f
        c.drawText("TOLL", w / 2f, h / 2f + 14f, p)
        return bmp
    }

    private fun makeCameraIcon(): Bitmap {
        val s = 104
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = s / 2f
        p.color = Color.parseColor("#E5484D")
        c.drawCircle(m, m, m - 5f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.WHITE
        c.drawCircle(m, m, m - 5f, p)
        p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(26f, 38f, 78f, 70f), 7f, 7f, p) // body
        c.drawRoundRect(RectF(38f, 30f, 58f, 42f), 4f, 4f, p) // top bump
        p.color = Color.parseColor("#E5484D")
        c.drawCircle(52f, 54f, 9f, p) // lens
        return bmp
    }

    /** Draws the toll plazas / cameras that lie on the route being looked
     * at (previewed or driven). */
    private fun renderRoutePois() {
        val src = routePoiSource ?: return
        val pois =
            when (navMode) {
                NavMode.ACTIVE -> activeRoutePois
                NavMode.PREVIEW -> routePoisByIndex[selectedRouteIndex].orEmpty()
                else -> emptyList()
            }
        val features =
            pois.map { poi ->
                val f = Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude))
                f.addStringProperty("icon", if (poi.kind == "toll") POI_ICON_TOLL else POI_ICON_CAMERA)
                val price = if (poi.kind == "toll") RoadPoiService.parsePrice(poi.charge) else null
                f.addStringProperty("label", if (price != null) String.format(Locale.US, "RM%.2f", price) else "")
                f
            }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Looks up toll plazas / cameras for [routes] in the background. */
    private fun loadRoutePois(
        routes: List<Route>,
        token: Int,
    ) {
        extrasExecutor.execute {
            val found =
                try {
                    RoadPoiService.fetchAlong(routes)
                } catch (e: Exception) {
                    emptyMap()
                }
            mainHandler.post {
                if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                routePoisByIndex.putAll(found)
                renderRoutePois()
                updateRoutePreviewSummary()
                if (routeListShowing) renderRouteList()
            }
        }
    }

    private fun loadActiveRoutePois(route: Route) {
        extrasExecutor.execute {
            val found =
                try {
                    RoadPoiService.fetchAlong(listOf(route))[0].orEmpty()
                } catch (e: Exception) {
                    emptyList()
                }
            mainHandler.post {
                if (navMode == NavMode.ACTIVE && activeRoute === route) {
                    activeRoutePois = found
                    renderRoutePois()
                }
            }
        }
    }

    /** "Toll ~RM4.20" (all prices known), "Toll" (plaza on the route but no
     * price in the map data) or null (no toll found). */
    private fun tollSummary(pois: List<RoadPoi>?): String? {
        val tolls = pois?.filter { it.kind == "toll" } ?: return null
        if (tolls.isEmpty()) return null
        val prices = tolls.map { RoadPoiService.parsePrice(it.charge) }
        return if (prices.all { it != null }) {
            String.format(Locale.US, "Toll ~RM%.2f", prices.sumOf { it ?: 0.0 })
        } else {
            "Toll"
        }
    }

    private fun formatRouteMinutes(seconds: Double): String {
        val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    private fun styleRouteToggle() {
        val blue = ContextCompat.getColor(this, R.color.navos_blue)
        val grey = ContextCompat.getColor(this, R.color.navos_text_secondary)
        toggleMap.setBackgroundResource(if (routeListShowing) 0 else R.drawable.bg_toggle_selected)
        toggleList.setBackgroundResource(if (routeListShowing) R.drawable.bg_toggle_selected else 0)
        toggleMap.setTextColor(if (routeListShowing) grey else blue)
        toggleList.setTextColor(if (routeListShowing) blue else grey)
    }

    private fun setRouteListVisible(show: Boolean) {
        if (navMode != NavMode.PREVIEW) return
        routeListShowing = show
        routeListOverlay.visibility = if (show) View.VISIBLE else View.GONE
        routePreviewCard.visibility = if (show) View.GONE else View.VISIBLE
        styleRouteToggle()
        if (show) renderRouteList() else fitSelectedRoute()
    }

    /** One card per route, quickest first. Tap a card to pick it, Go to start. */
    private fun renderRouteList() {
        routeListItems.removeAllViews()
        val order = routeOptions.indices.sortedBy { routeOptions[it].durationSeconds }
        for (index in order) {
            val route = routeOptions[index]
            val row = LayoutInflater.from(this).inflate(R.layout.item_route_option, routeListItems, false)
            row.findViewById<TextView>(R.id.routeItemDuration).text = formatRouteMinutes(route.durationSeconds)
            val arrival = Date(System.currentTimeMillis() + (route.durationSeconds * 1000).toLong())
            row.findViewById<TextView>(R.id.routeItemMeta).text =
                SimpleDateFormat("h:mm a", Locale.US).format(arrival) + "  •  " + formatTripDistance(route.distanceMeters)
            val via = viaRoadName(route)
            val viaView = row.findViewById<TextView>(R.id.routeItemVia)
            viaView.text = if (via != null) "Via $via" else ""
            viaView.visibility = if (via != null) View.VISIBLE else View.GONE
            val toll = tollSummary(routePoisByIndex[index])
            val chip = row.findViewById<TextView>(R.id.routeItemToll)
            chip.text = toll.orEmpty()
            chip.visibility = if (toll != null) View.VISIBLE else View.GONE
            if (index == selectedRouteIndex) row.setBackgroundResource(R.drawable.bg_route_card_selected)
            row.setOnClickListener {
                selectedRouteIndex = index
                redrawRouteLines()
                updateRoutePreviewSummary()
                renderRouteList()
            }
            row.findViewById<View>(R.id.routeItemGo).setOnClickListener {
                selectedRouteIndex = index
                beginActiveNavigation()
            }
            routeListItems.addView(row)
        }
    }

    private fun fitSelectedRoute() {
        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        val bounds = LatLngBounds.Builder()
        selected.geometry.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120, 120, 120, dp(380)), 800)
    }

    private fun hideRouteChooser() {
        routeListShowing = false
        routeListOverlay.visibility = View.GONE
        routeViewToggle.visibility = View.GONE
    }

    /** Keeps hunting for more route choices after the first ones are shown. */
    private fun searchMoreRoutes(
        origin: RoutePoint,
        destination: RoutePoint,
        token: Int,
    ) {
        val snapshot = routeOptions
        extrasExecutor.execute {
            RoutingService.findExtraRoutes(origin, destination, snapshot, MAX_ROUTE_OPTIONS, { token != previewToken }) { found ->
                mainHandler.post {
                    if (token == previewToken && navMode == NavMode.PREVIEW) {
                        routeOptions = routeOptions + found
                        redrawRouteLines()
                        updateRoutePreviewSummary()
                        routeViewToggle.visibility = View.VISIBLE
                        if (routeListShowing) renderRouteList()
                    }
                }
            }
            mainHandler.post {
                if (token == previewToken && navMode == NavMode.PREVIEW && routeOptions.size > snapshot.size) {
                    loadRoutePois(routeOptions, token)
                }
            }
        }
    }

    private var turnCalloutSource: GeoJsonSource? = null
    private var renderedCalloutStep = -1

    private val TURN_ICON_RES =
        listOf(
            R.drawable.ic_turn_arrive,
            R.drawable.ic_turn_roundabout_left,
            R.drawable.ic_turn_roundabout_right,
            R.drawable.ic_turn_roundabout_straight,
            R.drawable.ic_turn_uturn,
            R.drawable.ic_turn_fork_left,
            R.drawable.ic_turn_fork_right,
            R.drawable.ic_turn_straight,
            R.drawable.ic_turn_sharp_right,
            R.drawable.ic_turn_right,
            R.drawable.ic_turn_slight_right,
            R.drawable.ic_turn_slight_left,
            R.drawable.ic_turn_left,
            R.drawable.ic_turn_sharp_left,
        )

    private fun turnIconId(res: Int): String = "navos-turn-" + resources.getResourceEntryName(res)

    // x position (in the icon's 48-unit grid) where each arrow's tail starts.
    // The arrow is shifted so this tail sits exactly on the icon's centre, i.e.
    // on the route line, instead of the whole arrow being centred on it.
    private val TURN_STEM_X =
        mapOf(
            R.drawable.ic_turn_left to 36f,
            R.drawable.ic_turn_right to 12f,
            R.drawable.ic_turn_sharp_left to 34f,
            R.drawable.ic_turn_sharp_right to 14f,
            R.drawable.ic_turn_slight_left to 33f,
            R.drawable.ic_turn_slight_right to 15f,
            R.drawable.ic_turn_uturn to 14f,
        )

    /** The same white Waze-style turn arrow as the bottom button, made
     * bolder with a navy outline, and shifted so its tail lies on the road. */
    private fun makeTurnArrow(res: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, res)?.mutate() ?: return null
        val size = 160
        val glyph = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shift = ((24f - (TURN_STEM_X[res] ?: 24f)) / 48f * glyph).toInt()
        val left = (size - glyph) / 2 + shift
        val top = (size - glyph) / 2
        drawable.setBounds(left, top, left + glyph, top + glyph)
        val dirs =
            listOf(
                1f to 0f, 0.71f to 0.71f, 0f to 1f, -0.71f to 0.71f,
                -1f to 0f, -0.71f to -0.71f, 0f to -1f, 0.71f to -0.71f,
            )

        fun stamp(
            color: Int,
            radius: Float,
        ) {
            drawable.setTint(color)
            for ((dx, dy) in dirs) {
                canvas.save()
                canvas.translate(dx * radius, dy * radius)
                drawable.draw(canvas)
                canvas.restore()
            }
        }
        stamp(Color.parseColor("#14284B"), 8f) // outline
        stamp(Color.WHITE, 3f) // fatten the white stroke
        drawable.setTint(Color.WHITE)
        drawable.draw(canvas)
        return bitmap
    }

    /** Direction of travel (0 = north) as the route arrives at [point]:
     * measured from a spot ~6 m back along the route line. */
    private fun incomingBearing(
        geometry: List<com.excellentmindblow.navos.routing.RoutePoint>,
        point: com.excellentmindblow.navos.routing.RoutePoint,
    ): Double {
        if (geometry.size < 2) return 0.0
        var nearest = 0
        var best = Double.MAX_VALUE
        for (i in geometry.indices) {
            val d = GeoUtils.distanceMeters(geometry[i], point)
            if (d < best) {
                best = d
                nearest = i
            }
        }
        if (nearest == 0) return GeoUtils.bearingDegrees(geometry[0], geometry[1])
        var j = nearest - 1
        while (j > 0 && GeoUtils.distanceMeters(geometry[j], geometry[nearest]) < 6.0) j--
        return GeoUtils.bearingDegrees(geometry[j], geometry[nearest])
    }

    /** Draws an arrow + road name on the route line at each upcoming turn
     * (from [fromStep] on). Cleared whenever we're not driving. */
    private fun renderTurnCallouts(fromStep: Int) {
        val src = turnCalloutSource ?: return
        val steps = activeRoute?.steps
        val geometry = activeRoute?.geometry ?: emptyList()
        if (navMode != NavMode.ACTIVE || steps == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features = ArrayList<Feature>()
        for (i in fromStep.coerceAtLeast(0) until steps.size) {
            if (features.size >= MAX_TURN_CALLOUTS) break
            val s = steps[i]
            if (s.maneuverType == "depart" || s.maneuverType == "arrive") continue
            val mod = s.modifier.orEmpty()
            val turnLike =
                s.maneuverType.contains("roundabout") || s.maneuverType.contains("rotary") ||
                    s.maneuverType == "fork" || s.maneuverType.contains("ramp") ||
                    (mod.isNotEmpty() && mod != "straight")
            if (!turnLike) continue
            val feature = Feature.fromGeometry(Point.fromLngLat(s.point.longitude, s.point.latitude))
            feature.addStringProperty("icon", turnIconId(iconForManeuver(s.maneuverType, s.modifier)))
            feature.addStringProperty("name", s.roadName)
            feature.addNumberProperty("order", i)
            feature.addNumberProperty("bearing", incomingBearing(geometry, s.point))
            features.add(feature)
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Pushes the camera's centre down the screen while driving so the
     * arrow sits in the lower part of the view (Waze-style look-ahead). */
    private fun applyNavPadding(on: Boolean) {
        val m = map ?: return
        val top = if (on) (mapView.height * NAV_TOP_PADDING_FRACTION).toInt() else 0
        m.setPadding(0, top, 0, 0)
    }

    private fun setUpRouteLayers(style: Style) {
        // The base style's own road/POI name labels are symbol layers
        // further up its layer stack. style.addLayer() appends at the very
        // top by default, which was silently burying every label under our
        // route lines and the destination pin - inserting our route layers
        // below the first symbol layer keeps street names readable on top
        // of the route, matching how Google Maps/Waze draw it. (The
        // destination pin is deliberately NOT inserted this way - it's
        // meant to sit on top of labels too, like a real map pin.)
        val firstSymbolLayerId = style.layers.firstOrNull { it is SymbolLayer }?.id
        fun addBelowLabels(layer: Layer) {
            if (firstSymbolLayerId != null) {
                style.addLayerBelow(layer, firstSymbolLayerId)
            } else {
                style.addLayer(layer)
            }
        }

        // 3D buildings - the liberty style's vector tiles already carry
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
            if (SHOW_3D_BUILDINGS) addBelowLabels(buildingsLayer)
        } catch (e: Exception) {
            // Missing source/source-layer on whatever style version is
            // live - buildings just stay flat, nothing else breaks.
        }

        // Alternates first so the selected route's layers (added next) end
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
        addBelowLabels(altLayer)

        val source = GeoJsonSource(ROUTE_SOURCE_ID)
        style.addSource(source)
        routeSource = source

        // A white casing under the blue line, same two-tone look Google
        // Maps draws its route in, instead of a single flat line.
        val routeCasingLayer =
            LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.WHITE),
                PropertyFactory.lineWidth(routeWidthByZoom(6.5f, 10.5f, 16f, 22f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(routeCasingLayer)

        val layer =
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1CB0F6")),
                PropertyFactory.lineWidth(routeWidthByZoom(4f, 7f, 11f, 16f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(layer)

        // Turn callouts: a white arrow at every corner of the route plus a
        // blue name bubble above it, like Waze. Above the route line, below
        // the destination pin and the driver's arrow (added after this).
        // Toll plazas and speed cameras along the route (always visible).
        style.addImage(POI_ICON_TOLL, makeTollIcon())
        style.addImage(POI_ICON_CAMERA, makeCameraIcon())
        val poiSrc = GeoJsonSource(ROUTE_POI_SOURCE_ID)
        style.addSource(poiSrc)
        routePoiSource = poiSrc
        style.addLayer(
            SymbolLayer(ROUTE_POI_LAYER_ID, ROUTE_POI_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(0.55f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(Color.parseColor("#14284B")),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                PropertyFactory.textOptional(true),
                PropertyFactory.textAllowOverlap(true),
            ),
        )
        for (res in TURN_ICON_RES) {
            makeTurnArrow(res)?.let { style.addImage(turnIconId(res), it) }
        }
        val turnSrc = GeoJsonSource(TURN_SOURCE_ID)
        style.addSource(turnSrc)
        turnCalloutSource = turnSrc
        // One layer: the badge, with the road-name bubble above it. If two
        // corners are too close, the earlier one wins and the other is
        // hidden (instead of piling up); a name that doesn't fit is dropped
        // but its badge stays.
        style.addLayer(
            SymbolLayer(TURN_ARROW_LAYER_ID, TURN_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconSize(0.9f),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconAllowOverlap(false),
                PropertyFactory.iconIgnorePlacement(false),
                PropertyFactory.iconPadding(8f),
                // Lie flat ON the road and turn with it, like Waze.
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textHaloColor(Color.parseColor("#1B77A0")),
                PropertyFactory.textHaloWidth(5f),
                PropertyFactory.textMaxWidth(9f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
                PropertyFactory.textOffset(arrayOf(0f, -1.9f)),
                PropertyFactory.textOptional(true),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textPadding(6f),
                PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.symbolSortKey(Expression.get("order")),
            ),
        )

        // Destination pin - added last, with the plain addLayer() that
        // puts it above everything (including labels), same as a real pin.
        val pinBitmap = ContextCompat.getDrawable(this, R.drawable.ic_destination_pin)?.toBitmap()
        if (pinBitmap != null) {
            style.addImage(DESTINATION_ICON_ID, pinBitmap)
        }

        val destSource = GeoJsonSource(DESTINATION_SOURCE_ID)
        style.addSource(destSource)
        destinationSource = destSource

        val destLayer =
            SymbolLayer(DESTINATION_LAYER_ID, DESTINATION_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(DESTINATION_ICON_ID),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
            )
        style.addLayer(destLayer)

        // The driver's 3D arrow - drawn above everything (labels, pin),
        // upright to the screen like Waze's, rotated to the heading.
        style.addImage(PUCK_ICON_ID, PuckIcon.create(this))
        val puckSrc = GeoJsonSource(PUCK_SOURCE_ID)
        style.addSource(puckSrc)
        val puckLayer =
            SymbolLayer(PUCK_LAYER_ID, PUCK_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(PUCK_ICON_ID),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
            )
        style.addLayer(puckLayer)
        driveFollower.puckSource = puckSrc // also redraws it after a restyle

        // Incident reports ("What do you see?") - also on top of labels,
        // same as the destination pin. One source holds every reported
        // type; each feature carries which icon to draw via a data
        // expression instead of needing a separate layer per type.
        registerIncidentIcon(style, TRAFFIC_ICON_ID, R.drawable.ic_report_traffic)
        registerIncidentIcon(style, POLICE_ICON_ID, R.drawable.ic_report_police)
        registerIncidentIcon(style, HAZARD_ICON_ID, R.drawable.ic_report_hazard)
        registerIncidentIcon(style, CRASH_ICON_ID, R.drawable.ic_report_crash)

        val incidentsSrc = GeoJsonSource(INCIDENTS_SOURCE_ID)
        style.addSource(incidentsSrc)
        incidentsSource = incidentsSrc

        val incidentsLayer =
            SymbolLayer(INCIDENTS_LAYER_ID, INCIDENTS_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get(INCIDENT_ICON_PROPERTY)),
                PropertyFactory.iconAllowOverlap(true),
            )
        style.addLayer(incidentsLayer)

        setUpPoiLayer(style)
    }

    /** Petrol (orange) and EV (green) pins - above street labels but below
     * the destination pin, so the destination always stays on top. */
    private fun setUpPoiLayer(style: Style) {
        registerIncidentIcon(style, FUEL_PIN_ICON_ID, R.drawable.ic_poi_fuel_pin)
        registerIncidentIcon(style, EV_PIN_ICON_ID, R.drawable.ic_poi_ev_pin)

        val src = GeoJsonSource(POI_SOURCE_ID)
        style.addSource(src)
        poiSource = src

        val layer =
            SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get(POI_ICON_PROPERTY)),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
            )
        style.addLayerBelow(layer, DESTINATION_LAYER_ID)
        renderPoiPins()
    }

    private fun registerIncidentIcon(
        style: Style,
        iconId: String,
        drawableRes: Int,
    ) {
        val bitmap = ContextCompat.getDrawable(this, drawableRes)?.toBitmap()
        if (bitmap != null) {
            style.addImage(iconId, bitmap)
        }
    }

    private fun showDestinationMarker(latLng: LatLng) {
        destinationLatLng = latLng
        destinationSource?.setGeoJson(Point.fromLngLat(latLng.longitude, latLng.latitude))
    }

    private fun clearDestinationMarker() {
        destinationLatLng = null
        destinationSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
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
                    previewToken++
                    routePoisByIndex.clear()
                    showRoutePreview()
                    loadRoutePois(routes, previewToken)
                    searchMoreRoutes(originPoint, destinationPoint, previewToken)
                } else {
                    clearDestinationMarker()
                    followingUser = true
                    homeSheet.visibility = View.VISIBLE
                    setHomeSheetState(HomeSheetState.SEARCH)
                    Toast.makeText(this, "Couldn't calculate a route to that destination", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Reroute-on-off-route used to live here, recalculating from
    // lastKnownLocation back to the destination whenever NavigationEngine
    // flagged offRoute=true. It now lives in NavigationService instead
    // (same RoutingService call, same "only apply if still this trip"
    // guard) so a reroute still happens even if the driver has locked the
    // screen or switched apps - the whole reason that logic needed a
    // longer-lived home than this Activity. The service reports the result
    // back via NavigationService.Listener.onRerouted() above, which is
    // what redraws the route line here.

    // ---- Route preview (pre-navigation) --------------------------------

    /** Tap, or swipe up/down, on the drive bar's handle (or tap its time) to
     * reveal / hide the red Stop pill - Waze keeps Stop one swipe away so it
     * isn't hit by accident on the main bar. */
    private fun setUpNavSheetToggle() {
        var downY = 0f
        findViewById<View>(R.id.navSheetHandle).setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - downY
                    val threshold = dp(20)
                    when {
                        dy < -threshold -> setNavSheetExpanded(true)
                        dy > threshold -> setNavSheetExpanded(false)
                        else -> setNavSheetExpanded(!navSheetExpanded)
                    }
                    v.performClick()
                    true
                }
                else -> true
            }
        }
        findViewById<View>(R.id.navEtaCenter).setOnClickListener { setNavSheetExpanded(!navSheetExpanded) }
    }

    /** Lifts every floating drive control by however far the drive bar has
     * grown past its collapsed height, so they stay just above the card
     * instead of being covered when the Stop pill is revealed. */
    private fun updateNavFloatingOffset() {
        val floating = listOf(speedBadge, reportButton, poiToggleColumn, recenterButton, poiCard)
        if (bottomNavBar.visibility != View.VISIBLE || navBarRow.height == 0) {
            floating.forEach { if (it.translationY != 0f) it.translationY = 0f }
            return
        }
        val parentHeight = (bottomNavBar.parent as View).height
        val collapsedHeight = bottomNavBar.paddingTop + bottomNavBar.paddingBottom + dp(24) + navBarRow.height
        val extra = ((parentHeight - collapsedHeight) - bottomNavBar.top).coerceAtLeast(0)
        val ty = -extra.toFloat()
        floating.forEach { if (it.translationY != ty) it.translationY = ty }
    }

    private fun setNavSheetExpanded(expanded: Boolean) {
        if (expanded == navSheetExpanded) return
        navSheetExpanded = expanded
        TransitionManager.beginDelayedTransition(bottomNavBar as ViewGroup, AutoTransition().setDuration(220))
        navStopButton.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    /** "3:38 PM" (arrival clock time, big) and "1:11 h • 61 km" (time left
     * and distance left, under it) - the same two-line summary Waze shows. */
    private fun showTripSummary(
        durationSeconds: Double,
        distanceMeters: Double,
        clockView: TextView,
        detailView: TextView,
    ) {
        val arrival = Date(System.currentTimeMillis() + (durationSeconds * 1000).toLong())
        clockView.text = SimpleDateFormat("h:mm a", Locale.US).format(arrival)
        detailView.text = "${formatTripDuration(durationSeconds)} • ${formatTripDistance(distanceMeters)}"
    }

    private fun formatTripDuration(seconds: Double): String {
        val totalMinutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (totalMinutes >= 60) {
            String.format(Locale.US, "%d:%02d h", totalMinutes / 60, totalMinutes % 60)
        } else {
            "$totalMinutes min"
        }
    }

    private fun formatTripDistance(meters: Double): String =
        when {
            meters < 1000 -> "${meters.toInt()} m"
            meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
            else -> String.format(Locale.US, "%.0f km", meters / 1000.0)
        }

    /** The road this route spends the most distance on - the "Via ..." line. */
    private fun viaRoadName(route: Route): String? =
        route.steps
            .filter { it.roadName.isNotBlank() }
            .groupBy { it.roadName }
            .maxByOrNull { (_, steps) -> steps.sumOf { it.distanceMeters } }
            ?.key

    private fun showRoutePreview() {
        navMode = NavMode.PREVIEW
        topBar.visibility = View.GONE
        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        homeSheet.visibility = View.GONE
        recenterButton.visibility = View.GONE
        // Also reachable mid-drive via the drive bar's alt-route icon, so
        // clear the ACTIVE-only chrome too (harmless no-ops if already gone).
        speedBadge.visibility = View.GONE
        reportButton.visibility = View.GONE
        poiToggleColumn.visibility = View.GONE
        hidePoiCard()
        nextTurnStrip.visibility = View.GONE
        laneContainer.visibility = View.GONE
        renderPoiPins() // pins are drive-only
        redrawRouteLines()

        routeOptions.getOrNull(selectedRouteIndex) ?: return
        fitSelectedRoute()

        updateRoutePreviewSummary()
        routePreviewCard.visibility = View.VISIBLE
        routeListShowing = false
        routeListOverlay.visibility = View.GONE
        routeViewToggle.visibility = if (routeOptions.size > 1) View.VISIBLE else View.GONE
        styleRouteToggle()
    }

    private fun updateRoutePreviewSummary() {
        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        showTripSummary(selected.durationSeconds, selected.distanceMeters, routePreviewEta, routePreviewSummary)
        val via = viaRoadName(selected)
        val toll = tollSummary(routePoisByIndex[selectedRouteIndex])
        routePreviewVia.text = (if (via != null) "Via $via" else "") + (if (toll != null) "\n$toll" else "")
        routePreviewVia.visibility = if (via != null || toll != null) View.VISIBLE else View.GONE
        val destination = destinationName.orEmpty()
        routePreviewDestination.text = destination
        routePreviewDestination.visibility = if (destination.isNotBlank()) View.VISIBLE else View.GONE
        routePreviewHint.text =
            if (routeOptions.size > 1) {
                "${routeOptions.size} routes found - tap a grey route, or open List"
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
        renderRoutePois()
    }

    private fun onMapTapped(
        map: MapLibreMap,
        latLng: LatLng,
    ): Boolean {
        if (navMode == NavMode.ACTIVE) return onDriveMapTapped(map, latLng)
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
        applyPendingUiRecreate()
        routeOptions = emptyList()
        previewToken++
        routePoisByIndex.clear()
        hideRouteChooser()
        renderRoutePois()
        routePreviewCard.visibility = View.GONE
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        clearDestinationMarker()
        followingUser = true
        homeSheet.visibility = View.VISIBLE
        setHomeSheetState(HomeSheetState.COLLAPSED)
    }

    // ---- Active (driving) navigation ------------------------------------

    private fun beginActiveNavigation() {
        val route = routeOptions.getOrNull(selectedRouteIndex) ?: return

        navMode = NavMode.ACTIVE
        smoothedBearing = null
        smoothedNavZoom = null
        lastNavState = null
        navFollowing = true
        snapSegmentIndex = 0
        topBar.visibility = View.GONE
        recenterButton.visibility = View.GONE
        routePreviewCard.visibility = View.GONE
        // Only the chosen route stays on screen once driving - alternates
        // were just for picking, not useful mid-drive.
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

        activeRoute = route
        hideRouteChooser()
        previewToken++
        activeRoutePois = routePoisByIndex[selectedRouteIndex].orEmpty()
        renderRoutePois()
        if (activeRoutePois.isEmpty()) loadActiveRoutePois(route)
        applyNavPadding(true)
        renderedCalloutStep = -1
        renderTurnCallouts(1)
        bottomNavBar.visibility = View.VISIBLE
        speedBadge.visibility = View.VISIBLE
        reportButton.visibility = View.VISIBLE
        poiToggleColumn.visibility = View.VISIBLE
        turnDistanceText.text = ""
        turnStreetText.text = "Starting navigation..."
        showTripSummary(route.durationSeconds, route.distanceMeters, etaClockText, etaDetailText)
        navSheetExpanded = false
        navStopButton.visibility = View.GONE

        // Waze-style 3D follow camera, driven by driveFollower on every
        // display frame together with the arrow (not by LocationComponent's
        // own camera tracking, which resets tilt on every GPS update).
        lastKnownLocation?.let { location -> updateNavCamera(location, durationMs = NAV_RECENTER_EASE_MS) }
        renderPoiPins()
        lastKnownLocation?.let { maybeFetchPois(it) }

        // The actual GPS subscription + NavigationEngine + voice guidance
        // move to NavigationService for the duration of the drive, so they
        // keep running when the screen locks or this Activity is
        // backgrounded - stop this Activity's own idle tracker first so
        // there's only ever one GPS subscription live at a time.
        locationTracker?.stop()
        locationTracker = null
        startAndBindNavigationService(route)
    }

    /** Starts NavigationService as a foreground service (so it survives
     * this Activity going to the background) and binds to it so this
     * Activity can mirror its state into the UI while visible. Safe to
     * call again later (e.g. re-entering ACTIVE after process recreation)
     * - binding again is a no-op if already bound. */
    private fun startAndBindNavigationService(route: Route) {
        requestNotificationPermissionIfNeeded()
        val intent = Intent(this, NavigationService::class.java)
        ContextCompat.startForegroundService(this, intent)
        val existingService = navigationService
        if (existingService != null) {
            existingService.startNavigation(route)
            return
        }
        pendingStartRoute = route
        if (!navServiceBound) {
            bindService(intent, navServiceConnection, Context.BIND_AUTO_CREATE)
            navServiceBound = true
        }
    }

    @SuppressLint("InlinedApi") // guarded by the SDK_INT check itself
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Only unbinds - does NOT stop the service. Unbinding alone lets
     * NavigationService keep running as a started foreground service (GPS,
     * engine, voice, notification all still going) even while nothing is
     * bound to it, which is exactly the point: this Activity can safely
     * detach while backgrounded without ending the drive. */
    private fun unbindNavigationService() {
        if (navServiceBound) {
            navigationService?.setListener(null)
            unbindService(navServiceConnection)
            navServiceBound = false
        }
        navigationService = null
    }

    /** Keeps the 3D follow camera (tilt + zoom + heading) locked on the
     * driver's position during active navigation. Called on every GPS fix
     * instead of leaning on LocationComponent's own camera tracking - and
     * skipped while the driver has manually panned/tilted away from it, so
     * tapping Position (recenterCamera()) is the only thing that forces
     * the camera back rather than fighting the gesture every second. */
    private fun updateNavCamera(
        location: Location,
        durationMs: Int = NAV_FOLLOW_EASE_MS,
    ) {
        var lat = location.latitude
        var lng = location.longitude
        var rawBearing = if (location.hasBearing()) location.bearing.toDouble() else null

        // Snap the arrow onto the blue line (GPS drifts a few metres off
        // the road, which is why it sat beside the line) and take the
        // heading from the line itself so the arrow points along it.
        val geometry = activeRoute?.geometry
        if (geometry != null) {
            val snap =
                GeoUtils.snapToPolyline(
                    RoutePoint(lat, lng),
                    geometry,
                    snapSegmentIndex - SNAP_LOOKBACK_SEGMENTS,
                    SNAP_LOOKAHEAD_SEGMENTS,
                )
            if (snap != null && snap.distanceMeters <= SNAP_MAX_METERS) {
                lat = snap.point.latitude
                lng = snap.point.longitude
                snapSegmentIndex = snap.segmentIndex
                rawBearing = snap.bearing
                // Start already facing along the route, not north.
                if (smoothedBearing == null) smoothedBearing = snap.bearing
            }
        }

        val bearing = smoothedNavBearing(rawBearing, location)
        // Start the glide BEFORE the new fix is pushed, so even a big jump
        // is eased instead of snapping the camera in one frame.
        if (durationMs > NAV_FOLLOW_EASE_MS) driveFollower.beginCameraBlend(durationMs.toLong())
        driveFollower.pushFix(lat, lng, bearing, smartNavZoom(location))
    }

    /** Zoom for the follow camera: cruising zoom, nudged by speed (a little
     * closer when crawling, wider at highway speed) and pulled in as the
     * next turn / U-turn / roundabout approaches so the junction is easy to
     * read, then eased back out once it's behind. Smoothed across GPS fixes
     * so it glides instead of stepping. */
    private fun smartNavZoom(location: Location): Double {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val speedAdjust =
            when {
                speedKmh < 25.0 -> NAV_ZOOM_SLOW_BONUS * (1.0 - speedKmh / 25.0)
                speedKmh > 70.0 -> -NAV_ZOOM_FAST_PENALTY * ((speedKmh - 70.0) / 50.0).coerceAtMost(1.0)
                else -> 0.0
            }
        val target = NAV_ZOOM + speedAdjust + turnZoomBoost(lastNavState, speedKmh)
        val previous = smoothedNavZoom
        val next = if (previous == null) target else previous + (target - previous) * NAV_ZOOM_SMOOTHING
        smoothedNavZoom = next
        return next
    }

    /** How much closer to zoom for the upcoming maneuver: 0 on straights and
     * far from the turn, rising smoothly to a maximum right at the junction.
     * Sharper maneuvers get more (U-turn most), and the lead distance grows
     * with speed so a fast driver gets the zoom in time. */
    private fun turnZoomBoost(
        state: NavigationState?,
        speedKmh: Double,
    ): Double {
        if (state == null || state.isArrival || state.offRoute) return 0.0
        val maxBoost =
            when {
                state.modifier == "uturn" -> 1.6
                state.maneuverType == "roundabout" || state.maneuverType == "rotary" -> 1.0
                state.modifier == "left" || state.modifier == "right" ||
                    state.modifier == "sharp left" || state.modifier == "sharp right" -> 1.2
                state.modifier == "slight left" || state.modifier == "slight right" -> 0.6
                else -> 0.0 // straight / continue / merge / depart
            }
        if (maxBoost == 0.0) return 0.0
        val leadMeters = (speedKmh * 4.0).coerceIn(200.0, 450.0)
        val distance = state.distanceToNextTurnMeters
        if (distance >= leadMeters) return 0.0
        val t = 1.0 - distance / leadMeters
        return maxBoost * t * t * (3.0 - 2.0 * t) // smoothstep
    }

    /** Positions the recenter button for the current screen. During a drive
     * the bottom-right column is taken by the report and POI buttons (the
     * report button sits in exactly the spot the free-roam recenter button
     * uses and would hide it), so while driving it goes bottom-left, just
     * above the speed badge. In free-roam it keeps its layout position. */
    private fun placeRecenterButton(drivingLayout: Boolean) {
        val params = recenterButton.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        val density = resources.displayMetrics.density
        if (drivingLayout) {
            params.gravity = Gravity.BOTTOM or Gravity.START
            params.marginStart = (12 * density).toInt()
            params.marginEnd = 0
            params.bottomMargin = (210 * density).toInt()
        } else {
            params.gravity = Gravity.BOTTOM or Gravity.END
            params.marginStart = 0
            params.marginEnd = (12 * density).toInt()
            params.bottomMargin = (120 * density).toInt()
        }
        recenterButton.layoutParams = params
    }

    /** The Position/recenter button's actual behavior, chosen by whether a
     * drive is active - a flat "jump back to my dot" pan everywhere would
     * leave active navigation looking nothing like the 3D follow camera it
     * was in before the driver panned away. */
    private fun recenterCamera() {
        val location = lastKnownLocation
        if (navMode == NavMode.ACTIVE) {
            navFollowing = true
            smoothedNavZoom = null
            recenterButton.visibility = View.GONE
            if (location != null) {
                updateNavCamera(location, durationMs = NAV_RECENTER_EASE_MS)
            }
            // else: no fix yet - navFollowing is already true, so the next
            // GPS update (whichever arrives first) applies it automatically.
            return
        }

        followingUser = true
        recenterButton.visibility = View.GONE
        if (location == null) {
            Toast.makeText(this, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
            return
        }
        driveFollower.holdCamera(NAV_RECENTER_EASE_MS + 50L)
        map?.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition
                    .Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .tilt(0.0)
                    .zoom(IDLE_RECENTER_ZOOM)
                    .build(),
            ),
            NAV_RECENTER_EASE_MS,
        )
    }

    /** Softens the nav camera's heading so it eases into turns instead of
     * snapping. Raw GPS bearing is noisy (especially near-stationary) and,
     * fed straight into the camera, can also spin the "long way round"
     * whenever it crosses the 0/360 wrap (e.g. 350 -> 10). We keep an
     * unwrapped running bearing, always step toward the new reading via the
     * shortest signed angle, and only take a fraction of that step per
     * update so the turn reads as a smooth ease rather than a jump. */
    private fun smoothedNavBearing(rawBearing: Double?, location: Location): Double {
        val current = smoothedBearing ?: map?.cameraPosition?.bearing ?: 0.0
        // GPS heading is unreliable while nearly stationary - ignore it
        // rather than let the camera jitter in place at a red light.
        val reliable = rawBearing != null && (!location.hasSpeed() || location.speed > 1.0f)
        if (!reliable) return current

        val currentHeading = ((current % 360.0) + 360.0) % 360.0
        var delta = rawBearing!! - currentHeading
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0

        val next = current + delta * 0.35
        smoothedBearing = next
        return next
    }

    private fun endActiveNavigation() {
        navMode = NavMode.IDLE
        applyPendingUiRecreate()
        activeRoute = null
        applyNavPadding(false)
        activeRoutePois = emptyList()
        renderRoutePois()
        setRecalculating(false)
        renderedCalloutStep = -1
        renderTurnCallouts(0)
        closeNextTurns()
        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        speedBadge.visibility = View.GONE
        reportButton.visibility = View.GONE
        poiToggleColumn.visibility = View.GONE
        hidePoiCard()
        nextTurnStrip.visibility = View.GONE
        laneContainer.visibility = View.GONE
        recenterButton.visibility = View.GONE
        homeSheet.visibility = View.VISIBLE
        setHomeSheetState(HomeSheetState.COLLAPSED)
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        routeOptions = emptyList()
        clearDestinationMarker()
        nearbyPois = emptyList()
        lastPoiFetchLocation = null
        lastPoiFetchAtMs = 0L
        renderPoiPins()

        // Idempotent - safe to call whether this end was user-initiated
        // (which still needs to tell the service to stop) or reported by
        // the service itself (arrival, or the notification's "End
        // navigation" action), in which case it's already stopped.
        navigationService?.stopNavigation()
        unbindNavigationService()
        // The drive's own GPS subscription is gone with the service -
        // resume this Activity's idle one for free-roam map following.
        startLocationTracking()

        followingUser = true
        navFollowing = true

        val location = lastKnownLocation
        if (location != null) {
            driveFollower.holdCamera(650L)
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

    /** Slides the "Recalculating..." pill in (spinner + pulsing text) or out. */
    private fun setRecalculating(show: Boolean) {
        if (show == recalcShown) return
        recalcShown = show
        val slide = 24f * resources.displayMetrics.density
        recalcPill.animate().cancel()
        if (show) {
            recalcPill.visibility = View.VISIBLE
            recalcPill.alpha = 0f
            recalcPill.translationY = -slide
            recalcPill.animate().alpha(1f).translationY(0f).setDuration(220L).start()
            recalcPulse?.cancel()
            recalcPulse =
                ObjectAnimator.ofFloat(recalcText, View.ALPHA, 1f, 0.45f).apply {
                    duration = 700L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
        } else {
            recalcPulse?.cancel()
            recalcText.alpha = 1f
            recalcPill
                .animate()
                .alpha(0f)
                .translationY(-slide)
                .setDuration(180L)
                .withEndAction { if (!recalcShown) recalcPill.visibility = View.GONE }
                .start()
        }
    }

    private fun onNavigationState(state: NavigationState) {
        setRecalculating(state.offRoute && !state.isArrival)
        lastNavState = state
        val distanceLabel = formatCountdownDistance(state.distanceToNextTurnMeters)
        turnDistanceText.text = if (state.offRoute) "" else distanceLabel
        // Off the planned route: NavigationService is already fetching a new
        // one (and says "Recalculating"), so show that instead of a turn
        // instruction that no longer applies.
        turnStreetText.text = if (state.offRoute) "Recalculating..." else state.instruction

        showTripSummary(state.durationRemainingSeconds, state.distanceRemainingMeters, etaClockText, etaDetailText)

        if (state.currentStepIndex != renderedCalloutStep) {
            renderedCalloutStep = state.currentStepIndex
            renderTurnCallouts(state.currentStepIndex)
        }

        turnIcon.setImageResource(iconForManeuver(state.maneuverType, state.modifier))
        turnIcon.rotation = 0f

        if (nextTurnsSheet.visibility == View.VISIBLE) {
            if (state.currentStepIndex != nextTurnsRenderedIndex) {
                refreshNextTurns()
            } else {
                nextTurnsFirstDistance?.text = formatTurnDistance(state.distanceToNextTurnMeters)
            }
        }

        // "and then" look-ahead - only worth showing once the current turn
        // is close enough that the next one is relevant, same as Waze.
        val nextType = state.nextManeuverType
        if (nextType != null && state.distanceToNextTurnMeters <= 400 && !state.isArrival) {
            nextTurnIcon.setImageResource(iconForManeuver(nextType, state.nextModifier))
            nextTurnIcon.rotation = 0f
            nextTurnStrip.visibility = View.VISIBLE
        } else {
            nextTurnStrip.visibility = View.GONE
        }

        // Lane guidance - needs to show earlier than "and then" since the
        // driver has to physically move lanes before the junction, not
        // just know it's coming.
        if (state.distanceToNextTurnMeters <= 600 && !state.isArrival) {
            updateLaneGuidance(state.lanes)
        } else {
            laneContainer.visibility = View.GONE
        }

        // NavigationService detects arrival and off-route itself (it owns
        // the engine now) and already stops/reroutes on its own; this is
        // just a UI-side safety net in case a state update ever reaches
        // here before that - endActiveNavigation()/the service's own stop
        // are both idempotent, so a harmless double-call is fine.
        if (state.isArrival && state.distanceToNextTurnMeters <= 30) {
            endActiveNavigation()
        }
    }

    /** Picks the Waze-style arrow for a maneuver: a distinct drawn arrow
     * for every turn angle, plus U-turn, fork/ramp, roundabout (exit side)
     * and arrival. No rotation tricks - each is its own icon. */
    private fun iconForManeuver(
        maneuverType: String,
        modifier: String?,
    ): Int {
        val mod = modifier.orEmpty()
        return when {
            maneuverType == "arrive" -> R.drawable.ic_turn_arrive
            maneuverType.contains("roundabout") || maneuverType.contains("rotary") ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_roundabout_left
                    mod.contains("right") -> R.drawable.ic_turn_roundabout_right
                    else -> R.drawable.ic_turn_roundabout_straight
                }
            maneuverType == "uturn" || mod == "uturn" -> R.drawable.ic_turn_uturn
            maneuverType == "fork" || maneuverType == "off ramp" || maneuverType == "on ramp" ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_fork_left
                    mod.contains("right") -> R.drawable.ic_turn_fork_right
                    else -> R.drawable.ic_turn_straight
                }
            else ->
                when (mod) {
                    "sharp right" -> R.drawable.ic_turn_sharp_right
                    "right" -> R.drawable.ic_turn_right
                    "slight right" -> R.drawable.ic_turn_slight_right
                    "slight left" -> R.drawable.ic_turn_slight_left
                    "left" -> R.drawable.ic_turn_left
                    "sharp left" -> R.drawable.ic_turn_sharp_left
                    else -> R.drawable.ic_turn_straight // straight / depart / continue / new name / merge
                }
        }
    }

    /** Turn countdown shown on the round button: km with one decimal from
     * 1 km up, then 950 m, 900 m ... 100 m in 50 m steps, then 10 m steps
     * for the last stretch. */
    private fun formatCountdownDistance(meters: Double): String =
        when {
            meters >= 1000 -> String.format(Locale.US, "%.1f km", meters / 1000)
            meters >= 100 -> "${(Math.round(meters / 50.0) * 50).toInt().coerceAtMost(950)} m"
            else -> "${(Math.round(meters / 10.0) * 10).toInt().coerceAtLeast(10)} m"
        }

    private fun formatTurnDistance(meters: Double): String =
        if (meters >= 1000) {
            String.format(Locale.US, "%.1f km", meters / 1000)
        } else {
            // Round to 10 m like Waze's list so it doesn't flicker every fix.
            "${(Math.round(meters / 10.0) * 10).toInt().coerceAtLeast(10)} m"
        }

    private fun openNextTurns() {
        if (navMode != NavMode.ACTIVE) return
        nextTurnsRenderedIndex = -1
        refreshNextTurns()
        nextTurnsSheet.visibility = View.VISIBLE
    }

    private fun closeNextTurns() {
        if (!::nextTurnsSheet.isInitialized) return
        nextTurnsSheet.visibility = View.GONE
    }

    /** Rebuilds the Next-turns list from the live step index: the upcoming
     * maneuver first (distance from where you are now), then each later
     * turn with the distance between it and the previous one. */
    private fun refreshNextTurns() {
        val steps = activeRoute?.steps ?: return
        if (steps.isEmpty()) return
        val state = lastNavState
        val startIndex = (state?.currentStepIndex ?: 1).coerceIn(0, steps.size - 1)
        nextTurnsRenderedIndex = startIndex
        nextTurnsList.removeAllViews()
        nextTurnsFirstDistance = null
        var shown = 0
        for (i in startIndex until steps.size) {
            if (shown >= 40) break
            val meters =
                if (i == startIndex) {
                    state?.distanceToNextTurnMeters ?: steps.getOrNull(i - 1)?.distanceMeters ?: 0.0
                } else {
                    steps[i - 1].distanceMeters
                }
            val row = buildTurnRow(steps[i], meters, shaded = shown % 2 == 1, isFirst = shown == 0)
            nextTurnsList.addView(row)
            shown++
        }
    }

    private fun buildTurnRow(
        step: RouteStep,
        meters: Double,
        shaded: Boolean,
        isFirst: Boolean,
    ): View {
        val density = resources.displayMetrics.density

        fun px(v: Int) = (v * density).toInt()
        val textColor = ContextCompat.getColor(this, R.color.navos_text)
        val streetColor = ContextCompat.getColor(this, R.color.navos_blue)
        val isArrival = step.maneuverType == "arrive"

        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(20), px(14), px(20), px(14))
                if (shaded) setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.navos_surface_alt))
            }
        val icon =
            ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(px(52), px(52)).apply { marginEnd = px(18) }
                setImageResource(iconForManeuver(step.maneuverType, step.modifier))
                setColorFilter(textColor)
            }
        val texts =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        val distance =
            TextView(this).apply {
                text = formatTurnDistance(meters)
                setTextColor(textColor)
                textSize = 24f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        if (isFirst) nextTurnsFirstDistance = distance
        val street =
            TextView(this).apply {
                text = if (isArrival) step.instruction else step.roadName.ifBlank { step.instruction }
                setTextColor(if (isArrival) textColor else streetColor)
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 2
            }
        texts.addView(distance)
        texts.addView(street)
        row.addView(icon)
        row.addView(texts)
        return row
    }

    /** Rebuilds the lane-guidance row from scratch each call - the lane
     * count varies per junction, so there's no fixed set of views to just
     * update. Valid lanes (the ones that keep you on the route) are drawn
     * bright/blue, the rest dim - same convention Waze/Google Maps use. */
    private fun updateLaneGuidance(lanes: List<LaneInfo>?) {
        if (lanes.isNullOrEmpty()) {
            laneContainer.visibility = View.GONE
            return
        }
        laneContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (34 * density).toInt()
        val margin = (4 * density).toInt()
        val iconPadding = (7 * density).toInt()
        val validColor = ContextCompat.getColor(this, R.color.navos_blue)
        // Valid lane: blue pill with a dark arrow in both themes.
        val validTint = ContextCompat.getColor(this, R.color.navos_navy)
        // Other lanes: the theme's text colour, faded, so they read on
        // both the light and the dark card.
        val textColor = ContextCompat.getColor(this, R.color.navos_text)
        val dimBackground = ColorStateList.valueOf(ColorUtils.setAlphaComponent(textColor, 0x1F))
        val dimTint = ColorUtils.setAlphaComponent(textColor, 0x66)

        lanes.forEach { lane ->
            val indication = lane.indications.firstOrNull() ?: "straight"
            val icon =
                ImageView(this).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(size, size).apply {
                            marginStart = margin
                            marginEnd = margin
                        }
                    setBackgroundResource(R.drawable.bg_lane_pill)
                    backgroundTintList = if (lane.valid) ColorStateList.valueOf(validColor) else dimBackground
                    setImageResource(R.drawable.ic_turn_straight)
                    setColorFilter(if (lane.valid) validTint else dimTint)
                    setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                    rotation = rotationForIndication(indication)
                }
            laneContainer.addView(icon)
        }
        laneContainer.visibility = View.VISIBLE
    }

    private fun rotationForIndication(indication: String): Float =
        when (indication) {
            "sharp right" -> 135f
            "right" -> 90f
            "slight right" -> 45f
            "slight left" -> -45f
            "left" -> -90f
            "sharp left" -> -135f
            "uturn" -> 180f
            else -> 0f // straight / none
        }

    // ---- Location / map plumbing ----------------------------------------

    private fun ensureLocationPermissionThenTrack() {
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            permissionDeniedOverlay.visibility = View.GONE
            startLocationTracking()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** Only reached once permission is confirmed granted - still checks
     * the phone's GPS toggle separately, since that can be off (or get
     * turned off later) independent of app permission, and silently never
     * getting a location fix looked exactly like a frozen app. */
    private fun startLocationTracking() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled =
            try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                false
            }
        if (!gpsEnabled) {
            gpsDisabledOverlay.visibility = View.VISIBLE
            return
        }
        gpsDisabledOverlay.visibility = View.GONE

        if (locationTracker != null) return // already tracking (e.g. a repeat onResume check)

        val tracker =
            LiveLocationTracker(locationManager) { location ->
                onLocationUpdate(location)
            }
        locationTracker = tracker
        tracker.start()
    }

    // Only fires for the Activity's own idle/free-roam tracker - while
    // NavMode.ACTIVE, that tracker is stopped (see beginActiveNavigation())
    // and NavigationService's own GPS subscription drives the drive
    // instead, via navServiceListener.onNavLocation() above. So there's no
    // "are we navigating" branch needed here any more: this function only
    // ever runs for the non-navigating case.
    @SuppressLint("MissingPermission") // permission is confirmed before startLocationTracking() runs
    private fun onLocationUpdate(location: Location) {
        lastKnownLocation = location
        val bearing =
            if (location.hasBearing() && (!location.hasSpeed() || location.speed > 1f)) {
                location.bearing.toDouble()
            } else {
                Double.NaN // standing still: keep the arrow's last heading
            }

        if (!hasCenteredOnFirstFix) {
            hasCenteredOnFirstFix = true
            driveFollower.holdCamera(650L)
            driveFollower.pushFix(location.latitude, location.longitude, bearing, Double.NaN)
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
        } else {
            // Arrow and (when following) the camera glide together, driven
            // by driveFollower - no separate camera ease here any more.
            driveFollower.pushFix(location.latitude, location.longitude, bearing, Double.NaN)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        // A drive that was going while this Activity was stopped is still
        // running in NavigationService - rebind to it (no route to pass,
        // it already has one) so the UI starts mirroring it again instead
        // of showing a stale or blank drive card.
        if (navMode == NavMode.ACTIVE && !navServiceBound) {
            bindService(Intent(this, NavigationService::class.java), navServiceConnection, Context.BIND_AUTO_CREATE)
            navServiceBound = true
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Covers coming back from the "Open location settings" button -
        // re-check rather than leave the GPS-disabled overlay stuck up
        // after the driver actually turned GPS back on. Only meaningful
        // outside active navigation - during ACTIVE, NavigationService
        // owns location, not this Activity's own tracker.
        if (navMode != NavMode.ACTIVE &&
            ::gpsDisabledOverlay.isInitialized &&
            gpsDisabledOverlay.visibility == View.VISIBLE
        ) {
            startLocationTracking()
        }
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        // Only unbinds NavigationService (if a drive is active) - it keeps
        // running as a started foreground service regardless. The idle
        // tracker, on the other hand, really does stop here: there's
        // nothing to keep tracking for once this Activity isn't visible
        // and no drive is active (requirement: no background location
        // tracking without active navigation).
        unbindNavigationService()
        locationTracker?.stop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        driveFollower.stop()
        mapView.onDestroy()
        searchExecutor.shutdownNow()
        extrasExecutor.shutdownNow()
        placeSearchExecutor.shutdownNow()
        poiExecutor.shutdownNow()
        liveSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        unbindNavigationService()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
