package com.excellentmindblow.navos.signs

/**
 * Which signs are on screen, and how faded, for the arrow at [here] metres
 * along the route.
 *
 *  - Boards: only ONE at a time - the next one ahead. The board after it
 *    waits until this one is passed, so two boards never crowd the view.
 *  - A board fades in from [VIEW_AHEAD_M] and fades out again as the arrow
 *    nears it ([NEAR_FADE_START_M] -> [NEAR_HIDE_M]), so a board never fills
 *    the screen right in front of the arrow.
 *  - Traffic lights are small: every one within [LIGHTS_AHEAD_M] shows, and
 *    they stay until passed.
 *
 * Pure, so it is unit tested.
 */
object SignVisibility {
    const val VIEW_AHEAD_M = 900.0
    const val FADE_IN_M = 200.0
    const val NEAR_FADE_START_M = 140.0
    const val NEAR_HIDE_M = 90.0
    const val LIGHTS_AHEAD_M = 500.0
    const val LIGHT_PASSED_M = 15.0

    /** Signs to draw with their distance ahead and opacity (0..1), far ones first. */
    fun pick(
        signs: List<RoadSign>,
        here: Double,
    ): List<Triple<RoadSign, Double, Float>> {
        val out = ArrayList<Triple<RoadSign, Double, Float>>()
        // The one board: the nearest whose spot is still far enough ahead to show.
        val board =
            signs
                .filter { it.content !is SignContent.TrafficLight }
                .map { it to it.atMeters - here }
                .filter { (_, d) -> d > NEAR_HIDE_M && d < VIEW_AHEAD_M }
                .minByOrNull { it.second }
        if (board != null) {
            val d = board.second
            val fadeIn = ((VIEW_AHEAD_M - d) / FADE_IN_M).coerceIn(0.0, 1.0)
            val fadeNear = ((d - NEAR_HIDE_M) / (NEAR_FADE_START_M - NEAR_HIDE_M)).coerceIn(0.0, 1.0)
            out += Triple(board.first, d, (fadeIn * fadeNear).toFloat())
        }
        for (s in signs) {
            if (s.content !is SignContent.TrafficLight) continue
            val d = s.atMeters - here
            if (d <= -LIGHT_PASSED_M || d >= LIGHTS_AHEAD_M) continue
            val fadeIn = ((LIGHTS_AHEAD_M - d) / 100.0).coerceIn(0.0, 1.0)
            val fadeOut = ((d + LIGHT_PASSED_M) / LIGHT_PASSED_M).coerceIn(0.0, 1.0)
            out += Triple(s, d, (fadeIn * fadeOut).toFloat())
        }
        return out.filter { it.third > 0.01f }.sortedByDescending { it.second }
    }
}
