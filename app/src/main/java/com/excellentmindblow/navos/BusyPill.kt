package com.excellentmindblow.navos

import android.os.Handler
import android.view.View
import android.widget.TextView

/**
 * The one "working on it" pill at the top of the map (text + [SignalLoaderView]):
 * recalculating, finding or updating a route, adding or skipping a stop.
 * Several jobs can be busy at once; the most important one is shown.
 *
 * It appears only if a job takes longer than [SHOW_DELAY_MS] (quick answers
 * don't flash it) and then stays at least [MIN_SHOWN_MS] (no flicker).
 * In and out on the same path: fade + 24 dp slide, Motion.easeOut.
 */
class BusyPill(
    private val pill: View,
    private val label: TextView,
    private val handler: Handler,
) {
    /** Most important first. */
    enum class Job { RECALCULATING, ROUTE, STOP }

    private val active = LinkedHashMap<Job, String>()
    private var shown = false
    private var shownAtMs = 0L

    private val showNow = Runnable { reveal() }
    private val hideNow = Runnable { conceal() }

    fun set(
        job: Job,
        text: String?,
    ) {
        if (text == null) active.remove(job) else active[job] = text
        val top = Job.values().firstOrNull { it in active }
        if (top != null) {
            handler.removeCallbacks(hideNow)
            label.text = active[top]
            if (!shown) {
                handler.removeCallbacks(showNow)
                handler.postDelayed(showNow, SHOW_DELAY_MS)
            }
        } else {
            handler.removeCallbacks(showNow)
            if (shown) {
                val wait = (shownAtMs + MIN_SHOWN_MS - now()).coerceAtLeast(0L)
                handler.removeCallbacks(hideNow)
                handler.postDelayed(hideNow, wait)
            }
        }
    }

    fun isBusy(job: Job) = job in active

    fun clear() {
        active.clear()
        handler.removeCallbacks(showNow)
        handler.removeCallbacks(hideNow)
        if (shown) conceal()
    }

    private fun reveal() {
        if (active.isEmpty() || shown) return
        shown = true
        shownAtMs = now()
        val slide = SLIDE_DP * pill.resources.displayMetrics.density
        pill.animate().cancel()
        pill.visibility = View.VISIBLE
        pill.alpha = 0f
        pill.translationY = -slide
        pill.animate().alpha(1f).translationY(0f).setDuration(IN_MS).setInterpolator(Motion.easeOut).start()
        pill.announceForAccessibility(label.text)
    }

    private fun conceal() {
        if (!shown || active.isNotEmpty()) return
        shown = false
        val slide = SLIDE_DP * pill.resources.displayMetrics.density
        pill.animate().cancel()
        pill
            .animate()
            .alpha(0f)
            .translationY(-slide)
            .setDuration(OUT_MS)
            .setInterpolator(Motion.easeOut)
            .withEndAction { if (!shown) pill.visibility = View.GONE }
            .start()
    }

    private fun now() = android.os.SystemClock.uptimeMillis()

    private companion object {
        const val SHOW_DELAY_MS = 300L
        const val MIN_SHOWN_MS = 600L
        const val IN_MS = 220L
        const val OUT_MS = 160L
        const val SLIDE_DP = 24f
    }
}
