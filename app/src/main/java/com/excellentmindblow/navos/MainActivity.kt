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
import android.graphics.PointF
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
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiCache
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.poi.PoiService
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.routing.RoadLimit
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoadPoiService
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutePrefs
import com.excellentmindblow.navos.routing.RouteStep
import com.excellentmindblow.navos.routing.RouteStop
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.routing.SpeedLimitService
import com.excellentmindblow.navos.routing.TripPlanner
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import com.excellentmindblow.navos.search.IncomingLocation
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
import org.maplibre.android.style.layers.FillLayer
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
import org.maplibre.geojson.Polygon
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
private const val ROUTE_CHEVRON_LAYER_ID = "navos-route-chevron-layer"
private const val ROUTE_CHEVRON_ICON_ID = "navos-route-chevron-icon"
private const val ROUTE_PASSED_SOURCE_ID = "navos-route-passed-source"
private const val ROUTE_PASSED_LAYER_ID = "navos-route-passed-layer"
private const val PASSED_TAIL_POINTS = 150 // how much of the driven route stays visible (faded) behind the arrow
private const val ALT_ROUTE_SOURCE_ID = "navos-alt-route-source"
private const val ALT_ROUTE_LAYER_ID = "navos-alt-route-layer"
private const val ROUTE_INDEX_PROPERTY = "routeIndex"

private const val DESTINATION_SOURCE_ID = "navos-destination-source"
private const val DESTINATION_LAYER_ID = "navos-destination-layer"
private const val DESTINATION_ICON_ID = "navos-destination-icon"

// Numbered pins for the intermediate stops of a multi-stop trip.
private const val STOPS_SOURCE_ID = "navos-stops-source"
private const val STOPS_LAYER_ID = "navos-stops-layer"
private const val STOP_ICON_PREFIX = "navos-stop-"
private const val STOP_ICON_COUNT = 9

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
private const val CAMERA_ZONE_SOURCE_ID = "navos-camera-zone-source"
private const val CAMERA_ZONE_FILL_ID = "navos-camera-zone-fill"
private const val CAMERA_ZONE_LINE_ID = "navos-camera-zone-line"
private const val ZONE_RADIUS_M = 200.0 // speed-camera alert zone
private const val AREA_FETCH_MIN_MOVE_M = 2500.0 // idle map: refetch after the view moves this far
private const val POI_ICON_TOLL = "navos-poi-toll"
private const val POI_ICON_CAMERA = "navos-poi-camera"
private const val MAX_ROUTE_OPTIONS = 5

// Smart zoom: pull in as a turn / U-turn / roundabout gets close, ease back
// out on straights, and open up a little at highway speed.
private const val NAV_ZOOM_SLOW_BONUS = 0.6 // crawling / stopped: come in closer
private const val NAV_ZOOM_FAST_PENALTY = 1.0 // 110 km/h and up: open the view

// Position-button / nav-follow camera tuning. Two different ease
// durations on purpose: a short one for the continuous per-GPS-fix follow
// (so it reads as smooth tracking, not a fresh animation firing every
// second) and a longer one only for the deliberate "snap into place"
// moment - the Position button tap or entering active navigation.
private const val NAV_FOLLOW_EASE_MS = 350
private const val SNAP_MAX_METERS = 35.0 // beyond this the fix isn't "on" the route
private const val SNAP_LOOKAHEAD_SEGMENTS = 150
private const val SNAP_LOOKBACK_SEGMENTS = 5
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

/** Posted speed limits: download the roads around the driver again once they
 * have moved this far from the last download (the download covers 1.5 km)... */
private const val SPEED_REFETCH_DISTANCE_M = 700f

/** ...but never more often than this, and a failed download waits this long too. */
private const val SPEED_MIN_REFETCH_MS = 20_000L

/** Speeding alert tolerance, so GPS noise doesn't flash the badge red. */
private const val SPEEDING_MARGIN_KMH = 3.0

/** Where the app currently is in the search -> preview -> drive flow. */
private enum class NavMode { IDLE, PREVIEW, ACTIVE }

