package app.pane.browser.engine.prompts

import android.content.Intent

/**
 * What [PromptBridge] needs from the activity: a way to start the system file picker for
 * `<input type=file>` and hear back. `MainActivity` sets [launchFileChooser] while it is alive.
 *
 * The pending callback lives here, not in the activity, so a picker that outlives the activity
 * (rotation, process pressure) still delivers its result to the page.
 */
object PromptEnvironment {
    /**
     * Starts [Intent] for a result and calls back with `(resultCode, data)` once, when the picker
     * closes. Null while there is no activity to launch from.
     */
    @Volatile
    var launchFileChooser: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null

    @Volatile
    internal var pendingFileResult: ((Int, Intent?) -> Unit)? = null

    /** Called by the activity's result launcher. */
    internal fun deliverFileResult(resultCode: Int, data: Intent?) {
        val callback = pendingFileResult
        pendingFileResult = null
        callback?.invoke(resultCode, data)
    }
}
