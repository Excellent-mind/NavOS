package com.excellentmindblow.navos

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.routing.RoutePrefs
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * The two settings dialogs opened from the home sheet: Map display (colors,
 * driving view, what's drawn on the map, driving overlays) and Navigation
 * preferences (units, avoid tolls / highways). Choices are saved here; the
 * screen is told through [Host] so it can restyle the live map.
 */
class SettingsDialogs(
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
    }

    private val textColor get() = context.getColor(R.color.navos_text)
    private val hintColor get() = context.getColor(R.color.navos_hint)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun heading(
        label: String,
        topPadding: Int = 14,
    ) = TextView(context).apply {
        text = label
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        setTextColor(textColor)
        setPadding(0, dp(topPadding), 0, dp(4))
    }

    private fun note(
        label: String,
        topPadding: Int = 2,
    ) = TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(hintColor)
        setPadding(0, dp(topPadding), 0, dp(4))
    }

    private fun radio(
        group: RadioGroup,
        label: String,
    ): RadioButton {
        val button = MaterialRadioButton(context).apply { id = View.generateViewId(); text = label; setTextColor(textColor) }
        group.addView(button)
        return button
    }

    private fun switchRow(
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(
                TextView(context).apply {
                    text = label
                    textSize = 15f
                    setTextColor(textColor)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                SwitchMaterial(context).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _, on -> onChange(on) }
                },
            )
        }

    fun showMapDisplay() {
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), dp(8))
            }

        // Map colors
        column.addView(heading("Map colors"))
        val colors = RadioGroup(context)
        val rSystem = radio(colors, "Follow system")
        val rLight = radio(colors, "Light (day)")
        val rDark = radio(colors, "Dark (night)")
        colors.check(
            when (host.currentMapStyleMode()) {
                MAP_STYLE_DARK -> rDark.id
                MAP_STYLE_LIGHT -> rLight.id
                else -> rSystem.id
            },
        )
        colors.setOnCheckedChangeListener { _, id ->
            host.onMapStyleModeChosen(
                when (id) {
                    rDark.id -> MAP_STYLE_DARK
                    rLight.id -> MAP_STYLE_LIGHT
                    else -> MAP_STYLE_SYSTEM
                },
            )
        }
        column.addView(colors)

        // Map view
        column.addView(heading("Driving view"))
        val view = RadioGroup(context)
        val r3d = radio(view, "3D - tilted, looking ahead")
        val r2d = radio(view, "2D - flat, top-down")
        view.check(if (MapDisplayPrefs.view3d) r3d.id else r2d.id)
        view.setOnCheckedChangeListener { _, id ->
            MapDisplayPrefs.view3d = id == r3d.id
            MapDisplayPrefs.save(prefs)
            host.onDriveViewChanged()
        }
        column.addView(view)
        column.addView(note("Applies the next time you start driving."))

        // Show on map
        column.addView(heading("Show on map"))
        column.addView(
            switchRow("3D buildings", MapDisplayPrefs.buildings3d) {
                MapDisplayPrefs.buildings3d = it
                MapDisplayPrefs.save(prefs)
                host.applyMapDisplayLayers()
            },
        )
        column.addView(
            switchRow("Route direction arrows", MapDisplayPrefs.routeArrows) {
                MapDisplayPrefs.routeArrows = it
                MapDisplayPrefs.save(prefs)
                host.applyMapDisplayLayers()
            },
        )
        column.addView(switchRow("Petrol stations", host.isPoiKindShown(PoiKind.FUEL)) { host.setPoiKindShown(PoiKind.FUEL, it) })
        column.addView(switchRow("EV charging", host.isPoiKindShown(PoiKind.EV)) { host.setPoiKindShown(PoiKind.EV, it) })

        // While driving
        column.addView(heading("While driving"))
        column.addView(
            switchRow("Speedometer", MapDisplayPrefs.speedometer) {
                MapDisplayPrefs.speedometer = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(
            switchRow("Speed limit sign", MapDisplayPrefs.speedLimit) {
                MapDisplayPrefs.speedLimit = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(
            switchRow("Speed camera alerts", MapDisplayPrefs.cameraAlerts) {
                MapDisplayPrefs.cameraAlerts = it
                MapDisplayPrefs.save(prefs)
            },
        )
        column.addView(note("Driving options apply the next time you start driving."))

        val scroll = ScrollView(context).apply { addView(column) }
        MaterialAlertDialogBuilder(context)
            .setTitle("Map display")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .show()
    }

    fun showNavPreferences() {
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(8), dp(22), 0)
            }

        column.addView(heading("Distance units", topPadding = 10))
        val group = RadioGroup(context)
        val kmButton = radio(group, "Kilometers")
        val miButton = radio(group, "Miles")
        group.check(if (Units.miles) miButton.id else kmButton.id)
        group.setOnCheckedChangeListener { _, checkedId -> Units.save(prefs, checkedId == miButton.id) }
        column.addView(group)

        column.addView(heading("Route preferences", topPadding = 10))
        val tolls =
            MaterialCheckBox(context).apply {
                text = "Avoid tolls"
                isChecked = RoutePrefs.avoidTolls
                setTextColor(textColor)
            }
        val highways =
            MaterialCheckBox(context).apply {
                text = "Avoid highways"
                isChecked = RoutePrefs.avoidHighways
                setTextColor(textColor)
            }
        tolls.setOnCheckedChangeListener { _, checked -> RoutePrefs.save(prefs, checked, RoutePrefs.avoidHighways) }
        highways.setOnCheckedChangeListener { _, checked -> RoutePrefs.save(prefs, RoutePrefs.avoidTolls, checked) }
        column.addView(tolls)
        column.addView(highways)
        column.addView(note("Route preferences apply the next time you search for a route.", topPadding = 6))

        MaterialAlertDialogBuilder(context)
            .setTitle("Navigation preferences")
            .setView(column)
            .setPositiveButton("Done", null)
            .show()
    }
}
