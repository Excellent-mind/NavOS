package com.excellentmindblow.navos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.ui.AppViewModel
import com.excellentmindblow.navos.ui.NavOSApp

/**
 * The one activity. The UI is Jetpack Compose (ui/), on top of the engine
 * (navigation, routing, search, location, traffic, POIs). NavigationService's
 * notification opens this activity.
 */
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) app.startLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        Lang.applyToApp(prefs)
        Lang.load(prefs)
        Units.load(prefs)
        enableEdgeToEdge()
        setContent { NavOSApp(app) }
        if (hasLocation()) {
            app.startLocation()
        } else {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val PREFS_NAME = "navos_prefs"
    }
}
