package com.excellentmindblow.navos.ui.drive

import android.content.Context
import android.location.Location
import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.poi.Poi
import com.excellentmindblow.navos.poi.PoiAlongRoute
import com.excellentmindblow.navos.poi.PoiCache
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.poi.PoiService
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.signs.SignColour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Petrol, EV, parking and transit during a drive.
 *
 * Petrol and EV: only stations on the way - near the route ahead, and on an
 * expressway only the R&R on your side ([PoiAlongRoute]). Looked up around a
 * point a few km up the road, again every 2 km.
 *
 * Parking and transit: around the destination, looked up once.
 */
class OnTheWay(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    /** A pin on the map. [spot] for stations on the way; [fromDestinationM] for ones near the destination. */
    data class Pin(
        val poi: Poi,
        val spot: PoiAlongRoute.Spot?,
        val fromDestinationM: Double?,
    )

    private val _kinds = MutableStateFlow<Set<PoiKind>>(emptySet())
    val kinds: StateFlow<Set<PoiKind>> = _kinds.asStateFlow()

    private val _pins = MutableStateFlow<List<Pin>>(emptyList())
    val pins: StateFlow<List<Pin>> = _pins.asStateFlow()

    private val _loading = MutableStateFlow<Set<PoiKind>>(emptySet())
    val loading: StateFlow<Set<PoiKind>> = _loading.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private var route: Route? = null
    private var track: RouteTrack? = null
    private var here = Double.NaN
    private var hereSegment = 0

    private var nearby: List<Poi> = emptyList() // fuel + EV
    private var atDestination: Map<PoiKind, List<Poi>> = emptyMap()
    private var lastFetch: Location? = null
    private var lastFetchAt = 0L
    private var fetchJob: Job? = null
    private var lastPinsAt = Double.NaN

    // Which stretches are expressway: where each step starts, and whether its road is one.
    private var stepStarts = DoubleArray(0)
    private var stepExpressway = BooleanArray(0)

    fun start(r: Route) {
        route = r
        track = RouteTrack(r.geometry)
        atDestination = emptyMap()
        nearby = emptyList()
        lastFetch = null
        lastFetchAt = 0L
        lastPinsAt = Double.NaN
        here = Double.NaN
        indexExpressway(r)
        refreshPins()
    }

    /** After a reroute: the stations stay, their place on the new route is recomputed. */
    fun rerouted(r: Route) {
        val destMoved = route?.geometry?.lastOrNull()?.let { a -> r.geometry.lastOrNull()?.let { b -> GeoUtils.distanceMeters(a, b) > 200 } } ?: true
        route = r
        track = RouteTrack(r.geometry)
        if (destMoved) atDestination = emptyMap()
        indexExpressway(r)
        lastPinsAt = Double.NaN
        refreshPins()
        if (destMoved) _kinds.value.filter { it == PoiKind.PARKING || it == PoiKind.TRANSIT }.forEach { fetchDestination(it) }
    }

    fun stop() {
        fetchJob?.cancel()
        route = null
        track = null
        _kinds.value = emptySet()
        _pins.value = emptyList()
        _selected.value = null
        _loading.value = emptySet()
    }

    fun toggle(kind: PoiKind) {
        val on = kind !in _kinds.value
        _kinds.value = if (on) _kinds.value + kind else _kinds.value - kind
        if (!on) {
            if (_pins.value.firstOrNull { it.poi.id == _selected.value }?.poi?.kind == kind) _selected.value = null
            refreshPins()
            return
        }
        when (kind) {
            PoiKind.FUEL, PoiKind.EV -> if (nearby.isEmpty()) fetchAhead(force = true) else refreshPins()
            PoiKind.PARKING, PoiKind.TRANSIT -> if (atDestination[kind] == null) fetchDestination(kind) else refreshPins()
        }
    }

    fun select(id: String?) {
        _selected.value = id
    }

    /** Every fix: where we are on the route; drop passed stations, look further ahead. */
    fun onFix(
        l: Location,
        progressM: Double,
        segment: Int,
    ) {
        here = progressM
        hereSegment = segment
        if (!progressM.isNaN() && (lastPinsAt.isNaN() || kotlin.math.abs(progressM - lastPinsAt) > 150.0)) refreshPins()
        if (PoiKind.FUEL in _kinds.value || PoiKind.EV in _kinds.value) {
            val moved = lastFetch?.distanceTo(l) ?: Float.MAX_VALUE
            if (moved > REFETCH_M && System.currentTimeMillis() - lastFetchAt > MIN_REFETCH_MS) fetchAhead(force = false, at = l)
        }
    }

    /** The next station of the same kind after [pin], or how far ahead stations are known when there is none. */
    fun nextAfter(pin: Pin): PoiAlongRoute.Next? {
        val spot = pin.spot ?: return null
        val others = _pins.value.filter { it.poi.kind == pin.poi.kind && it.poi.id != pin.poi.id }.mapNotNull { it.spot?.aheadM }
        return PoiAlongRoute.nextAfter(spot.aheadM.coerceAtLeast(0.0), others, LOOK_AHEAD_M + PoiService.RADIUS_METERS)
    }

    fun nameOfPinAt(
        kind: PoiKind,
        aheadM: Double,
    ): Poi? = _pins.value.firstOrNull { it.poi.kind == kind && it.spot?.aheadM == aheadM }?.poi

    private fun fetchAhead(
        force: Boolean,
        at: Location? = null,
    ) {
        if (fetchJob?.isActive == true && !force) return
        val t = track ?: return
        val centre = if (!here.isNaN()) t.pointAt(here + LOOK_AHEAD_M) else t.pointAt(minOf(LOOK_AHEAD_M, t.distanceAt(t.source.size - 1)))
        lastFetchAt = System.currentTimeMillis()
        if (at != null) lastFetch = Location(at)
        _loading.value = _loading.value + setOf(PoiKind.FUEL, PoiKind.EV).intersect(_kinds.value)
        fetchJob =
            scope.launch {
                val found =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            PoiCache.loadFuelEv(context.cacheDir, centre.latitude, centre.longitude)
                                ?: PoiService.fetchNearby(centre.latitude, centre.longitude).also { PoiCache.saveFuelEv(context.cacheDir, centre.latitude, centre.longitude, it) }
                        }.getOrNull()
                    }
                _loading.value = _loading.value - PoiKind.FUEL - PoiKind.EV
                if (found != null && track === t) {
                    // Keep the ones still ahead from before, so a station doesn't blink out between lookups.
                    nearby = (found + nearby).distinctBy { it.id }
                    refreshPins()
                } else if (found == null) {
                    lastFetchAt = System.currentTimeMillis() - MIN_REFETCH_MS + 30_000 // try again in 30 s
                }
            }
    }

    private fun fetchDestination(kind: PoiKind) {
        val dest = route?.geometry?.lastOrNull() ?: return
        _loading.value = _loading.value + kind
        scope.launch {
            val found =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (kind == PoiKind.PARKING) PoiService.fetchParking(dest.latitude, dest.longitude) else PoiService.fetchTransit(dest.latitude, dest.longitude)
                    }.getOrNull()
                }
            _loading.value = _loading.value - kind
            atDestination = atDestination + (kind to (found ?: emptyList()))
            refreshPins()
        }
    }

    private fun refreshPins() {
        val t = track
        val r = route
        if (t == null || r == null) {
            _pins.value = emptyList()
            return
        }
        lastPinsAt = here
        val kinds = _kinds.value
        val dest = r.geometry.last()
        val onWay =
            nearby
                .filter { it.kind in kinds }
                .mapNotNull { p ->
                    PoiAlongRoute.spot(t, RoutePoint(p.latitude, p.longitude), here, hereSegment - 5) { onExpressway(t, it) }?.let { Pin(p, it, null) }
                }.sortedBy { it.spot!!.aheadM }
                .take(MAX_ON_WAY)
        val near =
            atDestination
                .filterKeys { it in kinds }
                .values
                .flatten()
                .map { Pin(it, null, GeoUtils.distanceMeters(dest, RoutePoint(it.latitude, it.longitude))) }
                .sortedBy { it.fromDestinationM }
                .take(MAX_NEAR_DEST)
        _pins.value = onWay + near
        if (_selected.value != null && _pins.value.none { it.poi.id == _selected.value }) _selected.value = null
    }

    private fun indexExpressway(r: Route) {
        val t = track ?: return
        var seg = 0
        val starts = ArrayList<Double>()
        val flags = ArrayList<Boolean>()
        for (step in r.steps) {
            val snap = GeoUtils.snapToPolyline(step.point, r.geometry, seg) ?: continue
            seg = snap.segmentIndex
            starts += t.progressOf(snap)
            flags += SignColour.isExpressway(step.ref) || EXPRESSWAY_NAME.containsMatchIn(step.roadName)
        }
        stepStarts = starts.toDoubleArray()
        stepExpressway = flags.toBooleanArray()
    }

    private fun onExpressway(
        t: RouteTrack,
        progressM: Double,
    ): Boolean {
        if (t !== track) return false
        val i = stepStarts.indexOfLast { it <= progressM }.coerceAtLeast(0)
        return stepExpressway.getOrElse(i) { false }
    }

    companion object {
        /** Look this far up the road for stations (the search circle's centre). */
        const val LOOK_AHEAD_M = 4000.0
        private const val REFETCH_M = 2000f
        private const val MIN_REFETCH_MS = 60_000L
        private const val MAX_ON_WAY = 12
        private const val MAX_NEAR_DEST = 15
        private val EXPRESSWAY_NAME = Regex("(?i)\\b(lebuhraya|expressway|highway)\\b")
    }
}
