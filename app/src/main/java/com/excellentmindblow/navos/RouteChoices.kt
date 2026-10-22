package com.excellentmindblow.navos

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.routing.RoutePoint

/** One route card in the preview. */
data class RouteChoice(
    /** Index in the app's route list (what selecting it sets). */
    val index: Int,
    val durationSeconds: Double,
    val distanceMeters: Double,
    val via: String?,
    /** Toll plazas on the route, or null while they are still being looked up. */
    val tolls: Int?,
    val fastest: Boolean,
    val selected: Boolean,
    /** Traffic lights (one per junction) on the route, or null while looked up. */
    val lights: Int? = null,
)

/** Which route cards the preview shows, and where each route's time bubble sits. */
object RouteChoices {
    /** Cards in the sheet; the rest are behind "All routes". */
    const val SHOWN = 3

    class Input(
        val durationSeconds: Double,
        val distanceMeters: Double,
        val via: String?,
        val tolls: Int?,
        val lights: Int? = null,
    )

    /**
     * Every route quickest first. With [limit], only that many - always
     * including the selected one (it replaces the slowest shown).
     */
    fun order(
        routes: List<Input>,
        selected: Int,
        limit: Int = Int.MAX_VALUE,
    ): List<RouteChoice> {
        if (routes.isEmpty()) return emptyList()
        val fastest = routes.indices.minBy { routes[it].durationSeconds }
        var picked = routes.indices.sortedBy { routes[it].durationSeconds }.take(limit)
        if (selected in routes.indices && selected !in picked) {
            picked = (picked.dropLast(1) + selected).sortedBy { routes[it].durationSeconds }
        }
        return picked.map { i ->
            val r = routes[i]
            RouteChoice(i, r.durationSeconds, r.distanceMeters, r.via, r.tolls, fastest = routes.size > 1 && i == fastest, selected = i == selected, lights = r.lights)
        }
    }

    /** Halfway along [line] by distance - where the route's time bubble goes. */
    fun labelPoint(line: List<RoutePoint>): RoutePoint? {
        if (line.isEmpty()) return null
        if (line.size == 1) return line[0]
        val lengths = line.zipWithNext { a, b -> GeoUtils.distanceMeters(a, b) }
        var remaining = lengths.sum() / 2
        for ((i, len) in lengths.withIndex()) {
            if (remaining <= len && len > 0) {
                val f = remaining / len
                val a = line[i]
                val b = line[i + 1]
                return RoutePoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)
            }
            remaining -= len
        }
        return line.last()
    }
}
