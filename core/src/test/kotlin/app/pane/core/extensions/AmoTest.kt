package app.pane.core.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmoTest {
    private val sample = """
    {"count": 2, "next": "https://addons.mozilla.org/api/v5/addons/search/?page=2", "results": [
      {"guid": "uBlock0@raymondhill.net", "slug": "ublock-origin",
       "name": {"en-US": "uBlock Origin"}, "summary": {"en-US": "Finally, an efficient blocker."},
       "icon_url": "https://addons.mozilla.org/user-media/addon_icons/607/607454-64.png",
       "authors": [{"name": "Raymond Hill"}], "average_daily_users": 9000000,
       "ratings": {"average": 4.78, "count": 18000},
       "current_version": {"file": {"url": "https://addons.mozilla.org/firefox/downloads/file/1/ublock.xpi"}},
       "url": "https://addons.mozilla.org/en-US/android/addon/ublock-origin/",
       "promoted": [{"category": "recommended", "apps": ["android"]}]},
      {"guid": "plain@example", "slug": "plain", "name": "Plain name", "summary": null, "promoted": null}
    ]}
    """.trimIndent()

    @Test fun parsesSearchPage() {
        val page = Amo.parsePage(sample)
        assertEquals(2, page.addons.size)
        val ubo = page.addons[0]
        assertEquals("uBlock Origin", ubo.name)
        assertEquals(listOf("Raymond Hill"), ubo.authors)
        assertEquals(9_000_000, ubo.users)
        assertTrue(ubo.recommended)
        assertEquals("https://addons.mozilla.org/firefox/downloads/file/1/ublock.xpi", ubo.xpiUrl)
        val plain = page.addons[1]
        assertEquals("Plain name", plain.name)
        assertEquals("", plain.summary)
        assertEquals(false, plain.recommended)
        assertTrue(page.next!!.contains("page=2"))
    }

    @Test fun buildsUrls() {
        assertTrue(Amo.searchUrl("dark mode").contains("q=dark+mode"))
        assertTrue(Amo.searchUrl(null).contains("promoted=recommended"))
        assertEquals("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi", Amo.latestXpiUrl("ublock-origin"))
    }
}
