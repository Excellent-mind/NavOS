package com.excellentmindblow.navos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.excellentmindblow.navos.ui.components.FloatingSearchBar
import com.excellentmindblow.navos.ui.home.HomeSheet
import com.excellentmindblow.navos.ui.map.MapController
import com.excellentmindblow.navos.ui.map.NavMap
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.ui.map.RouteLayers
import com.excellentmindblow.navos.ui.route.RoutePreviewScreen
import com.excellentmindblow.navos.ui.route.RouteViewModel
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.search.SearchScreen

/**
 * The whole app: the map underneath, and the current screen's panels on top
 * of it (like Google Maps / Waze, the map never leaves).
 */
@Composable
fun NavOSApp(
    app: AppViewModel = viewModel(),
    places: PlacesViewModel = viewModel(),
    routes: RouteViewModel = viewModel(),
    drive: com.excellentmindblow.navos.ui.drive.DriveViewModel = viewModel(),
) {
    val themePref by app.theme.collectAsStateWithLifecycle()
    val night =
        when (themePref) {
            com.excellentmindblow.navos.ui.settings.ThemePref.LIGHT -> false
            com.excellentmindblow.navos.ui.settings.ThemePref.DARK -> true
            else -> isSystemInDarkTheme()
        }
    com.excellentmindblow.navos.ui.theme.NavOSTheme(night = night) { AppContent(app, places, routes, drive, night) }
}

