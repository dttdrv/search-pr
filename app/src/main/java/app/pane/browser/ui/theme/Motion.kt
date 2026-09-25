package app.pane.browser.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI

/**
 * Springs described the way UIKit/SwiftUI describe them: a [response] (seconds for one
 * oscillation, i.e. how quick it feels) and a damping fraction. Everything in Pane moves on these,
 * so gestures hand off to animations without a visible seam.
 */
object Motion {
    fun stiffness(response: Float): Float = ((2 * PI / response) * (2 * PI / response)).toFloat()

    fun <T> spring(response: Float = 0.5f, damping: Float = 0.86f, visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = damping, stiffness = stiffness(response), visibilityThreshold = visibilityThreshold)

    /** Default for most transitions (≈ SwiftUI `.smooth`). */
    fun <T> smooth(): SpringSpec<T> = spring(0.5f, 1f)

    /** Quick and settled (≈ SwiftUI `.snappy`). Buttons, toggles, small elements. */
    fun <T> snappy(): SpringSpec<T> = spring(0.4f, 0.86f)

    /** A touch of overshoot for things that "land": sheets, the tab you picked. */
    fun <T> bouncy(): SpringSpec<T> = spring(0.5f, 0.72f)

    /** Tracks the finger, used while a gesture is in flight. */
    fun <T> interactive(): SpringSpec<T> = spring(0.2f, 0.86f)

    /** Screen pushes and pops. */
    fun <T> push(): SpringSpec<T> = spring(0.42f, 1f)

    val pushOffset: FiniteAnimationSpec<IntOffset> = spring(0.42f, 1f, IntOffset(1, 1))
    val sizeSpring: FiniteAnimationSpec<IntSize> = spring(0.42f, 0.9f, IntSize(1, 1))

    /** For opacity, where a spring's overshoot would be meaningless. */
    fun <T> fade(durationMs: Int = 180): FiniteAnimationSpec<T> = tween(durationMs)
}

/** True when the user asked for reduced motion; springs collapse to quick fades. */
val LocalReduceMotion = staticCompositionLocalOf { false }
