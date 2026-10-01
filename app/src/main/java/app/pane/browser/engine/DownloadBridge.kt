package app.pane.browser.engine

import android.util.Base64
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.pane.browser.downloads.FormPost
import app.pane.core.download.BlobChunk
import app.pane.core.download.BlobReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * what only the page knows about a download, which the web view's download callback leaves out.
 *
 * `blob:` addresses exist only inside the page that made them, so a script at document start remembers every
 * blob it is given an address for (past the page's own `revokeObjectURL`, which scripts call right after
 * clicking the link) and the app asks it for the bytes, one slice at a time, over a `WebMessageListener`.
 * the same script remembers the name a link gave its file (`download="…"`) and the form that was posted to
 * get it, which also reach the callback as a bare address, and [about] asks for both.
 */
class DownloadBridge {
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<List<String>?>>()
    private val ids = AtomicInteger()

    private val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    fun attach(page: WebView) {
        if (!supported) return
        runCatching {
            WebViewCompat.addWebMessageListener(page, JS_OBJECT, setOf("*")) { _, message, _, isMainFrame, _ ->
                val parts = message.data?.split('\t', limit = 4)
                if (isMainFrame && parts != null) waiting.remove(parts[0].toIntOrNull())?.complete(parts.takeIf { it.size == 4 })
            }
            WebViewCompat.addDocumentStartJavaScript(page, SCRIPT, setOf("*"))
        }
    }

    /** reads blobs from [page]; null when this web view can't. */
    fun reader(page: WebView): BlobReader? = if (supported) BlobReader { url, from, to -> read(page, url, from, to) } else null

    /** calls [then] with what [page] knows about [url]: the name its link gave the file and the form posted to get it. */
    fun about(page: WebView, url: String, then: (name: String?, form: FormPost?) -> Unit) {
        if (!supported) return then(null, null)
        page.evaluateJavascript("paneDownloadInfo(${JSONObject.quote(url)})") { raw ->
            val info = runCatching { JSONObject(raw) }.getOrNull()
            val form = info?.optJSONObject("form")
            then(
                info?.optString("name")?.takeIf { it.isNotEmpty() },
                form?.let { FormPost(it.getString("type"), Base64.decode(it.getString("body"), Base64.DEFAULT)) },
            )
        }
    }

    private suspend fun read(page: WebView, url: String, from: Long, to: Long): BlobChunk? {
        val id = ids.incrementAndGet()
        val reply = CompletableDeferred<List<String>?>().also { waiting[id] = it }
        withContext(Dispatchers.Main) {
            page.evaluateJavascript("paneBlobRead($id,${JSONObject.quote(url)},$from,$to)") { if (it != "true") reply.complete(null) }
        }
        val parts = withTimeoutOrNull(TIMEOUT_MS) { reply.await() }
        waiting.remove(id)
        parts ?: return null
        val bytes = withContext(Dispatchers.Default) { Base64.decode(parts[3], Base64.DEFAULT) }
        return BlobChunk(bytes, parts[1].toLong(), parts[2])
    }

    private companion object {
        const val JS_OBJECT = "paneDownload"
        const val TIMEOUT_MS = 30_000L

        val SCRIPT = """
(function(){
var P=window.paneDownload;
if(!P||window!==window.top)return;
var m=new Map(),c=URL.createObjectURL,r=URL.revokeObjectURL;
URL.createObjectURL=function(b){var u=c.call(this,b);m.set(u,b);return u};
URL.revokeObjectURL=function(u){r.call(this,u);setTimeout(function(){m.delete(u)},5000)};
function b64(b,ok,err){var f=new FileReader();f.onload=function(){ok(f.result.slice(f.result.indexOf(',')+1))};f.onerror=err;f.readAsDataURL(b)}
window.paneBlobRead=function(id,u,from,to){
var b=m.get(u);if(!b)return false;
b64(b.slice(from,to),function(t){P.postMessage(id+'\t'+b.size+'\t'+b.type+'\t'+t)},function(){P.postMessage(id+'\t-1')});
return true};
var n=new Map();
function note(a){if(a.download)n.set(a.href,a.download)}
document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');if(a)note(a)},true);
var ck=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){note(this);return ck.call(this)};
var de=EventTarget.prototype.dispatchEvent;
EventTarget.prototype.dispatchEvent=function(e){if(e.type==='click'&&this instanceof HTMLAnchorElement)note(this);return de.call(this,e)};
var last=null;
function pick(s,a,p,v){return s&&s.hasAttribute(a)&&s[p]||v}
function post(f,s){try{
if(pick(s,'formmethod','formMethod',f.method).toLowerCase()!=='post')return;
var a=pick(s,'formaction','formAction',f.action),d=new FormData(f,s);
var q=pick(s,'formenctype','formEnctype',f.enctype)==='multipart/form-data'?new Response(d):new Response(new URLSearchParams(d));
q.blob().then(function(b){if(b.size<=1048576)b64(b,function(t){last={url:a,type:b.type,body:t,at:Date.now()}})})
}catch(e){}}
document.addEventListener('submit',function(e){post(e.target,e.submitter)},true);
var sub=HTMLFormElement.prototype.submit;
HTMLFormElement.prototype.submit=function(){post(this);return sub.call(this)};
window.paneDownloadInfo=function(u){return{name:n.get(u)||'',form:last&&last.url===u&&Date.now()-last.at<6e4?last:null}};
})();
"""
    }
}
