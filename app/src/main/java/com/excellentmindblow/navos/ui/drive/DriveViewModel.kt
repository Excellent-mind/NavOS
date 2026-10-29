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

    /** The route from where you are to the end (the line on the map), or null to show it all. */
    private val _ahead = MutableStateFlow<List<RoutePoint>?>(null)
    val ahead: StateFlow<List<RoutePoint>?> = _ahead.asStateFlow()
    private var aheadAt = Double.NaN
    private var lastFixP = Double.NaN

    /** The next turn's arrow on the route (points along the line through the turn) and the head's bearing. */
    data class TurnArrow(
        val line: List<RoutePoint>,
        val bearing: Double,
    )

    private val _turn = MutableStateFlow<TurnArrow?>(null)
    val turn: StateFlow<TurnArrow?> = _turn.asStateFlow()

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
        /** A speed camera: its limit (0 = not known), else null. */
        val cameraLimit: Int? = null,
        /** Over the camera's limit right now. */
        val over: Boolean = false,
    )

    /** Cameras on the route, for the map. */
    private val _cameras = MutableStateFlow<List<com.excellentmindblow.navos.traffic.SpeedCameras.OnRoute>>(emptyList())
    val cameras: StateFlow<List<com.excellentmindblow.navos.traffic.SpeedCameras.OnRoute>> = _cameras.asStateFlow()
    private val overWarned = HashSet<String>()

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
        overWarned.clear()
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
        _turn.value = null
        _ahead.value = null
        aheadAt = Double.NaN
        lastFixP = Double.NaN
        _state.value = null
        _route.value = null
        _alert.value = null
        _limit.value = null
        _reports.value = emptyList()
        _openLanes.value = emptyList()
        _cameras.value = emptyList()
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
        _ahead.value = null
        aheadAt = Double.NaN
        lastFixP = Double.NaN
        smartOnRoute = SmartLanes.onRoute(t, SmartLanes.lanes)
        com.excellentmindblow.navos.traffic.SpeedCameras.load(getApplication())
        _cameras.value = com.excellentmindblow.navos.traffic.SpeedCameras.onRoute(t, com.excellentmindblow.navos.traffic.SpeedCameras.all)
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

    /** The driven part and the turn arrow, from where you are on the route. */
    private fun updateLine(
        p: Double,
        segment: Int?,
    ) {
        val t = track ?: return
        if (p.isNaN() || segment == null) return
        val total = t.distanceAt(t.source.size - 1)
        // The line starts under the arrow. The map glides the arrow from the last fix to this one
        // over about a second, so cut where the arrow is coming from (the last fix, a few metres
        // back), not where it is going: the line then always reaches under the arrow and never
        // starts ahead of it. After a jump (GPS gap, reroute) cut at the new fix.
        val prevP = lastFixP
        lastFixP = p
        val cut = (if (!prevP.isNaN() && p - prevP in 0.0..CUT_FOLLOW_MAX_M) prevP else p) - CUT_BEHIND_M
        if (aheadAt.isNaN() || kotlin.math.abs(cut - aheadAt) >= 1.0) {
            aheadAt = cut
            val g = t.source
            // First route point past the cut.
            var lo = 0
            var hi = g.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (t.distanceAt(mid) <= cut) lo = mid + 1 else hi = mid
            }
            val next = lo.coerceIn(1, g.size - 1)
            _ahead.value = listOf(t.pointAt(cut.coerceAtLeast(0.0))) + g.subList(next, g.size)
        }
        val s = _state.value
        val toTurn = s?.distanceToNextTurnMeters
        if (s == null || toTurn == null || s.offRoute || toTurn > TURN_ARROW_FROM_M ||
            !com.excellentmindblow.navos.navigation.DriveCamera.isRealTurn(s.maneuverType, s.modifier)
        ) {
            _turn.value = null
            return
        }
        val at = p + toTurn
        val from = maxOf(p, at - TURN_ARROW_BEFORE_M)
        val to = minOf(total, at + TURN_ARROW_AFTER_M)
        if (to - from < 8) {
            _turn.value = null
            return
        }
        val pts = ArrayList<RoutePoint>()
        var d = from
        while (d < to) {
            pts += t.pointAt(d)
            d += 3.0
        }
        pts += t.pointAt(to)
        _turn.value = TurnArrow(pts, t.bearingAt(to - 4.0, 4.0))
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
        val mapLimit = limits.limitAt(here)
        _limit.value = mapLimit
        val snap = snapOf(l)
        val p = snap?.let { track?.progressOf(it) } ?: Double.NaN
        _shown.value = onRoute(l, snap, p)
        updateLine(p, snap?.segmentIndex)
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
        // Speed camera ahead: first, it costs money. Its limit shows when the map has none.
        val cam = com.excellentmindblow.navos.traffic.SpeedCameras.nextToWarn(_cameras.value, p)
        val speedKmh = if (l.hasSpeed()) l.speed * 3.6 else 0.0
        val camLimit = cam?.camera?.limitKmh ?: mapLimit
        if (cam != null && mapLimit == null && cam.camera.limitKmh != null) _limit.value = cam.camera.limitKmh
        val camOver = cam != null && camLimit != null && speedKmh > camLimit + 3
        _alert.value =
            when {
                cam != null -> {
                    val c = cam.camera
                    val ahead = cam.atM - p
                    Alert(
                        "camera-${c.latitude}-${c.longitude}",
                        RoadReports.Kind.HAZARD,
                        if (c.redLight) tr("Red-light camera", "Kamera lampu merah") else tr("Speed camera", "Kamera laju"),
                        when {
                            camOver -> tr("Slow down - you're over the limit", "Perlahan - anda melebihi had")
                            c.limitKmh != null -> tr("AES · limit ${c.limitKmh} km/h", "AES · had ${c.limitKmh} km/j")
                            else -> tr("AES ahead", "AES di depan")
                        },
                        ahead.coerceAtLeast(0.0),
                        cameraLimit = c.limitKmh ?: 0,
                        over = camOver,
                    )
                }
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
            // Over the limit near a camera: one more, firmer warning.
            if (a.cameraLimit != null && a.over && a.distanceM < 400 && overWarned.add(a.key)) {
                service?.announce(a.key + "-over", tr("Slow down. Speed camera ahead.", "Perlahan. Kamera laju di depan."))
            }
            if (alerted.add(a.key)) {
                val spoken =
                    if (a.cameraLimit != null) {
                        if (a.cameraLimit > 0) {
                            tr("Speed camera ahead. Limit ${a.cameraLimit}.", "Kamera laju di depan. Had ${a.cameraLimit}.")
                        } else {
                            tr("Speed camera ahead.", "Kamera laju di depan.")
                        }
                    } else if (a.kind == RoadReports.Kind.SMART_LANE) {
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

        /** The line is cut this far behind where the arrow comes from, so it always runs under the arrow. */
        const val CUT_BEHIND_M = 3.0

        /** Further than this between two fixes is a jump, not driving: cut at the new fix. */
        const val CUT_FOLLOW_MAX_M = 80.0

        /** The turn arrow shows from this far before the turn, and covers this much each side of it. */
        const val TURN_ARROW_FROM_M = 400.0
        const val TURN_ARROW_BEFORE_M = 45.0
        const val TURN_ARROW_AFTER_M = 25.0
    }
}
