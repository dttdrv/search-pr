package app.pane.browser

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewCompat
import app.pane.browser.engine.TopColorBridge
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The page script (assets/bar-inset.js) in a real WebView: what it lifts above the floating bar, what it
 * leaves covering it, how the end of the page clears it, and the same at the top for the status area. Every
 * expectation is read off the page's own geometry against the insets the script was given.
 */
@RunWith(AndroidJUnit4::class)
class BarInsetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val script = instrumentation.targetContext.assets.open("bar-inset.js").bufferedReader().use { it.readText() }

    private fun evaluate(web: WebView, js: String): String {
        val answer = arrayOfNulls<String>(1)
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript(js) {
                answer[0] = it
                done.countDown()
            }
        }
        assertTrue("no answer to $js", done.await(20, TimeUnit.SECONDS))
        return answer[0].orEmpty()
    }

    /**
     * Loads [body] in a phone-sized page, runs the script with [inset] for the bar and [head] for the status area,
     * runs [act] (a js statement) and returns what [probe] (a js expression) reports. The script reads and writes
     * in slices after a short delay, so each step waits.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun measure(body: String, inset: Int = 90, head: Int = 0, act: String = "", told: MutableList<String>? = null, probe: String): JSONObject {
        val loaded = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                }
                // what the page tells the app it shows under the status area, in the order it said it
                if (told != null) WebViewCompat.addWebMessageListener(this, "__paneTop", setOf("*")) { _, message, _, _, _ -> synchronized(told) { told.add(message.data.orEmpty()) } }
                val density = resources.displayMetrics.density
                measure(View.MeasureSpec.makeMeasureSpec((400 * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.EXACTLY))
                layout(0, 0, measuredWidth, measuredHeight)
                loadDataWithBaseURL("http://pane.test/", "<meta name=viewport content=\"width=device-width\"><style>body{margin:0}</style>$body", "text/html", "utf-8", null)
            }
        }
        assertTrue("page did not load", loaded.await(20, TimeUnit.SECONDS))
        evaluate(web, "($script)($inset,$inset,$head)")
        Thread.sleep(700)
        if (act.isNotEmpty()) {
            evaluate(web, act)
            Thread.sleep(700)
        }
        val answer = evaluate(web, "JSON.stringify($probe)")
        instrumentation.runOnMainSync { web.destroy() }
        // evaluateJavascript hands back a js string literal
        return JSONObject(JSONObject("{\"v\":$answer}").getString("v"))
    }

    private val line = "innerHeight - parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pane-inset'))"
    private fun bottom(id: String) = "document.getElementById('$id').getBoundingClientRect().bottom"
    private fun top(id: String) = "document.getElementById('$id').getBoundingClientRect().top"
    private val above = "document.documentElement.style.getPropertyValue('--pane-ext-top')"

    @Test
    fun bottomBarSitsAboveTheBar() {
        val r = measure("<div id=nav style='position:fixed;left:0;right:0;bottom:0;height:56px;background:#fff'></div><div style='height:3000px'></div>",
            probe = "{nav: ${bottom("nav")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("nav"), 1.0)
    }

    @Test
    fun floatingButtonAwayFromTheEdgeIsLiftedToo() {
        val r = measure("<div id=fab style='position:fixed;right:20px;bottom:20px;width:56px;height:56px'></div>",
            probe = "{fab: ${bottom("fab")}, line: $line}")
        assertEquals(r.getDouble("line") - 20, r.getDouble("fab"), 1.0)
    }

    @Test
    fun fullHeightBoxesShrinkWhateverWayTheyAreSized() {
        for (size in listOf("top:0;bottom:0", "top:0;height:100%", "top:0;height:100vh", "top:0;bottom:0;height:100%")) {
            val r = measure("<div id=m style='position:fixed;left:0;width:100%;$size'><p>dialog</p></div>", probe = "{m: ${bottom("m")}, line: $line}")
            assertEquals(size, r.getDouble("line"), r.getDouble("m"), 1.0)
        }
    }

    @Test
    fun dimmedBackdropKeepsCoveringTheBarWhileItsSheetIsLifted() {
        val r = measure(
            "<div id=dim style='position:fixed;inset:0;background:#0007'></div>" +
                "<div id=sheet style='position:fixed;left:0;right:0;bottom:0;height:200px;background:rgb(10,20,30)'></div>",
            probe = "{dim: ${bottom("dim")}, sheet: ${bottom("sheet")}, line: $line, h: innerHeight, ext: document.documentElement.style.getPropertyValue('--pane-ext')}",
        )
        assertEquals(r.getDouble("h"), r.getDouble("dim"), 1.0)
        assertEquals(r.getDouble("line"), r.getDouble("sheet"), 1.0)
        assertEquals("the sheet's colour continues under the bar", "rgb(10, 20, 30)", r.getString("ext"))
    }

    @Test
    fun stickyFooterStaysAboveTheBar() {
        val r = measure("<div style='height:3000px'></div><div id=f style='position:sticky;bottom:0;height:50px'></div>",
            probe = "{f: ${bottom("f")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("f"), 1.0)
    }

    @Test
    fun theEndOfTheDocumentScrollsClearOfTheBar() {
        val r = measure("<div style='height:3000px'></div><div id=last style='height:40px'></div>",
            act = "scrollTo(0, 1e9)", probe = "{last: ${bottom("last")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("last"), 1.0)
    }

    @Test
    fun anAppShellsInnerScrollerClearsTheBarAndTheRootStaysStill() {
        val r = measure(
            "<style>html,body{height:100%;overflow:hidden}main{height:100%;overflow:auto}</style><main id=m><div style='height:3000px'></div><div id=last style='height:40px'></div></main>",
            act = "m.scrollTop = 1e9",
            probe = "{last: ${bottom("last")}, line: $line, root: document.documentElement.scrollHeight - innerHeight}",
        )
        assertEquals(r.getDouble("line"), r.getDouble("last"), 1.0)
        assertEquals("the root must not gain scroll room under overflow hidden", 0.0, r.getDouble("root"), 1.0)
    }

    @Test
    fun aFixedBarPlacedFromTheTopSitsAboveTheBarToo() {
        for (place in listOf("top:calc(100vh - 56px)", "top:100%;transform:translateY(-100%)")) {
            val r = measure("<div id=nav style='position:fixed;left:0;right:0;height:56px;$place'></div><div style='height:3000px'></div>",
                probe = "{nav: ${bottom("nav")}, line: $line}")
            assertEquals(place, r.getDouble("line"), r.getDouble("nav"), 1.0)
        }
    }

    @Test
    fun aPageThatDoesNotScrollEndsAboveTheBarHoweverItIsBuilt() {
        val bar = "<div id=nav style='height:56px;background:rgb(10,20,30)'></div>"
        val shells = mapOf(
            "flex at 100%" to "<style>html,body{height:100%}body{display:flex;flex-direction:column}main{flex:1;overflow:auto}</style><main><div style='height:3000px'></div></main>$bar",
            "flex at 100dvh" to "<style>body{height:100dvh;display:flex;flex-direction:column}main{flex:1;overflow:auto}</style><main><div style='height:3000px'></div></main>$bar",
            "grid" to "<style>html,body{height:100%}body{display:grid;grid-template-rows:1fr auto}main{overflow:auto}</style><main><div style='height:3000px'></div></main>$bar",
            "a box of its own under a header" to "<style>#app{height:100vh;display:flex;flex-direction:column}main{flex:1;overflow:auto}</style><div id=app><div style='height:60px'></div><main><div style='height:3000px'></div></main>$bar</div>",
            "a short page with its footer pushed down" to "<style>body{min-height:100vh;display:flex;flex-direction:column}#nav{margin-top:auto}</style><p>short</p>$bar",
            "placed against the viewport" to "<style>html,body{height:100%}main{height:100%;overflow:auto}#nav{position:absolute;left:0;right:0;bottom:0}</style><main><div style='height:3000px'></div></main>$bar",
        )
        for ((name, page) in shells) {
            val r = measure(page, probe = "{nav: ${bottom("nav")}, line: $line, ext: document.documentElement.style.getPropertyValue('--pane-ext'), root: document.documentElement.scrollHeight - innerHeight}")
            assertEquals(name, r.getDouble("line"), r.getDouble("nav"), 1.0)
            assertEquals("$name: the bar's colour continues under the floating bar", "rgb(10, 20, 30)", r.getString("ext"))
            assertEquals("$name: the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
        }
    }

    @Test
    fun aFramedPageEndsAboveTheBar() {
        val r = measure("<style>html,body{height:100%}iframe{border:0;width:100%;height:100%;display:block}</style><iframe id=f srcdoc='<p>inner</p>'></iframe>",
            probe = "{f: ${bottom("f")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("f"), 1.0)
    }

    @Test
    fun aPaintedLayerNobodyCanTouchKeepsCoveringTheBar() {
        val r = measure("<div id=bg style='position:fixed;inset:0;pointer-events:none'><div style='height:100%'></div></div><div style='height:3000px'></div>",
            probe = "{bg: ${bottom("bg")}, h: innerHeight}")
        assertEquals(r.getDouble("h"), r.getDouble("bg"), 1.0)
    }

    @Test
    fun aLayerThatOnlyLetsTouchesThroughStillLiftsTheButtonItHolds() {
        val r = measure("<div style='position:fixed;inset:0;pointer-events:none;display:flex;flex-direction:column;justify-content:flex-end'><button id=b style='pointer-events:auto;height:48px'>ok</button></div><div style='height:3000px'></div>",
            probe = "{b: ${bottom("b")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("b"), 1.0)
    }

    @Test
    fun aSheetsOwnListRunsUnderTheBarAndItsEndClearsIt() {
        val page = "<style>#sheet{position:fixed;left:0;right:0;bottom:0;height:60%;display:flex;flex-direction:column}#list{flex:1;overflow:auto}</style>" +
            "<div id=sheet><div style='height:40px'></div><div id=list><div style='height:3000px'></div><div id=last style='height:40px'></div></div></div><div style='height:3000px'></div>"
        val r = measure(page, act = "list.scrollTop = 1e9", probe = "{sheet: ${bottom("sheet")}, last: ${bottom("last")}, line: $line, h: innerHeight}")
        assertEquals("the sheet itself reaches the bottom edge", r.getDouble("h"), r.getDouble("sheet"), 1.0)
        assertEquals("the end of its list stops at the bar", r.getDouble("line"), r.getDouble("last"), 1.0)
    }

    @Test
    fun aPictureThatFillsAPageThatDoesNotScrollRunsUnderTheBar() {
        val r = measure("<style>html,body{height:100%}canvas{display:block;width:100%;height:100%}</style><canvas id=c></canvas>",
            probe = "{c: ${bottom("c")}, h: innerHeight}")
        assertEquals(r.getDouble("h"), r.getDouble("c"), 1.0)
    }

    @Test
    fun theDocumentStartsBelowTheStatusAreaAndScrollsUpUnderIt() {
        val page = "<div id=first style='height:40px'></div><div style='height:3000px'></div>"
        val still = measure(page, head = 30, probe = "{first: ${top("first")}, above: $above}")
        assertEquals(30.0, still.getDouble("first"), 1.0)
        assertEquals("nothing is held at the top, so nothing is painted over what passes under", "none", still.getString("above"))
        val moved = measure(page, head = 30, act = "scrollTo(0, 200)", probe = "{first: ${top("first")}}")
        assertEquals(30.0 - 200, moved.getDouble("first"), 1.0)
    }

    @Test
    fun withNoStatusAreaTheDocumentStartsWhereItDid() {
        val r = measure("<div id=first style='height:40px'></div><div style='height:3000px'></div>", probe = "{first: ${top("first")}}")
        assertEquals(0.0, r.getDouble("first"), 0.5)
    }

    @Test
    fun aHeaderHeldAtTheTopSitsBelowTheStatusAreaAndColoursIt() {
        val headers = mapOf(
            "fixed" to "<div id=h style='position:fixed;top:0;left:0;right:0;height:56px;background:rgb(10,20,30)'></div>",
            "sticky" to "<div id=h style='position:sticky;top:0;height:56px;background:rgb(10,20,30)'></div>",
        )
        for ((name, header) in headers) {
            val r = measure("$header<div style='height:3000px'></div>", head = 30, act = "scrollTo(0, 500)", probe = "{h: ${top("h")}, above: $above}")
            assertEquals(name, 30.0, r.getDouble("h"), 1.0)
            assertEquals("$name: its colour continues over the status area", "rgb(10, 20, 30)", r.getString("above"))
        }
    }

    @Test
    fun aStickyHeaderKeepsTheGapThePageGaveIt() {
        val r = measure("<div id=h style='position:sticky;top:12px;height:56px'></div><div style='height:3000px'></div>",
            head = 30, act = "scrollTo(0, 500)", probe = "{h: ${top("h")}}")
        assertEquals(30.0 + 12, r.getDouble("h"), 1.0)
    }

    @Test
    fun aHeaderPlacedAgainstTheViewportMovesDownWithTheDocument() {
        val r = measure("<div id=h style='position:absolute;top:0;left:0;right:0;height:56px'></div><div id=first style='height:300px'></div><div style='height:3000px'></div>",
            head = 30, probe = "{h: ${top("h")}, first: ${top("first")}}")
        assertEquals(r.getDouble("first"), r.getDouble("h"), 1.0)
        assertEquals(30.0, r.getDouble("h"), 1.0)
    }

    @Test
    fun aBackdropKeepsCoveringTheStatusAreaWhileADialogStartsBelowIt() {
        val r = measure(
            "<div id=dim style='position:fixed;inset:0;background:#0007'></div>" +
                "<div id=dialog style='position:fixed;inset:0;display:flex;flex-direction:column'><button>close</button></div><div style='height:3000px'></div>",
            head = 30, probe = "{dim: ${top("dim")}, dialog: ${top("dialog")}, end: ${bottom("dialog")}, line: $line}",
        )
        assertEquals(0.0, r.getDouble("dim"), 0.5)
        assertEquals(30.0, r.getDouble("dialog"), 1.0)
        assertEquals(r.getDouble("line"), r.getDouble("end"), 1.0)
    }

    @Test
    fun aPageThatDoesNotScrollFitsBetweenTheStatusAreaAndTheBar() {
        val r = measure(
            "<style>html,body{height:100%}body{display:flex;flex-direction:column}main{flex:1;overflow:auto}</style>" +
                "<div id=h style='height:56px;background:rgb(10,20,30)'></div><main><div style='height:3000px'></div></main><div id=nav style='height:56px;background:rgb(40,50,60)'></div>",
            head = 30,
            probe = "{h: ${top("h")}, nav: ${bottom("nav")}, line: $line, above: $above, below: document.documentElement.style.getPropertyValue('--pane-ext'), root: document.documentElement.scrollHeight - innerHeight}",
        )
        assertEquals(30.0, r.getDouble("h"), 1.0)
        assertEquals(r.getDouble("line"), r.getDouble("nav"), 1.0)
        assertEquals("rgb(10, 20, 30)", r.getString("above"))
        assertEquals("rgb(40, 50, 60)", r.getString("below"))
        assertEquals("the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
    }

    @Test
    fun thePageTellsTheAppWhatItShowsUnderTheStatusAreaAsItScrolls() {
        val told = mutableListOf<String>()
        measure(
            "<style>body{background:rgb(250,250,250)}</style><div style='height:1000px'></div><div style='height:3000px;background:rgb(10,20,30)'></div>",
            head = 30, act = "scrollTo(0, 1500)", told = told, probe = "{}",
        )
        assertEquals(listOf("rgb(250, 250, 250)", "rgb(10, 20, 30)"), synchronized(told) { told.toList() })
    }

    @Test
    fun aHeaderHeldAtTheTopIsWhatThePageTellsTheApp() {
        val told = mutableListOf<String>()
        measure(
            "<div style='position:fixed;top:0;left:0;right:0;height:56px;background:rgb(10,20,30)'></div><div style='height:3000px;background:rgb(250,250,250)'></div>",
            head = 30, act = "scrollTo(0, 500)", told = told, probe = "{}",
        )
        assertEquals("rgb(10, 20, 30)", synchronized(told) { told.last() })
    }

    @Test
    fun aPictureUnderTheStatusAreaIsLeftForTheScreenToRead() {
        val told = mutableListOf<String>()
        measure("<div style='height:3000px;background:linear-gradient(red,blue)'></div>", head = 30, told = told, probe = "{}")
        assertEquals(listOf(""), synchronized(told) { told.toList() })
    }

    @Test
    fun onlyAnOpaqueCssColourCountsAsAColour() {
        assertEquals(0xFF0A141E.toInt(), TopColorBridge.parse("rgb(10, 20, 30)"))
        for (not in listOf("", "rgba(10, 20, 30, 0.5)", "linear-gradient(rgb(1, 2, 3),rgb(1, 2, 3)),rgb(4, 5, 6)", null)) {
            assertEquals(not.toString(), null, TopColorBridge.parse(not))
        }
    }

    @Test
    fun aBoxThatMovesToTheTopIsNotLiftedAnyMore() {
        val page = "<style>#b{position:fixed;left:0;right:0;bottom:0;height:40px}#b.top{bottom:auto;top:0}</style><div id=b></div>"
        val r = measure(page, act = "window.was = b.hasAttribute('data-pane-lift'); b.className = 'top'",
            probe = "{was: window.was, now: b.hasAttribute('data-pane-lift'), top: b.getBoundingClientRect().top}")
        assertTrue(r.getBoolean("was"))
        assertFalse(r.getBoolean("now"))
        assertEquals(0.0, r.getDouble("top"), 0.5)
    }
}
