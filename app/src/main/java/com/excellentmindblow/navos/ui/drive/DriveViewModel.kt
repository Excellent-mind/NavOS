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
            }

            override fun onStopReached(stop: RouteStop) {}

            override fun onFasterRoute(
                route: Route,
                savedSeconds: Double,
            ) {
                // Phase 5: offer it. For now take it, like Waze does when the saving is real.
                service?.acceptFasterRoute(route)
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
        _state.value = null
        _route.value = null
        _alert.value = null
        _limit.value = null
        _reports.value = emptyList()
        _openLanes.value = emptyList()
        reportsJob?.cancel()
        track = null
        unbind()
    }

    private fun setTrack(route: Route) {
        val t = RouteTrack(route.geometry)
        track = t
        smartOnRoute = SmartLanes.onRoute(t, SmartLanes.lanes)
    }

    /** How far along the route [l] is, or NaN off it. */
    private fun progressOf(l: Location): Double {
        val t = track ?: return Double.NaN
        val snap = GeoUtils.snapToPolyline(RoutePoint(l.latitude, l.longitude), t.source) ?: return Double.NaN
        return if (snap.distanceMeters > 60) Double.NaN else t.progressOf(snap)
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
                    delay(120_000)
                }
            }
    }

    /** Every fix while driving: speed limit, and what to warn about ahead. */
    private fun onFix(l: Location) {
        val here = RoutePoint(l.latitude, l.longitude)
        limits.onLocation(here)
        _limit.value = limits.limitAt(here)
        val p = progressOf(l)
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
}
