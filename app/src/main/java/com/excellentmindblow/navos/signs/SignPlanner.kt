package com.excellentmindblow.navos.signs

import com.excellentmindblow.navos.navigation.GeoUtils
import com.excellentmindblow.navos.navigation.RouteTrack
import com.excellentmindblow.navos.routing.RoadPoi
import com.excellentmindblow.navos.routing.Route
import com.excellentmindblow.navos.routing.RoutePoint
import com.excellentmindblow.navos.routing.RouteStep
import java.util.Locale

/**
 * Decides which 3D signs stand along a route and where, from the route's own
 * junction data (road numbers, signposted places, exit numbers, lanes,
 * roundabout exits) and what was found beside it (tolls, cameras, R&Rs,
 * traffic lights). Pure: no Android, so it is unit tested.
 */
object SignPlanner {
    /** Where each sign stands relative to the place it is about. */
    const val DIRECTION_BEFORE_M = 50.0
    const val LANES_BEFORE_M = 250.0
    const val CONFIRM_AFTER_M = 350.0
    const val TOLL_BEFORE_M = 400.0
    const val REST_BEFORE_M = 1000.0
    const val AES_BEFORE_M = 300.0

    /** Traffic lights stand on the left kerb (Malaysia drives on the left). */
    const val LIGHT_SIDE_M = -14.0

    /** Lights closer than this along the route are one junction. */
    const val LIGHT_MERGE_M = 60.0

    /** Extra map data must be this close to the line to count as on it. */
    private const val ON_LINE_M = 25.0

    private val TOURIST =
        listOf(
            "genting highlands", "batu caves", "cameron highlands", "bukit tinggi", "bukit fraser", "fraser's hill",
            "zoo negara", "taman negara", "legoland", "sunway lagoon", "bukit merah", "kek lok si", "gua tempurung",
            "cherating", "desaru", "a famosa", "bukit larut", "kundasang", "teluk cempedak", "pantai cenang",
            "putrajaya", "menara kl", "klcc",
        )

    fun plan(
        route: Route,
        roadPois: List<RoadPoi>,
        extras: List<RoadPoi>,
    ): List<RoadSign> {
        if (route.geometry.size < 2) return emptyList()
        val track = RouteTrack(route.geometry)
        val stepAt = stepProgress(route, track)
        val signs = ArrayList<RoadSign>()
        signs += junctionSigns(route.steps, stepAt)
        for (poi in roadPois) {
            val at = progressOf(poi, route.geometry, track) ?: continue
            when (poi.kind) {
                "toll" -> signs += RoadSign("toll@${at.toInt()}", (at - TOLL_BEFORE_M).coerceAtLeast(0.0), SignContent.Toll(poi.name))
                "camera" -> signs += RoadSign("aes@${at.toInt()}", (at - AES_BEFORE_M).coerceAtLeast(0.0), SignContent.Aes(limitOf(poi.maxspeed)))
            }
        }
        for (poi in extras) {
            if (poi.kind != "services" && poi.kind != "rest_area") continue
            val at = restProgressOf(poi, route.geometry, track) ?: continue
            val signAt = (at - REST_BEFORE_M).coerceAtLeast(0.0)
            if (at - signAt < 200.0) continue
            signs += RoadSign("rest@${at.toInt()}", signAt, SignContent.RestStop(poi.name, poi.kind == "services", at - signAt))
        }
        for (at in trafficLights(route, extras)) {
            signs += RoadSign("light@${at.toInt()}", at, SignContent.TrafficLight, sideMeters = LIGHT_SIDE_M)
        }
        return signs.sortedBy { it.atMeters }
    }

    /**
     * Distances from the start of the route of the traffic lights the driver
     * will face, one per junction. Lights mapped on each approach of the same
     * junction (or a pedestrian crossing beside it) count once; lights under
     * an expressway the route flies over are left out.
     */
    fun trafficLights(
        route: Route,
        extras: List<RoadPoi>,
    ): List<Double> {
        if (route.geometry.size < 2) return emptyList()
        val track = RouteTrack(route.geometry)
        val stepAt = stepProgress(route, track)
        val raw =
            extras
                .filter { it.kind == "signal" }
                .mapNotNull { progressOf(it, route.geometry, track) }
                .filter { at -> !onExpressway(route.steps, stepAt, at) }
                .sorted()
        val out = ArrayList<Double>()
        for (at in raw) {
            if (out.isEmpty() || at - out.last() > LIGHT_MERGE_M) out.add(at)
        }
        return out
    }

    /** How many of [lights] are still ahead of [progressMeters]. */
    fun lightsAhead(
        lights: List<Double>,
        progressMeters: Double,
    ): Int = lights.count { it > progressMeters - 5.0 }

    private fun onExpressway(
        steps: List<RouteStep>,
        stepAt: DoubleArray,
        at: Double,
    ): Boolean {
        var idx = 0
        for (i in steps.indices) if (stepAt[i] <= at) idx = i
        return steps.getOrNull(idx)?.ref?.uppercase(Locale.ROOT)?.startsWith("E") == true
    }

