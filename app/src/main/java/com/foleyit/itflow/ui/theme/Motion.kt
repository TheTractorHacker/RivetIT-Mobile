package com.foleyit.itflow.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Motion tokens from the design reference's "Interactions & Behavior" section — a springy,
 * slightly-overshooting easing used for screen transitions, sheet slide-ups, the nav drawer, and
 * press states, distinct from stock Material's standard easing curves.
 */
object Motion {
    /** cubic-bezier(.34, 1.56, .64, 1) — overshoots past 100% before settling, the "springy" feel. */
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** cubic-bezier(.2, 0, 0, 1) — standard deceleration, used for fades/scrims. */
    val EaseStandard: Easing = FastOutSlowInEasing

    const val DurationFast = 150
    const val DurationMedium = 340

    /**
     * Screen transition (fade + rise) and sheet slide-up duration/easing. Generic over T (Float
     * for fades, IntOffset for slides, etc.) since [tween]'s easing remaps the 0..1 progress
     * curve abstractly — the type is inferred from each call site's own animationSpec parameter.
     */
    fun <T> medium(): FiniteAnimationSpec<T> = tween(durationMillis = DurationMedium, easing = EaseSpring)

    /** Press-state (button/card 0.95x, FAB 0.90x) duration/easing. */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(durationMillis = DurationFast, easing = EaseSpring)

    /** Scrim/fade duration/easing — deliberately the gentler standard curve, not the spring. */
    fun <T> standardFast(): FiniteAnimationSpec<T> = tween(durationMillis = DurationFast, easing = EaseStandard)
}
