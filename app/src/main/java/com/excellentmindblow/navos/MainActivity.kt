package com.excellentmindblow.navos

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before any MapView touches its native state.
        MapLibre.getInstance(applicationContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { _ ->
                statusText.text = "NavOS (native) — map loaded OK"
            }
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
