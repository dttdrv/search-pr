package app.pane.core.prompts

import app.pane.core.url.UrlDisplay

/**
 * The site permissions Pane asks about and lists in Site Settings: what the system WebView can ask
 * for that the user can meaningfully answer. Kept free of engine constants so the wording can be
 * tested and reused by any screen.
 */
enum class SitePermission(val label: String) {
    Location("Location"),
    Camera("Camera"),
    Microphone("Microphone"),
    ProtectedContent("Protected content"),
}

/** User-facing wording for permission prompts, in the direct style of iOS privacy alerts. */
object PermissionText {
    /**
     * The host people should judge the request by: Unicode unless that would enable a homograph
     * spoof, with `www.` dropped. Falls back to the raw string for non-URLs.
     */
    fun displayHost(url: String): String = UrlDisplay.toolbarText(url).ifEmpty { url }

    /** Sheet title, e.g. "example.com wants to use your location". */
    fun requestTitle(permission: SitePermission, host: String): String = when (permission) {
        SitePermission.Location -> "$host wants to use your location"
        SitePermission.ProtectedContent -> "$host wants to play protected content"
        SitePermission.Camera -> "$host wants to use your camera"
        SitePermission.Microphone -> "$host wants to use your microphone"
    }

    /** One line under the title saying what allowing actually means. */
    fun explanation(permission: SitePermission): String = when (permission) {
        SitePermission.Location -> "The site will be able to see where you are while it’s open."
        SitePermission.ProtectedContent -> "Some streaming services need this to play video. It uses DRM software provided by your device."
        SitePermission.Camera, SitePermission.Microphone -> "The site can use it until you leave the page."
    }

    /** Title for a camera/microphone request. */
    fun mediaTitle(host: String, camera: Boolean, microphone: Boolean): String = when {
        camera && microphone -> "$host wants to use your camera and microphone"
        camera -> "$host wants to use your camera"
        else -> "$host wants to use your microphone"
    }

    /** Row label in Site Settings. */
    fun settingLabel(permission: SitePermission): String = permission.label
}
