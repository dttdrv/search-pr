package app.pane.browser.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import app.pane.core.adblock.Hosts
import app.pane.core.adblock.RequestTypes
import app.pane.core.adblock.ResourceType
import app.pane.core.adblock.FilterEngine
import app.pane.core.adblock.FilterResources
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.util.WeakHashMap
import java.io.ByteArrayInputStream

/** list-driven filtering on request threads, with document-start delivery on the UI thread. */
class AdBlocker(private val lists: FilterLists, private val enabled: () -> Boolean) {
    /** The element-hiding half, attached to each page by the session manager. */
    val cosmetics = CosmeticFilters(lists, enabled)

    val engines get() = lists.engines
    private val scripts = WeakHashMap<WebView, Pair<FilterEngine, ScriptHandler>>()

    suspend fun awaitEngine() = engines.filterNotNull().first()

    fun setEnabled(page: WebView, on: Boolean) {
        val engine = lists.engineOrNull()
        val attached = scripts[page]
        if (on && attached?.first === engine) return
        attached?.second?.remove()
        scripts.remove(page)
        if (!on || engine == null || engine.documentStartScript.isEmpty() ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        scripts[page] = engine to WebViewCompat.addDocumentStartJavaScript(page, engine.documentStartScript, engine.scriptletOrigins)
    }

    fun prepare(url: String, pageHost: String?): String {
        if (!enabled()) return url
        val engine = lists.engineOrNull() ?: return url
        val host = android.net.Uri.parse(url).host ?: return url
        if (!Hosts.isValid(host) || pageHost != null && !Hosts.isValid(pageHost)) return url
        return engine.removeParameters(url, pageHost ?: host, ResourceType.DOCUMENT, pageHost != null && !Hosts.sameSite(host, pageHost))
    }

    /** The response for a blocked request, or null to let it through. [pageHost] is the page's host, if known. */
    fun intercept(request: WebResourceRequest, pageHost: String?): WebResourceResponse? {
        if (request.url.toString().startsWith(FilterResources.origin + "/")) {
            val resource = FilterResources.redirect(request.url.lastPathSegment.orEmpty()) ?: return blockedResponse()
            return resourceResponse(resource)
        }
        if (!enabled()) return null
        val url = request.url
        val host = url.host ?: return null
        val scheme = url.scheme
        if (scheme != "http" && scheme != "https") return null
        val engine = lists.engine()
        if (engine.isEmpty) return null
        val headers = request.requestHeaders
        val type = RequestTypes.infer(
            url = url.toString(),
            accept = header(headers, "Accept"),
            secFetchDest = header(headers, "Sec-Fetch-Dest"),
            method = request.method,
            ranged = header(headers, "Range") != null,
        )
        val page = header(headers, "Referer")?.let { android.net.Uri.parse(it).host } ?: pageHost
        if (!Hosts.isValid(host) || page != null && !Hosts.isValid(page)) return null
        val thirdParty = page != null && !Hosts.sameSite(host, page)
        val blocked = engine.shouldBlock(url.toString(), page, type, thirdParty, request.method)
        if (!blocked) return null
        val resource = engine.redirectResource(url.toString(), page, type, thirdParty, request.method)
        if (app.pane.browser.BuildConfig.DEBUG) android.util.Log.d("PaneAdblockTrace", "BLOCK host=$host method=${request.method} type=$type party=$thirdParty redirect=${resource?.name}")
        return if (resource == null) blockedResponse() else resourceResponse(resource)
    }

    private fun resourceResponse(resource: app.pane.core.adblock.RedirectResource) = WebResourceResponse(
            resource.mime, if (resource.mime.startsWith("text/") || resource.mime == "application/javascript" || resource.mime == "application/json") "utf-8" else null,
            200, "OK", mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"), ByteArrayInputStream(resource.bytes),
        )

    private fun header(headers: Map<String, String>?, name: String): String? {
        if (headers == null) return null
        headers[name]?.let { return it }
        for ((k, v) in headers) if (k.equals(name, ignoreCase = true)) return v
        return null
    }

    companion object {
        /** same registrable domain from the Public Suffix List. */
        fun sameSite(a: String, b: String): Boolean = Hosts.sameSite(a, b)

        fun registrable(host: String): String = Hosts.registrable(host)

        fun blockedResponse() = WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }
}
