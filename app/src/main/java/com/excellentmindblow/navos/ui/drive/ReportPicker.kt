package com.excellentmindblow.navos.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.traffic.RoadReports
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr
import com.excellentmindblow.navos.ui.theme.Traffic

/** Report button: five big tiles, one tap reports it where you are, for other drivers. */
@Composable
fun ReportPicker(
    onPick: (RoadReports.Kind) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Jalan.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sheet,
        title = { Text(tr("What's on the road?", "Apa ada kat jalan?"), style = JalanType.title, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Reported where you are now, for other drivers.", "Dilapor di tempat anda sekarang, untuk pemandu lain."), style = JalanType.body, color = c.inkQuiet)
                val kinds = RoadReports.Kind.entries
                kinds.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k -> Tile(k, Modifier.weight(1f)) { onPick(k) } }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Batal"), style = JalanType.label, color = c.ink) } },
    )
}

@Composable
private fun Tile(
    k: RoadReports.Kind,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val fill =
        when (k) {
            RoadReports.Kind.ACCIDENT -> Traffic.Jam
            RoadReports.Kind.POLICE -> Color(0xFF15181C)
            RoadReports.Kind.FLOOD -> Color(0xFF2457A6)
            RoadReports.Kind.HAZARD -> Traffic.Report
            RoadReports.Kind.SMART_LANE -> Jkr.Green
        }
    val ink = if (k == RoadReports.Kind.HAZARD) Color(0xFF111111) else Color.White
    Column(
        modifier.fillMaxWidth().height(84.dp).background(fill, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(k.label, style = JalanType.bodyStrong, color = ink)
    }
}
