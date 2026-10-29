package com.excellentmindblow.navos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.PlacesViewModel
import com.excellentmindblow.navos.ui.components.JalanButton
import com.excellentmindblow.navos.ui.components.JalanIcons
import com.excellentmindblow.navos.ui.components.JalanSheet
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/** A spot picked on the map: what it is, how far, Go here, and save it as Home or Work. */
@Composable
fun PlaceCard(
    pinned: PlacesViewModel.Pinned,
    distanceM: Double?,
    onGo: () -> Unit,
    onSaveHome: () -> Unit,
    onSaveWork: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    val place = pinned.place
    JalanSheet(modifier) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        place?.name ?: tr("Finding the address…", "Cari alamat…"),
                        style = JalanType.title,
                        color = if (place == null) c.inkQuiet else c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail =
                        listOfNotNull(
                            place?.category,
                            place?.address?.takeIf { it.isNotBlank() && it != place.name },
                        ).joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, style = JalanType.caption, color = c.inkQuiet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (distanceM != null) Text(tr("${Units.short(distanceM)} from you", "${Units.short(distanceM)} dari anda"), style = JalanType.caption, color = c.inkQuiet)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.field).clickable(role = Role.Button, onClickLabel = tr("Close", "Tutup"), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = tr("Close", "Tutup"), tint = c.ink) }
            }
            Spacer(Modifier.height(14.dp))
            JalanButton(tr("Go here", "Pergi sini"), onClick = { if (place != null) onGo() })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaveButton(tr("Save as Home", "Simpan Rumah"), Icons.Rounded.Home, place != null, onSaveHome, Modifier.weight(1f))
                SaveButton(tr("Save as Work", "Simpan Kerja"), JalanIcons.Work, place != null, onSaveWork, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaveButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = Jalan.colors
    Row(
        modifier.height(48.dp).background(c.field, RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) c.ink else c.inkQuiet, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = JalanType.label, color = if (enabled) c.ink else c.inkQuiet, maxLines = 1)
    }
}
