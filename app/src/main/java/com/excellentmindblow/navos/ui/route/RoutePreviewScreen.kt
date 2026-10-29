package com.excellentmindblow.navos.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.NavFormat
import com.excellentmindblow.navos.routing.TollPricer
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.components.JalanButton
import com.excellentmindblow.navos.ui.components.JalanSheet
import com.excellentmindblow.navos.ui.components.RouteShield
import com.excellentmindblow.navos.ui.components.TollPlate
import com.excellentmindblow.navos.ui.components.TripRibbon
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr
import com.excellentmindblow.navos.ui.theme.Traffic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route choice: From / To at the top, the route cards in a sheet at the
 * bottom (the chosen one with its trip ribbon), and the green Jalan button.
 */
@Composable
fun RoutePreviewScreen(
    destination: GeocodeResult?,
    state: RouteViewModel.State,
    selected: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onGo: () -> Unit,
) {
    val c = Jalan.colors
    Box(Modifier.fillMaxSize()) {
        // From / To, beside the back button.
        Row(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = CircleShape, color = c.card, shadowElevation = 6.dp, modifier = Modifier.size(48.dp)) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = tr("Back", "Kembali"), onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back", "Kembali"), tint = c.ink)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = c.card, shadowElevation = 6.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    FromToRow(dot = { Box(Modifier.size(12.dp).border(3.dp, c.route, CircleShape)) }, text = tr("Your location", "Lokasi anda"))
                    Box(Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth().height(1.dp).background(c.line))
                    FromToRow(dot = { Box(Modifier.size(12.dp).background(Traffic.Jam, RoundedCornerShape(2.dp))) }, text = destination?.name ?: "")
                }
            }
        }

        JalanSheet(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)) {
            when (state) {
                RouteViewModel.State.Idle, RouteViewModel.State.Loading -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        com.excellentmindblow.navos.ui.brand.NavLogoLoader(36.dp, ink = c.ink)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Finding routes…", "Cari laluan…"), style = JalanType.bodyStrong, color = c.ink)
                    }
                }
                is RouteViewModel.State.Failed -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (state.offline) tr("No connection. Check your data and try again.", "Tiada sambungan. Semak data dan cuba lagi.") else tr("No route found to this place.", "Tiada laluan ke tempat ini."),
                            style = JalanType.bodyStrong,
                            color = c.ink,
                        )
                        JalanButton(tr("Try again", "Cuba lagi"), onClick = onRetry)
                    }
                }
                is RouteViewModel.State.Ready -> {
                    val fastest = state.options.minOf { it.route.durationSeconds }
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.options.forEachIndexed { i, o -> RouteCard(o, i == selected, fastestS = fastest) { onSelect(i) } }
                        Spacer(Modifier.height(4.dp))
                        val sel = state.options.getOrNull(selected) ?: state.options.first()
                        JalanButton(tr("Jalan • ", "Jalan • ") + NavFormat.formatRouteMinutes(sel.route.durationSeconds), onClick = onGo)
                    }
                }
            }
        }
    }
}

@Composable
private fun FromToRow(
    dot: @Composable () -> Unit,
    text: String,
) {
    Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        dot()
        Spacer(Modifier.width(12.dp))
        Text(text, style = JalanType.label.copy(fontSize = 15.sp), color = Jalan.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RouteCard(
    o: RouteOption,
    selected: Boolean,
    fastestS: Double,
    onClick: () -> Unit,
) {
    val c = Jalan.colors
    val shape = RoundedCornerShape(18.dp)
    val r = o.route
    val arrive = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + (r.durationSeconds * 1000).toLong()))
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) c.ink else c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(NavFormat.formatRouteMinutes(r.durationSeconds), style = JalanType.cardTime.copy(fontSize = if (selected) 30.sp else 26.sp), color = c.ink)
            Spacer(Modifier.width(10.dp))
            Text(
                (if (selected) tr("Arrive $arrive · ", "Sampai $arrive · ") else "") + NavFormat.formatTripDistance(r.distanceMeters),
                style = JalanType.caption.copy(fontSize = 14.sp),
                color = c.inkQuiet,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            val fare = o.toll
            when {
                o.tolls == 0 -> TollPlate(tr("No toll", "Tiada tol"), free = true)
                fare != null && fare.totalSen > 0 -> TollPlate("TOL " + TollPricer.format(fare.totalSen) + if (fare.unpriced > 0) "+" else "", free = false)
                else -> TollPlate(tr("${o.tolls} TOL", "${o.tolls} TOL"), free = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val diff = Math.round((r.durationSeconds - fastestS) / 60.0).toInt()
            if (diff > 0) {
                Text("+$diff min · ", style = JalanType.caption.copy(fontSize = 14.sp), color = Traffic.Jam.copy(alpha = 0.9f))
            }
            if (o.viaRef != null || o.via != null) {
                Text(tr("via ", "ikut "), style = JalanType.caption.copy(fontSize = 14.sp), color = c.inkQuiet)
                if (o.viaRef != null) {
                    RouteShield(o.viaRef, small = true)
                    Spacer(Modifier.width(6.dp))
                }
                Text(o.via.orEmpty(), style = JalanType.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (diff <= 0 && selected) Text(tr("FASTEST", "PALING CEPAT"), style = JalanType.overline, color = Jkr.Green)
        }
        val strip = o.strip
        if (strip != null) TripRibbon(strip, mini = !selected)
    }
}
