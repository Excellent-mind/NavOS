package com.excellentmindblow.navos.car

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.navigation.NavigationService
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RoutingService
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import com.excellentmindblow.navos.search.SavedPlaces
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.excellentmindblow.navos.tr

// ---------------------------------------------------------------------------
// Home: search + saved Home/Work
// ---------------------------------------------------------------------------

class HomeScreen(
    ctx: CarContext,
    private val session: NavOsCarSession,
) : Screen(ctx) {
    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    // If the phone is already navigating, jump straight to that drive.
                    if (session.hasLocationPermission()) session.resumeIfNavigating()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        if (!session.hasLocationPermission()) {
            return MessageTemplate.Builder(tr("NavOS needs location access. Tap Allow, then confirm on your phone.", "NavOS perlu akses lokasi. Tekan Benarkan, lepas tu sahkan di telefon."))
                .setTitle("NavOS")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle(tr("Allow", "Benarkan"))
                        .setOnClickListener {
                            carContext.requestPermissions(
                                listOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            ) { _, _ -> invalidate() }
                        }
                        .build(),
                )
                .build()
        }

        val prefs = carContext.getSharedPreferences(NavOsCarSession.PREFS_NAME, Context.MODE_PRIVATE)
        val list = ItemList.Builder()
        list.addItem(
            Row.Builder()
                .setTitle(tr("Search destination", "Cari destinasi"))
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(SearchScreen(carContext, session)) }
                .build(),
        )
        val savedPlaces = SavedPlaces(prefs)
        for ((key, title) in listOf(SavedPlaces.HOME to tr("Home", "Rumah"), SavedPlaces.WORK to tr("Work", "Kerja"))) {
            val row = Row.Builder().setTitle(title)
            val saved = savedPlaces.get(key)
            if (saved != null) {
                val label = saved.label.orEmpty()
                val dest = GeocodeResult(label.ifBlank { title }, saved.latitude, saved.longitude)
                if (label.isNotBlank()) row.addText(label)
                row.setOnClickListener { screenManager.push(RoutePreviewScreen(carContext, session, dest)) }
            } else {
                row.addText(tr("Set this on your phone first", "Tetapkan di telefon dulu"))
            }
            list.addItem(row.build())
        }

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setTitle("NavOS")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

