package com.excellentmindblow.navos

import android.app.Activity
import android.os.Bundle

/**
 * Placeholder screen. The old UI was removed; only the engine is left
 * (navigation, routing, search, location, traffic, POIs, tolls, Smart
 * Lanes, signs planning). The new UI is built on top of it from here.
 * NavigationService's notification opens this activity.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.applyToApp(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    companion object {
        const val PREFS_NAME = "navos_prefs"
    }
}
