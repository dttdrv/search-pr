package app.pane.browser.engine.prompts

import app.pane.core.prompts.SitePermission

/**
 * A site asks for something it needs the user's say on: location, camera, microphone, protected
 * media, MIDI. The UI shows [title] with Allow and Don't Allow, and a Remember switch when
 * [canRemember] (never in private tabs, which keep nothing, nor for kinds that aren't stored).
 *
 * Identical requests that arrive while the sheet is open join this one and get the same answer;
 * that is the bridge's doing, the sheet is shown once.
 */
class ContentPermissionRequest internal constructor(
    tabId: String,
    val origin: String,
    /** What is being asked, in terms of what Site Settings lists; empty for things it doesn't (MIDI). */
    val kinds: List<SitePermission>,
    val title: String,
    val canRemember: Boolean,
    /** `remember` is null when the request was withdrawn rather than answered by the user. */
    private val onAnswer: (allowed: Boolean, remember: Boolean?) -> Unit,
) : WebViewPromptRequest(tabId) {
    fun allow(remember: Boolean) = settle { onAnswer(true, remember && canRemember) }

    fun deny(remember: Boolean) = settle { onAnswer(false, remember && canRemember) }

    /** The page moved on or the tab closed: deny, and don't hold it against the site. */
    override fun dismiss() = settle { onAnswer(false, null) }
}

/**
 * The page's permission needs an Android runtime permission (camera, microphone, location) that
 * the app doesn't hold yet. The UI asks the system and reports back; with [requireAll] false, any
 * one of [permissions] is enough (approximate or precise location).
 */
class AndroidPermissionRequest internal constructor(
    tabId: String,
    val permissions: List<String>,
    val requireAll: Boolean,
    private val onResult: (granted: Boolean) -> Unit,
) : WebViewPromptRequest(tabId) {
    fun complete(granted: Boolean) = settle { onResult(granted) }

    override fun dismiss() = complete(false)
}
