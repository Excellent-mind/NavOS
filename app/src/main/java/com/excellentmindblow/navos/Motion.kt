package com.excellentmindblow.navos

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
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

    /** False when the user has turned animations off (Developer options /
     * accessibility "Remove animations"). Springs don't follow the system
     * duration scale the way ValueAnimator does, so callers check this
     * and jump straight to the end state instead. */
    fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    /**
     * How far a release at [velocityPxPerSec] would carry on coasting, using
     * the same exponential decay as scroll flinging (Apple's projection from
     * "Designing Fluid Interfaces"). Snap decisions use the projected
     * landing point rather than where the finger let go, so a short flick
     * still throws a sheet all the way.
     */
    fun project(
        velocityPxPerSec: Float,
        decelerationRate: Float = 0.998f,
    ): Float = (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)

    /** Resistance past an edge: the further the drag goes beyond the bound,
     * the less the content follows, instead of hitting a hard stop. */
    fun rubberband(
        overshoot: Float,
        dimension: Float,
        constant: Float = 0.55f,
    ): Float = (overshoot * dimension * constant) / (dimension + constant * kotlin.math.abs(overshoot))

    /** Spring stiffness for a given response time (seconds), so springs can
     * be tuned in Apple's "response" terms rather than raw stiffness. */
    fun stiffnessFor(responseSeconds: Float): Float {
        val omega = 2f * Math.PI.toFloat() / responseSeconds
        return omega * omega
    }

    /** A firm tick for moments that commit something (starting a drive,
     * confirming arrival). Kept to a handful of places so it stays meaningful. */
    fun confirm(view: View) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        view.performHapticFeedback(type)
    }

    /** A light tick when a dragged sheet snaps to a new resting state. */
    fun snap(view: View) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        view.performHapticFeedback(type)
    }

    private const val SURFACE_IN_MS = 240L
    private const val SURFACE_OUT_MS = 160L
    private const val SURFACE_SCALE = 0.96f

    /**
     * Shows a floating card or banner by having it grow out of the edge it
     * is attached to ([pivotFraction] 0 = top edge, 1 = bottom edge) while
     * fading in. Uses scale + alpha only, so it never fights code that
     * positions the view with translationY. Safe to call on every GPS fix:
     * a view that is already showing (or on its way in) is left alone.
     */
    fun reveal(
        view: View,
        pivotFraction: Float,
    ) {
        if (view.visibility == View.VISIBLE && view.getTag(R.id.motion_leaving) != true) return
        view.setTag(R.id.motion_leaving, false)
        view.animate().cancel()
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.scaleX = SURFACE_SCALE
            view.scaleY = SURFACE_SCALE
            view.visibility = View.VISIBLE
        }
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        val setPivot = {
            view.pivotX = view.width / 2f
            view.pivotY = view.height * pivotFraction
        }
        if (view.height > 0) setPivot() else view.post(setPivot)
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(SURFACE_IN_MS).setInterpolator(easeOut).start()
    }

    /** The reverse of [reveal]: shrinks back toward the same edge it came
     * from (same path out as in), a little faster than it arrived, then
     * hides the view. */
    fun dismiss(view: View) {
        if (view.visibility != View.VISIBLE || view.getTag(R.id.motion_leaving) == true) return
        view.animate().cancel()
        if (!animationsEnabled()) {
            view.visibility = View.GONE
            return
        }
        view.setTag(R.id.motion_leaving, true)
        view.animate().alpha(0f).scaleX(SURFACE_SCALE).scaleY(SURFACE_SCALE)
            .setDuration(SURFACE_OUT_MS).setInterpolator(easeOut)
            .withEndAction {
                if (view.getTag(R.id.motion_leaving) == true) {
                    view.visibility = View.GONE
                    view.setTag(R.id.motion_leaving, false)
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
            }.start()
    }
}
