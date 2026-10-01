package app.pane.core.adblock

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterEngineTest {
    private fun engine(vararg lines: String): FilterEngine = FilterEngine.build(listOf(lines.joinToString("\n")))

    private fun FilterEngine.blocks(
        url: String,
        page: String? = "news.site",
        type: Int = ResourceType.SCRIPT,
        third: Boolean = true,
    ) = shouldBlock(url, page, type, third)

    @Test fun hostAnchorWalksParentDomains() {
        val e = engine("||ads.example.com^")
        assertTrue(e.blocks("https://ads.example.com/x.js"))
        assertTrue(e.blocks("https://sub.ads.example.com/"))
        assertTrue(e.blocks("https://ads.example.com"))
        assertTrue(e.blocks("https://ads.example.com:8443/x"))
        assertTrue(e.blocks("http://user:pw@ads.example.com/x"))
        assertFalse(e.blocks("https://notads.example.com/"))
        assertFalse(e.blocks("https://example.com/ads.example.com/"))
        assertFalse(e.blocks("https://ads.example.com.evil.net/"))
        assertFalse(e.blocks("https://example.com/"))
    }

    @Test fun separatorWildcardAndAnchors() {
        val e = engine("/banner/*/img^", "|https://track.", ".swf|", "||cdn.test/a*b^")
        assertTrue(e.blocks("https://x.com/banner/728/img?x=1"))
        assertTrue(e.blocks("https://x.com/banner/728/img"))
        assertFalse(e.blocks("https://x.com/banner/728/imgs"))
        assertTrue(e.blocks("https://track.example.com/p"))
        assertFalse(e.blocks("https://a.com/?u=https://track.example.com/"))
        assertTrue(e.blocks("https://x.com/movie.swf"))
        assertFalse(e.blocks("https://x.com/movie.swf?x"))
        assertTrue(e.blocks("https://cdn.test/a-long-b/"))
        assertFalse(e.blocks("https://cdn.test/a-long-bc"))
    }

    @Test fun separatorMeansNotLetterDigitOrDashDotUnderscorePercent() {
        val e = engine("||example.com^")
        assertTrue(e.blocks("https://example.com/"))
        assertTrue(e.blocks("https://example.com?x"))
        assertFalse(e.blocks("https://example.com.au/"))
        assertFalse(e.blocks("https://example.community/"))
        assertFalse(e.blocks("https://example.com-evil.net/"))
    }

    @Test fun exceptionsWinUnlessTheBlockIsImportant() {
        val e = engine(
            "||ads.com^", "@@||ads.com/ok.js^",
            "||hard.com^\$important", "@@||hard.com^",
        )
        assertTrue(e.blocks("https://ads.com/bad.js"))
        assertFalse(e.blocks("https://ads.com/ok.js"))
        assertTrue(e.blocks("https://hard.com/x.js"))
    }

    @Test fun thirdPartyOptions() {
        val e = engine("||t.com^\$third-party", "||f.com^\$~third-party")
        assertTrue(e.blocks("https://t.com/x", third = true))
        assertFalse(e.blocks("https://t.com/x", third = false))
        assertTrue(e.blocks("https://f.com/x", third = false))
        assertFalse(e.blocks("https://f.com/x", third = true))
    }

    @Test fun domainOptionIncludesExcludesAndEntities() {
        val e = engine("||t.com^\$domain=a.com|~b.a.com", "||e.com^\$domain=google.*")
        assertTrue(e.blocks("https://t.com/x", page = "a.com"))
        assertTrue(e.blocks("https://t.com/x", page = "www.a.com"))
        assertFalse(e.blocks("https://t.com/x", page = "b.a.com"))
        assertFalse(e.blocks("https://t.com/x", page = "x.b.a.com"))
        assertFalse(e.blocks("https://t.com/x", page = "other.com"))
        assertFalse(e.blocks("https://t.com/x", page = null))
        assertTrue(e.blocks("https://e.com/x", page = "google.com"))
        assertTrue(e.blocks("https://e.com/x", page = "www.google.co.uk"))
        assertFalse(e.blocks("https://e.com/x", page = "google.evil.org"))
    }

    @Test fun excludeOnlyDomainsApplyEverywhereElse() {
        val e = engine("||t.com^\$domain=~safe.com")
        assertTrue(e.blocks("https://t.com/x", page = "other.com"))
        assertFalse(e.blocks("https://t.com/x", page = "safe.com"))
    }

    @Test fun resourceTypes() {
        val e = engine("||s.com^\$script", "||i.com^\$~image", "||x.com^\$xhr,ping")
        assertTrue(e.blocks("https://s.com/a", type = ResourceType.SCRIPT))
        assertFalse(e.blocks("https://s.com/a", type = ResourceType.IMAGE))
        assertFalse(e.blocks("https://i.com/a", type = ResourceType.IMAGE))
        assertTrue(e.blocks("https://i.com/a", type = ResourceType.STYLESHEET))
        assertTrue(e.blocks("https://x.com/a", type = ResourceType.XHR))
        // Not knowing what it is: any shared bit is enough.
        assertTrue(e.blocks("https://x.com/a", type = ResourceType.UNKNOWN))
        assertTrue(e.blocks("https://s.com/a", type = ResourceType.UNKNOWN))
        assertFalse(e.blocks("https://s.com/a", type = ResourceType.IMAGE or ResourceType.FONT))
        // The main frame is never filtered.
        assertFalse(e.blocks("https://s.com/a", type = ResourceType.DOCUMENT))
    }

    @Test fun badfilterCancelsTheRuleItNames() {
        val e = engine("||a.com^\$third-party", "||b.com^", "||a.com^\$third-party,badfilter")
        assertFalse(e.blocks("https://a.com/x"))
        assertTrue(e.blocks("https://b.com/x"))
        assertEquals(1, e.networkRuleCount)
    }

    @Test fun hostsFilesAndBareDomains() {
        val e = FilterEngine.build(
            listOf(
                "# comment\n127.0.0.1 localhost\n0.0.0.0 ads.one.com ads.two.net\n",
                "tracker.example.org\n",
            ),
        )
        assertTrue(e.blocks("https://ads.one.com/x"))
        assertTrue(e.blocks("https://cdn.ads.two.net/x"))
        assertTrue(e.blocks("https://tracker.example.org/p"))
        assertFalse(e.blocks("https://localhost/"))
        assertEquals(3, e.networkRuleCount)
    }

    @Test fun regexRulesAndStepLimit() {
        val e = engine("/banner\\d+\\.gif/", "/(a+)+\$/\$script")
        assertTrue(e.blocks("https://x.com/img/banner300.gif", type = ResourceType.IMAGE))
        assertFalse(e.blocks("https://x.com/img/banner.gif", type = ResourceType.IMAGE))
        // A pattern that backtracks without end gives up instead of hanging the request thread.
        val start = System.nanoTime()
        e.blocks("https://x.com/" + "a".repeat(1500) + "!", type = ResourceType.SCRIPT)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
    }

    @Test fun matchCase() {
        val e = engine("/AdBanner\$match-case", "/popUnder")
        assertTrue(e.blocks("https://x.com/AdBanner.js"))
        assertFalse(e.blocks("https://x.com/adbanner.js"))
        assertTrue(e.blocks("https://x.com/POPUNDER.js"))
    }

    @Test fun unsupportedRulesAreNotLoose() {
        val e = engine(
            "||x.com^\$csp=script-src 'none'", "||y.com^\$removeparam=a", "||z.com^\$popup",
            "||w.com^\$redirect=googletagservices_gpt.js", "example.com##+js(abort-on-property-read, x)",
            "example.com##div:has-text(Ad)", "||q.com^\$document",
        )
        assertEquals(0, e.networkRuleCount)
        assertFalse(e.blocks("https://x.com/"))
        assertFalse(e.blocks("https://z.com/"))
    }

    @Test fun denyAllowSkipsListedHosts() {
        val e = engine("\$script,3p,denyallow=cdn.com|b.net")
        assertTrue(e.blocks("https://track.org/x.js"))
        assertFalse(e.blocks("https://cdn.com/x.js"))
        assertFalse(e.blocks("https://static.b.net/x.js"))
        assertFalse(e.blocks("https://track.org/x.js", third = false))
        assertFalse(e.blocks("https://track.org/x.png", type = ResourceType.IMAGE))
    }

    @Test fun pageExceptions() {
        val e = engine("||ads.com^", "@@||news.site^\$document", "||t.com^")
        assertFalse(e.blocks("https://ads.com/x", page = "news.site"))
        assertFalse(e.blocks("https://ads.com/x", page = "m.news.site"))
        assertTrue(e.blocks("https://ads.com/x", page = "other.site"))
    }

    @Test fun preprocessorKeepsTheRightBranch() {
        val e = engine(
            "!#if ext_ublock", "||kept.com^", "!#else", "||dropped.com^", "!#endif",
            "!#if ext_ubol", "||lite.com^", "!#endif",
            "!#if !ext_ubol && (env_chromium || env_firefox)", "||nested.com^", "!#endif",
        )
        assertTrue(e.blocks("https://kept.com/"))
        assertFalse(e.blocks("https://dropped.com/"))
        assertFalse(e.blocks("https://lite.com/"))
        assertTrue(e.blocks("https://nested.com/"))
    }

    @Test fun duplicatesAreCountedOnce() {
        val e = FilterEngine.build(listOf("||a.com^\n||a.com^\n", "||a.com^\n||b.com^\n"))
        assertEquals(2, e.networkRuleCount)
    }

    @Test fun nonWebSchemesAndEmptyEngine() {
        val e = engine("||a.com^")
        assertFalse(e.blocks("data:text/plain,hi"))
        assertFalse(e.blocks("about:blank"))
        assertFalse(FilterEngine.EMPTY.blocks("https://a.com/"))
        assertTrue(FilterEngine.EMPTY.isEmpty)
    }

    // ---- element hiding

    @Test fun cosmeticDomainScoping() {
        val e = engine(
            "##.ad-banner", "news.site##.promo", "news.site,other.org##div[id^=\"sp\"]",
            "~safe.com##.everywhere-but-safe", "a.com,~sub.a.com###top",
        )
        val news = e.cosmeticSelectors("www.news.site")
        assertTrue(".promo" in news && "div[id^=\"sp\"]" in news && ".ad-banner" in news)
        assertTrue("###top".removePrefix("##") !in news)
        val other = e.cosmeticSelectors("blog.other.org")
        assertTrue("div[id^=\"sp\"]" in other && ".promo" !in other)
        assertFalse(".everywhere-but-safe" in e.cosmeticSelectors("safe.com"))
        assertTrue(".everywhere-but-safe" in e.cosmeticSelectors("x.com"))
        assertTrue("#top" in e.cosmeticSelectors("a.com"))
        assertFalse("#top" in e.cosmeticSelectors("sub.a.com"))
    }

    @Test fun cosmeticExceptions() {
        val e = engine("##.ad", "##.keep-hidden", "news.site#@#.ad", "#@#.keep-hidden-no")
        assertFalse(".ad" in e.cosmeticSelectors("news.site"))
        assertTrue(".ad" in e.cosmeticSelectors("other.site"))
        assertTrue(".keep-hidden" in e.cosmeticSelectors("news.site"))
        val generic = engine("##.ad", "#@#.ad")
        assertFalse(".ad" in generic.cosmeticSelectors("x.com"))
    }

    @Test fun genericRulesAreOnlyOfferedForClassesAndIdsOnThePage() {
        val e = engine(
            "##.ad-slot", "###sponsor", "##div.Banner > a", "##a[href*=\"/click?\"]", "site.com##.local",
            "##.never:not(.x)",
        )
        // What a page gets up front: host rules plus generic rules with nothing to key on.
        val upFront = e.hostSelectors("site.com")
        assertTrue(".local" in upFront && "a[href*=\"/click?\"]" in upFront)
        assertFalse(".ad-slot" in upFront || "#sponsor" in upFront)
        val keyed = e.genericSelectors("site.com", listOf("AD-SLOT", "content"), listOf("sponsor"))
        assertEquals(setOf(".ad-slot", "#sponsor"), keyed.toSet())
        assertEquals(listOf("div.Banner > a"), e.genericSelectors("site.com", listOf("banner"), emptyList()))
        assertTrue(e.genericSelectors("site.com", listOf("nothing"), listOf("none")).isEmpty())
    }

    @Test fun genericHideCanBeSwitchedOffPerSite() {
        val e = engine("##.ad", "a.com##.own", "@@||a.com^\$generichide", "@@||b.com^\$elemhide")
        assertTrue(e.genericSelectors("a.com", listOf("ad"), emptyList()).isEmpty())
        assertTrue(".own" in e.hostSelectors("a.com"))
        assertEquals(listOf(".ad"), e.genericSelectors("c.com", listOf("ad"), emptyList()))
        assertTrue(e.hostSelectors("b.com").isEmpty())
        assertTrue(e.genericSelectors("b.com", listOf("ad"), emptyList()).isEmpty())
    }

    @Test fun hostSelectorsIgnoreProceduralRules() {
        val e = engine("a.com##.x:has-text(Ad)", "a.com##.ok")
        assertEquals(listOf(".ok"), e.hostSelectors("a.com"))
    }

    // ---- storage

    @Test fun serializationRoundTrip() {
        val lines = arrayOf(
            "||ads.example.com^", "@@||ads.example.com/ok^", "/ad-\\d+\\.js/", "||t.com^\$third-party,domain=a.com|~b.a.com",
            "||u.com^\$important", "|https://x.", ".swf|", "\$script,domain=google.*", "0.0.0.0 hosts.example.net",
            "##.ad", "a.com##.own", "a.com#@#.ad", "@@||ok.site^\$document",
        )
        val e = engine(*lines)
        val bytes = e.serialize()
        assertEquals(FilterEngine.FORMAT_VERSION, bytes[0].toInt())
        val back = FilterEngine.load(bytes)
        assertEquals(e.networkRuleCount, back.networkRuleCount)
        assertEquals(e.cosmeticRuleCount, back.cosmeticRuleCount)
        val urls = listOf(
            "https://ads.example.com/x.js", "https://ads.example.com/ok", "https://x.com/ad-12.js", "https://t.com/p",
            "https://u.com/p", "https://x.example/p", "https://q.com/m.swf", "https://hosts.example.net/", "https://clean.com/",
        )
        for (page in listOf("a.com", "b.a.com", "google.com", "ok.site", "plain.org")) for (u in urls) for (third in listOf(true, false)) {
            assertEquals(
                e.shouldBlock(u, page, ResourceType.SCRIPT, third), back.shouldBlock(u, page, ResourceType.SCRIPT, third),
                "$u on $page third=$third",
            )
        }
        for (page in listOf("a.com", "x.org")) {
            assertEquals(e.cosmeticSelectors(page), back.cosmeticSelectors(page))
            assertEquals(e.hostSelectors(page), back.hostSelectors(page))
        }
        assertEquals(e.serialize().toList(), back.serialize().toList())
    }

    @Test fun otherVersionsAndDamagedBytesAreRefused() {
        val bytes = engine("||a.com^", "##.x").serialize()
        val other = bytes.copyOf().also { it[0] = (FilterEngine.FORMAT_VERSION + 1).toByte() }
        assertFailsWith<IOException> { FilterEngine.load(other) }
        assertFailsWith<IOException> { FilterEngine.load(bytes.copyOf(bytes.size / 2)) }
        assertFailsWith<IOException> { FilterEngine.load(ByteArray(0)) }
        val empty = FilterEngine.load(FilterEngine.EMPTY.serialize())
        assertTrue(empty.isEmpty)
    }
}
