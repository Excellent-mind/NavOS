package com.excellentmindblow.navos.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.NavFormat
import com.excellentmindblow.navos.navigation.NavigationState
import com.excellentmindblow.navos.navigation.TripStrip
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.components.GantrySign
import com.excellentmindblow.navos.ui.components.JalanIcons
import com.excellentmindblow.navos.ui.components.JalanSheet
import com.excellentmindblow.navos.ui.components.SpeedSigns
import com.excellentmindblow.navos.ui.components.TripRibbon
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Traffic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Driving: the gantry sign at the top, speed on the right, Report and voice
 * on the left, and at the bottom when you arrive (big), time and distance
 * left, and End. The trip ahead runs under it as a strip of road.
 */
@Composable
fun DriveScreen(
    state: NavigationState?,
    speedKmh: Int,
    limitKmh: Int?,
    strip: TripStrip.Strip?,
    voiceOn: Boolean,
    alert: DriveViewModel.Alert?,
    onVoice: () -> Unit,
    onReport: () -> Unit,
    onEnd: () -> Unit,
) {
    val c = Jalan.colors
    Box(Modifier.fillMaxSize()) {
        // The sign.
        Column(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == null) {
                GantrySign("depart", null, "", tr("Starting navigation…", "Mula navigasi…"), null, null, null, null, null)
            } else if (state.offRoute) {
                GantrySign("continue", null, "", tr("Finding a new route…", "Cari laluan baru…"), null, null, null, null, null)
            } else {
                val showLanes = state.distanceToNextTurnMeters <= 600 && !state.isArrival
                val showThen = state.nextManeuverType != null && state.distanceToNextTurnMeters <= 400 && !state.isArrival
                GantrySign(
                    maneuverType = state.maneuverType,
                    modifier = state.modifier,
                    distance = NavFormat.formatCountdownDistance(state.distanceToNextTurnMeters),
                    road = state.instruction,
                    roadRef = state.roadRef,
                    lanes = if (showLanes) state.lanes else null,
                    thenType = if (showThen) state.nextManeuverType else null,
                    thenModifier = state.nextModifier,
                    thenText = state.nextRoadName ?: state.nextInstruction,
                )
            }
            if (alert != null) AlertBanner(alert)
        }

        val bottomGap = if (strip != null) 210.dp else 150.dp
        // Report and voice on the left.
        Column(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 14.dp, bottom = bottomGap), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Traffic.Report, shadowElevation = 6.dp, modifier = Modifier.size(56.dp)) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = tr("Report", "Lapor"), onClick = onReport), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Warning, contentDescription = tr("Report something on the road", "Lapor sesuatu di jalan"), tint = Color(0xFF111111))
                }
            }
            Surface(shape = CircleShape, color = c.card, shadowElevation = 6.dp, modifier = Modifier.size(52.dp)) {
                Box(Modifier.clickable(role = Role.Button, onClick = onVoice), contentAlignment = Alignment.Center) {
                    Icon(if (voiceOn) JalanIcons.Volume else JalanIcons.VolumeOff, contentDescription = if (voiceOn) tr("Mute voice", "Senyapkan suara") else tr("Turn voice on", "Hidupkan suara"), tint = c.ink)
                }
            }
        }
        // Speed on the right.
        SpeedSigns(speedKmh, limitKmh, Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 14.dp, bottom = bottomGap))

        // Arrival bar.
        JalanSheet(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)) {
            val remaining = state?.durationRemainingSeconds ?: 0.0
            val arrive = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (remaining * 1000).toLong()))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(if (state == null) "—" else arrive, style = JalanType.eta, color = c.ink)
                    Text(
                        if (state == null) "" else NavFormat.formatTripDuration(remaining) + " · " + NavFormat.formatTripDistance(state.distanceRemainingMeters),
                        style = JalanType.caption.copy(fontSize = 16.sp),
                        color = c.inkQuiet,
                    )
                }
                Box(
                    Modifier.height(52.dp).background(c.ink, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onEnd).padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("End", "Tamat"), style = JalanType.bodyStrong, color = c.onInk)
                }
            }
            if (strip != null) {
                Spacer(Modifier.height(10.dp))
                TripRibbon(strip, Modifier.fillMaxWidth())
            }
        }
    }
}

