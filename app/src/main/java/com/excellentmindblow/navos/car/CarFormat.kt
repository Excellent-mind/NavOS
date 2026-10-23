package com.excellentmindblow.navos.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import com.excellentmindblow.navos.R
import com.excellentmindblow.navos.Units
import java.util.TimeZone
import com.excellentmindblow.navos.tr

/** Converts NavOS route/step data into Car App Library model objects. */
object CarFormat {
    fun distance(meters: Double): Distance {
        val m = meters.coerceAtLeast(0.0)
        return if (Units.miles) {
            if (m < 160) {
                val feet = (Math.round(m * 3.28084 / 50.0) * 50).coerceAtLeast(50L)
                Distance.create(feet.toDouble(), Distance.UNIT_FEET)
            } else {
                Distance.create(Math.round(m / 1609.344 * 10.0) / 10.0, Distance.UNIT_MILES_P1)
            }
        } else {
            when {
                m < 1000 -> {
                    val meter = (Math.round(m / 10.0) * 10).coerceAtLeast(10L)
                    Distance.create(meter.toDouble(), Distance.UNIT_METERS)
                }
                m < 100_000 -> Distance.create(Math.round(m / 100.0) / 10.0, Distance.UNIT_KILOMETERS_P1)
                else -> Distance.create(Math.round(m / 1000.0).toDouble(), Distance.UNIT_KILOMETERS)
            }
        }
    }

    fun estimate(
        meters: Double,
        seconds: Double,
    ): TravelEstimate {
        val secs = seconds.coerceAtLeast(0.0).toLong()
        return TravelEstimate.Builder(
            distance(meters),
            DateTimeWithZone.create(System.currentTimeMillis() + secs * 1000L, TimeZone.getDefault()),
        )
            .setRemainingTimeSeconds(secs)
            .build()
    }

    /** "1 h 11 min" / "25 min" */
    fun duration(seconds: Double): String {
        val totalMin = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) tr("$h h $m min", "$h j $m min") else "$m min"
    }

    fun step(
        ctx: CarContext,
        instruction: String,
        type: String,
        modifier: String?,
        road: String?,
    ): Step {
        val icon = CarIcon.Builder(IconCompat.createWithResource(ctx, iconRes(type, modifier))).build()
        val maneuver = Maneuver.Builder(maneuverType(type, modifier)).setIcon(icon).build()
        val builder = Step.Builder(instruction).setManeuver(maneuver)
        if (!road.isNullOrBlank()) builder.setRoad(road)
        return builder.build()
    }

    // NavOS (OSRM) maneuver words -> Car App Library maneuver types. Malaysia
    // drives on the left, so roundabouts are counter-clockwise.
    private fun maneuverType(
        type: String,
        modifier: String?,
    ): Int {
        val mod = modifier.orEmpty()
        val left = mod.contains("left")
        val right = mod.contains("right")
        return when {
            type == "depart" -> Maneuver.TYPE_DEPART
            type == "arrive" ->
                when {
                    left -> Maneuver.TYPE_DESTINATION_LEFT
                    right -> Maneuver.TYPE_DESTINATION_RIGHT
                    else -> Maneuver.TYPE_DESTINATION
                }
            type == "roundabout" || type == "rotary" || type == "roundabout turn" -> Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
            type == "exit roundabout" || type == "exit rotary" -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
            type == "uturn" || mod == "uturn" -> Maneuver.TYPE_U_TURN_LEFT
            type == "fork" ->
                when {
                    left -> Maneuver.TYPE_FORK_LEFT
                    right -> Maneuver.TYPE_FORK_RIGHT
                    else -> Maneuver.TYPE_STRAIGHT
                }
            type == "off ramp" ->
                if (left) Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT else Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
            type == "merge" ->
                when {
                    left -> Maneuver.TYPE_MERGE_LEFT
                    right -> Maneuver.TYPE_MERGE_RIGHT
                    else -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
                }
            else ->
                when (mod) {
                    "sharp right" -> Maneuver.TYPE_TURN_SHARP_RIGHT
                    "right" -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                    "slight right" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                    "slight left" -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                    "left" -> Maneuver.TYPE_TURN_NORMAL_LEFT
                    "sharp left" -> Maneuver.TYPE_TURN_SHARP_LEFT
                    else -> Maneuver.TYPE_STRAIGHT
                }
        }
    }

    // Same arrows the phone UI uses (res/drawable/ic_turn_*).
    private fun iconRes(
        type: String,
        modifier: String?,
    ): Int {
        val mod = modifier.orEmpty()
        return when {
            type == "arrive" -> R.drawable.ic_turn_arrive
            type == "roundabout" || type == "rotary" || type == "roundabout turn" ||
                type == "exit roundabout" || type == "exit rotary" ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_roundabout_left
                    mod.contains("right") -> R.drawable.ic_turn_roundabout_right
                    else -> R.drawable.ic_turn_roundabout_straight
                }
            type == "uturn" || mod == "uturn" -> R.drawable.ic_turn_uturn
            type == "fork" || type == "off ramp" || type == "on ramp" ->
                when {
                    mod.contains("left") -> R.drawable.ic_turn_fork_left
                    mod.contains("right") -> R.drawable.ic_turn_fork_right
                    else -> R.drawable.ic_turn_straight
                }
            else ->
                when (mod) {
                    "sharp right" -> R.drawable.ic_turn_sharp_right
                    "right" -> R.drawable.ic_turn_right
                    "slight right" -> R.drawable.ic_turn_slight_right
                    "slight left" -> R.drawable.ic_turn_slight_left
                    "left" -> R.drawable.ic_turn_left
                    "sharp left" -> R.drawable.ic_turn_sharp_left
                    else -> R.drawable.ic_turn_straight
                }
        }
    }
}
