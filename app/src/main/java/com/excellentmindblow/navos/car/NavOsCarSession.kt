package com.excellentmindblow.navos.car

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Trip
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.excellentmindblow.navos.Lang
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.excellentmindblow.navos.tr

/** One Android Auto connection. Owns everything the car screens share. */
class NavOsCarSession : Session() {
    lateinit var link: CarNavLink
        private set
    lateinit var renderer: CarMapRenderer
        private set

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    var destinationName: String = ""
    var destinationAddress: String = ""
    private var driving = false
    private var navManager: NavigationManager? = null
    private var inited = false

    private val mainExecutor get() = ContextCompat.getMainExecutor(carContext)

    override fun onCreateScreen(intent: Intent): Screen {
        ensureInit()
        handleIntent(intent)
        return HomeScreen(carContext, this)
    }

    override fun onNewIntent(intent: Intent) {
        ensureInit()
        handleIntent(intent)
    }

    // carContext only exists once the session is created, so set up lazily.
    private fun ensureInit() {
        if (inited) return
        inited = true
        carContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).let {
            Units.load(it)
            Lang.load(it)
        }
        link = CarNavLink(carContext)
        renderer = CarMapRenderer(carContext)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)

        navManager =
            carContext.getCarService(NavigationManager::class.java).also { nm ->
                nm.setNavigationManagerCallback(
                    object : NavigationManagerCallback {
                        override fun onStopNavigation() {
                            // The car host asked us to stop (e.g. another nav app took over).
                            endDrive()
                        }

                        override fun onAutoDriveEnabled() {}
                    },
                )
            }

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    executor.shutdownNow()
                    renderer.release()
                    link.release()
                }
            },
        )
    }

    val screens: ScreenManager get() = carContext.getCarService(ScreenManager::class.java)

    // ---- Location -----------------------------------------------------------

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun currentLocation(): Location? {
        renderer.lastLocation?.let { return it }
        if (!hasLocationPermission()) return null
        val lm = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull {
                try {
                    lm.getLastKnownLocation(it)
                } catch (e: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }

    fun toast(text: String) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    fun runOnMain(block: () -> Unit) = mainExecutor.execute(block)

    // ---- Drive lifecycle --------------------------------------------------------

    fun startDrive(
        route: Route,
        dest: GeocodeResult,
    ) {
        destinationName = dest.name
        destinationAddress = dest.address
        if (!link.startNavigation(route)) {
            toast(tr("Couldn't start navigation. Open NavOS on your phone once, then try again.", "Tak dapat mula navigasi. Buka NavOS di telefon sekali, lepas tu cuba lagi."))
            return
        }
        beginDrive(route)
    }

    /** Car connected while the phone is already navigating: mirror that drive. */
    fun resumeIfNavigating() {
        link.withService { s ->
            if (s.isNavigating() && !driving) {
                val route = s.currentRoute()
                if (destinationName.isBlank()) {
                    destinationName = route?.steps?.lastOrNull()?.roadName?.takeIf { it.isNotBlank() } ?: tr("Destination", "Destinasi")
                }
                beginDrive(route)
            }
        }
    }

    private fun beginDrive(route: Route?) {
        driving = true
        navManager?.navigationStarted()
        route?.let { renderer.setRoute(it) }
        screens.popToRoot()
        screens.push(DriveScreen(carContext, this))
    }

    /** Driver tapped End (or the host stopped us). */
    fun endDrive() {
        link.stopNavigation()
        finishDrive()
        renderer.clearRoute()
        screens.popToRoot()
    }

    /** Service ended on its own: arrival (state says so) or the notification's End. */
    fun onNavigationEnded(arrived: Boolean) {
        finishDrive()
        screens.popToRoot()
        if (arrived) {
            screens.push(ArrivalScreen(carContext, this))
        } else {
            renderer.clearRoute()
        }
    }

    private fun finishDrive() {
        if (driving) navManager?.navigationEnded()
        driving = false
    }

    // ---- Trip info for the car's cluster / notifications ------------------------

    fun updateTrip(
        state: NavigationState,
        roadName: String?,
    ) {
        val nm = navManager ?: return
        if (!driving) return
        val share =
            if (state.distanceRemainingMeters > 0) {
                (state.distanceToNextTurnMeters / state.distanceRemainingMeters).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        val trip =
            Trip.Builder()
                .addDestination(
                    Destination.Builder()
                        .setName(destinationName.ifBlank { tr("Destination", "Destinasi") })
                        .setAddress(destinationAddress.ifBlank { destinationName.ifBlank { tr("Destination", "Destinasi") } })
                        .build(),
                    CarFormat.estimate(state.distanceRemainingMeters, state.durationRemainingSeconds),
                )
                .addStep(
                    CarFormat.step(carContext, state.instruction, state.maneuverType, state.modifier, roadName),
                    CarFormat.estimate(state.distanceToNextTurnMeters, state.durationRemainingSeconds * share),
                )
                .setCurrentRoad(roadName.orEmpty())
                .setLoading(false)
                .build()
        nm.updateTrip(trip)
    }

    // ---- "Hey Google, navigate to ..." ------------------------------------------

    private fun handleIntent(intent: Intent) {
        if (intent.action != CarContext.ACTION_NAVIGATE) return
        val uri = intent.data ?: return
        executor.execute {
            val dest = resolveGeo(uri)
            runOnMain {
                if (dest != null) {
                    screens.push(RoutePreviewScreen(carContext, this, dest))
                } else {
                    toast(tr("Couldn't find that destination.", "Tak jumpa destinasi tu."))
                }
            }
        }
    }

    /** geo:lat,lon  |  geo:0,0?q=lat,lon(label)  |  geo:0,0?q=place name */
    private fun resolveGeo(uri: Uri): GeocodeResult? {
        val ssp = uri.schemeSpecificPart ?: return null
        val path = ssp.substringBefore("?")
        val q =
            ssp.substringAfter("?", "")
                .split("&")
                .firstOrNull { it.startsWith("q=") }
                ?.removePrefix("q=")
                ?.let { Uri.decode(it.replace("+", " ")) }

        val coord = Regex("""(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
        val inQuery = q?.let { coord.find(it) }
        val inPath =
            coord.find(path)?.takeIf {
                !(it.groupValues[1].toDouble() == 0.0 && it.groupValues[2].toDouble() == 0.0)
            }
        val hit = inQuery ?: inPath
        if (hit != null) {
            val label =
                q?.substringAfter("(", "")?.substringBefore(")")?.takeIf { it.isNotBlank() } ?: tr("Destination", "Destinasi")
            return GeocodeResult(label, hit.groupValues[1].toDouble(), hit.groupValues[2].toDouble())
        }
        if (!q.isNullOrBlank()) {
            val loc = currentLocation()
            return try {
                GeocodingService.searchLive(q, loc?.latitude, loc?.longitude).firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    companion object {
        const val PREFS_NAME = "navos_prefs"
    }
}