    private fun junctionSigns(
        steps: List<RouteStep>,
        stepAt: DoubleArray,
    ): List<RoadSign> {
        val out = ArrayList<RoadSign>()
        for (i in 1 until steps.size) {
            val step = steps[i]
            val prev = steps[i - 1]
            val at = stepAt[i]
            val onExpressway = isExpressway(prev.ref)
            if (step.maneuverType == "roundabout" || step.maneuverType == "rotary") {
                if (step.roundaboutExit > 0) {
                    out += RoadSign("rb@$i", (at - DIRECTION_BEFORE_M).coerceAtLeast(0.0), roundabout(step))
                }
                continue
            }
            val lanes = step.lanes
            if (lanes != null && lanes.size >= 2 && lanes.any { it.valid } && lanes.any { !it.valid }) {
                val label = targetLines(step).firstOrNull()
                out +=
                    RoadSign(
                        "lanes@$i",
                        (at - LANES_BEFORE_M).coerceAtLeast(0.0),
                        SignContent.Lanes(lanes.map { SignLane(it.indications, it.valid) }, label, onExpressway || isExpressway(step.ref)),
                    )
            }
            if (wantsDirectionSign(step)) {
                out += RoadSign("dir@$i", (at - DIRECTION_BEFORE_M).coerceAtLeast(0.0), direction(step, prev))
            }
            confirmation(steps, stepAt, i)?.let { out += it }
        }
        return out
    }

    private fun wantsDirectionSign(step: RouteStep): Boolean {
        if (step.maneuverType in setOf("depart", "arrive", "new name", "exit roundabout", "exit rotary")) return false
        if (step.maneuverType == "continue" && (step.modifier == null || step.modifier == "straight")) return false
        val hasData = step.destinations != null || step.exits != null || step.ref != null
        return hasData || step.maneuverType in setOf("fork", "off ramp", "on ramp")
    }

    internal fun direction(
        step: RouteStep,
        prev: RouteStep,
    ): SignContent.Direction {
        val arrow = arrowOf(step.modifier)
        val lines = targetLines(step).ifEmpty { listOf(SignLine(step.roadName.ifBlank { "Exit" })) }
        val green = isExpressway(prev.ref) || lines.any { line -> line.shields.any { isExpressway(it) } } || step.exits != null
        val lit = SignPanel(lines, arrow, lit = true, expressway = green)
        // The way you are NOT taking: staying on the road you are on.
        val stayName = prev.roadName.ifBlank { null }
        val splits = step.maneuverType in setOf("fork", "off ramp", "turn", "end of road") && arrow != SignArrow.STRAIGHT
        if (!splits || (stayName == null && prev.ref == null)) return SignContent.Direction(step.exits, listOf(lit))
        val stay =
            SignPanel(
                listOf(SignLine(stayName ?: prev.ref.orEmpty(), shields = listOfNotNull(prev.ref?.takeIf { stayName != null }))),
                SignArrow.STRAIGHT,
                lit = false,
                expressway = isExpressway(prev.ref),
            )
        val leftward = arrow == SignArrow.LEFT || arrow == SignArrow.SLIGHT_LEFT || arrow == SignArrow.UTURN
        return SignContent.Direction(step.exits, if (leftward) listOf(lit, stay) else listOf(stay, lit))
    }

    /** Sign lines for where a step leads: its signposted places, else its road. */
    internal fun targetLines(step: RouteStep): List<SignLine> {
        val dest = step.destinations
        val refs: List<String>
        val places: List<String>
        if (dest != null) {
            val colon = dest.indexOf(':')
            refs = if (colon >= 0) splitList(dest.substring(0, colon)) else emptyList()
            places = splitList(if (colon >= 0) dest.substring(colon + 1) else dest)
        } else {
            refs = listOfNotNull(step.ref)
            places = listOfNotNull(step.roadName.ifBlank { null })
        }
        val shields = refs.ifEmpty { listOfNotNull(step.ref) }.take(2)
        if (places.isEmpty()) return if (shields.isEmpty()) emptyList() else listOf(SignLine("", shields))
        return places.take(3).mapIndexed { i, place ->
            SignLine(place, shields = if (i == 0) shields else emptyList(), tourist = isTourist(place), symbol = symbolOf(place))
        }
    }

    private fun roundabout(step: RouteStep): SignContent.Roundabout {
        val exitAngle = angleOf(step.modifier)
        // Clockwise round the ring (left-hand traffic), from the arm you come in on.
        var sweep = (exitAngle - 180.0) % 360.0
        if (sweep <= 0.0) sweep += 360.0
        val n = step.roundaboutExit
        val others = (1 until n).map { k -> norm(180.0 + sweep * k / n) }
        val label = targetLines(step).firstOrNull() ?: SignLine(step.roadName.ifBlank { "Exit $n" })
        return SignContent.Roundabout(norm(exitAngle), others, label)
    }

