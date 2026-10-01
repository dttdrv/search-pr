package app.pane.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefererTest {
    @Test fun sendsOnlyTheOrigin() {
        assertEquals("https://site.example/", Referer.origin("https://user:pw@site.example/a/b?q=1#f", "https://cdn.example/f.bin"))
        assertEquals("http://localhost:8080/", Referer.origin("http://localhost:8080/page", "http://localhost:8080/file"))
    }

    @Test fun neverDowngrades() {
        assertNull(Referer.origin("https://site.example/a", "http://plain.example/f"))
        assertEquals("http://site.example/", Referer.origin("http://site.example/a", "https://secure.example/f"))
    }

    @Test fun ignoresPagesThatAreNotWebAddresses() {
        assertNull(Referer.origin(null, "https://x/f"))
        assertNull(Referer.origin("about:blank", "https://x/f"))
        assertNull(Referer.origin("data:text/html,hi", "https://x/f"))
        assertNull(Referer.origin("not a url", "https://x/f"))
    }
}
