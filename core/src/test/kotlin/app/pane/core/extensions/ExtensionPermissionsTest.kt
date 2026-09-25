package app.pane.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtensionPermissionsTest {

    @Test fun allUrlsCollapsesToAllWebsites() {
        val text = ExtensionPermissions.describe(listOf("storage", "tabs"), listOf("<all_urls>", "https://example.com/*"))
        assertEquals("Access your data for all websites", text.first())
        assertEquals(1, text.count { it.startsWith("Access your data") })
    }

    @Test fun wildcardHostPatternsMeanAllWebsites() {
        for (pattern in listOf("*://*/*", "https://*/*", "http://*/")) {
            assertEquals(listOf("Access your data for all websites"), ExtensionPermissions.hostAccess(listOf(pattern)), pattern)
        }
    }

    @Test fun hostPatternsInsidePermissionsAreTreatedAsOrigins() {
        val text = ExtensionPermissions.describe(listOf("<all_urls>", "webRequest"))
        assertEquals(listOf("Access your data for all websites", "Monitor network requests on sites it can access"), text)
    }

    @Test fun apiPermissionsAreOrderedBySensitivityAndDeduplicated() {
        val text = ExtensionPermissions.describe(listOf("storage", "history", "nativeMessaging", "topSites", "downloads", "cookies"))
        assertEquals(
            listOf(
                "Exchange messages with apps other than Pane",
                "Access browsing history",
                "Download files and read and modify the browser’s download history",
                "Read and change cookies on sites it can access",
                "Store data on your device",
            ),
            text,
        )
    }

    @Test fun domainsAndSitesAreNamedThenSummarised() {
        val origins = listOf(
            "*://*.youtube.com/*",
            "https://www.youtube.com/*",
            "https://a.com/*",
            "https://b.com/*",
            "https://c.com/*",
            "https://d.com/*",
            "https://e.com/*",
        )
        val text = ExtensionPermissions.hostAccess(origins)
        assertEquals(
            listOf(
                "Access your data for sites in the youtube.com domain",
                "Access your data for a.com",
                "Access your data for b.com",
                "Access your data for c.com",
                "Access your data on 2 other sites",
            ),
            text,
        )
    }

    @Test fun ignoresNonWebPatternsAndInternalPermissions() {
        assertEquals(emptyList(), ExtensionPermissions.hostAccess(listOf("file:///*", "moz-extension://abc/*")))
        val text = ExtensionPermissions.describe(listOf("geckoViewAddons", "nativeMessagingFromContent"))
        assertEquals(emptyList(), text)
        assertEquals(listOf("frobnicate"), ExtensionPermissions.unlisted(listOf("storage", "frobnicate", "geckoViewAddons", "<all_urls>", "internal:x")))
    }

    @Test fun dataCollection() {
        assertNull(ExtensionPermissions.dataCollection(emptyList()))
        assertEquals("The developer says this extension doesn’t collect data.", ExtensionPermissions.dataCollection(listOf("none")))
        assertEquals(
            "The developer says this extension collects: location, browsing activity and search terms.",
            ExtensionPermissions.dataCollection(listOf("searchTerms", "browsingActivity", "locationInfo")),
        )
        assertNull(ExtensionPermissions.dataCollection(listOf("somethingNew")))
    }

    @Test fun naturalJoin() {
        assertEquals("", ExtensionPermissions.joinNatural(emptyList()))
        assertEquals("a", ExtensionPermissions.joinNatural(listOf("a")))
        assertEquals("a and b", ExtensionPermissions.joinNatural(listOf("a", "b")))
        assertEquals("a, b and c", ExtensionPermissions.joinNatural(listOf("a", "b", "c")))
    }

    @Test fun hostOfHandlesPortsAndUserInfo() {
        assertEquals("example.com", ExtensionPermissions.hostOf("https://user@example.com:8080/path"))
        assertEquals("*.example.com", ExtensionPermissions.hostOf("*://*.Example.com/*"))
        assertNull(ExtensionPermissions.hostOf("chrome://settings/*"))
        assertTrue(ExtensionPermissions.isHostPattern("<all_urls>"))
    }
}