    /** "Bentong 58 km" sign shortly after joining a numbered road. */
    private fun confirmation(
        steps: List<RouteStep>,
        stepAt: DoubleArray,
        i: Int,
    ): RoadSign? {
        val step = steps[i]
        val ref = step.ref ?: return null
        if (steps[i - 1].ref == ref) return null
        if (step.maneuverType !in setOf("on ramp", "merge", "fork", "turn", "end of road", "roundabout", "rotary", "off ramp")) return null
        val signAt = stepAt[i] + CONFIRM_AFTER_M
        if (step.distanceMeters < CONFIRM_AFTER_M + 150.0) return null
        val rows = ArrayList<Pair<String, Double>>()
        val seen = HashSet<String>()
        for (j in i + 1 until steps.size) {
            val place = targetLines(steps[j]).firstOrNull { it.text.isNotBlank() }?.text ?: continue
            if (steps[j].destinations == null) continue
            if (!seen.add(place.lowercase(Locale.ROOT))) continue
            rows += place to (stepAt[j] - signAt)
            if (rows.size == 3) break
        }
        if (rows.size < 2) return null
        return RoadSign("confirm@$i", signAt, SignContent.Confirmation(ref, rows, isExpressway(ref)))
    }

    /** Distance from the start of the route of each step's maneuver, matched in driving order. */
    internal fun stepProgress(
        route: Route,
        track: RouteTrack,
    ): DoubleArray {
        val out = DoubleArray(route.steps.size)
        var from = 0
        for ((i, step) in route.steps.withIndex()) {
            val snap = GeoUtils.snapToPolyline(step.point, route.geometry, from)
            if (snap == null) {
                out[i] = if (i == 0) 0.0 else out[i - 1]
            } else {
                out[i] = track.progressOf(snap)
                from = snap.segmentIndex
            }
        }
        return out
    }

    private fun progressOf(
        poi: RoadPoi,
        line: List<RoutePoint>,
        track: RouteTrack,
    ): Double? {
        val snap = GeoUtils.snapToPolyline(RoutePoint(poi.latitude, poi.longitude), line) ?: return null
        if (snap.distanceMeters > ON_LINE_M) return null
        return track.progressOf(snap)
    }

    /** R&Rs sit beside the road. Keep only ones on the driver's (left) side, so
     * the one serving the other carriageway doesn't get a sign. */
    private fun restProgressOf(
        poi: RoadPoi,
        line: List<RoutePoint>,
        track: RouteTrack,
    ): Double? {
        val p = RoutePoint(poi.latitude, poi.longitude)
        val snap = GeoUtils.snapToPolyline(p, line) ?: return null
        if (snap.distanceMeters > 220.0) return null
        if (snap.distanceMeters > 60.0) {
            var rel = GeoUtils.bearingDegrees(snap.point, p) - snap.bearing
            rel = ((rel + 540.0) % 360.0) - 180.0
            if (rel > 0.0) return null
        }
        return track.progressOf(snap)
    }

    internal fun arrowOf(modifier: String?): SignArrow =
        when (modifier) {
            "left", "sharp left" -> SignArrow.LEFT
            "slight left" -> SignArrow.SLIGHT_LEFT
            "right", "sharp right" -> SignArrow.RIGHT
            "slight right" -> SignArrow.SLIGHT_RIGHT
            "uturn" -> SignArrow.UTURN
            else -> SignArrow.STRAIGHT
        }

    private fun angleOf(modifier: String?): Double =
        when (modifier) {
            "slight right" -> 45.0
            "right" -> 90.0
            "sharp right" -> 135.0
            "uturn" -> 180.0
            "sharp left" -> -135.0
            "left" -> -90.0
            "slight left" -> -45.0
            else -> 0.0
        }

    private fun norm(a: Double): Double {
        var x = a % 360.0
        if (x > 180.0) x -= 360.0
        if (x <= -180.0) x += 360.0
        return x
    }

    internal fun isExpressway(ref: String?): Boolean {
        val r = ref?.trim()?.uppercase(Locale.ROOT) ?: return false
        return r.length >= 2 && r[0] == 'E' && r[1].isDigit()
    }

    internal fun isTourist(place: String): Boolean {
        val p = place.lowercase(Locale.ROOT)
        return TOURIST.any { p.contains(it) }
    }

    internal fun symbolOf(place: String): SignSymbol? {
        val p = place.lowercase(Locale.ROOT)
        return when {
            p.startsWith("klia") || p.contains("lapangan terbang") || p.contains("airport") -> SignSymbol.AIRPORT
            p.startsWith("hospital") || p.startsWith("hkl") -> SignSymbol.HOSPITAL
            p.startsWith("pelabuhan") || p.contains("westport") || p.contains("northport") -> SignSymbol.PORT
            p.contains("ciq") || p.contains("kastam") || p.contains("imigresen") || p.contains("bukit kayu hitam") -> SignSymbol.BORDER
            else -> null
        }
    }

    internal fun limitOf(maxspeed: String?): Int? = maxspeed?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()?.takeIf { it in 10..200 }

    private fun splitList(s: String): List<String> = s.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