class SearchScreen(
    ctx: CarContext,
    private val session: NavOsCarSession,
) : Screen(ctx) {
    private var results: List<GeocodeResult> = emptyList()
    private var seq = 0

    override fun onGetTemplate(): Template {
        val builder =
            SearchTemplate.Builder(
                object : SearchTemplate.SearchCallback {
                    override fun onSearchTextChanged(searchText: String) = search(searchText)

                    override fun onSearchSubmitted(searchTerm: String) = search(searchTerm)
                },
            )
                .setHeaderAction(Action.BACK)
                .setSearchHint(tr("Where to?", "Nak pergi mana?"))
                .setShowKeyboardByDefault(true)

        val list = ItemList.Builder()
        results.take(6).forEach { r ->
            val row = Row.Builder().setTitle(r.name)
            if (r.address.isNotBlank()) row.addText(r.address)
            row.setOnClickListener { screenManager.push(RoutePreviewScreen(carContext, session, r)) }
            list.addItem(row.build())
        }
        if (results.isEmpty()) list.setNoItemsMessage(tr("Type a place or address", "Taip tempat atau alamat"))
        return builder.setItemList(list.build()).build()
    }

    private fun search(text: String) {
        val query = text.trim()
        val mine = ++seq
        if (query.isEmpty()) {
            results = emptyList()
            invalidate()
            return
        }
        val loc = session.currentLocation()
        session.executor.execute {
            val found =
                try {
                    GeocodingService.searchLive(query, loc?.latitude, loc?.longitude)
                } catch (e: Exception) {
                    null
                }
            session.runOnMain {
                if (mine == seq && found != null) {
                    results = found
                    invalidate()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Route preview (ETA + Start)
// ---------------------------------------------------------------------------

class RoutePreviewScreen(
    ctx: CarContext,
    private val session: NavOsCarSession,
    private val dest: GeocodeResult,
) : Screen(ctx) {
    private var route: Route? = null
    private var error: String? = null

    init {
        load()
    }

    private fun load() {
        route = null
        error = null
        val origin: Location? = session.currentLocation()
        if (origin == null) {
            error = tr("Waiting for a GPS fix. Check that location is on.", "Tunggu GPS. Pastikan lokasi dihidupkan.")
            return
        }
        session.executor.execute {
            val routes =
                try {
                    RoutingService.calculateRoutes(
                        RoutePoint(origin.latitude, origin.longitude),
                        RoutePoint(dest.latitude, dest.longitude),
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            session.runOnMain {
                route = routes.firstOrNull()
                if (route == null) error = tr("Couldn't find a route. Check your connection.", "Tak jumpa laluan. Cek internet anda.")
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        error?.let { message ->
            return MessageTemplate.Builder(message)
                .setTitle(dest.name)
                .setHeaderAction(Action.BACK)
                .addAction(
                    Action.Builder()
                        .setTitle(tr("Retry", "Cuba lagi"))
                        .setOnClickListener {
                            load()
                            invalidate()
                        }
                        .build(),
                )
                .build()
        }
        val r =
            route ?: return ListTemplate.Builder()
                .setLoading(true)
                .setTitle(dest.name)
                .setHeaderAction(Action.BACK)
                .build()

        val eta = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (r.durationSeconds * 1000).toLong()))
        val summary = tr("Arrive $eta  •  ${Units.short(r.distanceMeters)}  •  ${CarFormat.duration(r.durationSeconds)}", "Sampai $eta  •  ${Units.short(r.distanceMeters)}  •  ${CarFormat.duration(r.durationSeconds)}")
        return MessageTemplate.Builder(summary)
            .setTitle(dest.name)
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(tr("Start", "Mula"))
                    .setOnClickListener { session.startDrive(r, dest) }
                    .build(),
            )
            .build()
    }
}

// ---------------------------------------------------------------------------
// Active drive: turn banner over the map
// ---------------------------------------------------------------------------

class DriveScreen(
    ctx: CarContext,
    private val session: NavOsCarSession,
) : Screen(ctx), NavigationService.Listener {
    private var state: NavigationState? = null
    private var lastKey: String? = null
    private var lastInvalidateMs = 0L

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    session.link.addListener(this@DriveScreen)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    session.link.removeListener(this@DriveScreen)
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val end =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle(tr("End", "Tamat"))
                        .setOnClickListener { session.endDrive() }
                        .build(),
                )
                .build()
        val builder =
            NavigationTemplate.Builder()
                .setActionStrip(end)
                .setBackgroundColor(NAVY)

        val s = state
        when {
            s == null -> builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
            s.offRoute -> builder.setNavigationInfo(MessageInfo.Builder(tr("Recalculating…", "Cari laluan baru…")).build())
            else -> {
                val routing =
                    RoutingInfo.Builder()
                        .setCurrentStep(
                            CarFormat.step(carContext, s.instruction, s.maneuverType, s.modifier, roadFor(s.currentStepIndex)),
                            CarFormat.distance(s.distanceToNextTurnMeters),
                        )
                val nextType = s.nextManeuverType
                val nextText = s.nextInstruction
                if (nextType != null && nextText != null) {
                    routing.setNextStep(
                        CarFormat.step(carContext, nextText, nextType, s.nextModifier, roadFor(s.currentStepIndex + 1)),
                    )
                }
                builder.setNavigationInfo(routing.build())
                builder.setDestinationTravelEstimate(
                    CarFormat.estimate(s.distanceRemainingMeters, s.durationRemainingSeconds),
                )
            }
        }
        return builder.build()
    }

    private fun roadFor(index: Int): String? = session.link.currentRoute()?.steps?.getOrNull(index)?.roadName

    // ---- NavigationService.Listener (all on the main thread) --------------------

    override fun onNavLocation(location: Location) {
        session.renderer.updateLocation(location)
    }

    override fun onNavState(state: NavigationState) {
        this.state = state
        session.updateTrip(state, roadFor(state.currentStepIndex))

        // Refresh the banner at most once a second, and only when something
        // the driver can see actually changed (the host rate-limits updates).
        val bucket = if (state.distanceToNextTurnMeters > 1000) 100 else 10
        val key =
            "${state.currentStepIndex}|${(state.distanceToNextTurnMeters / bucket).toInt()}|" +
                "${(state.distanceRemainingMeters / 100).toInt()}|${state.offRoute}"
        val now = System.currentTimeMillis()
        if (key != lastKey && now - lastInvalidateMs >= 1000L) {
            lastKey = key
            lastInvalidateMs = now
            invalidate()
        }
    }

    override fun onRerouted(route: Route) {
        session.renderer.setRoute(route)
        lastKey = null
        invalidate()
    }

    override fun onNavigationEnded() {
        val s = state
        val arrived = s != null && s.isArrival && s.distanceToNextTurnMeters <= 60.0
        session.onNavigationEnded(arrived)
    }

    private companion object {
        val NAVY: CarColor = CarColor.createCustom(0xFF14284B.toInt(), 0xFF14284B.toInt())
    }
}

// ---------------------------------------------------------------------------
// Arrival: same tr("Yes, I've arrived", "Ya, dah sampai") confirmation as the phone card
// ---------------------------------------------------------------------------

class ArrivalScreen(
    ctx: CarContext,
    private val session: NavOsCarSession,
) : Screen(ctx) {
    override fun onGetTemplate(): Template {
        val name = session.destinationName.ifBlank { "your destination" }
        return MessageTemplate.Builder(name)
            .setTitle(tr("You've arrived", "Dah sampai"))
            .addAction(
                Action.Builder()
                    .setTitle(tr("Yes, I've arrived", "Ya, dah sampai"))
                    .setOnClickListener {
                        session.renderer.clearRoute()
                        screenManager.popToRoot()
                    }
                    .build(),
            )
            .build()
    }
}
