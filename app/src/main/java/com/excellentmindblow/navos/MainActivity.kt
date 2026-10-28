package com.excellentmindblow.navos

import android.Manifest
import android.animation.ObjectAnimator
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
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.excellentmindblow.navos.location.DriveFollower
import com.excellentmindblow.navos.location.HeadingSensor
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.location.PuckIcon
import com.excellentmindblow.navos.navigation.CameraZone
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.navigation.SpeedLimitTracker
import com.excellentmindblow.navos.poi.FuelBrand
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiCache
import com.excellentmindblow.navos.poi.PoiAlongRoute
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.poi.PoiService
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.RoadPoiService
import com.excellentmindblow.navos.navigation.RouteCorners
import com.excellentmindblow.navos.signs.SignColour
import com.excellentmindblow.navos.signs.SignOverlayView
import com.excellentmindblow.navos.signs.SignPlanner
import com.excellentmindblow.navos.traffic.TrafficPrefs
import com.excellentmindblow.navos.traffic.TrafficFlow
import com.excellentmindblow.navos.traffic.DepartureAdvisor
import com.excellentmindblow.navos.traffic.TrafficClient
import com.excellentmindblow.navos.navigation.TripStrip
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutePrefs
import com.excellentmindblow.navos.routing.RouteStep
import com.excellentmindblow.navos.routing.RouteStop
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.routing.TripPlanner
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import com.excellentmindblow.navos.search.PlaceTileStore
import com.excellentmindblow.navos.search.IncomingLocation
import com.excellentmindblow.navos.search.RecentPlaces
import com.excellentmindblow.navos.search.SavedPlaces
import com.excellentmindblow.navos.poi.ParkAndRidePlanner
import com.excellentmindblow.navos.search.SearchPicks
import com.excellentmindblow.navos.traffic.RoadReports
import com.excellentmindblow.navos.search.TripHabits
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
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

// NavOS "Jalan" map styles (tools/mapstyle/build_style.py): Malaysian roads at
// their real width with lane paint, on OpenFreeMap's free, no-API-key tiles.
internal const val MAP_STYLE_URL = "asset://mapstyle/jalan-day.json"
internal const val MAP_STYLE_URL_DARK = "asset://mapstyle/jalan-night.json"

private const val PASSED_TAIL_POINTS = 150 // how much of the driven route stays visible (faded) behind the arrow
private const val ROUTE_INDEX_PROPERTY = "routeIndex"


// Drive camera: 3D tilted view, heading-up, the arrow sitting in the lower
// part of the screen so more road ahead is visible.
private const val NAV_TILT_DEGREES = 55.0

/** Where the Overture place packs are served from ({pack}.json.gz): NavOS's Cloudflare Pages site. */
private const val PLACE_TILES_URL = "https://navos-places.pages.dev/v1"
private const val TILE_PREFETCH_MOVE_M = 5_000f
// Waze-like camera height, the same for the 3D and the 2D driving view:
// about 200 m of road visible ahead at city speed (it used to be ~100 m,
// and ~50 m when stopped, which felt like hugging the road).
private const val NAV_ZOOM = 17.9 // cruising zoom (higher number = camera closer to the ground)
private const val NAV_TOP_PADDING_FRACTION = 0.46 // puts the arrow ~73% down the screen
private const val NAV_ARROW_MIN_FRACTION = 0.35 // an open drive sheet lifts the arrow no higher than this

// Turn callouts on the route line (arrow + road name at every corner).
private const val MAX_TURN_CALLOUTS = 12

// Toll plazas / speed cameras near the idle map, and how many route
// choices to offer (Waze gives 4-5).
private const val AREA_FETCH_MIN_MOVE_M = 2500.0 // idle map: refetch after the view moves this far
private const val MAX_ROUTE_OPTIONS = 5

// Smart zoom: pull in as a turn / U-turn / roundabout gets close, ease back
// out on straights, and open up a little at highway speed.
private const val NAV_ZOOM_SLOW_BONUS = 0.5 // crawling / stopped: come in closer
private const val NAV_ZOOM_FAST_PENALTY = 1.0 // 110 km/h and up: open the view

// Position-button / nav-follow camera tuning. Two different ease
// durations on purpose: a short one for the continuous per-GPS-fix follow
// (so it reads as smooth tracking, not a fresh animation firing every
// second) and a longer one only for the deliberate "snap into place"
// moment - the Position button tap or entering active navigation.
private const val NAV_FOLLOW_EASE_MS = 350

// Home sheet spring: response in seconds (Apple's sheet value), and how hard
// a release has to fling before the settle is allowed a small overshoot.
private const val SHEET_SPRING_RESPONSE_S = 0.3f
private const val SHEET_BOUNCE_FLING_PX_S = 1500f

// Below this tracked speed, a gesture shorter than FLICK_FALLBACK_MAX_MS is
// judged by its average speed instead (see onHomeSheetDragTouch).
private const val FLICK_FALLBACK_MIN_PX_S = 300f
private const val FLICK_FALLBACK_MAX_MS = 500L
private const val SNAP_MAX_METERS = 35.0 // beyond this the fix isn't "on" the route
private const val SNAP_LOOKAHEAD_SEGMENTS = 150
private const val SNAP_LOOKBACK_SEGMENTS = 5
private const val AUTO_RECENTER_DELAY_MS = 8000L // free-look during a drive, then glide back to the arrow
private const val NAV_RECENTER_EASE_MS = 450 // quick, soft glide back to the arrow
private const val IDLE_RECENTER_ZOOM = 16.5

private const val PREFS_NAME = "navos_prefs"
private const val KEY_VOICE_ENABLED = "voice_enabled"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled" // legacy (pre "Follow system")
private const val KEY_MAP_STYLE_MODE = "map_style_mode"
internal const val MAP_STYLE_SYSTEM = "system"
internal const val MAP_STYLE_LIGHT = "light"
internal const val MAP_STYLE_DARK = "dark"

/** Pause after the last keystroke before a live search fires - long
 * enough not to query on every letter, short enough to feel instant. */
private const val LIVE_SEARCH_DEBOUNCE_MS = 120L
private const val MAX_PETROL_RESULTS = 10

// Petrol station / EV charger pins during a drive.
private const val POI_ID_PROPERTY = "poiId"
private const val KEY_SHOW_FUEL = "show_fuel_pins"
private const val KEY_SHOW_EV = "show_ev_pins"
private const val KEY_SHOW_PARKING = "show_parking_pins"
private const val KEY_SHOW_TRANSIT = "show_transit_pins"

/** Park & ride is only worth looking at for trips at least this long. */
private const val PARK_RIDE_MIN_TRIP_M = 8_000.0
private const val FUEL_COLOR = "#E8710A"
private const val EV_COLOR = "#12A150"

/** Look stations up again once the driver has moved this far from the
 * last lookup (keeps the 5 km ring roughly centred on them)... */
private const val POI_REFETCH_DISTANCE_M = 2000f
private const val POI_LOOK_AHEAD_M = 4000.0
private const val FASTER_OFFER_MS = 15_000L
private val EXPRESSWAY_NAME = Regex("(?i)lebuhraya|expressway|highway")
private const val POI_PIN_REFRESH_M = 200.0

/** ...but never more often than this - Overpass is a free shared server,
 * and a failed lookup also waits this long before retrying. */
