package app.pane.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostsTest {
    @Test fun partiesUseThePublicSuffixListIncludingPrivateSuffixes() {
        assertFalse(Hosts.sameSite("a.github.io", "b.github.io"))
        assertTrue(Hosts.sameSite("cdn.a.github.io", "a.github.io"))
        assertFalse(Hosts.sameSite("a.blogspot.com", "b.blogspot.com"))
        assertEquals("example.com.au", Hosts.registrable("cdn.example.com.au"))
        assertEquals("a.b.ck", Hosts.registrable("a.b.ck"))
        assertEquals("www.ck", Hosts.registrable("cdn.www.ck"))
        assertEquals("city.kawasaki.jp", Hosts.registrable("cdn.city.kawasaki.jp"))
    }

    @Test fun namesAndAddressesAreNormalizedWithoutMergingDifferentIps() {
        assertTrue(Hosts.sameSite("CDN.Example.COM.", "example.com"))
        assertFalse(Hosts.sameSite("192.168.0.1", "10.168.0.1"))
        assertEquals("192.168.0.1", Hosts.registrable("192.168.0.1"))
        assertEquals("::1", Hosts.registrable("::1"))
        assertEquals("localhost", Hosts.registrable("localhost"))
    }
}
