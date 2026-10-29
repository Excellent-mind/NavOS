package com.excellentmindblow.navos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
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
    val mapLongTap = remember { mutableStateOf<((org.maplibre.android.geometry.LatLng) -> Unit)?>(null) }
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize()) {
        val c = Jalan.colors
        val driveShown by drive.shown.collectAsStateWithLifecycle()
        NavMap(
            controller = map,
            night = night,
            location = if (screen == Screen.DRIVE) driveShown ?: location else location,
            driving = screen == Screen.DRIVE,
            modifier = Modifier.fillMaxSize(),
            onStyleReady = { style ->
                RouteLayers.install(style, c.route.toArgb(), c.inkQuiet.copy(alpha = 0.55f).toArgb(), c.card.toArgb())
                com.excellentmindblow.navos.ui.map.DriveLayers.install(style)
                com.excellentmindblow.navos.ui.map.PoiPins.install(style)
                com.excellentmindblow.navos.ui.map.PoiPins.installArea(style)
                com.excellentmindblow.navos.ui.map.DropPin.install(context, style)
                com.excellentmindblow.navos.ui.map.RouteBubbles.install(style)
                styleTick++
            },
            onMapClick = { mapTap.value?.invoke(it) },
            onMapLongClick = { mapLongTap.value?.invoke(it) },
        )

        when (screen) {
            Screen.HOME -> {
                val pinned by places.pinned.collectAsStateWithLifecycle()
                // Long-press anywhere: a pin and its address. Tap a named place: that place. Tap elsewhere: close.
                androidx.compose.runtime.DisposableEffect(Unit) {
                    mapLongTap.value = { ll -> places.dropPin(ll.latitude, ll.longitude) }
                    mapTap.value = { ll ->
                        val m = map.map
                        val hit =
                            m?.let {
                                val pt = it.projection.toScreenLocation(ll)
                                val slop = with(density) { 16.dp.toPx() }
                                runCatching { it.queryRenderedFeatures(android.graphics.RectF(pt.x - slop, pt.y - slop, pt.x + slop, pt.y + slop), *TAPPABLE_LAYERS) }
                                    .getOrDefault(emptyList())
                                    .firstOrNull { f -> f.hasProperty("name") }
                            }
                        if (hit != null) {
                            val g = hit.geometry() as? org.maplibre.geojson.Point
                            val name = hit.getStringProperty("name:latin")?.takeIf { it.isNotBlank() } ?: hit.getStringProperty("name")
                            val cat = if (hit.hasProperty("class")) com.excellentmindblow.navos.search.PlaceCategory.label(hit.getStringProperty("class"), hit.getStringProperty("subclass")) else null
                            places.dropPin(g?.latitude() ?: ll.latitude, g?.longitude() ?: ll.longitude, name, cat)
                        } else if (places.pinned.value != null) {
                            places.clearPin()
                        }
                    }
                    onDispose {
                        mapLongTap.value = null
                        mapTap.value = null
                        places.clearPin()
                    }
                }
                // Live traffic on the roads around where the map is looking, every 2 minutes.
                LaunchedEffect(styleTick) {
                    while (true) {
                        val t = map.target
                        val z = map.map?.cameraPosition?.zoom ?: 0.0
                        val style = map.style
                        if (t != null && style != null && z >= 11.0) {
                            val found =
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { com.excellentmindblow.navos.traffic.TrafficClient.area(t.latitude - 0.15, t.longitude - 0.15, t.latitude + 0.15, t.longitude + 0.15) }.getOrNull()
                                }
                            if (found != null) com.excellentmindblow.navos.ui.map.PoiPins.showArea(style, found)
                        }
                        kotlinx.coroutines.delay(if (t == null) 5_000 else 120_000)
                    }
                }
                LaunchedEffect(pinned, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.DropPin.show(it, pinned?.latitude, pinned?.longitude) } }
                FloatingSearchBar(
                    onSearch = { openSearch(PlacesViewModel.Purpose.GO) },
                    onSettings = { app.go(Screen.SETTINGS) },
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
                )
                MapButtons(map, location, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 84.dp, end = 16.dp))
                val p = pinned
                if (p != null) {
                    BackHandler { places.clearPin() }
                    com.excellentmindblow.navos.ui.home.PlaceCard(
                        pinned = p,
                        distanceM = p.place?.let { places.distanceTo(it) },
                        onGo = { p.place?.let { app.routeTo(places.picked(it)) } },
                        onSaveHome = {
                            places.savePinned(com.excellentmindblow.navos.search.SavedPlaces.HOME)
                            android.widget.Toast.makeText(context, com.excellentmindblow.navos.tr("Saved as Home", "Disimpan sebagai Rumah"), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onSaveWork = {
                            places.savePinned(com.excellentmindblow.navos.search.SavedPlaces.WORK)
                            android.widget.Toast.makeText(context, com.excellentmindblow.navos.tr("Saved as Work", "Disimpan sebagai Kerja"), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onClose = places::clearPin,
                        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
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
                // Draw the routes with a bubble each (time, toll); the chosen one on top in blue.
                val routeArgb = Jalan.colors.route.toArgb()
                LaunchedEffect(state, selected, styleTick) {
                    val s = state as? RouteViewModel.State.Ready ?: return@LaunchedEffect
                    val style = map.style ?: return@LaunchedEffect
                    val sel = s.options.getOrNull(selected)?.route
                    RouteLayers.show(style, sel, s.options.map { it.route }.filter { it !== sel })
                    val bubbles =
                        s.options.mapIndexed { i, o ->
                            val fare = o.toll
                            val extra =
                                when {
                                    o.tolls == 0 -> com.excellentmindblow.navos.tr("No toll", "Tiada tol")
                                    fare != null && fare.totalSen > 0 -> com.excellentmindblow.navos.routing.TollPricer.format(fare.totalSen) + if (fare.unpriced > 0) "+" else ""
                                    else -> com.excellentmindblow.navos.tr("Toll", "Tol")
                                }
                            com.excellentmindblow.navos.ui.map.RouteBubbles.Bubble(i, com.excellentmindblow.navos.NavFormat.formatTripDuration(o.route.durationSeconds), extra, i == selected)
                        }
                    com.excellentmindblow.navos.ui.map.RouteBubbles.show(context, style, s.options.map { it.route }, bubbles, routeArgb)
                }
                // Frame them between the From / To card and the sheet, once per plan (not on every pick).
                LaunchedEffect(state, styleTick) {
                    val s = state as? RouteViewModel.State.Ready ?: return@LaunchedEffect
                    map.map?.let { m -> with(density) { RouteLayers.frame(m, s.options.map { it.route }, 200.dp.roundToPx(), 460.dp.roundToPx(), 56.dp.roundToPx()) } }
                }
                // Tap a bubble or near a line to pick that route.
                androidx.compose.runtime.DisposableEffect(Unit) {
                    mapTap.value = { ll ->
                        val m = map.map
                        if (m != null) {
                            val pt = m.projection.toScreenLocation(ll)
                            val slop = with(density) { 14.dp.toPx() }
                            val box = android.graphics.RectF(pt.x - slop, pt.y - slop, pt.x + slop, pt.y + slop)
                            val hit =
                                runCatching {
                                    m.queryRenderedFeatures(android.graphics.RectF(pt.x - 4, pt.y - 4, pt.x + 4, pt.y + 4), com.excellentmindblow.navos.ui.map.RouteBubbles.LAYER_BUBBLES).firstOrNull()
                                        ?: m.queryRenderedFeatures(box, com.excellentmindblow.navos.ui.map.RouteBubbles.LAYER_HIT).firstOrNull()
                                }.getOrNull()
                            hit?.getNumberProperty(com.excellentmindblow.navos.ui.map.RouteBubbles.PROP_INDEX)?.toInt()?.let(routes::select)
                        }
                    }
                    onDispose {
                        mapTap.value = null
                        map.style?.let { com.excellentmindblow.navos.ui.map.RouteBubbles.clear(it) }
                    }
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
    // Less on the map while driving (petrol, tolls, hospitals, stations stay).
    LaunchedEffect(styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.DriveDeclutter.set(it, true) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { map.style?.let { com.excellentmindblow.navos.ui.map.DriveDeclutter.set(it, false) } } }
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
    // The route behind you disappears; the next turn is painted on the line.
    val driven by drive.driven.collectAsStateWithLifecycle()
    val turnArrow by drive.turn.collectAsStateWithLifecycle()
    LaunchedEffect(driven, styleTick) { map.style?.let { RouteLayers.driven(it, driven) } }
    LaunchedEffect(turnArrow, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.DriveLayers.showTurn(it, turnArrow?.line, turnArrow?.bearing ?: 0.0) } }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            map.style?.let {
                RouteLayers.driven(it, 0.0)
                com.excellentmindblow.navos.ui.map.DriveLayers.showTurn(it, null, 0.0)
            }
        }
    }
    // Speed cameras on the route.
    val cams by drive.cameras.collectAsStateWithLifecycle()
    LaunchedEffect(cams, styleTick) { map.style?.let { com.excellentmindblow.navos.ui.map.DriveLayers.showCameras(it, cams) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { map.style?.let { com.excellentmindblow.navos.ui.map.DriveLayers.showCameras(it, emptyList()) } } }
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
    // The camera: follows you (zoom by speed, closer at turns), or the driver moved the map (free,
    // follows again after 12 s), or the whole way left (overview, until Re-centre).
    val shownFix by drive.shown.collectAsStateWithLifecycle()
    val here = shownFix ?: fix ?: phoneFix
    var sheetPx by remember { mutableStateOf(0) }
    var camMode by remember { mutableStateOf(com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW) }
    var lastGesture by remember { mutableStateOf(0L) }
    val camView = remember { mutableStateOf<com.excellentmindblow.navos.navigation.DriveCamera.View?>(null) }
    var lastCamAt by remember { mutableStateOf(0L) }
    val screenH = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        map.onUserGesture = {
            camMode = com.excellentmindblow.navos.navigation.DriveCamera.Mode.FREE
            lastGesture = System.currentTimeMillis()
        }
        onDispose { map.onUserGesture = null }
    }
    LaunchedEffect(camMode, lastGesture) {
        if (camMode != com.excellentmindblow.navos.navigation.DriveCamera.Mode.FREE) return@LaunchedEffect
        kotlinx.coroutines.delay(com.excellentmindblow.navos.navigation.DriveCamera.FREE_RESUME_MS)
        camMode = com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW
    }
    LaunchedEffect(camMode) {
        if (camMode != com.excellentmindblow.navos.navigation.DriveCamera.Mode.OVERVIEW) return@LaunchedEffect
        val m = map.map ?: return@LaunchedEffect
        val r = drive.route.value ?: return@LaunchedEffect
        // What's left of the way, from where you are.
        val l = here
        val from = if (l == null) 0 else com.excellentmindblow.navos.navigation.GeoUtils.snapToPolyline(RoutePoint(l.latitude, l.longitude), r.geometry)?.segmentIndex ?: 0
        val rest = r.copy(geometry = r.geometry.subList(from.coerceIn(0, r.geometry.size - 2), r.geometry.size))
        m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(org.maplibre.android.camera.CameraPosition.Builder(m.cameraPosition).tilt(0.0).bearing(0.0).build()), 300)
        kotlinx.coroutines.delay(320)
        RouteLayers.frame(m, listOf(rest), with(density) { 200.dp.roundToPx() }, with(density) { 280.dp.roundToPx() }, with(density) { 48.dp.roundToPx() })
    }
    LaunchedEffect(here, camMode) {
        if (camMode != com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW) return@LaunchedEffect
        val l = here ?: return@LaunchedEffect
        val m = map.map ?: return@LaunchedEffect
        val s = drive.state.value
        val turnM = s?.takeIf { com.excellentmindblow.navos.navigation.DriveCamera.isRealTurn(it.maneuverType, it.modifier) }?.distanceToNextTurnMeters
        val target = com.excellentmindblow.navos.navigation.DriveCamera.target(if (l.hasSpeed()) l.speed * 3.6 else 0.0, turnM)
        val now = System.currentTimeMillis()
        val v = com.excellentmindblow.navos.navigation.DriveCamera.step(camView.value, target, if (lastCamAt == 0L) 1.0 else (now - lastCamAt) / 1000.0)
        camView.value = v
        lastCamAt = now
        // The car sits [carAt] of the way down the clear band between the sign and the bottom sheet.
        val bandTop = with(density) { 190.dp.toPx() }
        val bandBottom = if (sheetPx > 0) sheetPx.toFloat() else with(density) { 250.dp.toPx() }
        val y = bandTop + (v.carAt * (screenH - bandTop - bandBottom)).toFloat()
        val padTop = (2 * y - screenH).coerceAtLeast(0f).toDouble()
        val padBottom = (screenH - 2 * y).coerceAtLeast(0f).toDouble()
        m.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(org.maplibre.android.geometry.LatLng(l.latitude, l.longitude))
                    .zoom(v.zoom).tilt(v.tilt)
                    .bearing(if (l.hasBearing() && l.speed > 1.5f) l.bearing.toDouble() else m.cameraPosition.bearing)
                    .padding(0.0, padTop, 0.0, padBottom)
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
        sign =
            state?.let { st ->
                val steps = route?.steps.orEmpty()
                com.excellentmindblow.navos.signs.TurnSign.of(steps.getOrNull(st.currentStepIndex), steps.getOrNull(st.currentStepIndex - 1)?.ref, st.instruction)
            },
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
        camMode = camMode,
        onRecentre = { camMode = com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW },
        onOverview = {
            camMode =
                if (camMode == com.excellentmindblow.navos.navigation.DriveCamera.Mode.OVERVIEW) com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW
                else com.excellentmindblow.navos.navigation.DriveCamera.Mode.OVERVIEW
        },
        moving = (here?.speed ?: 0f) > 4f,
        onCloseStation = { drive.onTheWay.select(null) },
        onSheetHeight = { sheetPx = it },
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

/** Map labels a tap can pick (names of places, shops, stations). */
private val TAPPABLE_LAYERS = arrayOf("poi-label-15", "poi-label-16", "poi-label-17", "poi-label-18", "poi-major", "transit-label", "toll-booth", "place-label")

/** Compass (only when the map is turned) and "back to me". */
@Composable
private fun MapButtons(
    map: MapController,
    location: android.location.Location?,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    val turned = kotlin.math.abs(((map.bearing + 540.0) % 360.0) - 180.0) > 2.0
    val awayFromMe =
        location != null &&
            map.target?.let { t ->
                com.excellentmindblow.navos.navigation.GeoUtils.distanceMeters(RoutePoint(t.latitude, t.longitude), RoutePoint(location.latitude, location.longitude)) > 60.0
            } ?: false
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        androidx.compose.animation.AnimatedVisibility(turned, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = c.card, shadowElevation = 6.dp, modifier = Modifier.size(48.dp)) {
                Box(Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = map::resetNorth), contentAlignment = Alignment.Center) {
                    // Needle: red end points north, turns with the map.
                    androidx.compose.foundation.Canvas(Modifier.size(26.dp).graphicsLayer { rotationZ = -map.bearing.toFloat() }) {
                        val w = size.width
                        val h = size.height
                        val north = androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, 0f); lineTo(w * 0.68f, h / 2); lineTo(w * 0.32f, h / 2); close() }
                        val south = androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, h); lineTo(w * 0.68f, h / 2); lineTo(w * 0.32f, h / 2); close() }
                        drawPath(north, com.excellentmindblow.navos.ui.theme.Traffic.Jam)
                        drawPath(south, c.inkQuiet)
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(awayFromMe, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(), exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = c.card, shadowElevation = 6.dp, modifier = Modifier.size(48.dp)) {
                Box(
                    Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = com.excellentmindblow.navos.tr("Show my location", "Tunjuk lokasi saya")) {
                        location?.let { map.moveTo(it.latitude, it.longitude, zoom = 16.0) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(com.excellentmindblow.navos.ui.components.JalanIcons.Recentre, contentDescription = com.excellentmindblow.navos.tr("Show my location", "Tunjuk lokasi saya"), tint = c.route)
                }
            }
        }
    }
}
