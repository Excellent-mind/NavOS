package com.excellentmindblow.navos.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.ui.PlacesViewModel
import com.excellentmindblow.navos.ui.components.JalanIcons
import com.excellentmindblow.navos.ui.components.Overline
import com.excellentmindblow.navos.ui.components.PlaceRow
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType

/**
 * Search, full screen over the map: the field at the top (keyboard up at
 * once), results as you type; before typing, Home / Work and recent places.
 */
@Composable
fun SearchScreen(
    purpose: PlacesViewModel.Purpose,
    query: String,
    results: List<GeocodeResult>,
    searching: Boolean,
    shortcuts: PlacesViewModel.Shortcuts,
    recents: List<GeocodeResult>,
    distanceTo: (GeocodeResult) -> Double?,
    onQuery: (String) -> Unit,
    onPick: (GeocodeResult) -> Unit,
    onBack: () -> Unit,
    onClearRecents: () -> Unit,
) {
    val c = Jalan.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(c.sheet).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = tr("Back", "Kembali"), onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back", "Kembali"), tint = c.ink)
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.weight(1f).height(52.dp).background(c.card, RoundedCornerShape(26.dp)).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            when (purpose) {
                                PlacesViewModel.Purpose.SET_HOME -> tr("Search for your home", "Cari rumah anda")
                                PlacesViewModel.Purpose.SET_WORK -> tr("Search for your work", "Cari tempat kerja")
                                PlacesViewModel.Purpose.GO -> tr("Where to?", "Nak ke mana?")
                            },
                            style = JalanType.body.copy(fontSize = 18.sp),
                            color = c.inkQuiet,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = JalanType.body.copy(fontSize = 18.sp, color = c.ink),
                        cursorBrush = SolidColor(c.route),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(onPick) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.size(40.dp).background(c.field, CircleShape).clickable(role = Role.Button, onClickLabel = tr("Clear", "Padam")) { onQuery("") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Clear, contentDescription = tr("Clear", "Padam"), tint = c.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.route, trackColor = c.line)

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (query.isBlank()) {
                if (purpose == PlacesViewModel.Purpose.GO) {
                    shortcuts.home?.let { p -> item { PlaceRow(tr("Home", "Rumah"), p.label, distanceTo(p)?.let { Units.short(it) }, Icons.Rounded.Home, onClick = { onPick(p) }) } }
                    shortcuts.work?.let { p -> item { PlaceRow(tr("Work", "Kerja"), p.label, distanceTo(p)?.let { Units.short(it) }, JalanIcons.Work, onClick = { onPick(p) }) } }
                }
                if (recents.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Overline(tr("Recent", "Terkini"), Modifier.weight(1f))
                            Text(tr("Clear", "Padam"), style = JalanType.label, color = c.route, modifier = Modifier.clickable(role = Role.Button, onClick = onClearRecents).padding(8.dp))
                        }
                    }
                    items(recents) { p -> PlaceRow(p.name, p.address, distanceTo(p)?.let { Units.short(it) }, JalanIcons.History, onClick = { onPick(p) }) }
                }
            } else {
                items(results) { p ->
                    PlaceRow(p.name, listOfNotNull(p.category, p.address.takeIf { it.isNotBlank() }).joinToString(" · "), distanceTo(p)?.let { Units.short(it) }, JalanIcons.Pin, onClick = { onPick(p) })
                }
                if (!searching && results.isEmpty()) {
                    item { Text(tr("No places found. Try another spelling.", "Tiada tempat dijumpai. Cuba ejaan lain."), style = JalanType.body, color = c.inkQuiet, modifier = Modifier.padding(vertical = 24.dp)) }
                }
            }
        }
    }
}

