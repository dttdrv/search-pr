package app.pane.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LetterTilesTest {
    @Test fun letterComesFromHostWithoutWww() {
        assertEquals("E", LetterTiles.letter("https://www.example.com/page"))
        assertEquals("W", LetterTiles.letter("https://m.wikipedia.org/"))
        assertEquals("4", LetterTiles.letter("https://4chan.org"))
    }

    @Test fun fallsBackToTitle() {
        assertEquals("N", LetterTiles.letter("about:blank", "  notes"))
        assertNull(LetterTiles.letter("about:blank", "!!"))
    }

    @Test fun colourIsStablePerSite() {
        val a = LetterTiles.colorIndex(LetterTiles.hostKey("https://www.example.com/a"), 8)
        val b = LetterTiles.colorIndex(LetterTiles.hostKey("https://example.com/b"), 8)
        assertEquals(a, b)
        assertTrue(a in 0 until 8)
        assertFailsWith<IllegalArgumentException> { LetterTiles.colorIndex("x", 0) }
    }
}

class UniqueNamesTest {
    @Test fun keepsFreeName() {
        assertEquals("report.pdf", UniqueNames.next("report.pdf") { false })
    }

    @Test fun numbersBeforeExtension() {
        val taken = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", UniqueNames.next("report.pdf") { it in taken })
    }

    @Test fun handlesNamesWithoutExtension() {
        assertEquals("README (1)", UniqueNames.next("README") { it == "README" })
        assertEquals("archive.tar (1).gz", UniqueNames.next("archive.tar.gz") { it == "archive.tar.gz" })
    }
}

class SiteOriginsTest {
    @Test fun normalisesPermissionUris() {
        assertEquals("https://example.com", SiteOrigins.originOf("https://Example.com/"))
        assertEquals("https://example.com", SiteOrigins.originOf("https://example.com:443/path?q"))
        assertEquals("https://example.com:8443", SiteOrigins.originOf("https://example.com:8443/"))
        assertEquals("http://localhost:3000", SiteOrigins.originOf("http://localhost:3000"))
        assertNull(SiteOrigins.originOf("about:config"))
        assertNull(SiteOrigins.originOf("https:///"))
    }

    @Test fun displayAndHost() {
        assertEquals("example.com:8443", SiteOrigins.displayName("https://example.com:8443"))
        assertEquals("example.com", SiteOrigins.hostOf("https://example.com:8443"))
        assertEquals("[::1]", SiteOrigins.hostOf("http://[::1]:8080"))
    }
}
