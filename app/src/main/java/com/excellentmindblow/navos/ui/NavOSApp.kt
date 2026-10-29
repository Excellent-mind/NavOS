package com.excellentmindblow.navos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.excellentmindblow.navos.ui.components.FloatingSearchBar
import com.excellentmindblow.navos.ui.map.MapController
import com.excellentmindblow.navos.ui.map.NavMap

/**
 * The whole app: the map underneath, and the current screen's panels on top
 * of it (like Google Maps / Waze, the map never leaves).
 */
@Composable
fun NavOSApp(app: AppViewModel = viewModel()) {
    val screen by app.screen.collectAsStateWithLifecycle()
    val location by app.location.collectAsStateWithLifecycle()
    val night = isSystemInDarkTheme()
    val map = remember { MapController() }

    // Centre on you at the first fix.
    LaunchedEffect(location) {
        val l = location ?: return@LaunchedEffect
        if (!app.centredOnce && map.map != null) {
            app.centredOnce = true
            map.moveTo(l.latitude, l.longitude, animated = false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavMap(controller = map, night = night, location = location, modifier = Modifier.fillMaxSize())

        when (screen) {
            Screen.HOME ->
                FloatingSearchBar(
                    onSearch = { app.go(Screen.SEARCH) },
                    onSettings = { app.go(Screen.SETTINGS) },
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            else -> Unit // Phases 2-5
        }
    }
}
