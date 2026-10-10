package com.excellentmindblow.navos

import android.Manifest
import android.animation.Animator
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
import android.graphics.Color
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
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.res.ColorStateList
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.poi.PoiService
import com.excellentmindblow.navos.routing.LaneInfo
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
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
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

private const val NAV_TILT_DEGREES = 55.0
private const val NAV_ZOOM = 18.0

// Position-button / nav-follow camera tuning. Two different ease
// durations on purpose: a short one for the continuous per-GPS-fix follow
// (so it reads as smooth tracking, not a fresh animation firing every
// second) and a longer one only for the deliberate "snap into place"
// moment - the Position button tap or entering active navigation.
private const val NAV_FOLLOW_EASE_MS = 350
private const val NAV_RECENTER_EASE_MS = 700
private const val IDLE_RECENTER_ZOOM = 16.5

private const val PREFS_NAME = "navos_prefs"
private const val KEY_VOICE_ENABLED = "voice_enabled"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"

/** Pause after the last keystroke before a live search fires - long
 * enough not to query on every letter, short enough to feel instant. */
private const val LIVE_SEARCH_DEBOUNCE_MS = 350L

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
    private lateinit var etaSummaryText: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var speedBadge: TextView
    private lateinit var recenterButton: ImageButton
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

    /** Home/Work rows' natural height (no search results showing yet) -
     * measured once and reused as the drag range when opening the sheet
     * from COLLAPSED. Dragging it closed again from an already-open sheet
     * (which may be taller, with results showing) uses the sheet's actual
     * current height instead - see the drag handler below. -1 = not
     * measured yet. */
    private var searchBodyBaselineHeight = -1
    private var dragStartRawY = 0f
    private var dragging = false

    /** True while the post-drag snap animation runs - keeps the sheet out
     * of full-height mode until it settles (the keyboard closing mid-snap
     * would otherwise re-apply full height and make it jump). */
    private var settlingDrag = false
    private var dragBaseHeight = 0
    private var dragRangeHeight = 0

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
                // Feeding a single-element list (vs. the plain-Location
                // overload) makes the location component ease the puck
                // from its last rendered spot to this one instead of
                // snapping - that snap-per-fix was the "map not smooth"
                // jitter during a drive.
                map?.locationComponent?.forceLocationUpdate(listOf(location), false)
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
                routeOptions = listOf(route)
                selectedRouteIndex = 0
                redrawRouteLines()
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

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { loadedMap ->
            map = loadedMap

            // Keep the compass, on the right below both the idle search
            // bar+Home/Work row and the active-nav turn banner - both span
            // the full width up top, so a left-side or shallow-margin spot
            // ends up hidden behind them. It's most useful once the nav
            // camera starts rotating the map, so it needs to actually be
            // visible during ACTIVE too.
            loadedMap.uiSettings.apply {
                isCompassEnabled = true
                setCompassGravity(Gravity.TOP or Gravity.END)
                val density = resources.displayMetrics.density
                setCompassMargins(0, (150 * density).toInt(), (16 * density).toInt(), 0)
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
                if (prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)) MAP_STYLE_URL_DARK else MAP_STYLE_URL
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
        val fullHeight = homeSheetState == HomeSheetState.SEARCH && !dragging && !settlingDrag
        val params = homeSheet.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null) {
            val topGap = (if (statusBarTop > 0) statusBarTop else dp(32)) + dp(8)
            val wantHeight = if (fullHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            val wantTop = if (fullHeight) topGap else 0
            if (params.height != wantHeight || params.topMargin != wantTop) {
                params.height = wantHeight
                params.topMargin = wantTop
                homeSheet.layoutParams = params
            }
        }
        val bottomPad = homeSheetBasePaddingBottom + if (fullHeight) lastImeBottom else 0
        homeSheet.setPadding(homeSheet.paddingLeft, homeSheet.paddingTop, homeSheet.paddingRight, bottomPad)
        homeSheet.translationY = if (fullHeight) 0f else -lastImeBottom.toFloat()

        topBar.setPadding(topBar.paddingLeft, topBar.paddingTop, topBar.paddingRight, lastImeBottom + dp(12))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setUpSettings() {
        findViewById<Switch>(R.id.voiceGuidanceSwitch).apply {
            isChecked = prefs.getBoolean(KEY_VOICE_ENABLED, true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(KEY_VOICE_ENABLED, isChecked).apply()
                navigationService?.setVoiceEnabled(isChecked)
            }
        }

        val isDark = prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)
        findViewById<RadioButton>(R.id.mapStyleLight).isChecked = !isDark
        findViewById<RadioButton>(R.id.mapStyleDark).isChecked = isDark

        findViewById<RadioGroup>(R.id.mapStyleGroup).setOnCheckedChangeListener { _, checkedId ->
            val wantDark = checkedId == R.id.mapStyleDark
            if (wantDark == prefs.getBoolean(KEY_DARK_MODE_ENABLED, false)) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, wantDark).apply()
            val loadedMap = map ?: return@setOnCheckedChangeListener
            applyMapStyle(loadedMap, if (wantDark) MAP_STYLE_URL_DARK else MAP_STYLE_URL, isRestyle = true)
        }
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
        turnDistanceText = findViewById(R.id.turnDistanceText)
        turnStreetText = findViewById(R.id.turnStreetText)
        etaSummaryText = findViewById(R.id.etaSummaryText)
        turnIcon = findViewById(R.id.turnIcon)
        speedBadge = findViewById(R.id.speedBadge)
        recenterButton = findViewById(R.id.recenterButton)
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

        homeSearchPill.setOnClickListener { setHomeSheetState(HomeSheetState.SEARCH) }
        homeSettingsButton.setOnClickListener { setHomeSheetState(HomeSheetState.SETTINGS) }
        homeSheetBackButton.setOnClickListener { setHomeSheetState(HomeSheetState.COLLAPSED) }
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
        findViewById<ImageButton>(R.id.stopNavigationButton).setOnClickListener {
            endActiveNavigation()
        }
        recenterButton.setOnClickListener { recenterCamera() }
        reportButton.setOnClickListener {
            showReportDialog()
        }
        findViewById<ImageButton>(R.id.bottomSearchButton).setOnClickListener {
            enterSearchFromNav()
        }
        findViewById<ImageButton>(R.id.bottomAltRouteButton).setOnClickListener {
            showRoutePreview()
        }
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

    /** Handles the home sheet's drag handle - lets the sheet be dragged
     * open/closed by hand (between COLLAPSED and SEARCH) instead of only
     * tapping the search pill/back arrow. SETTINGS has no drag - it's a
     * short fixed list, not something to "pull up more of", so it stays
     * tap-only (gear icon in, back arrow out), same as before. */
    private fun onHomeSheetDragTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = dragStartRawY - event.rawY // positive = finger moved up
                if (!dragging) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (kotlin.math.abs(deltaY) <= slop) return true
                    if (homeSheetState == HomeSheetState.SETTINGS) return false // no drag in settings
                    dragging = true
                    when (homeSheetState) {
                        HomeSheetState.COLLAPSED -> {
                            ensureSearchBodyBaselineMeasured()
                            dragRangeHeight = searchBodyBaselineHeight.coerceAtLeast(1)
                            dragBaseHeight = 0
                            beginDragOpenFromCollapsed()
                        }
                        HomeSheetState.SEARCH -> {
                            dragRangeHeight = homeSearchBody.height.coerceAtLeast(1)
                            dragBaseHeight = dragRangeHeight
                            // Shrink from full-screen back to a normal
                            // bottom sheet so dragging down visibly pulls
                            // the sheet's top edge down with the finger
                            // (applySheetInsets() skips full height while
                            // dragging is true).
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(homeSearchInput.windowToken, 0)
                            applySheetInsets()
                        }
                        HomeSheetState.SETTINGS -> return false
                    }
                }
                val target = (dragBaseHeight + deltaY.toInt()).coerceIn(0, dragRangeHeight)
                homeSearchBody.layoutParams.height = target
                homeSearchBody.alpha = target.toFloat() / dragRangeHeight
                homeSearchBody.requestLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    settleHomeSheetDrag()
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    /** Measured once (Home/Work rows only, before any search has run) and
     * reused as the open-from-collapsed drag range - cheap since it's just
     * an off-screen measure pass, not a real layout. */
    private fun ensureSearchBodyBaselineMeasured() {
        if (searchBodyBaselineHeight >= 0) return
        homeSearchBody.measure(
            View.MeasureSpec.makeMeasureSpec(homeSheet.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        searchBodyBaselineHeight = homeSearchBody.measuredHeight
    }

    /** Swaps the sheet's top row to the search/back-arrow layout right as a
     * drag-open begins (instant, not animated - only the body below grows
     * with the finger), without yet touching focus/keyboard - that happens
     * once the drag actually settles open, in setHomeSheetState(). */
    private fun beginDragOpenFromCollapsed() {
        homeSheetCollapsedRow.visibility = View.GONE
        homeSheetExpandedRow.visibility = View.VISIBLE
        homeSearchInputContainer.visibility = View.VISIBLE
        homeSheetExpandedTitle.visibility = View.GONE
        homeSearchBody.visibility = View.VISIBLE
        homeSearchBody.layoutParams.height = 0
        homeSearchBody.alpha = 0f
        homeSearchBody.requestLayout()
    }

    /** Animates the drag to whichever end it was closer to when released,
     * then hands off to setHomeSheetState() for the final, authoritative
     * visibility/focus/keyboard handling - same path the tap gestures use,
     * so a half-finished drag can never leave the sheet in a state tapping
     * wouldn't also reach. */
    private fun settleHomeSheetDrag() {
        val current = homeSearchBody.layoutParams.height.coerceIn(0, dragRangeHeight)
        val openEnough = current > dragRangeHeight / 2
        val target = if (openEnough) dragRangeHeight else 0
        settlingDrag = true
        ValueAnimator.ofInt(current, target).apply {
            duration = 180
            addUpdateListener { anim ->
                val value = anim.animatedValue as Int
                homeSearchBody.layoutParams.height = value
                homeSearchBody.alpha = value.toFloat() / dragRangeHeight
                homeSearchBody.requestLayout()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        settlingDrag = false
                        homeSearchBody.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        homeSearchBody.alpha = 1f
                        homeSearchBody.requestLayout()
                        setHomeSheetState(if (openEnough) HomeSheetState.SEARCH else HomeSheetState.COLLAPSED)
                    }
                },
            )
            start()
        }
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
        searchBackButton.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        activeSearchInput = searchInput
        activeSearchResultsList = searchResultsList
        searchInput.requestFocus()
    }

    /** Backs out of a search opened mid-drive without picking anything,
     * restoring the drive UI exactly as it was. */
    private fun exitSearchFromNav() {
        searchOpenedFromNav = false
        searchBackButton.visibility = View.GONE
        searchResultsList.visibility = View.GONE
        currentResults = emptyList()
        searchInput.setText("")
        searchInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        topBar.visibility = View.GONE

        if (navMode == NavMode.ACTIVE) {
            topNavBar.visibility = View.VISIBLE
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
        val navy = ContextCompat.getColor(this, R.color.navos_navy)
        fun style(
            button: ImageButton,
            on: Boolean,
            color: String,
        ) {
            button.backgroundTintList = ColorStateList.valueOf(if (on) Color.parseColor(color) else Color.WHITE)
            button.imageTintList = ColorStateList.valueOf(if (on) Color.WHITE else navy)
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
                        ColorStateList.valueOf(Color.parseColor(if (facility.listed) "#EAF7EF" else "#F1F3F6"))
                    setTextColor(Color.parseColor(if (facility.listed) "#1F9D55" else "#9AA4B5"))
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

        maybeSaveAsPendingPlace(result)

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
        loadedMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            enableLocationComponent(loadedMap, style)
            setUpRouteLayers(style)
            if (isRestyle) {
                redrawRouteLines()
                destinationLatLng?.let { showDestinationMarker(it) }
                renderPoiPins()
                if (incidentFeatures.isNotEmpty()) {
                    incidentsSource?.setGeoJson(FeatureCollection.fromFeatures(incidentFeatures))
                }
            } else {
                ensureLocationPermissionThenTrack()
            }
        }
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
            addBelowLabels(buildingsLayer)
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
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(routeCasingLayer)

        val layer =
            LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#1CB0F6")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        addBelowLabels(layer)

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
                    showRoutePreview()
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
        routeOptions = emptyList()
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
        navFollowing = true
        topBar.visibility = View.GONE
        recenterButton.visibility = View.GONE
        routePreviewCard.visibility = View.GONE
        // Only the chosen route stays on screen once driving - alternates
        // were just for picking, not useful mid-drive.
        altRouteSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

        activeRoute = route
        topNavBar.visibility = View.VISIBLE
        bottomNavBar.visibility = View.VISIBLE
        speedBadge.visibility = View.VISIBLE
        reportButton.visibility = View.VISIBLE
        poiToggleColumn.visibility = View.VISIBLE
        turnDistanceText.text = ""
        turnStreetText.text = "Starting navigation..."
        etaSummaryText.text =
            String.format(Locale.US, "%.0f min • %.1f km", route.durationSeconds / 60.0, route.distanceMeters / 1000.0)

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
        if (!navFollowing) return
        val rawBearing = if (location.hasBearing()) location.bearing.toDouble() else null
        val bearing = smoothedNavBearing(rawBearing, location)
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
            durationMs,
        )
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
            params.bottomMargin = (100 * density).toInt()
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
        activeRoute = null
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

        map?.locationComponent?.apply {
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.GPS
        }
        followingUser = true
        navFollowing = true

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
        val suffix = if (state.offRoute) " (off route)" else ""
        turnDistanceText.text = "$distanceLabel$suffix"
        turnStreetText.text = state.instruction

        val arrivalTime = Date(System.currentTimeMillis() + (state.durationRemainingSeconds * 1000).toLong())
        val arrivalLabel = SimpleDateFormat("h:mm a", Locale.US).format(arrivalTime)
        etaSummaryText.text =
            String.format(Locale.US, "%.0f min • %.1f km • %s", remainingMin, remainingKm, arrivalLabel)

        val (iconRes, rotation) = iconForManeuver(state.maneuverType, state.modifier)
        turnIcon.setImageResource(iconRes)
        turnIcon.rotation = rotation

        // "and then" look-ahead - only worth showing once the current turn
        // is close enough that the next one is relevant, same as Waze.
        val nextType = state.nextManeuverType
        if (nextType != null && state.distanceToNextTurnMeters <= 400 && !state.isArrival) {
            val (nextIconRes, nextRotation) = iconForManeuver(nextType, state.nextModifier)
            nextTurnIcon.setImageResource(nextIconRes)
            nextTurnIcon.rotation = nextRotation
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
        val validTint = ContextCompat.getColor(this, R.color.navos_navy)
        val dimBackground = ColorStateList.valueOf(0x33FFFFFF)
        val dimTint = 0x80FFFFFF.toInt()

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
                    setImageResource(R.drawable.ic_nav_arrow)
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
        // Same fix as the active-nav listener above - animate the puck
        // between fixes instead of snapping it.
        map?.locationComponent?.forceLocationUpdate(listOf(location), false)

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
        } else if (followingUser && navMode == NavMode.IDLE) {
            // Free-roam following, like Waze does before you've even
            // picked a destination - just re-centers, leaves the user's
            // current zoom/tilt/bearing alone. Route preview intentionally
            // skips this since it frames the whole route instead.
            map?.easeCamera(CameraUpdateFactory.newLatLng(LatLng(location.latitude, location.longitude)), 800)
        }
    }

    @SuppressLint("MissingPermission") // activation itself needs no permission; display does, gated by ensureLocationPermissionThenTrack()
    private fun enableLocationComponent(
        map: MapLibreMap,
        style: Style,
    ) {
        val locationComponent = map.locationComponent

        // Our own pro-designed arrow puck instead of MapLibre's default
        // plain dot, used both for the stationary icon and the heading
        // (bearing) icon - same drawable, it already points up.
        val componentOptions =
            LocationComponentOptions
                .builder(this)
                .foregroundDrawable(R.drawable.ic_location_puck)
                .bearingDrawable(R.drawable.ic_location_puck)
                .enableStaleState(false)
                .build()

        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions
                .builder(this, style)
                .locationComponentOptions(componentOptions)
                .useDefaultLocationEngine(false) // we feed it ourselves via LiveLocationTracker
                .build(),
        )
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.NONE // we drive the camera ourselves on first fix
        // GPS mode rotates the puck using the Location.bearing we already
        // feed it from LiveLocationTracker - more reliable than COMPASS
        // mode, which depends on the device's magnetometer instead.
        locationComponent.renderMode = RenderMode.GPS
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
        mapView.onDestroy()
        searchExecutor.shutdownNow()
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
