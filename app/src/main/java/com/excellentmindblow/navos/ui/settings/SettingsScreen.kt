package com.excellentmindblow.navos.ui.settings

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.excellentmindblow.navos.Lang
import com.excellentmindblow.navos.MainActivity
import com.excellentmindblow.navos.Units
import com.excellentmindblow.navos.search.RecentPlaces
import com.excellentmindblow.navos.search.TripHabits
import com.excellentmindblow.navos.tr
import com.excellentmindblow.navos.traffic.TrafficPrefs
import com.excellentmindblow.navos.ui.components.Overline
import com.excellentmindblow.navos.ui.theme.Jalan
import com.excellentmindblow.navos.ui.theme.JalanType
import com.excellentmindblow.navos.ui.theme.Jkr

/** How the app picks day or night. */
object ThemePref {
    const val KEY = "map_style"
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/** Settings: language, units, day / night, voice, live traffic, learning, data. */
@Composable
fun SettingsScreen(
    theme: String,
    onTheme: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    val c = Jalan.colors
    var lang by remember { mutableStateOf(Lang.choice(prefs)) }
    var miles by remember { mutableStateOf(Units.miles) }
    var voice by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var share by remember { mutableStateOf(TrafficPrefs.sharing(prefs)) }
    var learn by remember { mutableStateOf(TripHabits(prefs).enabled) }
    var cleared by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(c.sheet).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = tr("Back", "Kembali"), onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back", "Kembali"), tint = c.ink)
            }
            Text(tr("Settings", "Tetapan"), style = JalanType.title.copy(fontSize = JalanType.eta.fontSize * 0.62f), color = c.ink)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Overline(tr("Language", "Bahasa"))
            Segmented(
                listOf(Lang.AUTO to tr("Phone", "Telefon"), Lang.EN to "English", Lang.MS to "Bahasa Melayu"),
                lang,
            ) {
                lang = it
                Lang.save(prefs, it)
                // Re-creates the screen in the new language.
                if (Lang.applyToApp(prefs)) (context as? Activity)?.recreate()
            }

            Overline(tr("Look", "Rupa"))
            Segmented(listOf(ThemePref.SYSTEM to tr("Follow phone", "Ikut telefon"), ThemePref.LIGHT to tr("Day", "Siang"), ThemePref.DARK to tr("Night", "Malam")), theme, onTheme)

            Overline(tr("Units", "Unit"))
            Segmented(listOf("km" to tr("Kilometres", "Kilometer"), "mi" to tr("Miles", "Batu")), if (miles) "mi" else "km") {
                miles = it == "mi"
                Units.save(prefs, miles)
            }

            Overline(tr("Driving", "Memandu"))
            Toggle(tr("Voice guidance", "Panduan suara"), tr("Spoken turns and alerts", "Arahan belok dan amaran disebut"), voice) {
                voice = it
                prefs.edit().putBoolean("voice_enabled", it).apply()
            }
            Toggle(
                tr("Share live traffic", "Kongsi trafik langsung"),
                tr("Anonymous speeds help everyone avoid jams", "Kelajuan tanpa nama bantu semua elak jem"),
                share,
            ) {
                share = it
                TrafficPrefs.set(prefs, it)
            }
            Toggle(
                tr("Suggest my regular trips", "Cadangkan perjalanan biasa"),
                tr("Learned on this phone only. Turning it off forgets them.", "Dipelajari dalam telefon ini sahaja. Tutup untuk lupakan."),
                learn,
            ) {
                learn = it
                TripHabits(prefs).enabled = it
            }

            Overline(tr("Data", "Data"))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                    RecentPlaces(prefs).clear()
                    cleared = true
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (cleared) tr("Recent places cleared", "Tempat terkini dipadam") else tr("Clear recent places", "Padam tempat terkini"), style = JalanType.bodyStrong, color = if (cleared) c.inkQuiet else c.ink)
            }

            Overline(tr("About", "Tentang"))
            Text(
                tr(
                    "NavOS · maps and data © OpenStreetMap contributors, OpenFreeMap. Routing by OSRM. Toll fares from Lembaga Lebuhraya Malaysia. Logos and JKR symbols from Wikimedia Commons (see the licences in the app).",
                    "NavOS · peta dan data © penyumbang OpenStreetMap, OpenFreeMap. Laluan oleh OSRM. Kadar tol daripada Lembaga Lebuhraya Malaysia. Logo dan simbol JKR daripada Wikimedia Commons.",
                ),
                style = JalanType.caption,
                color = c.inkQuiet,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    value: String,
    onPick: (String) -> Unit,
) {
    val c = Jalan.colors
    Row(Modifier.fillMaxWidth().background(c.field, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (on) c.card else c.field, RoundedCornerShape(11.dp))
                    .then(if (on) Modifier.border(1.dp, c.line, RoundedCornerShape(11.dp)) else Modifier)
                    .clickable(role = Role.RadioButton) { onPick(key) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = JalanType.label, color = if (on) c.ink else c.inkQuiet, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    detail: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = Jalan.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Switch) { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = JalanType.bodyStrong, color = c.ink)
            Text(detail, style = JalanType.caption, color = c.inkQuiet)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Jkr.Green, checkedThumbColor = androidx.compose.ui.graphics.Color.White))
    }
}
