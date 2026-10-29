package com.excellentmindblow.navos.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.NavFormat
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/** One turn in the list: what to do, how far after the one before, onto where. */
data class TurnRow(
    val maneuverType: String,
    val modifier: String?,
    /** For the first row, how far away it is; after that, how far after the previous turn. */
    val distanceM: Double,
    val name: String,
    val exit: String? = null,
    val refs: List<String> = emptyList(),
)

/**
 * Every turn still ahead, opened by tapping the turn banner: the next one on
 * top in the banner's green, then the rest with the distance between turns,
 * and the destination last. Close (or back) returns to the drive.
 */
@Composable
fun TurnList(
    rows: List<TurnRow>,
    onClose: () -> Unit,
) {
    val c = Jalan.colors
    val green = if (c.isNight) BannerColors.Night else BannerColors.Day
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Surface(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(10.dp)
                .clickable(interactionSource = null, indication = null) {},
            shape = RoundedCornerShape(20.dp),
            color = c.card,
            shadowElevation = 12.dp,
        ) {
            Column {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(rows) { i, r ->
                        val first = i == 0
                        val ink = if (first) Color.White else c.ink
                        Row(
                            Modifier.fillMaxWidth().background(if (first) green else Color.Transparent).heightIn(min = 76.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                ManeuverGlyph(r.maneuverType, r.modifier, Modifier.size(if (first) 46.dp else 38.dp), ink, hole = if (first) green else c.card)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(NavFormat.formatCountdownDistance(r.distanceM), style = JalanType.title.copy(fontSize = if (first) 24.sp else 20.sp), color = ink, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r.exit != null) {
                                        Text(tr("Exit ${r.exit}", "Keluar ${r.exit}"), style = JalanType.label.copy(fontSize = 12.sp), color = ink, modifier = Modifier.padding(end = 6.dp))
                                    }
                                    r.refs.take(1).forEach {
                                        RouteShield(it, small = true)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(r.name, style = JalanType.bodyStrong.copy(fontSize = 17.sp), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (!first && i < rows.size - 1) Box(Modifier.fillMaxWidth().padding(start = 82.dp).height(1.dp).background(c.line))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(14.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).background(c.field, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text(tr("Close", "Tutup"), style = JalanType.bodyStrong, color = c.ink) }
                }
            }
        }
    }
}

/** Builds the list from the route's steps, starting at the next turn. */
fun turnRows(
    steps: List<com.excellentmindblow.navos.routing.RouteStep>,
    from: Int,
    toNextM: Double,
    destinationName: String?,
): List<TurnRow> {
    if (from !in steps.indices) return emptyList()
    return (from until steps.size).map { i ->
        val s = steps[i]
        val sign = com.excellentmindblow.navos.signs.TurnSign.of(s, steps.getOrNull(i - 1)?.ref, s.instruction, steps.getOrNull(i + 1))
        val dist = if (i == from) toNextM else steps[i - 1].distanceMeters
        val name =
            if (s.maneuverType == "arrive") {
                destinationName?.takeIf { it.isNotBlank() } ?: tr("Destination", "Destinasi")
            } else {
                sign.primary
            }
        TurnRow(s.maneuverType, s.modifier, dist, name, sign.exit, sign.refs)
    }
}
