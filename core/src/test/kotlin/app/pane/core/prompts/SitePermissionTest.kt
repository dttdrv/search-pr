package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitePermissionTest {
    @Test fun requestTitlesNameTheSite() {
        assertEquals("maps.example wants to use your location", PermissionText.requestTitle(SitePermission.Location, "maps.example"))
        assertEquals("news.example wants to send you notifications", PermissionText.requestTitle(SitePermission.Notifications, "news.example"))
        SitePermission.entries.forEach { permission ->
            val title = PermissionText.requestTitle(permission, "site.example", "https://embed.example")
            assertTrue("site.example" in title, "$permission: $title")
            assertTrue(PermissionText.explanation(permission).isNotBlank())
            assertTrue(permission.label.isNotBlank())
        }
    }

    @Test fun storageAccessNamesTheEmbeddedSite() {
        assertEquals(
            "video.example wants to use its cookies on news.example",
            PermissionText.requestTitle(SitePermission.StorageAccess, "news.example", "https://www.video.example"),
        )
        assertEquals(
            "news.example wants to use cookies across sites",
            PermissionText.requestTitle(SitePermission.StorageAccess, "news.example", null),
        )
        assertEquals("Cookies for video.example", PermissionText.settingLabel(SitePermission.StorageAccess, "https://video.example"))
        assertEquals("Location", PermissionText.settingLabel(SitePermission.Location))
    }

    @Test fun displayHostDropsWwwAndKeepsSpoofsInPunycode() {
        assertEquals("example.com", PermissionText.displayHost("https://www.example.com/path?q=1"))
        // Cyrillic "а" mixed into a Latin label must stay visibly suspicious.
        assertEquals("xn--pple-43d.com", PermissionText.displayHost("https://xn--pple-43d.com/"))
    }

    @Test fun mediaTitles() {
        assertEquals("a.example wants to use your camera and microphone", PermissionText.mediaTitle("a.example", camera = true, microphone = true))
        assertEquals("a.example wants to use your camera", PermissionText.mediaTitle("a.example", camera = true, microphone = false))
        assertEquals("a.example wants to use your microphone", PermissionText.mediaTitle("a.example", camera = false, microphone = true))
        assertEquals("a.example wants to share your screen", PermissionText.mediaTitle("a.example", camera = false, microphone = false, screen = true))
    }

    @Test fun deviceNames() {
        assertEquals("Front Camera", PermissionText.cameraName("Camera 1, Facing front, Orientation 270", 0))
        assertEquals("Back Camera", PermissionText.cameraName("Camera 0, Facing back, Orientation 90", 1))
        assertEquals("USB Webcam", PermissionText.cameraName(" USB Webcam ", 2))
        assertEquals("Camera 3", PermissionText.cameraName(null, 2))
        assertEquals("Microphone 1", PermissionText.microphoneName("", 0))
    }
}
