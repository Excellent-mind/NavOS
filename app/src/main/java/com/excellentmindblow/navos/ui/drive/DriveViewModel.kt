package com.excellentmindblow.navos.ui.drive

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RouteStop
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.navigation.SpeedLimitTracker
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.traffic.RoadReports
import com.excellentmindblow.navos.traffic.SmartLanes
import com.excellentmindblow.navos.traffic.TrafficClient
import com.excellentmindblow.navos.traffic.TrafficFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * The drive: starts NavigationService (which owns the engine, GPS, voice and
 * rerouting, and keeps going with the screen off) and mirrors what it says
 * into state for the drive screen.
 */
class DriveViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<NavigationState?>(null)
    val state: StateFlow<NavigationState?> = _state.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    /** Where the arrow is drawn: on the route line, heading along it, while you're on the route; else the GPS fix. */
    private val _shown = MutableStateFlow<Location?>(null)
    val shown: StateFlow<Location?> = _shown.asStateFlow()

    /** The drive ended by itself (arrived) or from the notification. */
    private val _ended = MutableStateFlow(false)
    val ended: StateFlow<Boolean> = _ended.asStateFlow()

    private val _voiceOn = MutableStateFlow(true)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()

    /** Something on the road ahead to show under the sign: a report, or an open Smart Lane. */
    data class Alert(
        val key: String,
        val kind: RoadReports.Kind,
        val title: String,
        val detail: String,
        val distanceM: Double,
    )

    private val _alert = MutableStateFlow<Alert?>(null)
    val alert: StateFlow<Alert?> = _alert.asStateFlow()

    /** Speed limit of the road you're on (km/h), when the map has one. */
    private val _limit = MutableStateFlow<Int?>(null)
    val limit: StateFlow<Int?> = _limit.asStateFlow()

    /** Reports on the route (for pins) and the Smart Lanes open on it now (painted green). */
    private val _reports = MutableStateFlow<List<RoadReports.OnRoute>>(emptyList())
    val reports: StateFlow<List<RoadReports.OnRoute>> = _reports.asStateFlow()

    private val _openLanes = MutableStateFlow<List<SmartLanes.Lane>>(emptyList())
    val openLanes: StateFlow<List<SmartLanes.Lane>> = _openLanes.asStateFlow()

    /** Petrol / EV on the way, parking / transit at the destination. */
    val onTheWay = OnTheWay(app, viewModelScope)

    /** Slow and jammed stretches ahead, from live traffic (empty without data). */
    data class TrafficRun(
        val points: List<RoutePoint>,
        val jam: Boolean,
    )

    private val _traffic = MutableStateFlow<List<TrafficRun>>(emptyList())
    val traffic: StateFlow<List<TrafficRun>> = _traffic.asStateFlow()

    /** A faster way found mid-drive, waiting for the driver to take it or not. */
    data class Faster(
        val route: Route,
        val savedMin: Int,
    )

    private val _faster = MutableStateFlow<Faster?>(null)
    val faster: StateFlow<Faster?> = _faster.asStateFlow()
    private var fasterTimeout: Job? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val limits = SpeedLimitTracker({ io.execute(it) }, { main.post(it) }, System::currentTimeMillis, { _route.value != null })
    private var track: RouteTrack? = null
    private var smartOnRoute: List<SmartLanes.OnRoute> = emptyList()
    private val alerted = HashSet<String>()
    private var reportsJob: Job? = null

    private var service: NavigationService? = null
    private var bound = false
    private var pending: Route? = null

    private val listener =
        object : NavigationService.Listener {
            override fun onNavLocation(location: Location) {
                _location.value = location
                onFix(location)
            }

            override fun onNavState(state: NavigationState) {
                _state.value = state
            }

            override fun onNavigationEnded() {
                _ended.value = true
            }

            override fun onRerouted(route: Route) {
                _route.value = route
                setTrack(route)
                onTheWay.rerouted(route)
                _faster.value = null
                refreshTraffic()
            }

            override fun onStopReached(stop: RouteStop) {}

            override fun onFasterRoute(
                route: Route,
                savedSeconds: Double,
            ) {
                val min = Math.round(savedSeconds / 60.0).toInt().coerceAtLeast(1)
                _faster.value = Faster(route, min)
                // Not answered in 20 s: keep the route you're on.
                fasterTimeout?.cancel()
                fasterTimeout = viewModelScope.launch { delay(20_000); _faster.value = null }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val s = (binder as NavigationService.LocalBinder).getService()
                service = s
                s.setListener(listener)
                pending?.let {
                    s.startNavigation(it)
                    pending = null
                }
                s.currentRoute()?.let { _route.value = it }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

    fun start(route: Route) {
        val ctx = getApplication<Application>()
        _route.value = route
        SmartLanes.load(ctx)
        setTrack(route)
        alerted.clear()
        limits.reset()
        startReports()
        arrivalName = null
        onTheWay.start(route)
        _traffic.value = emptyList()
        _faster.value = null
        _state.value = null
        _ended.value = false
        val intent = Intent(ctx, NavigationService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
        val s = service
        if (s != null) {
            s.startNavigation(route)
            return
        }
        pending = route
        if (!bound) {
            bound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun end() {
        service?.stopNavigation()
        _shown.value = null
        _state.value = null
        _route.value = null
        _alert.value = null
        _limit.value = null
        _reports.value = emptyList()
        _openLanes.value = emptyList()
        reportsJob?.cancel()
        onTheWay.stop()
        _traffic.value = emptyList()
        _faster.value = null
        track = null
        unbind()
    }

    private fun setTrack(route: Route) {
        val t = RouteTrack(route.geometry)
        track = t
        smartOnRoute = SmartLanes.onRoute(t, SmartLanes.lanes)
    }

    /** How far along the route [l] is, or NaN off it. */
    private fun progressOf(l: Location): Double = snapOf(l)?.let { track?.progressOf(it) } ?: Double.NaN

    private fun snapOf(l: Location): GeoUtils.RouteSnap? {
        val t = track ?: return null
        val snap = GeoUtils.snapToPolyline(RoutePoint(l.latitude, l.longitude), t.source) ?: return null
        return if (snap.distanceMeters > 60) null else snap
    }

    /**
     * The fix moved onto the route line (GPS wanders 5-15 m; on the route you are
     * on the road), pointing along it - like every car navigator does. Off the
     * route, or when the state says off-route, the real fix.
     */
    private fun onRoute(
        l: Location,
        snap: GeoUtils.RouteSnap?,
        progressM: Double,
    ): Location {
        val t = track ?: return l
        if (snap == null || progressM.isNaN() || snap.distanceMeters > SNAP_TO_ROUTE_M || _state.value?.offRoute == true) return l
        val p = t.pointAt(progressM)
        return Location(l).apply {
            latitude = p.latitude
            longitude = p.longitude
            bearing = t.bearingAt(progressM, 25.0).toFloat()
        }
    }

    /** Live traffic on the route ahead: amber where slow, red where jammed. */
    private fun refreshTraffic() {
        val r = _route.value ?: return
        val nodes = r.nodes ?: return
        val free = r.freeFlowMs ?: return
        val from = _location.value?.let { snapOf(it)?.segmentIndex } ?: 0
        viewModelScope.launch {
            val live = withContext(Dispatchers.IO) { runCatching { TrafficClient.flow(TrafficFlow.piecesAhead(nodes, from)) }.getOrNull() } ?: return@launch
            if (_route.value !== r) return@launch
            _traffic.value =
                TrafficFlow.runs(nodes, free, live, from).map { run ->
                    TrafficRun(r.geometry.subList(run.from, (run.to + 1).coerceAtMost(r.geometry.size)), run.level == TrafficFlow.Level.JAM)
                }
        }
    }

    /** The place the drive now ends at, when the driver changed it mid-drive (Go here). */
    var arrivalName: String? = null
        private set

    /** Take the faster way offered. */
    fun takeFaster() {
        val f = _faster.value ?: return
        _faster.value = null
        fasterTimeout?.cancel()
        service?.acceptFasterRoute(f.route)
        _route.value = f.route
        setTrack(f.route)
        onTheWay.rerouted(f.route)
        refreshTraffic()
    }

    fun keepRoute() {
        _faster.value = null
        fasterTimeout?.cancel()
    }

    /** Stop at [poi] on the way, in front of the destination. */
    fun stopAt(poi: com.excellentmindblow.navos.poi.Poi) {
        val r = _route.value ?: return
        service?.addStop(RouteStop(RoutePoint(poi.latitude, poi.longitude), poi.name, r.stops.size + 1))
        onTheWay.select(null)
    }

    /** Drive to [poi] instead (a car park or station at the destination): a fresh route from here. */
    fun goTo(
        poi: com.excellentmindblow.navos.poi.Poi,
        onFailed: () -> Unit,
    ) {
        val l = _location.value ?: return onFailed()
        onTheWay.select(null)
        arrivalName = poi.name.ifBlank { null }
        viewModelScope.launch {
            val routes = withContext(Dispatchers.IO) { runCatching { com.excellentmindblow.navos.routing.RoutingService.calculateRoutes(RoutePoint(l.latitude, l.longitude), RoutePoint(poi.latitude, poi.longitude)) }.getOrNull() }
            val best = routes?.firstOrNull() ?: return@launch onFailed()
            service?.startNavigation(best)
            _route.value = best
            setTrack(best)
            onTheWay.rerouted(best)
            refreshTraffic()
        }
    }

    /** Road reports along the route, every 2 minutes. */
    private fun startReports() {
        reportsJob?.cancel()
        reportsJob =
            viewModelScope.launch {
                while (isActive) {
                    val t = track
                    val l = _location.value
                    if (t != null) {
                        val from = l?.let { progressOf(it) }?.takeIf { !it.isNaN() } ?: 0.0
                        val box = RoadReports.boxAhead(t, from)
                        if (box != null) {
                            val found = withContext(Dispatchers.IO) { runCatching { RoadReports.fetch(box) }.getOrNull() }
                            if (found != null && track === t) _reports.value = RoadReports.onRoute(t, found)
                        }
                    }
                    // Smart Lanes load in the background; place them once they are in.
                    if (smartOnRoute.isEmpty()) track?.let { smartOnRoute = SmartLanes.onRoute(it, SmartLanes.lanes) }
                    refreshTraffic()
                    delay(120_000)
                }
            }
    }

    /** Every fix while driving: speed limit, and what to warn about ahead. */
    private fun onFix(l: Location) {
        val here = RoutePoint(l.latitude, l.longitude)
        limits.onLocation(here)
        _limit.value = limits.limitAt(here)
        val snap = snapOf(l)
        val p = snap?.let { track?.progressOf(it) } ?: Double.NaN
        _shown.value = onRoute(l, snap, p)
        onTheWay.onFix(l, p, snap?.segmentIndex ?: 0)
        if (p.isNaN()) {
            _alert.value = null
            return
        }
        val now = System.currentTimeMillis()
        // Smart Lanes open now (schedule, dated, or a driver's report along them).
        val open =
            smartOnRoute.filter { s ->
                SmartLanes.isOpen(s.lane, now) || _reports.value.any { it.report.kind == RoadReports.Kind.SMART_LANE && it.atM in (s.startM - 300.0)..s.endM }
            }
        if (open.map { it.lane.id } != _openLanes.value.map { it.id }) _openLanes.value = open.map { it.lane }

        val report = RoadReports.nextAhead(_reports.value, p, 800.0)?.takeIf { it.report.kind != RoadReports.Kind.SMART_LANE }
        val lane = open.firstOrNull { p in (it.startM - 2_000.0)..it.endM }
        _alert.value =
            when {
                report != null ->
                    Alert("report-${report.report.id}", report.report.kind, report.report.kind.label, RoadReports.detail(report.report), report.atM - p)
                lane != null -> {
                    val on = p >= lane.startM
                    val until = SmartLanes.openUntil(lane.lane, now)?.let { m -> String.format(java.util.Locale.US, "%d:%02d", ((m / 60) % 12).let { h -> if (h == 0) 12 else h }, m % 60) }
                    Alert(
                        "smartlane-${lane.lane.id}",
                        RoadReports.Kind.SMART_LANE,
                        if (on) tr("On the Smart lane", "Atas Smart lane") else RoadReports.Kind.SMART_LANE.label,
                        listOfNotNull(tr("Left emergency lane", "Lorong kecemasan kiri"), tr("max ${lane.lane.speedKmh} km/h", "had ${lane.lane.speedKmh} km/j"), until?.let { tr("until $it", "sampai $it") }).joinToString(" · "),
                        (if (on) lane.endM else lane.startM) - p,
                    )
                }
                else -> null
            }
        _alert.value?.let { a ->
            if (alerted.add(a.key)) {
                val spoken =
                    if (a.kind == RoadReports.Kind.SMART_LANE) {
                        tr("Smart lane open ahead. You may use the left emergency lane.", "Smart lane dibuka di depan. Boleh guna lorong kecemasan kiri.")
                    } else {
                        a.kind.spoken
                    }
                service?.announce(a.key, spoken)
            }
        }
    }

    fun setVoice(on: Boolean) {
        _voiceOn.value = on
        service?.setVoiceEnabled(on)
    }

    private fun unbind() {
        if (bound) {
            service?.setListener(null)
            runCatching { getApplication<Application>().unbindService(connection) }
            bound = false
        }
        service = null
    }

    override fun onCleared() = unbind()

    private companion object {
        /** Further than this from the line, the arrow shows the real fix (you may have left the route). */
        const val SNAP_TO_ROUTE_M = 35.0
    }
}
