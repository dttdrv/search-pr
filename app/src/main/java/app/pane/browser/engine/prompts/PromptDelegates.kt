package app.pane.browser.engine.prompts

import app.pane.browser.engine.PromptQueue
import org.mozilla.geckoview.GeckoSession

/**
 * Bridges Gecko's prompt callbacks (alert/confirm/prompt, auth, <select>, pickers, popups, share…)
 * into [PromptQueue] requests rendered by the prompt UI.
 *
 * STUB: owned by the prompts work stream.
 */
class WebPromptDelegate(
    private val tabId: String,
    private val queue: PromptQueue,
) : GeckoSession.PromptDelegate

/**
 * Site permission requests: location, camera/microphone, notifications, autoplay, DRM, storage
 * access, local network. Remembered per site by Gecko's permission store.
 *
 * STUB: owned by the prompts work stream.
 */
class WebPermissionDelegate(
    private val tabId: String,
    private val queue: PromptQueue,
) : GeckoSession.PermissionDelegate
