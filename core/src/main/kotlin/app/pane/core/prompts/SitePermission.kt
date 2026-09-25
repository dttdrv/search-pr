package app.pane.core.prompts

import app.pane.core.url.UrlDisplay

/**
 * The site permissions Pane asks about and lists in Site Settings. Kept free of engine constants
 * so the wording can be tested and reused by any screen.
 */
enum class SitePermission(val label: String) {
    Location("Location"),
    Notifications("Notifications"),
    PersistentStorage("Persistent Storage"),
    VirtualReality("Virtual Reality"),
    Autoplay("Autoplay"),
    AutoplayMuted("Muted Autoplay"),
    ProtectedContent("Protected Content"),
    Trackers("Allow Trackers"),
    StorageAccess("Cross-Site Cookies"),
    LocalDevices("Apps on This Device"),
    LocalNetwork("Local Network"),
    Camera("Camera"),
    Microphone("Microphone"),
}

/** User-facing wording for permission prompts, in the direct style of iOS privacy alerts. */
object PermissionText {
    /**
     * The host people should judge the request by: Unicode unless that would enable a homograph
     * spoof, with `www.` dropped. Falls back to the raw string for non-URLs.
     */
    fun displayHost(url: String): String = UrlDisplay.toolbarText(url).ifEmpty { url }

    /** Sheet title, e.g. "example.com wants to use your location". */
    fun requestTitle(permission: SitePermission, host: String, thirdPartyOrigin: String? = null): String = when (permission) {
        SitePermission.Location -> "$host wants to use your location"
        SitePermission.Notifications -> "$host wants to send you notifications"
        SitePermission.PersistentStorage -> "$host wants to store data on this device"
        SitePermission.VirtualReality -> "$host wants to use virtual reality devices"
        SitePermission.Autoplay -> "$host wants to play media with sound"
        SitePermission.AutoplayMuted -> "$host wants to play media"
        SitePermission.ProtectedContent -> "$host wants to play protected content"
        SitePermission.Trackers -> "$host wants to load trackers"
        SitePermission.StorageAccess -> {
            val embedded = thirdPartyOrigin?.let(::displayHost)?.takeIf { it.isNotBlank() && it != host }
            if (embedded != null) "$embedded wants to use its cookies on $host" else "$host wants to use cookies across sites"
        }
        SitePermission.LocalDevices -> "$host wants to connect to apps on this device"
        SitePermission.LocalNetwork -> "$host wants to connect to devices on your local network"
        SitePermission.Camera -> "$host wants to use your camera"
        SitePermission.Microphone -> "$host wants to use your microphone"
    }

    /** One line under the title saying what allowing actually means. */
    fun explanation(permission: SitePermission): String = when (permission) {
        SitePermission.Location -> "The site will be able to see where you are while it’s open."
        SitePermission.Notifications -> "The site will be able to show you alerts."
        SitePermission.PersistentStorage -> "Data the site saves won’t be cleared automatically when storage runs low."
        SitePermission.VirtualReality -> "The site will be able to use headsets and motion sensors."
        SitePermission.Autoplay, SitePermission.AutoplayMuted -> "Media on this site will start playing without a tap."
        SitePermission.ProtectedContent -> "Some streaming services need this to play video. It uses DRM software provided by your device."
        SitePermission.Trackers -> "Tracking protection will be turned off for this site."
        SitePermission.StorageAccess -> "This lets the embedded site recognize you here. Block it if it’s not clear why it needs to."
        SitePermission.LocalDevices -> "Only allow this if you trust the site. It could reach apps and services running on this device."
        SitePermission.LocalNetwork -> "Only allow this if you trust the site. It could reach printers, routers and other devices near you."
        SitePermission.Camera, SitePermission.Microphone -> "The site can use it until you leave the page."
    }

    /** Title for a camera/microphone request. */
    fun mediaTitle(host: String, camera: Boolean, microphone: Boolean, screen: Boolean = false): String = when {
        screen && microphone -> "$host wants to share your screen and use your microphone"
        screen -> "$host wants to share your screen"
        camera && microphone -> "$host wants to use your camera and microphone"
        camera -> "$host wants to use your camera"
        else -> "$host wants to use your microphone"
    }

    /** Row label in Site Settings; cookie grants name the embedded site they belong to. */
    fun settingLabel(permission: SitePermission, thirdPartyOrigin: String? = null): String {
        if (permission == SitePermission.StorageAccess) {
            val embedded = thirdPartyOrigin?.let(::displayHost)?.takeIf { it.isNotBlank() }
            if (embedded != null) return "Cookies for $embedded"
        }
        return permission.label
    }

    /**
     * Friendly device names. Gecko describes cameras as e.g. "Camera 1, Facing front, Orientation
     * 270"; people think in "Front Camera" and "Back Camera".
     */
    fun cameraName(raw: String?, index: Int): String {
        val name = raw?.trim().orEmpty()
        val lower = name.lowercase()
        return when {
            "facing front" in lower || lower.startsWith("front") -> "Front Camera"
            "facing back" in lower || "facing rear" in lower || lower.startsWith("back") || lower.startsWith("rear") -> "Back Camera"
            name.isNotEmpty() -> name
            else -> "Camera ${index + 1}"
        }
    }

    fun microphoneName(raw: String?, index: Int): String = raw?.trim()?.takeIf { it.isNotEmpty() } ?: "Microphone ${index + 1}"
}
