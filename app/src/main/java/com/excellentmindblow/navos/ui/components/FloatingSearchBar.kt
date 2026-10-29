package com.excellentmindblow.navos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/** Home's search bar, floating at the top of the map, with the settings button on it. */
@Composable
fun FloatingSearchBar(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Jalan.colors
    Surface(
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = c.card,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            Row(
                Modifier.weight(1f).height(60.dp).clickable(role = Role.Button, onClickLabel = tr("Search", "Cari"), onClick = onSearch).padding(start = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(tr("Where to?", "Nak ke mana?"), style = JalanType.title, color = c.ink)
            }
            Box(
                Modifier.padding(end = 8.dp).size(44.dp).background(c.ink, CircleShape).clickable(role = Role.Button, onClickLabel = tr("Settings", "Tetapan"), onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = tr("Settings", "Tetapan"), tint = c.onInk, modifier = Modifier.size(22.dp))
            }
        }
    }
}
