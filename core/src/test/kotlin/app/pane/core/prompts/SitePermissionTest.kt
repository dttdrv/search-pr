package app.pane.core.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SitePermissionTest {
    @Test fun requestTitlesNameTheSite() {
        assertEquals("maps.example wants to use your location", PermissionText.requestTitle(SitePermission.Location, "maps.example"))
        SitePermission.entries.forEach { permission ->
            val title = PermissionText.requestTitle(permission, "site.example")
            assertTrue("site.example" in title, "$permission: $title")
            assertTrue(PermissionText.explanation(permission).isNotBlank())
            assertTrue(PermissionText.settingLabel(permission).isNotBlank())
        }
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
    }
}
