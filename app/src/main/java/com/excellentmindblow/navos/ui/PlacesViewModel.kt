package com.excellentmindblow.navos.ui

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.excellentmindblow.navos.MainActivity
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import com.excellentmindblow.navos.search.RecentPlaces
import com.excellentmindblow.navos.search.SavedPlaces
import com.excellentmindblow.navos.search.SearchPicks
import com.excellentmindblow.navos.search.TripHabits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Search results and the places Home shows (Home / Work, recents, the likely trip now). */
class PlacesViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val saved = SavedPlaces(prefs)
    private val recentStore = RecentPlaces(prefs)
    private val picks = SearchPicks(prefs)
    private val habits = TripHabits(prefs)

    data class Shortcuts(
        val home: GeocodeResult?,
        val work: GeocodeResult?,
    )

    private val _shortcuts = MutableStateFlow(loadShortcuts())
    val shortcuts: StateFlow<Shortcuts> = _shortcuts.asStateFlow()

    private val _recents = MutableStateFlow(recentStore.load())
    val recents: StateFlow<List<GeocodeResult>> = _recents.asStateFlow()

    private val _predicted = MutableStateFlow<GeocodeResult?>(null)
    val predicted: StateFlow<GeocodeResult?> = _predicted.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val results: StateFlow<List<GeocodeResult>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** What a pick in search is for: going there, or setting Home / Work. */
    enum class Purpose { GO, SET_HOME, SET_WORK }

    var purpose = Purpose.GO

    private var searchJob: Job? = null
    private var here: Location? = null

    fun onLocation(l: Location?) {
        here = l
        _predicted.value = runCatching { habits.predict(l?.let { RoutePoint(it.latitude, it.longitude) }) }.getOrNull()
    }

    fun setQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        // Wait for a pause in typing, then search near you.
        searchJob =
            viewModelScope.launch {
                delay(250)
                _searching.value = true
                val found =
                    withContext(Dispatchers.IO) {
                        runCatching { GeocodingService.searchLive(q, here?.latitude, here?.longitude) }.getOrDefault(emptyList())
                    }
                _results.value = picks.boost(found, q)
                _searching.value = false
            }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _results.value = emptyList()
        _searching.value = false
    }

    /** A place was picked: remembered, and returned so the caller can route there. */
    fun picked(place: GeocodeResult): GeocodeResult {
        when (purpose) {
            Purpose.SET_HOME -> saved.save(SavedPlaces.HOME, place)
            Purpose.SET_WORK -> saved.save(SavedPlaces.WORK, place)
            Purpose.GO -> {
                recentStore.save(place)
                picks.record(place)
            }
        }
        _shortcuts.value = loadShortcuts()
        _recents.value = recentStore.load()
        return place
    }

    /** A spot on the map the driver long-pressed or tapped: where, and what's there once known. */
    data class Pinned(
        val latitude: Double,
        val longitude: Double,
        val place: GeocodeResult?,
        val loading: Boolean,
    )

    private val _pinned = MutableStateFlow<Pinned?>(null)
    val pinned: StateFlow<Pinned?> = _pinned.asStateFlow()
    private var pinJob: kotlinx.coroutines.Job? = null

    /** Drops a pin at [lat],[lon]. A tapped map label already knows its [name]; otherwise the address is looked up. */
    fun dropPin(
        lat: Double,
        lon: Double,
        name: String? = null,
        category: String? = null,
    ) {
        pinJob?.cancel()
        if (name != null) {
            _pinned.value = Pinned(lat, lon, GeocodeResult(name, lat, lon, name = name, address = "", category = category), loading = true)
        } else {
            _pinned.value = Pinned(lat, lon, null, loading = true)
        }
        pinJob =
            viewModelScope.launch {
                val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { GeocodingService.reverse(lat, lon) }.getOrNull() }
                val cur = _pinned.value ?: return@launch
                if (cur.latitude != lat || cur.longitude != lon) return@launch
                val place =
                    when {
                        name != null -> GeocodeResult("$name, ${found?.label ?: ""}".trimEnd(',', ' '), lat, lon, name = name, address = found?.label.orEmpty(), category = category)
                        found != null -> found.copy(latitude = lat, longitude = lon)
                        else -> GeocodeResult(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon), lat, lon, name = com.excellentmindblow.navos.tr("Dropped pin", "Pin diletak"), address = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon))
                    }
                _pinned.value = Pinned(lat, lon, place, loading = false)
            }
    }

    fun clearPin() {
        pinJob?.cancel()
        _pinned.value = null
    }

    /** Saves the pinned place as Home or Work. */
    fun savePinned(key: String) {
        val p = _pinned.value?.place ?: return
        saved.save(key, p)
        _shortcuts.value = loadShortcuts()
    }

    fun clearRecents() {
        recentStore.clear()
        _recents.value = emptyList()
    }

    /** How far [place] is from you, or null before the first fix. */
    fun distanceTo(place: GeocodeResult): Double? =
        here?.let { GeocodingService.distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude).toDouble() }

    private fun loadShortcuts() =
        Shortcuts(
            home = saved.get(SavedPlaces.HOME)?.let { GeocodeResult(it.label ?: "Home", it.latitude, it.longitude) },
            work = saved.get(SavedPlaces.WORK)?.let { GeocodeResult(it.label ?: "Work", it.latitude, it.longitude) },
        )
}
