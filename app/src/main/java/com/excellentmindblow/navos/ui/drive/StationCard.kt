package com.excellentmindblow.navos.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.poi.FuelBrand
import com.excellentmindblow.navos.poi.PoiAlongRoute
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.components.JalanButton
import com.excellentmindblow.navos.ui.components.JalanIcons
import com.excellentmindblow.navos.ui.map.PoiPins
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr
import com.excellentmindblow.navos.ui.theme.Traffic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petrol / EV / parking / transit toggles in the drive sheet. */
@Composable
fun PoiChips(
    on: Set<PoiKind>,
    loading: Set<PoiKind>,
    onToggle: (PoiKind) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PoiKind.entries.forEach { k ->
            val label =
                when (k) {
                    PoiKind.FUEL -> tr("Petrol", "Minyak")
                    PoiKind.EV -> tr("EV", "EV")
                    PoiKind.PARKING -> tr("Parking", "Parkir")
                    PoiKind.TRANSIT -> tr("Train", "Tren")
                }
            ToggleChip(label + if (k in loading) " …" else "", iconOf(k), k in on) { onToggle(k) }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Jalan.colors
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (selected) c.ink else c.card, RoundedCornerShape(22.dp))
            .border(1.dp, if (selected) c.ink else c.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.onInk else c.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = JalanType.label, color = if (selected) c.onInk else c.ink, maxLines = 1)
    }
}

fun iconOf(k: PoiKind) =
    when (k) {
        PoiKind.FUEL -> JalanIcons.Fuel
        PoiKind.EV -> JalanIcons.Bolt
        PoiKind.PARKING -> JalanIcons.Parking
        PoiKind.TRANSIT -> JalanIcons.Train
    }

/** Facility names from the map, in everyday Malay when that's the language. */
private fun facility(label: String): String =
    when (label) {
        "Toilet" -> tr("Toilet", "Tandas")
        "Shop" -> tr("Shop", "Kedai")
        "EV charging" -> tr("EV charging", "Cas EV")
        "Car wash" -> tr("Car wash", "Cuci kereta")
        "Air pump" -> tr("Air pump", "Pam angin")
        "24 hours" -> tr("24 hours", "24 jam")
        "Free" -> tr("Free", "Percuma")
        "Paid" -> tr("Paid", "Berbayar")
        "Covered" -> tr("Covered", "Berbumbung")
        "Disabled parking" -> tr("Disabled parking", "Parkir OKU")
        "Free to use" -> tr("Free to use", "Percuma")
        else -> label
    }

