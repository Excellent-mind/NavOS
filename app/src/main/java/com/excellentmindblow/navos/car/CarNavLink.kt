package com.excellentmindblow.navos.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.routing.Route

/**
 * The car's connection to the SAME [NavigationService] the phone UI uses, so
 * GPS, turn logic, voice guidance and rerouting are shared - the car only adds
 * a second listener on top.
 */
class CarNavLink(context: Context) {
    private val appContext = context.applicationContext
    private var service: NavigationService? = null
    private var bound = false
    private val pending = mutableListOf<(NavigationService) -> Unit>()

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val s = (binder as NavigationService.LocalBinder).getService()
                service = s
                val todo = pending.toList()
                pending.clear()
                todo.forEach { it(s) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }

    /** Runs [action] once the service is bound (immediately if it already is). */
    fun withService(action: (NavigationService) -> Unit) {
        val s = service
        if (s != null) {
            action(s)
            return
        }
        pending.add(action)
        if (!bound) {
            bound =
                appContext.bindService(
                    Intent(appContext, NavigationService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
        }
    }

    fun addListener(l: NavigationService.Listener) = withService { it.addListener(l) }

    fun removeListener(l: NavigationService.Listener) {
        service?.removeListener(l)
    }

    fun currentRoute(): Route? = service?.currentRoute()

    /** Returns false if Android refused to start the foreground service. */
    fun startNavigation(route: Route): Boolean =
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, NavigationService::class.java),
            )
            withService { it.startNavigation(route) }
            true
        } catch (e: Exception) {
            false
        }

    fun stopNavigation() {
        service?.stopNavigation()
    }

    fun release() {
        if (bound) {
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                // already unbound
            }
            bound = false
        }
        service = null
        pending.clear()
    }
}
