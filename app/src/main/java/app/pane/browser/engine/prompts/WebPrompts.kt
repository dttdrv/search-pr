package app.pane.browser.engine.prompts

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import app.pane.browser.engine.PromptRequest
import app.pane.core.prompts.DateTimeKind
import app.pane.core.prompts.PermissionText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AuthPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BeforeUnloadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ColorPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PopupPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RedirectPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RepostConfirmPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.SharePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A page prompt waiting in the queue: the Gecko [prompt] and the [result] Gecko is blocked on.
 *
 * The result is completed exactly once, by whoever gets there first: the user, the page (which
 * can withdraw a prompt, e.g. by navigating away) or the queue ([dismiss] when the tab closes).
 */
abstract class WebPromptRequest<P : BasePrompt>(
    final override val tabId: String,
    prompt: P,
) : PromptRequest {
    final override val id: Long = PromptRequest.nextId()

    val result: GeckoResult<PromptResponse> = GeckoResult()

    private val current = MutableStateFlow(prompt)
    private val settled = AtomicBoolean(false)

    /** The latest version of the prompt; a `<select>` can change while its sheet is open. */
    val prompt: P get() = current.value
    val updates: StateFlow<P> = current.asStateFlow()

    /** True once answered or withdrawn; later answers are ignored. */
    val isSettled: Boolean get() = settled.get()

    /** Runs once the prompt has been answered or withdrawn. */
    internal var onSettled: (() -> Unit)? = null

    override fun dismiss() = respond { it.dismiss() }

    protected fun respond(answer: (P) -> PromptResponse) {
        if (!settled.compareAndSet(false, true)) return
        val p = current.value
        // A prompt the page already withdrew can't be answered, and some confirm() overloads
        // reject unexpected input; fall back to a neutral answer where one is still possible.
        val response = runCatching { answer(p) }.recoverCatching { p.dismiss() }.getOrNull()
        if (response != null) result.complete(response)
        onSettled?.invoke()
    }

    /** The page took the prompt down itself; Gecko no longer expects an answer. */
    internal fun withdraw() {
        if (!settled.compareAndSet(false, true)) return
        onSettled?.invoke()
    }

    internal fun update(prompt: BasePrompt) {
        val type = current.value.javaClass
        if (type.isInstance(prompt)) current.value = type.cast(prompt)
    }
}

/**
 * `alert()`, `confirm()` and `prompt()`. [source] is who is asking: the host of the frame that
 * called, which may be an embedded third party rather than the tab's site.
 */
abstract class ScriptDialogRequest<P : BasePrompt>(
    tabId: String,
    prompt: P,
    val source: String?,
    /** The page keeps opening dialogs; offer "Don't allow more dialogs from this page". */
    val offerOptOut: Boolean,
    private val onOptOut: () -> Unit,
) : WebPromptRequest<P>(tabId, prompt) {
    abstract val message: String

    /** Silences the page's further dialogs until the tab moves on; answer this one as usual. */
    fun blockFurtherDialogs() = onOptOut()
}

