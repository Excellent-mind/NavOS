package com.excellentmindblow.navos.navigation

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.excellentmindblow.navos.MainActivity
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutingService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keeps an active drive alive when the screen locks or the driver switches
 * apps - the thing an Activity alone cannot do, since Android is free to
 * stop calling an Activity's location callbacks the moment it's no longer
 * in the foreground. This service owns the one GPS subscription, the one
 * [NavigationEngine] instance and the one [VoiceNarrator] for the whole
 * drive; MainActivity binds to it only to mirror state into the UI (camera,
 * turn card, lane strip) while it's actually visible. Nothing here changes
 * how a maneuver is calculated, announced, or recovered from - that's all
 * still NavigationEngine/VoiceNarrator/RoutingService exactly as before,
 * just driven from a component that outlives the Activity instead of one
 * that doesn't.
 *
 * Android reality check (documented rather than silently overpromised):
 * this survives screen-lock, app-switch, and normal backgrounding because
 * it's a proper foreground service with a persistent notification. It does
 * NOT survive the user force-stopping the app from Settings, or the OS
 * killing the whole process under severe memory pressure with the service
 * not yet restored - those are OS-level kills no app-level API can prevent,
 * and this app makes no claim otherwise. A killed service also does not
 * resume a drive by itself on next launch; the driver would need to pick
 * the destination again.
 */
class NavigationService : Service() {
    interface Listener {
        fun onNavLocation(location: Location)

        fun onNavState(state: NavigationState)

        /** Fired when the service stops navigation on its own (arrival,
         * or the "End" notification action) - the Activity, if bound,
         * uses this to drop back to its own idle UI rather than being
         * left showing a drive card for a session that's actually over. */
        fun onNavigationEnded()

        /** Fired when an off-route recalculation succeeds, with the new
         * [Route] - the Activity (if bound) needs this to redraw the route
         * polyline on the map, which this service has no map to do
         * itself. */
        fun onRerouted(route: Route)
    }

    inner class LocalBinder : Binder() {
        fun getService(): NavigationService = this@NavigationService
    }

    companion object {
        const val ACTION_STOP = "com.excellentmindblow.navos.action.STOP_NAVIGATION"
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 7001

        // Same off-route threshold NavigationEngine itself uses to flag
        // offRoute=true, kept here only as the trigger for "should we
        // bother recalculating" - not a second copy of the route-matching
        // logic.
        private const val MIN_ARRIVAL_METERS = 30.0

        private const val PREFS_NAME = "navos_prefs"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
    }

    private val binder = LocalBinder()
    private var listener: Listener? = null

    private var engine: NavigationEngine? = null
    private var voiceNarrator: VoiceNarrator? = null
    private var locationTracker: LiveLocationTracker? = null
    private var rerouteExecutor: ExecutorService? = null
    private var isRerouting = false
    private var activeRoute: Route? = null
    private var hasStoppedSelf = false

