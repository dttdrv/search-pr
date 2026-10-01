package app.pane.browser

import android.content.ContextWrapper
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import app.pane.browser.engine.AdBlocker
import app.pane.browser.engine.FilterLists
import app.pane.browser.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AdBlockingWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun listRulesRunBeforePageScriptsAndPreserveAllowedRequests() {
        val dir = File(instrumentation.targetContext.cacheDir, "filter-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) { override fun getFilesDir() = dir }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = SettingsStore(context)
        val original = settings.current
        settings.update { it.copy(blockAds = true, enabledFilterLists = listOf("ublock-filters")) }
        File(dir, "filters").mkdir()
        File(dir, "filters/ublock-filters.txt").writeText("""
example.test##+js(set, adblock, false)
example.test##+js(set, ytInitialPlayerResponse.adPlacements, undefined)
example.test##+js(json-prune, playerAds)
example.test##+js(json-prune-fetch-response, fetchAds, , propsToMatch, url:/fetch-json)
example.test##+js(json-prune-xhr-response, xhrAds, , propsToMatch, url:/xhr-json)
example.test##+js(trusted-replace-fetch-response, ad, content, url:/trusted-fetch)
example.test##+js(trusted-replace-xhr-response, ad, content, url:/trusted-xhr)
example.test##+js(abort-on-property-read, blockedRead)
example.test##+js(abort-on-property-write, blockedWrite)
example.test##+js(abort-current-script, dangerous, ad-script)
example.test##+js(no-fetch-if, url:/blocked-fetch)
example.test##+js(no-fetch-if, url:/stub-fetch, war:noop.json)
example.test##+js(no-xhr-if, /blocked-xhr)
example.test##+js(remove-attr, data-ad, #slot, stay)
example.test##+js(remove-class, advert, #slot)
example.test##+js(nostif, timerAd)
example.test##+js(nosiif, intervalAd)
example.test##+js(prevent-addEventListener, ad-event)
example.test>>##+js(set, ancestorRule, false)
||example.test/ad.js${'$'}script,redirect=googletagservices_gpt.js
||example.test/tracker^
||example.test/allowed-fetch${'$'}script
""".trimIndent())
        val lists = FilterLists(context, settings, scope).also { it.start() }
        val blocker = AdBlocker(lists) { settings.current.blockAds }
        val ready = CountDownLatch(1)
        scope.launchReady(blocker, ready)
        assertTrue("filter compilation", ready.await(20, TimeUnit.SECONDS))
        var view: WebView? = null
        try {
            instrumentation.runOnMainSync {
                val page = WebView(context)
                view = page
                page.settings.javaScriptEnabled = true
                page.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                        if (!r.isForMainFrame) blocker.intercept(r, "example.test")?.let { return it }
                        val path = r.url.path
                        val body = when (path) {
                            "/" -> HTML
                            "/frame" -> "<script nonce=fixture>window.ancestorRule=true;parent.fixture.ancestor=ancestorRule===false;</script>"
                            "/fetch-json" -> "{\"fetchAds\":1,\"keep\":2}"
                            "/xhr-json" -> "{\"xhrAds\":1,\"keep\":3}"
                            "/allowed-fetch" -> "allowed"
                            "/trusted-fetch", "/trusted-xhr" -> "ad body"
                            else -> throw AssertionError("unexpected fixture request: $path")
                        }
                        return WebResourceResponse(if (path == "/" || path == "/frame") "text/html" else "application/json", "utf-8", 200, "OK",
                            if (path == "/" || path == "/frame") mapOf("Content-Security-Policy" to "script-src 'nonce-fixture'") else emptyMap(), ByteArrayInputStream(body.toByteArray()))
                    }
                }
                blocker.setEnabled(page, true)
                page.loadUrl("https://example.test/")
            }
            val end = SystemClock.uptimeMillis() + 20_000
            var result = JSONObject()
            while (SystemClock.uptimeMillis() < end) {
                val done = CountDownLatch(1)
                instrumentation.runOnMainSync { view!!.evaluateJavascript("window.fixture || {}") { result = JSONObject(it); done.countDown() } }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                if (result.optBoolean("done")) break
                SystemClock.sleep(100)
            }
            assertTrue("fixture completed: $result", result.optBoolean("done"))
            for (key in listOf("constant", "initialJson", "json", "read", "write", "currentScript", "fetch", "xhr", "trustedFetch", "trustedXhr", "blockedFetch", "stubFetch", "blockedXhr", "attribute", "class", "event", "timers", "redirect", "allowed", "ancestor")) {
                assertTrue("$key: $result", result.getBoolean(key))
            }
            assertEquals(403, result.getInt("trackerStatus"))
        } finally {
            view?.let { page -> instrumentation.runOnMainSync { page.destroy() } }
            scope.cancel()
            settings.update { original }
            dir.deleteRecursively()
        }
    }

    private fun CoroutineScope.launchReady(blocker: AdBlocker, ready: CountDownLatch) = launch { blocker.awaitEngine(); ready.countDown() }

    @Test fun firstInstallHasDefaultScriptletsBeforeNetworkUpdates() {
        val dir = File(instrumentation.targetContext.cacheDir, "filter-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(instrumentation.targetContext) { override fun getFilesDir() = dir }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = SettingsStore(context)
        val original = settings.current
        settings.update { it.copy(blockAds = true, enabledFilterLists = app.pane.core.settings.BrowserSettings.DEFAULT_FILTER_LISTS) }
        val lists = FilterLists(context, settings, scope).also { it.start() }
        val blocker = AdBlocker(lists) { settings.current.blockAds }
        try {
            val ready = CountDownLatch(1)
            scope.launchReady(blocker, ready)
            assertTrue("bundled filter compilation", ready.await(20, TimeUnit.SECONDS))
            assertFalse(lists.engine().scriptletsForHost("m.youtube.com").isEmpty())
            assertTrue(lists.engine().shouldBlock("https://doubleclick.net/ad", "example.test", app.pane.core.adblock.ResourceType.SCRIPT, true))
            assertTrue(File(dir, "filters/engine.bin").isFile)
            for (host in listOf("example.test", "m.youtube.com")) {
                val done = CountDownLatch(1)
                var view: WebView? = null
                try {
                    instrumentation.runOnMainSync {
                        val page = WebView(context).also { view = it; it.settings.javaScriptEnabled = true }
                        page.webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = WebResourceResponse("text/html", "utf-8", ByteArrayInputStream("<!doctype html><link rel=icon href=data:,><p>content</p>".toByteArray()))
                            override fun onPageFinished(v: WebView, url: String) { v.evaluateJavascript("window.filterTestElapsed") {
                                android.util.Log.i("PaneAdblockTiming", "host=$host runtimeMs=$it programBytes=${lists.engine().documentStartScript.toByteArray().size}")
                                done.countDown()
                            } }
                        }
                        WebViewCompat.addDocumentStartJavaScript(page, "window.filterTestStart=performance.now();", setOf("*"))
                        blocker.setEnabled(page, true)
                        WebViewCompat.addDocumentStartJavaScript(page, "window.filterTestElapsed=performance.now()-filterTestStart;", setOf("*"))
                        page.loadUrl("https://$host/")
                    }
                    assertTrue("document-start timing", done.await(10, TimeUnit.SECONDS))
                } finally { view?.let { page -> instrumentation.runOnMainSync { page.destroy() } } }
            }
        } finally {
            scope.cancel()
            settings.update { original }
            dir.deleteRecursively()
        }
    }

    private companion object {
        val HTML = """
<!doctype html><html><head><link rel="icon" href="data:,">
<script nonce="fixture">
window.fixture={};window.adblock=true;fixture.constant=adblock===false;
window.ytInitialPlayerResponse={adPlacements:[1],videoDetails:{title:'content'}};
fixture.initialJson=ytInitialPlayerResponse.adPlacements===undefined&&ytInitialPlayerResponse.videoDetails.title==='content';
fixture.json=JSON.parse('{"playerAds":1,"keep":2}').playerAds===undefined;
try{void window.blockedRead;fixture.read=false}catch(e){fixture.read=true}
try{window.blockedWrite=1;fixture.write=false}catch(e){fixture.write=true}
</script><script nonce="fixture">/* ad-script */window.dangerous;window.currentScriptRan=true;</script>
<script nonce="fixture" src="/ad.js"></script></head><body><iframe src="/frame"></iframe><div id="slot" class="advert content" data-ad="x">content</div>
<script nonce="fixture">
fixture.currentScript=!window.currentScriptRan;
fixture.redirect=typeof googletag.defineSlot==='function';
window.eventRan=false;window.addEventListener('ad-event',()=>{window.eventRan=true});window.dispatchEvent(new Event('ad-event'));fixture.event=!eventRan;
window.timerRan=false;window.intervalRan=false;
setTimeout(function timerAd(){window.timerRan=true},0);setInterval(function intervalAd(){window.intervalRan=true},0);
const xhr=(url)=>new Promise((resolve,reject)=>{const x=new XMLHttpRequest();x.open('GET',url);x.onload=()=>resolve(x);x.onerror=reject;x.send()});
Promise.all([
fetch('/fetch-json').then(r=>r.json()).then(v=>{fixture.fetch=v.fetchAds===undefined&&v.keep===2}),
xhr('/xhr-json').then(x=>{const v=JSON.parse(x.responseText);fixture.xhr=v.xhrAds===undefined&&v.keep===3}),
fetch('/trusted-fetch').then(r=>r.text()).then(v=>{fixture.trustedFetch=v==='content body'}),
xhr('/trusted-xhr').then(x=>{fixture.trustedXhr=x.responseText==='content body'}),
fetch('/stub-fetch').then(r=>r.json()).then(v=>{fixture.stubFetch=typeof v==='object'}),
fetch('/blocked-fetch').then(r=>r.text()).then(v=>{fixture.blockedFetch=v===''}),
xhr('/blocked-xhr').then(x=>{fixture.blockedXhr=x.responseText===''}),
fetch('/allowed-fetch').then(r=>r.text()).then(v=>{fixture.allowed=v==='allowed'}),
fetch('/tracker').then(r=>{fixture.trackerStatus=r.status})
]).then(()=>setTimeout(()=>{
const el=document.getElementById('slot');fixture.attribute=!el.hasAttribute('data-ad');fixture.class=!el.classList.contains('advert')&&el.classList.contains('content');
fixture.timers=!timerRan&&!intervalRan;fixture.done=true;
},100));
</script></body></html>
""".trimIndent()
    }
}
