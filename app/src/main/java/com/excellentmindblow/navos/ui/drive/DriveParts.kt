package com.excellentmindblow.navos.ui.drive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.components.JalanButton
import com.excellentmindblow.navos.ui.components.JalanSheet
import com.excellentmindblow.navos.ui.map.DriveLayers
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/** A report or an open Smart Lane ahead: coloured dot, what it is, how far. */
@Composable
fun AlertBanner(
    alert: DriveViewModel.Alert,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = c.card, shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Color(android.graphics.Color.parseColor(DriveLayers.colour(alert.kind))), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.title, style = JalanType.bodyStrong, color = c.ink, maxLines = 1)
                Text(alert.detail, style = JalanType.caption, color = c.inkQuiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Units.short(alert.distanceM.coerceAtLeast(10.0)), style = JalanType.cardTime.copy(fontSize = 20.sp), color = c.ink)
        }
    }
}

/** You've arrived: where, which side, and Done. */
@Composable
fun ArrivalCard(
    name: String,
    side: String?,
    onDone: () -> Unit,
) {
    val c = Jalan.colors
    Box(Modifier.fillMaxSize()) {
        JalanSheet(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
                Text(tr("YOU'VE ARRIVED", "DAH SAMPAI"), style = JalanType.overline, color = c.inkQuiet)
                Spacer(Modifier.height(4.dp))
                Text(name, style = JalanType.title.copy(fontSize = 24.sp), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (side != null) Text(side, style = JalanType.body, color = c.inkQuiet)
                Spacer(Modifier.height(16.dp))
                JalanButton(tr("Done", "Selesai"), onClick = onDone)
            }
        }
    }
}