    private var lastLocation: Location? = null
    private var lastState: NavigationState? = null
    private var lastNotifiedKey: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopNavigationInternal(announceToListener = true)
        }
        // Not START_STICKY: if the OS kills this process, re-creating the
        // service with no route and no destination to resume would just
        // show a notification for a drive that no longer exists. The
        // driver restarting navigation from the app is the honest
        // recovery path here, not a silent respawn.
        return START_NOT_STICKY
    }

    /** Starts (or restarts, for a fresh destination) tracking this route.
     * Safe to call again while already running - setRoute()/resetGuard()
     * reset the existing engine/voice state rather than leaking a second
     * GPS subscription. */
    fun startNavigation(route: Route) {
        hasStoppedSelf = false
        activeRoute = route

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (voiceNarrator == null) {
            voiceNarrator = VoiceNarrator(this).also { it.enabled = prefs.getBoolean(KEY_VOICE_ENABLED, true) }
        }
        if (engine == null) {
            engine =
                NavigationEngine(
                    onState = { state -> handleState(state) },
                    onAnnounce = { key, text -> voiceNarrator?.speak(key, text) },
                )
        }
        engine?.setRoute(route)
        voiceNarrator?.resetGuard()
        lastNotifiedKey = null

        startForegroundNotification("Starting navigation...", "")
        // startForegroundNotification() tears everything back down via
        // stopNavigationInternal() if the OS refuses to let this become a
        // foreground service - don't then go start a GPS subscription for
        // a session that was just cancelled.
        if (hasStoppedSelf) return
        startLocationUpdatesIfNeeded()
    }

    /** Applies a recalculated route without tearing down the engine/voice
     * session - same object, same announce-guard, just new steps, exactly
     * like MainActivity's old in-place reroute did. */
    fun rerouteTo(route: Route) {
        activeRoute = route
        engine?.setRoute(route)
        listener?.onRerouted(route)
    }

    fun setVoiceEnabled(enabled: Boolean) {
        voiceNarrator?.enabled = enabled
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_ENABLED, enabled)
            .apply()
    }

    fun setListener(l: Listener?) {
        listener = l
        // Replay the last known fix/state/route immediately so a freshly
        // bound Activity (coming back from the background, possibly after
        // a reroute happened while it wasn't around to hear about it)
        // isn't left showing a blank or stale drive card/route line until
        // the next GPS update arrives.
        activeRoute?.let { l?.onRerouted(it) }
        lastLocation?.let { l?.onNavLocation(it) }
        lastState?.let { l?.onNavState(it) }
    }

    fun isNavigating(): Boolean = activeRoute != null

    fun stopNavigation() = stopNavigationInternal(announceToListener = false)

    private fun stopNavigationInternal(announceToListener: Boolean) {
        if (hasStoppedSelf) return
        hasStoppedSelf = true

        locationTracker?.stop()
        locationTracker = null
        rerouteExecutor?.shutdownNow()
        rerouteExecutor = null
        voiceNarrator?.shutdown()
        voiceNarrator = null
        engine = null
        activeRoute = null
        isRerouting = false
        lastLocation = null
        lastState = null

        if (announceToListener) {
            listener?.onNavigationEnded()
        }

        // The 1-arg Boolean overload is deprecated (API 33 added an
        // int-flags version) but still fully supported on every API level
        // this app targets, which keeps this call free of any uncertainty
        // about exactly which compat constant/overload is available in
        // the AndroidX version actually on the classpath.
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    private fun startLocationUpdatesIfNeeded() {
        if (locationTracker != null) return
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val tracker =
            LiveLocationTracker(locationManager) { location ->
                lastLocation = location
                listener?.onNavLocation(location)
                engine?.onLocation(
                    RoutePoint(location.latitude, location.longitude),
                    if (location.hasSpeed()) location.speed.toDouble() else null,
                )
            }
        locationTracker = tracker
        tracker.start()
    }

    private fun handleState(state: NavigationState) {
        lastState = state
        listener?.onNavState(state)
        updateNotification(state)

        if (state.isArrival && state.distanceToNextTurnMeters <= MIN_ARRIVAL_METERS) {
            stopNavigationInternal(announceToListener = true)
            return
        }

        if (state.offRoute && !isRerouting) {
            rerouteFromCurrentLocation()
        }
    }

    /** Same recalculate-in-place reroute MainActivity used to do, just
     * living here now so it keeps working whether or not the Activity is
     * even around to ask for it. Runs on the service's own executor, not
     * the Activity's, so it isn't at risk of losing its worker the moment
     * the Activity is destroyed in the background. */
    private fun rerouteFromCurrentLocation() {
        val origin = lastLocation ?: return
        val destination = activeRoute?.geometry?.lastOrNull() ?: return
        isRerouting = true

        val executor = rerouteExecutor ?: Executors.newSingleThreadExecutor().also { rerouteExecutor = it }
        val originPoint = RoutePoint(origin.latitude, origin.longitude)
        executor.execute {
            var routes: List<Route> = emptyList()
            try {
                routes = RoutingService.calculateRoutes(originPoint, destination)
            } catch (e: Exception) {
                // Empty list just means "stay off-route, try again next
                // off-route tick" rather than crash the service.
            }
            isRerouting = false
            val newRoute = routes.firstOrNull()
            if (newRoute != null && activeRoute != null) {
                rerouteTo(newRoute)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Navigation")
                .setDescription("Ongoing turn-by-turn navigation")
                .setShowBadge(false)
                .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        text: String,
    ): Notification {
        val openIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        val openPending =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            Intent(this, NavigationService::class.java).apply { action = ACTION_STOP }
        val stopPending =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_arrow)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, "End navigation", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundNotification(
        title: String,
        text: String,
    ) {
        val notification = buildNotification(title, text)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            // Covers Android 12+'s ForegroundServiceStartNotAllowedException
            // (the service tried to promote itself while the app wasn't in
            // a state allowed to start one - shouldn't happen here since
            // this only runs right after a driver taps "Start" in the
            // foreground app, but a device-specific OEM restriction is
            // still possible) - fail safe by shutting the attempt down
            // rather than leaving a non-foreground service quietly running
            // without the notification Android requires it to show.
            stopNavigationInternal(announceToListener = true)
        }
    }

    private fun updateNotification(state: NavigationState) {
        // Round so a slowly-ticking distance doesn't repaint the
        // notification (and re-trigger its layout) on every single GPS
        // fix - only when it's moved far enough to actually read
        // differently.
        val roundedMeters = (Math.round(state.distanceToNextTurnMeters / 10) * 10).toInt()
        val key = "${state.currentStepIndex}-$roundedMeters"
        if (key == lastNotifiedKey) return
        lastNotifiedKey = key

        val distanceLabel =
            if (state.distanceToNextTurnMeters >= 1000) {
                String.format("%.1f km", state.distanceToNextTurnMeters / 1000)
            } else {
                "$roundedMeters m"
            }
        val notification = buildNotification(state.instruction, distanceLabel)
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied (API 33+) - navigation keeps
            // running, the driver just won't see the ongoing notification.
        }
    }

    override fun onDestroy() {
        stopNavigationInternal(announceToListener = false)
        super.onDestroy()
    }
}
