package app.pane.browser.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/**
 * UIScrollView's rubber band: dragging past an edge moves the content with growing resistance,
 * and letting go (or flinging into an edge) springs it back. Replaces Android's stretch effect.
 */
class BounceOverscroll(private val dimension: Float) : OverscrollEffect {
    private var offsetX by mutableFloatStateOf(0f)
    private var offsetY by mutableFloatStateOf(0f)
    private var animating by mutableFloatStateOf(0f)

    override val isInProgress: Boolean get() = offsetX != 0f || offsetY != 0f || animating != 0f

    override val node: DelegatableNode = BounceNode(this)

    internal val translationX: Float get() = offsetX
    internal val translationY: Float get() = offsetY

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        // Scrolling back towards the content first eats up any overscroll.
        val usedX = relax(offsetX, delta.x)
        val usedY = relax(offsetY, delta.y)
        offsetX += usedX
        offsetY += usedY
        val remaining = Offset(delta.x - usedX, delta.y - usedY)

        val consumed = performScroll(remaining)
        val leftover = remaining - consumed
        if (source == NestedScrollSource.UserInput) {
            if (leftover.x != 0f) offsetX = rubberBand(offsetX, leftover.x)
            if (leftover.y != 0f) offsetY = rubberBand(offsetY, leftover.y)
            return delta
        }
        return Offset(usedX, usedY) + consumed
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (offsetX != 0f || offsetY != 0f) {
            settle(Velocity.Zero)
            return
        }
        val left = performFling(velocity)
        // A fling that hits an edge bounces: start at rest and let the spring's velocity carry it out and back.
        if (abs(left.y) > 50f || abs(left.x) > 50f) settle(Velocity(left.x * 0.2f, left.y * 0.2f))
    }

    private suspend fun settle(velocity: Velocity) {
        animating = 1f
        try {
            val spec = spring<Float>(dampingRatio = 1f, stiffness = Motion.stiffness(0.42f))
            if (offsetY != 0f || velocity.y != 0f) {
                Animatable(offsetY).animateTo(0f, spec, initialVelocity = velocity.y) { offsetY = value }
            }
            if (offsetX != 0f || velocity.x != 0f) {
                Animatable(offsetX).animateTo(0f, spec, initialVelocity = velocity.x) { offsetX = value }
            }
        } finally {
            offsetX = 0f
            offsetY = 0f
            animating = 0f
        }
    }

    /** The part of [delta] that moves [offset] back towards zero, never past it. */
    private fun relax(offset: Float, delta: Float): Float {
        if (offset == 0f || delta == 0f || offset.sign == delta.sign) return 0f
        return if (abs(delta) >= abs(offset)) -offset else delta
    }

    /** iOS's formula: the further you pull, the less the content follows. */
    private fun rubberBand(current: Float, add: Float): Float {
        val d = dimension.coerceAtLeast(1f)
        val raw = inverse(current, d) + add
        return (1f - 1f / (abs(raw) * 0.55f / d + 1f)) * d * raw.sign
    }

    private fun inverse(value: Float, d: Float): Float {
        if (value == 0f) return 0f
        val y = abs(value).coerceAtMost(d * 0.999f)
        return (d / 0.55f) * (1f / (1f - y / d) - 1f) * value.sign
    }
}

private class BounceNode(private val effect: BounceOverscroll) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = effect.translationX
                translationY = effect.translationY
            }
        }
    }
}

/** Supplies [BounceOverscroll] to every scrollable in the app. */
class BounceOverscrollFactory(private val dimension: Float) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = BounceOverscroll(dimension)

    override fun equals(other: Any?) = other is BounceOverscrollFactory && other.dimension == dimension

    override fun hashCode() = dimension.hashCode()
}