@Composable
private fun AppContent(
    app: AppViewModel,
    places: PlacesViewModel,
    routes: RouteViewModel,
    drive: com.excellentmindblow.navos.ui.drive.DriveViewModel,
    night: Boolean,
) {
    val screen by app.screen.collectAsStateWithLifecycle()
    val location by app.location.collectAsStateWithLifecycle()
    val destination by app.destination.collectAsStateWithLifecycle()
    val map = remember { MapController() }
    // Bumped after every style load (day / night switch) so layers are redrawn.
    var styleTick by remember { mutableStateOf(0) }

    LaunchedEffect(location) {
        places.onLocation(location)
        val l = location ?: return@LaunchedEffect
        // Centre on you at the first fix.
        if (!app.centredOnce && map.map != null) {
            app.centredOnce = true
            map.moveTo(l.latitude, l.longitude, animated = false)
        }
    }

    fun openSearch(purpose: PlacesViewModel.Purpose) {
        places.purpose = purpose
        places.clearSearch()
        app.go(Screen.SEARCH)
    }

    // The opening animation, once per launch, until the map has drawn.
    var splash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    // Whoever is on screen can listen for map taps (the drive: pins).
    val mapTap = remember { mutableStateOf<((org.maplibre.android.geometry.LatLng) -> Unit)?>(null) }
    Box(Modifier.fillMaxSize()) {
        val c = Jalan.colors
        NavMap(
            controller = map,
            night = night,
            location = location,
            modifier = Modifier.fillMaxSize(),
            onStyleReady = { style ->
                RouteLayers.install(style, c.route.toArgb(), c.inkQuiet.copy(alpha = 0.55f).toArgb(), c.card.toArgb())
                com.excellentmindblow.navos.ui.map.DriveLayers.install(style)
                com.excellentmindblow.navos.ui.map.PoiPins.install(style)
                styleTick++
            },
            onMapClick = { mapTap.value?.invoke(it) },
        )

        when (screen) {
            Screen.HOME -> {
                val shortcuts by places.shortcuts.collectAsStateWithLifecycle()
                val recents by places.recents.collectAsStateWithLifecycle()
                val predicted by places.predicted.collectAsStateWithLifecycle()
                FloatingSearchBar(
                    onSearch = { openSearch(PlacesViewModel.Purpose.GO) },
                    onSettings = { app.go(Screen.SETTINGS) },
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HomeSheet(
                    predicted = predicted,
                    shortcuts = shortcuts,
                    recents = recents,
                    distanceTo = places::distanceTo,
                    onGo = { app.routeTo(places.picked(it)) },
                    onSetHome = { openSearch(PlacesViewModel.Purpose.SET_HOME) },
                    onSetWork = { openSearch(PlacesViewModel.Purpose.SET_WORK) },
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
            Screen.SEARCH -> {
                val query by places.query.collectAsStateWithLifecycle()
                val results by places.results.collectAsStateWithLifecycle()
                val searching by places.searching.collectAsStateWithLifecycle()
                val shortcuts by places.shortcuts.collectAsStateWithLifecycle()
                val recents by places.recents.collectAsStateWithLifecycle()
                SearchScreen(
                    purpose = places.purpose,
                    query = query,
                    results = results,
                    searching = searching,
                    shortcuts = shortcuts,
                    recents = recents,
                    distanceTo = places::distanceTo,
                    onQuery = places::setQuery,
                    onPick = { p ->
                        val purpose = places.purpose
                        places.picked(p)
                        places.purpose = PlacesViewModel.Purpose.GO
                        if (purpose == PlacesViewModel.Purpose.GO) app.routeTo(p) else app.go(Screen.HOME)
                    },
                    onBack = { app.go(Screen.HOME) },
                    onClearRecents = places::clearRecents,
                )
            }
            Screen.PREVIEW -> {
                val state by routes.state.collectAsStateWithLifecycle()
                val selected by routes.selected.collectAsStateWithLifecycle()
                val density = LocalDensity.current
                fun back() {
                    routes.clear()
                    map.style?.let { RouteLayers.show(it, null, emptyList()) }
                    app.go(Screen.HOME)
                }
                BackHandler { back() }
                // Plan once there is a fix and a destination.
                LaunchedEffect(destination, location != null) {
                    val d = destination ?: return@LaunchedEffect
                    val l = location ?: return@LaunchedEffect
                    routes.plan(RoutePoint(l.latitude, l.longitude), RoutePoint(d.latitude, d.longitude))
                }
                // Draw the routes and frame them between the From / To card and the sheet.
                LaunchedEffect(state, selected, styleTick) {
                    val s = state as? RouteViewModel.State.Ready ?: return@LaunchedEffect
                    val style = map.style ?: return@LaunchedEffect
                    val sel = s.options.getOrNull(selected)?.route
                    RouteLayers.show(style, sel, s.options.map { it.route }.filter { it !== sel })
                    map.map?.let { m -> with(density) { RouteLayers.frame(m, s.options.map { it.route }, 170.dp.roundToPx(), 460.dp.roundToPx(), 40.dp.roundToPx()) } }
                }
                RoutePreviewScreen(
                    destination = destination,
                    state = state,
                    selected = selected,
                    onSelect = routes::select,
                    onBack = ::back,
                    onRetry = {
                        val d = destination
                        val l = location
                        if (d != null && l != null) routes.plan(RoutePoint(l.latitude, l.longitude), RoutePoint(d.latitude, d.longitude), force = true)
                    },
                    onGo = { app.go(Screen.DRIVE) },
                )
            }
            Screen.DRIVE -> DriveHost(app, routes, drive, map, styleTick, mapTap)
            Screen.ARRIVED -> {
                BackHandler { app.go(Screen.HOME) }
                com.excellentmindblow.navos.ui.drive.ArrivalCard(app.arrivedName, app.arrivedSide) { app.go(Screen.HOME) }
            }
            Screen.SETTINGS -> {
                val theme by app.theme.collectAsStateWithLifecycle()
                com.excellentmindblow.navos.ui.settings.SettingsScreen(theme = theme, onTheme = app::setTheme, onBack = { app.go(Screen.HOME) })
            }
        }
        if (splash) com.excellentmindblow.navos.ui.brand.SplashOverlay(ready = styleTick > 0, onDone = { splash = false })
    }
}

/** The drive: starts the service with the chosen route, follows you with the camera, draws the route. */
@Composable
private fun DriveHost(
    app: AppViewModel,
    routes: RouteViewModel,
    drive: com.excellentmindblow.navos.ui.drive.DriveViewModel,
    map: MapController,
    styleTick: Int,
    mapTap: androidx.compose.runtime.MutableState<((org.maplibre.android.geometry.LatLng) -> Unit)?>,
) {
    val state by drive.state.collectAsStateWithLifecycle()
    val traffic by drive.traffic.collectAsStateWithLifecycle()
    val faster by drive.faster.collectAsStateWithLifecycle()
    val poiOn by drive.onTheWay.kinds.collectAsStateWithLifecycle()
    val poiLoading by drive.onTheWay.loading.collectAsStateWithLifecycle()
    val pins by drive.onTheWay.pins.collectAsStateWithLifecycle()
    val selectedId by drive.onTheWay.selected.collectAsStateWithLifecycle()
    val route by drive.route.collectAsStateWithLifecycle()
    val fix by drive.location.collectAsStateWithLifecycle()
    val phoneFix by app.location.collectAsStateWithLifecycle()
    val ended by drive.ended.collectAsStateWithLifecycle()
    val voiceOn by drive.voiceOn.collectAsStateWithLifecycle()
    val alert by drive.alert.collectAsStateWithLifecycle()
    val limit by drive.limit.collectAsStateWithLifecycle()
    val reports by drive.reports.collectAsStateWithLifecycle()
    val openLanes by drive.openLanes.collectAsStateWithLifecycle()
    val destination by app.destination.collectAsStateWithLifecycle()
    var askEnd by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Start once with the route picked on the cards.
    LaunchedEffect(Unit) {
        if (drive.route.value == null) routes.selectedRoute()?.let { drive.start(it) }
    }
    // Pins and live traffic on the map; a tap on a pin opens its card, anywhere else closes it.
    LaunchedEffect(pins, selectedId, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.PoiPins.showPins(context, it, pins, selectedId) } }
    LaunchedEffect(traffic, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.PoiPins.showTraffic(it, traffic) } }
    androidx.compose.runtime.DisposableEffect(Unit) {
        mapTap.value = { ll ->
            val m = map.map
            if (m != null) {
                val pt = m.projection.toScreenLocation(ll)
                val slop = with(density) { 18.dp.toPx() }
                val hit =
                    runCatching { m.queryRenderedFeatures(android.graphics.RectF(pt.x - slop, pt.y - slop, pt.x + slop, pt.y + slop), com.excellentmindblow.navos.ui.map.PoiPins.LAYER_PINS) }
                        .getOrDefault(emptyList())
                        .firstNotNullOfOrNull { it.getStringProperty(com.excellentmindblow.navos.ui.map.PoiPins.PROP_ID) }
                drive.onTheWay.select(hit)
            }
        }
        onDispose {
            mapTap.value = null
            map.style?.let {
                com.excellentmindblow.navos.ui.map.PoiPins.showPins(context, it, emptyList(), null)
                com.excellentmindblow.navos.ui.map.PoiPins.showTraffic(it, emptyList())
            }
        }
    }
    // Reports on the route and open Smart Lanes on the map.
    LaunchedEffect(reports, openLanes, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.DriveLayers.show(it, reports, openLanes) } }
    // Arrived (the service ends the drive at the destination): the arrival card.
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val r = drive.route.value
        app.arrivedName = drive.arrivalName ?: destination?.name.orEmpty()
        app.arrivedSide = com.excellentmindblow.navos.NavFormat.arrivalSide(r?.steps?.lastOrNull()?.modifier)?.let { context.getString(it) }
        drive.end()
        map.style?.let {
            RouteLayers.show(it, null, emptyList())
            com.excellentmindblow.navos.ui.map.DriveLayers.show(it, emptyList(), emptyList())
        }
        app.go(Screen.ARRIVED)
    }
    // The route line (also after a reroute or a day / night switch).
    LaunchedEffect(route, styleTick) { map.style?.let { RouteLayers.show(it, route, emptyList()) } }
    // Follow you: tilted, heading up, the arrow low on the screen under the sign.
    val here = fix ?: phoneFix
    LaunchedEffect(here) {
        val l = here ?: return@LaunchedEffect
        val m = map.map ?: return@LaunchedEffect
        val top = with(density) { 220.dp.toPx().toDouble() }
        val bottom = with(density) { 200.dp.toPx().toDouble() }
        m.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(l.latitude, l.longitude))
                    .zoom(17.2).tilt(55.0)
                    .bearing(if (l.hasBearing() && l.speed > 1.5f) l.bearing.toDouble() else m.cameraPosition.bearing)
                    .padding(0.0, top, 0.0, bottom)
                    .build(),
            ),
            900,
        )
    }
    fun finish() {
        drive.end()
        map.style?.let {
            RouteLayers.show(it, null, emptyList())
            com.excellentmindblow.navos.ui.map.DriveLayers.show(it, emptyList(), emptyList())
        }
        here?.let { l -> map.moveTo(l.latitude, l.longitude) }
        app.go(Screen.HOME)
    }
    BackHandler { if (selectedId != null) drive.onTheWay.select(null) else askEnd = true }

    val routeState by routes.state.collectAsStateWithLifecycle()
    val routeSel by routes.selected.collectAsStateWithLifecycle()
    val strip = (routeState as? RouteViewModel.State.Ready)?.options?.getOrNull(routeSel)?.strip
    com.excellentmindblow.navos.ui.drive.DriveScreen(
        state = state,
        speedKmh = com.excellentmindblow.navos.Units.speed(((here?.speed ?: 0f) * 3.6f).toDouble()),
        limitKmh = limit,
        strip = strip,
        voiceOn = voiceOn,
        alert = alert,
        faster = faster,
        poiOn = poiOn,
        poiLoading = poiLoading,
        onPoiToggle = drive.onTheWay::toggle,
        onTakeFaster = drive::takeFaster,
        onKeepRoute = drive::keepRoute,
        station =
            pins.firstOrNull { it.poi.id == selectedId }?.let { pin ->
                {
                    val next = drive.onTheWay.nextAfter(pin)
                    val nextPoi = next?.nextAheadM?.let { drive.onTheWay.nameOfPinAt(pin.poi.kind, it) }
                    val nextName = nextPoi?.let { p -> com.excellentmindblow.navos.poi.FuelBrand.of(p.name)?.displayName ?: p.name.ifBlank { null } }
                    val s = state
                    val avg = if (s != null && s.durationRemainingSeconds > 1) s.distanceRemainingMeters / s.durationRemainingSeconds else 15.0
                    com.excellentmindblow.navos.ui.drive.StationCard(
                        pin = pin,
                        next = next,
                        nextName = nextName,
                        averageMs = avg,
                        onStop = { drive.stopAt(pin.poi) },
                        onGo = {
                            drive.goTo(pin.poi) {
                                android.widget.Toast.makeText(context, com.excellentmindblow.navos.tr("Couldn't get a route there", "Tak dapat laluan ke sana"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClose = { drive.onTheWay.select(null) },
                    )
                }
            },
        onVoice = { drive.setVoice(!voiceOn) },
        onReport = { picker = true },
        onEnd = { askEnd = true },
    )
    if (picker) {
        com.excellentmindblow.navos.ui.drive.ReportPicker(
            onPick = { kind ->
                picker = false
                val l = here
                if (l == null) {
                    android.widget.Toast.makeText(context, com.excellentmindblow.navos.tr("Waiting for GPS", "Tunggu GPS"), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val ok = runCatching { com.excellentmindblow.navos.traffic.RoadReports.send(kind, RoutePoint(l.latitude, l.longitude), if (l.hasBearing()) l.bearing.toDouble() else null) }.getOrNull() != null
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, if (ok) com.excellentmindblow.navos.tr("Reported. Thanks!", "Dah lapor. Terima kasih!") else com.excellentmindblow.navos.tr("Couldn't send the report", "Laporan tak dapat dihantar"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { picker = false },
        )
    }
    if (askEnd) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askEnd = false },
            containerColor = Jalan.colors.sheet,
            title = { androidx.compose.material3.Text(com.excellentmindblow.navos.tr("End the drive?", "Tamatkan perjalanan?"), style = com.excellentmindblow.navos.ui.theme.JalanType.title) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { askEnd = false; finish() }) {
                    androidx.compose.material3.Text(com.excellentmindblow.navos.tr("End", "Tamat"), style = com.excellentmindblow.navos.ui.theme.JalanType.label, color = com.excellentmindblow.navos.ui.theme.Traffic.Jam)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askEnd = false }) {
                    androidx.compose.material3.Text(com.excellentmindblow.navos.tr("Keep driving", "Teruskan"), style = com.excellentmindblow.navos.ui.theme.JalanType.label, color = Jalan.colors.ink)
                }
            },
        )
    }
}
