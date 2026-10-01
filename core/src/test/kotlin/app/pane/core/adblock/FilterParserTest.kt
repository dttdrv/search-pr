package app.pane.core.adblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterParserTest {
    private fun net(line: String): NetworkFilter = assertIs<NetworkFilter>(assertNotNull(FilterParser.parseOne(line), line))
    private fun cos(line: String): CosmeticFilter = assertIs<CosmeticFilter>(assertNotNull(FilterParser.parseOne(line), line))
    private fun ignored(line: String) = assertNull(FilterParser.parseOne(line), "should be ignored: $line")

    @Test fun commentsAndHeadersAreIgnored() {
        ignored("! Title: EasyList")
        ignored("[Adblock Plus 2.0]")
        ignored("[uBlock Origin 1.0]")
        ignored("# 127.0.0.1 localhost")
        ignored("   ")
        ignored("")
        ignored("#### Section ####")
    }

    @Test fun hostAnchorSeparatorAndOptions() {
        val f = net("||ads.example.com^\$third-party,script,domain=a.com|~b.a.com")
        assertEquals("ads.example.com^", f.pattern)
        assertTrue(f.anchorHost && !f.anchorStart && !f.anchorEnd && !f.exception && !f.regex)
        assertEquals(1, f.party)
        assertEquals(ResourceType.SCRIPT, f.types)
        assertEquals(listOf("a.com"), f.includeDomains)
        assertEquals(listOf("b.a.com"), f.excludeDomains)
    }

    @Test fun anchorsAndExceptions() {
        val f = net("@@|https://cdn.example.com/lib.js|")
        assertTrue(f.exception && f.anchorStart && f.anchorEnd && !f.anchorHost)
        assertEquals("https://cdn.example.com/lib.js", f.pattern)
    }

    @Test fun typeNegationsAndAliases() {
        assertEquals(ResourceType.ALL_REQUESTS and ResourceType.IMAGE.inv(), net("/x.\$~image").types)
        assertEquals(ResourceType.XHR or ResourceType.SUBDOCUMENT, net("||x.com^\$xhr,frame").types)
        assertEquals(ResourceType.STYLESHEET, net("||x.com^\$css").types)
        assertEquals(2, net("||x.com^\$1p").party.let { if (it == 2) 2 else -1 })
        assertEquals(1, net("||x.com^\$3p").party)
        assertEquals(2, net("||x.com^\$~third-party").party)
    }

    @Test fun flagsImportantBadfilterMatchCase() {
        val f = net("||x.com^\$important,match-case,badfilter")
        assertTrue(f.important && f.matchCase && f.badfilter)
    }

    @Test fun regexRules() {
        val f = net("/banner\\d+\\.gif/\$image")
        assertTrue(f.regex)
        assertEquals("banner\\d+\\.gif", f.pattern)
        // A `$` inside a regex that has no options is part of the regex.
        assertEquals("^https?://x\\.com/ad\$", net("/^https?://x\\.com/ad\$/").pattern)
    }

    @Test fun wildcardsAtTheEndsAreDropped() {
        assertEquals("ad", net("*ad*").pattern)
        assertEquals("a*b", net("a***b").pattern)
    }

    @Test fun unsupportedOptionsDropTheRule() {
        ignored("||x.com^\$csp=script-src 'none'")
        ignored("||x.com^\$replace=/a/b/")
        ignored("||x.com^\$header=via:1.1 google")
        ignored("||x.com^\$popup")
        ignored("||x.com^\$to=y.com")
        ignored("||x.com^\$document")
        ignored("||x.com^\$domain=/reg/")
    }

    @Test fun noopRedirectsAreJustBlocks() {
        assertTrue(net("||x.com^\$script,redirect=noop.js").types == ResourceType.SCRIPT)
        net("||x.com^\$empty")
        net("||x.com^\$mp4")
    }

    @Test fun listDrivenScriptletsAndModifiersAreParsed() {
        for (line in listOf(
            "example.com##+js(set, adblock, false)",
            "example.com##+js(json-prune, playerAds adPlacements)",
            "example.com#@#+js()",
            "||example.com^\$script,redirect-rule=noop.js",
            "||example.com^\$script,redirect=googletagservices_gpt.js",
            "||example.com^\$removeparam=utm_source",
            "||example.com^\$method=POST|~GET",
        )) assertNotNull(FilterParser.parseOne(line), line)
    }

    @Test fun pageLevelExceptions() {
        val f = net("@@||example.com^\$document")
        assertEquals(ResourceType.DOCUMENT, f.types)
        assertEquals(ResourceType.GENERICHIDE, net("@@||example.com^\$ghide").types)
        assertEquals(ResourceType.ELEMHIDE, net("@@||example.com^\$elemhide").types)
    }

    @Test fun denyAllow() {
        val f = net("\$image,3p,denyallow=a.com|b.net,from=x.org")
        assertEquals("", f.pattern)
        assertEquals(listOf("a.com", "b.net"), f.denyAllow)
        assertEquals(listOf("x.org"), f.includeDomains)
        // Nothing narrows it: it would block everything.
        ignored("*")
        ignored("*\$")
    }

    @Test fun hostsLinesAndBareDomains() {
        val out = ArrayList<ParsedFilter>()
        FilterParser.parse("0.0.0.0 ads.one.com ads.two.net # note") { out.add(it) }
        assertEquals(listOf("ads.one.com^", "ads.two.net^"), out.map { (it as NetworkFilter).pattern })
        assertTrue(out.all { (it as NetworkFilter).anchorHost })
        ignored("127.0.0.1 localhost")
        ignored("::1 ip6-localhost")
        assertEquals("tracker.example.org^", net("Tracker.Example.org").pattern)
        // Not a domain: a file name or a path stays an ordinary pattern.
        assertEquals("banner.js", net("banner.js").pattern)
    }

    @Test fun cosmeticRules() {
        val g = cos("##.ad-banner")
        assertEquals(".ad-banner", g.selector)
        assertTrue(g.includeDomains.isEmpty() && !g.exception)
        val d = cos("a.com,~b.a.com,c.org##div[id^=\"ad\"]")
        assertEquals(listOf("a.com", "c.org"), d.includeDomains)
        assertEquals(listOf("b.a.com"), d.excludeDomains)
        assertTrue(cos("a.com#@#.ad-banner").exception)
        // A comma list is split into rules of its own.
        val list = ArrayList<ParsedFilter>()
        FilterParser.parse("##.a, .b:not(.c, .d), [x=\"1,2\"]") { list.add(it) }
        assertEquals(listOf(".a", ".b:not(.c, .d)", "[x=\"1,2\"]"), list.map { (it as CosmeticFilter).selector })
    }

    @Test fun proceduralRulesAreIgnored() {
        ignored("a.com##.x:has-text(Sponsored)")
        ignored("a.com##div:xpath(//a)")
        ignored("a.com##.x:matches-css(display: block)")
        ignored("a.com##.x:upward(2)")
        ignored("a.com##.x:style(display:none)")
        ignored("a.com##^script:has-text(x)")
        ignored("a.com#?#.x:-abp-has(.y)")
        ignored("a.com#\$#.x { display: none }")
        ignored("##.x:remove()")
        // Native CSS is fine.
        cos("a.com##.x:has(> .y)")
        cos("a.com##.x:not([data-a=\"b\"]):nth-child(2)")
        assertFalse(FilterParser.isNativeSelector("a{color:red}"))
        assertFalse(FilterParser.isNativeSelector("@import url(x)"))
        assertFalse(FilterParser.isNativeSelector("a[href"))
    }
}
