package app.pane.browser.engine.prompts

import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import app.pane.browser.engine.PromptRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A prompt waiting in the queue, and the WebView callback the page is blocked on.
 *
 * The callback is completed exactly once, by whoever gets there first: the user, the bridge (the
 * page navigated or the tab closed) or the queue ([dismiss]). Later answers are ignored, so the UI
 * can't double-complete a `JsResult` and a withdrawn prompt can't throw.
 */
abstract class WebViewPromptRequest(final override val tabId: String) : PromptRequest {
    final override val id: Long = PromptRequest.nextId()

    private val settled = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** True once answered or withdrawn. */
    val isSettled: Boolean get() = settled.get()

    /** Runs once, after the callback has been completed. */
    internal fun onSettled(listener: () -> Unit) {
        listeners += listener
    }

    /** Runs [answer] if nobody has answered yet; the engine may already have dropped the callback. */
    protected fun settle(answer: () -> Unit) {
        if (!settled.compareAndSet(false, true)) return
        try {
            answer()
        } finally {
            listeners.forEach { it() }
        }
    }
}

/** Calls into the engine, which may have torn the callback down already. */
internal inline fun quietly(block: () -> Unit) {
    try {
        block()
    } catch (_: Exception) {
        // The page took the prompt away itself; there is nobody left to answer.
    }
}

/**
 * `alert()`, `confirm()` and `prompt()`. [source] is who is asking: the host of the frame that
 * called, which may be an embedded third party rather than the tab's site.
 */
abstract class ScriptDialogRequest(
    tabId: String,
    val source: String?,
    val message: String,
    /** The page keeps opening dialogs; offer "Block more dialogs". */
    val offerOptOut: Boolean,
    private val onOptOut: () -> Unit,
) : WebViewPromptRequest(tabId) {
    /** Silences the page's further dialogs until the tab moves on; answer this one as usual. */
    fun blockFurtherDialogs() = onOptOut()
}

class AlertRequest internal constructor(
    tabId: String,
    private val result: JsResult,
    source: String?,
    message: String,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest(tabId, source, message, offerOptOut, onOptOut) {
    fun ok() = settle { quietly { result.confirm() } }

    override fun dismiss() = ok()
}

class ConfirmRequest internal constructor(
    tabId: String,
    private val result: JsResult,
    source: String?,
    message: String,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest(tabId, source, message, offerOptOut, onOptOut) {
    fun answer(ok: Boolean) = settle { quietly { if (ok) result.confirm() else result.cancel() } }

    override fun dismiss() = answer(false)
}

/** The page's `prompt()`. */
class TextInputRequest internal constructor(
    tabId: String,
    private val result: JsPromptResult,
    source: String?,
    message: String,
    val defaultValue: String,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest(tabId, source, message, offerOptOut, onOptOut) {
    fun submit(text: String) = settle { quietly { result.confirm(text) } }

    override fun dismiss() = settle { quietly { result.cancel() } }
}

/** HTTP authentication. Nothing typed here is stored. */
class AuthRequest internal constructor(
    tabId: String,
    private val handler: HttpAuthHandler,
    val host: String,
    /** The server's own description of what is protected; shown quietly, it is not trustworthy. */
    val realm: String,
    /** Asked for by something other than the page in the tab (an embedded frame or image). */
    val crossOrigin: Boolean,
    /** The password would travel unencrypted. */
    val insecure: Boolean,
) : WebViewPromptRequest(tabId) {
    fun submit(username: String, password: String) = settle { quietly { handler.proceed(username, password) } }

    override fun dismiss() = settle { quietly { handler.cancel() } }
}

/** `beforeunload`: the page may have unsaved changes. */
class BeforeUnloadRequest internal constructor(
    tabId: String,
    private val result: JsResult,
) : WebViewPromptRequest(tabId) {
    fun answer(leave: Boolean) = settle { quietly { if (leave) result.confirm() else result.cancel() } }

    override fun dismiss() = answer(false)
}

/**
 * A pop-up the page opened without a tap, held back. Allowing it runs [open]; ignoring it (or the
 * banner timing out) leaves it blocked.
 */
class PopupRequest internal constructor(
    tabId: String,
    val targetUri: String?,
    private val open: () -> Unit,
) : WebViewPromptRequest(tabId) {
    fun answer(allow: Boolean) = settle { if (allow) open() }

    override fun dismiss() = answer(false)
}
