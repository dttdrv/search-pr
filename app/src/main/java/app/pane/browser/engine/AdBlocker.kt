package app.pane.browser.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import app.pane.core.adblock.Hosts
import app.pane.core.adblock.RequestTypes
import app.pane.core.adblock.ResourceType
import java.io.ByteArrayInputStream

/**
 * Stops requests that the filter lists name before they leave the phone. The lists and the engine
 * live in [FilterLists]; this is the part that sits in `shouldInterceptRequest`: it works out what a
 * request is (WebView doesn't say, see [RequestTypes]), whether it is third-party, asks the engine,
 * and answers a blocked request with an empty body of a harmless kind so the page's own code sees a
 * quiet failure rather than an error page.
 *
 * Runs on WebView's request threads, never the main thread, and never touches a view.
 */
class AdBlocker(private val lists: FilterLists, private val enabled: () -> Boolean) {
    /** The element-hiding half, attached to each page by the session manager. */
    val cosmetics = CosmeticFilters(lists, enabled)

    /** The response for a blocked request, or null to let it through. [pageHost] is the page's host, if known. */
    fun intercept(request: WebResourceRequest, pageHost: String?): WebResourceResponse? {
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
        val page = pageHost?.lowercase()
        val thirdParty = page != null && !Hosts.sameSite(host, page)
        if (!engine.shouldBlock(url.toString(), page, type, thirdParty)) return null
        return emptyResponse(type)
    }

    private fun header(headers: Map<String, String>?, name: String): String? {
        if (headers == null) return null
        headers[name]?.let { return it }
        for ((k, v) in headers) if (k.equals(name, ignoreCase = true)) return v
        return null
    }

    companion object {
        /** Same registrable domain, judged by the last two labels (three under a country second level). */
        fun sameSite(a: String, b: String): Boolean = Hosts.sameSite(a, b)

        fun registrable(host: String): String = Hosts.registrable(host)

        // A 1x1 transparent GIF: an image slot that shows nothing instead of a broken-image icon.
        private val PIXEL = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C,
            0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B,
        )

        /**
         * An empty answer of the kind that was asked for: an empty script or stylesheet parses to nothing,
         * an empty frame is blank, an image is a clear pixel, and anything else is "no content".
         */
        fun emptyResponse(type: Int): WebResourceResponse = when {
            type and ResourceType.IMAGE != 0 && type and ResourceType.UNKNOWN == 0 ->
                WebResourceResponse("image/gif", null, 200, "OK", emptyMap(), ByteArrayInputStream(PIXEL))
            type == ResourceType.STYLESHEET ->
                WebResourceResponse("text/css", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            type == ResourceType.SUBDOCUMENT ->
                WebResourceResponse("text/html", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            type and ResourceType.SCRIPT != 0 ->
                WebResourceResponse("application/javascript", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            else ->
                WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }
}
