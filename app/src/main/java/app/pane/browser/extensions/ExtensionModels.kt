package app.pane.browser.extensions

import androidx.compose.ui.graphics.ImageBitmap
import app.pane.core.extensions.ExtensionPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * An installed extension as the UI sees it: a plain snapshot of the engine's [WebExtension], so
 * screens never hold engine objects and can compare states cheaply.
 */
data class InstalledExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val creator: String?,
    val creatorUrl: String?,
    val homepageUrl: String?,
    val icon: ImageBitmap?,
    val enabled: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    /** False when the extension declares `incognito: "not_allowed"`, so the toggle is hidden. */
    val canRunInPrivate: Boolean,
    val optionsPageUrl: String?,
    val openOptionsPageInTab: Boolean,
    val isBuiltIn: Boolean,
    val permissions: List<String>,
    val origins: List<String>,
    val grantedOptionalPermissions: List<String>,
    val grantedOptionalOrigins: List<String>,
    val dataCollection: List<String>,
    val amoListingUrl: String?,
    val blocklistState: Int,
    val signedState: Int,
    val disabledFlags: Int,
    /** Why the extension can't run or needs attention (blocked, unsigned, incompatible), if anything. */
    val problem: String?,
) {
    /** Everything it may do, required and granted optional permissions together, in plain language. */
    val permissionDescriptions: List<String>
        get() = ExtensionPermissions.describe(permissions + grantedOptionalPermissions, origins + grantedOptionalOrigins)

    val dataCollectionDescription: String? get() = ExtensionPermissions.dataCollection(dataCollection)

    /** Hard-blocked by Mozilla; it can't be turned on. */
    val isBlocked: Boolean get() = blocklistState == WebExtension.BlocklistStateFlags.BLOCKED
}

/** One-shot things the extensions layer tells the UI about. */
sealed interface ExtensionEvent {
    /** `runtime.openOptionsPage()` was called. */
    data class OpenOptions(val extensionId: String) : ExtensionEvent

    /** The user approved an install and it finished. */
    data class Installed(val extensionId: String, val name: String) : ExtensionEvent

    /** An install or operation failed; [message] is ready to show. */
    data class Failed(val message: String) : ExtensionEvent

    /** Neutral information, e.g. the outcome of an update check. */
    data class Message(val message: String) : ExtensionEvent

    /** An extension or its popup/options page opened a tab and selected it. */
    data class TabOpened(val tabId: String) : ExtensionEvent
}

/**
 * A browser-action popup. Its [session] is opened by the manager and handed to Gecko, which loads
 * the popup page into it; whoever shows it must [close] it once it is no longer displayed.
 */
class ExtensionPopup internal constructor(
    val extensionId: String,
    val title: String,
    val icon: ImageBitmap?,
    val session: GeckoSession,
) {
    private val closed = AtomicBoolean(false)

    /** Closes the engine session. Safe to call more than once. */
    fun close() {
        if (closed.compareAndSet(false, true)) runCatching { session.close() }
    }
}

/**
 * Something the engine is waiting on the user for. Every prompt must eventually be answered or
 * [dismiss]ed; both are idempotent.
 */
sealed class ExtensionPrompt {
    abstract val id: Long
    abstract val extensionId: String
    abstract val name: String

    /** Refuses the request (Cancel, back, swipe away). */
    abstract fun dismiss()

    internal companion object {
        private val ids = AtomicLong()
        fun nextId(): Long = ids.incrementAndGet()
    }
}

/** "Add “uBlock Origin”?" with what it will be able to do. */
class InstallPrompt internal constructor(
    override val id: Long,
    override val extensionId: String,
    override val name: String,
    val creator: String?,
    val version: String?,
    /** Plain-language sentences, most consequential first. */
    val permissions: List<String>,
    val dataCollection: String?,
    val canRunInPrivate: Boolean,
    /** The developer asks, optionally, for technical and interaction data. */
    val offersTechnicalData: Boolean,
    private val pending: PendingResult<WebExtension.PermissionPromptResponse>,
    private val onAnswered: (InstallPrompt, Boolean) -> Unit,
) : ExtensionPrompt() {
    private val _icon = MutableStateFlow<ImageBitmap?>(null)
    val icon: StateFlow<ImageBitmap?> = _icon.asStateFlow()

    internal fun setIcon(bitmap: ImageBitmap?) {
        if (bitmap != null) _icon.value = bitmap
    }

    fun add(allowInPrivate: Boolean, shareTechnicalData: Boolean) {
        val response = WebExtension.PermissionPromptResponse(true, allowInPrivate && canRunInPrivate, shareTechnicalData && offersTechnicalData)
        if (pending.complete(response)) onAnswered(this, true)
    }

    override fun dismiss() {
        if (pending.complete(WebExtension.PermissionPromptResponse(false, false, false))) onAnswered(this, false)
    }
}

/** An installed extension asks for more: after an update, or at runtime (`permissions.request`). */
class PermissionPrompt internal constructor(
    override val id: Long,
    override val extensionId: String,
    override val name: String,
    val kind: Kind,
    val permissions: List<String>,
    private val pending: PendingResult<AllowOrDeny>,
    private val onAnswered: (PermissionPrompt) -> Unit,
) : ExtensionPrompt() {
    enum class Kind { Update, Optional }

    fun allow() {
        if (pending.complete(AllowOrDeny.ALLOW)) onAnswered(this)
    }

    override fun dismiss() {
        if (pending.complete(AllowOrDeny.DENY)) onAnswered(this)
    }
}
