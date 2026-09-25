package app.pane.core.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlDisplayTest {
    @Test fun showsRegistrableHostOnly() {
        assertEquals("example.com", UrlDisplay.toolbarText("https://www.example.com/path?q=1"))
        assertEquals("en.m.wikipedia.org", UrlDisplay.toolbarText("https://en.m.wikipedia.org/wiki/X"))
        assertEquals("example.com", UrlDisplay.toolbarText("https://m.example.com/"))
        assertEquals("", UrlDisplay.toolbarText("about:blank"))
    }

    @Test fun doesNotStripWwwFromTwoLabelHosts() {
        assertEquals("www.io", UrlDisplay.toolbarText("https://www.io/"))
    }

    @Test fun decodesPunycodeForSingleScriptHosts() {
        assertEquals("münchen.de", UrlDisplay.toolbarText("https://xn--mnchen-3ya.de/"))
    }

    @Test fun keepsPunycodeForMixedScriptHomographs() {
        // "аpple.com" with a Cyrillic "а".
        val spoof = java.net.IDN.toASCII("аpple.com")
        assertEquals(spoof, UrlDisplay.toolbarText("https://$spoof/"))
    }

    @Test fun security() {
        assertTrue(UrlDisplay.isSecure("https://a.b"))
        assertFalse(UrlDisplay.isSecure("http://a.b"))
    }
}
