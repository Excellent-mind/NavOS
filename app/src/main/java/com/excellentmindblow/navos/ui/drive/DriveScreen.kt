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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    /** What the sign says (exit number, shields, destinations). */
    sign: com.excellentmindblow.navos.signs.TurnSign?,
    faster: DriveViewModel.Faster?,
    poiOn: Set<com.excellentmindblow.navos.poi.PoiKind>,
    poiLoading: Set<com.excellentmindblow.navos.poi.PoiKind>,
    onPoiToggle: (com.excellentmindblow.navos.poi.PoiKind) -> Unit,
    onTakeFaster: () -> Unit,
    onKeepRoute: () -> Unit,
    station: (@Composable () -> Unit)?,
    camMode: com.excellentmindblow.navos.navigation.DriveCamera.Mode,
    onRecentre: () -> Unit,
    onOverview: () -> Unit,
    /** Driving along (not stopped): the open sheet tucks itself away. */
    moving: Boolean,
    onCloseStation: () -> Unit,
    onSheetHeight: (Int) -> Unit,
    onVoice: () -> Unit,
    onReport: () -> Unit,
    onEnd: () -> Unit,
) {
    val c = Jalan.colors
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var touchedAt by remember { mutableStateOf(0L) }
    var sheetPx by remember { mutableStateOf(0f) }
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
                    road = sign?.text ?: state.instruction,
                    roadRef = state.roadRef,
                    exit = sign?.exit,
                    refs = sign?.refs,
                    expressway = sign?.expressway,
                    lanes = if (showLanes) state.lanes else null,
                    thenType = if (showThen) state.nextManeuverType else null,
                    thenModifier = state.nextModifier,
                    thenText = state.nextRoadName ?: state.nextInstruction,
                )
            }
            // Faster route first (it needs an answer), else what's on the road ahead.
            androidx.compose.animation.AnimatedVisibility(
                visible = faster != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
            ) {
                val last = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(faster) }
                if (faster != null) last.value = faster
                last.value?.let { FasterCard(it, state?.durationRemainingSeconds ?: 0.0, onTakeFaster, onKeepRoute) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = faster == null && alert != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
            ) {
                val last = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(alert) }
                if (alert != null) last.value = alert
                last.value?.let { AlertBanner(it) }
            }
        }

        // Buttons float just above the sheet, wherever the sheet is.
        val bottomGap = with(androidx.compose.ui.platform.LocalDensity.current) { sheetPx.toDp() } + 16.dp
        // Report and voice on the left.
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = bottomGap), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = bottomGap),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Whole route / back to following.
            val overview = camMode == com.excellentmindblow.navos.navigation.DriveCamera.Mode.OVERVIEW
            Surface(shape = CircleShape, color = if (overview) c.ink else c.card, shadowElevation = 6.dp, modifier = Modifier.size(52.dp)) {
                Box(Modifier.clickable(role = Role.Button, onClick = onOverview), contentAlignment = Alignment.Center) {
                    Icon(
                        JalanIcons.Overview,
                        contentDescription = if (overview) tr("Back to driving view", "Kembali ke paparan pemanduan") else tr("See the whole route", "Lihat seluruh laluan"),
                        tint = if (overview) c.onInk else c.ink,
                    )
                }
            }
            SpeedSigns(speedKmh, limitKmh)
        }
        // The map was moved away from you: one tap back.
        androidx.compose.animation.AnimatedVisibility(
            visible = camMode != com.excellentmindblow.navos.navigation.DriveCamera.Mode.FOLLOW,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomGap),
        ) {
            Row(
                Modifier.height(48.dp).background(c.ink, RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onRecentre).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(JalanIcons.Recentre, contentDescription = null, tint = c.onInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Re-centre", "Pusatkan semula"), style = JalanType.label, color = c.onInk)
            }
        }

        // The sheet: open (petrol / EV / parking / train, arrival, trip strip) or tucked down to one
        // line so the map shows more road. Drag or tap the handle to switch; while driving it tucks
        // itself away after a few seconds, and opens for a station card.
        val open = expanded || station != null
        LaunchedEffect(expanded, moving, touchedAt, station != null) {
            if (expanded && moving && station == null) {
                kotlinx.coroutines.delay(AUTO_TUCK_MS)
                expanded = false
            }
        }
        val remaining = state?.durationRemainingSeconds ?: 0.0
        val arrive = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (remaining * 1000).toLong()))
        val left = if (state == null) "" else NavFormat.formatTripDuration(remaining) + " · " + NavFormat.formatTripDistance(state.distanceRemainingMeters)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { sheetPx = it.height.toFloat(); onSheetHeight(it.height) }
                .pointerInput(station != null) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = {
                            if (station == null) {
                                if (dy < -24f) expanded = true else if (dy > 24f) expanded = false
                            } else if (dy > 48f) {
                                onCloseStation()
                            }
                            touchedAt = System.currentTimeMillis()
                        },
                    ) { change, amount ->
                        change.consume()
                        dy += amount
                    }
                },
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = c.sheet,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = if (open) 16.dp else 10.dp).animateContentSize(androidx.compose.animation.core.spring(stiffness = 500f))) {
                    // Handle: tap to open / tuck.
                    Box(
                        Modifier.fillMaxWidth().height(24.dp).clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = if (open) tr("Show less", "Tunjuk kurang") else tr("Show more", "Tunjuk lagi"),
                        ) {
                            if (station != null) onCloseStation() else expanded = !expanded
                            touchedAt = System.currentTimeMillis()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(40.dp, 5.dp).background(c.line, CircleShape)) }
                    androidx.compose.animation.AnimatedContent(
                        targetState = if (station != null) 2 else if (open) 1 else 0,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideInVertically { it / 8 })
                                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)))
                        },
                        label = "drive-sheet",
                    ) { mode ->
                        when (mode) {
                            2 -> station?.invoke()
                            1 ->
                                Column {
                                    PoiChips(poiOn, poiLoading) { onPoiToggle(it); touchedAt = System.currentTimeMillis() }
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                            Text(if (state == null) "—" else arrive, style = JalanType.eta, color = c.ink)
                                            Text(left, style = JalanType.caption.copy(fontSize = 16.sp), color = c.inkQuiet)
                                        }
                                        EndPill(onEnd, big = true)
                                    }
                                    if (strip != null) {
                                        Spacer(Modifier.height(10.dp))
                                        TripRibbon(strip, Modifier.fillMaxWidth())
                                    }
                                }
                            else ->
                                // Tucked: one line, tap it to open.
                                Row(
                                    Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) {
                                        expanded = true
                                        touchedAt = System.currentTimeMillis()
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (state == null) "—" else arrive, style = JalanType.eta.copy(fontSize = 30.sp), color = c.ink)
                                    Spacer(Modifier.width(12.dp))
                                    Text(left, style = JalanType.caption.copy(fontSize = 15.sp), color = c.inkQuiet, modifier = Modifier.weight(1f), maxLines = 1)
                                    EndPill(onEnd, big = false)
                                }
                        }
                    }
                }
            }
        }
    }
}

/** Sheet open while driving tucks itself down after this long without a touch. */
private const val AUTO_TUCK_MS = 6_000L

@Composable
private fun EndPill(
    onEnd: () -> Unit,
    big: Boolean,
) {
    val c = Jalan.colors
    Box(
        Modifier.height(if (big) 52.dp else 44.dp).background(c.ink, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onEnd).padding(horizontal = if (big) 22.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tr("End", "Tamat"), style = JalanType.bodyStrong, color = c.onInk)
    }
}