/**
 * "Fuel up here or carry on?" - for a station on the way: how far ahead and
 * when, which side, what it has, what comes after it, and Stop here. For a
 * car park or train station at the destination: how far from it, and Go here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationCard(
    pin: OnTheWay.Pin,
    next: PoiAlongRoute.Next?,
    nextName: String?,
    averageMs: Double,
    onStop: () -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Jalan.colors
    val context = LocalContext.current
    val poi = pin.poi
    val brand = if (poi.kind == PoiKind.FUEL) FuelBrand.of(poi.name) else null
    val badge = remember(poi.id) { PoiPins.draw(context, poi.kind, brand, poi.lines.firstOrNull()?.badge?.take(3), poi.lines.firstOrNull()?.colour).asImageBitmap() }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // Who.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(badge, contentDescription = brand?.displayName, modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(poi.name.ifBlank { kindName(poi.kind) }, style = JalanType.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(kindName(poi.kind), style = JalanType.caption, color = c.inkQuiet, maxLines = 1)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.field).clickable(role = Role.Button, onClickLabel = tr("Close", "Tutup"), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = tr("Close", "Tutup"), tint = c.ink) }
        }
        Spacer(Modifier.height(12.dp))

        // Where.
        val spot = pin.spot
        if (spot != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (spot.aheadM < 0) {
                    Text(tr("Here", "Sini"), style = JalanType.cardTime, color = c.ink)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("just passed", "baru lepas"), style = JalanType.body, color = c.inkQuiet, modifier = Modifier.padding(bottom = 3.dp))
                } else {
                    Text(Units.short(spot.aheadM), style = JalanType.cardTime, color = c.ink)
                    Spacer(Modifier.width(8.dp))
                    val min = Math.round(spot.aheadM / averageMs.coerceAtLeast(5.0) / 60.0).toInt().coerceAtLeast(1)
                    Text(tr("ahead · about $min min", "di depan · lebih kurang $min min"), style = JalanType.body, color = c.inkQuiet, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Jkr.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                if (spot.offRouteM <= PoiAlongRoute.ON_LINE_M) {
                    Text(tr("On your route", "Atas laluan anda"), style = JalanType.bodyStrong, color = Jkr.Green)
                } else {
                    Text(tr("On your side", "Sebelah anda"), style = JalanType.bodyStrong, color = Jkr.Green)
                    Text(tr(" · ${Units.short(spot.offRouteM)} off route", " · ${Units.short(spot.offRouteM)} dari laluan"), style = JalanType.body, color = c.inkQuiet)
                }
            }
        } else if (pin.fromDestinationM != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Units.short(pin.fromDestinationM), style = JalanType.cardTime, color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text(tr("from your destination", "dari destinasi anda"), style = JalanType.body, color = c.inkQuiet, modifier = Modifier.padding(bottom = 3.dp))
            }
        }

        // Train lines, Park & Ride.
        if (poi.lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                poi.lines.distinctBy { it.badge }.take(6).forEach { line ->
                    Box(Modifier.background(line.colour?.let { Color(it) } ?: c.ink, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(line.badge, style = JalanType.label.copy(fontSize = 13.sp), color = Color.White, maxLines = 1)
                    }
                }
            }
        }
        poi.parkAndRide?.let { pr ->
            Spacer(Modifier.height(6.dp))
            Text(tr("Park & Ride next door", "Park & Ride di sebelah") + (pr.spaces?.let { tr(" · $it bays", " · $it petak") } ?: ""), style = JalanType.body, color = c.ink)
        }

        // What it has (only what the map lists - not listed isn't "no").
        val listed = poi.facilities.filter { it.listed }
        if (listed.isNotEmpty() || poi.spec != null) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listed.forEach { f ->
                    Box(Modifier.background(c.field, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(facility(f.label), style = JalanType.label.copy(fontSize = 13.sp), color = c.ink)
                    }
                }
            }
            poi.spec?.let {
                Spacer(Modifier.height(6.dp))
                Text(it.lines().take(3).joinToString(" · "), style = JalanType.caption, color = c.inkQuiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        // What comes after it.
        if (next != null && (poi.kind == PoiKind.FUEL || poi.kind == PoiKind.EV)) {
            Spacer(Modifier.height(10.dp))
            val fuel = poi.kind == PoiKind.FUEL
            val at = next.nextAheadM
            val warn = at == null
            Row(
                Modifier.fillMaxWidth().background(if (warn) Traffic.Report.copy(alpha = 0.18f) else c.field, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconOf(poi.kind), contentDescription = null, tint = c.inkQuiet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                if (at != null) {
                    Text(if (fuel) tr("Next petrol", "Minyak seterusnya") else tr("Next charger", "Cas EV seterusnya"), style = JalanType.body, color = c.inkQuiet, modifier = Modifier.weight(1f))
                    Text(listOfNotNull(nextName, Units.short(at)).joinToString(" · "), style = JalanType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    val km = Math.round(next.knownUpToM / 1000.0)
                    Text(
                        if (fuel) tr("No other petrol on your route for $km km", "Tiada stesen minyak lain di laluan untuk $km km") else tr("No other charger on your route for $km km", "Tiada cas EV lain di laluan untuk $km km"),
                        style = JalanType.bodyStrong,
                        color = c.ink,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (spot != null) {
            JalanButton(tr("Stop here", "Singgah sini"), onClick = onStop)
        } else {
            JalanButton(if (poi.kind == PoiKind.PARKING) tr("Park here", "Parkir sini") else tr("Go here", "Pergi sini"), onClick = onGo)
        }
    }
}

private fun kindName(k: PoiKind): String =
    when (k) {
        PoiKind.FUEL -> tr("Petrol station", "Stesen minyak")
        PoiKind.EV -> tr("EV charger", "Pengecas EV")
        PoiKind.PARKING -> tr("Car park", "Tempat letak kereta")
        PoiKind.TRANSIT -> tr("Train station", "Stesen tren")
    }

/** A faster way was found: take it or keep going; keeps the current route after 20 s. */
@Composable
fun FasterCard(
    faster: DriveViewModel.Faster,
    remainingS: Double,
    onTake: () -> Unit,
    onKeep: () -> Unit,
) {
    val c = Jalan.colors
    val progress = remember(faster) { Animatable(1f) }
    LaunchedEffect(faster) { progress.animateTo(0f, tween(20_000, easing = LinearEasing)) }
    val arrive = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + ((remainingS - faster.savedMin * 60.0).coerceAtLeast(0.0) * 1000).toLong()))
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.card, shadowElevation = 8.dp) {
        Column {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Jkr.Green, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Faster route", "Laluan lebih cepat"), style = JalanType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                    Text(tr("−${faster.savedMin} min", "−${faster.savedMin} min"), style = JalanType.cardTime.copy(fontSize = 22.sp), color = Jkr.Green)
                }
                Text(tr("Arrive $arrive · less traffic", "Sampai $arrive · kurang sesak"), style = JalanType.caption, color = c.inkQuiet)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).height(48.dp).background(c.field, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onKeep),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("No thanks", "Tak apa"), style = JalanType.label, color = c.ink) }
                    Box(
                        Modifier.weight(1f).height(48.dp).background(Jkr.Green, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onTake),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("Take it", "Ambil"), style = JalanType.label, color = Color.White) }
                }
            }
            // Time left to decide.
            Box(Modifier.fillMaxWidth(progress.value).height(3.dp).background(Jkr.Green))
        }
    }
}