/** Which state the free-roam home bottom sheet is in. COLLAPSED shows just
 * the search pill + settings icon; SEARCH and SETTINGS both expand the same
 * card in place, with a back arrow to return to COLLAPSED. */
private enum class HomeSheetState { COLLAPSED, SEARCH, SETTINGS }

/** What picking a place in the search sheet should do: replace the destination
 * (NONE, the normal case) or add it as a stop to the trip being previewed /
 * driven. */
private enum class AddStopTarget { NONE, PREVIEW, ACTIVE }

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

    // ---- Multi-stop trip state ----
    /** Every place of the trip in driving order; the LAST one is the
     * destination, anything before it is a stop. One entry = ordinary A -> B. */
    private var tripPlaces: MutableList<GeocodeResult> = mutableListOf()

    /** The places the route on screen was calculated for - what the list goes
     * back to if an edit can't be routed. */
    private var tripRoutedPlaces: List<GeocodeResult> = emptyList()
    private var tripRequestSeq = 0
    private var addStopTarget = AddStopTarget.NONE
    private var stopsSource: GeoJsonSource? = null

    /** Stops of the current drive already reached (their pins are dropped). */
    private val passedStopNumbers = mutableSetOf<Int>()
    private lateinit var tripStopsPanel: LinearLayout
    private lateinit var addStopButton: TextView
    private lateinit var nextStopText: TextView
    private lateinit var addStopDriveButton: TextView
    private lateinit var skipStopDriveButton: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var speedBadge: TextView
    private lateinit var recenterButton: View
    private lateinit var nextTurnsSheet: View
    private lateinit var nextTurnsList: LinearLayout

    /** Step the Next-turns list was last built for, and its first-row
     * distance label (updated every GPS fix without rebuilding the list). */
    private var nextTurnsRenderedIndex = -1
    private var nextTurnsFirstDistance: TextView? = null
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

    // "Tap any spot on the map to route there": the dropped pin reuses the
    // POI card. droppedPinResult is the destination (its name is filled in
    // by a reverse lookup a moment after the pin lands).
    private var droppedPin: LatLng? = null
    private var droppedPinResult: GeocodeResult? = null
    private var pinSeq = 0
    private lateinit var poiFootnote: View
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

    /** The route being driven as a distance-indexed track (rebuilt when the
     * route changes, e.g. after a reroute). */
    private var routeTrack: RouteTrack? = null

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
        ).also {
            it.driveTilt = NAV_TILT_DEGREES
            it.onFrame = { updateArrowOverlay() }
        }
    private var smoothedNavZoom: Double? = null
    private var lastNavState: NavigationState? = null

    private var routeSource: GeoJsonSource? = null
    private var routePassedSource: GeoJsonSource? = null

    /** Where the previous GPS fix landed on the route. The blue line is
     * trimmed to HERE (not to the newest fix), so the faded "already
     * driven" part never runs ahead of the arrow while it glides. */
    private var lastTrimSnap: GeoUtils.RouteSnap? = null
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
                checkCameraZone(location)
                val currentKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                speedBadge.text = "${Units.speed(currentKmh)}\n${Units.speedUnit}"
                updateSpeedLimit(location, currentKmh)
            }

            override fun onNavState(state: NavigationState) {
                onNavigationState(state)
            }

            override fun onNavigationEnded() {
                if (navMode == NavMode.ACTIVE) endActiveNavigation()
            }

            override fun onRerouteFailed() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastRerouteFailToastMs < 10_000L) return
                lastRerouteFailToastMs = now
                Toast.makeText(this@MainActivity, "Can't find a new route - check your connection", Toast.LENGTH_SHORT).show()
            }

            override fun onStopReached(stop: RouteStop) {
                passedStopNumbers.add(stop.number)
                activeRoute?.let { renderStopMarkers(it.stops) }
                val label = stop.name.ifBlank { "stop ${stop.number}" }
                Toast.makeText(this@MainActivity, "Arrived at $label", Toast.LENGTH_SHORT).show()
            }

            override fun onStopChangeFailed() {
                Toast.makeText(
                    this@MainActivity,
                    "Couldn't change the stops right now - check your GPS and connection",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onRerouted(route: Route) {
                activeRoute = route
                snapSegmentIndex = 0
                routeOptions = listOf(route)
                selectedRouteIndex = 0
                redrawRouteLines()
                setRecalculating(false)
                activeRoutePois = emptyList()
                cameraRouteIndex.clear()
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

    // Posted speed limit (OpenStreetMap maxspeed) for the road being driven.
    private val speedExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var speedLimitSign: TextView
    private var speedRoads: List<RoadLimit> = emptyList()
    private var speedRoadsCenter: Location? = null
    private var speedFetchInFlight = false
    private var lastSpeedFetchAtMs = 0L
    private var lastRerouteFailToastMs = 0L
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
        MapDisplayPrefs.load(prefs)
        driveFollower.driveTilt = if (MapDisplayPrefs.view3d) NAV_TILT_DEGREES else 0.0
        applyAppNightMode()

        super.onCreate(savedInstanceState)
        uiDarkApplied = isNightConfig(resources.configuration)
        // Opened from another app (shared location / maps link)? Handled once the map is ready.
        if (savedInstanceState == null) pendingIncomingIntent = intent

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
        setUpArrowOverlay()

        mapView.getMapAsync { loadedMap ->
            map = loadedMap

            // Our own always-visible compass (the built-in MapLibre one hides
            // itself facing north and was getting clipped). Rotates with the
            // map; tap to turn the map back to north.
            loadedMap.uiSettings.isCompassEnabled = false
            // No MapLibre logo and no (i) attribution button on the map.
            loadedMap.uiSettings.isLogoEnabled = false
            loadedMap.uiSettings.isAttributionEnabled = false
            val compass = findViewById<CompassView>(R.id.compassButton)
            loadedMap.addOnCameraMoveListener {
                compass.northAngle = (-loadedMap.cameraPosition.bearing).toFloat()
                updateArrowOverlay()
            }
            // Two views, like other navigation apps: tap to switch between
            // "north up" and "heading up" (map turns to the way you're going).
            compass.setOnClickListener {
                if (navMode == NavMode.ACTIVE) {
                    driveFollower.northUp = !driveFollower.northUp
                    recenterCamera() // resumes following, easing to the new view
                } else if (driveFollower.idleHeadingUp) {
                    driveFollower.idleHeadingUp = false
                    driveFollower.holdCamera(450L)
                    loadedMap.easeCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                } else {
                    driveFollower.idleHeadingUp = true
                    recenterCamera()
                }
                refreshCompassMode()
            }

            loadedMap.addOnMapClickListener { latLng -> onMapTapped(loadedMap, latLng) }
            // Long-press also drops a pin (same as Google Maps / Waze).
            loadedMap.addOnMapLongClickListener { latLng ->
                if (navMode == NavMode.IDLE && homeSheetState == HomeSheetState.COLLAPSED) {
                    dropPin(latLng)
                    true
                } else {
                    false
                }
            }
            loadedMap.addOnCameraIdleListener { onMapIdle() }

            // A manual pan/rotate/tilt means the driver wants to look
            // elsewhere - stop auto-recentering (in whichever mode is
            // actually active) and offer a way back via the Position
            // button, same affordance Waze/Google Maps both show once
            // you've panned away. Only during PREVIEW is a manual gesture
            // left alone entirely - that screen has no camera-follow of
            // its own to pause.
            // Pinch-zoom while following: zoom AROUND THE ARROW (not around
            // the fingers) so the arrow stays exactly where it is on screen,
            // and keep following afterwards. Panning/rotating still stops
            // the follow as before.
            loadedMap.addOnScaleListener(
                object : MapLibreMap.OnScaleListener {
                    override fun onScaleBegin(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                        val following =
                            (navMode == NavMode.ACTIVE && navFollowing) ||
                                (navMode == NavMode.IDLE && followingUser)
                        if (following && driveFollower.hasPosition()) {
                            val pt = loadedMap.projection.toScreenLocation(LatLng(driveFollower.arrowLat, driveFollower.arrowLng))
                            loadedMap.uiSettings.focalPoint = PointF(pt.x, pt.y)
                            pinchKeepsFollow = true
                        } else {
                            pinchKeepsFollow = false
                        }
                        driveFollower.pinching = true
                    }

                    override fun onScale(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {}

                    override fun onScaleEnd(detector: org.maplibre.android.gestures.StandardScaleGestureDetector) {
                        loadedMap.uiSettings.focalPoint = null
                        driveFollower.onPinchEnd(keepOffset = pinchKeepsFollow && navMode == NavMode.ACTIVE)
                        pinchKeepsFollow = false
                        updateArrowOverlay()
                    }
                },
            )

            loadedMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    // A pinch while following keeps following (decided one
                    // message later, so it does not matter whether the map
                    // reports the gesture start or the pinch start first).
                    mainHandler.post {
                        if (!pinchKeepsFollow) stopFollowingForGesture()
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

    /** True while a pinch started during follow mode: that pinch zooms
     * around the arrow and must not switch following off. */
    private var pinchKeepsFollow = false

    /** A pan / rotate / tilt means the person wants to look elsewhere:
     * stop auto-recentering and show the Recenter button. */
    private fun stopFollowingForGesture() {
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
        refreshCompassMode()
    }

    /** The driver's arrow. It is an ordinary view laid over the map, not a
     * map layer: a layer is refreshed asynchronously (a few frames late)
     * while the camera moves instantly, so a layer arrow always trailed the
     * camera and slid off the blue line. Here the view is put exactly where
     * the map's own projection says the arrow's position is, in the same
     * frame the camera moved, so it cannot drift. */
    private lateinit var arrowView: ImageView
    private var arrowSizePx = 0

    private fun setUpArrowOverlay() {
        val bitmap = PuckIcon.create(this)
        arrowSizePx = bitmap.width
        arrowView =
            ImageView(this).apply {
                setImageBitmap(bitmap)
                visibility = View.INVISIBLE
                isClickable = false
                isFocusable = false
            }
        val parent = mapView.parent as ViewGroup
        // Right above the map, below every other control and sheet.
        parent.addView(arrowView, parent.indexOfChild(mapView) + 1, ViewGroup.LayoutParams(arrowSizePx, arrowSizePx))
    }

    private fun updateArrowOverlay() {
        val m = map ?: return
        if (!::arrowView.isInitialized || !driveFollower.hasPosition()) return
        val pt = m.projection.toScreenLocation(LatLng(driveFollower.arrowLat, driveFollower.arrowLng))
        val cam = m.cameraPosition

        // The lane's direction on screen. A ground direction at angle d from
        // the camera heading is foreshortened along the view axis by
        // cos(tilt), so on a tilted map it leans further from vertical than
        // d itself - this keeps the arrow lying along the blue line.
        var d = (driveFollower.arrowHeading - cam.bearing) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        val dr = Math.toRadians(d)
        val screenAngle = Math.toDegrees(Math.atan2(Math.sin(dr), Math.cos(dr) * Math.cos(Math.toRadians(cam.tilt))))

        arrowView.x = mapView.x + pt.x - arrowSizePx / 2f
        arrowView.y = mapView.y + pt.y - arrowSizePx / 2f
        arrowView.rotation = screenAngle.toFloat()
        if (arrowView.visibility != View.VISIBLE) arrowView.visibility = View.VISIBLE
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
        Units.load(prefs)
        RoutePrefs.load(prefs)
        findViewById<View>(R.id.navPrefsRow).setOnClickListener { showNavPreferencesDialog() }
        findViewById<Switch>(R.id.voiceGuidanceSwitch).apply {
            isChecked = prefs.getBoolean(KEY_VOICE_ENABLED, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(KEY_VOICE_ENABLED, isChecked).apply()
                navigationService?.setVoiceEnabled(isChecked)
            }
        }

        findViewById<View>(R.id.navMapDisplayRow).setOnClickListener { showMapDisplayDialog() }
    }

    // ---- Settings > Map display (like Waze's Display & map) ------------------

    private fun onMapStyleModeChosen(mode: String) {
        if (mode == mapStyleMode()) return
        prefs.edit().putString(KEY_MAP_STYLE_MODE, mode).apply()
        // Re-theme the whole UI too (see onConfigurationChanged).
        applyAppNightMode()
        val loadedMap = map ?: return
        // e.g. Follow system -> Light while the phone is already light:
        // same map either way, so don't reload the style for nothing.
        if (wantsDarkMap() == appliedMapDark) return
        applyMapStyle(loadedMap, mapStyleUrl(), isRestyle = true)
    }

    /** Shows / hides the map layers that Map display controls. */
    private fun applyMapDisplayLayers() {
        val style = map?.style ?: return
        style.getLayer("navos-3d-buildings")
            ?.setProperties(PropertyFactory.visibility(if (MapDisplayPrefs.buildings3d) Property.VISIBLE else Property.NONE))
        style.getLayer(ROUTE_CHEVRON_LAYER_ID)
            ?.setProperties(PropertyFactory.visibility(if (MapDisplayPrefs.routeArrows) Property.VISIBLE else Property.NONE))
    }

    private fun showMapDisplayDialog() {
        val textColor = getColor(R.color.navos_text)
        val hintColor = getColor(R.color.navos_hint)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(8))
            }

        fun heading(label: String) =
            TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                setPadding(0, dp(14), 0, dp(4))
            }

        fun note(label: String) =
            TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(hintColor)
                setPadding(0, dp(2), 0, dp(4))
            }

        fun radio(
            group: RadioGroup,
            label: String,
        ): RadioButton {
            val button = RadioButton(this).apply { id = View.generateViewId(); text = label; setTextColor(textColor) }
            group.addView(button)
            return button
        }

        fun switchRow(
            label: String,
            checked: Boolean,
            onChange: (Boolean) -> Unit,
        ): View =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                addView(
                    TextView(this@MainActivity).apply {
                        text = label
                        textSize = 15f
                        setTextColor(textColor)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    Switch(this@MainActivity).apply {
                        isChecked = checked
                        setOnCheckedChangeListener { _, on -> onChange(on) }
                    },
                )
            }

        // Map colors
        column.addView(heading("Map colors"))
        val colors = RadioGroup(this)
        val rSystem = radio(colors, "Follow system")
        val rLight = radio(colors, "Light (day)")
        val rDark = radio(colors, "Dark (night)")
        colors.check(
            when (mapStyleMode()) {
                MAP_STYLE_DARK -> rDark.id
                MAP_STYLE_LIGHT -> rLight.id
                else -> rSystem.id
            },
        )
        colors.setOnCheckedChangeListener { _, id ->
            onMapStyleModeChosen(
                when (id) {
                    rDark.id -> MAP_STYLE_DARK
                    rLight.id -> MAP_STYLE_LIGHT
                    else -> MAP_STYLE_SYSTEM
                },
            )
        }
        column.addView(colors)

        // Map view
        column.addView(heading("Driving view"))
        val view = RadioGroup(this)
        val r3d = radio(view, "3D - tilted, looking ahead")
        val r2d = radio(view, "2D - flat, top-down")
        view.check(if (MapDisplayPrefs.view3d) r3d.id else r2d.id)
        view.setOnCheckedChangeListener { _, id ->
            MapDisplayPrefs.view3d = id == r3d.id
            MapDisplayPrefs.save(prefs)
            driveFollower.driveTilt = if (MapDisplayPrefs.view3d) NAV_TILT_DEGREES else 0.0
        }
        column.addView(view)
        column.addView(note("Applies the next time you start driving."))

        // Show on map
        column.addView(heading("Show on map"))
        column.addView(
            switchRow("3D buildings", MapDisplayPrefs.buildings3d) {
                MapDisplayPrefs.buildings3d = it
                MapDisplayPrefs.save(prefs)
                applyMapDisplayLayers()
            },
        )
        column.addView(
            switchRow("Route direction arrows", MapDisplayPrefs.routeArrows) {
                MapDisplayPrefs.routeArrows = it
                MapDisplayPrefs.save(prefs)
                applyMapDisplayLayers()
            },
        )
        column.addView(
            switchRow("Petrol stations", showFuelPins) {
                showFuelPins = it
                prefs.edit().putBoolean(KEY_SHOW_FUEL, it).apply()
                onPoiTogglesChanged(if (it) "Petrol stations shown" else "Petrol stations hidden")
            },
        )
        column.addView(
            switchRow("EV charging", showEvPins) {
                showEvPins = it
                prefs.edit().putBoolean(KEY_SHOW_EV, it).apply()
                onPoiTogglesChanged(if (it) "EV charging shown" else "EV charging hidden")
            },
        )

        // While driving
        column.addView(heading("While driving"))
        column.addView(
            switchRow("Speedometer", MapDisplayPrefs.speedometer) {
                MapDisplayPrefs.speedometer = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(
            switchRow("Speed limit sign", MapDisplayPrefs.speedLimit) {
                MapDisplayPrefs.speedLimit = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(
            switchRow("Speed camera alerts", MapDisplayPrefs.cameraAlerts) {
                MapDisplayPrefs.cameraAlerts = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(note("Driving options apply the next time you start driving."))

        val scroll = android.widget.ScrollView(this).apply { addView(column) }
        AlertDialog
            .Builder(this)
            .setTitle("Map display")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .show()
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
        speedLimitSign = findViewById(R.id.speedLimitSign)
        recenterButton = findViewById(R.id.recenterButton)
        nextTurnsSheet = findViewById(R.id.nextTurnsSheet)
        cameraAlert = findViewById(R.id.cameraAlert)
        cameraAlertTitle = findViewById(R.id.cameraAlertTitle)
        cameraAlertSub = findViewById(R.id.cameraAlertSub)
        nextTurnsList = findViewById(R.id.nextTurnsList)
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
        findViewById<View>(R.id.shareEtaButton).setOnClickListener { shareEta() }
        setUpTripUi()
        setUpNavSheetToggle()
        recenterButton.setOnClickListener { recenterCamera() }
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
                    } else if (addStopTarget != AddStopTarget.NONE) {
                        exitSearchFromNav()
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
        if (state != HomeSheetState.COLLAPSED && droppedPin != null) hidePoiCard()
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
        clearSpeedLimit(resetData = false)
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
        addStopTarget = AddStopTarget.NONE
        setHomeSheetState(HomeSheetState.COLLAPSED) // also clears text + hides keyboard
        homeSheet.visibility = View.GONE

        if (navMode == NavMode.ACTIVE) {
            bottomNavBar.visibility = View.VISIBLE
            speedBadge.visibility = if (MapDisplayPrefs.speedometer) View.VISIBLE else View.GONE
            poiToggleColumn.visibility = View.VISIBLE
        } else if (navMode == NavMode.PREVIEW) {
            // Backing out of "Add stop" from the route preview.
            routePreviewCard.visibility = View.VISIBLE
            routeViewToggle.visibility = if (routeOptions.size > 1) View.VISIBLE else View.GONE
        }
    }

    // ---- Speed limit ----------------------------------------------------

    /** Shows the posted limit of the road being driven (round sign) and turns the
     * speed badge red while the driver is over it. Roads with no mapped limit
     * show nothing - a limit is never guessed. */
    private fun updateSpeedLimit(
        location: Location,
        currentKmh: Double,
    ) {
        maybeFetchSpeedLimits(location)
        val limit = SpeedLimitService.limitAt(speedRoads, RoutePoint(location.latitude, location.longitude))
        if (limit == null || !MapDisplayPrefs.speedLimit || navMode != NavMode.ACTIVE || searchOpenedFromNav) {
            speedLimitSign.visibility = View.GONE
            speedBadge.setTextColor(getColor(R.color.navos_text))
            return
        }
        speedLimitSign.text = Units.speed(limit.toDouble()).toString()
        speedLimitSign.visibility = View.VISIBLE
        val speeding = currentKmh > limit + SPEEDING_MARGIN_KMH
        speedBadge.setTextColor(if (speeding) Color.parseColor("#E5484D") else getColor(R.color.navos_text))
    }

    /** Downloads the limited roads around the driver on the first fix of a drive, then
     * again every 700 m - at most every 20 s (Overpass is a free shared server). */
    private fun maybeFetchSpeedLimits(location: Location) {
        if (navMode != NavMode.ACTIVE || speedFetchInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (lastSpeedFetchAtMs != 0L && now - lastSpeedFetchAtMs < SPEED_MIN_REFETCH_MS) return
        val last = speedRoadsCenter
        if (last != null && last.distanceTo(location) < SPEED_REFETCH_DISTANCE_M) return

        speedFetchInFlight = true
        lastSpeedFetchAtMs = now
        val fetchAt = Location(location)
        speedExecutor.execute {
            val roads =
                try {
                    SpeedLimitService.fetchAround(fetchAt.latitude, fetchAt.longitude)
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                speedFetchInFlight = false
                if (isDestroyed || navMode != NavMode.ACTIVE || roads == null) return@post
                speedRoads = roads
                speedRoadsCenter = fetchAt
            }
        }
    }

    private fun clearSpeedLimit(resetData: Boolean) {
        if (::speedLimitSign.isInitialized) speedLimitSign.visibility = View.GONE
        if (::speedBadge.isInitialized) speedBadge.setTextColor(getColor(R.color.navos_text))
        if (resetData) {
            speedRoads = emptyList()
            speedRoadsCenter = null
            lastSpeedFetchAtMs = 0L
        }
    }

    // ---- Share ETA --------------------------------------------------------

    /** Sends a plain-text "on my way, arriving about..." message through the
     * phone's share sheet. It is a snapshot, not live location sharing (that
     * would need a server). */
    private fun shareEta() {
        val state = lastNavState ?: return
        val arrival = Date(System.currentTimeMillis() + (state.durationRemainingSeconds * 1000).toLong())
        val time = SimpleDateFormat("h:mm a", Locale.US).format(arrival)
        val place = destinationName?.takeIf { it.isNotBlank() }
        val text =
            "I'm on my way" + (if (place != null) " to $place" else "") +
                " - arriving around $time (${Units.short(state.distanceRemainingMeters)} to go)."
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        startActivity(Intent.createChooser(send, "Share ETA"))
    }

    // ---- Navigation preferences (units, avoid tolls / highways) ------------

    private fun showNavPreferencesDialog() {
        val textColor = getColor(R.color.navos_text)
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), 0)
            }

        fun heading(label: String) =
            TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                setPadding(0, dp(10), 0, dp(4))
            }

        column.addView(heading("Distance units"))
        val group = RadioGroup(this)
        val kmButton = RadioButton(this).apply { id = View.generateViewId(); text = "Kilometers"; setTextColor(textColor) }
        val miButton = RadioButton(this).apply { id = View.generateViewId(); text = "Miles"; setTextColor(textColor) }
        group.addView(kmButton)
        group.addView(miButton)
        group.check(if (Units.miles) miButton.id else kmButton.id)
        group.setOnCheckedChangeListener { _, checkedId -> Units.save(prefs, checkedId == miButton.id) }
        column.addView(group)

        column.addView(heading("Route preferences"))
        val tolls =
            android.widget.CheckBox(this).apply {
                text = "Avoid tolls"
                isChecked = RoutePrefs.avoidTolls
                setTextColor(textColor)
            }
        val highways =
            android.widget.CheckBox(this).apply {
                text = "Avoid highways"
                isChecked = RoutePrefs.avoidHighways
                setTextColor(textColor)
            }
        tolls.setOnCheckedChangeListener { _, checked -> RoutePrefs.save(prefs, checked, RoutePrefs.avoidHighways) }
        highways.setOnCheckedChangeListener { _, checked -> RoutePrefs.save(prefs, RoutePrefs.avoidTolls, checked) }
        column.addView(tolls)
        column.addView(highways)
        column.addView(
            TextView(this).apply {
                text = "Route preferences apply the next time you search for a route."
                textSize = 12f
                setTextColor(getColor(R.color.navos_hint))
                setPadding(0, dp(6), 0, dp(4))
            },
        )

        AlertDialog
            .Builder(this)
            .setTitle("Navigation preferences")
            .setView(column)
            .setPositiveButton("Done", null)
            .show()
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
        poiFootnote = findViewById(R.id.poiFootnote)

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
            val pin = droppedPinResult
            if (droppedPin != null && pin != null) {
                hidePoiCard()
                onDestinationSelected(pin)
                return@setOnClickListener
            }
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
            if (navMode == NavMode.PREVIEW) {
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
                    PoiCache.loadFuelEv(cacheDir, fetchAt.latitude, fetchAt.longitude)
                        ?: PoiService.fetchNearby(fetchAt.latitude, fetchAt.longitude).also {
                            PoiCache.saveFuelEv(cacheDir, fetchAt.latitude, fetchAt.longitude, it)
                        }
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
            // A dropped pin is moved by the caller, not closed by an empty tap.
            if (poiCard.visibility == View.VISIBLE && droppedPin == null) {
                hidePoiCard()
                return true
            }
            return false
        }
        showPoiCard(poi)
        return true
    }

    private fun showPoiCard(poi: Poi) {
        if (droppedPin != null) {
            droppedPin = null
            droppedPinResult = null
            clearDestinationMarker()
        }
        poiLogoIcon.clearColorFilter()
        poiFootnote.visibility = View.VISIBLE
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
        if (droppedPin != null) {
            droppedPin = null
            droppedPinResult = null
            pinSeq++
            clearDestinationMarker()
        }
        poiCard.visibility = View.GONE
    }

    /** Drops a pin where the person tapped / long-pressed and shows a card
     * with its address and a "Go here" button (like Google Maps / Waze).
     * Tapping elsewhere moves the pin; the X removes it. */
    private fun dropPin(latLng: LatLng) {
        val pinId = ++pinSeq
        droppedPin = latLng
        selectedPoi = null
        droppedPinResult =
            GeocodeResult(
                java.lang.String.format(java.util.Locale.US, "Pin %.5f, %.5f", latLng.latitude, latLng.longitude),
                latLng.latitude,
                latLng.longitude,
                "Dropped pin",
                "",
            )
        showDestinationMarker(latLng)

        poiLogo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1EA3EA"))
        poiLogoIcon.setImageResource(R.drawable.ic_place_pin)
        poiLogoIcon.setColorFilter(Color.WHITE)
        poiName.text = "Dropped pin"
        updatePinMeta(latLng, null)
        poiSpec.visibility = View.GONE
        poiChips.removeAllViews()
        poiFootnote.visibility = View.GONE
        poiCard.visibility = View.VISIBLE

        extrasExecutor.execute {
            val found =
                try {
                    GeocodingService.reverse(latLng.latitude, latLng.longitude)
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                if (isDestroyed || pinId != pinSeq || droppedPin == null || found == null) return@post
                droppedPinResult = found
                poiName.text = found.name.ifBlank { found.label }
                updatePinMeta(latLng, found.address)
            }
        }
    }

    private fun updatePinMeta(
        at: LatLng,
        address: String?,
    ) {
        val here = lastKnownLocation
        val away =
            here?.let {
                "${formatDistance(GeocodingService.distanceMeters(it.latitude, it.longitude, at.latitude, at.longitude))} away"
            }
        poiMeta.text = listOfNotNull(address?.takeIf { it.isNotBlank() }, away).joinToString(" \u00b7 ")
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
        // is cl
