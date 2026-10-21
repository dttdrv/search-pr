package app.pane.browser

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private fun measure(body: String, inset: Int = 90, head: Int = 0, fall: Int = 0, act: String = "", probe: String): JSONObject {
        val loaded = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) = loaded.countDown()
                }
                val density = resources.displayMetrics.density
                measure(View.MeasureSpec.makeMeasureSpec((400 * density).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((800 * density).toInt(), View.MeasureSpec.EXACTLY))
                layout(0, 0, measuredWidth, measuredHeight)
                loadDataWithBaseURL("http://pane.test/", "<meta name=viewport content=\"width=device-width\"><style>body{margin:0}</style>$body", "text/html", "utf-8", null)
            }
        }
        assertTrue("page did not load", loaded.await(20, TimeUnit.SECONDS))
        evaluate(web, "($script)($inset,$inset,$head,$fall,10,0.1,0.75)")
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

    private val line = "(innerHeight - parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pane-inset')))"
    private fun bottom(id: String) = "document.getElementById('$id').getBoundingClientRect().bottom"
    /** where the box's content ends, above its bottom padding */
    private fun content(id: String) = "(${bottom(id)} - parseFloat(getComputedStyle(document.getElementById('$id')).paddingBottom))"
    private fun top(id: String) = "document.getElementById('$id').getBoundingClientRect().top"

    /** what the status area shows: whether a header holds it, the background drawn there, and the last tone the app was told */
    private val edge = "{held: document.querySelector('pane-edge').hasAttribute('data-held'), " +
        "paint: getComputedStyle(document.querySelector('pane-edge')).backgroundImage + getComputedStyle(document.querySelector('pane-edge')).backgroundColor, " +
        "told: (window.__told || []).slice(-1)[0] || ''}"

    /** listens as the app does for the tone, then makes the script look at the page again */
    private val listen = "window.__told = []; window.__paneTone = {postMessage: m => __told.push(m)}; dispatchEvent(new Event('resize'))"

    /** a css rgb colour as the page posts it to the app: (r << 16) | (g << 8) | b */
    private fun tone(r: Int, g: Int, b: Int) = ((r shl 16) or (g shl 8) or b).toString()

    @Test
    fun bottomBarSitsAboveTheBar() {
        val r = measure("<div id=nav style='position:fixed;left:0;right:0;bottom:0;height:56px'></div><div style='height:3000px'></div>",
            probe = "{nav: ${bottom("nav")}, line: $line}")
        assertEquals(r.getDouble("line"), r.getDouble("nav"), 1.0)
    }

    @Test
    fun aBottomBarThatPaintsItselfRunsOnUnderTheBarWithItsContentAboveIt() {
        for (paint in listOf("background:rgb(10,20,30)", "background:linear-gradient(90deg,red,blue)", "padding-bottom:8px;background:#fff;box-sizing:border-box")) {
            val r = measure("<div id=nav style='position:fixed;left:0;right:0;bottom:0;height:56px;$paint'><p style='margin:0'>home</p></div><div style='height:3000px'></div>",
                probe = "{nav: ${bottom("nav")}, content: ${content("nav")}, height: ${bottom("nav")} - ${top("nav")}, line: $line, h: innerHeight, inset: innerHeight - $line, ext: document.documentElement.style.getPropertyValue('--pane-ext')}")
            assertEquals("$paint: its paint reaches the bottom edge", r.getDouble("h"), r.getDouble("nav"), 1.0)
            val pad = if ("padding-bottom" in paint) 8.0 else 0.0
            assertEquals("$paint: its content ends at the bar", r.getDouble("line") - pad, r.getDouble("content"), 1.0)
            assertEquals("$paint: it grows by the bar and no more", 56 + r.getDouble("inset"), r.getDouble("height"), 1.0)
            assertEquals("$paint: no colour is carried for it", "none", r.getString("ext"))
        }
    }

    // under the bar the same rule as under the status area: a bar lifted above it across the width carries its colour on
    // down, as it shows (dimmed by a backdrop over it); nothing does where the bar blurs, floats clear of the edge or
    // paints its own way on down

    private val under = "{ext: document.documentElement.style.getPropertyValue('--pane-ext')}"

    @Test
    fun aBarLiftedAboveThePillCarriesItsPaintedColourDownUnderIt() {
        // a sticky bar is lifted rather than run on under the pill, and a fixed one may paint only what it holds
        val bars = listOf(
            "<div style='height:3000px'></div><div style='position:sticky;bottom:0;height:56px;background:rgb(10,20,30)'></div>",
            "<div style='position:fixed;left:0;right:0;bottom:0;height:56px'><div style='height:100%;background:rgb(10,20,30)'></div></div><div style='height:3000px'></div>",
        )
        for (bar in bars) {
            assertEquals(bar, "linear-gradient(rgb(10, 20, 30),rgb(10, 20, 30)),linear-gradient(Canvas,Canvas)", measure(bar, probe = under).getString("ext"))
            val dimmed = measure("$bar<div style='position:fixed;inset:0;z-index:2;background:rgba(0,0,0,.5)'></div>", probe = under).getString("ext")
            assertTrue("a backdrop over it dims it there too: $dimmed", dimmed.startsWith("linear-gradient(rgba(0, 0, 0, 0.5),rgba(0, 0, 0, 0.5)),linear-gradient(rgb(10, 20, 30)"))
        }
    }

    @Test
    fun nothingIsCarriedUnderThePillForABarThatBlursOrFloatsClearOfTheEdge() {
        for (bar in listOf("bottom:0;backdrop-filter:blur(8px)", "bottom:12px")) {
            val r = measure("<div style='position:fixed;left:0;right:0;$bar;height:56px'><div style='height:100%;background:rgb(10,20,30)'></div></div><div style='height:3000px'></div>", probe = under)
            assertEquals(bar, "none", r.getString("ext"))
        }
        // bbc's shape: a consent sheet in a frame over a painted bar; what the frame shows can't be read
        val framed = measure("<div style='height:3000px'></div><div style='position:sticky;bottom:0;height:56px;background:rgb(10,20,30)'></div>" +
            "<div style='position:fixed;left:0;right:0;bottom:0;height:200px;z-index:3'><iframe style='display:block;width:100%;height:100%;border:0' srcdoc='<body style=background:#888>'></iframe></div>", probe = under)
        assertEquals("none", framed.getString("ext"))
    }

    @Test
    fun aFloatingCardOrButtonKeepsItsShape() {
        for (box in listOf("left:12px;right:12px;bottom:12px;height:80px;border-radius:12px", "right:16px;bottom:0;width:56px;height:56px")) {
            val r = measure("<div id=b style='position:fixed;$box;background:#fff'></div><div style='height:3000px'></div>",
                probe = "{b: ${bottom("b")}, height: ${bottom("b")} - ${top("b")}, line: $line}")
            val gap = if ("bottom:12px" in box) 12.0 else 0.0
            assertEquals(box, r.getDouble("line") - gap, r.getDouble("b"), 1.0)
            assertEquals(box, if (gap > 0) 80.0 else 56.0, r.getDouble("height"), 1.0)
        }
    }

    @Test
    fun theBarFoldingMovesWhatIsLiftedAndNothingElseTakesItsLength() {
        val r = measure("<div id=nav style='position:fixed;left:0;right:0;bottom:0;height:56px'></div><p id=p>text</p><div style='height:3000px'></div>",
            act = "__paneBar(40, 90, 0)",
            probe = "{nav: ${bottom("nav")}, h: innerHeight, p: getComputedStyle(document.getElementById('p')).getPropertyValue('--pane-inset')}")
        assertEquals("the lift follows the folded bar", r.getDouble("h") - 40, r.getDouble("nav"), 1.0)
        assertEquals("the page's own boxes don't inherit the lift, so a fold restyles only what wears it", "0px", r.getString("p"))
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
            probe = "{dim: ${bottom("dim")}, sheet: ${bottom("sheet")}, content: ${content("sheet")}, line: $line, h: innerHeight}",
        )
        assertEquals(r.getDouble("h"), r.getDouble("dim"), 1.0)
        assertEquals("the sheet runs on under the bar", r.getDouble("h"), r.getDouble("sheet"), 1.0)
        assertEquals("what the sheet holds ends at the bar", r.getDouble("line"), r.getDouble("content"), 1.0)
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
    fun aPageThatDoesNotScrollRunsOnUnderTheBarWithWhatItHoldsAboveItHoweverItIsBuilt() {
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
            val r = measure(page, probe = "{nav: ${bottom("nav")}, content: ${content("nav")}, line: $line, h: innerHeight, ext: document.documentElement.style.getPropertyValue('--pane-ext'), root: document.documentElement.scrollHeight - innerHeight}")
            assertEquals("$name: what it holds ends at the bar", r.getDouble("line"), r.getDouble("content"), 1.0)
            assertEquals("$name: its paint reaches the bottom edge", r.getDouble("h"), r.getDouble("nav"), 1.0)
            assertEquals("$name: no colour is carried for it", "none", r.getString("ext"))
            assertEquals("$name: the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
        }
    }

    @Test
    fun aGroundThatTakesTheWholePageRunsOnUnderTheBarAndKeepsItsHeight() {
        val r = measure("<style>html,body{height:100%}</style><div id=g style='height:100%;display:grid;place-items:center;background:linear-gradient(red,blue)'><button id=b style='height:40px'>sign up</button></div>",
            probe = "{g: ${bottom("g")}, height: ${bottom("g")} - ${top("g")}, content: ${content("g")}, b: ${bottom("b")}, line: $line, h: innerHeight, ext: document.documentElement.style.getPropertyValue('--pane-ext'), root: document.documentElement.scrollHeight - innerHeight}")
        assertEquals("its paint reaches the bottom edge", r.getDouble("h"), r.getDouble("g"), 1.0)
        assertEquals("it keeps the page's height", r.getDouble("h"), r.getDouble("height"), 1.0)
        assertEquals("what it holds is centred in what the bar leaves", (r.getDouble("line") + 40) / 2, r.getDouble("b"), 1.0)
        assertEquals("no colour is carried for it", "none", r.getString("ext"))
        assertEquals("the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
    }

    @Test
    fun aFramedPageReachesTheEdgeAndItsViewportEndsAtTheBar() {
        val r = measure("<style>html,body{height:100%}iframe{border:0;width:100%;height:100%;display:block}</style><iframe id=f srcdoc='<p>inner</p>'></iframe>",
            probe = "{f: ${bottom("f")}, content: ${content("f")}, line: $line, h: innerHeight, root: document.documentElement.scrollHeight - innerHeight}")
        assertEquals(r.getDouble("h"), r.getDouble("f"), 1.0)
        assertEquals(r.getDouble("line"), r.getDouble("content"), 1.0)
        assertEquals("the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
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
        val still = measure(page, head = 30, probe = "{first: ${top("first")}}")
        assertEquals(30.0, still.getDouble("first"), 1.0)
        val moved = measure(page, head = 30, act = "scrollTo(0, 200)", probe = "{first: ${top("first")}}")
        assertEquals(30.0 - 200, moved.getDouble("first"), 1.0)
    }

    @Test
    fun withNoStatusAreaTheDocumentStartsWhereItDid() {
        val r = measure("<div id=first style='height:40px'></div><div style='height:3000px'></div>", probe = "{first: ${top("first")}}")
        assertEquals(0.0, r.getDouble("first"), 0.5)
    }

    // webkit's rule for the top edge (LocalFrameView::fixedContainerEdges): a fixed or sticky box held at the edge
    // that spans the width carries its background colour on under the browser's own ui; nothing else does

    @Test
    fun aHeaderHeldAtTheTopCarriesItsColourUpUnderTheStatusArea() {
        for (place in listOf("position:fixed;top:0;left:0;right:0", "position:sticky;top:0")) {
            val r = measure("<div style='$place;height:56px;background:rgb(200,0,0)'></div><div style='height:3000px'></div>", head = 30, act = listen, probe = edge)
            assertTrue(place, r.getBoolean("held"))
            assertTrue(place, "rgb(200, 0, 0)" in r.getString("paint"))
            assertEquals(place, tone(200, 0, 0), r.getString("told"))
        }
    }

    @Test
    fun aHeaderPaintedByAPlateBehindItCarriesThePlatesColour() {
        // youtube's shape: an untouchable plate holds the colour, and the header over it holds the buttons
        val r = measure("<div style='position:fixed;top:0;left:0;right:0;height:48px;background:rgb(15,15,15);pointer-events:none'></div>" +
            "<div style='position:fixed;top:0;left:0;right:0;height:48px;z-index:1;pointer-events:none'><button style='pointer-events:auto'>menu</button></div>" +
            "<div style='height:3000px'></div>", head = 30, act = listen, probe = edge)
        assertTrue(r.getBoolean("held"))
        assertEquals(tone(15, 15, 15), r.getString("told"))
    }

    /** [told] is the source-over blend [over] at [alpha] on [under], to within the canvas's 8-bit rounding */
    private fun assertBlend(over: Int, alpha: Double, under: Int, told: String) {
        val expected = over * alpha + under * (1 - alpha)
        listOf(16, 8, 0).forEach { assertEquals(told, expected, ((told.toInt() shr it) and 0xFF).toDouble(), 1.0) }
    }

    @Test
    fun aSheetDimmingTheHeaderDimsWhatTheStatusAreaCarries() {
        val r = measure("<div style='position:fixed;top:0;left:0;right:0;height:56px;background:#fff'></div>" +
            "<div style='position:fixed;inset:0;z-index:2;background:rgba(0,0,0,.5)'></div><div style='height:3000px'></div>", head = 30, act = listen, probe = edge)
        assertTrue(r.getBoolean("held"))
        assertBlend(0, 0.5, 255, r.getString("told"))
    }

    @Test
    fun aHeaderThatLetsThePageThroughCarriesWhatShowsThroughIt() {
        // youtube's watch page: a translucent black header over the black player, on a white page
        for ((under, color) in listOf(0 to "#000", 255 to "#fff")) {
            val r = measure("<div style='position:fixed;top:0;left:0;right:0;height:56px;background:rgba(0,0,0,.5)'></div>" +
                "<div style='height:300px;background:$color'></div><div style='height:3000px'></div>", head = 30, act = listen, probe = edge)
            assertTrue(color, r.getBoolean("held"))
            assertBlend(0, 0.5, under, r.getString("told"))
        }
    }

    @Test
    fun atRestThePagesFirstRowGoesOnUpUnderTheStatusArea() {
        // the guardian's shape: a dark masthead in the page's flow, on a white page
        val page = "<div style='height:56px;background:rgb(2,15,37)'></div><div style='height:3000px'></div>"
        val rest = measure(page, head = 30, act = listen, probe = edge)
        assertTrue(rest.getBoolean("held"))
        assertEquals(tone(2, 15, 37), rest.getString("told"))
        assertFalse("scrolled, the page shows through", measure(page, head = 30, act = "$listen; scrollTo(0, 500)", probe = edge).getBoolean("held"))
    }

    @Test
    fun withNothingHeldThePageItselfShowsThroughBlurredUnderItsOwnBackground() {
        val page = "<style>body{background:rgb(250,250,250)}</style><div style='height:3000px'></div>"
        for (top in listOf("", "<div style='position:fixed;top:0;left:0;right:0;height:56px;background:rgba(255,255,255,.8);backdrop-filter:blur(8px)'></div>",
            "<div style='position:fixed;top:0;right:0;width:56px;height:56px;background:rgb(200,0,0)'></div>")) {
            val r = measure(top + page, head = 30, act = listen, probe = edge)
            assertFalse(top, r.getBoolean("held"))
            assertEquals(top, tone(250, 250, 250), r.getString("told"))
        }
    }

    @Test
    fun aHeaderTheSiteSlidesAwayLetsGoOfTheStatusArea() {
        // a WebView off screen runs no transitions, so the site's slide is taken to its end at once
        val page = "<div id=h style='position:fixed;top:0;left:0;right:0;height:56px;background:rgb(200,0,0)'></div><div style='height:3000px'></div>"
        assertTrue(measure(page, head = 30, probe = edge).getBoolean("held"))
        assertFalse(measure(page, head = 30, act = "document.getElementById('h').style.transform = 'translateY(-100%)'", probe = edge).getBoolean("held"))
    }

    @Test
    fun whatAHeaderSlidesUpInsideItselfGoesOutAtTheStatusAreasEdge() {
        // x's shape: the sticky header stays where it is held and only what it holds is moved up out of view
        val r = measure("<div style='position:sticky;top:0;height:56px'><div id=inner style='height:56px;background:#000;transform:translateY(-40px)'></div>" +
            "<div id=menu style='position:absolute;top:56px;left:0;width:200px;height:100px;background:#fff'></div></div><div style='height:3000px'></div>",
            head = 30, probe = "{above: (document.elementFromPoint(200, 25) || {}).id || '', below: (document.elementFromPoint(100, 30 + 56 + 50) || {}).id || ''}")
        assertFalse("nothing it holds shows over the status area", r.getString("above") == "inner")
        assertEquals("what drops from it still shows", "menu", r.getString("below"))
    }

    @Test
    fun theEdgeFadesPastTheStatusAreaOnlyOnceThePageScrolls() {
        val page = "<p>first</p><div style='height:3000px'></div>"
        val reach = "{fall: parseFloat(getComputedStyle(document.querySelector('pane-edge')).getPropertyValue('--pane-fall'))}"
        assertEquals("at rest the page's first line is not faded", 0.0, measure(page, head = 30, fall = 15, probe = reach).getDouble("fall"), 0.5)
        assertEquals(15.0, measure(page, head = 30, fall = 15, act = "scrollTo(0, 500)", probe = reach).getDouble("fall"), 0.5)
    }

    @Test
    fun aHeaderHeldAtTheTopSitsBelowTheStatusArea() {
        val headers = mapOf(
            "fixed" to "<div id=h style='position:fixed;top:0;left:0;right:0;height:56px;background:rgb(10,20,30)'></div>",
            "sticky" to "<div id=h style='position:sticky;top:0;height:56px;background:rgb(10,20,30)'></div>",
        )
        for ((name, header) in headers) {
            val r = measure("$header<div style='height:3000px'></div>", head = 30, act = "scrollTo(0, 500)", probe = "{h: ${top("h")}}")
            assertEquals(name, 30.0, r.getDouble("h"), 1.0)
        }
    }

    @Test
    fun aHeaderTheSiteSlidesAwayGoesOutAtTheStatusAreasEdge() {
        for (place in listOf("position:fixed;top:0;left:0;right:0", "position:sticky;top:0")) {
            val r = measure("<div id=h style='$place;height:56px'></div><div style='height:3000px'></div>", head = 30,
                act = "scrollTo(0, 500); h.style.transform = 'translateY(-100%)'",
                probe = "{top: ${top("h")}, cut: parseFloat(h.getAttribute('data-pane-cut'))}")
            assertEquals("$place: it slid up by its own height from below the status area", 30.0 - 56, r.getDouble("top"), 1.0)
            assertEquals("$place: what is left of it starts at the status area's edge", 30.0, r.getDouble("top") + r.getDouble("cut"), 1.0)
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
            probe = "{h: ${top("h")}, nav: ${bottom("nav")}, content: ${content("nav")}, line: $line, edge: innerHeight, below: document.documentElement.style.getPropertyValue('--pane-ext'), root: document.documentElement.scrollHeight - innerHeight}",
        )
        assertEquals(30.0, r.getDouble("h"), 1.0)
        assertEquals(r.getDouble("line"), r.getDouble("content"), 1.0)
        assertEquals(r.getDouble("edge"), r.getDouble("nav"), 1.0)
        assertEquals("none", r.getString("below"))
        assertEquals("the page must not start to scroll", 0.0, r.getDouble("root"), 1.0)
    }

    @Test
    fun boxesASiteStacksUnderItsHeaderStayStackedBelowTheStatusArea() {
        val r = measure(
            "<div id=h style='position:fixed;top:0;left:0;right:0;height:48px'></div>" +
                "<div id=player style='position:fixed;top:48px;left:0;right:0;height:200px'></div><div style='height:3000px'></div>",
            head = 30, probe = "{h: ${top("h")}, hEnd: ${bottom("h")}, player: ${top("player")}}",
        )
        assertEquals(30.0, r.getDouble("h"), 1.0)
        assertEquals("the box under the header still starts where the header ends", r.getDouble("hEnd"), r.getDouble("player"), 1.0)
    }

    @Test
    fun aBoxPlacedByAShareOfTheViewportStaysWhereItIs() {
        val r = measure("<div id=m style='position:fixed;top:50%;left:10%;width:80%;height:100px'></div><div style='height:3000px'></div>",
            head = 30, probe = "{m: ${top("m")}, h: innerHeight}")
        assertEquals(r.getDouble("h") / 2, r.getDouble("m"), 1.0)
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
