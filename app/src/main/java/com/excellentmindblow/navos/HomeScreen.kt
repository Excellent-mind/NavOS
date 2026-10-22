package com.excellentmindblow.navos

import android.content.res.ColorStateList
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.excellentmindblow.navos.poi.PoiKind
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.GeocodingService
import com.excellentmindblow.navos.search.RecentPlaces
import com.excellentmindblow.navos.search.SavedPlaces

/**
 * The Home screen's own content (the collapsed bottom sheet, Signal
 * design): Home / Work cards with the drive time, petrol / EV pin toggles
 * and the latest recent places. The sheet's dragging and the search it
 * opens stay with [MainActivity]; what a tap does is decided through [Host].
 */
class HomeScreen(
    private val root: View,
    private val savedPlaces: SavedPlaces,
    private val recentPlaces: RecentPlaces,
    private val driveTimes: HomeEtas,
    private val host: Host,
) {
    interface Host {
        fun currentLocation(): Location?

        fun onShortcutTapped(key: String)

        fun onRecentTapped(place: GeocodeResult)

        fun isPoiKindShown(kind: PoiKind): Boolean

        fun setPoiKindShown(
            kind: PoiKind,
            shown: Boolean,
        )
    }

    private val context = root.context
    private val homeCard: View = root.findViewById(R.id.homeShortcutHome)
    private val workCard: View = root.findViewById(R.id.homeShortcutWork)
    private val petrolChip: TextView = root.findViewById(R.id.homePetrolChip)
    private val evChip: TextView = root.findViewById(R.id.homeEvChip)
    private val recents: LinearLayout = root.findViewById(R.id.homePeekRecents)

    init {
        driveTimes.onChanged = { render() }
        homeCard.setOnClickListener { host.onShortcutTapped(SavedPlaces.HOME) }
        workCard.setOnClickListener { host.onShortcutTapped(SavedPlaces.WORK) }
        petrolChip.setOnClickListener { toggle(PoiKind.FUEL) }
        evChip.setOnClickListener { toggle(PoiKind.EV) }
        render()
    }

    private fun toggle(kind: PoiKind) {
        host.setPoiKindShown(kind, !host.isPoiKindShown(kind))
        renderChips()
    }

    /** Redraws everything from the saved data and starts any drive-time fetch due. */
    fun render() {
        renderShortcut(homeCard, SavedPlaces.HOME, R.string.shortcut_home, R.drawable.ic_home, loud = true)
        renderShortcut(workCard, SavedPlaces.WORK, R.string.shortcut_work, R.drawable.ic_work, loud = false)
        renderChips()
        renderRecents()
        refreshDriveTimes()
    }

    /** New GPS fix: drive times may be due again. */
    fun onLocation() = refreshDriveTimes()

    private fun refreshDriveTimes() {
        val here = host.currentLocation() ?: return
        val places =
            listOf(SavedPlaces.HOME, SavedPlaces.WORK)
                .mapNotNull { key -> savedPlaces.get(key)?.let { key to RoutePoint(it.latitude, it.longitude) } }
                .toMap()
        if (places.isNotEmpty()) driveTimes.refresh(RoutePoint(here.latitude, here.longitude), places)
    }

    private fun renderShortcut(
        card: View,
        key: String,
        labelRes: Int,
        iconRes: Int,
        loud: Boolean,
    ) {
        val fg = color(if (loud) R.color.sig_on_ink else R.color.sig_text)
        val quiet = color(if (loud) R.color.sig_on_ink_quiet else R.color.sig_text_quiet)
        card.backgroundTintList = ColorStateList.valueOf(color(if (loud) R.color.sig_ink else R.color.sig_field))

        val label = card.findViewById<TextView>(R.id.shortcutLabel)
        val value = card.findViewById<TextView>(R.id.shortcutValue)
        val detail = card.findViewById<TextView>(R.id.shortcutDetail)
        label.setText(labelRes)
        setIcon(label, iconRes, fg)
        label.setTextColor(fg)
        value.setTextColor(fg)
        detail.setTextColor(quiet)

        val saved = savedPlaces.get(key)
        if (saved == null) {
            value.setText(R.string.shortcut_set)
            detail.setText(R.string.shortcut_set_detail)
        } else {
            val seconds = driveTimes.secondsTo(key, RoutePoint(saved.latitude, saved.longitude))
            value.text = if (seconds != null) NavFormat.formatRouteMinutes(seconds) else context.getString(R.string.shortcut_no_time)
            detail.text = saved.label?.substringBefore(",")?.trim().orEmpty()
        }
        card.contentDescription = context.getString(R.string.shortcut_description, label.text, value.text, detail.text)
    }

    private fun renderChips() {
        petrolChip.isSelected = host.isPoiKindShown(PoiKind.FUEL)
        evChip.isSelected = host.isPoiKindShown(PoiKind.EV)
        // Petrol orange / EV green when off; on, the chip is ink and the icon follows the text.
        setIcon(petrolChip, R.drawable.ic_fuel, color(if (petrolChip.isSelected) R.color.sig_on_ink else R.color.sig_petrol))
        setIcon(evChip, R.drawable.ic_ev, color(if (evChip.isSelected) R.color.sig_on_ink else R.color.sig_ev))
    }

    /** An 18dp leading icon in [tint]. */
    private fun setIcon(
        view: TextView,
        iconRes: Int,
        tint: Int,
    ) {
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return
        icon.setBounds(0, 0, dp(18), dp(18))
        icon.setTint(tint)
        view.setCompoundDrawablesRelative(icon, null, null, null)
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun renderRecents() {
        // Child 0 is the "Recent" heading.
        while (recents.childCount > 1) recents.removeViewAt(1)
        val list = recentPlaces.load().take(PEEK_RECENTS)
        recents.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        val origin = host.currentLocation()
        for (place in list) {
            val row = LayoutInflater.from(context).inflate(R.layout.item_search_result, recents, false)
            row.findViewById<ImageView>(R.id.resultIcon).setImageResource(R.drawable.ic_history)
            row.findViewById<TextView>(R.id.resultName).text = place.name.ifBlank { place.label }
            val address = row.findViewById<TextView>(R.id.resultAddress)
            address.text = place.address.trim()
            address.visibility = if (place.address.isBlank()) View.GONE else View.VISIBLE
            val distance = row.findViewById<TextView>(R.id.resultDistance)
            if (origin != null) {
                distance.text =
                    NavFormat.formatDistance(
                        GeocodingService.distanceMeters(origin.latitude, origin.longitude, place.latitude, place.longitude),
                    )
            } else {
                distance.visibility = View.GONE
            }
            row.setOnClickListener { host.onRecentTapped(place) }
            recents.addView(row)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        /** Recent places shown on Home; the full list is in search. */
        const val PEEK_RECENTS = 2
    }
}
