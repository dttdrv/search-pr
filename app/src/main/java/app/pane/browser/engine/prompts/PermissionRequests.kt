package app.pane.browser.engine.prompts

import androidx.annotation.OptIn
import app.pane.browser.engine.PromptRequest
import app.pane.core.prompts.PermissionText
import app.pane.core.prompts.SitePermission
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import java.util.concurrent.atomic.AtomicBoolean

/** Gecko's permission constants in Pane's terms. */
object SitePermissions {
    fun fromGecko(permission: Int): SitePermission? = when (permission) {
        PermissionDelegate.PERMISSION_GEOLOCATION -> SitePermission.Location
        PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> SitePermission.Notifications
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> SitePermission.PersistentStorage
        PermissionDelegate.PERMISSION_XR -> SitePermission.VirtualReality
        PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> SitePermission.Autoplay
        PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> SitePermission.AutoplayMuted
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> SitePermission.ProtectedContent
        PermissionDelegate.PERMISSION_TRACKING -> SitePermission.Trackers
        PermissionDelegate.PERMISSION_STORAGE_ACCESS -> SitePermission.StorageAccess
        PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> SitePermission.LocalDevices
        PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> SitePermission.LocalNetwork
        else -> null
    }
}

/**
 * A site asks for a content permission (location, notifications, DRM…). Identical requests that
 * arrive while the sheet is open [follow] this one and get the same answer.
 */
class ContentPermissionRequest internal constructor(
    override val tabId: String,
    val permission: ContentPermission,
    val kind: SitePermission,
    private val result: GeckoResult<Int>,
    private val onDecided: (allowed: Boolean, remember: Boolean) -> Unit,
) : PromptRequest {
    override val id: Long = PromptRequest.nextId()
    private val settled = AtomicBoolean(false)
    private val followers = mutableListOf<GeckoResult<Int>>()

    val host: String get() = PermissionText.displayHost(permission.uri.orEmpty())
    val isPrivate: Boolean get() = permission.privateMode
    val title: String get() = PermissionText.requestTitle(kind, host, permission.thirdPartyOrigin)
    val explanation: String get() = PermissionText.explanation(kind)

    fun allow(remember: Boolean) = decide(allowed = true, remember = remember)

    fun deny(remember: Boolean) = decide(allowed = false, remember = remember)

    override fun dismiss() = decide(allowed = false, remember = false)

    /** Tells Gecko the prompt is on screen, as it expects of embedders that show one. */
    @OptIn(ExperimentalGeckoViewApi::class)
    fun markShown() {
        runCatching { permission.notifyShown() }
    }

    internal fun follow(): GeckoResult<Int> = GeckoResult<Int>().also { followers += it }

    private fun decide(allowed: Boolean, remember: Boolean) {
        if (!settled.compareAndSet(false, true)) return
        val value = if (allowed) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
        result.complete(value)
        followers.forEach { it.complete(value) }
        followers.clear()
        onDecided(allowed, remember)
    }
}

/** Camera and/or microphone for a page; the user picks the device when there are several. */
class MediaPermissionRequest internal constructor(
    override val tabId: String,
    val uri: String,
    val video: List<MediaSource>,
    val audio: List<MediaSource>,
    private val callback: MediaCallback,
    private val onDecided: (granted: Boolean) -> Unit,
) : PromptRequest {
    override val id: Long = PromptRequest.nextId()
    private val settled = AtomicBoolean(false)

    val host: String get() = PermissionText.displayHost(uri)
    val wantsScreen: Boolean get() = video.any { it.source == MediaSource.SOURCE_SCREEN }
    val title: String
        get() = PermissionText.mediaTitle(host, camera = video.isNotEmpty() && !wantsScreen, microphone = audio.isNotEmpty(), screen = wantsScreen)

    fun grant(video: MediaSource?, audio: MediaSource?) {
        if (video == null && audio == null) return reject()
        if (!settled.compareAndSet(false, true)) return
        callback.grant(video, audio)
        onDecided(true)
    }

    fun reject() {
        if (!settled.compareAndSet(false, true)) return
        callback.reject()
        onDecided(false)
    }

    override fun dismiss() = reject()
}

/**
 * Gecko needs an Android runtime permission (camera, location…) to fulfil something the user
 * already allowed for the site. The UI asks the system and reports back.
 */
class AndroidPermissionRequest internal constructor(
    override val tabId: String,
    val permissions: List<String>,
    private val callback: PermissionDelegate.Callback,
) : PromptRequest {
    override val id: Long = PromptRequest.nextId()
    private val settled = AtomicBoolean(false)

    fun complete(granted: Boolean) {
        if (!settled.compareAndSet(false, true)) return
        if (granted) callback.grant() else callback.reject()
    }

    override fun dismiss() = complete(false)
}
