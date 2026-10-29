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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var service: NavigationService? = null
    private var bound = false
    private var pending: Route? = null

    private val listener =
        object : NavigationService.Listener {
            override fun onNavLocation(location: Location) {
                _location.value = location
            }

            override fun onNavState(state: NavigationState) {
                _state.value = state
            }

            override fun onNavigationEnded() {
                _ended.value = true
            }

            override fun onRerouted(route: Route) {
                _route.value = route
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
        unbind()
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
