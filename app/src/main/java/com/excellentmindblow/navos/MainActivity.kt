package com.excellentmindblow.navos

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// Same free, no-API-key, production-safe tile style used by the earlier
// React Native build. https://openfreemap.org
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Minimal native Android entry point - deliberately kept to just "does the
 * map render" for the first native build, before porting over NavOS's
 * other screens/logic (search, routing, turn-by-turn voice) from the
 * archived React Native app in /legacy-expo-rn. See MapView lifecycle
 * forwarding below: MapLibre's MapView needs every Activity lifecycle
 * callback forwarded to it manually, unlike a typical Android view.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before any MapView touches its native state.
        MapLibre.getInstance(applicationContext)

        super.onCreate(savedInstanceState)

        // Classic, opt-in "draw behind the status bar" (available since API
        // 21, fully under our control) for a true full-screen map - NOT the
        // same thing as Android 15+'s mandatory edge-to-edge enforcement
        // that caused the real crash in the old React Native build. We
        // deliberately target SDK 34 so that enforcement never applies;
        // this is just making the status bar transparent on purpose.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL))
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
