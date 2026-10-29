package com.excellentmindblow.navos.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** NavOS "Jalan" map styles (tools/mapstyle/build_style.py): Malaysian roads at real width with lane paint. */
const val MAP_STYLE_DAY = "asset://mapstyle/jalan-day.json"
const val MAP_STYLE_NIGHT = "asset://mapstyle/jalan-night.json"

/** What the screens can ask of the map. */
class MapController {
    var map: MapLibreMap? = null
        internal set
    var style: Style? = null
        internal set

    fun moveTo(
        lat: Double,
        lon: Double,
        zoom: Double = 15.5,
        animated: Boolean = true,
    ) {
        val m = map ?: return
        val update = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).tilt(0.0).bearing(0.0).build())
        if (animated) m.animateCamera(update, 600) else m.moveCamera(update)
    }
}

/**
 * The map, always under every screen: MapLibre inside Compose, in the Jalan
 * style (day / night), with the blue location dot. [onStyleReady] runs after
 * every style load so screens can add their layers (route, pins).
 */
@SuppressLint("MissingPermission")
@Composable
fun NavMap(
    controller: MapController,
    night: Boolean,
    location: Location?,
    modifier: Modifier = Modifier,
    onStyleReady: (Style) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = remember { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }
    var loadedNight by remember { mutableStateOf<Boolean?>(null) }

    // MapView follows the screen's lifecycle.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    // (Re)load the style when day / night changes.
    LaunchedEffect(night) {
        mapView.getMapAsync { m ->
            controller.map = m
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.isLogoEnabled = false
            m.addOnMapClickListener { onMapClick(it); false }
            if (loadedNight == night && m.style != null) return@getMapAsync
            m.setStyle(Style.Builder().fromUri(if (night) MAP_STYLE_NIGHT else MAP_STYLE_DAY)) { style ->
                loadedNight = night
                controller.style = style
                addRoadShield(context, style)
                runCatching {
                    m.locationComponent.activateLocationComponent(
                        LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false).build(),
                    )
                    m.locationComponent.isLocationComponentEnabled = true
                    m.locationComponent.renderMode = RenderMode.COMPASS
                    m.locationComponent.cameraMode = CameraMode.NONE
                }
                onStyleReady(style)
            }
        }
    }

    // Feed our own fused location (LiveLocationTracker) to the location dot.
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        val m = controller.map ?: return@LaunchedEffect
        runCatching { if (m.locationComponent.isLocationComponentActivated) m.locationComponent.forceLocationUpdate(loc) }
    }
}

/** Image id the Jalan map style uses for route numbers (E1, FT2). */
const val ROAD_SHIELD_ID = "shield-yellow"

/** The chrome-yellow JKR route shield behind road numbers; stretches around the text (icon-text-fit). */
fun addRoadShield(
    context: Context,
    style: Style,
) {
    val d = context.resources.displayMetrics.density
    val w = (24 * d).toInt()
    val h = (20 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.density = context.resources.displayMetrics.densityDpi
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF111111.toInt()
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 4 * d, 4 * d, p)
    p.color = 0xFFF6C400.toInt()
    c.drawRoundRect(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d, 3 * d, 3 * d, p)
    style.addImage(
        ROAD_SHIELD_ID,
        bmp,
        listOf(ImageStretches(6 * d, 18 * d)),
        listOf(ImageStretches(6 * d, 14 * d)),
        ImageContent(5 * d, 4 * d, 19 * d, 16 * d),
    )
}
