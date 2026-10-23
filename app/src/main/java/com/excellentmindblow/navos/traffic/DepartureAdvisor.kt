package com.excellentmindblow.navos.traffic

import java.util.Calendar
import java.util.TimeZone

/**
 * "Leave at 8:00 PM to save 12 min": compares leaving now with leaving in
 * one to three hours, using how fast each road usually is at that time of
 * the week (the traffic server's typical speeds). Only worth saying when it
 * saves real time. Pure, so it is unit tested.
 */
object DepartureAdvisor {
    const val MIN_SAVE_S = 600.0
    const val MIN_SAVE_SHARE = 0.10
    val OFFSETS_H = listOf(0, 1, 2, 3)

    data class Advice(
        val offsetHours: Int,
        val savedSeconds: Double,
    )

    /** [baseS]: the route's free-road time; [delayByOffsetH]: typical traffic delay leaving in that many hours. */
    fun advise(
        baseS: Double,
        delayByOffsetH: Map<Int, Double>,
    ): Advice? {
        val nowTotal = baseS + (delayByOffsetH[0] ?: return null)
        val best = delayByOffsetH.filterKeys { it > 0 }.minByOrNull { it.value } ?: return null
        val saved = nowTotal - (baseS + best.value)
        return if (saved >= MIN_SAVE_S && saved >= nowTotal * MIN_SAVE_SHARE) Advice(best.key, saved) else null
    }

    /** The server's time-of-week bucket (weekend*24 + hour, Malaysia time) at [ms]. */
    fun bucketAt(ms: Long): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur")).apply { timeInMillis = ms }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        return (if (weekend) 24 else 0) + cal.get(Calendar.HOUR_OF_DAY)
    }
}
