package com.excellentmindblow.navos

import android.location.Location
import android.view.View
import android.widget.TextView
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.search.GeocodeResult
import com.excellentmindblow.navos.search.SavedPlaces

/**
 * The Search screen's own parts (Signal design): the All / Places /
 * Addresses / Petrol filters and the Saved rows (Home, Work with drive
 * time) shown while the box is empty. Typing, the network search and the
 * results list stay with [MainActivity], which runs every result list
 * through [filter] before showing it.
 */
class SearchScreen(
    private val root: View,
    private val savedPlaces: SavedPlaces,
    private val driveTimes: HomeEtas,
    private val host: Host,
) {
    enum class Filter { ALL, PLACES, ADDRESSES, PETROL }

    interface Host {
        fun currentLocation(): Location?

        /** Petrol stations already loaded near the driver, nearest first. */
        fun nearbyPetrol(): List<GeocodeResult>

        /** The filter changed: show the current results again. */
        fun onFilterChanged()

        fun onShortcutTapped(key: String)

        fun onChooseOnMap()
    }

    private val chips =
        mapOf(
            Filter.ALL to root.findViewById<TextView>(R.id.filterAll),
            Filter.PLACES to root.findViewById(R.id.filterPlaces),
            Filter.ADDRESSES to root.findViewById(R.id.filterAddresses),
            Filter.PETROL to root.findViewById(R.id.filterPetrol),
        )
    private val homeRow: View = root.findViewById(R.id.homeButton)
    private val workRow: View = root.findViewById(R.id.workButton)

    var filter = Filter.ALL
        private set

    init {
        chips.forEach { (f, chip) ->
            chip.setOnClickListener {
                if (filter != f) {
                    filter = f
                    renderChips()
                    host.onFilterChanged()
                }
            }
        }
        homeRow.setOnClickListener { host.onShortcutTapped(SavedPlaces.HOME) }
        workRow.setOnClickListener { host.onShortcutTapped(SavedPlaces.WORK) }
        root.findViewById<View>(R.id.chooseOnMapButton).setOnClickListener { host.onChooseOnMap() }
        driveTimes.addListener { renderSaved() }
        renderChips()
        renderSaved()
    }

    /** Back to "All" (each search starts unfiltered). */
    fun reset() {
        filter = Filter.ALL
        renderChips()
    }

    /** [results] narrowed to the chosen filter (Petrol lists nearby stations instead). */
    fun filter(
        results: List<GeocodeResult>,
        query: String,
    ): List<GeocodeResult> = apply(filter, results, query, if (filter == Filter.PETROL) host.nearbyPetrol() else emptyList())

    fun renderSaved() {
        renderSavedRow(homeRow, SavedPlaces.HOME, R.string.shortcut_home, R.drawable.ic_home)
        renderSavedRow(workRow, SavedPlaces.WORK, R.string.shortcut_work, R.drawable.ic_work)
    }

    private fun renderSavedRow(
        row: View,
        key: String,
        titleRes: Int,
        iconRes: Int,
    ) {
        val context = row.context
        val title = context.getString(titleRes)
        val saved = savedPlaces.get(key)
        if (saved == null) {
            PlaceRows.bind(row, GeocodeResult(title, 0.0, 0.0, title, context.getString(R.string.shortcut_set_detail)), origin = null, iconRes = iconRes)
            return
        }
        val place = GeocodeResult(title, saved.latitude, saved.longitude, title, saved.label?.substringBefore(",")?.trim().orEmpty())
        val seconds = driveTimes.secondsTo(key, RoutePoint(saved.latitude, saved.longitude))
        PlaceRows.bind(
            row,
            place,
            origin = host.currentLocation(),
            iconRes = iconRes,
            value = seconds?.let { NavFormat.formatRouteMinutes(it) },
        )
    }

    private fun renderChips() {
        chips.forEach { (f, chip) -> chip.isSelected = f == filter }
    }

    companion object {
        /**
         * Places keeps named places, Addresses keeps houses and roads. Petrol
         * shows the stations in [petrol]; with a query, only those whose name
         * matches it, unless none do.
         */
        fun apply(
            filter: Filter,
            results: List<GeocodeResult>,
            query: String,
            petrol: List<GeocodeResult>,
        ): List<GeocodeResult> =
            when (filter) {
                Filter.ALL -> results
                Filter.PLACES -> results.filter { !it.isAddress }
                Filter.ADDRESSES -> results.filter { it.isAddress }
                Filter.PETROL -> {
                    val q = query.trim()
                    val matching = if (q.isEmpty()) petrol else petrol.filter { it.name.contains(q, ignoreCase = true) }
                    matching.ifEmpty { petrol }
                }
            }
    }
}
