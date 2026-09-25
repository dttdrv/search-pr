package app.pane.core.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlInputTest {
    private fun nav(s: String) = assertEquals(InputAction.Navigate::class, UrlInput.classify(s)!!::class, "expected navigate for '$s'")
    private fun search(s: String) = assertEquals(InputAction.Search::class, UrlInput.classify(s)!!::class, "expected search for '$s'")

    @Test fun emptyInputIsIgnored() {
        assertNull(UrlInput.classify("   "))
    }

    @Test fun bareDomainsGetHttps() {
        assertEquals(InputAction.Navigate("https://example.com/"), UrlInput.classify("example.com"))
        assertEquals(InputAction.Navigate("https://news.ycombinator.com/item?id=1"), UrlInput.classify("news.ycombinator.com/item?id=1"))
        assertEquals(InputAction.Navigate("https://example.dev/"), UrlInput.classify("  Example.DEV  "))
    }

    @Test fun explicitSchemesAreKept() {
        assertEquals(InputAction.Navigate("http://neverssl.com/"), UrlInput.classify("http://neverssl.com"))
        assertEquals(InputAction.Navigate("https://example.com/a%20b"), UrlInput.classify("https://example.com/a b"))
        search("https://exa mple.com/")
    }

    @Test fun hostsWithPortsAndIps() {
        nav("localhost")
        nav("localhost:8080")
        nav("localhost:3000/api")
        nav("192.168.1.1")
        nav("10.0.0.2:8443/admin")
        nav("[::1]:8080")
        nav("example.com:8443")
        assertEquals(InputAction.Navigate("https://localhost:8080/"), UrlInput.classify("localhost:8080"))
    }

    @Test fun wordsAndSentencesAreSearches() {
        search("weather")
        search("what is kotlin")
        search("node.js")
        search("index.html")
        search("999.1.1.1")
        search("user@example.com")
        search("1.5")
    }

    @Test fun dangerousSchemesNeverNavigate() {
        search("javascript:alert(1)")
        search("JavaScript:alert(document.cookie)")
        search("data:text/html,<script>alert(1)</script>")
        search("file:///etc/passwd")
        search("content://com.android.contacts/contacts")
    }

    @Test fun externalSchemesAreHandedOff() {
        assertEquals(InputAction.External("mailto:a@b.c"), UrlInput.classify("mailto:a@b.c"))
        assertEquals(InputAction.External("tel:+123"), UrlInput.classify("tel:+123"))
    }

    @Test fun internalPagesNavigate() {
        nav("about:blank")
    }

    @Test fun idnHostsArePunycoded() {
        assertEquals(InputAction.Navigate("https://xn--mnchen-3ya.de/"), UrlInput.classify("münchen.de"))
    }

    @Test fun hostOf() {
        assertEquals("example.com", UrlInput.hostOf("https://user:pw@Example.com:8080/x"))
        assertNull(UrlInput.hostOf("not a url"))
    }
}
