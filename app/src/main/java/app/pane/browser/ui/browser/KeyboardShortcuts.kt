package app.pane.browser.ui.browser

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Hardware-keyboard commands, for tablets and Chromebooks. */
enum class Shortcut { NewTab, NewPrivateTab, CloseTab, FocusAddress, Reload, Find, NextTab, PreviousTab, Back, Forward, ShowTabs }

/**
 * Translates key presses at the activity level (before GeckoView sees them) into [Shortcut]s the
 * browser screen acts on.
 */
object KeyboardShortcuts {
    private val _events = MutableSharedFlow<Shortcut>(extraBufferCapacity = 8)
    val events: SharedFlow<Shortcut> = _events.asSharedFlow()

    /** Returns true when [event] was a Pane shortcut and has been consumed. */
    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val shortcut = when {
            event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_N -> Shortcut.NewPrivateTab
            event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_TAB -> Shortcut.PreviousTab
            event.isCtrlPressed && event.isShiftPressed && event.keyCode == KeyEvent.KEYCODE_BACKSLASH -> Shortcut.ShowTabs
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_T -> Shortcut.NewTab
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_W -> Shortcut.CloseTab
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_L -> Shortcut.FocusAddress
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_R -> Shortcut.Reload
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F -> Shortcut.Find
            event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_TAB -> Shortcut.NextTab
            event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Shortcut.Back
            event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Shortcut.Forward
            event.keyCode == KeyEvent.KEYCODE_F5 -> Shortcut.Reload
            else -> return false
        }
        return _events.tryEmit(shortcut)
    }
}
