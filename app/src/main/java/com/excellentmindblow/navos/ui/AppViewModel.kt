package com.excellentmindblow.navos.ui

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import com.excellentmindblow.navos.location.LiveLocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screens on top of the map, in the order a trip goes through them. */
enum class Screen { HOME, SEARCH, PREVIEW, DRIVE, ARRIVED, SETTINGS }

/**
 * App-wide state: which screen is up, where you are. Each screen gets its
 * own ViewModel for its data (search results, routes, the drive); this one
 * only holds what every screen shares.
 */
class AppViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    /** Where the trip goes (set by search / Home, used by route choice and the drive). */
    private val _destination = MutableStateFlow<com.excellentmindblow.navos.search.GeocodeResult?>(null)
    val destination: StateFlow<com.excellentmindblow.navos.search.GeocodeResult?> = _destination.asStateFlow()

    fun routeTo(place: com.excellentmindblow.navos.search.GeocodeResult) {
        _destination.value = place
        _screen.value = Screen.PREVIEW
    }

    /** True once the first fix arrived, so the map centres on you once. */
    var centredOnce = false

    private var tracker: LiveLocationTracker? = null

    private val prefs = app.getSharedPreferences(com.excellentmindblow.navos.MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    /** Day / night choice: follow the phone, always day, always night. */
    private val _theme = MutableStateFlow(prefs.getString(com.excellentmindblow.navos.ui.settings.ThemePref.KEY, null) ?: com.excellentmindblow.navos.ui.settings.ThemePref.SYSTEM)
    val theme: StateFlow<String> = _theme.asStateFlow()

    fun setTheme(value: String) {
        prefs.edit().putString(com.excellentmindblow.navos.ui.settings.ThemePref.KEY, value).apply()
        _theme.value = value
    }

    /** Where the last drive ended, for the arrival card. */
    var arrivedName: String = ""
    var arrivedSide: String? = null

    fun go(to: Screen) {
        _screen.value = to
    }

    /** Starts location updates (call once permission is granted). */
    fun startLocation() {
        if (tracker != null) return
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        tracker = LiveLocationTracker(lm) { _location.value = it }.also { runCatching { it.start() } }
    }

    override fun onCleared() {
        tracker?.stop()
        tracker = null
    }
}
