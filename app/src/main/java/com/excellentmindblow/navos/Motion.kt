package com.excellentmindblow.navos

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Shared motion vocabulary, so every panel in the app moves with the same
 * feel. The stock Android interpolators are too soft: an accelerate-
 * decelerate card starts slowly at exactly the moment the driver is
 * looking at it, which reads as lag.
 *
 * Durations still scale with the system "Animator duration scale"
 * (Developer options / Remove animations), so reduced-motion settings
 * keep working without extra code here.
 */
object Motion {
    /** Strong ease-out: things appearing or reacting to a tap. */
    val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)

    /** iOS-style sheet curve: bottom sheets and cards sliding on / off. */
    val easeDrawer = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    private const val PRESS_SCALE = 0.96f
    private const val PRESS_DOWN_MS = 100L
    private const val PRESS_UP_MS = 160L

    /**
     * Gives a tappable view a subtle shrink while the finger is down, so a
     * tap is felt instantly - most useful while driving, when the driver
     * can only glance. Implemented as a [StateListAnimator] rather than a
     * touch listener so it never fights the view's own click / drag
     * handling. Only touches scale, so elevation is left as it was.
     */
    fun addPressFeedback(view: View) {
        view.stateListAnimator =
            StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled), scaleTo(view, PRESS_SCALE, PRESS_DOWN_MS))
                addState(intArrayOf(), scaleTo(view, 1f, PRESS_UP_MS))
            }
    }

    private fun scaleTo(
        view: View,
        scale: Float,
        durationMs: Long,
    ) = ObjectAnimator
        .ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
        ).apply {
            duration = durationMs
            interpolator = easeOut
        }
}
