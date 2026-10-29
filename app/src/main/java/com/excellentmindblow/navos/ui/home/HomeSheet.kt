package com.excellentmindblow.navos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.PlacesViewModel
import com.excellentmindblow.navos.ui.components.JalanButton
import com.excellentmindblow.navos.ui.components.JalanChip
import com.excellentmindblow.navos.ui.components.JalanIcons
import com.excellentmindblow.navos.ui.components.JalanSheet
import com.excellentmindblow.navos.ui.components.Overline
import com.excellentmindblow.navos.ui.components.PlaceRow
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/**
 * Home, under the floating search bar: the trip you usually take now (one
 * tap to Jalan), Home / Work, and your last places.
 */
@Composable
fun HomeSheet(
    predicted: GeocodeResult?,
    shortcuts: PlacesViewModel.Shortcuts,
    recents: List<GeocodeResult>,
    distanceTo: (GeocodeResult) -> Double?,
    onGo: (GeocodeResult) -> Unit,
    onSetHome: () -> Unit,
    onSetWork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    JalanSheet(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (predicted != null) PredictedCard(predicted, distanceTo(predicted)) { onGo(predicted) }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val home = shortcuts.home
                val work = shortcuts.work
                JalanChip(if (home != null) tr("Home", "Rumah") else tr("Set Home", "Tetapkan Rumah"), onClick = { if (home != null) onGo(home) else onSetHome() }, icon = Icons.Rounded.Home)
                JalanChip(if (work != null) tr("Work", "Kerja") else tr("Set Work", "Tetapkan Kerja"), onClick = { if (work != null) onGo(work) else onSetWork() }, icon = JalanIcons.Work)
            }

            if (recents.isNotEmpty()) {
                Column {
                    Overline(tr("Recent", "Terkini"))
                    recents.take(3).forEach { p ->
                        PlaceRow(p.name, p.address, distanceTo(p)?.let { Units.short(it) }, JalanIcons.History, onClick = { onGo(p) })
                    }
                }
            }
        }
    }
}

/** "Usually now": the trip you take at this time, with the green Jalan button. */
@Composable
private fun PredictedCard(
    place: GeocodeResult,
    distance: Double?,
    onGo: () -> Unit,
) {
    val c = Jalan.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(tr("USUALLY NOW", "SELALUNYA WAKTU INI"), style = JalanType.overline, color = c.inkQuiet)
            Spacer(Modifier.height(4.dp))
            Text(place.name, style = JalanType.title.copy(fontSize = 21.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (distance != null) Text(Units.short(distance) + tr(" away", " dari sini"), style = JalanType.caption, color = c.inkQuiet)
        }
        JalanButton(tr("Jalan • Go", "Jalan"), onClick = onGo)
    }
}
