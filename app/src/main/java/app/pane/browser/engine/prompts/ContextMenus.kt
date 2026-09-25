package app.pane.browser.engine.prompts

import app.pane.browser.engine.PromptQueue
import org.mozilla.geckoview.GeckoSession

/**
 * Turns a long-press in a page into a context menu request (links, images, media).
 *
 * STUB: owned by the prompts work stream.
 */
object ContextMenus {
    fun request(queue: PromptQueue, tabId: String, private: Boolean, x: Int, y: Int, element: GeckoSession.ContentDelegate.ContextElement) = Unit
}
