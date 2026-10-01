package app.pane.browser.engine

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/**
 * Hides the page furniture the filter lists name (empty ad slots, "sponsored" blocks, cookie nags),
 * without an extension runtime and without sending a big stylesheet to every page.
 *
 * A small script runs at document start in the top frame of every page. It asks the app, over a
 * `WebMessageListener`, for the selectors that apply to its host and then, as the document fills in, for
 * the generic selectors that match the class names and ids it actually uses (a few rounds: when the
 * parser is done, when the page has loaded, and three seconds later). Only selectors that can match are
 * ever sent, so a page gets a few dozen where a list has tens of thousands. They are applied through a
 * constructed stylesheet (`adoptedStyleSheets`, which a page's CSP cannot block), falling back to an
 * empty `<style>` element filled through the CSS object model; each chunk of 40 selectors is one rule
 * and a chunk with an invalid selector is retried one by one, so one bad selector never costs the rest.
 *
 * Needs WebView support for document-start scripts and web message listeners; without it pages are simply
 * not cosmetically filtered. Requests are still blocked either way.
 */
class CosmeticFilters(
    private val lists: FilterLists,
    private val enabled: () -> Boolean,
) {
    private val scripts = WeakHashMap<WebView, ScriptHandler>()

    private val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** Attaches or detaches the script for [page]; safe to call again and again with the same value. */
    fun setEnabled(page: WebView, on: Boolean) {
        val attached = scripts[page]
        if (on && attached == null) {
            if (!supported) return
            runCatching {
                WebViewCompat.addWebMessageListener(page, JS_OBJECT, setOf("*")) { _, message, origin, isMainFrame, reply ->
                    if (!isMainFrame || !enabled()) return@addWebMessageListener
                    val host = origin.host?.lowercase() ?: return@addWebMessageListener
                    val answer = answer(host, message) ?: return@addWebMessageListener
                    if (answer.isNotEmpty()) reply.postMessage(answer)
                }
                scripts[page] = WebViewCompat.addDocumentStartJavaScript(page, SCRIPT, setOf("*"))
            }
        } else if (!on && attached != null) {
            runCatching { attached.remove() }
            runCatching { WebViewCompat.removeWebMessageListener(page, JS_OBJECT) }
            scripts.remove(page)
        }
    }

    private fun answer(host: String, message: WebMessageCompat): String? {
        val text = message.data ?: return null
        if (text.length > MAX_MESSAGE) return null
        // The saved index is still loading (the first page of a cold start): ask the page to repeat itself.
        val engine = lists.engineOrNull() ?: return if (text.length < MAX_ECHO) "?$text" else null
        if (text == "s") return engine.hostSelectors(host).joinToString("\n")
        if (!text.startsWith("g\n")) return null
        val lines = text.split('\n')
        val ids = lines.getOrNull(1)?.split(' ')?.filter { it.isNotEmpty() && it.length <= 100 }.orEmpty()
        val classes = lines.getOrNull(2)?.split(' ')?.filter { it.isNotEmpty() && it.length <= 100 }.orEmpty()
        return engine.genericSelectors(host, classes, ids).joinToString("\n")
    }

    private companion object {
        const val JS_OBJECT = "paneCosmetic"
        const val MAX_MESSAGE = 400_000
        const val MAX_ECHO = 20_000

        val SCRIPT = """
(function(){
var P=window.paneCosmetic;
if(!P||window!==window.top||location.protocol.indexOf('http')!==0)return;
var R='{display:none!important}',S=null,T=0,W=[],C=Object.create(null),I=Object.create(null);
function make(){
if(S)return true;
try{S=new CSSStyleSheet();document.adoptedStyleSheets=document.adoptedStyleSheets.concat([S]);return true}catch(e){S=null}
var p=document.head||document.documentElement;if(!p)return false;
var el=document.createElement('style');p.appendChild(el);S=el.sheet;return!!S}
function put(t){try{S.insertRule(t,S.cssRules.length);return true}catch(e){return false}}
function apply(l){for(var i=0;i<l.length;i+=40){var c=l.slice(i,i+40);
if(!put(c.join(',')+R))for(var j=0;j<c.length;j++)put(c[j]+R)}}
function add(l){if(make()){apply(l)}else{W.push(l)}}
function flush(){if(W.length&&make()){var w=W;W=[];for(var i=0;i<w.length;i++)apply(w[i])}}
P.onmessage=function(e){var d=e.data;if(!d)return;
if(d.charCodeAt(0)===63){if(++T<20)setTimeout(function(){P.postMessage(d.slice(1))},300);return}
add(d.split('\n'))};
P.postMessage('s');
function scan(){flush();
var c=[],d=[],n=document.querySelectorAll('[class],[id]'),k,j,v,l;
for(k=0;k<n.length;k++){v=n[k].id;
if(v&&!I[v]&&v.indexOf(' ')<0){I[v]=1;d.push(v)}
l=n[k].classList;for(j=0;l&&j<l.length;j++){v=l[j];if(!C[v]){C[v]=1;c.push(v)}}}
if(c.length||d.length)P.postMessage('g\n'+d.join(' ')+'\n'+c.join(' '))}
document.addEventListener('DOMContentLoaded',scan);
window.addEventListener('load',function(){scan();setTimeout(scan,3000)});
})();
""".trimIndent()
    }
}
