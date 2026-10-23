package com.excellentmindblow.navos.navigation

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.excellentmindblow.navos.location.DeadReckoning
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.excellentmindblow.navos.Lang
import com.excellentmindblow.navos.MainActivity
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.location.LiveLocationTracker
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStop
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.routing.TripPlanner
import com.excellentmindblow.navos.traffic.TrafficReporter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.excellentmindblow.navos.tr

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

        /** A recalculation after leaving the route could not get a new
         * route (no signal, server busy). The service keeps retrying. */
        fun onRerouteFailed() {}

        /** The driver reached an intermediate stop of a multi-stop trip. The
         * service carries on to the next stop by itself; this is for the UI
         * (drop the stop's pin, say so on screen). */
        fun onStopReached(stop: RouteStop) {}

        /** Adding or skipping a stop mid-drive could not be applied (no
         * signal, no GPS fix yet). The drive carries on as before. */
        fun onStopChangeFailed() {}
    }

    inner class LocalBinder : Binder() {
        fun getService(): NavigationService = this@NavigationService
    }

    companion object {
        /** How often to check for a GPS dropout, and the "provider" name of estimated fixes. */
        private const val ESTIMATE_TICK_MS = 1_000L
        const val ESTIMATE_PROVIDER = "navos-estimate"
        private const val ESTIMATE_MAX_OFF_ROUTE_M = 60.0

        const val ACTION_STOP = "com.excellentmindblow.navos.action.STOP_NAVIGATION"
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 7001

        // Same off-route threshold NavigationEngine itself uses to flag
        // offRoute=true, kept here only as the trigger for "should we
        // bother recalculating" - not a second copy of the route-matching
        // logic.
        private const val MIN_ARRIVAL_METERS = 30.0

        // Reroute pacing: the engine flags offRoute on every GPS fix (~1/s),
        // so without these a single noisy fix would trigger a reroute and a
        // failed request (no signal, OSRM busy/rate-limited) would be
        // retried every second.
        private const val OFF_ROUTE_FIXES_BEFORE_REROUTE = 2
        private const val REROUTE_RETRY_COOLDOWN_MS = 3_000L

        private const val PREFS_NAME = "navos_prefs"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
    }

    private val binder = LocalBinder()
    // The phone UI owns the primary listener (setListener); Android Auto and
    // anything else adds extra ones. Every callback fans out to all of them.
    private var primaryListener: Listener? = null
    private val extraListeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    private val listener: Listener =
        object : Listener {
            override fun onNavLocation(location: Location) {
                primaryListener?.onNavLocation(location)
                extraListeners.forEach { it.onNavLocation(location) }
            }

            override fun onNavState(state: NavigationState) {
                primaryListener?.onNavState(state)
                extraListeners.forEach { it.onNavState(state) }
            }

            override fun onNavigationEnded() {
                primaryListener?.onNavigationEnded()
                extraListeners.forEach { it.onNavigationEnded() }
            }

            override fun onRerouted(route: Route) {
                primaryListener?.onRerouted(route)
                extraListeners.forEach { it.onRerouted(route) }
            }

            override fun onRerouteFailed() {
                primaryListener?.onRerouteFailed()
                extraListeners.forEach { it.onRerouteFailed() }
            }

            override fun onStopReached(stop: RouteStop) {
                primaryListener?.onStopReached(stop)
                extraListeners.forEach { it.onStopReached(stop) }
            }

            override fun onStopChangeFailed() {
                primaryListener?.onStopChangeFailed()
                extraListeners.forEach { it.onStopChangeFailed() }
            }
        }

    private var engine: NavigationEngine? = null
    private var voiceNarrator: VoiceNarrator? = null
    private var locationTracker: LiveLocationTracker? = null
    private var rerouteExecutor: ExecutorService? = null
    private var isRerouting = false
    private var offRouteStreak = 0
    private var lastRerouteAttemptMs = 0L
    private var rerouteCount = 0
    private var recalcAnnounced = false

    // Multi-stop trips: how many stops this drive has already reached. Remaining
    // stops are numbered from here, so the pin that says "3" is always the third
    // stop of the trip even after earlier ones are gone.
    private var stopsPassed = 0
    private val mainHandler = Handler(Looper.getMainLooper())
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
        stopsPassed = 0

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        (trafficReporter ?: TrafficReporter(prefs).also { trafficReporter = it }).setRoute(route, sameDrive = false)
        Units.load(prefs)
        Lang.load(prefs)

        if (voiceNarrator == null) {
            voiceNarrator = VoiceNarrator(this).also { it.enabled = prefs.getBoolean(KEY_VOICE_ENABLED, true) }
        }
        voiceNarrator?.applyLanguage()
        if (engine == null) {
            engine =
                NavigationEngine(
                    onState = { state -> handleState(state) },
                    onAnnounce = { key, text -> voiceNarrator?.speak(key, text) },
                    onStopReached = { stop ->
                        stopsPassed += 1
                        listener.onStopReached(stop)
                    },
                )
        }
        engine?.setRoute(route)
        voiceNarrator?.resetGuard()
        lastNotifiedKey = null

        startForegroundNotification(tr("Starting navigation...", "Mula navigasi..."), "")
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
        trafficReporter?.setRoute(route, sameDrive = true)
        // The last state belongs to the OLD route's step numbering; keeping it
        // would make remainingStops() mis-judge which stops are still ahead
        // until the next GPS fix arrives.
        lastState = null
        engine?.setRoute(route)
        listener.onRerouted(route)
    }

    fun setVoiceEnabled(enabled: Boolean) {
        voiceNarrator?.enabled = enabled
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_ENABLED, enabled)
            .apply()
    }

    /** One-off spoken alert from the UI (e.g. entering a speed-camera zone),
     * respecting the Voice guidance setting. */
    fun announce(
        key: String,
        text: String,
    ) {
        voiceNarrator?.speak(key, text)
    }

    fun setListener(l: Listener?) {
        primaryListener = l
        // Replay the last known fix/state/route immediately so a freshly
        // bound Activity (coming back from the background, possibly after
        // a reroute happened while it wasn't around to hear about it)
        // isn't left showing a blank or stale drive card/route line until
        // the next GPS update arrives.
        if (l != null) replayTo(l)
    }

    /** Extra listener (Android Auto). Gets the same replay as [setListener]. */
    fun addListener(l: Listener) {
        if (!extraListeners.contains(l)) extraListeners.add(l)
        replayTo(l)
    }

    fun removeListener(l: Listener) {
        extraListeners.remove(l)
    }

    private fun replayTo(l: Listener) {
        activeRoute?.let { l.onRerouted(it) }
        lastLocation?.let { l.onNavLocation(it) }
        lastState?.let { l.onNavState(it) }
    }

    /** Current route, for UIs that attach mid-drive. */
    fun currentRoute(): Route? = activeRoute

    fun isNavigating(): Boolean = activeRoute != null

    fun stopNavigation() = stopNavigationInternal(announceToListener = false)

    private fun stopNavigationInternal(announceToListener: Boolean) {
        if (hasStoppedSelf) return
        hasStoppedSelf = true

        locationTracker?.stop()
        locationTracker = null
        deadReckoning = null
        trafficReporter?.stop()
        trafficReporter = null
        rerouteExecutor?.shutdownNow()
        rerouteExecutor = null
        voiceNarrator?.shutdown()
        voiceNarrator = null
        engine = null
        activeRoute = null
        isRerouting = false
        offRouteStreak = 0
        mainHandler.removeCallbacksAndMessages(null)
        lastLocation = null
        lastState = null

        if (announceToListener) {
            listener.onNavigationEnded()
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
            LiveLocationTracker(locationManager, minDistanceM = 0f) { location ->
                // A real fix: any tunnel estimate gives way to it.
                lastRealFixMs = SystemClock.elapsedRealtime()
                deadReckoning?.stop()
                trafficReporter?.onFix(location, lastRealFixMs)
                onFix(location)
            }
        locationTracker = tracker
        tracker.start()
        mainHandler.removeCallbacks(estimateTick)
        mainHandler.postDelayed(estimateTick, ESTIMATE_TICK_MS)
    }

    private fun onFix(location: Location) {
        lastLocation = location
        listener.onNavLocation(location)
        engine?.onLocation(
            RoutePoint(location.latitude, location.longitude),
            if (location.hasSpeed()) location.speed.toDouble() else null,
            if (location.hasBearing()) location.bearing.toDouble() else null,
        )
    }

    // ---- Tunnels: keep moving along the route when GPS drops out -----------

    private var deadReckoning: DeadReckoning? = null

    /** Anonymous road speeds for live traffic (only sent if the driver agreed). */
    private var trafficReporter: TrafficReporter? = null
    private var lastRealFixMs = 0L

    private val estimateTick =
        object : Runnable {
            override fun run() {
                if (activeRoute == null) return
                estimateIfSilent()
                mainHandler.postDelayed(this, ESTIMATE_TICK_MS)
            }
        }

    /** No fix for a few seconds while driving: carry on along the route at the last speed. */
    private fun estimateIfSilent() {
        val route = activeRoute ?: return
        val last = lastLocation ?: return
        val now = SystemClock.elapsedRealtime()
        val dr = deadReckoning
        if (dr == null || !dr.active) {
            if (now - lastRealFixMs < DeadReckoning.SILENCE_MS) return
            val speed = if (last.hasSpeed()) last.speed.toDouble() else 0.0
            if (speed < DeadReckoning.MIN_SPEED_MS || route.geometry.size < 2) return
            val track = RouteTrack(route.geometry)
            val snap = GeoUtils.snapToPolyline(RoutePoint(last.latitude, last.longitude), route.geometry) ?: return
            if (snap.distanceMeters > ESTIMATE_MAX_OFF_ROUTE_M) return
            deadReckoning = DeadReckoning(track).also { it.start(track.progressOf(snap), speed, lastRealFixMs) }
        }
        val e = deadReckoning?.estimate(now) ?: return
        onFix(
            Location(ESTIMATE_PROVIDER).apply {
                latitude = e.point.latitude
                longitude = e.point.longitude
                speed = e.speedMs.toFloat()
                bearing = e.bearingDeg.toFloat()
                // Grows the longer we go without a real fix.
                accuracy = (15.0 + 2.0 * e.secondsSinceFix).toFloat()
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }

    private fun handleState(state: NavigationState) {
        lastState = state
        listener.onNavState(state)
        updateNotification(state)

        // Only a real fix ends the drive, never a tunnel estimate.
        val realFix = lastLocation?.provider != ESTIMATE_PROVIDER
        if (realFix && state.isArrival && state.distanceToNextTurnMeters <= MIN_ARRIVAL_METERS) {
            stopNavigationInternal(announceToListener = true)
            return
        }

        if (!state.offRoute) {
            offRouteStreak = 0
            recalcAnnounced = false
            return
        }
        offRouteStreak += 1
        val now = android.os.SystemClock.elapsedRealtime()
        if (!isRerouting &&
            // One fix is enough when the heading proves it's a wrong turn.
            offRouteStreak >= (if (state.offRouteConfirmed) 1 else OFF_ROUTE_FIXES_BEFORE_REROUTE) &&
            now - lastRerouteAttemptMs >= REROUTE_RETRY_COOLDOWN_MS
        ) {
            lastRerouteAttemptMs = now
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
        // Stops already visited are gone; the ones still ahead stay on the trip
        // (otherwise one wrong turn would quietly drop the rest of the stops).
        val stopsAhead = remainingStops()
        isRerouting = true

        // Say tr("Recalculating", "Cari laluan baru") once per wrong turn, not on every retry.
        if (!recalcAnnounced) {
            recalcAnnounced = true
            rerouteCount += 1
            voiceNarrator?.speak("reroute-$rerouteCount", tr("Recalculating", "Cari laluan baru"))
        }

        val executor = rerouteExecutor ?: Executors.newSingleThreadExecutor().also { rerouteExecutor = it }
        val originPoint = RoutePoint(origin.latitude, origin.longitude)
        executor.execute {
            var routes: List<Route> = emptyList()
            try {
                routes = RoutingService.calculateRoutes(originPoint, stopsAhead, destination)
            } catch (e: Exception) {
                // Empty list just means "stay off-route, try again after the
                // cooldown" rather than crash the service.
            }
            // Everything that touches the engine, the map listener or this
            // service's state goes back onto the main thread - GPS updates,
            // the engine and the map all live there, and applying a route
            // from this worker thread raced with them (and map calls off the
            // main thread don't work), which is why the new route never
            // showed up.
            mainHandler.post {
                isRerouting = false
                val newRoute = routes.firstOrNull()
                if (newRoute != null && activeRoute != null && !hasStoppedSelf) {
                    offRouteStreak = 0
                    recalcAnnounced = false
                    rerouteTo(newRoute)
                } else if (newRoute == null && activeRoute != null && !hasStoppedSelf) {
                    listener.onRerouteFailed()
                }
            }
        }
    }

    // ---- Multi-stop: change the stops of a drive that is already under way ----

    /** Intermediate stops not yet reached, in driving order. */
    fun remainingStops(): List<RouteStop> {
        val route = activeRoute ?: return emptyList()
        val at = lastState?.currentStepIndex ?: 1
        return route.stops.filter { it.stepIndex >= at }
    }

    /** Adds [stop] to the drive in front of the destination, at the spot that
     * costs the least detour among the stops still ahead, and re-plans from where
     * the driver is now. Reports through [Listener.onStopChangeFailed] if it
     * cannot. */
    fun addStop(stop: RouteStop) =
        changeStops(tr("Stop added", "Hentian ditambah")) { remaining, origin, destination ->
            if (remaining.size >= TripPlanner.MAX_STOPS) {
                null
            } else {
                val index =
                    TripPlanner.cheapestInsertIndex(origin, remaining.map { it.point }, destination, stop.point)
                remaining.toMutableList().apply { add(index, stop) }
            }
        }

    /** Drops the next intermediate stop (closed, no longer needed) and re-plans
     * from where the driver is now. */
    fun skipNextStop() =
        changeStops(tr("Stop skipped", "Hentian dilangkau")) { remaining, _, _ ->
            if (remaining.isEmpty()) null else remaining.drop(1)
        }

    private fun changeStops(
        spoken: String,
        edit: (remaining: List<RouteStop>, origin: RoutePoint, destination: RoutePoint) -> List<RouteStop>?,
    ) {
        val fix = lastLocation
        val route = activeRoute
        val destination = route?.geometry?.lastOrNull()
        if (fix == null || route == null || destination == null || isRerouting || hasStoppedSelf) {
            listener.onStopChangeFailed()
            return
        }
        val originPoint = RoutePoint(fix.latitude, fix.longitude)
        val updated = edit(remainingStops(), originPoint, destination)
        if (updated == null) {
            listener.onStopChangeFailed()
            return
        }
        // Number what is left in driving order, continuing after the stops
        // already reached.
        val numbered = updated.mapIndexed { i, s -> s.copy(number = stopsPassed + i + 1, stepIndex = -1, geometryIndex = -1) }
        isRerouting = true
        val executor = rerouteExecutor ?: Executors.newSingleThreadExecutor().also { rerouteExecutor = it }
        executor.execute {
            var routes: List<Route> = emptyList()
            try {
                routes = RoutingService.calculateRoutes(originPoint, numbered, destination)
            } catch (e: Exception) {
                // reported below as a failed change
            }
            mainHandler.post {
                isRerouting = false
                val newRoute = routes.firstOrNull()
                if (newRoute != null && activeRoute != null && !hasStoppedSelf) {
                    offRouteStreak = 0
                    recalcAnnounced = false
                    rerouteTo(newRoute)
                    voiceNarrator?.speak("stops-changed-${System.nanoTime()}", spoken)
                } else if (activeRoute != null && !hasStoppedSelf) {
                    listener.onStopChangeFailed()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(tr("Navigation", "Navigasi"))
                .setDescription(tr("Ongoing turn-by-turn navigation", "Navigasi sedang berjalan"))
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
            .setSmallIcon(R.drawable.ic_stat_navos)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(0, tr("End navigation", "Tamat navigasi"), stopPending)
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

        val distanceLabel = Units.short(state.distanceToNextTurnMeters)
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
