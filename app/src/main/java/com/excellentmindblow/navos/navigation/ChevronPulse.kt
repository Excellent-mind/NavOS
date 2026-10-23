package com.excellentmindblow.navos.navigation

/**
 * Timing of the light that runs through a corner's guide chevrons.
 *
 * Every [PERIOD_MS] a pulse sweeps the chevrons in driving order, each one
 * [STAGGER_MS] after the one before: it brightens over [RISE_MS] and dims
 * over [FALL_MS], both on the strong ease-out curve. The chevrons never
 * move - only their brightness does - so the cue reads in peripheral
 * vision without pulling the driver's eyes to the screen. The loop clock is
 * linear (constant motion); the per-chevron envelope is eased.
 */
object ChevronPulse {
    const val PERIOD_MS = 1600L
    const val STAGGER_MS = 70L
    const val RISE_MS = 200L
    const val FALL_MS = 420L

    /** 0..1 brightness of chevron [index] at [timeMs]; [ease] is the ease-out curve. */
    fun intensity(
        timeMs: Long,
        index: Int,
        ease: (Float) -> Float,
    ): Float {
        var local = (timeMs - index * STAGGER_MS) % PERIOD_MS
        if (local < 0) local += PERIOD_MS
        return when {
            local < RISE_MS -> ease(local.toFloat() / RISE_MS)
            local < RISE_MS + FALL_MS -> 1f - ease((local - RISE_MS).toFloat() / FALL_MS)
            else -> 0f
        }
    }
}