private const val POI_MIN_REFETCH_MS = 60_000L

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
    private lateinit var routePreviewHint: TextView
    private lateinit var topNavBar: View
    private var turnSignBg = R.drawable.bg_sig_turn
    private lateinit var bottomNavBar: View
    private lateinit var arrivalCard: View
    private var arrivalCardShowing = false
    private lateinit var turnDistanceText: TextView
    private lateinit var turnStreetText: TextView
    private lateinit var recalcPill: View
    private lateinit var routeBackButton: View
    private lateinit var routePreviewSheet: RoutePreviewSheet
    private lateinit var routeListOverlay: View
    private lateinit var routeListItems: LinearLayout
    private lateinit var recalcText: TextView

    /** The one "working on it" pill (recalculating, routes, stops). */
    private lateinit var busy: BusyPill
    private lateinit var etaClockText: TextView
    private lateinit var etaDetailText: TextView
    private lateinit var navStopButton: View
    private var destinationAddress: String? = null

    /** When the current drive started (elapsedRealtime), for the arrival card's drive time. */
    private var driveStartedAtMs = 0L
    private lateinit var routePreviewDestination: TextView
    private lateinit var routePreviewAddress: TextView
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
    private lateinit var turnRefTag: TextView
    private lateinit var nextTurnStreet: TextView
    private lateinit var nextTurnDistance: TextView
    private lateinit var cameraAlertDistance: TextView
    private lateinit var muteButton: ImageButton
    private lateinit var speedBadge: TextView
    private lateinit var recenterButton: View
    private lateinit var nextTurnsSheet: View
    private lateinit var nextTurnsList: LinearLayout

    /** Step the Next-turns list was last built for, and its first-row
     * distance label (updated every GPS fix without rebuilding the list). */
    private var nextTurnsRenderedIndex = -1
    private var nextTurnsFirstDistance: TextView? = null
    private lateinit var homeButton: View
    private lateinit var workButton: View
    private lateinit var homeSearchEmpty: View
    private lateinit var chooseOnMapButton: View
    private lateinit var searchScreen: SearchScreen
    private lateinit var prefs: SharedPreferences
    private lateinit var savedPlaces: SavedPlaces
    private lateinit var settingsScreen: SettingsScreen
    private lateinit var recentPlaces: RecentPlaces

    private val routeOfflineCache by lazy { RouteOfflineCache(this) }

    /** Road reports from drivers: Report button, pins, alerts, "Still there?". */
    private lateinit var roadReportsUi: RoadReportsUi

    private val roadReportsHost =
        object : RoadReportsUi.Host {
            override fun track(): RouteTrack? {
                if (navMode != NavMode.ACTIVE) return null
                val geometry = activeRoute?.geometry?.takeIf { it.size >= 2 } ?: return null
                return routeTrack?.takeIf { it.source === geometry } ?: RouteTrack(geometry).also { routeTrack = it }
            }

            override fun progressM(): Double = driveFollower.progressMeters

            override fun here(): RoutePoint? = lastKnownLocation?.let { RoutePoint(it.latitude, it.longitude) }

            override fun heading(): Double? = lastKnownLocation?.takeIf { it.hasBearing() }?.bearing?.toDouble() ?: smoothedBearing

            override fun announce(
                key: String,
                text: String,
            ) {
                navigationService?.announce(key, text)
            }

            override fun redrawPins() = renderRoutePois()
        }

    /** Learns regular trips (time of day) to suggest the likely one on Home. */
    private lateinit var tripHabits: TripHabits

    /** Places picked from search, so search puts them first next time. */
    private lateinit var searchPicks: SearchPicks
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
    private var dragDownTime = 0L
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
    private var sheetSettleAnimator: SpringAnimation? = null

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
    private lateinit var navPetrolChip: TextView
    private lateinit var navEvChip: TextView
    private lateinit var navParkingChip: TextView
    private lateinit var navTransitChip: TextView
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
    private lateinit var poiAheadRow: View
    private lateinit var poiAheadValue: TextView
    private lateinit var poiAheadLabel: TextView
    private lateinit var poiAheadTime: TextView
    private lateinit var poiSide: TextView
    private lateinit var poiNext: View
    private lateinit var poiNextIcon: ImageView
    private lateinit var poiNextLabel: TextView
    private lateinit var poiNextValue: TextView
    private lateinit var poiGoIcon: View
    private lateinit var poiGoTitle: TextView
    private lateinit var poiGoSub: TextView
    private lateinit var poiGoInsteadButton: TextView
    private var lastPoiFetchLocation: Location? = null
    private var lastPoiFetchAtMs = 0L
    private var poiFetchInFlight = false
    private var showFuelPins = true
    private var showEvPins = true
    private var showParkingPins = true

    /** Car parks near the destination of this drive (see [loadParking]). */
    private var parkingPois: List<Poi> = emptyList()
    private var parkingFor: RoutePoint? = null

    /** Train stations near the destination (see [loadTransit]); off by default. */
    private var showTransitPins = false
    private var transitPois: List<Poi> = emptyList()
    private var transitFor: RoutePoint? = null

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
            it.onFrame = {
                updateArrowOverlay()
                if (::signOverlay.isInitialized) signOverlay.invalidate()
                if (::routeArrows.isInitialized) routeArrows.invalidate()
            }
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
                refreshPoiPinsAlongRoute()
                checkCameraZone(location)
                if (::roadReportsUi.isInitialized) roadReportsUi.onProgress()
                val currentKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                lastSpeedKmh = currentKmh
                updateSpeedLimit(location, currentKmh)
            }

            override fun onNavState(state: NavigationState) {
                onNavigationState(state)
            }

            override fun onNavigationEnded() {
                if (navMode == NavMode.ACTIVE) showArrivalCard()
            }

            override fun onRerouteFailed() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastRerouteFailToastMs < 10_000L) return
                lastRerouteFailToastMs = now
                Toast.makeText(this@MainActivity, tr("Can't find a new route - check your connection", "Tak jumpa laluan baru - cek internet anda"), Toast.LENGTH_SHORT).show()
            }

            override fun onStopReached(stop: RouteStop) {
                passedStopNumbers.add(stop.number)
                activeRoute?.let { renderStopMarkers(it.stops) }
                val label = stop.name.ifBlank { tr("stop ${stop.number}", "hentian ${stop.number}") }
                Toast.makeText(this@MainActivity, tr("Arrived at $label", "Dah sampai $label"), Toast.LENGTH_SHORT).show()
            }

            override fun onFasterRoute(
                route: Route,
                savedSeconds: Double,
            ) {
                if (navMode == NavMode.ACTIVE) showFasterRoute(route, savedSeconds)
            }

            override fun onStopChangeFailed() {
                busy.set(BusyPill.Job.STOP, null)
                Toast.makeText(
                    this@MainActivity,
                    tr("Couldn't change the stops right now - check your GPS and connection", "Tak dapat tukar hentian sekarang - cek GPS dan internet anda"),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            override fun onRerouted(route: Route) {
                busy.set(BusyPill.Job.STOP, null)
                activeRoute = route
                snapSegmentIndex = 0
                routeOptions = listOf(route)
                selectedRouteIndex = 0
                redrawRouteLines()
                setRecalculating(false)
                activeRoutePois = emptyList()
                activeExtras = emptyList()
                cameraRouteIndex.clear()
                renderRoutePois()
                updateRouteSigns(route)
                trafficLayer.start(route)
                renderPoiPins() // stations on the new route only
                loadActiveRoutePois(route)
                runCatching { routeOfflineCache.save(route, mapStyleUrl(), newDrive = false) }
                if (::roadReportsUi.isInitialized) roadReportsUi.start()
                loadParking(route)
                loadTransit(route)
        loadTransit(route)
        loadParking(route)
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

    // Background work (routing, search, POI and speed-limit lookups) reports
    // back through this handler. If the screen was destroyed in the meantime
    // (a configuration change, or the app closing) the result is for a screen
    // that no longer exists and its executors are shut down - acting on it
    // crashed with RejectedExecutionException. Drop such late results.
    /** Live traffic colours on the route while driving (see [TrafficLayer]). */
    private val trafficLayer by lazy { TrafficLayer(mainHandler) { snapSegmentIndex } }

    private val mainHandler =
        object : Handler(Looper.getMainLooper()) {
            override fun dispatchMessage(msg: Message) {
                if (!isDestroyed) super.dispatchMessage(msg)
            }
        }
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
    private val speedLimits =
        SpeedLimitTracker(
            runInBackground = { speedExecutor.execute(it) },
            postToMain = { mainHandler.post(it) },
            clock = SystemClock::elapsedRealtime,
            stillWanted = { !isDestroyed && navMode == NavMode.ACTIVE },
        )
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
        // Overture places for search (the shops OpenStreetMap doesn't have yet).
        if (GeocodingService.placeTiles == null) GeocodingService.placeTiles = PlaceTileStore(this, PLACE_TILES_URL)

        // The Map style setting (Follow system / Light / Dark) drives the
        // WHOLE app's day/night theme, not just the map tiles. Must run
        // before super.onCreate so the first inflate already uses it.
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        savedPlaces = SavedPlaces(prefs)
        settingsScreen =
            SettingsScreen(
                this,
                prefs,
                object : SettingsScreen.Host {
                    override fun currentMapStyleMode() = mapStyleMode()

                    override fun onMapStyleModeChosen(mode: String) = this@MainActivity.onMapStyleModeChosen(mode)

                    override fun onDriveViewChanged() {
                        driveFollower.driveTilt = if (MapDisplayPrefs.view3d) NAV_TILT_DEGREES else 0.0
                    }

                    override fun applyMapDisplayLayers() = this@MainActivity.applyMapDisplayLayers()

                    override fun isPoiKindShown(kind: PoiKind) = this@MainActivity.isPoiKindShown(kind)

                    override fun setPoiKindShown(
                        kind: PoiKind,
                        shown: Boolean,
                    ) = this@MainActivity.setPoiKindShown(kind, shown)

                    override fun isVoiceOn() = prefs.getBoolean(KEY_VOICE_ENABLED, true)

                    override fun setVoiceOn(on: Boolean) = setVoiceGuidance(on)

                    override fun onHelp() = openHelp()

                    override fun onAbout() = showAbout()
                },
            )
        recentPlaces = RecentPlaces(prefs)
        tripHabits = TripHabits(prefs)
        // Toll fares for the route cards; re-render once the table is read.
        com.excellentmindblow.navos.routing.TollFares.load(this) {
            mainHandler.post { if (navMode == NavMode.PREVIEW) updateRoutePreviewSummary() }
        }
        searchPicks = SearchPicks(prefs)
        MapDisplayPrefs.load(prefs)
        // Language (English / Malay) before any text is made.
        Lang.load(prefs)
        Lang.applyToApp(prefs)
        driveFollower.driveTilt = if (MapDisplayPrefs.view3d) NAV_TILT_DEGREES else 0.0
        applyAppNightMode()

        // Must be called before super.onCreate: swaps the splash theme for the app theme.
        installSplashScreen()
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
            signOverlay.map = loadedMap
            routeArrows.map = loadedMap
            loadedMap.addOnCameraMoveListener {
                compass.northAngle = (-loadedMap.cameraPosition.bearing).toFloat()
                updateArrowOverlay()
                signOverlay.invalidate()
                routeArrows.invalidate()
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
                        onUserGestureEnd()
                    }
                },
            )

            // The follow loop moves the camera every frame, so MapLibre never
            // reports the camera as idle and may not deliver
            // REASON_API_GESTURE for a drag. The gesture listeners below fire
            // from the touch itself, so a pan / rotate / tilt always breaks
            // the follow and shows the Re-center button.
            loadedMap.addOnMoveListener(
                object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                        userTouchingMap = true
                        mainHandler.post { if (!pinchKeepsFollow) stopFollowingForGesture() }
                    }

                    override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}

                    override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                        onUserGestureEnd()
                    }
                },
            )
            loadedMap.addOnRotateListener(
                object : MapLibreMap.OnRotateListener {
                    override fun onRotateBegin(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                        userTouchingMap = true
                        mainHandler.post { if (!pinchKeepsFollow) stopFollowingForGesture() }
                    }

                    override fun onRotate(detector: org.maplibre.android.gestures.RotateGestureDetector) {}

                    override fun onRotateEnd(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                        onUserGestureEnd()
                    }
                },
            )
            loadedMap.addOnShoveListener(
                object : MapLibreMap.OnShoveListener {
                    override fun onShoveBegin(detector: org.maplibre.android.gestures.ShoveGestureDetector) {
                        userTouchingMap = true
                        mainHandler.post { if (!pinchKeepsFollow) stopFollowingForGesture() }
                    }

                    override fun onShove(detector: org.maplibre.android.gestures.ShoveGestureDetector) {}

                    override fun onShoveEnd(detector: org.maplibre.android.gestures.ShoveGestureDetector) {
                        onUserGestureEnd()
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
        setUpImeInsetHandling()
        setUpPressFeedback()
        // Just switched language from Settings (that re-creates the screen): stay there.
        if (Lang.takeReopenSettings(prefs)) setHomeSheetState(HomeSheetState.SETTINGS)
    }

    /** Every button-like control shrinks slightly while pressed (see
     * [Motion.addPressFeedback]). Rows in the search / route lists keep
     * their ripple only - a whole list row scaling looks wobbly. */
    private fun setUpPressFeedback() {
        listOf(
            R.id.searchBackButton, R.id.searchClearButton, R.id.compassButton, R.id.recenterButton,
            R.id.navPetrolChip, R.id.navEvChip, R.id.navParkingChip, R.id.navTransitChip, R.id.routeBackButton, R.id.startNavigationButton,
            R.id.previewAddStopButton, R.id.routeAllRoutes,
            R.id.bottomSearchButton, R.id.bottomAltRouteButton, R.id.stopNavigationButton, R.id.shareEtaButton,
            R.id.poiCloseButton, R.id.poiGoButton, R.id.homeSettingsButton, R.id.homeSheetBackButton,
            R.id.homeSearchClearButton, R.id.homeButton, R.id.workButton, R.id.permissionRetryButton,
            R.id.permissionSettingsButton, R.id.gpsSettingsButton, R.id.arrivalConfirmButton,
            R.id.arrivalNotYetButton, R.id.arrivalSaveButton, R.id.exitCancelButton, R.id.exitConfirmButton,
            R.id.homeSearchPill, R.id.homeRecentsClear, R.id.chooseOnMapButton, R.id.filterAll, R.id.filterPlaces,
            R.id.filterAddresses, R.id.filterPetrol, R.id.homePetrolChip, R.id.homeEvChip, R.id.homeShortcutHome,
            R.id.homeShortcutWork, R.id.nextTurnsSwitchRoute, R.id.nextTurnsClose,
        ).forEach { id -> findViewById<View>(id)?.let(Motion::addPressFeedback) }
    }

    /** True while a pinch started during follow mode: that pinch zooms
     * around the arrow and must not switch following off. */
    private var pinchKeepsFollow = false

    /** True while a finger is down on the map (pan / rotate / tilt). */
    private var userTouchingMap = false

    private val autoRecenterRunnable =
        Runnable {
            if (isDestroyed || navMode != NavMode.ACTIVE || navFollowing) return@Runnable
            if (userTouchingMap) {
                scheduleAutoRecenter() // still moving the map: wait again
            } else {
                recenterCamera()
            }
        }

    /** Like Google Maps / Waze: after you free-look during a drive and stop
     * touching the map for a few seconds, the camera glides back to the arrow
     * by itself. The Re-center button still works right away. */
    private fun scheduleAutoRecenter() {
        mainHandler.removeCallbacks(autoRecenterRunnable)
        if (navMode == NavMode.ACTIVE && !navFollowing) {
            mainHandler.postDelayed(autoRecenterRunnable, AUTO_RECENTER_DELAY_MS)
        }
    }

    private fun onUserGestureEnd() {
        userTouchingMap = false
        scheduleAutoRecenter() // restart the idle countdown
    }

    /** A pan / rotate / tilt means the person wants to look elsewhere:
     * stop auto-recentering and show the Recenter button. */
    private fun stopFollowingForGesture() {
        when (navMode) {
            NavMode.ACTIVE -> {
                navFollowing = false
                placeRecenterButton(drivingLayout = true)
                recenterButton.visibility = View.VISIBLE
                scheduleAutoRecenter()
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
        // The 3D road signs stand between the map and the arrow.
        signOverlay = SignOverlayView(this).apply { progress = { driveFollower.progressMeters } }
        parent.addView(signOverlay, parent.indexOfChild(mapView) + 1, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Guide chevrons lie on the line itself, under the signs.
        routeArrows = RouteArrowsView(this).apply { progress = { driveFollower.progressMeters } }
        parent.addView(routeArrows, parent.indexOfChild(mapView) + 1, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Animated guide chevrons through every bend of the route ahead. */
    private lateinit var routeArrows: RouteArrowsView

    /** Road signs and traffic lights standing along the route while driving. */
    private lateinit var signOverlay: SignOverlayView

    /** Traffic lights / R&Rs beside each previewed route, and the active route's. */
    private val routeExtrasByIndex = HashMap<Int, List<RoadPoi>>()
    private var activeExtras: List<RoadPoi> = emptyList()

    /** Where the active route's traffic lights are (metres from its start). */
    private var activeLights: List<Double> = emptyList()

    /** Re-plans the signs for [route] from what is known about it so far. */
    private fun updateRouteSigns(route: Route) {
        val pois = activeRoutePois
        val extras = activeExtras
        signExecutor.execute {
            val signs = SignPlanner.plan(route, pois, extras)
            val lights = SignPlanner.trafficLights(route, extras)
            val corners = if (route.geometry.size >= 2) RouteCorners.find(RouteTrack(route.geometry)) else emptyList()
            Log.i(
                "NavOSSigns",
                "planned ${signs.size} signs (${signs.groupingBy { it.content.javaClass.simpleName }.eachCount()}), " +
                    "${lights.size} traffic lights from ${extras.size} extras / ${pois.size} pois",
            )
            mainHandler.post {
                if (isDestroyed || navMode != NavMode.ACTIVE || activeRoute !== route) return@post
                activeLights = lights
                val track = routeTrack?.takeIf { it.source === route.geometry } ?: RouteTrack(route.geometry).also { routeTrack = it }
                signOverlay.setRoute(track, signs)
                routeArrows.setRoute(track, corners)
                lastNavState?.let { showTripSummary(it.durationRemainingSeconds + trafficLayer.delayAheadSeconds, it.distanceRemainingMeters, etaClockText, etaDetailText) }
            }
        }
    }

    /** " • 8 lights" for the drive bar: traffic lights still ahead, or "" if none / unknown. */
    private fun lightsAheadSuffix(): String {
        if (navMode != NavMode.ACTIVE || activeLights.isEmpty()) return ""
        val here = driveFollower.progressMeters
        if (here.isNaN()) return ""
        val n = SignPlanner.lightsAhead(activeLights, here)
        return if (n <= 0) "" else " • " + resources.getQuantityString(R.plurals.n_lights_short, n, n)
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
        // Search and Settings open to full height, stopping below the status bar.
        val fullHeight = (homeSheetState == HomeSheetState.SEARCH || homeSheetState == HomeSheetState.SETTINGS) && !ownedByDrag
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
        // While typing, the keyboard needs the room for results; "Choose on
        // map" comes back once it closes.
        if (::chooseOnMapButton.isInitialized) chooseOnMapButton.visibility = if (lastImeBottom > 0) View.GONE else View.VISIBLE
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

    /** Laid-out height of the Home content (search row, shortcuts, chips,
     * recents), remembered while it is shown - it is hidden mid-drag. */
    private var homeCollapsedContentHeight = 0

    /** The sheet's height when collapsed: padding + drag handle (24dp) + the
     * Home content, with the search body hidden. */
    private fun sheetCollapsedHeight(): Int =
        homeSheet.paddingTop + dp(24) + homeCollapsedContentHeight.coerceAtLeast(dp(56)) + homeSheetBasePaddingBottom

    private fun setUpSettings() {
        Units.load(prefs)
        Lang.load(prefs)
        RoutePrefs.load(prefs)
        settingsScreen.build(findViewById(R.id.homeSettingsList))
    }

    /** Voice guidance on/off - from the Settings switch or the drive screen's mute button. */
    private fun setVoiceGuidance(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply()
        navigationService?.setVoiceEnabled(enabled)
        if (::settingsScreen.isInitialized) settingsScreen.syncVoice(enabled)
        if (::muteButton.isInitialized) styleMuteButton()
    }

    private fun styleMuteButton() {
        val on = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        muteButton.setImageResource(if (on) R.drawable.ic_volume else R.drawable.ic_volume_off)
        muteButton.contentDescription = getString(if (on) R.string.mute_voice else R.string.unmute_voice)
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

    /** Help & feedback: an email to the team (there's no account / backend yet). */
    private fun openHelp() {
        val intent =
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:excellentmindblow@gmail.com")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "NavOS feedback")
            }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, tr("No email app found", "Tiada app emel"), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Faster route offer ----------------------------------------------------

    private var fasterOffer: Route? = null
    private val fasterTimeout = Runnable { hideFasterRoute() }

    /** "Faster route · saves 6 min · via ..." with Switch / Keep; keeps the current route after 15 s. */
    private fun showFasterRoute(
        route: Route,
        savedSeconds: Double,
    ) {
        fasterOffer = route
        val card = findViewById<View>(R.id.fasterCard)
        val minutes = Math.round(savedSeconds / 60.0).toInt()
        findViewById<TextView>(R.id.fasterTitle).text = tr("Faster route · saves $minutes min", "Laluan lebih cepat · jimat $minutes min")
        val via = NavFormat.viaRoadName(route)
        findViewById<TextView>(R.id.fasterDetail).apply {
            text = via?.let { tr("via $it", "ikut $it") }.orEmpty()
            visibility = if (via == null) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.fasterKeep).apply {
            text = tr("Keep", "Kekal")
            setOnClickListener { hideFasterRoute() }
        }
        findViewById<TextView>(R.id.fasterSwitch).apply {
            text = tr("Switch", "Tukar")
            setOnClickListener {
                val offer = fasterOffer
                hideFasterRoute()
                if (offer != null) navigationService?.acceptFasterRoute(offer)
            }
        }
        Motion.reveal(card, pivotFraction = 0f)
        mainHandler.removeCallbacks(fasterTimeout)
        mainHandler.postDelayed(fasterTimeout, FASTER_OFFER_MS)
    }

    private fun hideFasterRoute() {
        fasterOffer = null
        mainHandler.removeCallbacks(fasterTimeout)
        val card = findViewById<View>(R.id.fasterCard)
        if (card.visibility == View.VISIBLE) Motion.dismiss(card)
    }

    /** First drive only: a short note that live traffic is on (no question, nothing to tap). */
    private fun showTrafficNoticeOnce() {
        val prefs = getSharedPreferences(TrafficPrefs.PREFS_NAME, MODE_PRIVATE)
        if (!TrafficPrefs.sharing(prefs) || !TrafficPrefs.takeFirstNotice(prefs)) return
        Toast.makeText(this, R.string.traffic_notice, Toast.LENGTH_LONG).show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("NavOS")
            .setMessage(
                tr("Version", "Versi") + " ${BuildConfig.VERSION_NAME}\nPowered by Excellent Mindblow\n\n" +
                    tr("Map data", "Data peta") + " \u00A9 OpenStreetMap contributors\n" +
                    tr("Map tiles", "Jubin peta") + ": OpenFreeMap \u00A9 OpenMapTiles\n" +
                    tr("Places", "Tempat") + ": Overture Maps Foundation (Meta, Foursquare, Microsoft, AllThePlaces)\n" +
                    tr("Search", "Carian") + ": Photon by komoot; " + tr("backup", "sandaran") + " Photon by OpenRoad\n" +
                    tr("Icons", "Ikon") + ": Lucide (ISC)",
            ).setPositiveButton("OK", null)
            .show()
    }

    private fun setUpSearch() {
        searchInput = findViewById(R.id.searchInput)
        searchResultsList = findViewById(R.id.searchResultsList)
        homeSearchInput = findViewById(R.id.homeSearchInput)
        homeSearchResultsList = findViewById(R.id.homeSearchResultsList)
        homeSearchInputContainer = findViewById(R.id.homeSearchInputContainer)
        homeRecentsContainer = findViewById(R.id.homeRecentsContainer)
        findViewById<View>(R.id.homeRecentsClear).setOnClickListener {
            recentPlaces.clear()
            renderRecents()
            homeScreen.render()
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
                        if (resultsList === homeSearchResultsList) {
                            activeSearchResultsList = resultsList
                            showSearchResults(emptyList()) // Saved / Recent, or nearby petrol
                        } else {
                            currentResults = emptyList()
                            resultsList.visibility = View.GONE
                        }
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
            val result = (resultsList.adapter as? PlaceRows.Adapter)?.results?.getOrNull(position) ?: return@setOnItemClickListener
            onDestinationSelected(result)
        }
    }

    private fun setUpRouteControls() {
        topBar = findViewById(R.id.topBar)
        routePreviewCard = findViewById(R.id.routePreviewCard)
        routePreviewHint = findViewById(R.id.routePreviewHint)
        topNavBar = findViewById(R.id.topNavBar)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        arrivalCard = findViewById(R.id.arrivalCard)
        findViewById<View>(R.id.arrivalConfirmButton).setOnClickListener {
            Motion.confirm(it)
            hideArrivalCard()
            endActiveNavigation()
        }
        findViewById<View>(R.id.arrivalNotYetButton).setOnClickListener {
            hideArrivalCard()
        }
        navBarRow = findViewById(R.id.navBarRow)
        // Keep the speed badge / report / POI / recenter buttons riding on
        // the drive bar's top edge as it expands or collapses (runs every
        // frame, so it also follows the 220 ms expand animation).
        bottomNavBar.viewTreeObserver.addOnPreDrawListener {
            updateNavFloatingOffset()
            true
        }
        turnDistanceText = findViewById(R.id.turnSignDistance)
        turnStreetText = findViewById(R.id.turnStreetText)
        recalcPill = findViewById(R.id.recalcPill)
        routeBackButton = findViewById(R.id.routeBackButton)
        routeListOverlay = findViewById(R.id.routeListOverlay)
        routeListItems = findViewById(R.id.routeListItems)
        routePreviewSheet =
            RoutePreviewSheet(
                root = findViewById(android.R.id.content),
                onSelect = { index ->
                    if (routeListShowing) setRouteListVisible(false)
                    selectRoute(index)
                },
                onShowAll = { setRouteListVisible(true) },
            )
        recalcText = findViewById(R.id.recalcText)
        busy = BusyPill(recalcPill, recalcText, mainHandler)
        etaClockText = findViewById(R.id.etaClockText)
        etaDetailText = findViewById(R.id.etaDetailText)
        navStopButton = findViewById(R.id.stopNavigationButton)
        routePreviewAddress = findViewById(R.id.routePreviewAddress)
        routePreviewDestination = findViewById(R.id.routePreviewDestination)
        turnIcon = findViewById(R.id.turnSignIcon)
        turnRefTag = findViewById(R.id.turnRefTag)
        nextTurnStreet = findViewById(R.id.nextTurnStreet)
        nextTurnDistance = findViewById(R.id.nextTurnDistance)
        cameraAlertDistance = findViewById(R.id.cameraAlertDistance)
        muteButton = findViewById(R.id.muteButton)
        muteButton.setOnClickListener { setVoiceGuidance(!prefs.getBoolean(KEY_VOICE_ENABLED, true)) }
        styleMuteButton()
        findViewById<View>(R.id.topNavBar).setOnClickListener { openNextTurns() }
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
        homeSheetCollapsedRow.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.visibility == View.VISIBLE && v.height > 0) homeCollapsedContentHeight = v.height
        }
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
            Motion.confirm(it)
            beginActiveNavigation()
        }
        routeBackButton.setOnClickListener {
            if (routeListShowing) setRouteListVisible(false) else cancelRoutePreview()
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
        // End (X): opens the bar to the big End trip button, so a stray tap never ends a drive.
        findViewById<View>(R.id.bottomAltRouteButton).setOnClickListener {
            setNavSheetExpanded(!navSheetExpanded)
        }
        findViewById<View>(R.id.nextTurnsClose).setOnClickListener { closeNextTurns() }
        findViewById<View>(R.id.nextTurnsSwitchRoute).setOnClickListener {
            closeNextTurns()
            showRoutePreview()
        }
        // Back on the idle map asks "Exit NavOS?" instead of closing right away.
        val exitOverlay = findViewById<View>(R.id.exitOverlay)
        findViewById<View>(R.id.exitCancelButton).setOnClickListener { exitOverlay.visibility = View.GONE }
        findViewById<View>(R.id.exitConfirmButton).setOnClickListener { finishAndRemoveTask() }
        exitOverlay.setOnClickListener { exitOverlay.visibility = View.GONE } // tap outside the card = Cancel

        // Back closes the Next-turns sheet first; otherwise behaves as before.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (exitOverlay.visibility == View.VISIBLE) {
                        exitOverlay.visibility = View.GONE
                    } else if (routeListShowing) {
                        setRouteListVisible(false)
                    } else if (addStopTarget != AddStopTarget.NONE) {
                        exitSearchFromNav()
                    } else if (nextTurnsSheet.visibility == View.VISIBLE) {
                        closeNextTurns()
                    } else if (navMode == NavMode.IDLE && homeSheetState == HomeSheetState.COLLAPSED) {
                        exitOverlay.visibility = View.VISIBLE
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
            if (homeSearchInput.text.isNullOrEmpty()) setHomeQuickPicksVisible(true)
            homeSearchInput.requestFocus()
            imm.showSoftInput(homeSearchInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            // Leaving search (back to COLLAPSED, or into SETTINGS) clears
            // whatever was typed - otherwise the next time the sheet opens
            // in search mode it still shows the last destination typed,
            // instead of the empty "Search destination in Malaysia" hint.
            searchScreen.reset()
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
                dragDownTime = event.eventTime
                dragging = false
                dragVelocityTracker?.recycle()
                dragVelocityTracker = VelocityTracker.obtain().also { trackScreenMovement(it, event) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragVelocityTracker?.let { trackScreenMovement(it, event) }
                val deltaY = dragStartRawY - event.rawY // positive = finger moved up
                if (!dragging) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(deltaY) <= slop) return true
                    if (homeSheetState == HomeSheetState.SETTINGS) return false
                    startSheetDrag()
                }
                val collapsed = sheetCollapsedHeight()
                val full = sheetFullHeight()
                val raw = (dragStartSheetHeight + deltaY).toFloat()
                val range = (full - collapsed).toFloat().coerceAtLeast(1f)
                // Past either end the sheet keeps following, with growing
                // resistance, instead of stopping dead under the finger.
                val target =
                    when {
                        raw < collapsed -> collapsed - Motion.rubberband(collapsed - raw, range)
                        raw > full -> full + Motion.rubberband(raw - full, range)
                        else -> raw
                    }
                val maxHeight = (homeSheet.parent as View).height
                applySheetDragHeight(target.toInt().coerceAtMost(maxHeight), collapsed, full)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tracker = dragVelocityTracker
                dragVelocityTracker = null
                // A quick flick on a busy phone can lift off before any move
                // past the touch slop was delivered: it is still a flick,
                // not a tap. Take the sheet over now and judge it below.
                val movedPx = dragStartRawY - event.rawY
                if (!dragging && event.actionMasked == MotionEvent.ACTION_UP &&
                    kotlin.math.abs(movedPx) > ViewConfiguration.get(this).scaledTouchSlop &&
                    homeSheetState != HomeSheetState.SETTINGS
                ) {
                    tracker?.addMovement(event)
                    startSheetDrag()
                }
                if (dragging) {
                    dragging = false
                    tracker?.computeCurrentVelocity(1000)
                    val trackedVelocityY = tracker?.yVelocity ?: 0f // px/s, negative = flinging up
                    tracker?.recycle()
                    // VelocityTracker drops its samples when touch events
                    // arrive more than ~40 ms apart (a device dropping
                    // frames), reporting ~0 for a real flick. For a short,
                    // quick gesture fall back to its average speed.
                    val elapsedMs = (event.eventTime - dragDownTime).coerceAtLeast(1L)
                    val averageVelocityY = (event.rawY - dragStartRawY) * 1000f / elapsedMs
                    // Too few samples can also leave it pointing the wrong way.
                    val trackerUnreliable =
                        kotlin.math.abs(trackedVelocityY) < FLICK_FALLBACK_MIN_PX_S ||
                            (averageVelocityY != 0f && kotlin.math.sign(trackedVelocityY) != kotlin.math.sign(averageVelocityY))
                    val velocityY = if (trackerUnreliable && elapsedMs < FLICK_FALLBACK_MAX_MS) averageVelocityY else trackedVelocityY
                    Log.i(
                        "NavOSSheet",
                        "release tracked=${trackedVelocityY.toInt()} avg=${averageVelocityY.toInt()} used=${velocityY.toInt()} ms=$elapsedMs",
                    )
                    val collapsed = sheetCollapsedHeight()
                    val full = sheetFullHeight()
                    // Snap to whichever end the gesture is heading for (its
                    // projected landing point), not just where it let go - so
                    // a quick short flick is enough to open or close.
                    val projected = homeSheet.height - Motion.project(velocityY)
                    val open = kotlin.math.abs(projected - full) < kotlin.math.abs(projected - collapsed)
                    if (open != (homeSheetState == HomeSheetState.SEARCH)) Motion.snap(homeSheet)
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

    /** Feeds [event] to [tracker] in screen coordinates. The drag handle
     * rides up and down with the finger, so its own (view-relative)
     * coordinates barely change during a drag - tracking those reported
     * almost zero velocity and made every flick behave like a slow drag. */
    private fun trackScreenMovement(
        tracker: VelocityTracker,
        event: MotionEvent,
    ) {
        val screen = MotionEvent.obtain(event)
        screen.setLocation(event.rawX, event.rawY)
        tracker.addMovement(screen)
        screen.recycle()
    }

    /** Sets the sheet to [height] px and fades the body in with it. Below
     * the collapsed height (rubber-banding, or a spring settling past it)
     * the sheet keeps its collapsed size and slides down instead, so the
     * search pill is never clipped. */
    private fun applySheetDragHeight(
        height: Int,
        collapsed: Int = sheetCollapsedHeight(),
        full: Int = sheetFullHeight(),
    ) {
        homeSheet.translationY = (collapsed - height).coerceAtLeast(0).toFloat()
        val laidOutHeight = height.coerceAtLeast(collapsed)
        val params = homeSheet.layoutParams
        if (params.height != laidOutHeight) {
            params.height = laidOutHeight
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
        val to = if (open) full else collapsed
        settlingDrag = true
        sheetSettleAnimator?.cancel()

        val finish = {
            sheetSettleAnimator = null
            settlingDrag = false
            homeSearchBody.alpha = 1f
            setHomeSheetState(if (open) HomeSheetState.SEARCH else HomeSheetState.COLLAPSED)
            // Dragged down mid-drive: treat as "back to navigation".
            if (!open && searchOpenedFromNav) exitSearchFromNav()
        }
        if (!Motion.animationsEnabled()) {
            applySheetDragHeight(to, collapsed, full)
            finish()
            return
        }

        // A spring, not a fixed-duration tween: it starts from wherever the
        // sheet is on screen right now (mid-drag, rubber-banded or mid-
        // settle) and carries the finger's release speed straight on, so
        // there is no seam between dragging and settling. Critically damped
        // by default; a hard flick in the direction of travel earns a little
        // overshoot, because the gesture itself had momentum.
        val heightVelocity = -velocityY // finger up (negative y) = sheet growing
        val flungTowardTarget = kotlin.math.abs(heightVelocity) > SHEET_BOUNCE_FLING_PX_S &&
            kotlin.math.sign(heightVelocity) == kotlin.math.sign((to - homeSheet.height).toFloat())
        val maxHeight = (homeSheet.parent as View).height
        val settle =
            SpringAnimation(FloatValueHolder(homeSheet.height.toFloat())).apply {
                spring =
                    SpringForce(to.toFloat()).apply {
                        dampingRatio = if (flungTowardTarget) 0.85f else SpringForce.DAMPING_RATIO_NO_BOUNCY
                        stiffness = Motion.stiffnessFor(SHEET_SPRING_RESPONSE_S)
                    }
                setStartVelocity(heightVelocity)
                setMinimumVisibleChange(1f)
                addUpdateListener { _, value, _ ->
                    applySheetDragHeight(value.toInt().coerceIn(0, maxHeight), collapsed, full)
                }
                addEndListener { _, canceled, _, _ ->
                    if (!canceled) finish()
                }
            }
        sheetSettleAnimator = settle
        settle.start()
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
            // Backing out of tr("Add stop", "Tambah hentian") from the route preview.
            routePreviewCard.visibility = View.VISIBLE
            routeBackButton.visibility = View.VISIBLE
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
        val here = RoutePoint(location.latitude, location.longitude)
        if (navMode == NavMode.ACTIVE) speedLimits.onLocation(here)
        val limit = speedLimits.limitAt(here)
        if (limit == null || !MapDisplayPrefs.speedLimit || navMode != NavMode.ACTIVE || searchOpenedFromNav) {
            speedLimitSign.visibility = View.GONE
            showSpeed(currentKmh, speeding = false)
            return
        }
        val limitText = Units.speed(limit.toDouble()).toString()
        speedLimitSign.text = limitText
        speedLimitSign.contentDescription = getString(R.string.speed_limit, limitText)
        speedLimitSign.visibility = View.VISIBLE
        showSpeed(currentKmh, speeding = currentKmh > limit + SpeedLimitTracker.SPEEDING_MARGIN_KMH)
    }

    private var lastSpeedKmh = 0.0

    /** The round speed readout: big number (limit red when speeding) over a small unit. */
    private fun showSpeed(
        kmh: Double,
        speeding: Boolean,
    ) {
        val number = Units.speed(kmh).toString()
        val unit = Units.speedUnit
        val text = android.text.SpannableString("$number\n$unit")
        text.setSpan(android.text.style.AbsoluteSizeSpan(34, true), 0, number.length, 0)
        text.setSpan(
            android.text.style.ForegroundColorSpan(getColor(if (speeding) R.color.sig_limit else R.color.sig_text)),
            0,
            number.length,
            0,
        )
        text.setSpan(android.text.style.AbsoluteSizeSpan(12, true), number.length + 1, text.length, 0)
        text.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.sig_text_quiet)), number.length + 1, text.length, 0)
        speedBadge.text = text
        speedBadge.contentDescription = getString(R.string.your_speed, number, unit)
    }

    private fun clearSpeedLimit(resetData: Boolean) {
        if (::speedLimitSign.isInitialized) speedLimitSign.visibility = View.GONE
        if (::speedBadge.isInitialized) showSpeed(lastSpeedKmh, speeding = false)
        if (resetData) speedLimits.reset()
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
            tr("I'm on my way", "Saya dalam perjalanan") + (if (place != null) tr(" to $place", " ke $place") else "") +
                tr(" - arriving around $time (${Units.short(state.distanceRemainingMeters)} to go).", " - sampai lebih kurang $time (lagi ${Units.short(state.distanceRemainingMeters)}).")
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        startActivity(Intent.createChooser(send, tr("Share ETA", "Kongsi ETA")))
    }

    // ---- Petrol / EV charging pins (active drive) ---------------------------

    private fun setUpPoiControls() {
        poiToggleColumn = findViewById(R.id.poiToggleColumn)
        roadReportsUi = RoadReportsUi(this, roadReportsHost)
        navPetrolChip = findViewById(R.id.navPetrolChip)
        navEvChip = findViewById(R.id.navEvChip)
        navParkingChip = findViewById(R.id.navParkingChip)
        navTransitChip = findViewById(R.id.navTransitChip)
        poiCard = findViewById(R.id.poiCard)
        poiLogo = findViewById(R.id.poiLogo)
        poiLogoIcon = findViewById(R.id.poiLogoIcon)
        poiName = findViewById(R.id.poiName)
        poiMeta = findViewById(R.id.poiMeta)
        poiSpec = findViewById(R.id.poiSpec)
        poiChips = findViewById(R.id.poiChips)
        poiFootnote = findViewById(R.id.poiFootnote)
        poiAheadRow = findViewById(R.id.poiAheadRow)
        poiAheadValue = findViewById(R.id.poiAheadValue)
        poiAheadLabel = findViewById(R.id.poiAheadLabel)
        poiAheadTime = findViewById(R.id.poiAheadTime)
        poiSide = findViewById(R.id.poiSide)
        poiNext = findViewById(R.id.poiNext)
        poiNextIcon = findViewById(R.id.poiNextIcon)
        poiNextLabel = findViewById(R.id.poiNextLabel)
        poiNextValue = findViewById(R.id.poiNextValue)
        poiGoIcon = findViewById(R.id.poiGoIcon)
        poiGoTitle = findViewById(R.id.poiGoTitle)
        poiGoSub = findViewById(R.id.poiGoSub)
        poiGoInsteadButton = findViewById(R.id.poiGoInsteadButton)

        showFuelPins = prefs.getBoolean(KEY_SHOW_FUEL, true)
        showEvPins = prefs.getBoolean(KEY_SHOW_EV, true)
        showParkingPins = prefs.getBoolean(KEY_SHOW_PARKING, true)
        showTransitPins = prefs.getBoolean(KEY_SHOW_TRANSIT, false)
        stylePoiToggles()

        navPetrolChip.setOnClickListener { setPoiKindShown(PoiKind.FUEL, !showFuelPins) }
        navEvChip.setOnClickListener { setPoiKindShown(PoiKind.EV, !showEvPins) }
        navParkingChip.setOnClickListener { setPoiKindShown(PoiKind.PARKING, !showParkingPins) }
        navTransitChip.setOnClickListener { setPoiKindShown(PoiKind.TRANSIT, !showTransitPins) }

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
            if (poi.kind == PoiKind.PARKING) {
                onDestinationSelected(GeocodeResult(poi.name, poi.latitude, poi.longitude))
                return@setOnClickListener
            }
            if (poi.kind == PoiKind.TRANSIT) {
                // Park & Ride: drive to the station's car park; else to the station.
                val park = poi.parkAndRide
                onDestinationSelected(
                    if (park != null) {
                        GeocodeResult(tr("Park & Ride ${poi.name}", "Park & Ride ${poi.name}"), park.latitude, park.longitude)
                    } else {
                        GeocodeResult(poi.name, poi.latitude, poi.longitude)
                    },
                )
                return@setOnClickListener
            }
            if (navMode == NavMode.ACTIVE && navigationService != null) {
                // Stop here: the station becomes a stop, the destination stays.
                navigationService?.addStop(RouteStop(RoutePoint(poi.latitude, poi.longitude), poi.name, 0))
                busy.set(BusyPill.Job.STOP, tr("Adding stop…", "Tambah hentian…"))
            } else {
                onDestinationSelected(GeocodeResult(poi.name, poi.latitude, poi.longitude))
            }
        }
        poiGoInsteadButton.setOnClickListener {
            val poi = selectedPoi ?: return@setOnClickListener
            hidePoiCard()
            // Same path as picking a search result: ends this drive and
            // previews a route to the station.
            onDestinationSelected(GeocodeResult(poi.name, poi.latitude, poi.longitude))
        }
    }

    private fun onPoiTogglesChanged(message: String) {
        stylePoiToggles()
        if (::homeScreen.isInitialized) homeScreen.render()
        val selected = selectedPoi
        if (selected != null && !isPoiKindShown(selected.kind)) hidePoiCard()
        renderPoiPins()
        lastKnownLocation?.let { maybeFetchPois(it) }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isPoiKindShown(kind: PoiKind): Boolean =
        when (kind) {
            PoiKind.FUEL -> showFuelPins
            PoiKind.EV -> showEvPins
            PoiKind.PARKING -> showParkingPins
            PoiKind.TRANSIT -> showTransitPins
        }

    /** The petrol / EV toggles on the drive map and in Map display. */
    private fun setPoiKindShown(
        kind: PoiKind,
        shown: Boolean,
    ) {
        when (kind) {
            PoiKind.FUEL -> {
                showFuelPins = shown
                prefs.edit().putBoolean(KEY_SHOW_FUEL, shown).apply()
                onPoiTogglesChanged(if (shown) tr("Petrol stations shown", "Stesen minyak ditunjuk") else tr("Petrol stations hidden", "Stesen minyak disorok"))
            }
            PoiKind.EV -> {
                showEvPins = shown
                prefs.edit().putBoolean(KEY_SHOW_EV, shown).apply()
                onPoiTogglesChanged(if (shown) tr("EV charging shown", "Cas EV ditunjuk") else tr("EV charging hidden", "Cas EV disorok"))
            }
            PoiKind.PARKING -> {
                showParkingPins = shown
                prefs.edit().putBoolean(KEY_SHOW_PARKING, shown).apply()
                onPoiTogglesChanged(
                    if (shown) tr("Parking near your destination shown", "Parking dekat destinasi ditunjuk") else tr("Parking hidden", "Parking disorok"),
                )
                if (shown) activeRoute?.let { loadParking(it) }
            }
            PoiKind.TRANSIT -> {
                showTransitPins = shown
                prefs.edit().putBoolean(KEY_SHOW_TRANSIT, shown).apply()
                onPoiTogglesChanged(
                    if (shown) tr("Train stations near your destination shown", "Stesen tren dekat destinasi ditunjuk") else tr("Train stations hidden", "Stesen tren disorok"),
                )
                if (shown) activeRoute?.let { loadTransit(it) }
            }
        }
    }

    /** The drive sheet's place chips, like the Home ones: on = ink chip with
     * the icon in the text colour; off = plain chip with the icon in its colour. */
    private fun stylePoiToggles() {
        fun style(
            chip: TextView,
            on: Boolean,
            iconRes: Int,
            colorRes: Int,
        ) {
            chip.isSelected = on
            val icon = ContextCompat.getDrawable(this, iconRes)?.mutate() ?: return
            icon.setBounds(0, 0, dp(16), dp(16))
            icon.setTint(ContextCompat.getColor(this, if (on) R.color.sig_on_ink else colorRes))
            chip.setCompoundDrawablesRelative(icon, null, null, null)
        }
        style(navPetrolChip, showFuelPins, R.drawable.ic_fuel, R.color.sig_petrol)
        style(navEvChip, showEvPins, R.drawable.ic_ev, R.color.sig_ev)
        style(navParkingChip, showParkingPins, R.drawable.ic_parking, R.color.sig_flood)
        style(navTransitChip, showTransitPins, R.drawable.ic_train, R.color.sig_text)
    }

    /** Train stations within [PoiService.TRANSIT_RADIUS_M] of [route]'s destination
     * (to park & ride, drop someone off, or walk on), looked up once per destination. */
    private fun loadTransit(route: Route) {
        if (!showTransitPins) return
        val dest = route.geometry.lastOrNull() ?: return
        val last = transitFor
        if (last != null && GeoUtils.distanceMeters(last, dest) < 100.0) return
        transitFor = dest
        transitPois = emptyList()
        poiExecutor.execute {
            val found = runCatching { PoiService.fetchTransit(dest.latitude, dest.longitude) }.getOrNull()
            mainHandler.post {
                if (isDestroyed || navMode != NavMode.ACTIVE || transitFor != dest) return@post
                if (found == null) {
                    transitFor = null
                    return@post
                }
                transitPois = found
                renderPoiPins()
            }
        }
    }

    /** Car parks within [PoiService.PARKING_RADIUS_M] of [route]'s destination,
     * looked up once per destination (where you'll want one, not along the way). */
    private fun loadParking(route: Route) {
        if (!showParkingPins) return
        val dest = route.geometry.lastOrNull() ?: return
        val last = parkingFor
        if (last != null && GeoUtils.distanceMeters(last, dest) < 100.0) return
        parkingFor = dest
        parkingPois = emptyList()
        poiExecutor.execute {
            val found = runCatching { PoiService.fetchParking(dest.latitude, dest.longitude) }.getOrNull()
            mainHandler.post {
                if (isDestroyed || navMode != NavMode.ACTIVE || parkingFor != dest) return@post
                if (found == null) {
                    parkingFor = null // try again on the next reroute / toggle
                    return@post
                }
                parkingPois = found
                renderPoiPins()
            }
        }
    }

    /** Where [poi] sits on the current drive (ahead, off the line), or null
     * when there is no drive or it isn't on the way (see [PoiAlongRoute]). */
    private fun poiSpot(poi: Poi): PoiAlongRoute.Spot? {
        if (navMode != NavMode.ACTIVE) return null
        val route = activeRoute ?: return null
        if (route.geometry.size < 2) return null
        val track = routeTrack?.takeIf { it.source === route.geometry } ?: RouteTrack(route.geometry).also { routeTrack = it }
        return PoiAlongRoute.spot(track, RoutePoint(poi.latitude, poi.longitude), driveFollower.progressMeters, snapSegmentIndex - 5) {
            onExpresswayAt(route, track, it)
        }
    }

    // Which stretches of the active route are expressway: where each step
    // starts along the route, and whether its road is one.
    private var expresswayRoute: Route? = null
    private var expresswayStarts = DoubleArray(0)
    private var expresswayFlags = BooleanArray(0)

    private fun onExpresswayAt(
        route: Route,
        track: RouteTrack,
        progressM: Double,
    ): Boolean {
        if (expresswayRoute !== route) {
            var seg = 0
            val starts = ArrayList<Double>()
            val flags = ArrayList<Boolean>()
            for (step in route.steps) {
                val snap = GeoUtils.snapToPolyline(step.point, route.geometry, seg) ?: continue
                seg = snap.segmentIndex
                starts += track.progressOf(snap)
                flags += SignPlanner.isExpressway(step.ref) || EXPRESSWAY_NAME.containsMatchIn(step.roadName)
            }
            expresswayStarts = starts.toDoubleArray()
            expresswayFlags = flags.toBooleanArray()
            expresswayRoute = route
        }
        var i = expresswayStarts.indexOfLast { it <= progressM }
        if (i < 0) i = 0
        return expresswayFlags.getOrElse(i) { false }
    }

    private var lastPoiPinProgress = Double.NaN

    /** The car moved on a stretch: drop the stations now behind it. */
    private fun refreshPoiPinsAlongRoute() {
        val here = driveFollower.progressMeters
        if (here.isNaN() || (!lastPoiPinProgress.isNaN() && kotlin.math.abs(here - lastPoiPinProgress) < POI_PIN_REFRESH_M)) return
        renderPoiPins()
    }

    /** Petrol / EV pins: only during a drive, and only the stations on the
     * way - near the route line ahead - so the driver can decide to fuel up
     * or carry on. Kinds toggled off stay hidden. */
    private fun renderPoiPins() {
        val src = poiSource ?: return
        lastPoiPinProgress = driveFollower.progressMeters
        val features =
            if (navMode != NavMode.ACTIVE) {
                emptyList()
            } else {
                (
                    nearbyPois.filter { isPoiKindShown(it.kind) && poiSpot(it) != null } +
                        parkingPois.takeIf { showParkingPins }.orEmpty() +
                        transitPois.takeIf { showTransitPins }.orEmpty()
                ).map { poi ->
                    Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).also {
                        it.addStringProperty(
                            POI_ICON_PROPERTY,
                            when (poi.kind) {
                                PoiKind.FUEL -> FuelBrand.of(poi.name)?.pinIconId ?: FUEL_PIN_ICON_ID
                                PoiKind.EV -> EV_PIN_ICON_ID
                                PoiKind.PARKING -> PARKING_PIN_ICON_ID
                                PoiKind.TRANSIT -> TRANSIT_PIN_ICON_ID
                            },
                        )
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
        // Search around a point up the road, so the stations ahead show
        // (about 9 km of the route), not the ones behind.
        val track = routeTrack?.takeIf { t -> activeRoute?.geometry === t.source }
        val here = driveFollower.progressMeters
        val center = if (track != null && !here.isNaN()) track.pointAt(here + POI_LOOK_AHEAD_M) else null
        if (center != null) {
            fetchAt.latitude = center.latitude
            fetchAt.longitude = center.longitude
        }
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
                lastPoiFetchLocation = location
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
        val poi = id?.let { poiId -> (nearbyPois + parkingPois + transitPois).firstOrNull { it.id == poiId } }
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
        resetPoiCardExtras()
        poiLogoIcon.clearColorFilter()
        poiLogoIcon.imageTintList = null
        poiFootnote.visibility = View.VISIBLE
        selectedPoi = poi
        if (poi.kind == PoiKind.PARKING) {
            showParkingCard(poi)
            return
        }
        if (poi.kind == PoiKind.TRANSIT) {
            showTransitCard(poi)
            return
        }
        val isFuel = poi.kind == PoiKind.FUEL
        val brand = if (isFuel) FuelBrand.of(poi.name) else null
        if (brand != null) {
            poiLogo.backgroundTintList = ColorStateList.valueOf(brand.fill)
            poiLogoIcon.setImageBitmap(MapIcons.brandBadge(this, brand, 40))
            poiLogoIcon.contentDescription = brand.displayName
        } else {
            poiLogo.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, if (isFuel) R.color.sig_petrol else R.color.sig_ev))
            poiLogoIcon.setImageResource(if (isFuel) R.drawable.ic_fuel else R.drawable.ic_ev)
            poiLogoIcon.contentDescription = null
        }
        poiName.text = poi.name

        val kindLabel = if (isFuel) tr("Petrol station", "Stesen minyak") else tr("EV charging", "Cas EV")
        val here = lastKnownLocation
        val spot = poiSpot(poi)
        if (spot != null) {
            poiMeta.text = kindLabel
            showPoiOnTheWay(poi, spot)
        } else {
            poiMeta.text =
                if (here != null) {
                    val meters = GeocodingService.distanceMeters(here.latitude, here.longitude, poi.latitude, poi.longitude)
                    tr("$kindLabel · ${NavFormat.formatDistance(meters)} away", "$kindLabel · ${NavFormat.formatDistance(meters)} dari sini")
                } else {
                    kindLabel
                }
        }

        val spec = Labels.spec(poi.spec)
        poiSpec.text = spec ?: ""
        poiSpec.visibility = if (spec.isNullOrBlank()) View.GONE else View.VISIBLE

        // Only what the station has - a row of "not listed" is noise at a glance.
        poiChips.removeAllViews()
        poi.facilities.filter { it.listed }.forEach { facility ->
            poiChips.addView(
                TextView(this).apply {
                    text = Labels.show(facility.label)
                    setTextAppearance(R.style.TextAppearance_Signal_Label)
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.sig_text))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_sig_chip)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), 0, dp(14), 0)
                    minHeight = dp(36)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                },
            )
        }
        poiChips.visibility = if (poiChips.childCount == 0) View.GONE else View.VISIBLE

        Motion.reveal(poiCard, pivotFraction = 1f)
    }

    /** A train station near the destination: its lines (in their colours), the
     * walk, Park & Ride; "Park & ride here" (or "Drive here"). */
    private fun showTransitCard(poi: Poi) {
        poiLogo.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_ink))
        poiLogoIcon.setImageResource(R.drawable.ic_train)
        poiLogoIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_on_ink)) // ink flips at night
        poiLogoIcon.contentDescription = null
        poiName.text = poi.name
        val label = tr("Train station", "Stesen tren")
        val dest = transitFor
        poiMeta.text =
            if (dest != null) {
                val m = GeoUtils.distanceMeters(dest, RoutePoint(poi.latitude, poi.longitude))
                val walk = Math.round(m / 80.0).toInt().coerceAtLeast(1)
                tr("$label · ${NavFormat.formatDistance(m.toFloat())} from your destination ($walk min walk)", "$label · ${NavFormat.formatDistance(m.toFloat())} dari destinasi ($walk min jalan kaki)")
            } else {
                label
            }
        // The lines, full names under the badges.
        val names = poi.lines.map { it.name.substringBefore(":").trim() }.distinct()
        val park = poi.parkAndRide
        val parkLine =
            park?.let {
                if (it.spaces != null) tr("Park & Ride · ${it.spaces} spaces", "Park & Ride · ${it.spaces} petak") else tr("Park & Ride", "Park & Ride")
            }
        val spec = (names + listOfNotNull(parkLine)).joinToString("\n")
        poiSpec.text = spec
        poiSpec.visibility = if (spec.isBlank()) View.GONE else View.VISIBLE
        poiChips.removeAllViews()
        for (line in poi.lines) {
            val fill = line.colour ?: ContextCompat.getColor(this, R.color.sig_ink)
            val ink = if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) > 0.5) Color.BLACK else Color.WHITE
            poiChips.addView(
                TextView(this).apply {
                    text = line.badge
                    setTextAppearance(R.style.TextAppearance_Signal_Label)
                    textSize = 14f
                    setTextColor(ink)
                    background = ContextCompat.getDrawable(context, R.drawable.bg_sig_chip)?.mutate()
                    backgroundTintList = ColorStateList.valueOf(fill)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), 0, dp(14), 0)
                    minHeight = dp(36)
                    contentDescription = line.name
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                },
            )
        }
        poiChips.visibility = if (poiChips.childCount == 0) View.GONE else View.VISIBLE
        poiFootnote.visibility = View.GONE
        poiGoTitle.text = if (park != null) tr("Park & ride here", "Park & ride sini") else tr("Drive here", "Pergi sini")
        Motion.reveal(poiCard, pivotFraction = 1f)
    }

    /** A car park near the destination: how far from it, what it has, "Park here". */
    private fun showParkingCard(poi: Poi) {
        poiLogo.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_flood))
        poiLogoIcon.setImageResource(R.drawable.ic_parking)
        poiLogoIcon.contentDescription = null
        poiName.text = if (poi.name == "Parking") tr("Parking", "Parking") else poi.name
        val dest = parkingFor
        val label = tr("Parking", "Parking")
        poiMeta.text =
            if (dest != null) {
                val m = GeoUtils.distanceMeters(dest, RoutePoint(poi.latitude, poi.longitude))
                val walk = Math.round(m / 80.0).toInt().coerceAtLeast(1) // ~80 m a minute on foot
                tr("$label · ${NavFormat.formatDistance(m.toFloat())} from your destination ($walk min walk)", "$label · ${NavFormat.formatDistance(m.toFloat())} dari destinasi ($walk min jalan kaki)")
            } else {
                label
            }
        val spec = Labels.spec(poi.spec)
        poiSpec.text = spec ?: ""
        poiSpec.visibility = if (spec.isNullOrBlank()) View.GONE else View.VISIBLE
        poiChips.removeAllViews()
        poi.facilities.filter { it.listed }.forEach { facility ->
            poiChips.addView(
                TextView(this).apply {
                    text = Labels.show(facility.label)
                    setTextAppearance(R.style.TextAppearance_Signal_Label)
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.sig_text))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_sig_chip)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), 0, dp(14), 0)
                    minHeight = dp(36)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                },
            )
        }
        poiChips.visibility = if (poiChips.childCount == 0) View.GONE else View.VISIBLE
        poiFootnote.visibility = View.GONE
        // Park here: the car park becomes where the drive ends.
        poiGoTitle.text = tr("Park here", "Parking sini")
        Motion.reveal(poiCard, pivotFraction = 1f)
    }

    /** The parts of the card only a station on the drive has, hidden; "Go here" as the button. */
    private fun resetPoiCardExtras() {
        poiAheadRow.visibility = View.GONE
        poiSide.visibility = View.GONE
        poiNext.visibility = View.GONE
        poiGoInsteadButton.visibility = View.GONE
        poiGoIcon.visibility = View.GONE
        poiGoSub.visibility = View.GONE
        poiGoTitle.text = getString(R.string.go_here)
        poiChips.visibility = View.VISIBLE
    }

    /** "Fuel up here or carry on?": how far ahead, which side, what comes next, Stop here. */
    private fun showPoiOnTheWay(
        poi: Poi,
        spot: PoiAlongRoute.Spot,
    ) {
        val ahead = spot.aheadM.coerceAtLeast(0.0)
        poiAheadRow.visibility = View.VISIBLE
        if (spot.aheadM < 0) {
            poiAheadValue.text = tr("Here", "Sini")
            poiAheadLabel.text = tr("just passed", "baru lepas")
            poiAheadTime.text = ""
        } else {
            poiAheadValue.text = NavFormat.formatDistance(ahead.toFloat())
            poiAheadLabel.text = tr("ahead", "di depan")
            val minutes = Math.round(ahead / averageSpeedAheadMs() / 60.0).toInt().coerceAtLeast(1)
            poiAheadTime.text = tr("about $minutes min", "lebih kurang $minutes min")
        }

        val off = NavFormat.formatDistance(spot.offRouteM.toFloat())
        poiSide.visibility = View.VISIBLE
        if (spot.offRouteM <= PoiAlongRoute.ON_LINE_M) {
            poiSide.text = tr("On your route", "Atas laluan anda")
        } else {
            poiSide.text =
                android.text.SpannableStringBuilder().apply {
                    append(tr("On your side", "Sebelah anda"), android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.sig_success)), 0)
                    append(tr(" · $off off route", " · $off dari laluan"), android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.sig_text_quiet)), 0)
                }
        }
        poiSide.setCompoundDrawablesRelativeWithIntrinsicBounds(
            ContextCompat.getDrawable(this, R.drawable.ic_check)?.mutate()?.apply { setTint(ContextCompat.getColor(this@MainActivity, R.color.sig_success)) },
            null,
            null,
            null,
        )

        // What comes after this one on the route (stations are only known some way ahead).
        val others = nearbyPois.filter { it.kind == poi.kind && it.id != poi.id }.mapNotNull { poiSpot(it)?.aheadM }
        val next = PoiAlongRoute.nextAfter(ahead, others, POI_LOOK_AHEAD_M + PoiService.RADIUS_METERS)
        val isFuel = poi.kind == PoiKind.FUEL
        poiNext.visibility = View.VISIBLE
        poiNextIcon.setImageResource(if (isFuel) R.drawable.ic_fuel else R.drawable.ic_ev)
        val nextAt = next.nextAheadM
        if (nextAt != null) {
            poiNext.backgroundTintList = null
            poiNextIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_text_quiet))
            poiNextLabel.setTextColor(ContextCompat.getColor(this, R.color.sig_text_quiet))
            poiNextValue.setTextColor(ContextCompat.getColor(this, R.color.sig_text))
            poiNextLabel.text = if (isFuel) tr("Next petrol on your route", "Minyak seterusnya di laluan") else tr("Next charger on your route", "Cas EV seterusnya di laluan")
            val nextPoi = nearbyPois.filter { it.kind == poi.kind }.firstOrNull { poiSpot(it)?.aheadM == nextAt }
            val name = nextPoi?.let { FuelBrand.of(it.name)?.displayName ?: it.name }
            val dist = NavFormat.formatDistance(nextAt.toFloat())
            poiNextValue.text = if (name != null) "$name · $dist" else dist
        } else {
            val upTo = Math.round(next.knownUpToM / 1000.0).toInt()
            poiNext.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_caution_fill))
            poiNextIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sig_caution_ink))
            poiNextLabel.setTextColor(ContextCompat.getColor(this, R.color.sig_caution_ink))
            poiNextValue.setTextColor(ContextCompat.getColor(this, R.color.sig_caution_ink))
            poiNextValue.text = tr("Last one in the next $upTo km", "Yang terakhir dalam $upTo km")
            poiNextLabel.text = if (isFuel) tr("No other petrol station on your route", "Tiada stesen minyak lain di laluan") else tr("No other charger on your route", "Tiada cas EV lain di laluan")
        }

        // Stop here (keeps the destination) is the main action mid-drive.
        poiGoTitle.text = tr("Stop here", "Singgah sini")
        poiGoIcon.visibility = View.VISIBLE
        val dest = destinationName?.takeIf { it.isNotBlank() } ?: tripPlaces.lastOrNull()?.name
        if (!dest.isNullOrBlank()) {
            poiGoSub.text = tr("then on to $dest", "lepas tu terus ke $dest")
            poiGoSub.visibility = View.VISIBLE
        }
        poiGoInsteadButton.text = tr("Go here instead", "Pergi sini saja")
        poiGoInsteadButton.visibility = View.VISIBLE
    }

    /** Average speed of the rest of the drive (m/s), for "about N min" - 60 km/h when unknown. */
    private fun averageSpeedAheadMs(): Double {
        val st = lastNavState
        if (st != null && st.durationRemainingSeconds > 30 && st.distanceRemainingMeters > 100) {
            return (st.distanceRemainingMeters / st.durationRemainingSeconds).coerceIn(5.0, 35.0)
        }
        return 60 / 3.6
    }

    private fun hidePoiCard() {
        selectedPoi = null
        if (droppedPin != null) {
            droppedPin = null
            droppedPinResult = null
            pinSeq++
            clearDestinationMarker()
        }
        Motion.dismiss(poiCard)
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
                tr("Dropped pin", "Pin diletak"),
                "",
            )
        showDestinationMarker(latLng)

        poiLogo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1EA3EA"))
        poiLogoIcon.setImageResource(R.drawable.ic_place_pin)
        poiLogoIcon.setColorFilter(Color.WHITE)
        resetPoiCardExtras()
        poiName.text = tr("Dropped pin", "Pin diletak")
        updatePinMeta(latLng, null)
        poiSpec.visibility = View.GONE
        poiChips.removeAllViews()
        poiFootnote.visibility = View.GONE
        Motion.reveal(poiCard, pivotFraction = 1f)

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
                tr("${NavFormat.formatDistance(GeocodingService.distanceMeters(it.latitude, it.longitude, at.latitude, at.longitude))} away", "${NavFormat.formatDistance(GeocodingService.distanceMeters(it.latitude, it.longitude, at.latitude, at.longitude))} dari sini")
            }
        poiMeta.text = listOfNotNull(address?.takeIf { it.isNotBlank() }, away).joinToString(" \u00b7 ")
    }

    // ---- Saved places (Home/Work) -----------------------------------------

    private fun setUpSavedPlaces() {
        homeButton = findViewById(R.id.homeButton)
        workButton = findViewById(R.id.workButton)
        homeSearchEmpty = findViewById(R.id.homeSearchEmpty)
        chooseOnMapButton = findViewById(R.id.chooseOnMapButton)

        val driveTimes =
            HomeEtas(
                runInBackground = { if (!extrasExecutor.isShutdown) extrasExecutor.execute(it) },
                postToMain = { mainHandler.post(it) },
                clock = SystemClock::elapsedRealtime,
                fetchSeconds = { from, to -> RoutingService.calculateRoutes(from, to).first().durationSeconds },
            )
        homeScreen =
            HomeScreen(
                root = homeSheet,
                savedPlaces = savedPlaces,
                recentPlaces = recentPlaces,
                driveTimes = driveTimes,
                habits = tripHabits,
                host =
                    object : HomeScreen.Host {
                        override fun currentLocation(): Location? = lastKnownLocation

                        override fun onShortcutTapped(key: String) = onSavedPlaceTapped(key)

                        override fun onRecentTapped(place: GeocodeResult) = onDestinationSelected(place)

                        override fun isPoiKindShown(kind: PoiKind): Boolean = this@MainActivity.isPoiKindShown(kind)

                        override fun setPoiKindShown(
                            kind: PoiKind,
                            shown: Boolean,
                        ) = this@MainActivity.setPoiKindShown(kind, shown)
                    },
            )
        searchScreen =
            SearchScreen(
                root = homeSheet,
                savedPlaces = savedPlaces,
                driveTimes = driveTimes,
                host =
                    object : SearchScreen.Host {
                        override fun currentLocation(): Location? = lastKnownLocation

                        override fun nearbyPetrol(): List<GeocodeResult> = nearbyPetrolResults()

                        override fun onFilterChanged() = showSearchResults(currentResults)

                        override fun onShortcutTapped(key: String) = onSavedPlaceTapped(key)

                        override fun onChooseOnMap() {
                            setHomeSheetState(HomeSheetState.COLLAPSED)
                            Toast.makeText(this@MainActivity, R.string.choose_on_map_hint, Toast.LENGTH_LONG).show()
                        }
                    },
            )
        refreshSavedPlaceLabels()
    }

    private fun refreshSavedPlaceLabels() {
        homeScreen.render()
        searchScreen.renderSaved()
    }

    /** Petrol stations the map has already loaded, nearest to the driver first. */
    private fun nearbyPetrolResults(): List<GeocodeResult> {
        val here = lastKnownLocation
        return nearbyPois
            .filter { it.kind == PoiKind.FUEL }
            .sortedBy { if (here == null) 0f else GeocodingService.distanceMeters(here.latitude, here.longitude, it.latitude, it.longitude) }
            .take(MAX_PETROL_RESULTS)
            .map { GeocodeResult(it.name, it.latitude, it.longitude, it.name, "", category = "Petrol") }
    }

    private fun savedPlaceLabel(key: String): String? = savedPlaces.get(key)?.label

    private fun onSavedPlaceTapped(key: String) {
        val saved = savedPlaces.get(key)
        val label = saved?.label
        if (saved != null && label != null) {
            onDestinationSelected(GeocodeResult(label, saved.latitude, saved.longitude))
            return
        }

        // Not set yet - same as Waze's "Set once and go": start a search,
        // and save whatever destination gets picked next under this label.
        pendingSavedPlaceLabel = key
        val text =
            if (key == "home") {
                tr("Search your home address - we'll save it", "Cari alamat rumah - nanti kami simpan")
            } else {
                tr("Search your work address - we'll save it", "Cari alamat tempat kerja - nanti kami simpan")
            }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        setHomeSheetState(HomeSheetState.SEARCH)
    }

    private fun maybeSaveAsPendingPlace(result: GeocodeResult) {
        val key = pendingSavedPlaceLabel ?: return
        pendingSavedPlaceLabel = null
        savedPlaces.save(key, result)
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
                    if (!live) Toast.makeText(this, tr("Search failed - check your connection", "Carian gagal - cek internet anda"), Toast.LENGTH_SHORT).show()
                    return@post
                }
                if (!live && results.isEmpty()) {
                    Toast.makeText(this, tr("No places found", "Tak jumpa tempat"), Toast.LENGTH_SHORT).show()
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

    private fun showSearchResults(found: List<GeocodeResult>) {
        val query = activeSearchInput?.text?.toString().orEmpty()
        val results = searchPicks.boost(found, query) // your usual places first
        currentResults = results
        val list = activeSearchResultsList ?: return
        // The home search runs through the filters; Saved / Recent make way
        // for results and come back once there are none (empty box).
        val shown = if (list === homeSearchResultsList) searchScreen.filter(results, query) else results
        if (list === homeSearchResultsList) {
            setHomeQuickPicksVisible(shown.isEmpty())
            if (shown.isEmpty() && searchScreen.filter == SearchScreen.Filter.PETROL) {
                Toast.makeText(this, R.string.no_petrol_nearby, Toast.LENGTH_SHORT).show()
            }
        }
        if (shown.isEmpty()) {
            list.visibility = View.GONE
            return
        }
        list.adapter = PlaceRows.Adapter(shown, lastKnownLocation, query)
        list.visibility = View.VISIBLE
        list.setSelection(0)
    }

    private fun setHomeQuickPicksVisible(visible: Boolean) {
        homeSearchEmpty.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) renderRecents()
    }

    // ---- Search helpers: instant matches + recent searches --------------

    private val searchCache = LinkedHashMap<String, List<GeocodeResult>>()
    private var lastLiveResults: List<GeocodeResult> = emptyList()
    private lateinit var homeRecentsContainer: LinearLayout
    private lateinit var homeScreen: HomeScreen

    /** Matches shown the moment a key is pressed, before the network
     * answers: recent searches first, then the last live results. */
    private fun instantMatches(query: String): List<GeocodeResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return (recentPlaces.load() + lastLiveResults)
            .distinctBy { it.label }
            .filter { it.label.lowercase().contains(needle) }
            .take(8)
    }

    /** Fills the "Recent" block under Home/Work (hidden when empty). */
    private fun renderRecents() {
        val container = homeRecentsContainer
        // Child 0 is the "Recent / Clear" header - everything after it is a row.
        while (container.childCount > 1) container.removeViewAt(1)
        val recents = recentPlaces.load().take(RecentPlaces.MAX_SHOWN)
        if (recents.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        val origin = lastKnownLocation
        for (r in recents) {
            val row = PlaceRows.inflate(container)
            PlaceRows.bind(row, r, origin, iconRes = R.drawable.ic_history)
            row.setOnClickListener { onDestinationSelected(r) }
            container.addView(row)
        }
        container.visibility = View.VISIBLE
    }

    private fun onDestinationSelected(result: GeocodeResult) {
        if (droppedPin != null) hidePoiCard()
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

        // tr("Add stop", "Tambah hentian") search: the pick becomes a stop on the current trip
        // instead of replacing it.
        if (addStopTarget != AddStopTarget.NONE) {
            onStopPicked(result)
            return
        }

        // A new destination picked mid-drive replaces whatever was active.
        if (navMode == NavMode.ACTIVE) endActiveNavigation()
        searchOpenedFromNav = false
        searchBackButton.visibility = View.GONE

        recentPlaces.save(result)
        searchPicks.record(result)
        maybeSaveAsPendingPlace(result)
        homeScreen.render()
        destinationName = result.name
        destinationAddress = result.address

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

        tripPlaces = mutableListOf(result)
        passedStopNumbers.clear()
        fetchTripRoute()
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
        if (::signOverlay.isInitialized) signOverlay.setNight(appliedMapDark == true)
        if (::routeArrows.isInitialized) routeArrows.refreshColor()
        loadedMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            MapLabels.apply(style)
            MapIcons.addRoadShield(this, style)
            val sources = MapLayers(this).install(style)
            altRouteSource = sources.altRoute
            routeSource = sources.route
            routePassedSource = sources.routePassed
            routePoiSource = sources.routePoi
            cameraZoneSource = sources.cameraZone
            turnCalloutSource = sources.turnCallouts
            destinationSource = sources.destination
            stopsSource = sources.stops
            poiSource = sources.poi
            routeTimeSource = sources.routeTimes
            trafficLayer.source = sources.traffic
            renderPoiPins()
            if (isRestyle) {
                redrawRouteLines()
                destinationLatLng?.let { showDestinationMarker(it) }
                renderPoiPins()
                renderRoutePois()
                if (navMode == NavMode.ACTIVE) {
                    renderedCalloutStep = -1
                    renderTurnCallouts(lastNavState?.currentStepIndex ?: 1)
                }
            } else {
                ensureLocationPermissionThenTrack()
                handlePendingIncomingIntent()
            }
        }
    }

    // ---- Opened from another app (geo: link, Maps link, shared text) -------

    private var pendingIncomingIntent: Intent? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIncomingIntent = intent
        if (map?.style != null) handlePendingIncomingIntent()
    }

    private fun handlePendingIncomingIntent() {
        val incoming = pendingIncomingIntent ?: return
        pendingIncomingIntent = null
        val raw = IncomingLocation.rawFrom(incoming) ?: return
        Toast.makeText(this, tr("Opening location\u2026", "Buka lokasi\u2026"), Toast.LENGTH_SHORT).show()
        val bias = lastKnownLocation
        placeSearchExecutor.execute {
            val result: GeocodeResult? =
                try {
                    when (val target = IncomingLocation.resolve(raw)) {
                        is IncomingLocation.Target.Coords -> {
                            val label =
                                target.label
                                    ?: try {
                                        GeocodingService.reverse(target.latitude, target.longitude)?.label
                                    } catch (e: Exception) {
                                        null
                                    }
                                    ?: tr("Shared location", "Lokasi dikongsi")
                            GeocodeResult(label, target.latitude, target.longitude)
                        }
                        is IncomingLocation.Target.Query ->
                            GeocodingService.search(target.text, bias?.latitude, bias?.longitude).firstOrNull()
                        null -> null
                    }
                } catch (e: Exception) {
                    null
                }
            mainHandler.post {
                if (isDestroyed) return@post
                if (result == null) {
                    Toast.makeText(this, tr("Couldn't read that location", "Tak dapat baca lokasi tu"), Toast.LENGTH_LONG).show()
                } else {
                    onDestinationSelected(result)
                }
            }
        }
    }

    private var routePoiSource: GeoJsonSource? = null
    private var routeTimeSource: GeoJsonSource? = null
    private var cameraZoneSource: GeoJsonSource? = null

    /** Toll plazas / cameras around the plain (idle) map view. */
    private var areaRoadPois: List<RoadPoi> = emptyList()
    private var areaFetchInFlight = false
    private var lastAreaFetchCenter: LatLng? = null
    private var idlePoiFetchInFlight = false
    private var lastIdlePoiCenter: LatLng? = null

    private lateinit var cameraAlert: View
    private lateinit var cameraAlertTitle: TextView
    private lateinit var cameraAlertSub: TextView
    private val alertedCameraKeys = HashSet<String>()
    private val cameraRouteIndex = HashMap<String, Int>()
    private val routePoisByIndex = HashMap<Int, List<RoadPoi>>()
    private var activeRoutePois: List<RoadPoi> = emptyList()

    @Volatile private var previewToken = 0
    private var routeListShowing = false
    private val extrasExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Traffic lights / R&Rs lookups: big Overpass queries kept off [extrasExecutor]
     * so they never hold up the toll / camera lookups queued there. */
    private val lightsExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Sign planning: quick, and must never wait behind a network call. */
    private val signExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private fun poiKey(poi: RoadPoi): String = String.format(Locale.US, "%.5f,%.5f", poi.latitude, poi.longitude)

    private fun currentRoadPois(): List<RoadPoi> =
        when (navMode) {
            NavMode.ACTIVE -> activeRoutePois
            NavMode.PREVIEW -> routePoisByIndex[selectedRouteIndex].orEmpty()
            else -> areaRoadPois
        }

    /** 200 m ring around a camera: the alert zone. */
    private fun zoneFeature(poi: RoadPoi): Feature {
        val dLat = CameraZone.RADIUS_M / 111_320.0
        val dLon = CameraZone.RADIUS_M / (111_320.0 * Math.cos(Math.toRadians(poi.latitude)).coerceAtLeast(0.2))
        val ring = ArrayList<Point>()
        for (i in 0..48) {
            val a = 2.0 * Math.PI * i / 48.0
            ring.add(Point.fromLngLat(poi.longitude + dLon * Math.cos(a), poi.latitude + dLat * Math.sin(a)))
        }
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }

    /** Draws the toll plazas / speed cameras (and each camera's 200 m
     * zone) for whatever the map is showing: the route being previewed or
     * driven, or - on the plain map - the ones around the view. */
    private fun renderRoutePois() {
        val src = routePoiSource ?: return
        val pois = currentRoadPois()
        val features =
            pois.map { poi ->
                val f = Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude))
                f.addStringProperty("icon", if (poi.kind == "toll") POI_ICON_TOLL else POI_ICON_CAMERA)
                f.addStringProperty("key", poiKey(poi))
                f
            }
        val reports =
            if (navMode == NavMode.ACTIVE && ::roadReportsUi.isInitialized) {
                roadReportsUi.onRoute.map { r ->
                    val f = Feature.fromGeometry(Point.fromLngLat(r.report.longitude, r.report.latitude))
                    f.addStringProperty("icon", reportIconId(r.report.kind))
                    f.addStringProperty("key", "report-${r.report.id}")
                    f
                }
            } else {
                emptyList()
            }
        src.setGeoJson(FeatureCollection.fromFeatures(features + reports))
        cameraZoneSource?.setGeoJson(FeatureCollection.fromFeatures(pois.filter { it.kind == "camera" }.map { zoneFeature(it) }))
    }

    /** Tap on a toll plaza / camera icon. */
    private fun handleRoadPoiTap(
        map: MapLibreMap,
        latLng: LatLng,
    ): Boolean {
        val pt = map.projection.toScreenLocation(latLng)
        val slop = dp(24).toFloat()
        val hits =
            try {
                map.queryRenderedFeatures(RectF(pt.x - slop, pt.y - slop, pt.x + slop, pt.y + slop), ROUTE_POI_LAYER_ID)
            } catch (e: Exception) {
                emptyList()
            }
        val key = hits.firstNotNullOfOrNull { it.getStringProperty("key") } ?: return false
        if (key.startsWith("report-")) {
            val r = roadReportsUi.reportById(key.removePrefix("report-").toIntOrNull() ?: -1) ?: return false
            Toast.makeText(this, "${r.kind.label} · ${RoadReports.detail(r)}", Toast.LENGTH_LONG).show()
            return true
        }
        val poi = currentRoadPois().firstOrNull { poiKey(it) == key } ?: return false
        if (poi.kind == "toll") showTollInfo(poi) else showCameraInfo(poi)
        return true
    }

    private fun showCameraInfo(poi: RoadPoi) {
        val limit = poi.maxspeed?.filter { it.isDigit() }?.toIntOrNull()
        val text =
            tr("Speed camera - alert zone ${Units.short(CameraZone.RADIUS_M)}", "Kamera laju - zon amaran ${Units.short(CameraZone.RADIUS_M)}") +
                (if (limit != null) tr(", limit ", ", had ") + "${Units.speed(limit.toDouble())} ${Units.speedUnit}" else "")
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /** Tap on a toll plaza: just its name (prices are intentionally not shown). */
    private fun showTollInfo(poi: RoadPoi) {
        Toast.makeText(this, poi.name.ifBlank { tr("Toll plaza", "Plaza tol") }, Toast.LENGTH_SHORT).show()
    }

    // ---- Speed-camera zone (200 m) ------------------------------------


    /** Alerts when the driver ENTERS a camera's 200 m zone on the route:
     * pop-up banner + one spoken warning per camera, with the distance
     * counting down until it's passed. */
    private fun checkCameraZone(location: Location) {
        val geometry = activeRoute?.geometry
        val cameras = activeRoutePois.filter { it.kind == "camera" }
        if (geometry == null || cameras.isEmpty()) {
            hideCameraAlert()
            return
        }
        val approach =
            CameraZone.nearestAhead(
                here = RoutePoint(location.latitude, location.longitude),
                heading = smoothedBearing,
                cameras = cameras,
                passedSegment = snapSegmentIndex,
            ) { cam ->
                cameraRouteIndex.getOrPut(poiKey(cam)) {
                    GeoUtils.snapToPolyline(RoutePoint(cam.latitude, cam.longitude), geometry)?.segmentIndex ?: -1
                }
            }
        if (approach == null || !MapDisplayPrefs.cameraAlerts) {
            hideCameraAlert()
            return
        }
        val cam = approach.camera
        val nearestD = approach.distanceMeters
        val key = poiKey(cam)
        if (alertedCameraKeys.add(key)) navigationService?.announce("camera-$key", tr("Speed camera ahead", "Ada kamera laju di depan"))
        val limit = cam.maxspeed?.filter { it.isDigit() }?.toIntOrNull()
        val kmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
        val distance = Units.short(nearestD.coerceAtLeast(10.0))
        cameraAlertTitle.text = getString(if (limit != null && kmh > limit) R.string.camera_slow_down else R.string.camera_ahead)
        cameraAlertSub.text = if (limit != null) getString(R.string.camera_limit, Units.speed(limit.toDouble()).toString()) else ""
        cameraAlertSub.visibility = if (limit != null) View.VISIBLE else View.GONE
        cameraAlertDistance.text = distance
        Motion.reveal(cameraAlert, pivotFraction = 0f)
    }

    private fun hideCameraAlert() {
        if (::cameraAlert.isInitialized) Motion.dismiss(cameraAlert)
    }

    // ---- Idle map: POIs around the view, instant from the cache ---------

    private fun onMapIdle() {
        val m = map ?: return
        if (navMode != NavMode.IDLE) return
        val cam = m.cameraPosition
        val center = cam.target ?: return
        if (cam.zoom < 9.0) return
        maybeFetchAreaPois(center)
        maybeFetchIdlePois(center)
    }

    private fun movedFar(
        last: LatLng?,
        now: LatLng,
    ): Boolean =
        last == null ||
            GeoUtils.distanceMeters(RoutePoint(last.latitude, last.longitude), RoutePoint(now.latitude, now.longitude)) >= AREA_FETCH_MIN_MOVE_M

    /** Toll plazas + speed cameras around [center]: the saved copy shows
     * immediately; only when there is none (or it is a day old) does the
     * network get asked. */
    private fun maybeFetchAreaPois(center: LatLng) {
        if (!movedFar(lastAreaFetchCenter, center)) return
        val cached = PoiCache.loadRoad(cacheDir, center.latitude, center.longitude)
        if (cached != null) {
            areaRoadPois = cached
            lastAreaFetchCenter = center
            if (navMode == NavMode.IDLE) renderRoutePois()
            return
        }
        if (areaFetchInFlight) return
        areaFetchInFlight = true
        extrasExecutor.execute {
            val found =
                try {
                    RoadPoiService.fetchArea(center.latitude, center.longitude)
                } catch (e: Exception) {
                    null
                }
            if (found != null) PoiCache.saveRoad(cacheDir, center.latitude, center.longitude, found)
            mainHandler.post {
                areaFetchInFlight = false
                if (isDestroyed || found == null) return@post
                areaRoadPois = found
                lastAreaFetchCenter = center
                if (navMode == NavMode.IDLE) renderRoutePois()
            }
        }
    }

    /** Petrol stations + EV chargers around [center] for the plain map. */
    private fun maybeFetchIdlePois(center: LatLng) {
        if (!movedFar(lastIdlePoiCenter, center)) return
        val cached = PoiCache.loadFuelEv(cacheDir, center.latitude, center.longitude)
        if (cached != null) {
            nearbyPois = cached
            lastIdlePoiCenter = center
            renderPoiPins()
            return
        }
        if (idlePoiFetchInFlight) return
        idlePoiFetchInFlight = true
        poiExecutor.execute {
            val pois =
                try {
                    PoiService.fetchNearby(center.latitude, center.longitude)
                } catch (e: Exception) {
                    null
                }
            if (pois != null) PoiCache.saveFuelEv(cacheDir, center.latitude, center.longitude, pois)
            mainHandler.post {
                idlePoiFetchInFlight = false
                if (isDestroyed || pois == null) return@post
                nearbyPois = pois
                lastIdlePoiCenter = center
                renderPoiPins()
            }
        }
    }

    /** Looks up toll plazas / cameras for [routes] in the background. */
    private fun loadRoutePois(
        routes: List<Route>,
        token: Int,
    ) {
        extrasExecutor.execute {
            // Bundled plazas first: the tolls (and their price) show at once,
            // offline, even when the live lookup below times out.
            val bundled =
                if (com.excellentmindblow.navos.routing.BundledTolls.ensureLoaded(this)) {
                    routes.indices.associateWith { com.excellentmindblow.navos.routing.BundledTolls.along(routes[it]) }
                } else {
                    emptyMap()
                }
            if (bundled.isNotEmpty()) {
                mainHandler.post {
                    if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                    for ((i, tolls) in bundled) if (routePoisByIndex[i] == null) routePoisByIndex[i] = tolls
                    rebuildRouteStrips()
                    renderRoutePois()
                    updateRoutePreviewSummary()
                }
            }
            val found =
                try {
                    RoadPoiService.fetchAlong(routes)
                } catch (e: Exception) {
                    emptyMap()
                }
            // The live answer adds cameras; keep the bundled tolls where it found none.
            val merged =
                found.mapValues { (i, pois) ->
                    if (pois.none { it.kind == "toll" }) pois + bundled[i].orEmpty() else pois
                }
            mainHandler.post {
                if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                routePoisByIndex.putAll(merged)
                rebuildRouteStrips()
                renderRoutePois()
                updateRoutePreviewSummary()
                if (routeListShowing) renderRouteList()
            }
        }
        // Live traffic on each route (quick): colours its strip, "Jam on ... +6 min".
        extrasExecutor.execute {
            val notes = HashMap<Int, Pair<List<TripStrip.Traffic>, TrafficNote?>>()
            routes.forEachIndexed { i, r -> previewTraffic(r)?.let { notes[i] = it } }
            val leave = HashMap<Int, DepartureAdvisor.Advice>()
            routes.forEachIndexed { i, r -> leaveAdvice(r)?.let { leave[i] = it } }
            val reports = HashMap<Int, List<RoadReports.OnRoute>>()
            routes.forEachIndexed { i, r -> previewReports(r)?.let { reports[i] = it } }
            mainHandler.post {
                if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                routeTraffic.putAll(notes)
                routeReports.putAll(reports)
                routeLeaveAdvice.putAll(leave)
                rebuildRouteStrips()
                updateRoutePreviewSummary()
                if (routeListShowing) renderRouteList()
            }
        }
        // Traffic lights and R&Rs: a second, bigger lookup on its own thread,
        // so the toll counts above don't wait for it.
        lightsExecutor.execute {
            val extras =
                try {
                    RoadPoiService.fetchExtrasAlong(routes)
                } catch (e: Exception) {
                    Log.w("NavOSSigns", "traffic lights / R&R lookup failed: $e")
                    emptyMap()
                }
            Log.i("NavOSSigns", "preview extras: " + extras.mapValues { it.value.groupingBy { p -> p.kind }.eachCount() })
            val lights = extras.mapValues { (i, list) -> SignPlanner.trafficLights(routes[i], list) }
            mainHandler.post {
                if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                routeExtrasByIndex.putAll(extras)
                routeLightCounts.putAll(lights.mapValues { it.value.size })
                routeLightsM.putAll(lights)
                rebuildRouteStrips()
                updateRoutePreviewSummary()
                if (routeListShowing) renderRouteList()
            }
        }
        // Park & ride: stations near the destination, then Park & Ride car parks along the way.
        val first = routes.firstOrNull()
        if (first != null && first.distanceMeters >= PARK_RIDE_MIN_TRIP_M) {
            lightsExecutor.execute {
                val dest = first.geometry.lastOrNull() ?: return@execute
                val alight = runCatching { PoiService.fetchTransit(dest.latitude, dest.longitude) }.getOrNull()?.filter { it.lines.isNotEmpty() }
                if (alight.isNullOrEmpty()) return@execute
                val board = runCatching { PoiService.fetchParkAndRideAlong(first.geometry) }.getOrNull()
                if (board.isNullOrEmpty()) return@execute
                mainHandler.post {
                    if (token != previewToken || navMode != NavMode.PREVIEW) return@post
                    parkRideBoard = board
                    parkRideAlight = alight
                    updateRoutePreviewSummary()
                }
            }
        }
    }

    /** Traffic lights on each previewed route, once looked up. */
    private val routeLightCounts = HashMap<Int, Int>()

    /** Where each previewed route's traffic lights are (metres along). */
    private val routeLightsM = HashMap<Int, List<Double>>()

    /** Live traffic on each previewed route: slow / jammed stretches and the one-line note. */
    private val routeTraffic = HashMap<Int, Pair<List<TripStrip.Traffic>, TrafficNote?>>()

    /** Park & ride candidates for this preview: stations with Park & Ride along
     * the way, and stations near the destination (see [ParkAndRidePlanner]). */
    private var parkRideBoard: List<Poi> = emptyList()
    private var parkRideAlight: List<Poi> = emptyList()

    /** Road reports on each previewed route (accident, police, ...). */
    private val routeReports = HashMap<Int, List<RoadReports.OnRoute>>()

    /** Live road reports on [route]. Blocking (network). */
    private fun previewReports(route: Route): List<RoadReports.OnRoute>? {
        if (route.geometry.size < 2) return null
        val track = RouteTrack(route.geometry)
        val box = RoadReports.boxAhead(track, 0.0) ?: return null
        val found = runCatching { RoadReports.fetch(box) }.getOrNull() ?: return null
        return RoadReports.onRoute(track, found).takeIf { it.isNotEmpty() }
    }

    /** "Leave at 8:00 PM to save 12 min" for each previewed route, when worth saying. */
    private val routeLeaveAdvice = HashMap<Int, DepartureAdvisor.Advice>()

    /** Typical traffic on [route] leaving now vs in 1-3 hours. Blocking (network). */
    private fun leaveAdvice(route: Route): DepartureAdvisor.Advice? {
        val nodes = route.nodes ?: return null
        val free = route.freeFlowMs ?: return null
        val now = System.currentTimeMillis()
        val buckets = DepartureAdvisor.OFFSETS_H.associateWith { DepartureAdvisor.bucketAt(now + it * 3_600_000L) }
        val typical = runCatching { TrafficClient.typical(TrafficFlow.piecesAhead(nodes, 0), buckets.values.distinct()) }.getOrNull() ?: return null
        val track = RouteTrack(route.geometry)
        val cum = DoubleArray(route.geometry.size) { track.distanceAt(it) }
        val delays = buckets.mapValues { (_, b) -> TrafficFlow.delaySeconds(nodes, free, cum, typical[b].orEmpty()) }
        return DepartureAdvisor.advise(route.durationSeconds, delays)
    }

    /** Each previewed route's whole trip on one line (see [TripStrip]). */
    private val routeStrips = HashMap<Int, TripStrip.Strip>()

    /** Builds the strips from what has been looked up so far (tolls / R&Rs / lights / traffic arrive separately). */
    private fun rebuildRouteStrips() {
        routeOptions.forEachIndexed { i, route ->
            val pois = routePoisByIndex[i]
            val extras = routeExtrasByIndex[i]
            // Built even before (or without) the toll / R&R lookups: the line
            // with Now / Arrive and traffic still helps; markers join as they come.
            val track = RouteTrack(route.geometry)
            TripStrip
                .build(
                    route,
                    pois.orEmpty(),
                    extras.orEmpty(),
                    routeLightsM[i].orEmpty(),
                    routeTraffic[i]?.first.orEmpty(),
                    { onExpresswayAt(route, track, it) },
                    routeReports[i].orEmpty(),
                )
                ?.let { routeStrips[i] = it }
        }
    }

    /** Live traffic along [route] for the preview: its slow / jammed stretches and a
     * one-line note with the delay. Blocking (network); null when there is none. */
    private fun previewTraffic(route: Route): Pair<List<TripStrip.Traffic>, TrafficNote?>? {
        val nodes = route.nodes ?: return null
        val free = route.freeFlowMs ?: return null
        val live = runCatching { TrafficClient.flow(TrafficFlow.piecesAhead(nodes, 0)) }.getOrNull() ?: return null
        if (live.isEmpty()) return null
        val track = RouteTrack(route.geometry)
        val runs = TrafficFlow.runs(nodes, free, live)
        if (runs.isEmpty()) return null
        val spans = runs.map { TripStrip.Traffic(track.distanceAt(it.from), track.distanceAt(it.to), it.level == TrafficFlow.Level.JAM) }
        val cum = DoubleArray(route.geometry.size) { track.distanceAt(it) }
        val delayS = TrafficFlow.delaySeconds(nodes, free, cum, live)
        val worst = runs.firstOrNull { it.level == TrafficFlow.Level.JAM } ?: runs.first()
        val jam = worst.level == TrafficFlow.Level.JAM
        val at = track.distanceAt(worst.from)
        val road =
            route.steps
                .lastOrNull { s -> GeoUtils.snapToPolyline(s.point, route.geometry)?.let { track.progressOf(it) <= at + 1 } == true }
                ?.roadName
                ?.takeIf { it.isNotBlank() }
        val minutes = Math.round(delayS / 60.0).toInt()
        val what = if (jam) tr("Jam", "Jem") else tr("Slow traffic", "Trafik perlahan")
        val where = road?.let { tr(" on $it", " di $it") }.orEmpty()
        val note = TrafficNote(what + where + if (minutes >= 1) " · +$minutes min" else "", jam, delayS)
        return spans to note
    }

    private fun loadActiveRoutePois(
        route: Route,
        needPois: Boolean = true,
        needExtras: Boolean = true,
    ) {
        extrasExecutor.execute {
            if (needPois) {
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
                        updateRouteSigns(route)
                    }
                }
            }
        }
        if (needExtras) {
            lightsExecutor.execute {
                val extras =
                    try {
                        RoadPoiService.fetchExtrasAlong(listOf(route))[0].orEmpty()
                    } catch (e: Exception) {
                        Log.w("NavOSSigns", "traffic lights / R&R lookup failed: $e")
                        emptyList()
                    }
                mainHandler.post {
                    if (navMode == NavMode.ACTIVE && activeRoute === route) {
                        activeExtras = extras
                        updateRouteSigns(route)
                    }
                }
            }
        }
    }



    private fun setRouteListVisible(show: Boolean) {
        if (navMode != NavMode.PREVIEW) return
        routeListShowing = show
        routeListOverlay.visibility = if (show) View.VISIBLE else View.GONE
        routePreviewCard.visibility = if (show) View.GONE else View.VISIBLE
        if (show) renderRouteList() else fitSelectedRoute()
    }

    /** Inputs for the route cards: time, distance, main road and tolls (null until looked up). */
    private fun routeChoiceInputs(): List<RouteChoices.Input> =
        routeOptions.mapIndexed { i, r ->
            RouteChoices.Input(
                // Live traffic counts: "fastest" is fastest today, not on a free road.
                r.durationSeconds + (routeTraffic[i]?.second?.delaySeconds ?: 0.0),
                r.distanceMeters,
                NavFormat.viaRoadName(r),
                routePoisByIndex[i]?.count { it.kind == "toll" },
                routeLightCounts[i],
                routeStrips[i],
                routeTraffic[i]?.second,
                tollEstimate(i),
            )
        }

    /** What route [i]'s tolls cost: its plazas in the order they're passed (see [TollPricer]). */
    private fun tollEstimate(i: Int): com.excellentmindblow.navos.routing.TollEstimate? {
        val pricer = com.excellentmindblow.navos.routing.TollFares.pricer ?: return null
        val strip = routeStrips[i] ?: return null
        // A plaza merged from several booths: price it by the first name LLM knows.
        val names =
            strip.marks.filter { it.kind == TripStrip.Kind.TOLL }.map { m ->
                (listOf(m.name) + m.otherNames).firstOrNull { pricer.match(it).isNotEmpty() } ?: m.name
            }
        if (names.isEmpty()) return null
        return pricer.price(names)
    }

    /** The "All routes" screen: every route as a card, quickest first; tap one to pick it. */
    private fun renderRouteList() {
        routePreviewSheet.renderAll(RouteChoices.order(routeChoiceInputs(), selectedRouteIndex))
    }

    private fun fitSelectedRoute() {
        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        val bounds = LatLngBounds.Builder()
        selected.geometry.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        // Fit into the map left above the preview sheet and below the back button.
        val sheet = if (routePreviewCard.isShown && routePreviewCard.height > 0) routePreviewCard.height else dp(380)
        val top = dp(104)
        val bottom = (sheet + dp(24)).coerceAtMost(mapView.height - top - dp(80))
        map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(40), top, dp(40), bottom), 800)
    }

    private fun hideRouteChooser() {
        routeListShowing = false
        routeListOverlay.visibility = View.GONE
        routeBackButton.visibility = View.GONE
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


    private fun turnIconId(res: Int): String = "navos-turn-" + resources.getResourceEntryName(res)

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
            // A plain arrow lying along the route line (not the turn-sign
            // glyph): it points the way the line goes at this spot.
            feature.addStringProperty("icon", turnIconId(R.drawable.ic_turn_straight))
            feature.addStringProperty("name", s.roadName)
            feature.addNumberProperty("order", i)
            feature.addNumberProperty("bearing", GeoUtils.outgoingBearing(geometry, s.point))
            features.add(feature)
        }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** Pushes the camera's centre down the screen while driving so the
     * arrow sits in the lower part of the view (Waze-style look-ahead). */
    private fun applyNavPadding(on: Boolean) {
        val m = map ?: return
        navPaddingOn = on
        val top = if (on) (mapView.height * NAV_TOP_PADDING_FRACTION).toInt() else 0
        m.setPadding(0, top, 0, 0)
        navSheetRaisePx = -1 // re-apply below
        updateNavCameraForSheet(if (on) currentNavSheetRaise() else 0)
    }

    private var navPaddingOn = false
    private var navSheetRaisePx = -1

    /**
     * Smart drive camera: when the drive sheet opens over the map, the car
     * arrow moves up by the same amount, frame by frame with the sheet, so
     * it stays in view just above the sheet's edge (and goes back down when
     * it closes). Both paddings move: the view keeps its look-ahead shape
     * instead of the arrow being squashed toward the top.
     */
    private fun updateNavCameraForSheet(raisePx: Int) {
        if (raisePx == navSheetRaisePx) return
        navSheetRaisePx = raisePx
        if (!navPaddingOn || mapView.height == 0) {
            driveFollower.setPadding(0.0, 0.0)
            return
        }
        val base = mapView.height * NAV_TOP_PADDING_FRACTION
        // Never push the arrow above ~35% of the screen (a very tall sheet).
        val maxRaise = ((base + mapView.height) / 2 - mapView.height * NAV_ARROW_MIN_FRACTION).coerceAtLeast(0.0)
        val raise = raisePx.toDouble().coerceIn(0.0, maxRaise)
        driveFollower.setPadding((base - raise).coerceAtLeast(0.0), raise)
    }

    /** How far the drive sheet stands above its closed height right now (px). */
    private fun currentNavSheetRaise(): Int {
        if (bottomNavBar.visibility != View.VISIBLE || navBarRow.height == 0) return 0
        val parentHeight = (bottomNavBar.parent as View).height
        val collapsedHeight = bottomNavBar.paddingTop + bottomNavBar.paddingBottom + dp(24) + navBarRow.height
        return ((parentHeight - collapsedHeight) - bottomNavBar.top).coerceAtLeast(0)
    }

    private fun showDestinationMarker(latLng: LatLng) {
        destinationLatLng = latLng
        destinationSource?.setGeoJson(Point.fromLngLat(latLng.longitude, latLng.latitude))
    }

    private fun clearDestinationMarker() {
        destinationLatLng = null
        destinationSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    // ---- Multi-stop trips ----------------------------------------------------
    //
    // A trip is a list of places (tripPlaces): everything before the last one is
    // a stop, the last one is the destination. One entry is the ordinary A -> B
    // trip with route alternatives; with stops it is ONE route through them in
    // the listed order (the routing server only offers alternatives for A -> B).
    // Stops can be added, removed, reordered or auto-ordered in the route
    // preview, and added / skipped while driving (NavigationService re-plans the
    // rest of the drive from where the car is).

    private fun tripPoint(place: GeocodeResult) = RoutePoint(place.latitude, place.longitude)

    /** Calculates the route for [tripPlaces] and shows it in the preview.
     * [isEdit] = the driver changed the stops of a trip that is already on
     * screen: if no route can be found the list goes back to what it was. */
    private fun fetchTripRoute(isEdit: Boolean = false) {
        val places = tripPlaces.toList()
        val destination = places.lastOrNull() ?: return
        val origin = lastKnownLocation
        if (origin == null) {
            Toast.makeText(this, tr("Still waiting for a GPS fix - can't calculate a route yet", "Masih tunggu GPS - belum boleh cari laluan"), Toast.LENGTH_SHORT).show()
            if (isEdit) tripPlaces = tripRoutedPlaces.toMutableList()
            return
        }

        val originPoint = RoutePoint(origin.latitude, origin.longitude)
        val destinationPoint = tripPoint(destination)
        val stops = places.dropLast(1).mapIndexed { i, p -> RouteStop(tripPoint(p), p.name, i + 1) }
        val seq = ++tripRequestSeq
        busy.set(BusyPill.Job.ROUTE, if (isEdit) tr("Updating route…", "Kemas kini laluan…") else tr("Finding route…", "Cari laluan…"))

        searchExecutor.execute {
            var routes: List<Route> = emptyList()
            RoutePrefs.excludeUnsupported = false
            try {
                routes = RoutingService.calculateRoutes(originPoint, stops, destinationPoint)
            } catch (e: Exception) {
                // handled below - empty list means "couldn't route"
            }
            val couldNotAvoid = RoutePrefs.excludeUnsupported
            mainHandler.post {
                // A newer edit (or cancelling the trip) already superseded this one.
                if (seq != tripRequestSeq) return@post
                busy.set(BusyPill.Job.ROUTE, null)
                if (couldNotAvoid && routes.isNotEmpty()) {
                    Toast.makeText(this, tr("The routing server can't avoid tolls or highways yet - showing normal routes", "Server laluan belum boleh elak tol atau highway - tunjuk laluan biasa"), Toast.LENGTH_LONG).show()
                }
                if (routes.isNotEmpty()) {
                    tripRoutedPlaces = places
                    routeOptions = routes
                    selectedRouteIndex = 0
                    previewToken++
                    routePoisByIndex.clear()
                    routeExtrasByIndex.clear()
                    routeLightsM.clear()
                    routeLeaveAdvice.clear()
                    routeTraffic.clear()
                    routeReports.clear()
                    parkRideBoard = emptyList()
                    parkRideAlight = emptyList()
                    routeStrips.clear()
                    routeLightCounts.clear()
                    destinationName = destination.name
                    destinationAddress = destination.address
                    showDestinationMarker(LatLng(destination.latitude, destination.longitude))
                    showRoutePreview()
                    loadRoutePois(routes, previewToken)
                    // Extra alternatives only exist for a plain A -> B trip.
                    if (stops.isEmpty()) searchMoreRoutes(originPoint, destinationPoint, previewToken)
                } else if (isEdit) {
                    Toast.makeText(this, tr("Couldn't find a route through those stops", "Tak jumpa laluan ikut hentian tu"), Toast.LENGTH_SHORT).show()
                    tripPlaces = tripRoutedPlaces.toMutableList()
                    updateRoutePreviewSummary()
                } else {
                    clearDestinationMarker()
                    followingUser = true
                    homeSheet.visibility = View.VISIBLE
                    setHomeSheetState(HomeSheetState.SEARCH)
                    Toast.makeText(this, tr("Couldn't calculate a route to that destination", "Tak dapat cari laluan ke tempat tu"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Applies a change to the stop list and recalculates the route. */
    private fun editTrip(change: (MutableList<GeocodeResult>) -> Unit) {
        change(tripPlaces)
        if (tripPlaces.isEmpty()) {
            cancelRoutePreview()
            return
        }
        renderTripStopsPanel() // the new list shows at once; the route follows
        fetchTripRoute(isEdit = true)
    }

    private fun clearTrip() {
        tripRequestSeq++ // a late route reply must not bring the trip back
        busy.set(BusyPill.Job.ROUTE, null)
        tripPlaces = mutableListOf()
        tripRoutedPlaces = emptyList()
        passedStopNumbers.clear()
        addStopTarget = AddStopTarget.NONE
        renderStopMarkers(emptyList())
    }

    /** Opening the route preview mid-drive: the trip is what is still ahead. */
    private fun rebuildTripFromRoute(route: Route) {
        val destination = tripPlaces.lastOrNull() ?: return
        tripPlaces =
            route.stops
                .filter { it.number !in passedStopNumbers }
                .map { GeocodeResult(it.name, it.point.latitude, it.point.longitude) }
                .toMutableList()
                .apply { add(destination) }
        tripRoutedPlaces = tripPlaces.toList()
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int? = null,
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    /** Builds the multi-stop views in code (no layout changes): the stop list
     * and tr("Add stop", "Tambah hentian") button in the route preview card, and the next-stop line
     * plus tr("Add stop", "Tambah hentian") / tr("Skip next stop", "Langkau hentian seterusnya") buttons in the drive bar. */
    private fun setUpTripUi() {
        val blue = ContextCompat.getColor(this, R.color.navos_blue)
        val secondary = ContextCompat.getColor(this, R.color.navos_text_secondary)
        val primary = ContextCompat.getColor(this, R.color.navos_text)

        // Stops are listed in the preview header, under the destination.
        val header = findViewById<LinearLayout>(R.id.routePreviewHeader)
        tripStopsPanel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        header.addView(tripStopsPanel)
        addStopButton = findViewById(R.id.previewAddStopButton)
        addStopButton.setOnClickListener { enterAddStopSearch(AddStopTarget.PREVIEW) }

        val bar = bottomNavBar as LinearLayout
        nextStopText =
            TextView(this).apply {
                visibility = View.GONE
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(secondary)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = dp(8) }
            }
        bar.addView(nextStopText, bar.indexOfChild(navBarRow) + 1)

        fun driveButton(
            label: String,
            onClick: () -> Unit,
        ) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(primary)
            background = roundedBackground(ContextCompat.getColor(this@MainActivity, R.color.navos_surface_variant))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        }
        addStopDriveButton = driveButton(tr("Add stop", "Tambah hentian")) { enterAddStopSearch(AddStopTarget.ACTIVE) }
        skipStopDriveButton = driveButton(tr("Skip next stop", "Langkau hentian seterusnya")) { skipNextStopFromDrive() }
        val stopAt = bar.indexOfChild(navStopButton)
        bar.addView(addStopDriveButton, stopAt)
        bar.addView(skipStopDriveButton, stopAt + 1)
    }

    /** The stop list in the preview card: numbered stops then the destination,
     * each with move up / move down / remove, plus tr("Optimize order", "Susun ikut paling dekat"). Hidden for
     * an ordinary A -> B trip (the destination line shows as before). */
    private fun renderTripStopsPanel() {
        val multi = tripPlaces.size > 1
        tripStopsPanel.removeAllViews()
        tripStopsPanel.visibility = if (multi) View.VISIBLE else View.GONE
        if (multi) {
            // The list below names every stop and the destination.
            routePreviewDestination.visibility = View.GONE
            routePreviewAddress.visibility = View.GONE
        }
        val stopCount = (tripPlaces.size - 1).coerceAtLeast(0)
        addStopButton.visibility =
            if (tripPlaces.isNotEmpty() && stopCount < TripPlanner.MAX_STOPS) View.VISIBLE else View.GONE
        if (!multi) return

        val primary = ContextCompat.getColor(this, R.color.sig_text)
        val secondary = ContextCompat.getColor(this, R.color.sig_text_quiet)
        val ink = ContextCompat.getColor(this, R.color.sig_ink)
        val onInk = ContextCompat.getColor(this, R.color.sig_on_ink)
        val red = ContextCompat.getColor(this, R.color.sig_limit)

        fun action(
            glyph: String,
            description: String,
            onClick: () -> Unit,
        ) = TextView(this).apply {
            text = glyph
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(secondary)
            contentDescription = description
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { onClick() }
        }

        tripPlaces.forEachIndexed { i, place ->
            val isDestination = i == tripPlaces.size - 1
            val row =
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(2))
                }
            val badge =
                TextView(this).apply {
                    gravity = Gravity.CENTER
                    text = if (isDestination) "\u25A0" else "${i + 1}"
                    setTextColor(if (isDestination) Color.WHITE else onInk)
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    background =
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(if (isDestination) red else ink)
                        }
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) }
                }
            val label =
                TextView(this).apply {
                    text = place.name.ifBlank { if (isDestination) tr("Destination", "Destinasi") else tr("Stop ${i + 1}", "Hentian ${i + 1}") }
                    setTextColor(primary)
                    textSize = 16f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            row.addView(badge)
            row.addView(label)
            if (i > 0) row.addView(action("\u25B2", tr("Move up", "Naik")) { editTrip { it.add(i - 1, it.removeAt(i)) } })
            if (!isDestination) row.addView(action("\u25BC", tr("Move down", "Turun")) { editTrip { it.add(i + 1, it.removeAt(i)) } })
            row.addView(action("\u2715", tr("Remove", "Buang")) { editTrip { it.removeAt(i) } })
            tripStopsPanel.addView(row)
        }

        if (stopCount >= 2) {
            tripStopsPanel.addView(
                TextView(this).apply {
                    text = tr("Optimize order", "Susun ikut paling dekat")
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sig_text))
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    setPadding(0, dp(8), 0, dp(4))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { optimizeStopOrder() }
                },
            )
        }
    }

    /** Reorders the stops (destination stays last) into the shortest order. */
    private fun optimizeStopOrder() {
        val origin = lastKnownLocation ?: return
        if (tripPlaces.size < 3) return
        val stops = tripPlaces.dropLast(1)
        val destination = tripPlaces.last()
        val order =
            TripPlanner.bestOrder(
                RoutePoint(origin.latitude, origin.longitude),
                stops.map { tripPoint(it) },
                tripPoint(destination),
            )
        if (order == stops.indices.toList()) {
            Toast.makeText(this, tr("Your stops are already in the shortest order", "Hentian dah tersusun paling dekat"), Toast.LENGTH_SHORT).show()
            return
        }
        editTrip { places ->
            places.clear()
            places.addAll(order.map { stops[it] })
            places.add(destination)
        }
    }

    /** Opens the search sheet so the next place picked is added as a stop. */
    private fun enterAddStopSearch(target: AddStopTarget) {
        when (target) {
            AddStopTarget.PREVIEW -> {
                if (navMode != NavMode.PREVIEW || tripPlaces.isEmpty()) return
                if (tripPlaces.size - 1 >= TripPlanner.MAX_STOPS) {
                    Toast.makeText(this, tr("A trip can have up to ${TripPlanner.MAX_STOPS} stops", "Satu perjalanan boleh ada sampai ${TripPlanner.MAX_STOPS} hentian"), Toast.LENGTH_SHORT).show()
                    return
                }
                if (routeListShowing) setRouteListVisible(false)
                addStopTarget = target
                searchOpenedFromNav = true // same back / drag-down handling as a mid-drive search
                routePreviewCard.visibility = View.GONE
                routeBackButton.visibility = View.GONE
                topBar.visibility = View.GONE
                homeSheet.visibility = View.VISIBLE
                setHomeSheetState(HomeSheetState.SEARCH)
            }
            AddStopTarget.ACTIVE -> {
                if (navMode != NavMode.ACTIVE) return
                if ((navigationService?.remainingStops()?.size ?: 0) >= TripPlanner.MAX_STOPS) {
                    Toast.makeText(this, tr("A trip can have up to ${TripPlanner.MAX_STOPS} stops", "Satu perjalanan boleh ada sampai ${TripPlanner.MAX_STOPS} hentian"), Toast.LENGTH_SHORT).show()
                    return
                }
                addStopTarget = target
                enterSearchFromNav()
            }
            AddStopTarget.NONE -> Unit
        }
    }

    /** A place was picked in the tr("Add stop", "Tambah hentian") search. */
    private fun onStopPicked(result: GeocodeResult) {
        val target = addStopTarget
        exitSearchFromNav() // puts the preview card / drive bar back, clears addStopTarget
        recentPlaces.save(result)
        searchPicks.record(result)
        when (target) {
            AddStopTarget.PREVIEW -> {
                val pick = tripPoint(result)
                if (tripPlaces.any { GeoUtils.distanceMeters(tripPoint(it), pick) < 30.0 }) {
                    Toast.makeText(this, tr("That place is already on your trip", "Tempat tu dah ada dalam perjalanan"), Toast.LENGTH_SHORT).show()
                    return
                }
                editTrip { places ->
                    val destination = places.last()
                    val start =
                        lastKnownLocation?.let { RoutePoint(it.latitude, it.longitude) } ?: tripPoint(destination)
                    // Slot it in wherever it adds the least detour; moving it
                    // around afterwards is one tap.
                    val index =
                        TripPlanner.cheapestInsertIndex(start, places.dropLast(1).map { tripPoint(it) }, tripPoint(destination), pick)
                    places.add(index, result)
                }
            }
            AddStopTarget.ACTIVE -> {
                navigationService?.addStop(RouteStop(tripPoint(result), result.name, 0))
                busy.set(BusyPill.Job.STOP, tr("Adding stop…", "Tambah hentian…"))
            }
            AddStopTarget.NONE -> Unit
        }
    }

    private fun skipNextStopFromDrive() {
        navigationService?.skipNextStop()
        busy.set(BusyPill.Job.STOP, tr("Skipping stop…", "Langkau hentian…"))
    }

    /** The drive bar's extra buttons: only in the expanded bar, and "Skip next
     * stop" only while a stop is still ahead. */
    private fun updateStopButtons() {
        val show = navSheetExpanded && navMode == NavMode.ACTIVE
        addStopDriveButton.visibility = if (show) View.VISIBLE else View.GONE
        skipStopDriveButton.visibility =
            if (show && (lastNavState?.stopsRemaining ?: 0) > 0) View.VISIBLE else View.GONE
    }

    /** "Stop 2: Name - 12 min - 4.1 km" under the ETA while a stop is ahead. */
    private fun updateNextStopLine(state: NavigationState?) {
        val stop = state?.nextStop
        if (state == null || stop == null || state.offRoute) {
            nextStopText.visibility = View.GONE
        } else {
            val title = if (stop.name.isBlank()) tr("Stop ${stop.number}", "Hentian ${stop.number}") else tr("Stop ${stop.number}: ${stop.name}", "Hentian ${stop.number}: ${stop.name}")
            nextStopText.text =
                "$title \u2022 ${NavFormat.formatRouteMinutes(state.durationToNextStopSeconds)} \u2022 ${NavFormat.formatTripDistance(state.distanceToNextStopMeters)}"
            nextStopText.visibility = View.VISIBLE
        }
        updateStopButtons()
    }

    /** Numbered pins for the stops of the route on screen. */
    private fun renderStopMarkers(stops: List<RouteStop>) {
        val src = stopsSource ?: return
        val features =
            stops
                .filter { it.number !in passedStopNumbers }
                .map { stop ->
                    Feature.fromGeometry(Point.fromLngLat(stop.point.longitude, stop.point.latitude)).also {
                        it.addStringProperty("icon", "$STOP_ICON_PREFIX${stop.number.coerceIn(1, STOP_ICON_COUNT)}")
                    }
                }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
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
        val floating = listOf(speedBadge, speedLimitSign, poiToggleColumn, recenterButton, poiCard)
        if (bottomNavBar.visibility != View.VISIBLE || navBarRow.height == 0) {
            floating.forEach { if (it.translationY != 0f) it.translationY = 0f }
            if (navMode == NavMode.ACTIVE) updateNavCameraForSheet(0)
            return
        }
        val parentHeight = (bottomNavBar.parent as View).height
        val collapsedHeight = bottomNavBar.paddingTop + bottomNavBar.paddingBottom + dp(24) + navBarRow.height
        val extra = ((parentHeight - collapsedHeight) - bottomNavBar.top).coerceAtLeast(0)
        val ty = -extra.toFloat()
        floating.forEach { if (it.translationY != ty) it.translationY = ty }
        // Same frame, same amount: the camera lifts the arrow with the sheet.
        if (navMode == NavMode.ACTIVE) updateNavCameraForSheet(extra)
    }

    private fun setNavSheetExpanded(expanded: Boolean) {
        if (expanded == navSheetExpanded) return
        navSheetExpanded = expanded
        // Bounds change and fade together, not AutoTransition's three
        // back-to-back steps (which tripled the time it took to settle).
        TransitionManager.beginDelayedTransition(
            bottomNavBar as ViewGroup,
            TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(ChangeBounds())
                .addTransition(Fade())
                .setDuration(200)
                .setInterpolator(Motion.easeOut),
        )
        navStopButton.visibility = if (expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.navPoiChips).visibility = if (expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.shareEtaButton).visibility = if (expanded) View.VISIBLE else View.GONE
        updateStopButtons()
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
        detailView.text = "${NavFormat.formatTripDuration(durationSeconds)} • ${NavFormat.formatTripDistance(distanceMeters)}" +
            if (detailView === etaDetailText) lightsAheadSuffix() else ""
    }




    private fun showRoutePreview() {
        // Opened mid-drive (switch route): the trip is whatever is still ahead.
        if (navMode == NavMode.ACTIVE) activeRoute?.let { rebuildTripFromRoute(it) }
        navMode = NavMode.PREVIEW
        topBar.visibility = View.GONE
        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        homeSheet.visibility = View.GONE
        recenterButton.visibility = View.GONE
        // Also reachable mid-drive via the drive bar's alt-route icon, so
        // clear the ACTIVE-only chrome too (harmless no-ops if already gone).
        speedBadge.visibility = View.GONE
        clearSpeedLimit(resetData = false)
        poiToggleColumn.visibility = View.GONE
        hidePoiCard()
        nextTurnStrip.visibility = View.GONE
        laneContainer.visibility = View.GONE
        renderPoiPins() // pins are drive-only
        redrawRouteLines()

        routeOptions.getOrNull(selectedRouteIndex) ?: return

        updateRoutePreviewSummary()
        routePreviewCard.visibility = View.VISIBLE
        routeListShowing = false
        routeListOverlay.visibility = View.GONE
        routeBackButton.visibility = View.VISIBLE
        // After layout, so the fit knows how tall the sheet is.
        routePreviewCard.post { if (navMode == NavMode.PREVIEW) fitSelectedRoute() }
    }

    private fun updateRoutePreviewSummary() {
        val selected = routeOptions.getOrNull(selectedRouteIndex) ?: return
        val inputs = routeChoiceInputs()
        val arrival = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (selected.durationSeconds * 1000).toLong()))
        val stopCount = (tripPlaces.size - 1).coerceAtLeast(0)
        // The selected card's strip already says "Arrive ..." (with traffic).
        val arrive = if (routeStrips[selectedRouteIndex] == null) getString(R.string.route_hint_arrive, arrival) else null
        val leave =
            routeLeaveAdvice[selectedRouteIndex]?.let { a ->
                val at = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + a.offsetHours * 3_600_000L))
                val min = Math.round(a.savedSeconds / 60.0).toInt()
                tr("Usually quicker later: leave at $at to save $min min", "Biasanya lebih cepat nanti: gerak pukul $at, jimat $min min")
            }
        val hint =
            listOfNotNull(
                if (stopCount > 0) getString(R.string.route_hint_stops, if (stopCount == 1) "1 stop" else "$stopCount stops") else null,
                arrive,
                leave,
            ).joinToString("\n")
        routePreviewSheet.render(
            shown = RouteChoices.order(inputs, selectedRouteIndex, RouteChoices.SHOWN),
            totalRoutes = routeOptions.size,
            destinationName = destinationName,
            destinationAddress = destinationAddress,
            hintText = hint,
        )
        val plan =
            if (parkRideBoard.isEmpty() || parkRideAlight.isEmpty() || selected.geometry.size < 2) {
                null
            } else {
                ParkAndRidePlanner.best(
                    RouteTrack(selected.geometry),
                    selected.durationSeconds + (routeTraffic[selectedRouteIndex]?.second?.delaySeconds ?: 0.0),
                    selected.geometry.last(),
                    parkRideBoard,
                    parkRideAlight,
                )
            }
        routePreviewSheet.showParkRide(plan) {
            val park = plan?.from?.parkAndRide ?: return@showParkRide
            // Drive to the car park; the train part is up to the driver from there.
            onDestinationSelected(GeocodeResult(tr("Park & Ride ${plan.from.name}", "Park & Ride ${plan.from.name}"), park.latitude, park.longitude))
        }
        if (routeListShowing) renderRouteList()
        renderTripStopsPanel()
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
        clearPassedRoute()
        renderRoutePois()
        renderStopMarkers(selected.stops)
        renderRouteTimeLabels()
    }

    /** Preview only: a time bubble halfway along each route (the chosen one in ink, on top). */
    private fun renderRouteTimeLabels() {
        val src = routeTimeSource ?: return
        val style = map?.style
        if (navMode != NavMode.PREVIEW || style == null) {
            src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        val features =
            routeOptions.mapIndexedNotNull { index, route ->
                val at = RouteChoices.labelPoint(route.geometry) ?: return@mapIndexedNotNull null
                val selected = index == selectedRouteIndex
                val icon = "navos-route-time-$index-${if (selected) "on" else "off"}"
                style.addImage(icon, MapIcons.timeBubble(this, NavFormat.formatRouteMinutes(route.durationSeconds), selected))
                Feature.fromGeometry(Point.fromLngLat(at.longitude, at.latitude)).also {
                    it.addStringProperty("icon", icon)
                    it.addNumberProperty(ROUTE_INDEX_PROPERTY, index)
                    it.addNumberProperty("order", if (selected) 1 else 0)
                }
            }
        src.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun onMapTapped(
        map: MapLibreMap,
        latLng: LatLng,
    ): Boolean {
        if (handleRoadPoiTap(map, latLng)) return true
        if (navMode == NavMode.ACTIVE || navMode == NavMode.IDLE) {
            if (onDriveMapTapped(map, latLng)) return true
            // Not driving, search sheet closed: tapping empty map drops a pin.
            if (navMode == NavMode.IDLE && homeSheetState == HomeSheetState.COLLAPSED) {
                dropPin(latLng)
                return true
            }
            return false
        }
        if (navMode != NavMode.PREVIEW) return false
        val screenPoint = map.projection.toScreenLocation(latLng)
        val tapped =
            try {
                map.queryRenderedFeatures(screenPoint, ROUTE_TIME_LAYER_ID, ALT_ROUTE_LAYER_ID)
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
        routePreviewCard.post { if (navMode == NavMode.PREVIEW && !routeListShowing) fitSelectedRoute() }
    }

    private fun cancelRoutePreview() {
        navMode = NavMode.IDLE
        applyPendingUiRecreate()
        routeOptions = emptyList()
        previewToken++
        routePoisByIndex.clear()
        routeExtrasByIndex.clear()
        routeLightsM.clear()
        routeLeaveAdvice.clear()
        routeTraffic.clear()
        routeReports.clear()
        parkRideBoard = emptyList()
        parkRideAlight = emptyList()
        routeStrips.clear()
        routeLightCounts.clear()
        hideRouteChooser()
        renderRoutePois()
        routePreviewCard.visibility = View.GONE
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        renderRouteTimeLabels()
        clearDestinationMarker()
        clearTrip()
        followingUser = true
        homeSheet.visibility = View.VISIBLE
        setHomeSheetState(HomeSheetState.COLLAPSED)
    }

    // ---- Active (driving) navigation ------------------------------------

    private fun beginActiveNavigation() {
        val route = routeOptions.getOrNull(selectedRouteIndex) ?: return
        driveStartedAtMs = SystemClock.elapsedRealtime()
        tripPlaces.lastOrNull()?.let { tripHabits.record(it) }
        // Offline safety net: the map along the route, for tunnels / dead zones.
        runCatching { routeOfflineCache.save(route, mapStyleUrl()) }
        if (::roadReportsUi.isInitialized) roadReportsUi.start()

        navMode = NavMode.ACTIVE
        driveFollower.resetArrowHeading()
        driveFollower.northUp = false // every drive starts heading-up
        refreshCompassMode()
        smoothedBearing = null
        smoothedNavZoom = null
        lastNavState = null
        navFollowing = true
        driveFollower.clearManualZoom()
        snapSegmentIndex = 0
        lastTrimSnap = null
        alertedCameraKeys.clear()
        cameraRouteIndex.clear()
        hideCameraAlert()
        topBar.visibility = View.GONE
        recenterButton.visibility = View.GONE
        routePreviewCard.visibility = View.GONE
        // Only the chosen route stays on screen once driving - alternates
        // were just for picking, not useful mid-drive.
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

        activeRoute = route
        passedStopNumbers.clear()
        hideRouteChooser()
        previewToken++
        activeRoutePois = routePoisByIndex[selectedRouteIndex].orEmpty()
        activeExtras = routeExtrasByIndex[selectedRouteIndex].orEmpty()
        activeLights = emptyList()
        renderRoutePois()
        if (activeRoutePois.isEmpty() || activeExtras.isEmpty()) {
            loadActiveRoutePois(route, needPois = activeRoutePois.isEmpty(), needExtras = activeExtras.isEmpty())
        }
        applyNavPadding(true)
        renderedCalloutStep = -1
        renderTurnCallouts(1)
        signOverlay.setNight(appliedMapDark == true)
        updateRouteSigns(route)
        trafficLayer.start(route)
        showTrafficNoticeOnce()
        bottomNavBar.visibility = View.VISIBLE
        speedBadge.visibility = if (MapDisplayPrefs.speedometer) View.VISIBLE else View.GONE
        poiToggleColumn.visibility = View.VISIBLE
        turnDistanceText.text = ""
        turnStreetText.text = tr("Starting navigation...", "Mula navigasi...")
        showTripSummary(route.durationSeconds, route.distanceMeters, etaClockText, etaDetailText)
        navSheetExpanded = false
        navStopButton.visibility = View.GONE
        findViewById<View>(R.id.shareEtaButton).visibility = View.GONE
        updateStopButtons()
        updateNextStopLine(null)

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
        headingSensor.stop()
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
        var fromLane = false

        // Snap the arrow onto the blue line (GPS drifts a few metres off
        // the road) and take the heading from the LANE itself - looking a
        // little way ahead along it - never from the phone/GPS heading.
        // That way the arrow follows the line through bends and keeps
        // turning even when you're crawling or stopped (GPS heading is
        // meaningless below walking speed, which is why it used to freeze).
        val geometry = activeRoute?.geometry
        var progress = Double.NaN
        // The arrow moves by distance along the route (see RouteTrack) so it
        // stays on the blue line through bends.
        driveFollower.track =
            if (geometry != null && geometry.size >= 2) {
                routeTrack?.takeIf { it.source === geometry } ?: RouteTrack(geometry).also { routeTrack = it }
            } else {
                null
            }
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
                progress = driveFollower.track?.progressOf(snap) ?: Double.NaN
                val speedMs = if (location.hasSpeed()) location.speed.toDouble() else 0.0
                val ahead = (8.0 + speedMs * 1.2).coerceIn(10.0, 35.0)
                rawBearing = GeoUtils.bearingAhead(geometry, snap, ahead)
                fromLane = true
                // Start already facing along the route, not north.
                if (smoothedBearing == null) smoothedBearing = rawBearing
                // Fade the part of the line already driven (trails one fix
                // behind so it never gets ahead of the gliding arrow).
                lastTrimSnap?.let { trimRoute(it, geometry) }
                lastTrimSnap = snap
                trafficLayer.onProgress()
            }
        }

        val bearing = smoothedNavBearing(rawBearing, location, fromLane)
        // Start the glide BEFORE the new fix is pushed, so even a big jump
        // is eased instead of snapping the camera in one frame.
        if (durationMs > NAV_FOLLOW_EASE_MS) driveFollower.beginCameraBlend(durationMs.toLong())
        driveFollower.pushFix(
            lat,
            lng,
            bearing,
            smartNavZoom(location),
            progressM = progress,
            newSpeedMs = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
        )
    }

    /** Splits the route at [snap]: everything ahead stays the bright blue
     * line, a short tail behind it is drawn faded. */
    private fun trimRoute(
        snap: GeoUtils.RouteSnap,
        geometry: List<RoutePoint>,
    ) {
        val i = snap.segmentIndex.coerceIn(0, geometry.size - 2)
        val head = Point.fromLngLat(snap.point.longitude, snap.point.latitude)

        val remaining = ArrayList<Point>(geometry.size - i)
        remaining.add(head)
        for (k in i + 1 until geometry.size) remaining.add(Point.fromLngLat(geometry[k].longitude, geometry[k].latitude))
        if (remaining.size >= 2) routeSource?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(remaining)))

        val tailStart = (i - PASSED_TAIL_POINTS).coerceAtLeast(0)
        val passed = ArrayList<Point>()
        for (k in tailStart..i) passed.add(Point.fromLngLat(geometry[k].longitude, geometry[k].latitude))
        passed.add(head)
        if (passed.size >= 2) routePassedSource?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(passed)))
    }

    private fun clearPassedRoute() {
        lastTrimSnap = null
        routePassedSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }

    /** Target zoom for the follow camera: cruising zoom, a good deal closer
     * when crawling or stopped, wider at speed, and pulled in as the next
     * turn / U-turn / roundabout approaches so the junction is easy to read
     * (then back out once it's behind). This only picks the target - the
     * smooth easing toward it happens every frame in DriveFollower, so it
     * glides in and out instead of stepping on each GPS fix. */
    private fun smartNavZoom(location: Location): Double {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val speedAdjust =
            when {
                speedKmh < 25.0 -> NAV_ZOOM_SLOW_BONUS * (1.0 - speedKmh / 25.0)
                speedKmh > 60.0 -> -NAV_ZOOM_FAST_PENALTY * ((speedKmh - 60.0) / 50.0).coerceAtMost(1.0)
                else -> 0.0
            }
        val target = NAV_ZOOM + speedAdjust + turnZoomBoost(lastNavState, speedKmh)
        smoothedNavZoom = target
        return target
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
                state.modifier == "uturn" -> 1.3
                state.maneuverType == "roundabout" || state.maneuverType == "rotary" -> 0.8
                state.modifier == "left" || state.modifier == "right" ||
                    state.modifier == "sharp left" || state.modifier == "sharp right" -> 0.9
                state.modifier == "slight left" || state.modifier == "slight right" -> 0.45
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
        driveFollower.clearManualZoom()
        mainHandler.removeCallbacks(autoRecenterRunnable)
        if (navMode == NavMode.ACTIVE) {
            navFollowing = true
            smoothedNavZoom = null
            recenterButton.visibility = View.GONE
            refreshCompassMode()
            if (location != null) {
                updateNavCamera(location, durationMs = NAV_RECENTER_EASE_MS)
            }
            // else: no fix yet - navFollowing is already true, so the next
            // GPS update (whichever arrives first) applies it automatically.
            return
        }

        followingUser = true
        recenterButton.visibility = View.GONE
        refreshCompassMode()
        if (location == null) {
            Toast.makeText(this, tr("Waiting for GPS...", "Tunggu GPS..."), Toast.LENGTH_SHORT).show()
            return
        }
        driveFollower.holdCamera(NAV_RECENTER_EASE_MS + 50L)
        val camera =
            CameraPosition
                .Builder()
                .target(LatLng(location.latitude, location.longitude))
                .tilt(0.0)
                .zoom(IDLE_RECENTER_ZOOM)
        // Heading mode: come back facing the way you're travelling.
        if (driveFollower.idleHeadingUp) camera.bearing(driveFollower.currentBearing())
        map?.easeCamera(CameraUpdateFactory.newCameraPosition(camera.build()), NAV_RECENTER_EASE_MS)
    }

    /** Compass ring is blue while the camera is following AND turning with
     * your heading, navy in north-up (or when you've panned away). */
    private fun refreshCompassMode() {
        val heading =
            when (navMode) {
                NavMode.ACTIVE -> navFollowing && !driveFollower.northUp
                NavMode.IDLE -> followingUser && driveFollower.idleHeadingUp
                NavMode.PREVIEW -> false
            }
        findViewById<CompassView>(R.id.compassButton)?.headingMode = heading
    }

    /** Softens the nav camera's heading so it eases into turns instead of
     * snapping. Raw GPS bearing is noisy (especially near-stationary) and,
     * fed straight into the camera, can also spin the "long way round"
     * whenever it crosses the 0/360 wrap (e.g. 350 -> 10). We keep an
     * unwrapped running bearing, always step toward the new reading via the
     * shortest signed angle, and only take a fraction of that step per
     * update so the turn reads as a smooth ease rather than a jump. */
    private fun smoothedNavBearing(
        rawBearing: Double?,
        location: Location,
        fromLane: Boolean = false,
    ): Double {
        val current = smoothedBearing ?: map?.cameraPosition?.bearing ?: 0.0
        // GPS heading is unreliable while nearly stationary - ignore it
        // rather than let the camera jitter in place at a red light. A
        // heading taken from the route line itself is always reliable, so
        // it skips that gate.
        val reliable = rawBearing != null && (fromLane || !location.hasSpeed() || location.speed > 1.0f)
        if (!reliable) return current

        val currentHeading = ((current % 360.0) + 360.0) % 360.0
        var delta = rawBearing!! - currentHeading
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0

        val next = current + delta * (if (fromLane) 0.6 else 0.35)
        smoothedBearing = next
        return next
    }

    /** Arrival overlay: the drive UI hides, the destination pin stays on the
     * map, and a card asks the driver to confirm. "Yes" ends navigation. */
    private fun showArrivalCard() {
        if (arrivalCardShowing) return
        arrivalCardShowing = true

        findViewById<TextView>(R.id.arrivalDestinationName).apply {
            val name = destinationName.orEmpty()
            text = name
            visibility = if (name.isNotBlank()) View.VISIBLE else View.GONE
        }
        val route = activeRoute
        findViewById<TextView>(R.id.arrivalSide).apply {
            val side = NavFormat.arrivalSide(route?.steps?.lastOrNull { it.maneuverType == "arrive" }?.modifier)
            if (side != null) setText(side)
            visibility = if (side != null) View.VISIBLE else View.GONE
        }
        // Trip stats: real drive time, the route's distance, toll plazas passed (only if any).
        fun stat(
            id: Int,
            value: String?,
            labelRes: Int,
        ) {
            val tile = findViewById<View>(id)
            tile.visibility = if (value == null) View.GONE else View.VISIBLE
            tile.findViewById<TextView>(R.id.statValue).text = value.orEmpty()
            tile.findViewById<TextView>(R.id.statLabel).setText(labelRes)
        }
        val drove = if (driveStartedAtMs > 0) (SystemClock.elapsedRealtime() - driveStartedAtMs) / 1000.0 else null
        val tolls = activeRoutePois.count { it.kind == "toll" }
        stat(R.id.arrivalStatTime, drove?.let { NavFormat.formatRouteMinutes(it) }, R.string.stat_drive_time)
        stat(R.id.arrivalStatDistance, route?.let { NavFormat.formatTripDistance(it.distanceMeters) }, R.string.stat_distance)
        stat(R.id.arrivalStatTolls, if (tolls > 0) tolls.toString() else null, R.string.stat_tolls)
        val arrivedAt = destinationLatLng
        val arrivedName = destinationName
        findViewById<View>(R.id.arrivalSaveButton).apply {
            visibility = if (arrivedAt != null) View.VISIBLE else View.GONE
            setOnClickListener {
                if (arrivedAt != null) saveArrivedPlace(arrivedName, arrivedAt)
            }
        }

        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        speedBadge.visibility = View.GONE
        laneContainer.visibility = View.GONE
        nextTurnStrip.visibility = View.GONE
        recenterButton.visibility = View.GONE

        arrivalCard.visibility = View.VISIBLE
        arrivalCard.post {
            arrivalCard.translationY = arrivalCard.height.toFloat()
            arrivalCard.animate().translationY(0f).setDuration(320).setInterpolator(Motion.easeDrawer).start()
        }

        destinationLatLng?.let { dest ->
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(dest, 17.0), 600)
        }
    }

    /** "Save place" on the arrival card: keep where we arrived as Home or Work. */
    private fun saveArrivedPlace(
        name: String?,
        at: LatLng,
    ) {
        val label = listOfNotNull(name?.takeIf { it.isNotBlank() }, destinationAddress?.trim()?.takeIf { it.isNotBlank() }).joinToString(", ")
        val place = GeocodeResult(label.ifBlank { tr("Saved place", "Tempat disimpan") }, at.latitude, at.longitude)
        val keys = arrayOf(SavedPlaces.HOME, SavedPlaces.WORK)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.save_place)
            .setItems(arrayOf(getString(R.string.save_as_home), getString(R.string.save_as_work))) { _, which ->
                savedPlaces.save(keys[which], place)
                refreshSavedPlaceLabels()
                val what = getString(if (which == 0) R.string.shortcut_home else R.string.shortcut_work)
                Toast.makeText(this, getString(R.string.saved_as, what), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun hideArrivalCard() {
        arrivalCardShowing = false
        arrivalCard.animate().cancel()
        arrivalCard.animate().translationY(arrivalCard.height.toFloat())
            .setDuration(200)
            .setInterpolator(Motion.easeOut)
            .withEndAction { arrivalCard.visibility = View.GONE }
            .start()
    }

    private fun endActiveNavigation() {
        navMode = NavMode.IDLE
        if (::roadReportsUi.isInitialized) roadReportsUi.stop()
        parkingPois = emptyList()
        parkingFor = null
        transitPois = emptyList()
        transitFor = null
        arrivalCardShowing = false
        arrivalCard.animate().cancel()
        arrivalCard.visibility = View.GONE
        driveFollower.resetArrowHeading()
        driveFollower.northUp = false
        driveFollower.track = null
        routeTrack = null
        applyPendingUiRecreate()
        activeRoute = null
        applyNavPadding(false)
        activeRoutePois = emptyList()
        activeExtras = emptyList()
        activeLights = emptyList()
        signOverlay.clear()
        routeArrows.clear()
        trafficLayer.stop()
        hideFasterRoute()
        renderRoutePois()
        setRecalculating(false)
        busy.set(BusyPill.Job.STOP, null)
        renderedCalloutStep = -1
        renderTurnCallouts(0)
        clearPassedRoute()
        hideCameraAlert()
        alertedCameraKeys.clear()
        cameraRouteIndex.clear()
        renderPoiPins()
        closeNextTurns()
        topNavBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        speedBadge.visibility = View.GONE
        clearSpeedLimit(resetData = true)
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
        clearTrip()
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
        refreshCompassMode()

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
        busy.set(BusyPill.Job.RECALCULATING, if (show) getString(R.string.recalculating) else null)
    }

    private fun onNavigationState(state: NavigationState) {
        // Parked / pulling in off the route (petrol, R&R): no "Recalculating" until it drives on.
        val pulledIn = lastSpeedKmh < 3.0 * 3.6 && state.distanceToRouteMeters < 300.0
        setRecalculating(state.offRoute && !state.isArrival && !pulledIn)
        lastNavState = state
        val distanceLabel = NavFormat.formatCountdownDistance(state.distanceToNextTurnMeters)
        turnDistanceText.text = if (state.offRoute) "" else distanceLabel
        // Off the planned route: NavigationService is already fetching a new
        // one (and says "Recalculating"), so show that instead of a turn
        // instruction that no longer applies.
        turnStreetText.text = if (state.offRoute) tr("Recalculating…", "Cari laluan baru…") else state.instruction

        showTripSummary(state.durationRemainingSeconds + trafficLayer.delayAheadSeconds, state.distanceRemainingMeters, etaClockText, etaDetailText)
        updateNextStopLine(state)

        if (state.currentStepIndex != renderedCalloutStep) {
            renderedCalloutStep = state.currentStepIndex
            renderTurnCallouts(state.currentStepIndex)
        }

        turnIcon.setImageResource(NavFormat.iconForManeuver(state.maneuverType, state.modifier))
        turnIcon.rotation = 0f
        val ref = if (state.offRoute) null else state.roadRef
        turnRefTag.text = ref.orEmpty()
        turnRefTag.visibility = if (ref == null) View.GONE else View.VISIBLE
        // Like the real gantry: green on the E (expressway) roads, JKR blue everywhere else.
        val signBg = if (SignColour.isExpressway(ref)) R.drawable.bg_sig_turn else R.drawable.bg_sign_blue
        if (signBg != turnSignBg) {
            turnSignBg = signBg
            topNavBar.setBackgroundResource(signBg)
        }

        if (nextTurnsSheet.visibility == View.VISIBLE) {
            if (state.currentStepIndex != nextTurnsRenderedIndex) {
                refreshNextTurns()
            } else {
                nextTurnsFirstDistance?.text = NavFormat.formatTurnDistance(state.distanceToNextTurnMeters)
            }
        }

        // "and then" look-ahead - only worth showing once the current turn
        // is close enough that the next one is relevant, same as Waze.
        val nextType = state.nextManeuverType
        if (nextType != null && state.distanceToNextTurnMeters <= 400 && !state.isArrival) {
            nextTurnIcon.setImageResource(NavFormat.iconForManeuver(nextType, state.nextModifier))
            nextTurnIcon.rotation = 0f
            nextTurnStreet.text = state.nextRoadName ?: state.nextInstruction.orEmpty()
            nextTurnDistance.text =
                if (state.distanceBetweenTurnsMeters > 0) {
                    getString(R.string.in_distance, NavFormat.formatTurnDistance(state.distanceBetweenTurnsMeters))
                } else {
                    ""
                }
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
            showArrivalCard()
        }
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
                setImageResource(NavFormat.iconForManeuver(step.maneuverType, step.modifier))
                setColorFilter(textColor)
            }
        val texts =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        val distance =
            TextView(this).apply {
                text = NavFormat.formatTurnDistance(meters)
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
        // White arrows on the dark lane strip under the turn sign; lanes
        // that don't keep you on the route are faded.
        val validTint = Color.WHITE
        val dimTint = ColorUtils.setAlphaComponent(Color.WHITE, 0x61)
        lanes.forEach { lane ->
            val indication = lane.indications.firstOrNull() ?: "straight"
            val icon =
                ImageView(this).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(size, size).apply {
                            marginStart = margin
                            marginEnd = margin
                        }
                    setImageResource(R.drawable.ic_turn_straight)
                    setColorFilter(if (lane.valid) validTint else dimTint)
                    setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                    rotation = NavFormat.rotationForIndication(indication)
                }
            laneContainer.addView(icon)
        }
        laneContainer.visibility = View.VISIBLE
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
        headingSensor.start()
    }

    /** The phone's compass, for the arrow while standing still (idle map only). */
    private val headingSensor by lazy { HeadingSensor(this) }

    // Only fires for the Activity's own idle/free-roam tracker - while
    // NavMode.ACTIVE, that tracker is stopped (see beginActiveNavigation())
    // and NavigationService's own GPS subscription drives the drive
    // instead, via navServiceListener.onNavLocation() above. So there's no
    // "are we navigating" branch needed here any more: this function only
    // ever runs for the non-navigating case.
    private var lastTilePrefetch: Location? = null

    /** Downloads the Overture place tiles around you ahead of the first search here. */
    private fun prefetchPlaceTiles(location: Location) {
        val last = lastTilePrefetch
        if (last != null && last.distanceTo(location) < TILE_PREFETCH_MOVE_M) return
        lastTilePrefetch = location
        GeocodingService.prefetchTiles(location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission") // permission is confirmed before startLocationTracking() runs
    private fun onLocationUpdate(location: Location) {
        val firstFix = lastKnownLocation == null
        lastKnownLocation = location
        if (firstFix) homeScreen.render() else homeScreen.onLocation()
        prefetchPlaceTiles(location)
        val bearing =
            if (location.hasBearing() && (!location.hasSpeed() || location.speed > 1f)) {
                location.bearing.toDouble()
            } else {
                // Standing still: GPS has no heading, so point where the phone points
                // (compass), or keep the arrow's last heading if there's no compass.
                headingSensor.headingDegrees(location.latitude, location.longitude)
            }

        if (!hasCenteredOnFirstFix) {
            val here = LatLng(location.latitude, location.longitude)
            maybeFetchAreaPois(here)
            maybeFetchIdlePois(here)
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
        // onStop() paused the idle tracker; resume it (it was never restarted
        // before, so the arrow froze after coming back to the app).
        if (navMode != NavMode.ACTIVE) {
            locationTracker?.let {
                it.start()
                headingSensor.start()
            }
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
        headingSensor.stop()
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
        lightsExecutor.shutdownNow()
        signExecutor.shutdownNow()
        trafficLayer.shutdown()
        placeSearchExecutor.shutdownNow()
        poiExecutor.shutdownNow()
        speedExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        unbindNavigationService()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
