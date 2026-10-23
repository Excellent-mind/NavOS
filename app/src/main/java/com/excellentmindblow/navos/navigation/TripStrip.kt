package com.excellentmindblow.navos.navigation

import com.excellentmindblow.navos.poi.PoiAlongRoute
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.traffic.RoadReports

/**
 * The whole trip on one straight line, for the route cards: where along the
 * route (metres from the start) each toll, camera, R&R, petrol station and EV
 * charger is, where the traffic lights are, and which stretches are slow or
 * jammed. Pure, so it is unit tested; [TripStripView] draws it.
 */
object TripStrip {
    enum class Kind { TOLL, CAMERA, RNR, PETROL, EV, REPORT }

    data class Mark(
        val kind: Kind,
        val atM: Double,
        val name: String,
        /** For [Kind.REPORT]: what was reported. */
        val report: RoadReports.Kind? = null,
    )

    /** A slow ([jam] = false) or jammed stretch, [fromM]..[toM] along the route. */
    data class Traffic(
        val fromM: Double,
        val toM: Double,
        val jam: Boolean,
    )

    data class Strip(
        val lengthM: Double,
        val marks: List<Mark>,
        val lightsM: List<Double>,
        val traffic: List<Traffic>,
    ) {
        fun count(kind: Kind) = marks.count { it.kind == kind }
    }

    /** Two of the same kind closer than this along the route are one place (a plaza's booths, both pumps of an R&R). */
    private val MERGE_M = mapOf(Kind.TOLL to 600.0, Kind.CAMERA to 200.0, Kind.RNR to 600.0, Kind.PETROL to 150.0, Kind.EV to 150.0, Kind.REPORT to 1.0)

    /**
     * [roadPois]: tolls and cameras on the route; [extras]: R&Rs, petrol, EV
     * chargers near it; [lightsM]: the traffic lights (metres along);
     * [onExpressway]: whether the route is expressway at a distance along it
     * (there only places on your side count, see [PoiAlongRoute]).
     */
    fun build(
        route: Route,
        roadPois: List<RoadPoi>,
        extras: List<RoadPoi>,
        lightsM: List<Double>,
        traffic: List<Traffic> = emptyList(),
        onExpressway: (Double) -> Boolean = { false },
        reports: List<RoadReports.OnRoute> = emptyList(),
    ): Strip? {
        if (route.geometry.size < 2) return null
        val track = RouteTrack(route.geometry)
        val marks = ArrayList<Mark>()
        for (poi in roadPois) {
            val kind =
                when (poi.kind) {
                    "toll" -> Kind.TOLL
                    "camera" -> Kind.CAMERA
                    else -> continue
                }
            val snap = GeoUtils.snapToPolyline(RoutePoint(poi.latitude, poi.longitude), route.geometry) ?: continue
            marks += Mark(kind, track.progressOf(snap), poi.name)
        }
        for (poi in extras) {
            val kind =
                when (poi.kind) {
                    "services" -> Kind.RNR
                    "fuel" -> Kind.PETROL
                    "charging" -> Kind.EV
                    else -> continue
                }
            // R&Rs sit on expressways: only the one on your side.
            val expressway = if (kind == Kind.RNR) { _: Double -> true } else onExpressway
            val spot = PoiAlongRoute.spot(track, RoutePoint(poi.latitude, poi.longitude), 0.0, 0, expressway) ?: continue
            marks += Mark(kind, spot.aheadM.coerceIn(0.0, track.length), poi.name)
        }
        for (r in reports) marks += Mark(Kind.REPORT, r.atM.coerceIn(0.0, track.length), r.report.kind.label, r.report.kind)
        val merged =
            marks
                .groupBy { it.kind }
                .flatMap { (kind, list) ->
                    val gap = MERGE_M.getValue(kind)
                    val out = ArrayList<Mark>()
                    for (m in list.sortedBy { it.atM }) {
                        val last = out.lastOrNull()
                        if (last != null && m.atM - last.atM < gap) {
                            if (last.name.isBlank() && m.name.isNotBlank()) out[out.size - 1] = last.copy(name = m.name)
                        } else {
                            out += m
                        }
                    }
                    out
                }.sortedBy { it.atM }
        return Strip(track.length, merged, lightsM.sorted(), traffic.sortedBy { it.fromM })
    }
}
