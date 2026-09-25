package app.pane.browser.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Crisp, sparse haptics in the spirit of iOS: confirm, tick, and rigid impacts only. */
class Haptics(private val view: View, private val enabled: () -> Boolean) {
    private fun perform(constant: Int) {
        if (enabled()) view.performHapticFeedback(constant)
    }

    /** A selection moved: segmented control, picker detent, tab swipe crossing a boundary. */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** A light tap acknowledging a press. */
    fun tap() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Something committed: tab closed, bookmark saved, gesture completed. */
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 -> if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CLOCK_TICK
        },
    )

    fun gestureStart() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CLOCK_TICK)

    fun gestureThreshold() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK)
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view) { enabled } }
}
