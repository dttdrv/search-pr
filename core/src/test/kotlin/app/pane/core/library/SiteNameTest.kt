package app.pane.core.library

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteNameTest {
    @Test fun registrableLabel() {
        assertEquals("wikipedia", LetterTiles.siteName("en.m.wikipedia.org"))
        assertEquals("github", LetterTiles.siteName("github.com"))
        assertEquals("bbc", LetterTiles.siteName("www.bbc.co.uk"))
        assertEquals("localhost", LetterTiles.siteName("localhost"))
    }

    @Test fun letterUsesSiteName() {
        assertEquals("W", LetterTiles.letter("https://en.wikipedia.org/wiki/X"))
    }
}