class AlertRequest internal constructor(
    tabId: String,
    prompt: AlertPrompt,
    source: String?,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest<AlertPrompt>(tabId, prompt, source, offerOptOut, onOptOut) {
    override val message: String get() = prompt.message.orEmpty()

    fun ok() = dismiss()
}

class ConfirmRequest internal constructor(
    tabId: String,
    prompt: ButtonPrompt,
    source: String?,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest<ButtonPrompt>(tabId, prompt, source, offerOptOut, onOptOut) {
    override val message: String get() = prompt.message.orEmpty()

    fun answer(ok: Boolean) = respond { it.confirm(if (ok) ButtonPrompt.Type.POSITIVE else ButtonPrompt.Type.NEGATIVE) }
}

class TextInputRequest internal constructor(
    tabId: String,
    prompt: TextPrompt,
    source: String?,
    offerOptOut: Boolean,
    onOptOut: () -> Unit,
) : ScriptDialogRequest<TextPrompt>(tabId, prompt, source, offerOptOut, onOptOut) {
    override val message: String get() = prompt.message.orEmpty()
    val defaultValue: String get() = prompt.defaultValue.orEmpty()

    fun submit(text: String) = respond { it.confirm(text) }
}

/** HTTP authentication. Nothing typed here is stored. */
class AuthRequest internal constructor(tabId: String, prompt: AuthPrompt) : WebPromptRequest<AuthPrompt>(tabId, prompt) {
    private val options: AuthPrompt.AuthOptions get() = prompt.authOptions
    private fun flag(flag: Int) = (options.flags and flag) != 0

    val host: String get() = options.uri?.let(PermissionText::displayHost).orEmpty()
    val onlyPassword: Boolean get() = flag(AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD)
    val isProxy: Boolean get() = flag(AuthPrompt.AuthOptions.Flags.PROXY)
    val previousFailed: Boolean get() = flag(AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED)
    val crossOrigin: Boolean get() = flag(AuthPrompt.AuthOptions.Flags.CROSS_ORIGIN_SUB_RESOURCE)

    /** The password would travel unencrypted. */
    val insecure: Boolean get() = options.level == AuthPrompt.AuthOptions.Level.NONE
    val defaultUsername: String get() = options.username.orEmpty()

    fun submit(username: String, password: String) =
        respond { if (onlyPassword) it.confirm(password) else it.confirm(username, password) }
}

/** `<select>` (single or multiple) and page-defined menus. */
class ChoiceRequest internal constructor(tabId: String, prompt: ChoicePrompt) : WebPromptRequest<ChoicePrompt>(tabId, prompt) {
    val isMultiple: Boolean get() = prompt.type == ChoicePrompt.Type.MULTIPLE
    val isMenu: Boolean get() = prompt.type == ChoicePrompt.Type.MENU

    /** Answers by id so a choice from a superseded version of the prompt still works. */
    fun select(choiceId: String) = respond { it.confirm(choiceId) }

    fun selectAll(choiceIds: List<String>) = respond { it.confirm(choiceIds.toTypedArray()) }
}

/** `<input type=color>`; values are `#rrggbb`. */
class ColorRequest internal constructor(tabId: String, prompt: ColorPrompt) : WebPromptRequest<ColorPrompt>(tabId, prompt) {
    val defaultValue: String? get() = prompt.defaultValue

    /** Colours from the input's `<datalist>`. */
    val suggestions: List<String> get() = prompt.predefinedValues?.filterNotNull().orEmpty()

    fun pick(hex: String) = respond { it.confirm(hex) }
}

/** `<input type=date|month|week|time|datetime-local>`. */
class DateTimeRequest internal constructor(tabId: String, prompt: DateTimePrompt) : WebPromptRequest<DateTimePrompt>(tabId, prompt) {
    val kind: DateTimeKind
        get() = when (prompt.type) {
            DateTimePrompt.Type.MONTH -> DateTimeKind.Month
            DateTimePrompt.Type.WEEK -> DateTimeKind.Week
            DateTimePrompt.Type.TIME -> DateTimeKind.Time
            DateTimePrompt.Type.DATETIME_LOCAL -> DateTimeKind.DateTimeLocal
            else -> DateTimeKind.Date
        }
    val defaultValue: String? get() = prompt.defaultValue
    val minValue: String? get() = prompt.minValue
    val maxValue: String? get() = prompt.maxValue

    fun pick(value: String) = respond { it.confirm(value) }

    /** Empties the input. */
    fun clear() = respond { it.confirm("") }
}

/** `<input type=file>`. Answered with files the UI has already copied somewhere Gecko can read. */
class FileRequest internal constructor(tabId: String, prompt: FilePrompt) : WebPromptRequest<FilePrompt>(tabId, prompt) {
    val allowsMultiple: Boolean get() = prompt.type == FilePrompt.Type.MULTIPLE

    /** MIME types for the system picker; `accept` extensions are mapped, unknown ones dropped. */
    val mimeTypes: Array<String>
        get() {
            val types = prompt.mimeTypes?.filterNotNull().orEmpty().mapNotNull { toMime(it) }.distinct()
            return if (types.isEmpty()) arrayOf("*/*") else types.toTypedArray()
        }

    fun pick(context: Context, files: List<Uri>) = respond { p ->
        when {
            files.isEmpty() -> p.dismiss()
            allowsMultiple -> p.confirm(context, files.toTypedArray())
            else -> p.confirm(context, files.first())
        }
    }

    private fun toMime(token: String): String? {
        val t = token.trim().lowercase()
        if (t.isEmpty()) return null
        if ('/' in t) return t
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(t.removePrefix("*").removePrefix("."))
    }
}

/** A pop-up the engine blocked; the user can let it through. */
class PopupRequest internal constructor(tabId: String, prompt: PopupPrompt) : WebPromptRequest<PopupPrompt>(tabId, prompt) {
    val targetUri: String? get() = prompt.targetUri

    fun answer(allow: Boolean) = respond { it.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun dismiss() = answer(false)
}

/**
 * An embedded frame tried to navigate the whole tab without a tap (a "framebusting" redirect,
 * a common ad trick). Blocked unless the user allows it.
 */
class RedirectRequest internal constructor(tabId: String, prompt: RedirectPrompt) : WebPromptRequest<RedirectPrompt>(tabId, prompt) {
    val targetUri: String? get() = prompt.targetUri

    fun answer(allow: Boolean) = respond { it.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun dismiss() = answer(false)
}

/** Reloading a page that was the result of a form POST. */
class RepostRequest internal constructor(tabId: String, prompt: RepostConfirmPrompt) : WebPromptRequest<RepostConfirmPrompt>(tabId, prompt) {
    fun answer(resend: Boolean) = respond { it.confirm(if (resend) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun dismiss() = answer(false)
}

/** `beforeunload`: the page may have unsaved changes. */
class BeforeUnloadRequest internal constructor(tabId: String, prompt: BeforeUnloadPrompt) : WebPromptRequest<BeforeUnloadPrompt>(tabId, prompt) {
    fun answer(leave: Boolean) = respond { it.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }

    override fun dismiss() = answer(false)
}

/** `navigator.share()`, fulfilled with the Android share sheet. */
class ShareRequest internal constructor(tabId: String, prompt: SharePrompt) : WebPromptRequest<SharePrompt>(tabId, prompt) {
    val title: String? get() = prompt.title
    val text: String? get() = prompt.text
    val uri: String? get() = prompt.uri

    fun finish(shared: Boolean) = respond { it.confirm(if (shared) SharePrompt.Result.SUCCESS else SharePrompt.Result.FAILURE) }

    override fun dismiss() = respond { it.confirm(SharePrompt.Result.ABORT) }
}
