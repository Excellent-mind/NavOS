package com.excellentmindblow.navos

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.routing.RoutePrefs
import com.excellentmindblow.navos.search.TripHabits
import com.excellentmindblow.navos.traffic.TrafficPrefs
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * The Settings page in the home sheet, Signal style: grouped cards with
 * switches and segmented choices right on the page (no pop-up dialogs).
 *
 *  - Guidance: voice, speed camera alerts, speed limit sign, speedometer
 *  - Routes: avoid tolls / highways
 *  - Display: map style (Auto / Day / Night), driving view (3D / 2D), units
 *  - Show on map: 3D buildings, route arrows, petrol stations, EV charging
 *  - Help & feedback, About NavOS
 *
 * Choices are saved where they always were (MapDisplayPrefs, RoutePrefs,
 * Units, the voice preference); the activity is told through [Host] so it
 * can restyle the live map.
 */
class SettingsScreen(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val host: Host,
) {
    interface Host {
        fun currentMapStyleMode(): String

        fun onMapStyleModeChosen(mode: String)

        /** 3D / 2D driving view changed (MapDisplayPrefs.view3d). */
        fun onDriveViewChanged()

        /** 3D buildings or route arrows switched on/off. */
        fun applyMapDisplayLayers()

        fun isPoiKindShown(kind: PoiKind): Boolean

        fun setPoiKindShown(
            kind: PoiKind,
            shown: Boolean,
        )

        fun isVoiceOn(): Boolean

        fun setVoiceOn(on: Boolean)

        fun onHelp()

        fun onAbout()
    }

    private var voiceSwitch: SwitchMaterial? = null

    /** Fills [container] (a vertical LinearLayout) with the settings page. */
    fun build(container: LinearLayout) {
        container.removeAllViews()

        container.addView(section(tr("Guidance", "Panduan")))
        container.addView(
            card(
                switchRow(tr("Voice guidance", "Suara arah"), tr("Spoken turn-by-turn directions", "Bagi tahu setiap belok dengan suara"), host.isVoiceOn()) { host.setVoiceOn(it) }
                    .also { voiceSwitch = it.findViewWithTag(SWITCH_TAG) },
                switchRow(tr("Speed camera alerts", "Amaran kamera laju"), null, MapDisplayPrefs.cameraAlerts) {
                    MapDisplayPrefs.cameraAlerts = it
                    MapDisplayPrefs.save(prefs)
                },
                switchRow(tr("Speed limit sign", "Papan had laju"), null, MapDisplayPrefs.speedLimit) {
                    MapDisplayPrefs.speedLimit = it
                    MapDisplayPrefs.save(prefs)
                },
                switchRow(tr("Speedometer", "Meter laju"), null, MapDisplayPrefs.speedometer) {
                    MapDisplayPrefs.speedometer = it
                    MapDisplayPrefs.save(prefs)
                },
            ),
        )

        container.addView(section(tr("Routes", "Laluan")))
        container.addView(
            card(
                switchRow(tr("Avoid tolls", "Elak tol"), null, RoutePrefs.avoidTolls) { RoutePrefs.save(prefs, it, RoutePrefs.avoidHighways) },
                switchRow(tr("Avoid highways", "Elak highway"), tr("Applies to the next route you search for", "Untuk laluan yang anda cari lepas ni"), RoutePrefs.avoidHighways) {
                    RoutePrefs.save(prefs, RoutePrefs.avoidTolls, it)
                },
                switchRow(
                    tr("Suggest my regular trips", "Cadang perjalanan biasa"),
                    tr("Learns where you drive and when, and puts your usual places first in search. On this phone only", "Belajar tempat dan masa anda memandu, dan tunjuk tempat biasa dulu bila cari. Dalam telefon ni saja"),
                    TripHabits(prefs).enabled,
                ) { TripHabits(prefs).enabled = it },
                switchRow(tr("Share anonymous traffic speeds", "Kongsi laju trafik tanpa nama"), tr("Helps show jams in red. No account, no start or end of your trip", "Bantu tunjuk jem warna merah. Tak guna akaun, tak hantar tempat mula atau hujung perjalanan"), TrafficPrefs.sharing(prefs)) {
                    TrafficPrefs.set(prefs, it)
                },
            ),
        )

        container.addView(section(tr("Display", "Paparan")))
        container.addView(
            card(
                choiceRow(
                    tr("Map style", "Gaya peta"),
                    listOf(MAP_STYLE_SYSTEM to tr("Auto", "Auto"), MAP_STYLE_LIGHT to tr("Day", "Siang"), MAP_STYLE_DARK to tr("Night", "Malam")),
                    host.currentMapStyleMode(),
                ) { host.onMapStyleModeChosen(it) },
                choiceRow(tr("Driving view", "Pandangan memandu"), listOf("3d" to "3D", "2d" to "2D"), if (MapDisplayPrefs.view3d) "3d" else "2d") {
                    MapDisplayPrefs.view3d = it == "3d"
                    MapDisplayPrefs.save(prefs)
                    host.onDriveViewChanged()
                },
                choiceRow(tr("Language", "Bahasa"), listOf(Lang.AUTO to tr("Phone", "Ikut telefon"), Lang.EN to "English", Lang.MS to "Melayu"), Lang.choice(prefs)) {
                    Lang.save(prefs, it)
                },
                choiceRow(tr("Distance units", "Unit jarak"), listOf("km" to tr("Kilometres", "Kilometer"), "mi" to tr("Miles", "Batu")), if (Units.miles) "mi" else "km") {
                    Units.save(prefs, it == "mi")
                },
            ),
        )

        container.addView(section(tr("Show on map", "Tunjuk di peta")))
        container.addView(
            card(
                switchRow(tr("3D buildings", "Bangunan 3D"), null, MapDisplayPrefs.buildings3d) {
                    MapDisplayPrefs.buildings3d = it
                    MapDisplayPrefs.save(prefs)
                    host.applyMapDisplayLayers()
                },
                switchRow(tr("Route direction arrows", "Anak panah laluan"), null, MapDisplayPrefs.routeArrows) {
                    MapDisplayPrefs.routeArrows = it
                    MapDisplayPrefs.save(prefs)
                    host.applyMapDisplayLayers()
                },
                switchRow(tr("Petrol stations", "Stesen minyak"), tr("Near your route while driving", "Dekat laluan semasa memandu"), host.isPoiKindShown(PoiKind.FUEL)) { host.setPoiKindShown(PoiKind.FUEL, it) },
                switchRow(tr("EV charging", "Cas EV"), tr("Near your route while driving", "Dekat laluan semasa memandu"), host.isPoiKindShown(PoiKind.EV)) { host.setPoiKindShown(PoiKind.EV, it) },
                switchRow(tr("Parking", "Parking"), tr("Near your destination while driving", "Dekat destinasi semasa memandu"), host.isPoiKindShown(PoiKind.PARKING)) { host.setPoiKindShown(PoiKind.PARKING, it) },
                switchRow(tr("Train stations", "Stesen tren"), tr("MRT, LRT, KTM near your destination, with Park & Ride", "MRT, LRT, KTM dekat destinasi, dengan Park & Ride"), host.isPoiKindShown(PoiKind.TRANSIT)) { host.setPoiKindShown(PoiKind.TRANSIT, it) },
            ),
        )

        container.addView(spacer(18))
        container.addView(card(linkRow(tr("Help & feedback", "Bantuan & maklum balas")) { host.onHelp() }, linkRow(tr("About NavOS", "Tentang NavOS")) { host.onAbout() }))

        container.addView(
            TextView(context).apply {
                text = context.getString(R.string.settings_footer, BuildConfig.VERSION_NAME)
                setTextAppearance(R.style.TextAppearance_Signal_Caption)
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(8))
            },
        )
    }

    /** Keeps the switch in step when voice is toggled from the drive screen's mute button. */
    fun syncVoice(on: Boolean) {
        voiceSwitch?.let { if (it.isChecked != on) it.isChecked = on }
    }

    // ---- building blocks ----------------------------------------------------

    private fun section(label: String): View =
        TextView(context).apply {
            text = label
            setTextAppearance(R.style.TextAppearance_Signal_Overline)
            ViewCompat.setAccessibilityHeading(this, true)
            setPadding(dp(6), dp(18), dp(6), dp(6))
        }

    /** A rounded card holding [rows], with hairlines between them. */
    private fun card(vararg rows: View): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background =
                GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(color(R.color.sig_field))
                }
            clipToOutline = true
            rows.forEachIndexed { i, row ->
                if (i > 0) {
                    addView(
                        View(context).apply { setBackgroundColor(color(R.color.sig_outline)) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(16) },
                    )
                }
                addView(row)
            }
        }

    /** Label (+ optional hint) and a switch; tapping anywhere on the row toggles it. */
    private fun switchRow(
        label: String,
        hint: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): View {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                setPadding(dp(16), dp(8), dp(12), dp(8))
                background = ContextCompat.getDrawable(context, rippleRes())
            }
        row.addView(labels(label, hint), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw =
            SwitchMaterial(context).apply {
                tag = SWITCH_TAG
                // No "ON/OFF" text in the track: SwitchCompat crashes measuring
                // a null label if a style turns showText on.
                showText = false
                textOn = ""
                textOff = ""
                isChecked = checked
                // Signal colours: ink track with a light thumb when on, quiet grey when off.
                setUseMaterialThemeColors(false)
                thumbTintList = states(color(R.color.sig_on_ink), color(R.color.sig_sheet))
                trackTintList = states(color(R.color.sig_ink), color(R.color.sig_text_quiet))
                // The row is the touch target and the accessibility node.
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnCheckedChangeListener { _, on -> onChange(on) }
            }
        row.addView(sw)
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
        ViewCompat.setAccessibilityDelegate(
            row,
            object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.Switch"
                    info.isCheckable = true
                    info.isChecked = sw.isChecked
                    info.contentDescription = if (hint == null) label else "$label. $hint"
                }
            },
        )
        return row
    }

    /** A label over a segmented control; the chosen segment is ink, the rest quiet. */
    private fun choiceRow(
        label: String,
        options: List<Pair<String, String>>,
        selected: String,
        onChoose: (String) -> Unit,
    ): View {
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        column.addView(
            TextView(context).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Signal)
                textSize = 17f
            },
        )
        val track =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(color(R.color.sig_sheet))
                    }
            }
        val buttons = ArrayList<TextView>()
        var current = selected
        fun paint() {
            for ((i, b) in buttons.withIndex()) {
                val on = options[i].first == current
                b.isSelected = on
                b.background =
                    if (on) {
                        GradientDrawable().apply {
                            cornerRadius = dp(11).toFloat()
                            setColor(color(R.color.sig_ink))
                        }
                    } else {
                        null
                    }
                b.setTextColor(color(if (on) R.color.sig_on_ink else R.color.sig_text))
                b.contentDescription = "$label: ${options[i].second}" + if (on) tr(", selected", ", dipilih") else ""
            }
        }
        for ((value, text) in options) {
            val b =
                TextView(context).apply {
                    this.text = text
                    setTextAppearance(R.style.TextAppearance_Signal_Label)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (current == value) return@setOnClickListener
                        current = value
                        paint()
                        onChoose(value)
                    }
                }
            Motion.addPressFeedback(b)
            buttons += b
            track.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        paint()
        column.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        return column
    }

    /** A row that opens something, with a chevron. */
    private fun linkRow(
        label: String,
        onClick: () -> Unit,
    ): View {
        val row =
            FrameLayout(context).apply {
                minimumHeight = dp(56)
                setPadding(dp(16), 0, dp(14), 0)
                background = ContextCompat.getDrawable(context, rippleRes())
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { onClick() }
            }
        row.addView(
            TextView(context).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Signal)
                textSize = 17f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START),
        )
        row.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = ColorStateList.valueOf(color(R.color.sig_text_quiet))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER_VERTICAL or Gravity.END),
        )
        return row
    }

    private fun labels(
        label: String,
        hint: String?,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(
                TextView(context).apply {
                    text = label
                    setTextAppearance(R.style.TextAppearance_Signal)
                    textSize = 17f
                },
            )
            if (hint != null) {
                addView(
                    TextView(context).apply {
                        text = hint
                        setTextAppearance(R.style.TextAppearance_Signal_Caption)
                        setPadding(0, dp(2), 0, 0)
                    },
                )
            }
        }

    private fun spacer(heightDp: Int): View = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }

    private fun rippleRes(): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    private fun states(
        checked: Int,
        unchecked: Int,
    ) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(checked, unchecked))

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val SWITCH_TAG = "settings-switch"
    }
}
