package com.excellentmindblow.navos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.NavFormat
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.signs.SignColour
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr

/**
 * The next turn as a JKR gantry sign: green on E (expressway) roads, blue on
 * everything else; arrow, distance, the road's shield and name in sign
 * capitals. Lanes and "Then ..." hang under it in a dark strip.
 */
@Composable
fun GantrySign(
    maneuverType: String,
    modifier: String?,
    distance: String,
    road: String,
    roadRef: String?,
    lanes: List<LaneInfo>?,
    thenType: String?,
    thenModifier: String?,
    thenText: String?,
    modifierLayout: Modifier = Modifier,
) {
    val panel = if (SignColour.isExpressway(roadRef)) Jkr.Green else Jkr.Blue
    val strip = Jalan.colors.laneStrip
    val showLanes = !lanes.isNullOrEmpty()
    val showThen = thenType != null && !thenText.isNullOrBlank()
    Column(modifierLayout.fillMaxWidth().semantics { contentDescription = "$distance, $road" }) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (showLanes || showThen) 0.dp else 18.dp, bottomEnd = if (showLanes || showThen) 0.dp else 18.dp))
                .padding(3.dp)
                .border(2.5.dp, Color.White, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = if (showLanes || showThen) 0.dp else 15.dp, bottomEnd = if (showLanes || showThen) 0.dp else 15.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(NavFormat.iconForManeuver(maneuverType, modifier)), contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(distance, style = JalanType.signDistance, color = Color.White, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!roadRef.isNullOrBlank()) {
                        RouteShield(roadRef.split(';').first().trim(), small = true)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(road.uppercase(), style = JalanType.signRoad, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (showLanes) {
            Row(
                Modifier.fillMaxWidth().background(strip, if (showThen) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                lanes.orEmpty().forEach { lane ->
                    Box(Modifier.size(44.dp, 34.dp).alpha(if (lane.valid) 1f else 0.3f), contentAlignment = Alignment.Center) {
                        val ind = lane.indications.firstOrNull { it != "none" } ?: "straight"
                        Icon(painterResource(R.drawable.ic_turn_straight), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp).rotate(NavFormat.rotationForIndication(ind)))
                    }
                }
            }
        }
        if (showThen) {
            Row(
                Modifier.background(strip, RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Then", "Kemudian"), style = JalanType.label, color = Color(0xFFB9BDC2))
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(NavFormat.iconForManeuver(thenType!!, thenModifier)), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(thenText!!, style = JalanType.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The posted speed limit as a Malaysian roundel (red ring, black number), and your speed under it. */
@Composable
fun SpeedSigns(
    speedKmh: Int,
    limitKmh: Int?,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (limitKmh != null) {
            Box(Modifier.size(60.dp).background(Color.White, CircleShape).border(7.dp, Jkr.LimitRing, CircleShape), contentAlignment = Alignment.Center) {
                Text("$limitKmh", style = JalanType.title.copy(fontSize = 22.sp), color = Color(0xFF111111))
            }
        }
        val over = limitKmh != null && speedKmh > limitKmh + 3
        Column(
            Modifier.background(if (over) Jkr.LimitRing else c.card, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$speedKmh", style = JalanType.cardTime.copy(fontSize = 24.sp), color = if (over) Color.White else c.ink)
            Text(tr("km/h", "km/j"), style = JalanType.caption.copy(fontSize = 11.sp), color = if (over) Color.White else c.inkQuiet)
        }
    }
}
