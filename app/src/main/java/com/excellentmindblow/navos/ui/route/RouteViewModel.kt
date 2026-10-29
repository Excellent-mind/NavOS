package com.excellentmindblow.navos.ui.route

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.excellentmindblow.navos.NavFormat
import com.excellentmindblow.navos.navigation.TripStrip
import com.excellentmindblow.navos.routing.BundledTolls
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.routing.TollEstimate
import com.excellentmindblow.navos.routing.TollFares
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One route as the cards show it. */
data class RouteOption(
    val route: Route,
    /** Main road name and its number ("Lebuhraya Persekutuan", "2"). */
    val via: String?,
    val viaRef: String?,
    /** Toll plazas passed (null = not known yet), and what they cost (null = can't price). */
    val tolls: Int,
    val toll: TollEstimate?,
    val strip: TripStrip.Strip?,
    /** Speed / red-light cameras on this route (AES / AwAS). */
    val cameras: Int = 0,
)

/** Route choice: asks the routing engine, then works out tolls, prices and the trip strip for each route. */
class RouteViewModel(
    app: Application,
) : AndroidViewModel(app) {
    sealed interface State {
        data object Idle : State

        data object Loading : State

        data class Ready(
            val options: List<RouteOption>,
        ) : State

        data class Failed(
            val offline: Boolean,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private var job: Job? = null
    private var lastKey: String? = null

    init {
        TollFares.load(app) { refreshPrices() }
        com.excellentmindblow.navos.traffic.SpeedCameras.load(app)
    }

    fun select(i: Int) {
        _selected.value = i
    }

    fun selectedRoute(): Route? = (state.value as? State.Ready)?.options?.getOrNull(selected.value)?.route

    /** Plans from [from] to [to]; does nothing when the same trip is already planned. */
    fun plan(
        from: RoutePoint,
        to: RoutePoint,
        force: Boolean = false,
    ) {
        val key = "${"%.4f".format(from.latitude)},${"%.4f".format(from.longitude)}>${to.latitude},${to.longitude}"
        if (!force && key == lastKey && state.value is State.Ready) return
        lastKey = key
        job?.cancel()
        _state.value = State.Loading
        _selected.value = 0
        job =
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            BundledTolls.ensureLoaded(getApplication())
                            RoutingService.calculateRoutes(from, to).take(4).map(::option)
                        }
                    }
                _state.value =
                    result.fold(
                        onSuccess = { if (it.isEmpty()) State.Failed(offline = false) else State.Ready(it) },
                        onFailure = { State.Failed(offline = it is java.io.IOException) },
                    )
            }
    }

    fun clear() {
        job?.cancel()
        lastKey = null
        _state.value = State.Idle
    }

    private fun option(route: Route): RouteOption {
        val plazas = BundledTolls.along(route)
        val cams = com.excellentmindblow.navos.traffic.SpeedCameras.onRoute(com.excellentmindblow.navos.navigation.RouteTrack(route.geometry), com.excellentmindblow.navos.traffic.SpeedCameras.all)
        val strip = TripStrip.build(route, plazas + com.excellentmindblow.navos.traffic.SpeedCameras.asRoadPois(cams), emptyList(), emptyList())
        val main = route.steps.filter { it.roadName.isNotBlank() }.groupBy { it.roadName }.maxByOrNull { (_, s) -> s.sumOf { it.distanceMeters } }
        val ref =
            main?.value?.firstNotNullOfOrNull { it.ref?.split(';')?.firstOrNull()?.trim()?.takeIf { r -> r.isNotBlank() } }
                ?: route.steps.filter { !it.ref.isNullOrBlank() }.maxByOrNull { it.distanceMeters }?.ref?.split(';')?.first()?.trim()
        val tollMarks = strip?.marks?.filter { it.kind == TripStrip.Kind.TOLL }.orEmpty()
        return RouteOption(
            route = route,
            via = main?.key ?: NavFormat.viaRoadName(route),
            viaRef = ref,
            tolls = tollMarks.size,
            toll = price(tollMarks),
            strip = strip,
            cameras = cams.size,
        )
    }

    /** Prices the plazas in route order; a merged plaza is priced by the first of its names LLM knows. */
    private fun price(marks: List<TripStrip.Mark>): TollEstimate? {
        if (marks.isEmpty()) return TollEstimate(0, 0)
        val pricer = TollFares.pricer ?: return null
        val names = marks.map { m -> (listOf(m.name) + m.otherNames).firstOrNull { pricer.match(it).isNotEmpty() } ?: m.name }
        return pricer.price(names)
    }

    private fun refreshPrices() {
        val s = state.value as? State.Ready ?: return
        _state.value = State.Ready(s.options.map { o -> o.copy(toll = price(o.strip?.marks?.filter { it.kind == TripStrip.Kind.TOLL }.orEmpty())) })
    }
}
