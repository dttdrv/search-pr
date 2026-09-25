package app.pane.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddonMatchingTest {
    private val ubo = InstalledAddonRef("uBlock0@raymondhill.net", "uBlock Origin")
    private val bitwarden = InstalledAddonRef("{446900e4-71c2-419f-a6a7-df9c091e268b}", "Bitwarden Password Manager")

    @Test fun exactGuidWins() {
        assertTrue(AddonMatching.isInstalled("ublock-origin", "uBlock Origin", listOf(ubo), guid = "uBlock0@raymondhill.net"))
        assertFalse(AddonMatching.isInstalled("ublock-origin-lite", "uBlock Origin Lite", listOf(ubo), guid = "uBOLite@raymondhill.net"))
    }

    @Test fun knownIdsAndRecordedInstalls() {
        assertTrue(AddonMatching.isInstalled("bitwarden-password-manager", "Bitwarden", listOf(bitwarden)))
        val custom = InstalledAddonRef("weird@id", "Whatever")
        assertTrue(AddonMatching.isInstalled("some-slug", "Other name", listOf(custom), recorded = mapOf("some-slug" to "weird@id")))
        assertFalse(AddonMatching.isInstalled("some-slug", "Other name", listOf(custom)))
    }

    @Test fun listingUrlAndNameFallbacks() {
        val viaUrl = InstalledAddonRef("x@y", "Renamed", "https://addons.mozilla.org/en-US/android/addon/video-background-play-fix/")
        assertTrue(AddonMatching.isInstalled("video-background-play-fix", "Video Background Play Fix", listOf(viaUrl)))
        val viaName = InstalledAddonRef("a@b", "Video Background Play Fix - keep playing")
        assertTrue(AddonMatching.isInstalled("video-background-play-fix", "Video Background Play Fix", listOf(viaName)))
        // A known id means a similarly named add-on isn't mistaken for it.
        val lite = InstalledAddonRef("uBOLite@raymondhill.net", "uBlock Origin Lite")
        assertFalse(AddonMatching.isInstalled("ublock-origin", "uBlock Origin", listOf(lite)))
    }

    @Test fun slugFromListingUrl() {
        assertEquals("ublock-origin", AddonMatching.slugFromListingUrl("https://addons.mozilla.org/en-US/android/addon/ublock-origin/"))
        assertEquals("darkreader", AddonMatching.slugFromListingUrl("https://addons.mozilla.org/android/addon/darkreader?src=x"))
        assertNull(AddonMatching.slugFromListingUrl("https://example.com/"))
        assertNull(AddonMatching.slugFromListingUrl(null))
    }

    @Test fun batchLookupUrl() {
        val url = AddonMatching.listingsByIdUrl(listOf("a@b", "{c}"))
        assertTrue(url.startsWith("https://addons.mozilla.org/api/v5/addons/search/?guid="))
        assertTrue(url.contains("a%40b%2C%7Bc%7D"))
        assertTrue(url.contains("page_size=2"))
    }
}
