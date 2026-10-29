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
