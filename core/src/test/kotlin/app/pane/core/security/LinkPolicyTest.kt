package app.pane.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPolicyTest {
    @Test fun routing() {
        assertEquals(LinkDecision.LoadInBrowser, LinkPolicy.decide("https://a.b"))
        assertEquals(LinkDecision.AskToOpenExternally, LinkPolicy.decide("mailto:x@y.z"))
        assertEquals(LinkDecision.AskToOpenExternally, LinkPolicy.decide("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(LinkDecision.Block, LinkPolicy.decide("file:///sdcard/secret"))
        assertEquals(LinkDecision.Block, LinkPolicy.decide("content://media/external"))
        assertEquals(LinkDecision.Block, LinkPolicy.decide("no-scheme"))
    }

    @Test fun intentFallbackMustBeWeb() {
        val ok = IntentUri.parse("intent://x#Intent;scheme=foo;package=com.foo;S.browser_fallback_url=https%3A%2F%2Ffoo.com%2F;end")!!
        assertEquals("https://foo.com/", ok.fallbackUrl)
        assertEquals("com.foo", ok.packageName)
        val evil = IntentUri.parse("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end")!!
        assertNull(evil.fallbackUrl)
        assertNull(IntentUri.parse("https://x"))
    }
}
