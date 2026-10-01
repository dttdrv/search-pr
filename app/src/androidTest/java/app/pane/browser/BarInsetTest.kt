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
 * leaves covering it, and how the end of the page clears it. Every expectation is read off the page's own
 * geometry against the inset the script was given.
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
     * Loads [body] in a phone-sized page, runs the script with [inset], runs [act] (a js statement) and returns what
     * [probe] (a js expression) reports. The script reads and writes in slices after a short delay, so each step waits.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun measure(body: String, inset: Int = 90, act: String = "", probe: String): JSONObject {
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
        evaluate(web, "($script)($inset,$inset)")
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
    fun aBoxThatMovesToTheTopIsNotLiftedAnyMore() {
        val page = "<style>#b{position:fixed;left:0;right:0;bottom:0;height:40px}#b.top{bottom:auto;top:0}</style><div id=b></div>"
        val r = measure(page, act = "window.was = b.hasAttribute('data-pane-lift'); b.className = 'top'",
            probe = "{was: window.was, now: b.hasAttribute('data-pane-lift'), top: b.getBoundingClientRect().top}")
        assertTrue(r.getBoolean("was"))
        assertFalse(r.getBoolean("now"))
        assertEquals(0.0, r.getDouble("top"), 0.5)
    }
}
