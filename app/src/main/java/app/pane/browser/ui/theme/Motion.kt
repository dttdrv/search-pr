package app.pane.browser.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Pane's springs: the platform's own stiffnesses, all critically damped, so nothing overshoots and
 * a gesture hands off to an animation without a seam.
 *
 * Which token for what (use these; don't invent springs at call sites):
 *  - [snappy] small things reacting: presses, toggles, icons swapping, counters, chips.
 *  - [smooth] things moving, arriving or changing size: sheets, the bar, a field opening, layout.
 *  - [push]   whole screens travelling: navigation, the tab overview.
 *  - [fade]   opacity, on the standard easing.
 */
object Motion {
    fun <T> snappy(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)

    fun <T> smooth(): SpringSpec<T> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    fun <T> push(): SpringSpec<T> = smooth()

    val pushOffset: FiniteAnimationSpec<IntOffset> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntOffset(1, 1))
    val sizeSpring: FiniteAnimationSpec<IntSize> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntSize(1, 1))

    fun <T> fade(durationMs: Int = 180): FiniteAnimationSpec<T> = tween(durationMs)
}

/** True when the user asked for reduced motion; springs collapse to quick fades. */
val LocalReduceMotion = staticCompositionLocalOf { false }
