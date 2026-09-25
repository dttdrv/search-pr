package app.pane.browser.engine.prompts

import android.os.Handler
import android.os.Looper
import app.pane.browser.engine.PromptQueue
import app.pane.core.prompts.DialogThrottle
import app.pane.core.prompts.DialogVerdict
import app.pane.core.prompts.PromptText
import app.pane.core.prompts.TemporaryDecisions
import app.pane.core.url.UrlInput
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AuthPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AutocompleteRequest
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BeforeUnloadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.CertificateRequest
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ColorPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FolderUploadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.IdentityCredential
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PopupPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptInstanceDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RedirectPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RepostConfirmPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.SharePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.WebAuthnRelatedOriginPrompt

/**
 * Bridges Gecko's prompt callbacks (alert/confirm/prompt, auth, `<select>`, pickers, pop-ups,
 * share…) into [PromptQueue] requests rendered by the prompt UI.
 *
 * Every callback either answers immediately or queues a request whose result Gecko waits on. Things
 * Pane deliberately doesn't do yet (password manager, FedCM, client certificates, folder uploads)
 * are declined here explicitly rather than left to engine defaults.
 */
class WebPromptDelegate(
    private val tabId: String,
    private val queue: PromptQueue,
) : GeckoSession.PromptDelegate {
    private val throttle = DialogThrottle()

    private fun pageUrl(): String? = PromptEnvironment.pageUrl(tabId)

    private fun pageKey(): String? = pageUrl()?.let { UrlInput.hostOf(it) ?: it }

    /** Queues [request]; the page may withdraw or update the prompt while it waits. */
    private fun enqueue(request: WebPromptRequest<*>): GeckoResult<PromptResponse> {
        request.prompt.setDelegate(object : PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: BasePrompt) {
                request.withdraw()
                queue.remove(request)
            }

            override fun onPromptUpdate(prompt: BasePrompt) {
                request.update(prompt)
            }
        })
        queue.enqueue(request)
        return request.result
    }

    /** Shared path for alert/confirm/prompt, which a page can fire in a loop. */
    private fun scriptDialog(
        prompt: BasePrompt,
        create: (source: String?, offerOptOut: Boolean) -> ScriptDialogRequest<*>,
    ): GeckoResult<PromptResponse> {
        val verdict = throttle.onDialogOpened(pageKey())
        if (verdict == DialogVerdict.Suppress) return GeckoResult.fromValue(prompt.dismiss())
        val request = create(PromptText.dialogSource(prompt.title, pageUrl()), verdict == DialogVerdict.ShowWithOptOut)
        request.onSettled = { throttle.onDialogClosed() }
        return enqueue(request)
    }

    // region Script dialogs

    override fun onAlertPrompt(session: GeckoSession, prompt: AlertPrompt): GeckoResult<PromptResponse>? =
        scriptDialog(prompt) { source, optOut -> AlertRequest(tabId, prompt, source, optOut) { throttle.block() } }

    override fun onButtonPrompt(session: GeckoSession, prompt: ButtonPrompt): GeckoResult<PromptResponse>? =
        scriptDialog(prompt) { source, optOut -> ConfirmRequest(tabId, prompt, source, optOut) { throttle.block() } }

    override fun onTextPrompt(session: GeckoSession, prompt: TextPrompt): GeckoResult<PromptResponse>? =
        scriptDialog(prompt) { source, optOut -> TextInputRequest(tabId, prompt, source, optOut) { throttle.block() } }

    // endregion

    // region Navigation

    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: BeforeUnloadPrompt): GeckoResult<PromptResponse>? =
        enqueue(BeforeUnloadRequest(tabId, prompt))

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: RepostConfirmPrompt): GeckoResult<PromptResponse>? =
        enqueue(RepostRequest(tabId, prompt))

    override fun onPopupPrompt(session: GeckoSession, prompt: PopupPrompt): GeckoResult<PromptResponse>? {
        if (!PromptEnvironment.settings().blockPopups) return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
        // One banner per tab: a burst of pop-ups is blocked without asking again.
        if (queue.requests.value.any { it is PopupRequest && it.tabId == tabId }) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
        }
        return enqueue(PopupRequest(tabId, prompt))
    }

    override fun onRedirectPrompt(session: GeckoSession, prompt: RedirectPrompt): GeckoResult<PromptResponse>? {
        if (queue.requests.value.any { it is RedirectRequest && it.tabId == tabId }) {
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
        }
        return enqueue(RedirectRequest(tabId, prompt))
    }

    // endregion

    // region Forms

    override fun onAuthPrompt(session: GeckoSession, prompt: AuthPrompt): GeckoResult<PromptResponse>? =
        enqueue(AuthRequest(tabId, prompt))

    override fun onChoicePrompt(session: GeckoSession, prompt: ChoicePrompt): GeckoResult<PromptResponse>? {
        if (prompt.choices.isNullOrEmpty()) return GeckoResult.fromValue(prompt.dismiss())
        return enqueue(ChoiceRequest(tabId, prompt))
    }

    override fun onColorPrompt(session: GeckoSession, prompt: ColorPrompt): GeckoResult<PromptResponse>? =
        enqueue(ColorRequest(tabId, prompt))

    override fun onDateTimePrompt(session: GeckoSession, prompt: DateTimePrompt): GeckoResult<PromptResponse>? =
        enqueue(DateTimeRequest(tabId, prompt))

    override fun onFilePrompt(session: GeckoSession, prompt: FilePrompt): GeckoResult<PromptResponse>? {
        if (prompt.type == FilePrompt.Type.FOLDER) return GeckoResult.fromValue(prompt.dismiss())
        return enqueue(FileRequest(tabId, prompt))
    }

    override fun onFolderUploadPrompt(session: GeckoSession, prompt: FolderUploadPrompt): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    override fun onSharePrompt(session: GeckoSession, prompt: SharePrompt): GeckoResult<PromptResponse>? =
        enqueue(ShareRequest(tabId, prompt))

    // endregion

    // region Not supported yet: declined explicitly

    override fun onLoginSave(session: GeckoSession, request: AutocompleteRequest<Autocomplete.LoginSaveOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onLoginSelect(session: GeckoSession, request: AutocompleteRequest<Autocomplete.LoginSelectOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onAddressSave(session: GeckoSession, request: AutocompleteRequest<Autocomplete.AddressSaveOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onAddressSelect(session: GeckoSession, request: AutocompleteRequest<Autocomplete.AddressSelectOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onCreditCardSave(session: GeckoSession, request: AutocompleteRequest<Autocomplete.CreditCardSaveOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onCreditCardSelect(session: GeckoSession, request: AutocompleteRequest<Autocomplete.CreditCardSelectOption>): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(request.dismiss())

    override fun onSelectIdentityCredentialProvider(
        session: GeckoSession,
        prompt: IdentityCredential.ProviderSelectorPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.dismiss())

    override fun onSelectIdentityCredentialAccount(
        session: GeckoSession,
        prompt: IdentityCredential.AccountSelectorPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.dismiss())

    override fun onShowPrivacyPolicyIdentityCredential(
        session: GeckoSession,
        prompt: IdentityCredential.PrivacyPolicyPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.dismiss())

    override fun onRequestCertificate(session: GeckoSession, prompt: CertificateRequest): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.dismiss())

    override fun onWebAuthnRelatedOriginPrompt(session: GeckoSession, prompt: WebAuthnRelatedOriginPrompt): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

    // endregion
}

/**
 * Site permission requests: location, camera/microphone, notifications, autoplay, DRM, storage
 * access, local network.
 *
 * With "Remember" on, the answer goes into Gecko's permission store (session-only in private
 * tabs), so the site isn't asked again. Without it, the answer only holds for this tab for an hour
 * and is taken back out of the store, in case Gecko kept it, so the site asks again next time.
 */
class WebPermissionDelegate(
    private val tabId: String,
    private val queue: PromptQueue,
) : GeckoSession.PermissionDelegate {
    private val temporary = TemporaryDecisions<String>()
    private val pending = HashMap<String, ContentPermissionRequest>()
    private val main = Handler(Looper.getMainLooper())

    override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int>? {
        when (perm.permission) {
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return GeckoResult.fromValue(valueOf(!PromptEnvironment.settings().autoplayBlocked))
            // Tracking exceptions are managed from Site Settings; pages don't get to ask.
            PermissionDelegate.PERMISSION_TRACKING -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        }
        val kind = SitePermissions.fromGecko(perm.permission) ?: return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        val key = keyOf(perm)
        temporary.lookup(key)?.let { allowed -> return GeckoResult.fromValue(valueOf(allowed)) }
        pending[key]?.let { return it.follow() }

        val result = GeckoResult<Int>()
        val request = ContentPermissionRequest(tabId, perm, kind, result) { allowed, remember ->
            pending.remove(key)
            onDecided(perm, key, allowed, remember)
        }
        pending[key] = request
        queue.enqueue(request)
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out MediaSource>?,
        audio: Array<out MediaSource>?,
        callback: MediaCallback,
    ) {
        val cameras = video?.filterNotNull().orEmpty()
        val microphones = audio?.filterNotNull().orEmpty()
        if (cameras.isEmpty() && microphones.isEmpty()) {
            callback.reject()
            return
        }
        // Declining holds for a while so a page can't re-ask in a loop; allowing always asks.
        val key = "media|${UrlInput.hostOf(uri) ?: uri}|${cameras.isNotEmpty()}|${microphones.isNotEmpty()}"
        if (temporary.lookup(key) == false) {
            callback.reject()
            return
        }
        queue.enqueue(
            MediaPermissionRequest(tabId, uri, cameras, microphones, callback) { granted ->
                if (!granted) temporary.record(key, false)
            },
        )
    }

    override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback) {
        val needed = permissions?.filterNotNull().orEmpty()
        if (needed.isEmpty()) {
            callback.grant()
            return
        }
        queue.enqueue(AndroidPermissionRequest(tabId, needed, callback))
    }

    private fun onDecided(perm: ContentPermission, key: String, allowed: Boolean, remember: Boolean) {
        val storage = PromptEnvironment.runtime?.storageController
        if (remember) {
            temporary.forget(key)
            storage?.setPermission(perm, if (allowed) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
        } else {
            temporary.record(key, allowed)
            if (storage != null) {
                // After Gecko has applied the answer, so a copy it may have stored is replaced.
                main.postDelayed({ runCatching { storage.setPermission(perm, ContentPermission.VALUE_PROMPT) } }, RESET_DELAY_MS)
            }
        }
    }

    private fun keyOf(perm: ContentPermission): String {
        val site = perm.uri?.let { UrlInput.hostOf(it) ?: it }
        return "${perm.permission}|$site|${perm.thirdPartyOrigin}|${perm.privateMode}"
    }

    private fun valueOf(allowed: Boolean): Int = if (allowed) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY

    private companion object {
        const val RESET_DELAY_MS = 1_000L
    }
}
