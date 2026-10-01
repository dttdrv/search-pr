package app.pane.core.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterModifiersTest {
    private fun engine(vararg rules: String, trusted: Boolean = false) = FilterEngineBuilder().apply {
        addText(rules.joinToString("\n"), trusted)
    }.build()
    private val url = "https://ads.example/script.js"
    private fun FilterEngine.redirect() = redirectResource(url, "news.example", ResourceType.SCRIPT, true)
    private fun FilterEngine.strip(url: String) = removeParameters(url, "example.com", ResourceType.DOCUMENT, false)

    @Test fun redirectRulesDoNotBlockAndExceptionsKeepTheBlock() {
        val e = engine("||ads.example^\$script,redirect-rule=noop.js")
        assertFalse(e.shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
        val block = engine("||ads.example^\$script,redirect=noopjs", "@@||ads.example^\$redirect-rule=noop.js")
        assertTrue(block.shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
        assertNull(block.redirect())
        assertFalse(engine("||ads.example^\$redirect=noop.js", "@@||ads.example^").shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
    }

    @Test fun redirectsUsePriorityAndRealSurrogates() {
        val e = engine("||ads.example^\$redirect=noop.js", "||ads.example^\$redirect-rule=googletagservices_gpt.js:9")
        assertEquals("googletagservices_gpt.js", e.redirect()?.name)
        assertTrue(String(e.redirect()!!.bytes).contains("googletag"))
        assertNull(engine("||ads.example^\$redirect=noop.js", "||ads.example^\$redirect-rule=none:2").redirect())
        assertEquals("noop.js", engine("||ads.example^\$redirect=noop.js", "||ads.example^\$redirect-rule=googletagservices_gpt.js:2", "@@||ads.example^\$redirect-rule=googletagservices_gpt.js:2").redirect()?.name)
        assertEquals("image/gif", FilterResources.redirect("1x1.gif")?.mime)
        assertTrue(FilterResources.redirect("1x1.gif")!!.bytes.take(3).toByteArray().contentEquals("GIF".toByteArray()))
    }

    @Test fun modifierBadfiltersCancelAliasesAndIndividualDomains() {
        val e = engine("||ads.example^\$redirect=noopjs", "||ads.example^\$redirect=noop.js,badfilter")
        assertFalse(e.shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
        assertNull(e.redirect())
        val domains = engine("*\$script,domain=a.com|b.com", "*\$script,domain=a.com,badfilter")
        assertFalse(domains.shouldBlock(url, "a.com", ResourceType.SCRIPT, true))
        assertTrue(domains.shouldBlock(url, "b.com", ResourceType.SCRIPT, true))
        val params = engine("||example.com^\$removeparam=ad", "||example.com^\$removeparam=ad,badfilter")
        assertEquals("https://example.com/?ad=1", params.strip("https://example.com/?ad=1"))
    }

    @Test fun parametersKeepRawEncodingOrderDuplicatesAndAntiAbuseTokens() {
        val e = engine("||example.com^\$removeparam=utm_source")
        val input = "https://example.com/?utm_source=a&ei=a%2Fb&sxsrf=x&ved=2&sca_esv=3&gs_lcrp=4&source=hp&keep=x+y&keep=%2B&utm_source=b#anchor"
        assertEquals("https://example.com/?ei=a%2Fb&sxsrf=x&ved=2&sca_esv=3&gs_lcrp=4&source=hp&keep=x+y&keep=%2B#anchor", e.strip(input))
        assertFalse(e.shouldBlock(input, "example.com", ResourceType.SCRIPT, false))
        assertEquals("https://example.com/#x", e.strip("https://example.com/?utm_source=x#x"))
        assertEquals("https://example.com/?UTM_source=x", e.strip("https://example.com/?UTM_source=x"))
        assertEquals("https://example.com/", e.strip("https://example.com/?utm_source=%Q"))
    }

    @Test fun parameterRegexAndExceptionsFollowModifierSemantics() {
        val e = engine("||example.com^\$removeparam=/^utm_.*=/", "@@||example.com^\$removeparam=utm_keep")
        assertEquals("https://example.com/?safe=3", e.strip("https://example.com/?utm_%61d=1&utm_keep=2&safe=3"))
        assertEquals("https://example.com/", engine("||example.com^\$removeparam", "@@||example.com^\$removeparam=utm_keep").strip("https://example.com/?ad=1&utm_keep=2"))
        assertEquals("https://example.com/", engine("||example.com^\$removeparam=ad,important", "@@||example.com^\$removeparam").strip("https://example.com/?ad=1"))
        assertEquals("https://example.com/?keep=1", engine("||example.com^\$removeparam=~keep").strip("https://example.com/?keep=1&ad=2"))
        assertNull(FilterParser.parseOne("||example.com^\$removeparam=~/[/"))
    }

    @Test fun methodsStrictPartiesAndPrivateSuffixEntitiesAreIndependent() {
        val e = engine("||ads.example^\$method=POST|~GET")
        assertTrue(e.shouldBlock(url, "news.example", ResourceType.XHR, true, "POST"))
        assertFalse(e.shouldBlock(url, "news.example", ResourceType.XHR, true, "GET"))
        assertFalse(engine("||ads.example^\$1p").shouldBlock(url, null, ResourceType.SCRIPT, false))
        assertFalse(engine("||ads.example^\$3p").shouldBlock(url, null, ResourceType.SCRIPT, true))
        val strict = engine("||example.com^\$1p,strict3p")
        assertTrue(strict.shouldBlock("https://cdn.example.com/x", "example.com", ResourceType.SCRIPT, false))
        assertFalse(strict.shouldBlock("https://example.com/x", "example.com", ResourceType.SCRIPT, false))
        val entity = engine("||ads.example^\$domain=alice.*")
        assertTrue(entity.shouldBlock(url, "alice.github.io", ResourceType.SCRIPT, true))
        assertFalse(entity.shouldBlock(url, "alice.other.github.io", ResourceType.SCRIPT, true))
        assertNull(FilterParser.parseOne("*\$script,denyallow=example.com"))
        assertNull(FilterParser.parseOne("*\$script,denyallow=example.*,domain=news.com"))
    }

    @Test fun scriptletAliasesQuotingEntitiesAndNegatedExceptions() {
        val e = engine("example.*##+js(set, value, false)", "example.com,~sub.example.com#@#+js(set-constant, value, false)")
        assertTrue(e.scriptletsForHost("sub.example.com").isEmpty())
        assertTrue(e.scriptletsForHost("example.com").isEmpty())
        assertEquals(1, e.scriptletsForHost("www.example.co.uk").size)
        assertTrue(e.scriptletsForHost("example.evil.com").isEmpty())
        assertEquals(listOf("json-prune.js", "a,b", "c,d"), (FilterParser.parseOne("example.com##+js(json-prune, 'a,b', c\\,d)") as ScriptletFilter).args)
        assertTrue(engine("##+js(set, a, false)").scriptletsForHost("example.com").isEmpty())
        assertNull(FilterParser.parseOne("~example.com#@#+js()"))
        assertEquals(listOf("set", "path", "\\\\", "false"), FilterParser.scriptletArgs("set, path, \\\\, false"))
        assertTrue(engine("example.com##+js(set, a, false)", "example.com#@#+js()").scriptletsForHost("example.com").isEmpty())
        assertEquals(1, engine("example.com##+js(set, a, false)", "example.com##+js(set-constant, a, false)").scriptletRuleCount)
    }

    @Test fun trustedScriptletsRequireTrustedListsAndSavedEnginesRetainBehavior() {
        val rule = "example.com##+js(trusted-set-constant, ad, false)"
        assertTrue(engine(rule).isEmpty)
        assertEquals(1, engine(rule, trusted = true).scriptletRuleCount)
        val original = engine(rule, "||ads.example^\$redirect=noop.js", "||example.com^\$removeparam=ad", trusted = true)
        val out = ByteArrayOutputStream().also(original::serialize)
        val saved = FilterEngine.load(ByteArrayInputStream(out.toByteArray()))
        assertEquals(original.scriptletsForHost("example.com"), saved.scriptletsForHost("example.com"))
        assertEquals(original.documentStartScript, saved.documentStartScript)
        assertEquals(original.redirect()?.name, saved.redirect()?.name)
        assertEquals("https://example.com/", saved.strip("https://example.com/?ad=1"))
        assertNotNull(FilterResources.scriptlet("nostif"))
        assertNotNull(FilterResources.scriptlet("nosiif"))
    }

    @Test fun scriptletAncestorScopesMatchFramesAndKeepLocalExceptions() {
        val e = engine("publisher.*>>##+js(set, ad, false)", "frame.test#@#+js(set, ad, false)")
        assertTrue(e.scriptletsForHost("publisher.com").isEmpty())
        assertEquals(1, e.scriptletsForHost("player.test", listOf("www.publisher.co.uk")).size)
        assertTrue(e.scriptletsForHost("frame.test", listOf("publisher.com")).isEmpty())
        assertTrue(e.scriptletsForHost("player.test", listOf("publisher.other.com")).isEmpty())
    }

    @Test fun reviewedModifierAndScopeCounterexamples() {
        val r = engine("||ads.example^", "@@||ads.example^\$redirect=noop.js")
        assertTrue(r.shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
        assertNull(r.redirect())
        val b = engine("||ads.example^\$script,redirect=noop.js", "||ads.example^\$script,badfilter")
        assertFalse(b.shouldBlock(url, "news.example", ResourceType.SCRIPT, true))
        assertNotNull(engine("||ads.example^\$redirect=noop.js,important", "@@||ads.example^\$redirect-rule").redirect())
        val p = engine("||example.com^\$~document,removeparam=token")
        assertEquals("https://example.com/?token=1", p.strip("https://example.com/?token=1"))
        assertEquals("https://example.com/?%74oken=1", engine("||example.com^\$removeparam=token").strip("https://example.com/?%74oken=1"))
        assertEquals("https://example.com/?token=a+b", engine("||example.com^\$removeparam=/^token=a b$/").strip("https://example.com/?token=a+b"))
        assertTrue(engine("example.com,~sub.example.com##+js(set, a, false)", "sub.example.com##+js(set, a, false)").scriptletsForHost("sub.example.com").isEmpty())
        assertFalse(Hosts.isValid("a..b"))
        assertEquals("https://a..b/?token=1", p.strip("https://a..b/?token=1"))
        assertTrue(FilterResources.scriptlet("proxy-apply-config")!!.priority > FilterResources.scriptlet("prevent-xhr")!!.priority)
    }

    @Test fun longUrlsAndLateTokensStillMatch() {
        val prefix = "https://example.com/?" + (1..150).joinToString("&") { "token$it=value$it" }
        assertTrue(engine("late-ad-token").shouldBlock("$prefix&late-ad-token=1", "example.com", ResourceType.SCRIPT, false))
        assertTrue(engine("late-ad-token").shouldBlock("https://example.com/" + "x".repeat(3000) + "/late-ad-token", "example.com", ResourceType.SCRIPT, false))
    }
}
