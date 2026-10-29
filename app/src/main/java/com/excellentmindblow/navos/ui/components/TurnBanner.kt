package com.excellentmindblow.navos.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.routing.LaneInfo
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/** The turn banner's greens: the banner, and the darker band for lanes and "Then". */
object BannerColors {
    val Day = Color(0xFF0B7B47)
    val DayDark = Color(0xFF075C34)
    val Night = Color(0xFF0A5F38)
    val NightDark = Color(0xFF064326)
}

/**
 * The next turn, laid out the way Google Maps does it: a green banner with a
 * big white turn icon and the distance under it, the road you turn onto in
 * large type with "towards ..." under it, lane arrows in a darker band below,
 * and a small "Then" tab when the next turn comes right after.
 */
@Composable
fun TurnBanner(
    maneuverType: String,
    modifier: String?,
    distance: String,
    primary: String,
    towards: String?,
    exit: String?,
    refs: List<String>,
    lanes: List<LaneInfo>?,
    thenType: String?,
    thenModifier: String?,
    layout: Modifier = Modifier,
) {
    val night = Jalan.colors.isNight
    val green = if (night) BannerColors.Night else BannerColors.Day
    val dark = if (night) BannerColors.NightDark else BannerColors.DayDark
    val showLanes = !lanes.isNullOrEmpty() && lanes.size in 2..8
    Column(layout.fillMaxWidth().semantics { contentDescription = "$distance, $primary" }) {
        Surface(shape = RoundedCornerShape(20.dp), color = green, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.animateContentSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Icon with the distance under it.
                    Column(Modifier.widthIn(min = 76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ManeuverGlyph(maneuverType, modifier, Modifier.size(58.dp), Color.White, hole = green)
                        Spacer(Modifier.height(4.dp))
                        Text(distance, style = JalanType.title.copy(fontSize = 20.sp, lineHeight = 22.sp), color = Color.White, maxLines = 1)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        if (exit != null || refs.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (exit != null) {
                                    Box(Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text(tr("Exit $exit", "Keluar $exit"), style = JalanType.label.copy(fontSize = 13.sp), color = Color.White)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                refs.forEach { r ->
                                    RouteShield(r, small = true)
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            primary,
                            style = JalanType.title.copy(fontSize = if (primary.length > 22) 23.sp else 27.sp, lineHeight = if (primary.length > 22) 27.sp else 31.sp),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!towards.isNullOrBlank()) {
                            Text(
                                tr("towards $towards", "ke arah $towards"),
                                style = JalanType.body.copy(fontSize = 15.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (showLanes) {
                    // Lanes: the ones to be in bright, the others faded.
                    Row(
                        Modifier.fillMaxWidth().background(dark).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        lanes.orEmpty().forEachIndexed { i, lane ->
                            if (i > 0) Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.25f)))
                            val ind = lane.indications.firstOrNull { it != "none" } ?: "straight"
                            Box(Modifier.size(46.dp, 34.dp).alpha(if (lane.valid) 1f else 0.35f), contentAlignment = Alignment.Center) {
                                ManeuverGlyph(if (ind == "uturn") "uturn" else "turn", ind, Modifier.size(28.dp), Color.White, hole = dark)
                            }
                        }
                    }
                }
            }
        }
        if (thenType != null) {
            // "Then" tab hanging under the banner's left edge.
            Row(
                Modifier.padding(start = 12.dp).background(dark, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Then", "Kemudian"), style = JalanType.label.copy(fontSize = 15.sp), color = Color.White)
                Spacer(Modifier.width(8.dp))
                ManeuverGlyph(thenType, thenModifier, Modifier.size(24.dp), Color.White, hole = dark)
            }
        }
    }
}
